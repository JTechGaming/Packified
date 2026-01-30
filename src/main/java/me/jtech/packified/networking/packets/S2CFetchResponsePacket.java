package me.jtech.packified.networking.packets;

import me.jtech.packified.common.CommitDTO;
import me.jtech.packified.networking.CustomCodecs;
import me.jtech.packified.networking.NetworkingConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

public record S2CFetchResponsePacket(String headVersion, List<CommitDTO> commits) implements CustomPayload {
    public static final Id<S2CFetchResponsePacket> ID = new Id<>(NetworkingConstants.S2C_FETCH_RESPONSE);
    public static final PacketCodec<RegistryByteBuf, S2CFetchResponsePacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, S2CFetchResponsePacket::headVersion,
            CustomCodecs.COMMIT_DTO_LIST_CODEC, S2CFetchResponsePacket::commits,
            S2CFetchResponsePacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
