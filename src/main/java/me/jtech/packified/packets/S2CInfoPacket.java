package me.jtech.packified.packets;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record S2CInfoPacket(String info, UUID player) implements CustomPayload {
    public static final Id<S2CInfoPacket> ID = new Id<>(NetworkingConstants.C2S_INFO);
    public static final PacketCodec<RegistryByteBuf, S2CInfoPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, S2CInfoPacket::info,
            Uuids.PACKET_CODEC, S2CInfoPacket::player,
            S2CInfoPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
