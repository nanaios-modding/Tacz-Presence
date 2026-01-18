package com.tacz.presence.server;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.presence.TaczPresence;
import com.tacz.presence.network.PresenceNetwork;
import com.tacz.presence.network.SniperGlarePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side handler that tracks players aiming with high-zoom scopes
 * and sends network packets to sync this state to nearby clients.
 */
@Mod.EventBusSubscriber(modid = TaczPresence.MODID)
public class SniperGlareHandler {

    public static final float MIN_SNIPER_ZOOM = 3.0f;

    // Track previous aiming state
    private static final Map<UUID, Boolean> previousAimingState = new ConcurrentHashMap<>();

    // Track which weapon/scope the player was using (to detect weapon switches)
    private static final Map<UUID, String> previousWeaponId = new ConcurrentHashMap<>();

    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 3; // More frequent updates for better sync

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (event.getServer() == null) {
            return;
        }

        tickCounter++;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            checkPlayerAimingState(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UUID playerId = serverPlayer.getUUID();

            if (previousAimingState.getOrDefault(playerId, false)) {
                PresenceNetwork.sendToAll(new SniperGlarePacket(playerId, false, Vec3.ZERO));
            }

            previousAimingState.remove(playerId);
            previousWeaponId.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UUID playerId = serverPlayer.getUUID();

            // Clear glare state when player dies
            if (previousAimingState.getOrDefault(playerId, false)) {
                PresenceNetwork.sendToAll(new SniperGlarePacket(playerId, false, Vec3.ZERO));
            }

            previousAimingState.remove(playerId);
            previousWeaponId.remove(playerId);
        }
    }

    private static void checkPlayerAimingState(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // Get current weapon identifier
        ItemStack mainHandItem = player.getMainHandItem();
        String currentWeaponId = getWeaponIdentifier(mainHandItem);
        String previousWeapon = previousWeaponId.get(playerId);

        // Check if weapon changed
        boolean weaponChanged = previousWeapon != null && !previousWeapon.equals(currentWeaponId);

        // Update weapon tracking
        previousWeaponId.put(playerId, currentWeaponId);

        // If weapon changed and was aiming, force stop glare immediately
        if (weaponChanged && previousAimingState.getOrDefault(playerId, false)) {
            previousAimingState.put(playerId, false);
            PresenceNetwork.sendToTrackingPlayers(player,
                    new SniperGlarePacket(playerId, false, Vec3.ZERO));
            return;
        }

        boolean currentlyAiming = isAimingWithSniperScope(player);
        boolean wasAiming = previousAimingState.getOrDefault(playerId, false);

        Vec3 lookDirection = player.getLookAngle();

        if (currentlyAiming != wasAiming) {
            previousAimingState.put(playerId, currentlyAiming);
            PresenceNetwork.sendToTrackingPlayers(player,
                    new SniperGlarePacket(playerId, currentlyAiming, lookDirection));
        } else if (currentlyAiming && tickCounter % UPDATE_INTERVAL == 0) {
            // Send more frequent updates while aiming
            PresenceNetwork.sendToTrackingPlayers(player,
                    new SniperGlarePacket(playerId, true, lookDirection));
        }
    }

    /**
     * Get a unique identifier for the weapon to detect weapon switches.
     */
    private static String getWeaponIdentifier(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        if (stack.getItem() instanceof IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(stack);
            ResourceLocation scopeId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
            return (gunId != null ? gunId.toString() : "unknown") +
                    ":" + (scopeId != null ? scopeId.toString() : "no_scope");
        }
        return stack.getItem().toString();
    }

    private static boolean isAimingWithSniperScope(ServerPlayer player) {
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        if (gunOperator == null) {
            return false;
        }

        ShooterDataHolder dataHolder = gunOperator.getDataHolder();
        if (dataHolder == null || !dataHolder.isAiming) {
            return false;
        }

        // Also check aiming progress - must be sufficiently aimed (not just starting)
        if (dataHolder.aimingProgress < 0.8f) {
            return false;
        }

        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            return false;
        }

        ResourceLocation scopeId = iGun.getAttachmentId(mainHandItem, AttachmentType.SCOPE);

        if (scopeId == null || scopeId.getPath().isEmpty()) {
            return false;
        }

        return isHighZoomScope(scopeId);
    }

    /**
     * Check if a scope is specifically a HIGH-ZOOM scope (3x or higher).
     */
    private static boolean isHighZoomScope(ResourceLocation scopeId) {
        String path = scopeId.getPath().toLowerCase();

        // High-zoom indicators
        if (path.contains("8x") || path.contains("6x") || path.contains("4x") || path.contains("3x")) {
            return true;
        }

        if (path.contains("lpvo") || path.contains("acog") || path.contains("sniper")) {
            return true;
        }

        if (path.contains("1_6") || path.contains("1-6") || path.contains("2_10") || path.contains("3_9")) {
            return true;
        }

        // Exclude low-power optics
        if (path.contains("red_dot") || path.contains("reddot") || path.contains("holo") ||
                path.contains("reflex") || path.contains("1x") || path.contains("eotech") ||
                path.contains("kobra") || path.contains("coyote")) {
            return false;
        }

        if (path.contains("scope")) {
            return true;
        }

        return false;
    }
}
