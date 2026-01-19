package me.jtech.packified.client.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import imgui.type.ImBoolean;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.windows.LogWindow;
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
    private static final List<FileStage> fileStages = new ArrayList<>();

    public static void init(String packName) {
        Path vcPath = getVersionControlFile(packName);

        if (!Files.exists(vcPath)) {
            try {
                Files.createDirectories(vcPath.getParent());
                meta = new VersionControlMeta(
                        packName,
                        "0.0.0",
                        new HashMap<>(),
                        new ArrayList<>(),
                        new ArrayList<>()
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

            fileStages.clear();
            Path root = FileUtils.getPackFolderPath();

            for (StagingEntry entry : meta.pendingStages()) {
                fileStages.add(new FileStage(
                        root.resolve(entry.relativePath),
                        entry.stageType,
                        entry.included
                ));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load version control data", e);
        }
    }

    private static void save(String packName) {
        Path root = FileUtils.getPackFolderPath();

        List<StagingEntry> serializedStages = new ArrayList<>();
        for (FileStage fc : fileStages) {
            serializedStages.add(fc.toEntry(root));
        }

        meta = new VersionControlMeta(
                meta.packName(),
                meta.currentVersion(),
                meta.versions(),
                serializedStages,
                meta.pushedVersions()
        );

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

        for (String path : hashes.keySet()) {
            try {
                storeObject(Path.of(path), hashes.get(path));
            } catch (IOException e) {
                LogWindow.addError(e.getMessage());
            }
        }

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
                meta.versions(),
                meta.pendingStages,
                meta.pushedVersions
        );

        save(packName);

        fileStages.clear();
    }

    private static void storeObject(Path file, String hash) throws IOException {
        Path objectPath = FileUtils.getPackFolderPath()
                .resolve(".packified/objects/" + hash);

        if (!Files.exists(objectPath)) {
            Files.createDirectories(objectPath.getParent());
            Files.copy(file, objectPath);
        }
    }

    public static void rollback(String targetVersion) {
        VersionControlEntry entry = meta.versions().get(targetVersion);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown version: " + targetVersion);
        }

        Path root = FileUtils.getPackFolderPath();

        try {
            // Delete all files in pack (except vc metadata)
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains(".packified"))
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });

            // Restore files
            for (Map.Entry<String, String> e : entry.getFileHashes().entrySet()) {
                Path dest = root.resolve(e.getKey());
                Path obj = root.resolve(".packified/objects/" + e.getValue());

                Files.createDirectories(dest.getParent());
                Files.copy(obj, dest, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            throw new RuntimeException("Rollback failed", e);
        }

        fileStages.clear();

        meta = new VersionControlMeta(
                meta.packName(),
                targetVersion,
                meta.versions(),
                new ArrayList<>(),
                meta.pushedVersions()
        );

        save(meta.packName());
    }

    private static Map<String, String> computeFileHashes(Path root) {
        Map<String, String> hashes = new HashMap<>();

        //try {
            fileStages.stream().filter(fileStage -> fileStage.include.get()).map(fileStage -> fileStage.filePath)
            //Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("meta.pkfvc"))
                    .forEach(path -> {
                        try {
                            String relative = root.relativize(path).toString();
                            String hash = FileUtils.sha1(path);
                            hashes.put(relative, hash);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
//        } catch (IOException e) {
//            throw new RuntimeException("Failed to compute file hashes", e);
//        }

        return hashes;
    }

    public static Map<String, StageType> diff(String fromVersion, String toVersion) {
        Map<String, StageType> result = new HashMap<>();

        Map<String, String> a = meta.versions().get(fromVersion).getFileHashes();
        Map<String, String> b = meta.versions().get(toVersion).getFileHashes();

        for (String path : a.keySet()) {
            if (!b.containsKey(path)) {
                result.put(path, StageType.DELETED);
            } else if (!a.get(path).equals(b.get(path))) {
                result.put(path, StageType.MODIFIED);
            }
        }

        for (String path : b.keySet()) {
            if (!a.containsKey(path)) {
                result.put(path, StageType.ADDED);
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

    public static void stageFile(FileStage fileStage) {
        for (FileStage c : fileStages) {
            if (c.getFilePath().equals(fileStage.getFilePath())) {
                return;
            }
        }
        fileStages.add(fileStage);
    }

    public static List<FileStage> queryStages() {
        return fileStages;
    }

    public static void pushToRemote() {
        List<String> pushed = new ArrayList<>(meta.pushedVersions());

        for (String version : meta.versions().keySet()) {
            if (!pushed.contains(version)) {
                // TODO: actual remote push
                pushed.add(version);
            }
        }

        meta = new VersionControlMeta(
                meta.packName(),
                meta.currentVersion(),
                meta.versions(),
                meta.pendingStages(),
                pushed
        );

        save(meta.packName());
    }

    public static boolean isPushed(String version) {
        return meta.pushedVersions().contains(version);
    }

    public static void toggleAll(boolean state) {
        for (FileStage fileStage : fileStages) {
            fileStage.getInclude().set(state);
        }
    }

    public enum StageType {
        ADDED(0x618333), MODIFIED(0x6496BA), DELETED(0x6B6B59);

        private final int color;

        StageType(int color){ this.color = color; }

        public int getColor() {
            return color;
        }

        public static StageType parseStandardWatchEventKinds(WatchEvent.Kind<?> eventKind) {
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
            Map<String, VersionControlEntry> versions,
            List<StagingEntry> pendingStages,
            List<String> pushedVersions
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

    public static class FileStage {
        private final Path filePath;
        private final StageType stageType;
        private final ImBoolean include = new ImBoolean(false);

        public FileStage(Path filePath, StageType stageType, boolean include) {
            this.filePath = filePath;
            this.stageType = stageType;
            this.include.set(include);
        }

        public FileStage(Path filePath, WatchEvent.Kind<?> stageType) {
            this.filePath = filePath;
            this.stageType = StageType.parseStandardWatchEventKinds(stageType);
        }

        public StagingEntry toEntry(Path packRoot) {
            return new StagingEntry(
                    packRoot.relativize(filePath).toString(),
                    stageType,
                    include.get()
            );
        }

        public Path getFilePath() {
            return filePath;
        }

        public StageType getStageType() {
            return stageType;
        }

        public ImBoolean getInclude() {
            return include;
        }
    }

    public static class StagingEntry {
        public String relativePath;
        public StageType stageType;
        public boolean included;

        public StagingEntry(String relativePath, StageType stageType, boolean included) {
            this.relativePath = relativePath;
            this.stageType = stageType;
            this.included = included;
        }
    }
}
