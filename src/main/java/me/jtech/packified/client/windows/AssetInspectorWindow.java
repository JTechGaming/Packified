package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.extension.texteditor.TextEditor;
import imgui.extension.texteditor.flag.TextEditorPaletteIndex;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.PackFile;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.windows.popups.PackBrowserWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static me.jtech.packified.client.windows.EditorWindow.checkForErrors;

@Environment(EnvType.CLIENT)
public class AssetInspectorWindow {
    public static ImBoolean isOpen = new ImBoolean(false);

    private static PackFile base = null;
    private static PackFile compare = null;

    private static boolean firstBase = true;
    private static List<ResourcePackProfile> packs = new ArrayList<>();
    private static ResourcePackProfile baseProfile = null;
    private static ResourcePackProfile compareProfile = null;

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

            ImGui.pushStyleColor(ImGuiCol.ChildBg, 0x15FFFFFF);

            // Split screen
            if (ImGui.beginChild("Base", ImGui.getContentRegionAvailX()/2 - 30, ImGui.getContentRegionAvailY())) {
                if (compare == null) {
                    ImGui.setCursorPos((ImGui.getContentRegionAvailX() - ImGui.calcTextSize("No file loaded").x) / 2, (ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing()) / 2);
                    ImGui.text("No file loaded");
                    ImGui.setCursorPos((ImGui.getContentRegionAvailX() - ImGui.calcTextSize("Load File ").x) / 2, ImGui.getCursorPosY() + ImGui.getTextLineHeightWithSpacing()-20);
                    if (ImGui.button("Load File")) {
                        if (baseProfile == null) {
                            ImGui.openPopup("Select Pack");
                            if (firstBase) {
                                firstBase = false;
                                packs = PackUtils.refreshInternalPacks();
                            }
                            for (ResourcePackProfile pack : packs) {
                                if (ImGui.menuItem(pack.getDisplayName().getString())) {
                                    baseProfile = pack;
                                }
                            }
                            ImGui.endPopup();
                        }
                    }
                    if (ImGui.isItemHovered() && baseProfile != null) {
                        ImGui.setTooltip("Open a file from the file hierarchy");
                    }
                } else {
                    renderTextFileEditor(base);
                }
                ImGui.endChild();
            }
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_arrow.png"), 24, 24);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Transfer");
            }
            if (ImGui.isItemClicked()) {
                if (base != null && compare != null) {
                    compare.getTextEditor().setText(base.getTextEditor().getText());
                }
            }
            ImGui.sameLine();
            if (ImGui.beginChild("Compare", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY())) {
                if (compare == null) {
                    ImGui.setCursorPos((ImGui.getContentRegionAvailX() - ImGui.calcTextSize("No file loaded").x) / 2, (ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing()) / 2);
                    ImGui.text("No file loaded");
                    ImGui.setCursorPos((ImGui.getContentRegionAvailX() - ImGui.calcTextSize("Load File ").x) / 2, ImGui.getCursorPosY() + ImGui.getTextLineHeightWithSpacing()-20);
                    if (ImGui.button(PackifiedClient.currentPack == null ? "Open Pack" : "Create File")) {
                        if (PackifiedClient.currentPack != null) {

                        } else {
                            PackBrowserWindow.open.set(true);
                        }
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Create a new file by clicking this button, or open one from the file hierarchy");
                    }
                } else {
                    renderTextFileEditor(base);
                }
                ImGui.endChild();
            }

            ImGui.popStyleColor();
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
