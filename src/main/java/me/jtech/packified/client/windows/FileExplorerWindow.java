// java
package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.helpers.ExternalEditorHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.util.SafeTextureLoader;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import me.jtech.packified.client.windows.popups.PackBrowserWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class FileExplorerWindow {
    public static List<FileExplorerWindow> explorers = new ArrayList<>();

    public ImBoolean isOpen = new ImBoolean(false);
    private Path currentDirectory;
    private Object lastPack = null;
    public final int i;
    private final ImString searchInput = new ImString(100);
    private final ImInt extensionFilter = new ImInt(0); // 0: All, 1: Models, 2: Textures, 3: Sounds
    public final boolean allowFolderSelection;
    private final Consumer<Path> onSelect;

    public FileExplorerWindow(int i, Path startingDirectory) {
        this.i = i;
        this.currentDirectory = startingDirectory;
        explorers.add(this);
        this.allowFolderSelection = false;
        this.onSelect = null;
    }

    public FileExplorerWindow(int i, Path startingDirectory, boolean allowFolderSelection, Consumer<Path> onSelect) {
        this.i = i;
        this.currentDirectory = startingDirectory;
        this.allowFolderSelection = allowFolderSelection;
        this.onSelect = onSelect;
        explorers.add(this);
    }

    public static void createNewExplorer(Path startingDirectory) {
        new FileExplorerWindow(explorers.size(), startingDirectory).isOpen.set(true);
    }

    public static void renderAll() {
        for (FileExplorerWindow explorer : new ArrayList<>(explorers)) {
            explorer.render();
        }
    }

    public static void open(Path packPath, boolean isSelecting, Consumer<Path> onSelect) {
        FileExplorerWindow explorer = new FileExplorerWindow(explorers.size(), packPath, isSelecting, onSelect);
        explorer.isOpen.set(true);
    }

    public void render() {
        if (!isOpen.get()) {
            if (PreferencesWindow.dontSaveExplorerOnClose.get()) {
                explorers.remove(this);
            }
            return;
        }
        alreadyRenderedHoverThisFrame = false;

        ImGuiImplementation.saveExplorers();

        if (PackifiedClient.currentPack != null) {
            Path packFolder = PackUtils.getPackFolder(PackifiedClient.currentPack.createResourcePack());
            // Only initialize/reset when there's no current directory or the pack changed
            if (currentDirectory == null) {
                currentDirectory = packFolder;
                lastPack = PackifiedClient.currentPack;
            }
        }

        if (ImGui.begin("File Explorer " + (i + 1), isOpen) && PackifiedClient.currentPack != null) {
            if (ImGui.beginChild("Left Panel##" + i, 220f, 0f, true)) {
                Path tempPath = currentDirectory;
                Path root = PackUtils.getPackFolder(PackifiedClient.currentPack.createResourcePack()).getParent();
                List<Path> ancestors = new ArrayList<>();
                while (tempPath != null && !tempPath.equals(root)) {
                    ancestors.add(tempPath);
                    tempPath = tempPath.getParent();
                }
                for (int idx = ancestors.size() - 1; idx >= 0; idx--) {
                    Path p = ancestors.get(idx);
                    if (ImGuiImplementation.menuItemWithIcon(ImGuiImplementation.getFileIconTextureId("folder"), p.getFileName().toString(), 16, 16)) {
                        currentDirectory = p;
                    }
                }
            }
            ImGui.endChild();

            ImGui.sameLine();

            if (ImGui.beginChild("Center Panel##" + i, 0f, 0f, true)) {
                if (Objects.requireNonNull(currentDirectory.toFile().listFiles()).length == 0) {
                    ImGuiImplementation.centeredText("This folder is empty.");
                }
                ImGui.inputText("Search##" + i, searchInput, ImGuiInputTextFlags.None);
                ImGui.sameLine();
                ImGui.combo("Extensions##" + i, extensionFilter, new String[]{"All", "Models", "Textures", "Sounds"}, 0);
                ImGui.separator();
                Arrays.stream(Objects.requireNonNull(currentDirectory.toFile().listFiles(file -> !file.isHidden()))).filter(file -> {
                    String search = searchInput.get().toLowerCase();
                    if (extensionFilter.get() == 1) { // Models
                        String ext = FileUtils.getFileExtension(file.getName()).toLowerCase();
                        if (!ext.equals(".json") && !ext.equals(".obj") && !ext.equals(".bbmodel")) {
                            return false;
                        }
                    } else if (extensionFilter.get() == 2) { // Textures
                        String ext = FileUtils.getFileExtension(file.getName()).toLowerCase();
                        if (!ext.equals(".png") && !ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".bmp")) {
                            return false;
                        }
                    } else if (extensionFilter.get() == 3) { // Sounds
                        String ext = FileUtils.getFileExtension(file.getName()).toLowerCase();
                        if (!ext.equals(".ogg") && !ext.equals(".wav") && !ext.equals(".mp3")) {
                            return false;
                        }
                    } // There is probably a better way to do this but oh well

                    return search.isEmpty() || file.getName().toLowerCase().contains(search); // If searching, filter files/folders
                }).forEach(file -> { // Display each file/folder
                    if (file.isDirectory()) {
                        if (ImGuiImplementation.menuItemWithIcon(ImGuiImplementation.getFileIconTextureId("folder"), file.getName(), 16, 16)) {
                            currentDirectory = file.toPath();
                        }
                        renderHoverTooltip(file.getName(), file.toPath(), true);

                        // Enable Drag & Drop Target for Folders
                        if (ImGui.beginDragDropTarget()) {
                            String payload = ImGui.acceptDragDropPayload("DND_FILE");
                            if (payload != null) {
                                Path draggedFile = Path.of(payload);
                                FileUtils.moveFile(draggedFile, file.toPath().resolve(draggedFile.getFileName()).toString()); // Move the file to the correct location
                            }
                            ImGui.endDragDropTarget();
                        }
                    } else {
                        if (isEditingName && file.toPath().equals(selectedFile)) {
                            ImGui.inputText("##FileNameInput", fileNameInput, ImGuiInputTextFlags.EnterReturnsTrue);
                            if (ImGui.isItemDeactivatedAfterEdit()) {
                                isEditingName = false;
                                selectedFileName = null; // Reset the selected file name
                                FileUtils.renameFile(file.toPath(), fileNameInput.get());
                            }
                        } else if (ImGuiImplementation.menuItemWithIcon(ImGuiImplementation.getFileIconTextureId(FileUtils.getFileExtension(file.getName())), file.getName(), 16, 16) && !isEditingName && !isCreatingNewFile) {
                            selectedFile = file.toPath();
                            FileUtils.openFile(file.toPath());
                        }

                        renderHoverTooltip(file.getName(), file.toPath(), false);

                        if (!allowFolderSelection) {
                            renderRightClickPopup(file.getName(), file.toPath(), false);
                        }

                        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
                            ImGui.setDragDropPayload("DND_FILE", file.toPath().toString());
                            ImGui.text(file.getName());
                            ImGui.endDragDropSource();
                        }
                    }
                });

                if (allowFolderSelection) {
                    ImGui.separator();
                    if (ImGui.button("Select This Folder")) {
                        if (onSelect != null) {
                            onSelect.accept(currentDirectory);
                        }
                        isOpen.set(false);
                        explorers.remove(this);
                    }
                }
            }
            if (PackifiedClient.currentPack == null) {
                ImGuiImplementation.centeredText("No pack loaded.");
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private static float itemHoverTime = 0.0f;
    private static String selectedFileName = null;
    private static boolean alreadyRenderedHoverThisFrame = false;

    private static void renderHoverTooltip(String name, Path filePath, boolean isFolder) {
        if (alreadyRenderedHoverThisFrame) return; // Prevent multiple tooltips from rendering in the same frame
        if (!name.equals(selectedFileName) && selectedFileName != null) {
            return;
        }

        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenBlockedByPopup) && !ImGui.isPopupOpen(name)) {
            itemHoverTime += ImGui.getIO().getDeltaTime();

            if (itemHoverTime > 1.1f) itemHoverTime = 1.0f;

            if (itemHoverTime >= 1.0f) {
                alreadyRenderedHoverThisFrame = true;

                ImGui.beginTooltip();

                ImGui.setNextWindowSize(200, 200);
                if (FileUtils.getFileExtension(filePath.getFileName().toString()).equalsIgnoreCase(".png")) {
                    int textureId = SafeTextureLoader.load(filePath);
                    if (textureId != -1) {
                        ImGui.image(textureId, 100, 100);
                    }
                }
                ImGui.text(String.format("Path: %s\nSize: %s" + (isFolder ? "\nType: %s\nFiles: %s" : "\nType: %s%s"),
                        FileUtils.getRelativePackPath(filePath),
                        isFolder ? FileUtils.formatFileSize(FileUtils.getFolderSize(filePath)) : FileUtils.formatFileSize(FileUtils.getFileSize(filePath)),
                        isFolder ? "Folder" : FileUtils.formatExtension(FileUtils.getFileExtension(name)),
                        isFolder ? FileUtils.getFolderFileCount(filePath) : ""));

                ImGui.endTooltip();
            } else {
                itemHoverTime += ImGui.getIO().getDeltaTime();
            }
        } else {
            if (itemHoverTime >= 1.0f && itemHoverTime < 2.5f) {
                itemHoverTime += ImGui.getIO().getDeltaTime();
            }
        }
    }

    private static boolean fileSelect;
    private static boolean isEditingName = false;
    private static ImString fileNameInput = new ImString(100);

    private static boolean isCreatingNewFile = false;
    private static Path selectedFileForCreation = null;
    private static Path selectedFile;

    private void renderRightClickPopup(String name, Path path, boolean isFolder) {
        if (ImGui.beginPopupContextItem(name)) {
            fileSelect = true;
            selectedFile = path;
            // Open file
            if (!isFolder) {
                if (ImGui.menuItem("Open")) {
                    FileUtils.openFile(path);
                }
                if (FileUtils.getExtension(path).equals(".json")) {
                    if (ImGui.menuItem("Open in model editor")) {
                        ModelEditorWindow.loadModel(path.toString());
                    }
                }
            }
            if (ImGui.beginMenu("New")) {
                if (ImGui.menuItem("File")) {
                    // Create a new file
                    isCreatingNewFile = true;

                    if (!isFolder) {
                        selectedFileForCreation = path.getParent();
                    } else {
                        selectedFileForCreation = path;
                    }

                    fileNameInput.set("file");
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
            if (ImGui.beginMenu("Generate")) {
                if (ImGui.beginMenu("Folders")) {
                    if (ImGui.menuItem("All")) {
                        FileUtils.generateFolderStructure();
                    }
                    ImGui.separator();
                    if (ImGui.beginMenu("Models")) {
                        if (ImGui.menuItem("All")) {
                            FileUtils.generateFolderStructure("models/");
                        }
                        ImGui.separator();
                        if (ImGui.menuItem("Item")) {
                            FileUtils.generateFolderStructure("models/item/");
                        }
                        if (ImGui.menuItem("Block")) {
                            FileUtils.generateFolderStructure("models/block/item/");
                            FileUtils.generateFolderStructure("models/block/block/");
                        }
                        ImGui.endMenu();
                    }
                    if (ImGui.beginMenu("Textures")) {
                        if (ImGui.menuItem("All")) {
                            FileUtils.generateFolderStructure("textures/");
                            FileUtils.generateFolderStructure("textures/item/");
                            FileUtils.generateFolderStructure("textures/block/");
                        }
                        ImGui.separator();
                        if (ImGui.menuItem("Item")) {
                            FileUtils.generateFolderStructure("textures/item/");
                        }
                        if (ImGui.menuItem("Block")) {
                            FileUtils.generateFolderStructure("textures/block/");
                        }
                        ImGui.endMenu();
                    }
                    if (ImGui.beginMenu("Shaders")) {
                        if (ImGui.menuItem("All")) {
                            FileUtils.generateFolderStructure("shaders/programs/");
                            FileUtils.generateFolderStructure("shaders/core/");
                        }
                        ImGui.separator();
                        if (ImGui.menuItem("Programs")) {
                            FileUtils.generateFolderStructure("shaders/programs/");
                        }
                        if (ImGui.menuItem("Core")) {
                            FileUtils.generateFolderStructure("shaders/core/");
                        }
                        ImGui.endMenu();
                    }
                    if (ImGui.beginMenu("Sounds")) {
                        if (ImGui.menuItem("All")) {
                            FileUtils.generateFolderStructure("sounds/block/");
                            FileUtils.generateFolderStructure("sounds/records/");
                        }
                        ImGui.separator();
                        if (ImGui.menuItem("Block")) {
                            FileUtils.generateFolderStructure("sounds/block/");
                        }
                        if (ImGui.menuItem("Records")) {
                            FileUtils.generateFolderStructure("sounds/records/");
                        }
                        ImGui.endMenu();
                    }
                    if (ImGui.menuItem("Lang")) {
                        FileUtils.generateFolderStructure("lang/");
                    }
                    if (ImGui.menuItem("Blockstates")) {
                        FileUtils.generateFolderStructure("blockstates/");
                    }
                    if (ImGui.menuItem("Font")) {
                        FileUtils.generateFolderStructure("font/");
                    }
                    ImGui.endMenu();
                }
                ImGui.endMenu();
            }

            ImGui.separator();
            if (ImGui.beginMenu("Pack")) {
                if (ImGui.menuItem("Load")) {
                    selectedFile = path;
                    // Open the select pack window
                    PackBrowserWindow.open.set(true);
                }
                if (ImGui.menuItem("Close")) {
                    selectedFile = null;
                    PackifiedClient.currentPack = null; // Close the current pack
                    ModConfig.savePackStatus(null);
                }
                if (ImGui.menuItem("Reload")) {
                    selectedFile = path;
                    // Reload the current pack
                    CompletableFuture.runAsync(PackUtils::reloadPack);
                }
                ImGui.separator();
                if (ImGui.menuItem("Export")) {
                    selectedFile = path;
                    // Export the current pack
                    PackUtils.exportPack();
                }
                ImGui.endMenu();
            }

            if (!isFolder) {
                ImGui.separator();

                if (ImGui.menuItem("Open")) {
                    selectedFile = path;

                    // Open file
                    FileUtils.openFile(path);
                }
                if (FileUtils.getExtension(path).equals(".json")) {
                    if (ExternalEditorHelper.findJSONEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findJSONEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findJSONEditor().get(), path);
                        }
                    }
                }
                if (FileUtils.getExtension(path).equals(".png")) {
                    if (ExternalEditorHelper.findImageEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findImageEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findJSONEditor().get(), path);
                        }
                    }
                }
                if (FileUtils.getExtension(path).equals(".ogg")) {
                    if (ExternalEditorHelper.findAudioEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findAudioEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findJSONEditor().get(), path);
                        }
                    }
                }
                ImGui.separator();
                if (ImGui.beginMenu("Refactor")) {
                    if (ImGui.menuItem("Rename")) {
                        // Create a new file
                        isEditingName = true;
                        fileNameInput.set(selectedFile.getFileName().toString());
                        selectedFileName = path.getFileName().toString();
                    }
                    if (ImGui.menuItem("Move")) {
                        // Create a new folder
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
                if (ImGui.menuItem("Duplicate")) {
                    ModifyFileWindow.open("Duplicate File", path, (fileIdentifier) -> {
                        // Create the file
                        String fileName = fileIdentifier.substring(fileIdentifier.lastIndexOf('/') + 1);
                        Path newPath = path.getParent().resolve(fileName);
                        String content = FileUtils.readFile(path);
                        FileUtils.saveSingleFile(newPath, FileUtils.getFileExtension(fileName), content, PackifiedClient.currentPack);
                    });
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

    public void setCurrentDirectory(Path of) {
        this.currentDirectory = of;
    }

    public Object getCurrentDirectory() {
        return currentDirectory;
    }
}
