package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileDialog;
import me.jtech.packified.client.util.FileUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public class FileHierarchy {
    private final Map<String, FileHierarchy> children = new HashMap<>();
    private Identifier identifier;

    private static ImString searchQuery = new ImString();
    private static String selectedExtension = "none";
    private static String selectedName = "";
    private static Identifier selectedIdentifier;
    private static String[] extensions = {
            "none", ".png", ".json", ".ogg", ".mcmeta", ".txt", ".properties", ".vsh", ".fsh", ".bbmodel", ".bbmodel.json"
    };

    private enum FileTypeComboOptions {
        NONE, PNG, JSON, OGG, MC_META, TEXT, PROPERTIES, VSH, FSH, BB_MODEL, BB_MODEL_JSON
    }

    public enum FileType {
        PNG(".png"), JSON(".json"), OGG(".ogg"), MC_META(".mcmeta"), TEXT(".txt"), PROPERTIES(".properties"), VSH(".vsh"), FSH(".fsh"), BB_MODEL(".bbmodel"), BB_MODEL_JSON(".bbmodel.json");

        private final String extension;

        FileType(String extension) {
            this.extension = extension;
        }

        public static FileType fromExtension(String extension) {
            for (FileType fileType : values()) {
                if (fileType.extension.equals(extension)) {
                    return fileType;
                }
            }
            return null;
        }

        public String getExtension() {
            return extension;
        }
    }

    public static String[] supportedFileExtensions = {
            ".png", ".json", ".ogg", ".mcmeta", ".txt", ".properties", ".vsh", ".fsh", ".bbmodel", ".bbmodel.json"
    };

    public void addPath(String[] pathParts, int index, Identifier identifier) {
        if (index >= pathParts.length) return;
        FileHierarchy child = new FileHierarchy();
        child.identifier = identifier;
        children.putIfAbsent(pathParts[index], child);
        children.get(pathParts[index]).addPath(pathParts, index + 1, identifier);
    }

    public void renderTree(String name, boolean selecting) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        if (children.isEmpty()) { // File
            if (selecting) return;
            for (String extension : supportedFileExtensions) {
                if (name.endsWith(extension)) { // Supported file type
                    drawFile(name, identifier);
                    return;
                }
            }
            drawFile(name, identifier); // Unsupported file type
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Unsupported file type");
            }
        } else { // Folder
            if (ImGui.treeNode(name)) {
                drawFolder(identifier, selecting);
                for (Map.Entry<String, FileHierarchy> entry : children.entrySet()) {
                    entry.getValue().renderTree(entry.getKey(), selecting);
                }

                ImGui.treePop();
            }

            renderRightClickPopup(name, identifier, true);

            if (ImGui.beginDragDropTarget()) {
                String payload = ImGui.acceptDragDropPayload("DND_FILE");
                if (payload != null) {
                    // Logic to move the file to the new location
                    FileUtils.sendDebugChatMessage("Moving file " + payload + " to " + identifier);
                    moveFile(payload, identifier);
                }
                ImGui.endDragDropTarget();
            }
        }
    }

    private static void drawFile(String name, Identifier identifier) {
        if (identifier == null) return;
        if (ImGui.selectable(name, selectedName.equals(name), ImGuiSelectableFlags.AllowDoubleClick | ImGuiDragDropFlags.SourceExtern | ImGuiTabItemFlags.UnsavedDocument)) {
            if (ImGui.isMouseDoubleClicked(0)) {
                selectedName = name;
                selectedIdentifier = identifier;
                // Open file
                FileUtils.openFile(identifier, FileUtils.getExtension(identifier));
            }
        }

        renderRightClickPopup(name, identifier, false);

        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            ImGui.setDragDropPayload("DND_FILE", identifier.toString());
            ImGui.text(name);
            ImGui.endDragDropSource();
        }

        ImGui.tableSetColumnIndex(1);
        ImGui.text(FileUtils.formatFileSize(FileUtils.getFileSize(identifier)));
        ImGui.tableSetColumnIndex(2);
        ImGui.text(FileUtils.formatExtension(FileUtils.getFileExtension(name)));
        ImGui.tableSetColumnIndex(0);
    }

    private static void renderRightClickPopup(String name, Identifier identifier, boolean isFolder) {
        if (ImGui.beginPopupContextItem(name)) {
            selectedName = name;
            selectedIdentifier = identifier;
            // Open file
            if (ImGui.beginMenu("New")) {
                if (ImGui.menuItem("File")) {
                    // Create a new file
                    ModifyFileWindow.open("Create File", identifier, (fileIdentifier) -> {
                        // Create the file
                        String fileName = fileIdentifier.substring(fileIdentifier.lastIndexOf('/') + 1);
                        String newIdentifierPath = identifier.getPath().substring(0, identifier.getPath().lastIndexOf('/') + 1) + fileName;
                        System.out.println("newIdentifierPath: " + newIdentifierPath);
                        Identifier newIdentifier = FileUtils.validateIdentifier(newIdentifierPath);
                        FileUtils.saveSingleFile(newIdentifier, FileUtils.getFileExtension(fileName), "");
                    });
                }
                if (ImGui.menuItem("Folder")) {
                    // Create a new folder
                    ModifyFileWindow.open("Create Folder", identifier, (folderIdentifier) -> {
                        // Create the folder
                        String folderName = folderIdentifier.substring(folderIdentifier.lastIndexOf('/') + 1);
                        String newIdentifierPath = identifier.getPath().substring(0, identifier.getPath().lastIndexOf('/') + 1) + folderName;
                        Identifier newIdentifier = FileUtils.validateIdentifier(newIdentifierPath);
                        if (newIdentifier == null) {
                            return;
                        }
                        FileUtils.saveFolder(newIdentifier);
                    });
                }
                ImGui.endMenu();
            }
            if (ImGui.menuItem("Load Pack")) {
                selectedName = name;
                selectedIdentifier = identifier;
                // Open the select pack window
                SelectPackWindow.open = true;
            }
            if (!isFolder) {
                if (ImGui.menuItem("Open")) {
                    selectedName = name;
                    selectedIdentifier = identifier;
                    // Open file
                    FileUtils.openFile(identifier, FileUtils.getExtension(identifier));
                }
                ImGui.separator();
                if (ImGui.beginMenu("Refactor")) {
                    if (ImGui.menuItem("Rename")) {
                        // Create a new file
                        System.out.println("Rename");
                        ModifyFileWindow.open("Rename", identifier, (fileName) -> {
                            // Create the file
                            FileUtils.renameFile(identifier, fileName);
                        });
                    }
                    if (ImGui.menuItem("Move")) {
                        // Create a new folder
                        System.out.println("Move");
                        ModifyFileWindow.open("Move", identifier, (fileName) -> {
                            // Create the file
                            FileUtils.moveFile(identifier, fileName);
                        });
                    }
                    ImGui.endMenu();
                }
                ImGui.separator();
                if (ImGui.beginMenu("Copy")) {
                    if (ImGui.menuItem("Name")) {
                        // Copy the file name to the players clipboard
                        MinecraftClient.getInstance().keyboard.setClipboard(selectedName);
                    }
                    if (ImGui.menuItem("Identifier")) {
                        // Copy the file identifier to the players clipboard
                        // Example: "minecraft:textures/block/dirt.png"
                        // Would copy: "block/dirt.png" to the clipboard
                        String id = identifier.getPath().substring(identifier.getPath().indexOf('/') + 1);
                        MinecraftClient.getInstance().keyboard.setClipboard(id.substring(0, id.lastIndexOf('.')));
                    }
                    ImGui.endMenu();
                }
                ImGui.separator();
                if (ImGui.menuItem("Open In Explorer")) {
                    selectedName = name;
                    selectedIdentifier = identifier;

                    // Open the file in the system file explorer
                    FileUtils.openFileInExplorer(identifier);
                }
                ImGui.separator();
                if (ImGui.menuItem("Delete")) {
                    // Delete file
                    ConfirmWindow.open("delete this file", "The file will be lost forever.", () -> {
                        FileUtils.deleteFile(identifier);
                    });
                }
            }
            ImGui.endPopup();
        }
    }

    public static void moveFile(String sourceIdentifier, Identifier targetIdentifier) {
        // Logic to move the file from sourceIdentifier to targetIdentifier
        FileUtils.moveFile(Identifier.of(sourceIdentifier), targetIdentifier.getPath());
    }

    public static void render() {
        ImGui.begin("File Hierarchy");

        // Search bar
        ImGui.inputText("Search", searchQuery);

        // Filter options
        if (ImGui.beginCombo("Filter by extension", selectedExtension)) {
            for (String extension : extensions) {
                boolean isSelected = selectedExtension.equals(extension);
                if (ImGui.selectable(extension, isSelected)) {
                    selectedExtension = extension;
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        if (PackifiedClient.currentPack != null) {
            // Import and Delete buttons
            ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_import.png"), 14, 14);
            if (ImGui.isItemClicked()) {
                // Logic to import a file
                String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified").toString();
                FileDialog.openFileDialog(defaultFolder, "Files", "json", "png").thenAccept(pathStr -> {
                    if (pathStr != null) {
                        Path path = Path.of(pathStr);
                        MinecraftClient.getInstance().submit(() -> {
                            FileUtils.importFile(path);
                        });
                    }
                });
            }
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_delete.png"), 14, 14);
            if (ImGui.isItemClicked()) {
                // Logic to delete a file
                ConfirmWindow.open("delete this file", "The file will be lost forever.", () -> {
                    FileUtils.deleteFile(selectedIdentifier);
                });
            }
            if (ImGui.beginTable("FileHierarchyTable", 3, ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.NoBordersInBody
                    | ImGuiTableFlags.BordersOuter | ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY)) {
                ImGui.tableSetupColumn("Name");
                ImGui.tableSetupColumn("Size");
                ImGui.tableSetupColumn("Type");
                ImGui.tableHeadersRow();
                ImGui.tableNextRow();
                drawFolder(null, false);
                if (ImGui.treeNode(PackifiedClient.currentPack.getDisplayName().getString())) {
                    ImGui.tableNextRow();
                    drawFolder(null, false);
                    if (ImGui.treeNode("assets")) {
                        try (ResourcePack resourcePack = PackifiedClient.currentPack.createResourcePack()) {
                            String namespace = "minecraft";

                            // Build the hierarchy structure
                            FileHierarchy root = new FileHierarchy();

                            find(resourcePack, namespace, root, "atlases");
                            find(resourcePack, namespace, root, "blockstates");
                            find(resourcePack, namespace, root, "font");
                            find(resourcePack, namespace, root, "lang");
                            find(resourcePack, namespace, root, "models");
                            find(resourcePack, namespace, root, "particles");
                            find(resourcePack, namespace, root, "shaders");
                            find(resourcePack, namespace, root, "sounds");
                            find(resourcePack, namespace, root, "texts");
                            find(resourcePack, namespace, root, "textures");

                            // Render the hierarchy
                            root.renderTree(namespace, false);

                            ImGui.treePop(); // Close "assets"
                        }
                    }
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    String mcmeta = "pack.mcmeta";
                    if (ImGui.selectable(mcmeta, selectedName.equals(mcmeta), ImGuiSelectableFlags.AllowDoubleClick | ImGuiDragDropFlags.SourceExtern)) {
                        if (ImGui.isMouseDoubleClicked(0)) {
                            selectedName = mcmeta;
                            selectedIdentifier = Identifier.of("pack.mcmeta");
                            // Open file
                            //FileUtils.openFile(selectedIdentifier, FileType.MC_META); //TODO: Implement pack.mcmeta
                        }
                    }
                    ImGui.tableSetColumnIndex(1);
                    ImGui.text("---");
                    ImGui.tableSetColumnIndex(2);
                    ImGui.text("Meta");
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    String packpng = "pack.png";
                    if (ImGui.selectable(packpng, selectedName.equals(packpng), ImGuiSelectableFlags.AllowDoubleClick | ImGuiDragDropFlags.SourceExtern)) {
                        if (ImGui.isMouseDoubleClicked(0)) {
                            selectedName = packpng;
                            selectedIdentifier = Identifier.of("pack.png");
                            // Open file
                            //FileUtils.openFile(selectedIdentifier, FileType.PNG); //TODO: Implement pack.png
                        }
                    }
                    ImGui.tableSetColumnIndex(1);
                    ImGui.text("---");
                    ImGui.tableSetColumnIndex(2);
                    ImGui.text("Image");
                    ImGui.tableSetColumnIndex(0);
                    ImGui.treePop(); // Close pack name
                }
                ImGui.endTable();
            }
        } else {
            // Centered text to indicate that no pack is loaded
            ImGui.setCursorPos((ImGui.getWindowWidth() - ImGui.calcTextSize("No pack loaded").x) / 2, (ImGui.getWindowHeight() - ImGui.getTextLineHeightWithSpacing()) / 2);
            ImGui.text("No pack loaded");
            // Centered button to load a pack
            ImGui.setCursorPos((ImGui.getWindowWidth() - ImGui.calcTextSize("Load Pack").x) / 2, (ImGui.getWindowHeight() - ImGui.getTextLineHeightWithSpacing()) / 2 + ImGui.getTextLineHeightWithSpacing());
            if (ImGui.button("Load Pack")) {
                SelectPackWindow.open = true;
            }
        }

        ImGui.end();
    }

    public static void find(ResourcePack resourcePack, String namespace, FileHierarchy root, String prefix) {
        resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, prefix, (identifier, resourceSupplier) -> {
            if ((!searchQuery.get().isBlank() && !identifier.getPath().contains(searchQuery.get())) || (!selectedExtension.equals("none") && !identifier.getPath().contains(selectedExtension))) {
                return;
            }
            String[] parts = identifier.getPath().split("/"); // Split into folder structure
            root.addPath(parts, 0, identifier);
        });
    }

    public static void drawFolder(Identifier identifier, boolean selecting) {
        if (ImGui.isMouseDoubleClicked(0) && identifier != null && selecting) {
            SelectFolderWindow.close(identifier);
            //TODO fix that this also gets called for any parent folder and their other subfolders
        }
        ImGui.tableSetColumnIndex(1);
        ImGui.text("---");
        ImGui.tableSetColumnIndex(2);
        ImGui.text("Folder");
        ImGui.tableSetColumnIndex(0);
    }

    @FunctionalInterface
    public interface FileModifyAction {
        void execute(String fileName);
    }
}