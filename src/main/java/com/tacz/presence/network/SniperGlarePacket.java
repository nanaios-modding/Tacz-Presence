package com.tacz.presence.network;

import com.tacz.presence.client.ClientSideGlareDetector;
import com.tacz.presence.client.SniperGlareClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet sent from server to clients to sync sniper aiming state.
 * Includes the look direction so clients can determine if they're in the
 * sniper's sights.
 */
public class SniperGlarePacket {
    private final UUID playerId;
    private final boolean isAiming;
    private final Vec3 lookDirection;

    public SniperGlarePacket(UUID playerId, boolean isAiming, Vec3 lookDirection) {
        this.playerId = playerId;
        this.isAiming = isAiming;
        this.lookDirection = lookDirection;
    }

    public static void encode(SniperGlarePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerId);
        buf.writeBoolean(packet.isAiming);
        buf.writeDouble(packet.lookDirection.x);
        buf.writeDouble(packet.lookDirection.y);
        buf.writeDouble(packet.lookDirection.z);
    }

    public static SniperGlarePacket decode(FriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        boolean isAiming = buf.readBoolean();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        return new SniperGlarePacket(playerId, isAiming, new Vec3(x, y, z));
    }

    public static void handle(SniperGlarePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleClient(packet);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SniperGlarePacket packet) {
        // Notify client-side detector that we're receiving server packets
        ClientSideGlareDetector.onServerPacketReceived();

        if (packet.isAiming) {
            SniperGlareClientData.addAimingPlayer(packet.playerId, packet.lookDirection);
        } else {
            SniperGlareClientData.removeAimingPlayer(packet.playerId);
        }
    }
}
