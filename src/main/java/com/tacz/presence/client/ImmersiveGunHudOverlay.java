package com.tacz.presence.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.presence.TaczPresence;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.List;

public class ImmersiveGunHudOverlay implements IGuiOverlay {
    private static final ResourceLocation SEMI = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID, "textures/hud/fire_mode_semi.png");
    private static final ResourceLocation AUTO = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID, "textures/hud/fire_mode_auto.png");
    private static final ResourceLocation BURST = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "textures/hud/fire_mode_burst.png");

    private static final AmmoAlertHudOverlay AMMO_ALERT = new AmmoAlertHudOverlay();

    private static final DecimalFormat CURRENT_AMMO_FORMAT = new DecimalFormat("00");
    private static final DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new DecimalFormat("00%");
    private static final DecimalFormat INVENTORY_AMMO_FORMAT = new DecimalFormat("00");
    private static long checkAmmoTimestamp = -1L;
    private static int cacheMaxAmmoCount = 0;
    private static int cacheInventoryAmmoCount = 0;

    private static final int MAX_AMMO_COUNT = 9999;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!RenderConfig.GUN_HUD_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(stack);

        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null || display == null) {
            return;
        }

        // 是否背包直读
        boolean useInventoryAmmo = iGun.useInventoryAmmo(stack);
        // 是否使用虚拟备弹
        boolean useDummyAmmo = iGun.useDummyAmmo(stack);
        // 是否完全过热
        boolean overheatLocked = gunData.hasHeatData() && iGun.isOverheatLocked(stack);
        // 当前枪械弹药数
        int ammoCount = useInventoryAmmo
                ? cacheInventoryAmmoCount
                        + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0)
                : iGun.getCurrentAmmoCount(stack)
                        + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0);
        ammoCount = Math.min(ammoCount, MAX_AMMO_COUNT);
        // 弹药颜色
        int ammoCountColor;
        if (ammoCount < (cacheMaxAmmoCount * 0.25) && ammoCount < 10 || overheatLocked) {
            // 红色
            ammoCountColor = 0xFF5555;
        } else {
            // 如果背包直读并且使用虚拟备弹为青色，否则背包直读为黄色，其他为白色
            ammoCountColor = useInventoryAmmo && useDummyAmmo ? 0x55FFFF : useInventoryAmmo ? 0xFFFF55 : 0xFFFFFF;
        }
        // 备弹颜色
        int inventoryAmmoCountColor;
        if (!useInventoryAmmo && useDummyAmmo) {
            inventoryAmmoCountColor = 0x55FFFF;
        } else {
            inventoryAmmoCountColor = 0xAAAAAA;
        }

        // 当前枪械弹药数显示
        String currentAmmoCountText;
        if (ammoCount >= MAX_AMMO_COUNT) {
            currentAmmoCountText = "∞";
        } else if (display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
            // 百分比模式
            currentAmmoCountText = CURRENT_AMMO_FORMAT_PERCENT
                    .format((float) ammoCount / (cacheMaxAmmoCount == 0 ? 1f : cacheMaxAmmoCount));
        } else {
            // 普通模式
            currentAmmoCountText = CURRENT_AMMO_FORMAT.format(ammoCount);
        }

        // 备弹数显示 (背包直读模式不显示备弹)
        String inventoryAmmoCountText = useInventoryAmmo ? "" : INVENTORY_AMMO_FORMAT.format(cacheInventoryAmmoCount);
        if (!useInventoryAmmo && (gunData.getReloadData().isInfinite() || cacheInventoryAmmoCount >= 9999)) {
            inventoryAmmoCountText = "∞";
        }

        // 计算弹药数
        handleCacheCount(player, stack, gunData, iGun, useInventoryAmmo);

        PoseStack poseStack = graphics.pose();
        Font font = mc.font;

        // --- RENDER GUN HUD (NEW LAYOUT) ---
        // Right alignment logic
        int rightMargin = 15; // Increased margin as requested (was 10)
        int hudBaseX = width - rightMargin;
        // Adjusted Y position: User wants it lower, but with padding.
        // Let's set the "base" (which is roughly the ammo count text line)
        // such that the bottom element (reserve ammo) is at height - 35.
        // Reserve ammo is drawn at hudBaseY + 16 approx.
        // So hudBaseY + 16 = height - 35 => hudBaseY = height - 51.
        // Let's try height - 45 to be safe and slightly lower.
        int hudBaseY = height - 35;

        // Calculate text widths for alignment
        int ammoTextWidth = font.width(currentAmmoCountText);

        // 3. Render Gun Icon
        // Position to the left of the biggest text block (usually ammo count)
        // Icon size: Scaled 1.5x
        // Adjust spacing: Increased to 65 to accommodate 3-digit ammo counts (like
        // "148") without overlapping
        // User request: "que tenga cierto margen entre la imagen y todo lo demás del contenido"
        // User update: "un poco menos de espaciado estaría perfecto." (Reduced from 60 to 48)
        int gunIconWidth = 58; // 1.5x scale width
        int gunIconX = hudBaseX - 38 - gunIconWidth; // Reduced margin (was 60)
        int gunIconY = hudBaseY - 6; // Adjusted Y for centering

        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ResourceLocation hudTexture = display.getHUDTexture();
        @Nullable
        ResourceLocation hudEmptyTexture = display.getHudEmptyTexture();

        if (ammoCount <= 0 || overheatLocked) {
            if (hudEmptyTexture == null) {
                RenderSystem.setShaderColor(1, 0.3f, 0.3f, 1);
            } else {
                hudTexture = hudEmptyTexture;
            }
        } else {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }

        // Draw icon with scale
        poseStack.pushPose();
        poseStack.translate(gunIconX, gunIconY, 0);
        poseStack.scale(1.5f, 1.5f, 1); // Reduced scale to 1.5x
        graphics.blit(hudTexture, 0, 0, 0, 0, 39, 13, 39, 13);
        poseStack.popPose();

        RenderSystem.setShaderColor(1, 1, 1, 1); // Reset

        // 1. Render Ammo Count (Big)
        poseStack.pushPose();
        float ammoScale = 1.55f; // Reduced from 2.0f
        poseStack.scale(ammoScale, ammoScale, 1);
        // Align right edge of text to hudBaseX
        float textX = (hudBaseX - (ammoTextWidth * ammoScale)) / ammoScale;
        float textY = (hudBaseY - 5) / ammoScale; // Adjusted Y

        // Simple shadow (default dropShadow=true handles it)
        graphics.drawString(font, currentAmmoCountText, textX, textY, ammoCountColor, true);
        poseStack.popPose();

        // 2. Render Reserve Ammo (Small)
        // Right align to hudBaseX
        int reserveTextWidth = font.width(inventoryAmmoCountText);
        poseStack.pushPose();
        float reserveScale = 1.0f;
        poseStack.scale(reserveScale, reserveScale, 1);
        float reserveX = (hudBaseX - (reserveTextWidth * reserveScale)) / reserveScale;
        float reserveY = (hudBaseY + 10) / reserveScale; // Adjusted Y
        graphics.drawString(font, inventoryAmmoCountText, reserveX, reserveY, inventoryAmmoCountColor, true);
        poseStack.popPose();

        // 4. Render Fire Modes (All available)
        List<FireMode> supportedModes = gunData.getFireModeSet();
        FireMode currentFireMode = IGun.getMainHandFireMode(player);

        // Hide if only 1 mode, but logic preserves space implicitly
        if (supportedModes.size() > 1) {
            // Position: Below gun icon, centered relative to gun, shifted right
            int gunCenter = gunIconX + (gunIconWidth / 2);
            int modeStartX = gunCenter;
            
            int modeYPos = gunIconY + 20; // Below gun icon
            modeYPos += 5; // Extra margin

            for (FireMode mode : supportedModes) {
                ResourceLocation modeTexture;
                switch (mode) {
                    case AUTO -> modeTexture = AUTO;
                    case BURST -> modeTexture = BURST;
                    case SEMI -> modeTexture = SEMI;
                    default -> {
                        continue;
                    }
                }

                boolean isActive = (mode == currentFireMode);

                if (isActive) {
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                } else {
                    RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 0.5f); // Dimmed
                }

                graphics.blit(modeTexture, modeStartX, modeYPos, 0, 0, 10, 10, 10, 10);

                if (isActive) {
                    // Highlight line
                    graphics.fill(modeStartX, modeYPos + 11, modeStartX + 10, modeYPos + 12, 0xFFFFFFFF);
                }

                modeStartX += 15; // Spacing
            }
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);

        // 5. Render Ammo Alerts (Reload, No Ammo, Low Ammo)
        AMMO_ALERT.render(gui, graphics, partialTick, width, height);
    }

    private static void handleCacheCount(LocalPlayer player, ItemStack stack, GunData gunData, IGun iGun,
            boolean useInventoryAmmo) {
        // Check every 50ms (1 tick)
        if ((System.currentTimeMillis() - checkAmmoTimestamp) > 50) {
            checkAmmoTimestamp = System.currentTimeMillis();
            // 当前枪械的总弹药数
            cacheMaxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData);
            // 玩家背包弹药数
            if (IGunOperator.fromLivingEntity(player).needCheckAmmo()) {
                if (iGun.useDummyAmmo(stack)) {
                    // 缓存虚拟弹药数
                    cacheInventoryAmmoCount = iGun.getDummyAmmoAmount(stack);
                } else {
                    // 缓存背包内的弹药数
                    handleInventoryAmmo(stack, player.getInventory());
                }
            } else {
                cacheInventoryAmmoCount = MAX_AMMO_COUNT;
            }
            if (useInventoryAmmo) {
                iGun.setCurrentAmmoCount(stack, cacheInventoryAmmoCount);
            }
        }
    }

    private static void handleInventoryAmmo(ItemStack stack, Inventory inventory) {
        cacheInventoryAmmoCount = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack inventoryItem = inventory.getItem(i);
            if (inventoryItem.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(stack, inventoryItem)) {
                cacheInventoryAmmoCount += inventoryItem.getCount();
            }
            if (inventoryItem.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(stack, inventoryItem)) {
                // 创造模式弹药箱？直接返回 9999
                if (iAmmoBox.isAllTypeCreative(inventoryItem) || iAmmoBox.isCreative(inventoryItem)) {
                    cacheInventoryAmmoCount = 9999;
                    return;
                }
                cacheInventoryAmmoCount += iAmmoBox.getAmmoCount(inventoryItem);
            }
        }
    }
}
