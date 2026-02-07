package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.extension.texteditor.flag.TextEditorPaletteIndex;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.helpers.DisplayScaleHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.imgui.ImguiThemes;
import me.jtech.packified.client.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Map;

@Environment(EnvType.CLIENT)
public class PreferencesWindow {
    public static ImBoolean stayInCreative = new ImBoolean(ModConfig.getBoolean("stayincreative", true));
    public static ImBoolean autoReloadAssets = new ImBoolean(ModConfig.getBoolean("autoreloadassets", false));
    public static ImBoolean dontSaveExplorerOnClose = new ImBoolean(ModConfig.getBoolean("dontsaveexploreronclose", false));
    public static ImInt maxBackupCount = new ImInt(ModConfig.getInt("maxBackupCount", 0)); // Maximum number of backups to keep
    public static ImBoolean logPackReloadData = new ImBoolean(ModConfig.getBoolean("packreloadlogging", true));
    public static ImBoolean logPackDownloadInfo = new ImBoolean(ModConfig.getBoolean("packdownloadlogging", true));
    public static ImInt fontSize = new ImInt(ModConfig.getInt("fontsize", DisplayScaleHelper.getIdealFontSize()));
    public static ImInt selectedFont = new ImInt(ModConfig.getInt("font",
            ImGuiImplementation.loadedFontNames.indexOf("Roboto (Regular)")
    ));

    public static ImBoolean isOpen = new ImBoolean(false);

    public static void render() {
        if (!isOpen.get()) {
            return; // If the window is not open, do not render
        }

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getCenterViewportPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);

        if (ImGui.begin("Settings", isOpen)) {
            if (ImGui.collapsingHeader("General Settings")) {
                if (ImGui.checkbox("Stay In Creative: ", stayInCreative)) {
                    ModConfig.updateSettings(Map.of("stayincreative", stayInCreative.get()));
                    PackifiedClient.changeGameMode(PackifiedClient.getPreviousGameMode());
                }
                if (ImGui.checkbox("Auto Reload Assets: ", autoReloadAssets)) {
                    ModConfig.updateSettings(Map.of("autoreloadassets", autoReloadAssets.get()));
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Whether assets should automatically be hotswapped when changed on disk.");
                }
                if (ImGui.checkbox("Don't Save Explorer State On Close: ", dontSaveExplorerOnClose)) {
                    ModConfig.updateSettings(Map.of("dontsaveexploreronclose", dontSaveExplorerOnClose.get()));
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("If enabled, the file explorer state will not be retained when it is closed.");
                }
                if (ImGui.inputInt("Max backups", maxBackupCount)) {
                    ModConfig.updateSettings(Map.of("maxBackupCount", maxBackupCount.get()));
                }
            }
            if (ImGui.collapsingHeader("Appearance & Behavior")) {
                if (ImGui.combo("Theme", ImguiThemes.getCurrentTheme(), ImguiThemes.getAvailableThemes())) {
                    ImguiThemes.setTheme(ImguiThemes.getCurrentTheme(), false);
                }
                if (ImGui.checkbox("Flat style", ImguiThemes.flatStyle)) {
                    ImguiThemes.applyStyle();
                    ModConfig.updateSettings(Map.of("flatstyle", ImguiThemes.flatStyle.get()));
                }
                if (ImGui.inputInt("Font size", fontSize)) {
                    ImGui.getIO().setFontGlobalScale(fontSize.get() / 14.0f);
                    ModConfig.updateSettings(Map.of("fontsize", fontSize.get()));
                }
                if (ImGui.combo("Font", selectedFont, ImGuiImplementation.loadedFontNames.toArray(String[]::new))) {
                    ImGuiImplementation.currentFont = ImGuiImplementation.loadedFonts.get(selectedFont.get());
                    ModConfig.updateSettings(Map.of("font", selectedFont.get()));
                }
                if (ImGui.checkbox("Log unimportant pack data", logPackReloadData)) {
                    ModConfig.updateSettings(Map.of("packreloadlogging", logPackReloadData.get()));
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Whether the log window should display unimportant pack reload data like:\n 'Created: 1024x1024x4 minecraft:textures/atlas/armor_trims.png-atlas'");
                }
                if (ImGui.checkbox("Log pack download info", logPackDownloadInfo)) {
                    ModConfig.updateSettings(Map.of("packdownloadlogging", logPackDownloadInfo.get()));
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Whether the log window should display info about pack downloading progress");
                }
                if (ImGui.collapsingHeader("Editor")) {
                    renderTextEditorColorSettings();
                }
            }
//            if (ImGui.collapsingHeader("Keymap")) {
//                ImGui.text("This feature is still in development");
//            }


            // Close button in bottom right
            ImGui.setCursorPosY(ImGui.getWindowHeight() - ImGui.getFrameHeightWithSpacing());
            ImGui.setCursorPosX(ImGui.getWindowWidth() - ImGui.getFrameHeightWithSpacing() - ImGui.getStyle().getItemSpacingX() - ImGui.getStyle().getWindowMinSizeX());
            if (ImGui.button("Close")) {
                isOpen.set(false); // Close the settings window
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
