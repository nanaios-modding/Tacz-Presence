package com.tacz.presence.network;

import com.tacz.presence.TaczPresence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel and packet registration for the Presence mod.
 */
public class PresenceNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(SniperGlarePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SniperGlarePacket::decode)
                .encoder(SniperGlarePacket::encode)
                .consumerMainThread(SniperGlarePacket::handle)
                .add();

        TaczPresence.LOGGER.info("TACZ Presence: Network packets registered");
    }

    /**
     * Send a packet to all players tracking a specific player within range.
     */
    public static void sendToTrackingPlayers(ServerPlayer target, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), packet);
    }

    /**
     * Send a packet to a specific player.
     */
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Send a packet to all players in a dimension.
     */
    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
