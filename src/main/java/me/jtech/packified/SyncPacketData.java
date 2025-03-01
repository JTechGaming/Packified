package me.jtech.packified;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record SyncPacketData(String packName, List<AssetData> assets, String metadata, boolean finalChunk) {

    public static final class AssetData {

        public static final PacketCodec<PacketByteBuf, AssetData> PACKET_CODEC = new PacketCodec<PacketByteBuf, AssetData>() {
            public AssetData decode(PacketByteBuf byteBuf) {
                Identifier identifier = byteBuf.readIdentifier();
                String extension = byteBuf.readString();
                String assetData = byteBuf.readString();
                boolean finalChunk = byteBuf.readBoolean();
                return new AssetData(identifier, extension, assetData, finalChunk);
            }

            public void encode(PacketByteBuf byteBuf, AssetData assetData) {
                byteBuf.writeIdentifier(assetData.identifier);
                byteBuf.writeString(assetData.extension);
                byteBuf.writeString(assetData.assetData);
                byteBuf.writeBoolean(assetData.finalChunk);
            }
        };


        public static final PacketCodec<PacketByteBuf, List<AssetData>> LIST_PACKET_CODEC = PACKET_CODEC.collect(PacketCodecs.toList());
        private final Identifier identifier;
        private final String extension;
        private String assetData;
        private boolean finalChunk;

        public AssetData(Identifier identifier, String extension, String assetData, boolean finalChunk) {
            this.identifier = identifier;
            this.extension = extension;
            this.assetData = assetData;
            this.finalChunk = finalChunk;
        }

        public Identifier identifier() {
            return identifier;
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
