package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.extension.texteditor.flag.TextEditorPaletteIndex;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.imgui.ImguiThemes;
import me.jtech.packified.client.util.ModConfig;

import java.util.Map;

public class PreferencesWindow {
    public static ImBoolean stayInCreative = new ImBoolean((boolean) ModConfig.getSettings().getOrDefault("stayincreateive", false));
    public static ImInt maxBackupCount = new ImInt((int) ModConfig.getSettings().getOrDefault("maxBackupCount", 10)); // Maximum number of backups to keep

    public static boolean isOpen = false;

    public static void render() {
        if (!isOpen) {
            return; // If the window is not open, do not render
        }
        // Render the settings window
        if (ImGui.begin("Settings", ImGuiWindowFlags.NoResize| ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse)) {
            if (ImGui.collapsingHeader("General Settings")) {
                if (ImGui.checkbox("Stay In Creative: ", stayInCreative)) {
                    ModConfig.updateSettings(Map.of("stayincreative", stayInCreative.get()));
                    PackifiedClient.changeGameMode(PackifiedClient.getPreviousGameMode());
                }
                if (ImGui.inputInt("Max backups" , maxBackupCount)) {
                    ModConfig.updateSettings(Map.of("maxBackupCount", maxBackupCount.get()));
                }
            }
            if (ImGui.collapsingHeader("Appearance & Behavior")) {
                if (ImGui.combo("Theme", ImguiThemes.getCurrentTheme(), ImguiThemes.getAvailableThemes())) {
                    ImguiThemes.setTheme(ImguiThemes.getCurrentTheme());
                }
            }
            if (ImGui.collapsingHeader("Editor")) {
                renderTextEditorColorSettings();
            }

            // Close button in bottom right
            ImGui.setCursorPosY(ImGui.getWindowHeight() - ImGui.getFrameHeightWithSpacing());
            ImGui.setCursorPosX(ImGui.getWindowWidth() - ImGui.getFrameHeightWithSpacing() - ImGui.getStyle().getItemSpacingX() - ImGui.getStyle().getWindowMinSizeX());
            if (ImGui.button("Close")) {
                isOpen = false; // Close the settings window
            }
        }
        ImGui.end();
    }

    private static final float[][] defaultPalette = new float[TextEditorPaletteIndex.Max][4];

    private static void initDefaultPalette() {
        defaultPalette[TextEditorPaletteIndex.Default] = new float[]{0.731f, 0.975f, 0.590f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.LineNumber] = new float[]{0.541f, 0.541f, 0.541f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.Punctuation] = new float[]{0.969f, 0.616f, 0.427f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.Selection] = new float[]{0.345f, 0.345f, 0.345f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.CurrentLineFill] = new float[]{0.063f, 0.063f, 0.063f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.CurrentLineEdge] = new float[]{0.498f, 0.624f, 0.498f, 0.5f};
        defaultPalette[TextEditorPaletteIndex.Keyword] = new float[]{0.863f, 0.863f, 0.800f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.Number] = new float[]{0.549f, 0.816f, 0.827f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.String] = new float[]{0.902f, 0.753f, 0.416f, 1.0f};
        defaultPalette[TextEditorPaletteIndex.Identifier] = new float[]{0.6f, 0.85f, 0.7f, 1.0f};
    }

    // Define somewhere globally or persistently
    private static float[][] paletteColors = new float[TextEditorPaletteIndex.Max][4];
    private static boolean paletteInitialized = false;

    public static int[] customPalette = new int[TextEditorPaletteIndex.Max];

    public static void renderTextEditorColorSettings() {
        //ImGui.text("This feature is still in development and all buttons here are not yet functional.");
        if (!paletteInitialized) {
            initDefaultPalette(); // <-- initialize defaults once

            paletteColors = defaultPalette;
            for (int i = 0; i < paletteColors.length; i++) {
                customPalette[i] = ImGui.colorConvertFloat4ToU32(
                        paletteColors[i][0], paletteColors[i][1],
                        paletteColors[i][2], paletteColors[i][3]
                );
            }
            paletteInitialized = true;
        }

        drawColorSetting("Default", TextEditorPaletteIndex.Default);
        drawColorSetting("Line Number", TextEditorPaletteIndex.LineNumber);
        drawColorSetting("Punctuation", TextEditorPaletteIndex.Punctuation);
        drawColorSetting("Selection", TextEditorPaletteIndex.Selection);
        drawColorSetting("Current Line Fill", TextEditorPaletteIndex.CurrentLineFill);
        drawColorSetting("Current Line Edge", TextEditorPaletteIndex.CurrentLineEdge);
        drawColorSetting("Keyword", TextEditorPaletteIndex.Keyword);
        drawColorSetting("Number", TextEditorPaletteIndex.Number);
        drawColorSetting("String", TextEditorPaletteIndex.String);
        drawColorSetting("Identifier", TextEditorPaletteIndex.Identifier);

        if (ImGui.button("Apply Colors")) {
            for (int i = 0; i < paletteColors.length; i++) {
                customPalette[i] = ImGui.colorConvertFloat4ToU32(
                        paletteColors[i][0], paletteColors[i][1],
                        paletteColors[i][2], paletteColors[i][3]
                );
            }
        }

        ImGui.sameLine();

        if (ImGui.button("Reset to Defaults")) {
            paletteColors = defaultPalette;
            for (int i = 0; i < paletteColors.length; i++) {
                customPalette[i] = ImGui.colorConvertFloat4ToU32(
                        paletteColors[i][0], paletteColors[i][1],
                        paletteColors[i][2], paletteColors[i][3]
                );
            }
        }
    }

    private static void drawColorSetting(String label, int index) {
        ImGui.colorEdit4(label, paletteColors[index]);
    }

    private static float[] u32ToFloat4(int colorU32) {
        float r = ((colorU32 >> 0) & 0xFF) / 255.0f;
        float g = ((colorU32 >> 8) & 0xFF) / 255.0f;
        float b = ((colorU32 >> 16) & 0xFF) / 255.0f;
        float a = ((colorU32 >> 24) & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
    }
}
