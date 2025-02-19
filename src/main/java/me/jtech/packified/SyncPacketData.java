package me.jtech.packified;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import java.util.List;

public class SyncPacketData {
    private final String packName;
    private final List<AssetData> assets;
    private final String metadata;

    public SyncPacketData(String packName, List<AssetData> assets, String metadata) {
        this.packName = packName;
        this.assets = assets;
        this.metadata = metadata;
    }

    public static class AssetData {
        private final Identifier identifier;
        private final String extension;
        private final String assetData;

        public AssetData(Identifier identifier, String extension, String assetData) {
            this.identifier = identifier;
            this.extension = extension;
            this.assetData = assetData;
        }

        public Identifier getIdentifier() {
            return identifier;
        }

        public String getExtension() {
            return extension;
        }

        public String getAssetData() {
            return assetData;
        }

        public static final PacketCodec<PacketByteBuf, AssetData> PACKET_CODEC = new PacketCodec<PacketByteBuf, AssetData>() {
            public AssetData decode(PacketByteBuf byteBuf) {
                Identifier identifier = byteBuf.readIdentifier();
                String extension = byteBuf.readString();
                String assetData = byteBuf.readString();

                return new AssetData(identifier, extension, assetData);
            }

            public void encode(PacketByteBuf byteBuf, AssetData assetData) {
                byteBuf.writeIdentifier(assetData.identifier);
                byteBuf.writeString(assetData.extension);
                byteBuf.writeString(assetData.assetData);
            }
        };

        public static final PacketCodec<PacketByteBuf, List<AssetData>> LIST_PACKET_CODEC = PACKET_CODEC.collect(PacketCodecs.toList());
    }

    public static final PacketCodec<PacketByteBuf, SyncPacketData> PACKET_CODEC = new PacketCodec<PacketByteBuf, SyncPacketData>() {
        public SyncPacketData decode(PacketByteBuf byteBuf) {
            String packName = byteBuf.readString();
            List<AssetData> assets = byteBuf.readList(AssetData.PACKET_CODEC);
            String metadata = byteBuf.readString();

            return new SyncPacketData(packName, assets, metadata);
        }

        public void encode(PacketByteBuf byteBuf, SyncPacketData selectionData) {
            byteBuf.writeString(selectionData.packName);
            byteBuf.writeCollection(selectionData.assets, AssetData.PACKET_CODEC);
            byteBuf.writeString(selectionData.metadata);
        }
    };

    public static final PacketCodec<PacketByteBuf, List<SyncPacketData>> LIST_PACKET_CODEC = PACKET_CODEC.collect(PacketCodecs.toList());

    public String getPackName() {
        return packName;
    }

    public List<AssetData> getAssets() {
        return assets;
    }

    public String getMetadata() {
        return metadata;
    }
}
