package me.jtech.packified.client.windows;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import imgui.ImGui;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class VersionControlWindow { // todo implement version control system for packs
    private static final Gson GSON = new GsonBuilder().create();

    public static void render() {
        // Render the version control window
        if (ImGui.begin("Version Control")) {

        }
        ImGui.end();
    }

    public static void loadVersionControlData(String packName) {
        Path versionControlFile = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/packified/versioncontrol/" + packName + ".json");
        if (versionControlFile.toFile().exists()) {
            // Load the version control data from the file
            try (Reader reader = Files.newBufferedReader(versionControlFile)) {
                //GSON.fromJson();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Create the version control file if it doesn't exist
            try {
                versionControlFile.toFile().getParentFile().mkdirs();
                versionControlFile.toFile().createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class VersionControlEntry {
        private String version;
        private String description;
        private String author;
        private long timestamp;

        public VersionControlEntry(String version, String description, String author, long timestamp) {
            this.version = version;
            this.description = description;
            this.author = author;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
