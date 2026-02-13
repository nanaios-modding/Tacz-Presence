package com.tacz.presence.mixin;

import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.presence.PresenceConfig;
import com.tacz.presence.client.ImmersiveGunHudOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunHudOverlay.class,remap = false)
public class GunHudOverlayMixin {
    @Unique
    private static final ImmersiveGunHudOverlay tacz_Presence$REPLACEMENT = new ImmersiveGunHudOverlay();

    /**
     * Inject at the head of render method to conditionally replace with custom HUD.
     * If config is set to TACZ_P_HUD, use custom HUD and cancel original.
     * If config is set to TACZ_HUD, do nothing and let original run.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void onRender(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height,
            CallbackInfo ci) {
        if (PresenceConfig.GUN_HUD_TYPE.get() == PresenceConfig.HudType.TACZ_P_HUD) {
            tacz_Presence$REPLACEMENT.render(gui, graphics, partialTick, width, height);
            ci.cancel();
        }
    }
}