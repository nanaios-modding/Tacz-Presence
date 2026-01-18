package com.tacz.presence.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.presence.PresenceConfig;
import com.tacz.presence.TaczPresence;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class DamageOverlay implements IGuiOverlay {
    
    public static final DamageOverlay INSTANCE = new DamageOverlay();

    private static final ResourceLocation OVERLAY_1 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID, "textures/gui/overlay_1.png");
    private static final ResourceLocation OVERLAY_2 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID, "textures/gui/overlay_2.png");
    private static final ResourceLocation OVERLAY_3 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID, "textures/gui/overlay_3.png");
    private static final ResourceLocation OVERLAY_4 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID, "textures/gui/overlay_4.png");
    private static float displayAlpha = 0.0f;
    private static int lingerTicks = 0;
    private static final int LINGER_MAX = 12;

    // Ya no usamos textura de viñeta para evitar el fondo oscuro.
    // Usaremos renderizado de vértices para crear un gradiente rojo radial.

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        float alpha = PresenceHandler.getVignetteAlpha();
        if (alpha <= 0.01f && displayAlpha <= 0.01f)
            return;

        float maxAlpha = PresenceConfig.VIGNETTE_MAX_ALPHA.get().floatValue();
        float target = Math.min(alpha, maxAlpha);
        if (target > displayAlpha) {
            displayAlpha = target;
            lingerTicks = LINGER_MAX;
        } else {
            if (target <= 0.01f) {
                if (lingerTicks > 0) {
                    lingerTicks--;
                } else {
                    displayAlpha = Math.max(0.0f, displayAlpha - 0.05f);
                }
            } else {
                displayAlpha = displayAlpha * 0.85f + target * 0.15f;
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        float alpha1 = Math.min(1.0f, displayAlpha * 1.3f);
        if (alpha1 > 0) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha1);
            guiGraphics.blit(OVERLAY_1, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        float alpha2 = Math.max(0.0f, (displayAlpha - 0.25f) * 1.3f);
        if (alpha2 > 0) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Math.min(1.0f, alpha2));
            guiGraphics.blit(OVERLAY_2, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        float alpha3 = Math.max(0.0f, (displayAlpha - 0.5f) * 2.5f);
        alpha3 *= 0.6f;
        if (alpha3 > 0) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Math.min(1.0f, alpha3));
            guiGraphics.blit(OVERLAY_3, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        float alpha4 = Math.max(0.0f, (displayAlpha - 0.75f) * 3.0f);
        alpha4 *= 0.4f;
        if (alpha4 > 0) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Math.min(1.0f, alpha4));
            guiGraphics.blit(OVERLAY_4, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        float radialAlpha = displayAlpha * 0.25f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        
        float red = 0.5f;
        float green = 0.0f;
        float blue = 0.0f;
        
        float minBorder = 0.05f; 
        float maxBorder = 0.90f; 
        
        float borderProgress = (float) Math.pow(Math.min(1.0f, alpha), 0.8);
        float currentBorderScale = minBorder + (maxBorder - minBorder) * borderProgress;
        
        double centerX = screenWidth / 2.0;
        double centerY = screenHeight / 2.0;
        
        double maxRadiusX = screenWidth * 0.8; 
        double maxRadiusY = screenHeight * 0.9;
        
        double baseRadiusX = screenWidth * 0.55; 
        double baseRadiusY = screenHeight * 0.55;
        
        double innerRadiusX = baseRadiusX * (1.0 - currentBorderScale);
        double innerRadiusY = baseRadiusY * (1.0 - currentBorderScale);

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        
        bufferbuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        
        int segments = 52; 
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            
            bufferbuilder.vertex(centerX + innerRadiusX * cos, centerY + innerRadiusY * sin, 0.0)
                         .color(red, green, blue, 0.0f)
                         .endVertex();
            
            bufferbuilder.vertex(centerX + maxRadiusX * cos, centerY + maxRadiusY * sin, 0.0)
                         .color(red, green, blue, radialAlpha)
                         .endVertex();
        }
        
        tesselator.end();
        
        
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
