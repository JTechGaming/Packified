package me.jtech.packified.client.config;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ModConfig {
    private static final Path CONFIG_FILE = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/packified/client_config.json");
    private static final Path DOCK_FILE = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/packified/dock_config.json");
    private static final Gson GSON = new GsonBuilder().create();

    public static void updateSettings(Map<String, Object> changedSettings) {
        if (!Files.exists(CONFIG_FILE)) {
            CONFIG_FILE.getParent().toFile().mkdirs(); // Ensure the directory exists
            try {
                Files.createFile(CONFIG_FILE); // Create the file if it doesn't exist
            } catch (IOException e) {
                throw new RuntimeException("Failed to create config file", e);
            }
        }
        Map<String, Object> map;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, Object>>() {
            }.getType();
            map = GSON.fromJson(reader, type); // Load the existing map
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file", e);
        }
        if (map == null) {
            map = new HashMap<>();
        }
        map.putAll(changedSettings);
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(map, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write config file", e);
        }
    }

    private static Map<String, Object> getSettings() {
        if (!Files.exists(CONFIG_FILE)) {
            CONFIG_FILE.getParent().toFile().mkdirs(); // Ensure the directory exists
            try {
                Files.createFile(CONFIG_FILE); // Create the file if it doesn't exist
            } catch (IOException e) {
                throw new RuntimeException("Failed to create config file", e);
            }
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> map = GSON.fromJson(reader, type);
            if (map == null) {
                map = new HashMap<>();
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file", e);
        }
    }

    public static int getInt(String key, int defaultValue) {
        Map<String, Object> settings = getSettings();
        return settings.containsKey(key) ? ((Number) settings.get(key)).intValue() : defaultValue;
    }

    public static float getFloat(String key, float defaultValue) {
        Map<String, Object> settings = getSettings();
        return settings.containsKey(key) ? ((Number) settings.get(key)).floatValue() : defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        Map<String, Object> settings = getSettings();
        return settings.containsKey(key) ? (boolean) settings.get(key) : defaultValue;
    }

    public static String getString(String key, String defaultValue) {
        Map<String, Object> settings = getSettings();
        return settings.containsKey(key) ? (String) settings.get(key) : defaultValue;
    }

    public static DockConfig getDockConfig() {
        if (!Files.exists(DOCK_FILE)) {
            DOCK_FILE.getParent().toFile().mkdirs(); // Ensure the directory exists
            try {
                Files.createFile(DOCK_FILE); // Create the file if it doesn't exist
            } catch (IOException e) {
                throw new RuntimeException("Failed to create dock config file", e);
            }
        }
        try (Reader reader = Files.newBufferedReader(DOCK_FILE)) {
            DockConfig config;
            try {
                 config = GSON.fromJson(reader, DockConfig.class);
            } catch (JsonSyntaxException e) {
                throw new RuntimeException("FAILED TO PARSE DOCKCONFIG: This is likely because you loaded a pack with a name containing special characters! " + e.getMessage());
            }
            if (config == null) {
                config = new DockConfig();
            }
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read dock config file", e);
        }
    }

    public static void savePackStatus(ResourcePackProfile pack) {
        DockConfig config = getDockConfig();
        if (pack == null) {
            config.lastOpenedPack = null;
        } else {
            config.lastOpenedPack = pack.getDisplayName().getString();
        }
        saveDockConfig(config);
    }

    public static void saveDockConfig(DockConfig config) {
        try (Writer writer = Files.newBufferedWriter(DOCK_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write dock config file", e);
        }
    }

    public static class DockConfig {
        public Map<Integer, FileExplorerInfo> explorers = new HashMap<>();
        public String layout;
        public String lastOpenedPack;
    }

    public static class FileExplorerInfo {
        public String lastDirectory;
        public boolean isOpen;
    }
}
