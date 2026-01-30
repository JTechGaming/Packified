package me.jtech.packified.client.networking.packets;

import me.jtech.packified.client.util.SyncPacketData;
import me.jtech.packified.networking.NetworkingConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record C2SSendFullPack(SyncPacketData packetData, UUID player) implements CustomPayload {
    public static final Id<C2SSendFullPack> ID = new Id<>(NetworkingConstants.C2S_SEND_FULL_PACK);
    public static final PacketCodec<RegistryByteBuf, C2SSendFullPack> CODEC = PacketCodec.tuple(
            SyncPacketData.PACKET_CODEC, C2SSendFullPack::packetData,
            Uuids.PACKET_CODEC, C2SSendFullPack::player,
            C2SSendFullPack::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
