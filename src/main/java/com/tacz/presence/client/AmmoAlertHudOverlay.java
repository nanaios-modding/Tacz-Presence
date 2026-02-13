package com.tacz.presence.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.presence.PresenceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class AmmoAlertHudOverlay implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) return;

        ResourceLocation gunId = iGun.getGunId(stack);
        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        if (gunData == null) return;

        boolean useInventoryAmmo = iGun.useInventoryAmmo(stack);
        boolean overheatLocked = gunData.hasHeatData() && iGun.isOverheatLocked(stack);

        if (overheatLocked || useInventoryAmmo) return;

        int ammoCount = iGun.getCurrentAmmoCount(stack) + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0);

        Component alertText = null;
        int alertColor = 0xFFFFFFFF;
        boolean show = false;
        long time = System.currentTimeMillis();

        // 1. NO AMMO
        if (ammoCount <= 0) {
            if (AmmoCacheCounter.getCacheInventoryAmmoCount()  <= 0 && !gunData.getReloadData().isInfinite()) {
                if (PresenceConfig.HUD_NO_AMMO_ENABLED.get()) {
                    alertText = Component.translatable("gui.tacz.hud.no_ammo");
                    if (alertText.getString().equals("gui.tacz.hud.no_ammo")) alertText = Component.literal("NO AMMO");

                    // Pulse logic: Oscillate between 0.4 and 1.0 opacity
                    // Speed: Fixed, roughly 1 pulse per second
                    float alpha = 0.4f + 0.6f * 0.5f * (1.0f + (float) Math.sin(time / 200.0));
                    int alphaInt = (int) (alpha * 255) << 24;
                    alertColor = parseColor(PresenceConfig.HUD_NO_AMMO_COLOR.get()) | alphaInt;
                    show = true;
                }
            } else {
                // 2. RELOAD
                if (PresenceConfig.HUD_RELOAD_ENABLED.get()) {
                    alertText = Component.translatable("gui.tacz.hud.reload");
                    if (alertText.getString().equals("gui.tacz.hud.reload")) alertText = Component.literal("RELOAD");

                    float alpha = 0.4f + 0.6f * 0.5f * (1.0f + (float) Math.sin(time / 200.0));
                    int alphaInt = (int) (alpha * 255) << 24;
                    alertColor = parseColor(PresenceConfig.HUD_RELOAD_COLOR.get()) | alphaInt;
                    show = true;
                }
            }
        } else if (ammoCount < (AmmoCacheCounter.getCacheMaxAmmoCount() * 0.25) && ammoCount < 10) {
            // 3. LOW AMMO
            if (PresenceConfig.HUD_LOW_AMMO_ENABLED.get()) {
                alertText = Component.translatable("gui.tacz.hud.low_ammo");
                if (alertText.getString().equals("gui.tacz.hud.low_ammo")) alertText = Component.literal("LOW AMMO");

                float alpha = 0.4f + 0.6f * 0.5f * (1.0f + (float) Math.sin(time / 300.0));
                int alphaInt = (int) (alpha * 255) << 24;
                alertColor = parseColor(PresenceConfig.HUD_LOW_AMMO_COLOR.get()) | alphaInt;
                show = true;
            }
        }

        if (show && alertText != null) {
            Font font = mc.font;
            int alertTextWidth = font.width(alertText);

            int yOffset = PresenceConfig.HUD_ALERT_Y_OFFSET.get();
            float scale = PresenceConfig.HUD_ALERT_SCALE.get().floatValue();

            float scaledX = (width / 2.0f) / scale - (alertTextWidth / 2.0f);
            float scaledY = (height / 2.0f) / scale + (yOffset / scale);

            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.scale(scale, scale, 1);
            graphics.drawString(font, alertText, (int) scaledX, (int) scaledY, alertColor, true);
            poseStack.popPose();
        }
    }

    private static int parseColor(String hexColor) {
        try {
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }
            return Integer.parseInt(hexColor, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF; // Default white
        }
    }
}
