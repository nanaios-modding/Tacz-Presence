package com.tacz.presence.mixin;

import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.presence.client.ImmersiveGunHudOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GunHudOverlay.class)
public class GunHudOverlayMixin {
    private static final ImmersiveGunHudOverlay REPLACEMENT = new ImmersiveGunHudOverlay();

    /**
     * @author ImmersionMod
     * @reason Replace the original HUD with the immersive one
     */
    @Overwrite(remap = false)
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        REPLACEMENT.render(gui, graphics, partialTick, width, height);
    }
}