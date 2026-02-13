package com.tacz.presence.compat.curios_for_ammo_box;

import com.nanaios.curios_for_ammo_box.util.InventoryWithCurios;
import com.tacz.presence.client.AmmoCacheCounter;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = AmmoCacheCounter.class, remap = false)
public class MixinAmmoCacheCounter {
    @ModifyVariable(method = "handleInventoryAmmo", at = @At("HEAD"), argsOnly = true)
    private static Inventory tacz_Presence$handleInventoryAmmo(Inventory inventory) {
        return new InventoryWithCurios(inventory);
    }
}
