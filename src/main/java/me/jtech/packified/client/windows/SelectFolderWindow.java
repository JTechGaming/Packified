package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.FileUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class SelectFolderWindow {
    public static ImBoolean open = new ImBoolean(false);
    private static String fileName = "";
    private static String extension = "";
    private static String content = "";
    private static Map<Path, String> files = new HashMap<>();

    private final Path filePath;
    private final Map<String, SelectFolderWindow> children = new HashMap<>();

    /**
     * Open the select folder window with the given file name, extension, and content
     */
    public static void open(String fileName, String extension, String content) {
        open.set(true);
        SelectFolderWindow.fileName = fileName;
        SelectFolderWindow.extension = extension;
        SelectFolderWindow.content = content;
    }

    /**
     * Open the select folder window with the given folder name and its files
     */
    public static void open(String folderName, List<Path> filePaths) {
        open.set(true);
        SelectFolderWindow.fileName = folderName;
        Map<Path, String> fileMap = new HashMap<>();

        for (Path file : filePaths) {
            try {
                String content = Files.readString(file);
                fileMap.put(file, content);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SelectFolderWindow.files = fileMap;
    }

    public static void close(Path selectedFolder) {
        if (!open.get()) return; // Prevent multiple executions
        open.set(false);

        if (selectedFolder == null) return;

        if (!files.isEmpty()) {
            for (Map.Entry<Path, String> entry : files.entrySet()) {
                Path file = entry.getKey();
                String content = entry.getValue();

                Path targetPath = selectedFolder.resolve(file.getFileName());
                LogWindow.addInfo("Saving file to: " + targetPath);
                FileUtils.saveSingleFile(targetPath, FileUtils.getFileExtension(file.getFileName().toString()), content, PackifiedClient.currentPack);
            }
        } else {
            Path targetPath = selectedFolder.resolve(fileName);
            LogWindow.addInfo("Saving single file to: " + targetPath);
            FileUtils.saveSingleFile(targetPath, extension, content, PackifiedClient.currentPack);
        }
    }

    public SelectFolderWindow(Path filePath) {
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

        SelectFolderWindow current = this;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (i == parts.size() - 1 && Files.isRegularFile(path)) {
                current.children.putIfAbsent(part, new SelectFolderWindow(path));
            } else {
                current.children.putIfAbsent(part, new SelectFolderWindow(filePath.resolve(part)));
                current = current.children.get(part); // Navigate deeper
            }
        }
    }

    public void renderTree(String name) {
        if (!Files.isRegularFile(filePath)) {// It's a folder
            ImGui.tableNextRow();
            ImGui.tableSetColumnIndex(0);
            boolean isOpen = ImGui.treeNode(name);
            if (ImGui.isMouseDoubleClicked(0)) {
                close(filePath);
            }
            if (isOpen) {
                for (Map.Entry<String, SelectFolderWindow> entry : children.entrySet()) {
                    entry.getValue().renderTree(entry.getKey());
                }
                ImGui.treePop();
            }
        }
    }

    public static void render() {
        if (!open.get()) return;

        if (ImGui.begin("Select Folder", open)) {
            if (ImGui.beginTable("SelectFolderTable", 3, ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY)) {
                ImGui.tableSetupColumn("Name");
                ImGui.tableSetupColumn("Size");
                ImGui.tableSetupColumn("Type");
                ImGui.tableHeadersRow();

                // Load actual resource pack folder path
                Path packPath = getPackFolderPath();
                if (packPath != null && Files.exists(packPath)) {
                    SelectFolderWindow root = SelectFolderWindow.buildFileHierarchy(packPath);
                    root.renderTree(packPath.getFileName().toString());
                } else {
                    ImGui.text("No valid pack folder found.");
                }

                ImGui.endTable();
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

    public static SelectFolderWindow buildFileHierarchy(Path rootPath) {
        SelectFolderWindow root = new SelectFolderWindow(rootPath);
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> sortedPaths = paths
                    .filter(Files::exists)
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
}
