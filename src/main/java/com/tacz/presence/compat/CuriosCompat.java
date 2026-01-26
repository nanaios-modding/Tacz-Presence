package com.tacz.presence.compat;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.concurrent.atomic.AtomicInteger;

public class CuriosCompat {
    private static boolean isCuriosLoaded;
    private static boolean checked = false;

    public static boolean isCuriosLoaded() {
        if (!checked) {
            isCuriosLoaded = ModList.get().isLoaded("curios");
            checked = true;
        }
        return isCuriosLoaded;
    }

    public static int getCuriosAmmoCount(LivingEntity entity, ItemStack gunStack) {
        if (!isCuriosLoaded()) {
            return 0;
        }

        try {
            return Inner.getAmmoCount(entity, gunStack);
        } catch (Throwable e) {
            // Failsafe in case Curios API changes or crashes
            return 0;
        }
    }

    // Inner class to isolate Curios dependencies. 
    // This class will only be loaded if the method above calls it.
    private static class Inner {
        static int getAmmoCount(LivingEntity entity, ItemStack gunStack) {
            AtomicInteger ammoCount = new AtomicInteger(0);

            // Use full class names to avoid imports in the main class
            net.minecraftforge.common.util.LazyOptional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional = 
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity);
            
            optional.ifPresent(handler -> {
                java.util.Map<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> curios = handler.getCurios();

                curios.values().forEach(stacksHandler -> {
                    top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                    for (int i = 0; i < stackHandler.getSlots(); i++) {
                        ItemStack stack = stackHandler.getStackInSlot(i);
                        if (stack.isEmpty()) continue;

                        // Direct ammo check
                        if (stack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gunStack, stack)) {
                            ammoCount.addAndGet(stack.getCount());
                        }

                        // Ammo box check
                        if (stack.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gunStack, stack)) {
                             if (iAmmoBox.isAllTypeCreative(stack) || iAmmoBox.isCreative(stack)) {
                                 ammoCount.addAndGet(9999);
                             } else {
                                 ammoCount.addAndGet(iAmmoBox.getAmmoCount(stack));
                             }
                        }
                    }
                });
            });

            return ammoCount.get();
        }
    }
}
