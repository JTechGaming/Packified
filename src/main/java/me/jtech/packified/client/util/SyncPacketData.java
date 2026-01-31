package me.jtech.packified.client.util;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;

public record SyncPacketData(
        String packName,
        byte[] zipChunk,      // Byte array for binary data
        int chunkIndex,
        int totalChunks,
        boolean lastChunk,
        String metadata       // Only used in first chunk
) {

    public static final PacketCodec<PacketByteBuf, SyncPacketData> PACKET_CODEC = new PacketCodec<PacketByteBuf, SyncPacketData>() {
        public SyncPacketData decode(PacketByteBuf byteBuf) {
            String packName = byteBuf.readString();
            int chunkIndex = byteBuf.readInt();
            int totalChunks = byteBuf.readInt();
            boolean lastChunk = byteBuf.readBoolean();
            String metadata = byteBuf.readString();
            byte[] zipChunk = byteBuf.readByteArray();

            return new SyncPacketData(packName, zipChunk, chunkIndex, totalChunks, lastChunk, metadata);
        }

        public void encode(PacketByteBuf byteBuf, SyncPacketData data) {
            byteBuf.writeString(data.packName);
            byteBuf.writeInt(data.chunkIndex);
            byteBuf.writeInt(data.totalChunks);
            byteBuf.writeBoolean(data.lastChunk);
            byteBuf.writeString(data.metadata);
            byteBuf.writeByteArray(data.zipChunk);
        }
    };
}
