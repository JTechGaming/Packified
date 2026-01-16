package me.jtech.packified.client.windows.popups;

import imgui.ImGui;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.windows.LogWindow;
import me.jtech.packified.client.windows.FileExplorerWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class SelectFolderWindow {
    public static ImBoolean open = new ImBoolean(false);
    private static String fileName = "";
    private static String extension = "";
    private static String content = "";
    private static Path selectedFolder = null;
    private static boolean multipleFiles = false;

    public static void open(String fileName, String extension, String content) {
        open.set(true);
        SelectFolderWindow.fileName = fileName;
        SelectFolderWindow.extension = extension;
        SelectFolderWindow.content = content;
        selectedFolder = null;
        multipleFiles = false;
    }

    public static void open(String folderName, List<Path> fileList) {
        open.set(true);
        selectedFolder = null;
        fileName = folderName;
        extension = "Multiple Files";
        content = String.join("᭩ ", fileList.stream().map(Path::toString).toList());
        multipleFiles = true;
    }

    public static void render() {
        if (!open.get()) return;

        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
        if (ImGui.begin("Select Folder", open)) {
            ImGui.text("Select a folder to save:");
            Path packPath = getPackFolderPath();

            // Button to open the shared FileExplorerWindow for folder selection.
            if (packPath != null && Files.exists(packPath)) {
                if (ImGui.button("Open File Explorer")) {
                    Consumer<Path> onSelect = (Path p) -> {
                        if (p != null && Files.isDirectory(p)) {
                            selectedFolder = p;
                        }
                    };
                    FileExplorerWindow.open(packPath, true, onSelect);
                }
            } else {
                ImGui.text("No valid pack folder found.");
            }

            if (selectedFolder != null && ImGui.button("Save Here")) {
                Path targetPath = selectedFolder.resolve(fileName);
                LogWindow.addInfo("Saving file to: " + targetPath);
                if (multipleFiles) {
                    String[] fileNames = content.split("᭩ ");
                    for (String filePath : fileNames) {
                        Path sourcePath = Paths.get(filePath.trim());
                        if (Files.exists(sourcePath)) {
                            try {
                                Files.copy(sourcePath, selectedFolder.resolve(sourcePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                LogWindow.addInfo("Copied " + sourcePath + " to " + selectedFolder);
                            } catch (IOException e) {
                                LogWindow.addError("Failed to copy " + sourcePath + ": " + e.getMessage());
                            }
                        } else {
                            LogWindow.addError("Source file does not exist: " + sourcePath);
                        }
                    }
                } else {
                    boolean success = FileUtils.saveSingleFile(targetPath, extension, content, PackifiedClient.currentPack);
                    if (success) {
                        LogWindow.addInfo("File saved successfully.");
                    } else {
                        LogWindow.addError("Failed to save file.");
                    }
                }
                open.set(false);
            }
        }
        ImGui.end();
    }

    private static Path getPackFolderPath() {
        if (PackifiedClient.currentPack == null) return null;
        return FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(PackifiedClient.currentPack.getDisplayName().getString());
    }

    private static List<Path> getSubFolders(Path root) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, Files::isDirectory)) {
            List<Path> folders = new ArrayList<>();
            for (Path entry : stream) {
                folders.add(entry);
            }
            return folders;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
