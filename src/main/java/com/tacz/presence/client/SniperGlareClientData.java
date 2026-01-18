package com.tacz.presence.client;

import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side storage for tracking which players are currently aiming with
 * sniper scopes.
 * Now includes their look direction for visibility checks.
 */
public class SniperGlareClientData {
    // Map of player UUID to their look direction
    private static final Map<UUID, Vec3> aimingPlayers = new ConcurrentHashMap<>();

    /**
     * Add a player to the aiming set with their look direction.
     */
    public static void addAimingPlayer(UUID playerId, Vec3 lookDirection) {
        aimingPlayers.put(playerId, lookDirection);
    }

    /**
     * Remove a player from the aiming set.
     */
    public static void removeAimingPlayer(UUID playerId) {
        aimingPlayers.remove(playerId);
    }

    /**
     * Check if a player is currently aiming with a sniper scope.
     */
    public static boolean isPlayerAiming(UUID playerId) {
        return aimingPlayers.containsKey(playerId);
    }

    /**
     * Get the look direction of an aiming player.
     */
    public static Vec3 getLookDirection(UUID playerId) {
        return aimingPlayers.getOrDefault(playerId, Vec3.ZERO);
    }

    /**
     * Get all players currently aiming.
     */
    public static Map<UUID, Vec3> getAimingPlayers() {
        return Collections.unmodifiableMap(aimingPlayers);
    }

    /**
     * Set a player's aiming state (convenience method for client-side detector).
     */
    public static void setPlayerAiming(UUID playerId, boolean isAiming, Vec3 lookDirection) {
        if (isAiming) {
            addAimingPlayer(playerId, lookDirection);
        } else {
            removeAimingPlayer(playerId);
        }
    }

    /**
     * Clear all aiming data.
     */
    public static void clear() {
        aimingPlayers.clear();
    }
}
