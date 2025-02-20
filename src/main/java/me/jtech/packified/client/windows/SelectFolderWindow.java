package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.JsonFile;
import me.jtech.packified.client.util.PackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class SelectFolderWindow {
    public static boolean open = false;
    private static String fileName = "";
    private static String extension = "";
    private static String content = "";
    private static Map<File, String> files;

    /**
     * Open the select folder window with the given file name, extension, and content
     * @param fileName
     * @param extension
     * @param content
     */
    public static void open(String fileName, String extension, String content) {
        open = true;
        SelectFolderWindow.fileName = fileName;
        SelectFolderWindow.extension = extension;
        SelectFolderWindow.content = content;
    }

    /**
     * Open the select folder window with the given folder name and its files
     * @param folderName
     * @param files
     */
    public static void open(String folderName, List<File> files) {
        open = true;
        SelectFolderWindow.fileName = folderName;
        Map<File, String> fileMap = new HashMap<>();
        for (File file : files) {
            String extension = FileUtils.getFileExtensionName(folderName);
            if (extension == null) {
                System.err.println("Unsupported file extension: " + folderName);
                return;
            }
            String content = null;
            try {
                content = Files.readString(file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (content == null) {
                System.err.println("Failed to read file: " + file.getAbsolutePath());
                return;
            }
            fileMap.put(file, content);
        }
        SelectFolderWindow.files = fileMap;
    }

    public static void close(Identifier identifier) {
        open = false;
        if (identifier == null) {
            return;
        }
        if (files != null) {
            for (File file : files.keySet()) {
                String path = file.getPath();
                String name = path.substring(path.lastIndexOf('/') + 1);
                String newIdentifierPath = identifier.getPath().substring(0, identifier.getPath().lastIndexOf('/') + 1) + name;
                Identifier newIdentifier = FileUtils.validateIdentifier(newIdentifierPath);
                FileUtils.saveSingleFile(newIdentifier, FileUtils.getExtension(Identifier.of(name)).getExtension(), files.get(file));
            }
            return;
        }
        String newIdentifierPath = identifier.getPath().substring(0, identifier.getPath().lastIndexOf('/') + 1) + fileName;
        Identifier newIdentifier = FileUtils.validateIdentifier(newIdentifierPath);
        System.out.println(newIdentifier);
        FileUtils.saveSingleFile(newIdentifier, extension, content);
    }

    public static void render() {
        if (!open) {
            return;
        }
        if (ImGui.begin("Select Folder")) {
            if (ImGui.beginTable("FileHierarchyTable", 3, ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.NoBordersInBody
                    | ImGuiTableFlags.BordersOuter | ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY)) {
                ImGui.tableSetupColumn("Name");
                ImGui.tableSetupColumn("Size");
                ImGui.tableSetupColumn("Type");
                ImGui.tableHeadersRow();
                ImGui.tableNextRow();
                drawFolder();
                if (ImGui.treeNode(PackifiedClient.currentPack.getDisplayName().getString())) {
                    if (ImGui.isMouseDoubleClicked(0)) {
                        close(null);
                    }
                    ImGui.tableNextRow();
                    drawFolder();
                    if (ImGui.treeNode("assets")) {
                        if (ImGui.isMouseDoubleClicked(0)) {
                            close(null);
                        }
                        try (ResourcePack resourcePack = PackifiedClient.currentPack.createResourcePack()) {
                            String namespace = "minecraft";

                            // Build the hierarchy structure
                            FileHierarchy root = new FileHierarchy();

                            FileHierarchy.find(resourcePack, namespace, root, "atlases");
                            FileHierarchy.find(resourcePack, namespace, root, "blockstates");
                            FileHierarchy.find(resourcePack, namespace, root, "font");
                            FileHierarchy.find(resourcePack, namespace, root, "lang");
                            FileHierarchy.find(resourcePack, namespace, root, "models");
                            FileHierarchy.find(resourcePack, namespace, root, "particles");
                            FileHierarchy.find(resourcePack, namespace, root, "shaders");
                            FileHierarchy.find(resourcePack, namespace, root, "sounds");
                            FileHierarchy.find(resourcePack, namespace, root, "texts");
                            FileHierarchy.find(resourcePack, namespace, root, "textures");

                            // Render the hierarchy
                            root.renderTree(namespace, true);

                            ImGui.treePop(); // Close "assets"
                        }
                    }
                    ImGui.treePop(); // Close pack name
                }
                ImGui.endTable();
            }
        }
        ImGui.end();
    }

    public static void drawFolder() {
        ImGui.tableSetColumnIndex(1);
        ImGui.text("---");
        ImGui.tableSetColumnIndex(2);
        ImGui.text("Folder");
        ImGui.tableSetColumnIndex(0);
    }
}
