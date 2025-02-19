package me.jtech.packified;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.UUID;

public class UuidList {
    public static final PacketCodec<PacketByteBuf, List<UUID>> PACKET_CODEC = new PacketCodec<PacketByteBuf, List<UUID>>() {
        public List<UUID> decode(PacketByteBuf byteBuf) {
            return byteBuf.readList(Uuids.PACKET_CODEC);
        }

        public void encode(PacketByteBuf byteBuf, List<UUID> assetData) {
            byteBuf.writeCollection(assetData, Uuids.PACKET_CODEC);
        }
    };
}
