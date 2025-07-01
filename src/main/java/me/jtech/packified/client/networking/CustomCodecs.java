package me.jtech.packified.client.networking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Uuids;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class CustomCodecs {
    public class UuidCodecs {
        public static final PacketCodec<PacketByteBuf, List<UUID>> PACKET_CODEC = new PacketCodec<PacketByteBuf, List<UUID>>() {
            public List<UUID> decode(PacketByteBuf byteBuf) {
                return byteBuf.readList(Uuids.PACKET_CODEC);
            }

            public void encode(PacketByteBuf byteBuf, List<UUID> assetData) {
                byteBuf.writeCollection(assetData, Uuids.PACKET_CODEC);
            }
        };
    }

    public class PathCodecs {
        public static final PacketCodec<PacketByteBuf, Path> PACKET_CODEC = new PacketCodec<PacketByteBuf, Path>() {
            public Path decode(PacketByteBuf byteBuf) {
                return Path.of(byteBuf.readString());
            }

            public void encode(PacketByteBuf byteBuf, Path path) {
                byteBuf.writeString(path.toString());
            }
        };
    }
}
