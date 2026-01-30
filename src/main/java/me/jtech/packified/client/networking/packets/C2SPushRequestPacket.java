package me.jtech.packified.client.networking.packets;

import me.jtech.packified.common.CommitDTO;
import me.jtech.packified.networking.CustomCodecs;
import me.jtech.packified.networking.NetworkingConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.Map;

public record C2SPushRequestPacket(String packName, List<CommitDTO> commits, Map<String, byte[]> blobs) implements CustomPayload {
    public static final Id<C2SPushRequestPacket> ID = new Id<>(NetworkingConstants.C2S_PUSH_REQUEST);
    public static final PacketCodec<RegistryByteBuf, C2SPushRequestPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, C2SPushRequestPacket::packName,
            CustomCodecs.COMMIT_DTO_LIST_CODEC, C2SPushRequestPacket::commits,
            CustomCodecs.BLOB_MAP_CODEC, C2SPushRequestPacket::blobs,
            C2SPushRequestPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
