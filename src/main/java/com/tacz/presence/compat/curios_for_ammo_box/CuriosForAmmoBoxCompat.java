package com.tacz.presence.compat.curios_for_ammo_box;

import com.nanaios.curios_for_ammo_box.util.InventoryWithCurios;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fml.loading.LoadingModList;

public class CuriosForAmmoBoxCompat {
    public static boolean isLoaded() {
        return LoadingModList.get().getModFileById("curios_for_ammo_box") != null;
    }

    public static Inventory transformToCuriosInventory(Inventory original) {
        return new InventoryWithCurios(original);
    }
}
