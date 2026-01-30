package me.jtech.packified.common;

import me.jtech.packified.ServerVersionControlManager;
import me.jtech.packified.client.helpers.VersionControlHelper;

import java.util.Map;

public class CommitDTO {
    /** For forward compatibility */
    public int protocolVersion = 1;

    /** Commit identity */
    public String version;
    public String parentVersion;

    /** Metadata */
    public String author;
    public long timestamp;
    public String message;

    /**
     * Map of relativePath → hash
     * - hash == null → deleted
     * - hash != null → added or modified
     */
    public Map<String, String> fileChanges;

    /** Empty constructor for Gson */
    public CommitDTO() {}

    public CommitDTO(
            String version,
            String parentVersion,
            String author,
            long timestamp,
            String message,
            Map<String, String> fileChanges
    ) {
        this.version = version;
        this.parentVersion = parentVersion;
        this.author = author;
        this.timestamp = timestamp;
        this.message = message;
        this.fileChanges = fileChanges;
    }

    public static CommitDTO toDTO(VersionControlHelper.VersionControlEntry entry) {
        return new CommitDTO(
                entry.getVersion(),
                entry.getParentVersion(),
                entry.getAuthor(),
                entry.getTimestamp(),
                entry.getDescription(),
                entry.getFileHashes()
        );
    }

    public static ServerVersionControlManager.RemoteCommit fromDTO(CommitDTO dto) {
        return new ServerVersionControlManager.RemoteCommit(
                dto.version,
                dto.parentVersion,
                dto.author,
                dto.timestamp,
                dto.message,
                dto.fileChanges
        );
    }
}

