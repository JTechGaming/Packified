package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
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

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class FileHierarchy {
    private final Path filePath;
    private final Map<String, FileHierarchy> children = new HashMap<>();

    private static ImString searchQuery = new ImString();
    private static String selectedExtension = "none";
    private static Path selectedFile;
    public static final String[] extensions = {
            "none", ".png", ".json", ".ogg", ".mcmeta", ".txt", ".properties", ".vsh", ".fsh", ".bbmodel", ".bbmodel.json"
    };

    public FileHierarchy(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Recursively adds files and folders to their correct place in the hierarchy.
     */
    public void addFile(Path path) {
        Path relativePath = filePath.relativize(path); // Get relative path from root
        List<String> parts = new ArrayList<>();

        for (Path p : relativePath) {
            if (!p.toString().isEmpty()) {
                parts.add(p.toString());
            }
        }

        if (parts.isEmpty()) return;

        FileHierarchy current = this;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (i == parts.size() - 1 && Files.isRegularFile(path)) {
                current.children.putIfAbsent(part, new FileHierarchy(path));
            } else {
                current.children.putIfAbsent(part, new FileHierarchy(filePath.resolve(part)));
                current = current.children.get(part); // Navigate deeper
            }
        }
    }

    public void renderTree(String name) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);

        if (Files.isRegularFile(filePath)) { // It's a file
            drawFile(name, filePath);
        } else { // It's a folder
            boolean isOpen = ImGui.treeNode(name);

            renderRightClickPopup(name, filePath, true);

            if (!isOpen) {
                // Enable Drag & Drop Target for Folders
                if (ImGui.beginDragDropTarget()) {
                    String payload = ImGui.acceptDragDropPayload("DND_FILE");
                    if (payload != null) {
                        Path draggedFile = Path.of(payload);
                        FileUtils.moveFile(draggedFile, FileUtils.getRelativePackPath(filePath, draggedFile.getFileName().toString()));
                    }
                    ImGui.endDragDropTarget();
                }
            }

            if (isOpen) {
                for (Map.Entry<String, FileHierarchy> entry : children.entrySet()) {
                    entry.getValue().renderTree(entry.getKey());
                }
                ImGui.treePop();
            }
        }
    }

    private static void drawFile(String name, Path filePath) {
        if (filePath == null) return;
        if (ImGui.selectable(name, Objects.equals(selectedFile, filePath))) {
            selectedFile = filePath;
            FileUtils.openFile(filePath);
        }

        renderRightClickPopup(name, filePath, false);

        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            ImGui.setDragDropPayload("DND_FILE", filePath.toString());
            ImGui.text(name);
            ImGui.endDragDropSource();
        }

        ImGui.tableSetColumnIndex(1);
        ImGui.text(FileUtils.formatFileSize(FileUtils.getFileSize(filePath)));
        ImGui.tableSetColumnIndex(2);
        ImGui.text(FileUtils.formatExtension(FileUtils.getFileExtension(name)));
        ImGui.tableSetColumnIndex(0);
    }

    public static void render() {
        ImGui.begin("File Hierarchy");

        ImGui.inputText("Search", searchQuery);

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
                    FileUtils.deleteFile(selectedFile);
                });
            }

            Path packPath = getPackFolderPath();
            if (packPath != null && Files.exists(packPath)) {
                FileHierarchy root = buildFileHierarchy(packPath);
                if (ImGui.beginTable("FileHierarchyTable", 3, ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY)) {
                    ImGui.tableSetupColumn("Name");
                    ImGui.tableSetupColumn("Size");
                    ImGui.tableSetupColumn("Type");
                    ImGui.tableHeadersRow();

                    // Render File Hierarchy Tree
                    root.renderTree(packPath.getFileName().toString());

                    ImGui.endTable();
                }
            } else {
                ImGui.setCursorPos((ImGui.getWindowWidth() - ImGui.calcTextSize("No pack loaded").x) / 2, (ImGui.getWindowHeight() - ImGui.getTextLineHeightWithSpacing()) / 2);
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

    public static Path getPackFolderPath() {
        if (PackifiedClient.currentPack == null) return null;
        return FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(PackifiedClient.currentPack.getDisplayName().getString());
    }

    public static FileHierarchy buildFileHierarchy(Path rootPath) {
        FileHierarchy root = new FileHierarchy(rootPath);
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> sortedPaths = paths
                    .filter(Files::exists)
                    .filter(path -> searchQuery.get().isEmpty() || path.toString().toLowerCase().contains(searchQuery.get().toLowerCase()))
                    .filter(path -> selectedExtension.equals("none") || path.toString().toLowerCase().endsWith(selectedExtension.toLowerCase()))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
            for (Path path : sortedPaths) {
                root.addFile(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return root;
    }

    private static void renderRightClickPopup(String name, Path path, boolean isFolder) {
        if (ImGui.beginPopupContextItem(name)) {
            selectedFile = path;
            // Open file
            if (ImGui.beginMenu("New")) {
                if (ImGui.menuItem("File")) {
                    // Create a new file
                    ModifyFileWindow.open("Create File", path, (fileIdentifier) -> {
                        // Create the file
                        String fileName = fileIdentifier.substring(fileIdentifier.lastIndexOf('/') + 1);
                        String newIdentifierPath = path.toString().substring(0, path.toString().lastIndexOf('/') + 1) + fileName;
                        Path newPath = FileUtils.validateIdentifier(newIdentifierPath);
                        FileUtils.saveSingleFile(newPath, FileUtils.getFileExtension(fileName), "");
                    });
                }
                if (ImGui.menuItem("Folder")) {
                    // Create a new folder
                    ModifyFileWindow.open("Create Folder", path, (folderIdentifier) -> {
                        // Create the folder
                        String folderName = folderIdentifier.substring(folderIdentifier.lastIndexOf('/') + 1);
                        String newIdentifierPath = path.toString().substring(0, path.toString().lastIndexOf('/') + 1) + folderName;
                        Path newPath = FileUtils.validateIdentifier(newIdentifierPath);
                        if (newPath == null) {
                            return;
                        }
                        FileUtils.saveFolder(newPath);
                    });
                }
                ImGui.endMenu();
            }
            if (ImGui.menuItem("Load Pack")) {
                selectedFile = path;
                // Open the select pack window
                SelectPackWindow.open = true;
            }
            if (!isFolder) {
                if (ImGui.menuItem("Open")) {
                    selectedFile = path;

                    // Open file
                    FileUtils.openFile(path);
                }
                ImGui.separator();
                if (ImGui.beginMenu("Refactor")) {
                    if (ImGui.menuItem("Rename")) {
                        // Create a new file
                        System.out.println("Rename");
                        ModifyFileWindow.open("Rename", path, (fileName) -> {
                            // Create the file
                            FileUtils.renameFile(path, fileName);
                        });
                    }
                    if (ImGui.menuItem("Move")) {
                        // Create a new folder
                        System.out.println("Move");
                        ModifyFileWindow.open("Move", path, (fileName) -> {
                            // Create the file
                            FileUtils.moveFile(path, fileName);
                        });
                    }
                    ImGui.endMenu();
                }
                ImGui.separator();
                if (ImGui.beginMenu("Copy")) {
                    if (ImGui.menuItem("Name")) {
                        // Copy the file name to the players clipboard
                        MinecraftClient.getInstance().keyboard.setClipboard(selectedFile.getFileName().toString());
                    }
                    if (ImGui.menuItem("Identifier")) {
                        // Copy the file identifier to the players clipboard
                        // Example: "minecraft:textures/block/dirt.png"
                        // Would copy: "block/dirt.png" to the clipboard
                        String id = path.getFileName().toString().substring(path.getFileName().toString().indexOf('/') + 1);
                        MinecraftClient.getInstance().keyboard.setClipboard(id.substring(0, id.lastIndexOf('.')));
                    }
                    ImGui.endMenu();
                }
            }
            ImGui.separator();
            if (ImGui.menuItem("Open In Explorer")) {
                selectedFile = path;

                // Open the file in the system file explorer
                FileUtils.openFileInExplorer(path);
            }
            ImGui.separator();
            if (ImGui.menuItem("Delete")) {
                // Delete file
                ConfirmWindow.open("delete this file", "The file will be lost forever.", () -> {
                    FileUtils.deleteFile(path);
                });
            }
            ImGui.endPopup();
        }
    }

    @FunctionalInterface
    public interface FileModifyAction {
        void execute(String fileName);
    }
}
