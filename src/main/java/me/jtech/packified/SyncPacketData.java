package me.jtech.packified;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.nio.file.Path;
import java.util.List;

public record SyncPacketData(String packName, List<AssetData> assets, String metadata, boolean finalChunk) {

    public static final class AssetData {

        public static final PacketCodec<PacketByteBuf, AssetData> PACKET_CODEC = new PacketCodec<PacketByteBuf, AssetData>() {
            public AssetData decode(PacketByteBuf byteBuf) {
                Path path = Path.of(byteBuf.readString());
                String extension = byteBuf.readString();
                String assetData = byteBuf.readString();
                boolean finalChunk = byteBuf.readBoolean();
                return new AssetData(path, extension, assetData, finalChunk);
            }

            public void encode(PacketByteBuf byteBuf, AssetData assetData) {
                byteBuf.writeString(assetData.path.toString());
                byteBuf.writeString(assetData.extension);
                byteBuf.writeString(assetData.assetData);
                byteBuf.writeBoolean(assetData.finalChunk);
            }
        };


        public static final PacketCodec<PacketByteBuf, List<AssetData>> LIST_PACKET_CODEC = PACKET_CODEC.collect(PacketCodecs.toList());
        private final Path path;
        private final String extension;
        private String assetData;
        private boolean finalChunk;

        public AssetData(Path path, String extension, String assetData, boolean finalChunk) {
            this.path = path;
            this.extension = extension;
            this.assetData = assetData;
            this.finalChunk = finalChunk;
        }

        public Path path() {
            return path;
        }

        public String extension() {
            return extension;
        }

        public String assetData() {
            return assetData;
        }

        public boolean finalChunk() {
            return finalChunk;
        }

        public void setAssetData(String assetData) {
            this.assetData = assetData;
        }
    }

    public static final PacketCodec<PacketByteBuf, SyncPacketData> PACKET_CODEC = new PacketCodec<PacketByteBuf, SyncPacketData>() {
        public SyncPacketData decode(PacketByteBuf byteBuf) {
            String packName = byteBuf.readString();
            List<AssetData> assets = byteBuf.readList(AssetData.PACKET_CODEC);
            String metadata = byteBuf.readString();
            boolean finalChunk = byteBuf.readBoolean();

            return new SyncPacketData(packName, assets, metadata, finalChunk);
        }

        public void encode(PacketByteBuf byteBuf, SyncPacketData selectionData) {
            byteBuf.writeString(selectionData.packName);
            byteBuf.writeCollection(selectionData.assets, AssetData.PACKET_CODEC);
            byteBuf.writeString(selectionData.metadata);
            byteBuf.writeBoolean(selectionData.finalChunk);
        }
    };

    public static final PacketCodec<PacketByteBuf, List<SyncPacketData>> LIST_PACKET_CODEC = PACKET_CODEC.collect(PacketCodecs.toList());
}
