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
 * The glare appears in front of the sniper (at their scope) in the direction
 * they're looking.
 * All values are configurable via PresenceConfig.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "tacz_presence")
public class SniperGlareRenderer {

    private static final ResourceLocation GLARE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tacz_presence", "textures/shared/glare.png");

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

        // Check if glare is enabled in config
        if (!PresenceConfig.GLARE_ENABLED.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null || mc.level == null) {
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

        for (Map.Entry<UUID, Vec3> entry : aimingPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            Vec3 sniperLookDir = entry.getValue();

            // Don't render for local player
            if (playerId.equals(localPlayer.getUUID())) {
                continue;
            }

            Player targetPlayer = mc.level.getPlayerByUUID(playerId);
            if (targetPlayer == null) {
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

            renderGlare(event.getPoseStack(), glarePos, cameraPos, distance);
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

    private static void renderGlare(PoseStack poseStack, Vec3 glarePos, Vec3 cameraPos, double distance) {
        double x = glarePos.x - cameraPos.x;
        double y = glarePos.y - cameraPos.y;
        double z = glarePos.z - cameraPos.z;

        // Get config values for scaling
        double minDistance = PresenceConfig.GLARE_MIN_DISTANCE.get();
        float scaleClose = PresenceConfig.GLARE_SCALE_CLOSE.get().floatValue();
        float scaleMid = PresenceConfig.GLARE_SCALE_MID.get().floatValue();
        float scaleFar = PresenceConfig.GLARE_SCALE_FAR.get().floatValue();
        float baseSize = PresenceConfig.GLARE_BASE_SIZE.get().floatValue();
        float horizontalStretch = PresenceConfig.GLARE_HORIZONTAL_STRETCH.get().floatValue();

        // Scale: SMALL when close, LARGE when far
        // Uses config values for scaleClose, scaleMid, scaleFar
        float distanceScale;
        if (distance < 50.0) {
            // Linear growth from scaleClose at minDistance to scaleMid at 50 blocks
            double range = 50.0 - minDistance;
            distanceScale = (float) (scaleClose + (scaleMid - scaleClose) * ((distance - minDistance) / range));
            distanceScale = Math.max(scaleClose, distanceScale);
        } else {
            // Continue growing from scaleMid at 50 blocks to scaleFar at 100 blocks, then
            // cap
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

        // Apply stretch from config
        float halfWidth = (size * horizontalStretch) / 2.0f;
        float halfHeight = size / 2.0f;

        // Render setup - WITH depth test so glare hides behind blocks
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GLARE_TEXTURE);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // Apply opacity from config
        float opacity = PresenceConfig.GLARE_OPACITY.get().floatValue();
        int alpha = (int) (pulse * opacity * 255);

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
