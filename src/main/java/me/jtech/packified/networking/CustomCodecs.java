package me.jtech.packified.networking;

import me.jtech.packified.common.CommitDTO;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomCodecs {
    private static final int MAX_BYTE_ARRAY_SIZE = 2097152; // Max packet size

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

    public static final PacketCodec<PacketByteBuf, List<CommitDTO>> COMMIT_DTO_LIST_CODEC = new PacketCodec<PacketByteBuf, List<CommitDTO>>() {
        public List<CommitDTO> decode(PacketByteBuf byteBuf) {
            return byteBuf.readList(COMMIT_DTO_CODEC);
        }

        public void encode(PacketByteBuf byteBuf, List<CommitDTO> assetData) {
            byteBuf.writeCollection(assetData, COMMIT_DTO_CODEC);
        }
    };

    public static final PacketCodec<PacketByteBuf, CommitDTO> COMMIT_DTO_CODEC = new PacketCodec<PacketByteBuf, CommitDTO>() {
        public CommitDTO decode(PacketByteBuf byteBuf) {
            CommitDTO commitDTO = new CommitDTO();
            commitDTO.version = byteBuf.readString();
            commitDTO.parentVersion = byteBuf.readString();
            commitDTO.author = byteBuf.readString();
            commitDTO.timestamp = byteBuf.readLong();
            commitDTO.message = byteBuf.readString();
            commitDTO.fileChanges = byteBuf.readMap(PacketCodecs.STRING, PacketCodecs.STRING);
            return commitDTO;
        }

        public void encode(PacketByteBuf byteBuf, CommitDTO commitDTO) {
            byteBuf.writeString(commitDTO.version);
            byteBuf.writeString(commitDTO.parentVersion);
            byteBuf.writeString(commitDTO.author);
            byteBuf.writeLong(commitDTO.timestamp);
            byteBuf.writeString(commitDTO.message);
            byteBuf.writeMap(commitDTO.fileChanges, PacketCodecs.STRING, PacketCodecs.STRING);
        }
    };

    public static final PacketCodec<PacketByteBuf, Map<String, byte[]>> BLOB_MAP_CODEC = new PacketCodec<PacketByteBuf, Map<String, byte[]>>() {
        public Map<String, byte[]> decode(PacketByteBuf byteBuf) {
            return byteBuf.readMap(PacketCodecs.STRING, PacketCodecs.byteArray(MAX_BYTE_ARRAY_SIZE));
        }

        public void encode(PacketByteBuf byteBuf, Map<String, byte[]> assetData) {
            byteBuf.writeMap(assetData, PacketCodecs.STRING, PacketCodecs.byteArray(MAX_BYTE_ARRAY_SIZE));
        }
    };
}
