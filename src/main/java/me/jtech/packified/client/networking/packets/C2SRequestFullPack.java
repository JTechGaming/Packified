package me.jtech.packified.client.networking.packets;

import me.jtech.packified.networking.NetworkingConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record C2SRequestFullPack(String packName, UUID player) implements CustomPayload {
    public static final Id<C2SRequestFullPack> ID = new Id<>(NetworkingConstants.C2S_REQUEST_FULL_PACK);
    public static final PacketCodec<RegistryByteBuf, C2SRequestFullPack> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, C2SRequestFullPack::packName,
            Uuids.PACKET_CODEC, C2SRequestFullPack::player,
            C2SRequestFullPack::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
