// java
package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.helpers.ExternalEditorHelper;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.util.SafeTextureLoader;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import me.jtech.packified.client.windows.popups.PackBrowserWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class FileExplorerWindow {
    public static List<FileExplorerWindow> explorers = new ArrayList<>();

    public ImBoolean isOpen = new ImBoolean(false);
    private Path currentDirectory;
    public final int i;
    private final ImString searchInput = new ImString(100);
    private final ImInt extensionFilter = new ImInt(0); // 0: All, 1: Models, 2: Textures, 3: Sounds
    public final boolean allowFolderSelection;
    private final Consumer<Path> onSelect;

    private File[] cachedFiles;
    private Path cachedDirectory;

    private static final Set<String> MODEL_EXTS = Set.of(".json", ".obj", ".bbmodel");
    private static final Set<String> IMAGE_EXTS = Set.of(".png", ".jpg", ".jpeg", ".bmp");
    private static final Set<String> SOUND_EXTS = Set.of(".ogg", ".wav", ".mp3");

    private void refreshDirectoryCache() {
        if (!Objects.equals(cachedDirectory, currentDirectory)) {
            cachedDirectory = currentDirectory;
            cachedFiles = currentDirectory.toFile().listFiles(f -> !f.isHidden());
        }
    }

    private static final Map<Path, FileTooltipData> TOOLTIP_CACHE = new HashMap<>();

    private static FileTooltipData getTooltipData(Path path, boolean isFolder) {
        return TOOLTIP_CACHE.computeIfAbsent(path, p -> FileTooltipData.compute(p, isFolder));
    }

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FileExplorer-IO");
        t.setDaemon(true);
        return t;
    });

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
        for (int idx = explorers.size() - 1; idx >= 0; idx--) {
            explorers.get(idx).render();
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

        if (PackHelper.isValid() && !this.currentDirectory.toString().contains(FileUtils.getPackFolderPath().toString())) {
            currentDirectory = null;
        }

        alreadyRenderedHoverThisFrame = false;

        ImGuiImplementation.saveExplorers();

        var currentPack = PackHelper.getCurrentPack().createResourcePack();

        if (PackHelper.isValid()) {
            Path packFolder = PackUtils.getPackFolder(currentPack);
            // Only initialize/reset when there's no current directory or the pack changed
            if (currentDirectory == null) {
                currentDirectory = packFolder;
            }
        }

        if (ImGui.begin("File Explorer " + (i + 1), isOpen) && PackHelper.isValid()) {
            if (ImGui.beginChild("Left Panel##" + i, 220f, 0f, true)) {
                Path tempPath = currentDirectory;
                Path packPath = PackUtils.getPackFolder(currentPack);
                if (packPath != null) {
                    Path root = packPath.getParent();
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
            }
            ImGui.endChild();

            ImGui.sameLine();

            if (ImGui.beginChild("Center Panel##" + i, 0f, 0f, true)) {
                refreshDirectoryCache();
                if (cachedFiles == null || cachedFiles.length == 0) {
                    ImGuiImplementation.centeredText("This folder is empty.");
                }
                ImGui.inputText("Search##" + i, searchInput, ImGuiInputTextFlags.None);
                ImGui.sameLine();
                ImGui.combo("Extensions##" + i, extensionFilter, new String[]{"All", "Models", "Textures", "Sounds"}, 0);
                ImGui.separator();

                String search = searchInput.get().toLowerCase(Locale.ROOT);

                for (File file : cachedFiles) {
                    if (!passesFilter(file, search)) continue;
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
                }

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
            if (PackHelper.isInvalid()) {
                ImGuiImplementation.centeredText("No pack loaded.");
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private void invalidateCache() {
        cachedDirectory = null;
    }

    public static void invalidateAllCaches() { // Called when PackWatcher detects a filesystem change in pack directory
        for (int idx = explorers.size() - 1; idx >= 0; idx--) {
            explorers.get(idx).invalidateCache();
        }
        TOOLTIP_CACHE.clear();
    }

    private boolean passesFilter(File file, String search) {
        if (!search.isEmpty() && !file.getName().toLowerCase().contains(search)) {
            return false;
        }

        String ext = FileUtils.getFileExtension(file.getName());
        return switch (extensionFilter.get()) {
            case 1 -> MODEL_EXTS.contains(ext);
            case 2 -> IMAGE_EXTS.contains(ext);
            case 3 -> SOUND_EXTS.contains(ext);
            default -> true;
        };
    }

    private Path hoveredPath;
    private float hoverTime;
    private String selectedFileName = null;
    private boolean alreadyRenderedHoverThisFrame = false;

    private void renderHoverTooltip(String name, Path filePath, boolean isFolder) {
        if (alreadyRenderedHoverThisFrame) return; // Prevent multiple tooltips from rendering in the same frame
        if (!name.equals(selectedFileName) && selectedFileName != null) {
            return;
        }

        FileTooltipData tooltipData = getTooltipData(filePath, isFolder);

        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenBlockedByPopup) && !ImGui.isPopupOpen(name)) {
            if (!filePath.equals(hoveredPath)) {
                hoveredPath = filePath;
                hoverTime = 0f;
            }

                alreadyRenderedHoverThisFrame = true;

                ImGui.beginTooltip();

                ImGui.setNextWindowSize(200, 200);
                if (FileUtils.getFileExtension(filePath.getFileName().toString()).equalsIgnoreCase(".png")) {
                    if (tooltipData.textureId != -1) {
                        ImGui.image(tooltipData.textureId, 100, 100);
                    }
                }
                ImGui.text(String.format("Path: %s\nSize: %s" + (isFolder ? "\nType: %s\nFiles: %s" : "\nType: %s%s"),
                        FileUtils.getRelativePackPath(filePath),
                        tooltipData.size,
                        tooltipData.type,
                        tooltipData.fileCount
                ));

                ImGui.endTooltip();
        } else if (filePath.equals(hoveredPath)) {
            hoveredPath = null;
            hoverTime = 0f;
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
                if (FileUtils.getExtensionFromPath(path).equals(".json")) {
                    if (ImGui.menuItem("Open in model editor")) {
                        ModelEditorWindow.loadModel(path.toString());
                        if (!ModelEditorWindow.isModelWindowOpen()) {
                            ModelEditorWindow.isOpen.set(true);
                        }
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
                    PackHelper.closePack();
                    ModConfig.savePackStatus(null);
                }
                if (ImGui.menuItem("Reload")) {
                    selectedFile = path;
                    // Reload the current pack
                    CompletableFuture.runAsync(PackUtils::reloadPack, IO_EXECUTOR);
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
                if (FileUtils.getExtensionFromPath(path).equals(".bbmodel")) {
                    if (ExternalEditorHelper.findBlockBenchEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findBlockBenchEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findBlockBenchEditor().get(), path);
                        }
                    }
                }
                if (FileUtils.getExtensionFromPath(path).equals(".json")) {
                    if (ExternalEditorHelper.findJSONEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findJSONEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findJSONEditor().get(), path);
                        }
                    }
                }
                if (FileUtils.getExtensionFromPath(path).equals(".png")) {
                    if (ExternalEditorHelper.findImageEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findImageEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findImageEditor().get(), path);
                        }
                    }
                }
                if (FileUtils.getExtensionFromPath(path).equals(".ogg")) {
                    if (ExternalEditorHelper.findAudioEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findAudioEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findAudioEditor().get(), path);
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
                        FileUtils.saveSingleFile(newPath, FileUtils.getFileExtension(fileName), content, PackHelper.getCurrentPack());
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
                ConfirmWindow.open("Are you sure you want to delete this file", "The file will be lost forever.", () -> {
                    FileUtils.deleteFile(path);
                });
            }
            ImGui.endPopup();
        }
    }

    record FileTooltipData(
            String size,
            String type,
            String fileCount,
            int textureId
    ) {
        static FileTooltipData compute(Path filePath, boolean isFolder) {
            return new FileTooltipData(
                    isFolder ? FileUtils.formatFileSize(FileUtils.getFolderSize(filePath)) : FileUtils.formatFileSize(FileUtils.getFileSize(filePath)),
                    isFolder ? "Folder" : FileUtils.formatExtension(FileUtils.getFileExtension(filePath.getFileName().toString())),
                    isFolder ? FileUtils.getFolderFileCount(filePath) + "" : "",
                    isFolder || !FileUtils.getFileExtension(filePath.getFileName().toString()).equals(".png")
                            ? -1
                            : SafeTextureLoader.load(filePath)
            );
        }
    }

    public void setCurrentDirectory(Path of) {
        this.currentDirectory = of;
    }

    public Object getCurrentDirectory() {
        return currentDirectory;
    }
}
