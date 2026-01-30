package me.jtech.packified.client.networking.packets;

import me.jtech.packified.networking.NetworkingConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record C2SInfoPacket(String info, UUID player) implements CustomPayload {
    public static final Id<C2SInfoPacket> ID = new Id<>(NetworkingConstants.C2S_INFO);
    public static final PacketCodec<RegistryByteBuf, C2SInfoPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, C2SInfoPacket::info,
            Uuids.PACKET_CODEC, C2SInfoPacket::player,
            C2SInfoPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
