package me.jtech.packified.packets;

import me.jtech.packified.SyncPacketData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record C2SSyncPackChanges(SyncPacketData packetData) implements CustomPayload {
    public static final CustomPayload.Id<C2SSyncPackChanges> ID = new CustomPayload.Id<>(NetworkingConstants.C2S_SYNC_PACK_CHANGES);
    public static final PacketCodec<RegistryByteBuf, C2SSyncPackChanges> CODEC = PacketCodec.tuple(
            SyncPacketData.PACKET_CODEC, C2SSyncPackChanges::packetData,
            C2SSyncPackChanges::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
