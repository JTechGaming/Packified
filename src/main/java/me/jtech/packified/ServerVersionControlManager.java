package me.jtech.packified;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ServerVersionControlManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, RemoteRepo> REPOS = new HashMap<>();

    public static RemoteRepo getOrCreateRepo(String packName) {
        return REPOS.computeIfAbsent(packName, ServerVersionControlManager::loadRepo);
    }

    private static RemoteRepo loadRepo(String packName) {
        Path path = repoPath(packName).resolve("repo.json");

        if (Files.exists(path)) {
            try {
                return GSON.fromJson(Files.readString(path), RemoteRepo.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        RemoteRepo repo = new RemoteRepo();
        repo.packName = packName;
        repo.commits = new HashMap<>();
        repo.headVersion = null;

        saveRepo(repo);
        return repo;
    }

    public static void saveRepo(RemoteRepo repo) {
        try {
            Path dir = repoPath(repo.packName);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("repo.json"), GSON.toJson(repo));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void storeObject(String hash, byte[] data) {
        try {
            Path obj = repoObjectsPath().resolve(hash);
            if (!Files.exists(obj)) {
                Files.createDirectories(obj.getParent());
                Files.write(obj, data);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path repoPath(String packName) {
        return Path.of("packified/repos").resolve(packName);
    }

    private static Path repoObjectsPath() {
        return Path.of("packified/repos/objects");
    }

    // ===== Data types =====

    public static class RemoteRepo {
        public String packName;
        public Map<String, RemoteCommit> commits;
        public String headVersion;
    }

    public static class RemoteCommit {
        public String version;
        public String parentVersion;
        public String author;
        public long timestamp;
        public String commitMessage;
        public Map<String, String> fileHashes;

        public RemoteCommit(
                String version,
                String parentVersion,
                String author,
                long timestamp,
                String commitMessage,
                Map<String, String> fileHashes
        ) {
            this.version = version;
            this.parentVersion = parentVersion;
            this.author = author;
            this.timestamp = timestamp;
            this.commitMessage = commitMessage;
            this.fileHashes = fileHashes;
        }
    }
}

