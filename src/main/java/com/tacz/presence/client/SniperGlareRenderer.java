package com.tacz.presence.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.tacz.presence.PresenceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

/**
 * Renders the sniper glare effect for players aiming with high-zoom scopes.
 * Uses weather-based texture selection:
 * - Day/Sunny: glare_3.png (bright)
 * - Rain: glare_2.png (faint)
 * - Night: glare.png (dim)
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "tacz_presence")
public class SniperGlareRenderer {

    // Weather-based textures
    private static final ResourceLocation GLARE_NIGHT = ResourceLocation.fromNamespaceAndPath(
            "tacz_presence", "textures/shared/glare.png");
    private static final ResourceLocation GLARE_RAIN = ResourceLocation.fromNamespaceAndPath(
            "tacz_presence", "textures/shared/glare_2.png");
    private static final ResourceLocation GLARE_DAY = ResourceLocation.fromNamespaceAndPath(
            "tacz_presence", "textures/shared/glare_3.png");

    // Texture aspect ratios (width:height) - 2:1 makes glare wider
    private static final float TEXTURE_ASPECT_RATIO = 2.0f;

    // Fixed constants
    private static final float EYE_HEIGHT = 1.6f;
    private static final float FORWARD_OFFSET = 1.5f;

    // Animation
    private static float animTime = 0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null || mc.level == null) {
            return;
        }

        // Check if glare is enabled in config
        if (!PresenceConfig.GLARE_ENABLED.get()) {
            return;
        }

        animTime += 0.02f;

        Map<UUID, Vec3> aimingPlayers = SniperGlareClientData.getAimingPlayers();
        if (aimingPlayers.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        // Get config values
        double minDistance = PresenceConfig.GLARE_MIN_DISTANCE.get();
        double viewAngle = PresenceConfig.GLARE_VIEW_ANGLE.get();

        // Determine weather conditions once per frame
        boolean isRaining = mc.level.isRaining();
        long dayTime = mc.level.getDayTime() % 24000;
        boolean isNight = dayTime > 13000 && dayTime < 23000;

        // Select texture and opacity based on weather
        ResourceLocation activeTexture = getActiveTexture(isRaining, isNight);
        float weatherOpacity = getWeatherOpacity(isRaining, isNight);

        for (Map.Entry<UUID, Vec3> entry : aimingPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            Vec3 sniperLookDir = entry.getValue();

            // Don't render for local player
            if (playerId.equals(localPlayer.getUUID())) {
                continue;
            }

            // IMPORTANT: Check if target player still exists (fixes death sync bug)
            Player targetPlayer = mc.level.getPlayerByUUID(playerId);
            if (targetPlayer == null) {
                // Player no longer exists (died, disconnected, etc.)
                // Clean up the data
                SniperGlareClientData.removeAimingPlayer(playerId);
                continue;
            }

            // Also check if player is alive
            if (!targetPlayer.isAlive()) {
                SniperGlareClientData.removeAimingPlayer(playerId);
                continue;
            }

            Vec3 playerPos = targetPlayer.getPosition(partialTick);

            // Calculate glare position: at eye level, IN FRONT of player
            Vec3 eyePos = playerPos.add(0, EYE_HEIGHT, 0);
            Vec3 glarePos;

            if (sniperLookDir.lengthSqr() > 0.001) {
                Vec3 forward = sniperLookDir.normalize();
                glarePos = eyePos.add(forward.scale(FORWARD_OFFSET));
            } else {
                glarePos = eyePos;
            }

            double distance = cameraPos.distanceTo(playerPos);

            if (distance <= minDistance) {
                continue;
            }

            // Check if observer is in front of sniper
            if (!isObserverInSniperView(eyePos, sniperLookDir, cameraPos, viewAngle)) {
                continue;
            }

            // Line of sight check
            if (!hasLineOfSight(mc, cameraPos, eyePos)) {
                continue;
            }

            renderGlare(event.getPoseStack(), glarePos, cameraPos, distance, activeTexture, weatherOpacity);
        }
    }

    /**
     * Select texture based on weather conditions.
     */
    private static ResourceLocation getActiveTexture(boolean isRaining, boolean isNight) {
        if (isRaining) {
            // Rain always uses glare_2 (works for both day and night rain)
            return GLARE_RAIN;
        } else if (isNight) {
            // Clear night uses glare.png
            return GLARE_NIGHT;
        } else {
            // Clear day uses glare_3 (brightest)
            return GLARE_DAY;
        }
    }

    /**
     * Calculate opacity based on weather conditions using config values.
     */
    private static float getWeatherOpacity(boolean isRaining, boolean isNight) {
        if (isRaining && isNight) {
            return PresenceConfig.GLARE_OPACITY_NIGHT_RAIN.get().floatValue();
        } else if (isRaining) {
            return PresenceConfig.GLARE_OPACITY_RAIN.get().floatValue();
        } else if (isNight) {
            return PresenceConfig.GLARE_OPACITY_NIGHT.get().floatValue();
        } else {
            return PresenceConfig.GLARE_OPACITY_DAY.get().floatValue();
        }
    }

    private static boolean hasLineOfSight(Minecraft mc, Vec3 from, Vec3 to) {
        if (mc.level == null)
            return false;

        ClipContext context = new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null);

        BlockHitResult result = mc.level.clip(context);

        if (result.getType() == HitResult.Type.MISS) {
            return true;
        }

        Vec3 hitPos = result.getLocation();
        double hitDistance = hitPos.distanceTo(to);
        return hitDistance < 1.0;
    }

    private static boolean isObserverInSniperView(Vec3 sniperPos, Vec3 sniperLookDir, Vec3 observerPos,
            double viewAngle) {
        if (sniperLookDir.lengthSqr() < 0.001) {
            return true;
        }

        Vec3 toObserver = observerPos.subtract(sniperPos).normalize();
        Vec3 lookDir = sniperLookDir.normalize();
        double dot = lookDir.dot(toObserver);
        double threshold = Math.cos(Math.toRadians(viewAngle));

        return dot > threshold;
    }

    private static void renderGlare(PoseStack poseStack, Vec3 glarePos, Vec3 cameraPos, double distance,
            ResourceLocation texture, float weatherOpacity) {
        double x = glarePos.x - cameraPos.x;
        double y = glarePos.y - cameraPos.y;
        double z = glarePos.z - cameraPos.z;

        // Get config values for scaling
        double minDistance = PresenceConfig.GLARE_MIN_DISTANCE.get();
        float scaleClose = PresenceConfig.GLARE_SCALE_CLOSE.get().floatValue();
        float scaleMid = PresenceConfig.GLARE_SCALE_MID.get().floatValue();
        float scaleFar = PresenceConfig.GLARE_SCALE_FAR.get().floatValue();
        float baseSize = PresenceConfig.GLARE_BASE_SIZE.get().floatValue();

        // Scale: SMALL when close, LARGE when far
        float distanceScale;
        if (distance < 50.0) {
            double range = 50.0 - minDistance;
            distanceScale = (float) (scaleClose + (scaleMid - scaleClose) * ((distance - minDistance) / range));
            distanceScale = Math.max(scaleClose, distanceScale);
        } else {
            distanceScale = (float) (scaleMid + (scaleFar - scaleMid) * ((distance - 50.0) / 50.0));
            distanceScale = Math.min(scaleFar, distanceScale);
        }
        float size = baseSize * distanceScale;

        // Subtle, slow pulse
        float pulse = 0.85f + 0.15f * (float) Math.sin(animTime * 1.5);

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // Billboard - face camera
        Minecraft mc = Minecraft.getInstance();
        float camYaw = mc.gameRenderer.getMainCamera().getYRot();
        float camPitch = mc.gameRenderer.getMainCamera().getXRot();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camYaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(camPitch));

        Matrix4f matrix = poseStack.last().pose();

        // Apply texture aspect ratio (2:1 = wider than tall)
        float halfWidth = (size * TEXTURE_ASPECT_RATIO) / 2.0f;
        float halfHeight = size / 2.0f;

        // Render setup - standard alpha blend for proper PNG transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // Apply weather-based opacity (replaces config opacity)
        int alpha = (int) (pulse * weatherOpacity * 255);

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, -halfWidth, -halfHeight, 0).uv(0, 1).color(255, 255, 255, alpha).endVertex();
        buffer.vertex(matrix, halfWidth, -halfHeight, 0).uv(1, 1).color(255, 255, 255, alpha).endVertex();
        buffer.vertex(matrix, halfWidth, halfHeight, 0).uv(1, 0).color(255, 255, 255, alpha).endVertex();
        buffer.vertex(matrix, -halfWidth, halfHeight, 0).uv(0, 0).color(255, 255, 255, alpha).endVertex();

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }
}
