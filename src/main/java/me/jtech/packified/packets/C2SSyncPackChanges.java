package me.jtech.packified.packets;

import me.jtech.packified.SyncPacketData;
import me.jtech.packified.CustomCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;
import java.util.UUID;

public record C2SSyncPackChanges(SyncPacketData packetData, List<UUID> markedPlayers) implements CustomPayload {
    public static final CustomPayload.Id<C2SSyncPackChanges> ID = new CustomPayload.Id<>(NetworkingConstants.C2S_SYNC_PACK_CHANGES);
    public static final PacketCodec<RegistryByteBuf, C2SSyncPackChanges> CODEC = PacketCodec.tuple(
            SyncPacketData.PACKET_CODEC, C2SSyncPackChanges::packetData,
            CustomCodecs.UuidCodecs.PACKET_CODEC, C2SSyncPackChanges::markedPlayers,
            C2SSyncPackChanges::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
