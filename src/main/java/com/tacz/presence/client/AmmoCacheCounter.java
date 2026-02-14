package com.tacz.presence.client;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.presence.compat.curios_for_ammo_box.CuriosForAmmoBoxCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class AmmoCacheCounter {
    private static long checkAmmoTimestamp = -1L;
    private static int cacheInventoryAmmoCount = 0;
    private static int cacheMaxAmmoCount = 0;
    private static final int MAX_AMMO_COUNT = 9999;

    public static int getCacheInventoryAmmoCount() {
        return cacheInventoryAmmoCount;
    }
    public static int getCacheMaxAmmoCount() {
        return cacheMaxAmmoCount;
    }

    public static void handleCacheCount(LocalPlayer player, ItemStack stack, GunData gunData, IGun iGun,
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
        // Compat with Curios For Ammo Box mod, if loaded, to count ammo in curios slots as well
        if(CuriosForAmmoBoxCompat.isLoaded()) {
            inventory = CuriosForAmmoBoxCompat.transformToCuriosInventory(inventory);
        }

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
