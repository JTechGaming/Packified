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
}
