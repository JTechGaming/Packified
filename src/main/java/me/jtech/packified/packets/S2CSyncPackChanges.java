package me.jtech.packified.packets;

import me.jtech.packified.SyncPacketData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record S2CSyncPackChanges(SyncPacketData packetData, UUID player) implements CustomPayload {
    public static final Id<S2CSyncPackChanges> ID = new Id<>(NetworkingConstants.S2C_SYNC_PACK_CHANGES);
    public static final PacketCodec<RegistryByteBuf, S2CSyncPackChanges> CODEC = PacketCodec.tuple(
            SyncPacketData.PACKET_CODEC, S2CSyncPackChanges::packetData,
            Uuids.PACKET_CODEC, S2CSyncPackChanges::player,
            S2CSyncPackChanges::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}