package com.tacz.presence.client;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.presence.PresenceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side detector for sniper glare when running in single player
 * or when the server doesn't have the mod installed.
 * This provides hybrid support - works both with and without server-side mod.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "tacz_presence")
public class ClientSideGlareDetector {

    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 5;

    // Track which players we've detected locally (vs server packets)
    private static final Set<UUID> locallyDetectedPlayers = new HashSet<>();

    // Time threshold to determine if we're receiving server packets
    private static long lastServerPacketTime = 0;
    private static final long SERVER_PACKET_TIMEOUT = 5000; // 5 seconds

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Check if glare is enabled in config
        if (!PresenceConfig.GLARE_ENABLED.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // If we're receiving server packets, don't do client-side detection
        if (isReceivingServerPackets()) {
            // Clear any locally detected players since server is handling it
            for (UUID playerId : locallyDetectedPlayers) {
                SniperGlareClientData.setPlayerAiming(playerId, false, Vec3.ZERO);
            }
            locallyDetectedPlayers.clear();
            return;
        }

        tickCounter++;
        if (tickCounter % UPDATE_INTERVAL != 0) {
            return;
        }

        LocalPlayer localPlayer = mc.player;
        Set<UUID> currentlyAimingPlayers = new HashSet<>();

        // Check all players in the world
        for (Player player : mc.level.players()) {
            // Skip local player
            if (player.getUUID().equals(localPlayer.getUUID())) {
                continue;
            }

            // Check if player is aiming with sniper scope
            if (isAimingWithSniperScope(player)) {
                currentlyAimingPlayers.add(player.getUUID());
                Vec3 lookDir = player.getLookAngle();
                SniperGlareClientData.setPlayerAiming(player.getUUID(), true, lookDir);
            }
        }

        // Remove players who stopped aiming
        for (UUID playerId : locallyDetectedPlayers) {
            if (!currentlyAimingPlayers.contains(playerId)) {
                SniperGlareClientData.setPlayerAiming(playerId, false, Vec3.ZERO);
            }
        }

        locallyDetectedPlayers.clear();
        locallyDetectedPlayers.addAll(currentlyAimingPlayers);
    }

    /**
     * Called by network packet handler to indicate we're receiving server updates.
     */
    public static void onServerPacketReceived() {
        lastServerPacketTime = System.currentTimeMillis();
    }

    /**
     * Check if we're in server-synced mode (receiving packets from server).
     */
    private static boolean isReceivingServerPackets() {
        return System.currentTimeMillis() - lastServerPacketTime < SERVER_PACKET_TIMEOUT;
    }

    private static boolean isAimingWithSniperScope(Player player) {
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        if (gunOperator == null) {
            return false;
        }

        ShooterDataHolder dataHolder = gunOperator.getDataHolder();
        if (dataHolder == null || !dataHolder.isAiming) {
            return false;
        }

        // Check aiming progress threshold
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
     * Check if a scope is specifically a HIGH-ZOOM scope.
     * Uses naming patterns since actual zoom data requires server-side API.
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
