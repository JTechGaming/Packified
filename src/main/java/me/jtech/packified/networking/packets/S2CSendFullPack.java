package me.jtech.packified.networking.packets;

import me.jtech.packified.client.util.SyncPacketData;
import me.jtech.packified.networking.NetworkingConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record S2CSendFullPack(SyncPacketData packetData) implements CustomPayload {
    public static final Id<S2CSendFullPack> ID = new Id<>(NetworkingConstants.S2C_SEND_FULL_PACK);
    public static final PacketCodec<RegistryByteBuf, S2CSendFullPack> CODEC = PacketCodec.tuple(
            SyncPacketData.PACKET_CODEC, S2CSendFullPack::packetData,
            S2CSendFullPack::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
