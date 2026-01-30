package me.jtech.packified.networking.packets;

import me.jtech.packified.networking.NetworkingConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record S2CPushResponsePacket(boolean success, String newHeadVersion, String error) implements CustomPayload {
    public static final Id<S2CPushResponsePacket> ID = new Id<>(NetworkingConstants.S2C_PUSH_RESPONSE);
    public static final PacketCodec<RegistryByteBuf, S2CPushResponsePacket> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, S2CPushResponsePacket::success,
            PacketCodecs.STRING, S2CPushResponsePacket::newHeadVersion,
            PacketCodecs.STRING, S2CPushResponsePacket::error,
            S2CPushResponsePacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
