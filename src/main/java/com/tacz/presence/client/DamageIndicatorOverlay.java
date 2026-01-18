package com.tacz.presence.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.tacz.presence.PresenceConfig;
import com.tacz.presence.TaczPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

public class DamageIndicatorOverlay implements IGuiOverlay {
    public static final DamageIndicatorOverlay INSTANCE = new DamageIndicatorOverlay();

    // Using texture from tacz_presence assets
    private static final ResourceLocation INDICATOR_TEXTURE = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "textures/hud/damage_indicator.png");

    private static class Indicator {
        Vec3 sourcePos;
        float life; // Ticks
        float maxLife;
        // Almacenamos el radio inicial calculado al momento del impacto
        // para que no cambie dinámicamente si el jugador se mueve.
        float fixedRadiusScale;

        Indicator(Vec3 pos, float duration, float damage, float radiusScale) {
            this.sourcePos = pos;
            this.life = duration;
            this.maxLife = duration;
            this.fixedRadiusScale = radiusScale;
        }
    }

    private static final List<Indicator> indicators = new ArrayList<>();

    public static void addIndicator(Vec3 sourcePos, float damage) {
        if (sourcePos == null)
            return;

        Minecraft mc = Minecraft.getInstance();
        float radiusScale = 1.0f;

        if (mc.player != null) {
            // Calcular distancia inicial y definir el radio fijo para toda la vida del
            // indicador
            double distance = sourcePos.distanceTo(mc.player.position());
            double maxDist = 64.0;
            double distFactor = Math.min(1.0, distance / maxDist); // 0.0 (near) to 1.0 (far)

            // Lógica de Radio: Aumenta ligeramente con la distancia
            // Cerca (0) -> 0.6x
            // Lejos (64) -> 1.0x (Reduced to prevent indicator going off-screen)
            float minRadius = 0.6f;
            float maxRadius = 1.0f;

            float rawScale = (float) (minRadius + (distFactor * (maxRadius - minRadius)));

            // Añadimos un pequeño factor aleatorio (+- 10%) para variedad ("hacerlo
            // random")
            float randomFactor = 0.9f + (float) Math.random() * 0.2f;
            radiusScale = rawScale * randomFactor;

            // Final Hard Clamp to ensure it never exceeds limits even with random factor
            radiusScale = Math.max(minRadius * 0.8f, Math.min(maxRadius * 1.1f, radiusScale));
        }

        indicators.add(new Indicator(sourcePos, 60.0f, damage, radiusScale)); // 3 seconds fade
    }

    public static void tick() {
        indicators.removeIf(i -> {
            i.life -= 1.0f;
            return i.life <= 0;
        });
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!PresenceConfig.ENABLE_DAMAGE_INDICATOR.get())
            return;
        if (indicators.isEmpty())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        Vec3 playerPos = mc.player.getPosition(partialTick);
        Vec3 lookDir = mc.player.getViewVector(partialTick);

        float cx = screenWidth / 2.0f;
        float cy = screenHeight / 2.0f;

        float baseRadius = Math.min(screenWidth, screenHeight)
                * PresenceConfig.DAMAGE_INDICATOR_RADIUS.get().floatValue();

        // Size: Maintain aspect ratio of 820:223 (~3.67)
        // User request: "no te pedí que sea más pequeño. solo menos ancho, para que sea
        // un poco más ovalado."
        // We restore reasonable height (~30px) but keep width constrained (80px instead
        // of 110px).
        // Original: 110w -> 30h.
        // New: 80w -> we force height to be ~30 to maintain "size" but squash width.
        int width = 80;
        int height = 30;

        for (Indicator ind : indicators) {
            float fadeAlpha = Math.max(0, ind.life / ind.maxLife);
            // Non-linear fade (disappear faster at end)
            fadeAlpha = (float) Math.pow(fadeAlpha, 1.5);
            if (fadeAlpha <= 0)
                continue;

            // Radius Logic:
            // User request: "que si me dispara de lejos y me voy acercando, el indicador
            // también va cambiando su radio y eso noe stá 100% bien"
            // Solución: Usamos el fixedRadiusScale calculado al inicio.
            float radiusScale = ind.fixedRadiusScale;
            float radius = baseRadius * radiusScale;

            // Opacity Logic:
            // User request: "ya no le pongas opacidad" (Don't change opacity by distance)
            float finalAlpha = fadeAlpha;

            // Apply global opacity from config if needed, but reference ignores
            // distance-based opacity
            float configOpacity = PresenceConfig.DAMAGE_INDICATOR_OPACITY.get().floatValue();
            finalAlpha *= configOpacity;

            Vec3 dirToSource = ind.sourcePos.subtract(playerPos);

            double angleToSource = Math.atan2(dirToSource.z, dirToSource.x);
            double lookAngle = Math.atan2(lookDir.z, lookDir.x);

            double diff = angleToSource - lookAngle;
            // Normalize
            while (diff < -Math.PI)
                diff += 2 * Math.PI;
            while (diff > Math.PI)
                diff -= 2 * Math.PI;

            // Screen Angle Calculation
            // 0 -> Front -> Top (-PI/2)
            // -PI/2 -> Right -> Right (0)
            // Math: screenAngle = diff - PI/2
            double screenAngle = diff - Math.PI / 2.0;

            guiGraphics.pose().pushPose();

            // 1. Move to Center
            guiGraphics.pose().translate(cx, cy, 0);

            // 2. Rotate to point to the direction
            guiGraphics.pose().mulPose(Axis.ZP.rotation((float) screenAngle));

            // 3. Move out to radius (Along +X which is now pointing to direction)
            guiGraphics.pose().translate(radius, 0, 0);

            // 4. Rotate image to point Outward
            // Assuming image points UP by default.
            // Rotating -90 (270) to align width with tangent and face center correctly
            guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(-90));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, finalAlpha);

            // Draw centered
            guiGraphics.blit(INDICATOR_TEXTURE, -width / 2, -height / 2, 0, 0, width, height, width, height);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            guiGraphics.pose().popPose();
        }
    }
}
