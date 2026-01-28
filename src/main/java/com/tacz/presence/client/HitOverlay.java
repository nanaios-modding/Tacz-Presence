package com.tacz.presence.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Hit Overlay - Efecto visual de impacto con bordes radiales rojos oscuros
 * en los lados izquierdo y derecho que aparecen con animación sutil de slide +
 * escala
 */
public class HitOverlay implements IGuiOverlay {

    public static final HitOverlay INSTANCE = new HitOverlay();

    // Estado de la animación
    private static float intensity = 0.0f; // Intensidad general (alpha)
    private static float slideProgress = 0.0f; // Progreso del slide (0 = fuera, 1 = dentro)
    private static float scaleProgress = 0.0f; // Progreso de la escala

    // Velocidades de animación - más sutiles
    private static final float SLIDE_IN_SPEED = 0.12f; // Velocidad de entrada (sutil)
    private static final float SLIDE_OUT_SPEED = 0.04f; // Velocidad de salida (más lento y suave)
    private static final float SCALE_SPEED = 0.1f; // Velocidad de escala (sutil)
    private static final float FADE_SPEED = 0.03f; // Velocidad de desvanecimiento (muy suave)

    // Fase de la animación
    private static boolean isEntering = false;
    private static boolean isExiting = false;

    /**
     * Activa el efecto Hit Overlay cuando el jugador recibe un impacto
     */
    public static void trigger() {
        intensity = 1.0f;
        slideProgress = 0.0f;
        scaleProgress = 0.0f;
        isEntering = true;
        isExiting = false;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (intensity <= 0.01f && slideProgress <= 0.01f) {
            return;
        }

        // Actualizar animación
        updateAnimation();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        // Renderizar bordes radiales en izquierda y derecha
        renderSideBorders(screenWidth, screenHeight);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void updateAnimation() {
        if (isEntering) {
            // Fase de entrada: slide hacia adentro y escalar (suave con easing)
            slideProgress = Math.min(1.0f, slideProgress + SLIDE_IN_SPEED * (1.0f - slideProgress * 0.5f));
            scaleProgress = Math.min(1.0f, scaleProgress + SCALE_SPEED * (1.0f - scaleProgress * 0.5f));

            // Cuando llega al máximo, comenzar a salir
            if (slideProgress >= 0.85f && scaleProgress >= 0.75f) {
                isEntering = false;
                isExiting = true;
            }
        } else if (isExiting) {
            // Fase de salida: desvanecer y hacer slide hacia afuera (muy suave)
            intensity = Math.max(0.0f, intensity - FADE_SPEED);
            slideProgress = Math.max(0.0f, slideProgress - SLIDE_OUT_SPEED);
            scaleProgress = Math.max(0.0f, scaleProgress - SLIDE_OUT_SPEED * 0.3f);

            if (intensity <= 0.01f) {
                isExiting = false;
                slideProgress = 0.0f;
                scaleProgress = 0.0f;
            }
        }
    }

    private void renderSideBorders(int screenWidth, int screenHeight) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // Colores rojos más oscuros para el efecto de impacto
        float red = 0.5f;
        float green = 0.02f;
        float blue = 0.02f;

        // Alpha basado en intensidad y slide progress - opacidad 0.8
        // Usamos easing cuadrático para transición más suave
        float easedIntensity = intensity * intensity; // Easing suave
        float alpha = easedIntensity * slideProgress * 0.8f;

        // Dimensiones del borde
        float borderWidth = screenWidth * 0.15f * (0.6f + scaleProgress * 0.4f); // Ancho del borde con escala
        float slideOffset = borderWidth * (1.0f - slideProgress); // Offset del slide

        // Curva del borde radial - más pronunciada
        int segments = 40;
        float curveDepth = screenWidth * 0.12f * scaleProgress; // Profundidad de la curva radial

        // === BORDE IZQUIERDO ===
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float y = t * screenHeight;

            // Curva sinusoidal más pronunciada para el borde radial
            float curve = (float) Math.sin(t * Math.PI) * curveDepth;
            // Añadir segunda armónica para forma más radial
            curve += (float) Math.sin(t * Math.PI * 2) * curveDepth * 0.15f;

            // Borde exterior (visible, con slide)
            float outerX = -slideOffset;
            // Borde interior (gradiente hacia transparente)
            float innerX = borderWidth - slideOffset + curve;

            // Vértice exterior (con color)
            buffer.vertex(outerX, y, 0.0)
                    .color(red, green, blue, alpha)
                    .endVertex();

            // Vértice interior (transparente con gradiente)
            buffer.vertex(innerX, y, 0.0)
                    .color(red, green, blue, 0.0f)
                    .endVertex();
        }
        tesselator.end();

        // === BORDE DERECHO ===
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float y = t * screenHeight;

            // Curva sinusoidal más pronunciada para el borde radial (espejado)
            float curve = (float) Math.sin(t * Math.PI) * curveDepth;
            // Añadir segunda armónica para forma más radial
            curve += (float) Math.sin(t * Math.PI * 2) * curveDepth * 0.15f;

            // Borde interior (gradiente hacia transparente)
            float innerX = screenWidth - borderWidth + slideOffset - curve;
            // Borde exterior (visible, con slide)
            float outerX = screenWidth + slideOffset;

            // Vértice interior (transparente con gradiente)
            buffer.vertex(innerX, y, 0.0)
                    .color(red, green, blue, 0.0f)
                    .endVertex();

            // Vértice exterior (con color)
            buffer.vertex(outerX, y, 0.0)
                    .color(red, green, blue, alpha)
                    .endVertex();
        }
        tesselator.end();
    }
}
