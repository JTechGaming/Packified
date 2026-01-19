package me.jtech.packified.client.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import imgui.type.ImBoolean;
import me.jtech.packified.client.util.FileUtils;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//todo document this
public class VersionControlHelper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VersionControlMeta meta;
    private static final List<FileChange> fileChanges = new ArrayList<>();

    public static void init(String packName) {
        Path vcPath = getVersionControlFile(packName);

        if (!Files.exists(vcPath)) {
            try {
                Files.createDirectories(vcPath.getParent());
                meta = new VersionControlMeta(
                        packName,
                        "0.0.0",
                        new HashMap<>()
                );
                save(packName);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize version control", e);
            }
        } else {
            load(packName);
        }
    }

    private static void load(String packName) {
        try (Reader reader = Files.newBufferedReader(getVersionControlFile(packName))) {
            meta = GSON.fromJson(reader, VersionControlMeta.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load version control data", e);
        }
    }

    private static void save(String packName) {
        try {
            Files.writeString(
                    getVersionControlFile(packName),
                    GSON.toJson(meta)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to save version control data", e);
        }
    }

    private static Path getVersionControlFile(String packName) {
        return MinecraftClient.getInstance()
                .runDirectory
                .toPath()
                .resolve("config/packified/versioncontrol/" + packName + ".json");
    }

    public static void commit(String packName, String version, String message, String author) {
        Path packRoot = FileUtils.getPackFolderPath();

        Map<String, String> hashes = computeFileHashes(packRoot);

        VersionControlEntry entry = new VersionControlEntry(
                version,
                message,
                author,
                System.currentTimeMillis(),
                hashes
        );

        meta.versions().put(version, entry);
        meta = new VersionControlMeta(
                meta.packName(),
                version,
                meta.versions()
        );

        save(packName);
    }

    private static Map<String, String> computeFileHashes(Path root) {
        Map<String, String> hashes = new HashMap<>();

        //try {
            fileChanges.stream().filter(fileChange -> fileChange.include.get()).map(fileChange -> fileChange.filePath)
            //Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("meta.pkfvc"))
                    .forEach(path -> {
                        try {
                            String relative = root.relativize(path).toString();
                            hashes.put(relative, FileUtils.sha1(path));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
//        } catch (IOException e) {
//            throw new RuntimeException("Failed to compute file hashes", e);
//        }

        return hashes;
    }

    public static Map<String, ChangeType> diff(String fromVersion, String toVersion) {
        Map<String, ChangeType> result = new HashMap<>();

        Map<String, String> a = meta.versions().get(fromVersion).getFileHashes();
        Map<String, String> b = meta.versions().get(toVersion).getFileHashes();

        for (String path : a.keySet()) {
            if (!b.containsKey(path)) {
                result.put(path, ChangeType.DELETED);
            } else if (!a.get(path).equals(b.get(path))) {
                result.put(path, ChangeType.MODIFIED);
            }
        }

        for (String path : b.keySet()) {
            if (!a.containsKey(path)) {
                result.put(path, ChangeType.ADDED);
            }
        }

        return result;
    }

    public static String getCurrentVersion() {
        if (meta == null) {
            return null;
        }
        return meta.currentVersion();
    }

    public static void submitFileChange(FileChange change) {
        for (FileChange c : fileChanges) {
            if (c.getFilePath().equals(change.getFilePath())) {
                return;
            }
        }
        fileChanges.add(change);
    }

    public static List<FileChange> queryFileChanges() {
        return fileChanges;
    }

    public static void pushToRemote() {

    }

    public static void toggleAll(boolean state) {
        for (FileChange fileChange : fileChanges) {
            fileChange.getInclude().set(state);
        }
    }

    public enum ChangeType {
        ADDED(0x618333), MODIFIED(0x6496BA), DELETED(0x6B6B59);

        private final int color;

        ChangeType(int color){ this.color = color; }

        public int getColor() {
            return color;
        }

        public static ChangeType parseStandardWatchEventKinds(WatchEvent.Kind<?> eventKind) {
            if (eventKind == StandardWatchEventKinds.ENTRY_CREATE)
                return ADDED;
            if (eventKind == StandardWatchEventKinds.ENTRY_MODIFY)
                return MODIFIED;
            if (eventKind == StandardWatchEventKinds.ENTRY_DELETE)
                return DELETED;
            return null;
        }
    }

    public record VersionControlMeta(
            String packName,
            String currentVersion,
            Map<String, VersionControlEntry> versions
    ) {}

    public static class VersionControlEntry {
        private String version;
        private String description;
        private String author;
        private long timestamp;

        // Map<relativePath, sha1Hash>
        private Map<String, String> fileHashes;

        public VersionControlEntry(String version, String description, String author,
                                   long timestamp, Map<String, String> fileHashes) {
            this.version = version;
            this.description = description;
            this.author = author;
            this.timestamp = timestamp;
            this.fileHashes = fileHashes;
        }

        public Map<String, String> getFileHashes() {
            return fileHashes;
        }
    }

    public static class FileChange {
        private final Path filePath;
        private final ChangeType changeType;
        private final ImBoolean include = new ImBoolean(false);

        public FileChange(Path filePath, ChangeType changeType) {
            this.filePath = filePath;
            this.changeType = changeType;
        }

        public FileChange(Path filePath, WatchEvent.Kind<?> changeType) {
            this.filePath = filePath;
            this.changeType = ChangeType.parseStandardWatchEventKinds(changeType);
        }

        public Path getFilePath() {
            return filePath;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        public ImBoolean getInclude() {
            return include;
        }
    }
}
