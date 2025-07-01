package me.jtech.packified.client.networking.packets;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record S2CRequestFullPack(String packName, UUID player) implements CustomPayload {
    public static final Id<S2CRequestFullPack> ID = new Id<>(NetworkingConstants.S2C_REQUEST_FULL_PACK);
    public static final PacketCodec<RegistryByteBuf, S2CRequestFullPack> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, S2CRequestFullPack::packName,
            Uuids.PACKET_CODEC, S2CRequestFullPack::player,
            S2CRequestFullPack::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
