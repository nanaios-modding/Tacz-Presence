package com.tacz.presence.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.presence.PresenceConfig;
import com.tacz.presence.TaczPresence;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class DamageOverlay implements IGuiOverlay {

    public static final DamageOverlay INSTANCE = new DamageOverlay();

    private static final ResourceLocation OVERLAY_1 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "textures/gui/overlay_1.png");
    private static final ResourceLocation OVERLAY_2 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "textures/gui/overlay_2.png");
    private static final ResourceLocation OVERLAY_3 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "textures/gui/overlay_3.png");
    private static final ResourceLocation OVERLAY_4 = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "textures/gui/overlay_4.png");

    // Damage Overlay state
    private static float displayAlpha = 0.0f;
    private static int lingerTicks = 0;
    private static final int LINGER_MAX = 60; // Duración muy larga en pantalla
    private static final float FADE_RATE = 0.008f; // Desvanecimiento muy gradual y sutil

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        float alpha = PresenceHandler.getVignetteAlpha();

        if (alpha <= 0.01f && displayAlpha <= 0.01f)
            return;

        float maxAlpha = PresenceConfig.VIGNETTE_MAX_ALPHA.get().floatValue();
        float target = Math.min(alpha, maxAlpha);

        if (target > displayAlpha) {
            // Cuando recibe daño, aumentar rápidamente
            displayAlpha = target;
            lingerTicks = LINGER_MAX;
        } else {
            if (target <= 0.01f) {
                // Linger antes de empezar a desvanecer
                if (lingerTicks > 0) {
                    lingerTicks--;
                } else {
                    // Desvanecimiento muy gradual usando interpolación suave
                    displayAlpha = displayAlpha * (1.0f - FADE_RATE);
                    if (displayAlpha < 0.01f) {
                        displayAlpha = 0.0f;
                    }
                }
            } else {
                // Transición ultra suave cuando hay algo de alpha
                displayAlpha = displayAlpha * 0.97f + target * 0.03f;
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // Calcular alpha visual para el desvanecimiento gradual (efecto de curación)
        // Usamos una curva suave para que se sienta más natural
        float visualAlpha = (float) Math.pow(displayAlpha, 0.7); // Curva para fade más suave

        // Renderizar texturas con opacidad basada en displayAlpha para efecto de
        // desvanecimiento
        if (displayAlpha > 0.01f) {
            // Primera capa siempre visible mientras haya displayAlpha
            float layer1Alpha = Math.min(1.0f, visualAlpha * 1.5f);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, layer1Alpha);
            guiGraphics.blit(OVERLAY_1, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        if (displayAlpha > 0.25f) {
            // Segunda capa aparece gradualmente
            float layer2Alpha = Math.min(1.0f, (displayAlpha - 0.25f) * 2.0f) * visualAlpha;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, layer2Alpha);
            guiGraphics.blit(OVERLAY_2, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        if (displayAlpha > 0.5f) {
            // Tercera capa para daño más alto
            float layer3Alpha = Math.min(1.0f, (displayAlpha - 0.5f) * 2.5f) * visualAlpha;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, layer3Alpha);
            guiGraphics.blit(OVERLAY_3, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        if (displayAlpha > 0.75f) {
            // Cuarta capa solo para daño crítico
            float layer4Alpha = Math.min(1.0f, (displayAlpha - 0.75f) * 4.0f) * visualAlpha;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, layer4Alpha);
            guiGraphics.blit(OVERLAY_4, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
