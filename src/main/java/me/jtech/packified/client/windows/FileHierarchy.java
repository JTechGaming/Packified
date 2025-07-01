package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class FileHierarchy {
    public static ImBoolean isOpen = new ImBoolean(true);

    private static boolean fileSelect;
    private Path filePath;
    private final Map<String, FileHierarchy> children = new HashMap<>();

    private static ImString searchQuery = new ImString();
    private static String selectedExtension = "none";
    private static Path selectedFile;
    public static final String[] extensions = {
            "none", ".png", ".json", ".ogg", ".mcmeta", ".txt", ".properties", ".vsh", ".fsh", ".bbmodel", ".bbmodel.json"
    };
    private static float itemHoverTime = 0.0f;
    private static String selectedFileName = null;
    private static boolean alreadyRenderedHoverThisFrame = false;

    private static boolean isEditingName = false;
    private static ImString fileNameInput = new ImString(100);

    private static boolean isCreatingNewFile = false;
    private static Path selectedFileForCreation = null;

    private static FileHierarchy cachedHierarchy = null;
    private static PackWatcher watcher;

    private static final Map<Path, Integer> textureCache = new HashMap<>();

    private static int inportIcon = ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_import.png");
    private static int deleteIcon = ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_delete.png");

    private static int getOrLoadTexture(Path filePath) {
        if (textureCache.size() >= 40 && !textureCache.containsKey(filePath)) { // prevent the cache from growing too large
            textureCache.clear();
        }
        return textureCache.computeIfAbsent(filePath, path -> {
            BufferedImage image = ImGuiImplementation.getBufferedImageFromPath(path);
            return image != null ? ImGuiImplementation.loadTextureFromBufferedImage(image) : -1;
        });
    }

    public static FileHierarchy getCachedHierarchy(Path rootPath) {
        if (watcher != null && !watcher.rootPath.equals(rootPath)) {
            try {
                watcher.stop();
            } catch (IOException e) {
                e.printStackTrace();
            }
            watcher = null; // Reset the watcher if the root path has changed
            cachedHierarchy = null; // Reset the cached hierarchy
            LogWindow.addDebugInfo("PackWatcher: Stopped and reset due to root path change.");
        }
        if (watcher == null) {
            try {
                watcher = new PackWatcher(rootPath);
                watcher.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (watcher != null) watcher.stop();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        }

        if (cachedHierarchy == null || watcher.isInvalidated()) {
            LogWindow.addDebugInfo("PackWatcher: Successfully rebuilt file hierarchy");
            cachedHierarchy = buildFileHierarchy(rootPath);
            if (watcher != null) watcher.resetInvalidated();
        }

        return cachedHierarchy;
    }

    public static void clearCache() {
        textureCache.clear();
        cachedHierarchy = null;
        if (watcher != null) {
            try {
                watcher.stop();
            } catch (IOException e) {
                e.printStackTrace();
            }
            watcher = null; // Reset the watcher
        }
        LogWindow.addDebugInfo("PackWatcher: Cache cleared and watcher reset.");
    }

    public FileHierarchy(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Recursively adds files and folders to their correct place in the hierarchy.
     */
    public void addFile(Path path) {
        // Ensure the relative path is calculated correctly
        Path relativePath = FileUtils.getPackFolderPath().relativize(path);
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
                // Add file to the hierarchy
                current.children.putIfAbsent(part, new FileHierarchy(path));
            } else {
                // Construct the full path for the folder using relativePath
                Path folderPath = FileUtils.getPackFolderPath().resolve(String.join("/", parts.subList(0, i + 1)));

                // Add folder to the hierarchy
                current.children.putIfAbsent(part, new FileHierarchy(folderPath));
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

            renderHoverTooltip(name, filePath, true);

            renderRightClickPopup(name, filePath, true);

            // Enable Drag & Drop Target for Folders
            if (ImGui.beginDragDropTarget()) {
                String payload = ImGui.acceptDragDropPayload("DND_FILE");
                if (payload != null) {
                    Path draggedFile = Path.of(payload);
                    FileUtils.moveFile(draggedFile, filePath.resolve(draggedFile.getFileName()).toString()); // Move the file to the correct location
                }
                ImGui.endDragDropTarget();
            }

            if (isOpen) {
                if (children.isEmpty()) {
                    try (Stream<Path> stream = Files.list(filePath)) {
                        stream.forEach(this::addFile); // lazy add files
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                for (Map.Entry<String, FileHierarchy> entry : children.entrySet()) {
                    entry.getValue().renderTree(entry.getKey());
                }

                if (isCreatingNewFile && filePath.equals(selectedFileForCreation)) {
                    ImGui.inputText("##FileNameInput", fileNameInput, ImGuiInputTextFlags.EnterReturnsTrue);
                    if (ImGui.isItemDeactivatedAfterEdit()) {
                        isCreatingNewFile = false;
                        selectedFileForCreation = null;
                        System.out.println(filePath);
                        Path newPath = filePath.resolve(fileNameInput.get());
                        FileUtils.saveSingleFile(newPath, FileUtils.getFileExtension(newPath.toString()), "", PackifiedClient.currentPack);
                    }
                }

                ImGui.treePop();
            }
        }
    }

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
                    int texId = getOrLoadTexture(filePath);
                    if (texId != -1) {
                        ImGui.image(texId, 100, 100);
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

    private void drawFile(String name, Path filePath) {
        if (filePath == null) return;
        if (isEditingName && filePath.equals(selectedFile)) {
            ImGui.inputText("##FileNameInput", fileNameInput, ImGuiInputTextFlags.EnterReturnsTrue);
            if (ImGui.isItemDeactivatedAfterEdit()) {
                isEditingName = false;
                selectedFileName = null; // Reset the selected file name
                System.out.println(filePath);
                FileUtils.renameFile(filePath, fileNameInput.get());
            }
        } else {
            if (ImGui.selectable(name, Objects.equals(selectedFile, filePath)) && !isEditingName && !isCreatingNewFile) {
                selectedFile = filePath;
                FileUtils.openFile(filePath);
            }
        }

        renderHoverTooltip(name, filePath, false);

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
        if (!isOpen.get()) {
            return; // If the window is not open, do not render
        }
        alreadyRenderedHoverThisFrame = false;

        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.begin("File Hierarchy", isOpen);

        ImGuiImplementation.pushWindowCenterPos();

        if (TutorialHelper.beginTutorialFocus(TutorialHelper.HIERARCHY_STAGE, "You can search.")) {
            // Render a semi-transparent overlay to blur the rest of the UI
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 4.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, 0xFFFF0000);
        }
        ImGui.inputText("Search", searchQuery);
        if (TutorialHelper.endTutorialFocus()) {
            ImGui.popStyleVar();
            ImGui.popStyleColor();
        }

        if (TutorialHelper.beginTutorialFocus(TutorialHelper.HIERARCHY_STAGE, "You can filter.")) {
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 4.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, 0xFFFF0000);
        }
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
        if (TutorialHelper.endTutorialFocus()) {
            ImGui.popStyleVar();
            ImGui.popStyleColor();
        }

        if (PackifiedClient.currentPack != null) {
            ImGui.imageButton(inportIcon, 14, 14);
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
            ImGui.imageButton(deleteIcon, 14, 14);
            if (ImGui.isItemClicked()) {
                // Logic to delete a file
                ConfirmWindow.open("delete this file", "The file will be lost forever.", () -> {
                    FileUtils.deleteFile(selectedFile);
                });
            }

            Path packPath = getPackFolderPath();
            if (packPath != null && Files.exists(packPath)) {
                FileHierarchy root = getCachedHierarchy(packPath);
                if (ImGui.beginTable("FileHierarchyTable", 3, ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY)) {
                    ImGui.tableSetupColumn("Name");
                    ImGui.tableSetupColumn("Size");
                    ImGui.tableSetupColumn("Type");
                    ImGui.tableHeadersRow();

                    // Render File Hierarchy Tree
                    root.renderTree(packPath.getFileName().toString());

                    ImGui.endTable();
                }

                if (!fileSelect) {
                    if (ImGui.beginPopupContextItem("##FileHierarchyPopupNoSelection")) {
                        // Open file
                        if (ImGui.beginMenu("New")) {
                            if (ImGui.menuItem("Pack")) {
                                // Create a new pack
                                PackCreationWindow.isOpen.set(true);
                            }
                            ImGui.endMenu();
                        }
                        if (ImGui.menuItem("Load Pack")) {
                            // Open the select pack window
                            SelectPackWindow.open.set(true);
                        }
                        ImGui.endPopup();
                    }
                }
                fileSelect = false;
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
                SelectPackWindow.open.set(true);
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

    private void renderRightClickPopup(String name, Path path, boolean isFolder) {
        if (ImGui.beginPopupContextItem(name)) {
            fileSelect = true;
            selectedFile = path;
            // Open file
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
                    SelectPackWindow.open.set(true);
                }
                if (ImGui.menuItem("Close")) {
                    selectedFile = null;
                    PackifiedClient.currentPack = null; // Close the current pack
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
                        String newIdentifierPath = path.toString().substring(0, path.toString().lastIndexOf('/') + 1) + fileName;
                        Path newPath = FileUtils.validateIdentifier(newIdentifierPath);
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

    @FunctionalInterface
    public interface FileModifyAction {
        void execute(String fileName);
    }
}
