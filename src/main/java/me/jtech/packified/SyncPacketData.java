package me.jtech.packified;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncPacketData(String packName, List<AssetData> assets, String metadata, int chunks, int chunkIndex) {

    public record AssetData(Identifier identifier, String extension, String assetData) {

        public List<String> splitAssetData(int chunkSize) {
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < assetData.length(); i += chunkSize) {
                chunks.add(assetData.substring(i, Math.min(assetData.length(), i + chunkSize)));
            }
            return chunks;
        }


        public static final PacketCodec<PacketByteBuf, AssetData> PACKET_CODEC = new PacketCodec<PacketByteBuf, AssetData>() {
            public AssetData decode(PacketByteBuf byteBuf) {
                Identifier identifier = byteBuf.readIdentifier();
                String extension = byteBuf.readString();
                int chunkCount = byteBuf.readInt();
                StringBuilder assetDataBuilder = new StringBuilder();
                for (int i = 0; i < chunkCount; i++) {
                    assetDataBuilder.append(byteBuf.readString());
                }
                String assetData = assetDataBuilder.toString();
                return new AssetData(identifier, extension, assetData);
            }

            public void encode(PacketByteBuf byteBuf, AssetData assetData) {
                byteBuf.writeIdentifier(assetData.identifier);
                byteBuf.writeString(assetData.extension.substring(0, Math.min(assetData.extension.length(), 32767)));
                //List<String> chunks = assetData.splitAssetData(32767);
                List<String> chunks = assetData.splitAssetData(8192);
                byteBuf.writeInt(chunks.size());
                for (String chunk : chunks) {
                    byteBuf.writeString(chunk);
                }
            }
        };

        public static final PacketCodec<PacketByteBuf, List<AssetData>> LIST_PACKET_CODEC = PACKET_CODEC.collect(PacketCodecs.toList());
    }
// TODO make this be sending the data in multiple packets instead of one big packet with multiple strings
    public static final PacketCodec<PacketByteBuf, SyncPacketData> PACKET_CODEC = new PacketCodec<PacketByteBuf, SyncPacketData>() {
        public SyncPacketData decode(PacketByteBuf byteBuf) {
            String packName = byteBuf.readString();
            List<AssetData> assets = byteBuf.readList(AssetData.PACKET_CODEC);
            String metadata = byteBuf.readString();
            int chunks = byteBuf.readInt();
            int chunkIndex = byteBuf.readInt();

            return new SyncPacketData(packName, assets, metadata, chunks, chunkIndex);
        }

        public void encode(PacketByteBuf byteBuf, SyncPacketData selectionData) {
            byteBuf.writeString(selectionData.packName);
            byteBuf.writeCollection(selectionData.assets, AssetData.PACKET_CODEC);
            byteBuf.writeString(selectionData.metadata);
            byteBuf.writeInt(selectionData.chunks);
            byteBuf.writeInt(selectionData.chunkIndex);
        }
    };

    public static final PacketCodec<PacketByteBuf, List<SyncPacketData>> LIST_PACKET_CODEC = PACKET_CODEC.collect(PacketCodecs.toList());
}
