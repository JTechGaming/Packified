package me.jtech.packified.client.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.awt.*;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
    private static final Path CONFIG_FILE = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/packified/client_config.json");
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
}
