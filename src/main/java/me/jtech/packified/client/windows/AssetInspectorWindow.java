package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.extension.texteditor.TextEditor;
import imgui.extension.texteditor.flag.TextEditorPaletteIndex;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.internal.ImGuiWindow;
import imgui.type.ImBoolean;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.PackFile;

import java.util.Map;

import static me.jtech.packified.client.windows.EditorWindow.checkForErrors;

public class AssetInspectorWindow {
    public static ImBoolean isOpen = new ImBoolean(false);

    private static PackFile base = null;
    private static PackFile compare = null;

    public static void render() {
        if (!isOpen.get()) {
            return;
        }

        ImVec2 centerPos = ImGuiImplementation.getCenterViewportPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);

        if (ImGui.begin("Asset Inspector", isOpen, ImGuiWindowFlags.MenuBar)) {
            // Menu bar
            if (ImGui.beginMenuBar()) {
                if (ImGui.beginMenu("File")) {
                    // something
                    ImGui.menuItem("Test");
                    ImGui.endMenu();
                }
            }
            ImGui.endMenuBar();

            // Split screen
            if (ImGui.beginChild("Base")) {
                if (base == null) {
                    //ImGui.setCursorPos((ImGui.getContentRegionAvailX() - ImGui.calcTextSize("No file loaded").x) / 2, (ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing()) / 2);
                    ImGui.text("No file loaded");
                    if (ImGui.button("Load File")) {

                    }
                } else {
                    renderTextFileEditor(base);
                }
            }
            ImGui.endChild();
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_arrow.png"), 48, 48);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Transfer");
            }
            if (ImGui.isItemClicked()) {
                if (base != null && compare != null) {
                    compare.getTextEditor().setText(base.getTextEditor().getText());
                }
            }
            ImGui.sameLine();
            if (ImGui.beginChild("Compare")) {
                if (compare == null) {
                    //ImGui.setCursorPos((ImGui.getContentRegionAvailX() - ImGui.calcTextSize("No file loaded").x) / 2, (ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing()) / 2);
                    ImGui.text("No file loaded");
                    if (ImGui.button("Load File")) {

                    }
                } else {
                    renderTextFileEditor(base);
                }
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private static void renderTextFileEditor(PackFile file) {
        // Logic to render the JSON editor for the given file
        TextEditor textEditor = file.getTextEditor();
        // Set syntax highlighting

        // Set error markers
        Map<Integer, String> errorMarkers = checkForErrors(file.getTextEditor().getText());
        textEditor.setErrorMarkers(errorMarkers);

        int[] customPalette = textEditor.getDarkPalette();

        customPalette[TextEditorPaletteIndex.Default] = ImGui.colorConvertFloat4ToU32(0.731f, 0.975f, 0.590f, 1.0f);
        customPalette[TextEditorPaletteIndex.LineNumber] = ImGui.colorConvertFloat4ToU32(0.541f, 0.541f, 0.541f, 1.0f);
        customPalette[TextEditorPaletteIndex.Punctuation] = ImGui.colorConvertFloat4ToU32(0.969f, 0.616f, 0.427f, 1.0f);
        customPalette[TextEditorPaletteIndex.Selection] = ImGui.colorConvertFloat4ToU32(0.345f, 0.345f, 0.345f, 1.0f);
        customPalette[TextEditorPaletteIndex.CurrentLineFill] = ImGui.colorConvertFloat4ToU32(0.063f, 0.063f, 0.063f, 1.0f);
        customPalette[TextEditorPaletteIndex.CurrentLineEdge] = ImGui.colorConvertFloat4ToU32(0.498f, 0.624f, 0.498f, 0.5f);
        customPalette[TextEditorPaletteIndex.Keyword] = ImGui.colorConvertFloat4ToU32(0.863f, 0.863f, 0.800f, 1.0f);
        customPalette[TextEditorPaletteIndex.Number] = ImGui.colorConvertFloat4ToU32(0.549f, 0.816f, 0.827f, 1.0f);
        customPalette[TextEditorPaletteIndex.String] = ImGui.colorConvertFloat4ToU32(0.902f, 0.753f, 0.416f, 1.0f);
        customPalette[TextEditorPaletteIndex.Identifier] = ImGui.colorConvertFloat4ToU32(0.6f, 0.85f, 0.7f, 1.0f);

        textEditor.setPalette(customPalette);

        // Render the editor
        textEditor.render("TextEditor");
    }
}
