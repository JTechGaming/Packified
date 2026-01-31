package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.helpers.DisplayScaleHelper;
import me.jtech.packified.client.helpers.ExternalEditorHelper;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.helpers.TutorialHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.*;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import me.jtech.packified.client.windows.popups.PackBrowserWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFWDropCallback;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.lwjgl.glfw.GLFW.glfwSetDropCallback;

@Environment(EnvType.CLIENT)
public class FileHierarchyWindow {
    public static ImBoolean isOpen = new ImBoolean(true);

    private static boolean fileSelect;
    private final Path filePath;
    private final Map<String, FileHierarchyWindow> children = new HashMap<>();

    private static final ImString searchQuery = new ImString();
    private static String lastFrameSearch = searchQuery.get();
    private static String selectedExtension = "none";
    private static String lastFrameExtension = selectedExtension;
    private static Path selectedFile;
    public static final String[] extensions = { // These are only used for filtering, so if a user wants to filter by a custom extension they can type it in the search bar, and if a type is unknown it doesn't really affect other code
            "none", ".png", ".json", ".ogg", ".mcmeta", ".txt", ".properties", ".vsh", ".fsh", ".glsl", ".bbmodel", ".bbmodel.json"
    };
    private static float itemHoverTime = 0.0f;
    private static String selectedFileName = null;
    private static boolean alreadyRenderedHoverThisFrame = false;

    private static boolean isEditingName = false;
    private static final ImString fileNameInput = new ImString(100);

    private static boolean isCreatingNewFile = false;
    private static Path selectedFileForCreation = null;

    public static FileHierarchyWindow cachedHierarchy = null;

    private static final Map<Path, Integer> textureCache = new HashMap<>();

    private static final int inportIcon = ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_import.png");
    private static final int deleteIcon = ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_delete.png");

    @Deprecated
    private static int getOrLoadTexture(Path filePath) {
        if (textureCache.size() >= 40 && !textureCache.containsKey(filePath)) { // prevent the cache from growing too large
            textureCache.clear();
        }
        return textureCache.computeIfAbsent(filePath, ImGuiImplementation::loadTextureFromPath);
    }

    public static FileHierarchyWindow getCachedHierarchy(Path rootPath) {
        if (PackHelper.getWatcher() == null) return cachedHierarchy;
        if (cachedHierarchy == null || PackHelper.getWatcher().isInvalidated() || !lastFrameSearch.equals(searchQuery.get()) || !selectedExtension.equals(lastFrameExtension)) {
            LogWindow.addDebugInfo("PackWatcher: Successfully rebuilt file hierarchy");
            cachedHierarchy = buildFileHierarchy(rootPath);
            if (PreferencesWindow.autoReloadAssets.get()) {
                CompletableFuture.runAsync(PackUtils::reloadPack);
            }
            if (PackHelper.getWatcher() != null) PackHelper.getWatcher().resetInvalidated();
        }

        return cachedHierarchy;
    }

    public static void clearCache() {
        textureCache.clear();
        if (PackHelper.isValid()) {
            PackHelper.disposeWatcher();
        }
        LogWindow.addDebugInfo("PackWatcher: Cache cleared and watcher reset.");
    }

    public FileHierarchyWindow(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Recursively adds files and folders to their correct place in the hierarchy.
     */
    public void addFile(Path path) {
        // Ensure the relative path is calculated correctly
        if (FileUtils.getPackFolderPath() == null) return;
        Path relativePath = FileUtils.getPackFolderPath().relativize(path);
        List<String> parts = new ArrayList<>();

        for (Path p : relativePath) {
            if (!p.toString().isEmpty()) {
                parts.add(p.toString());
            }
        }

        if (parts.isEmpty()) return;

        FileHierarchyWindow current = this;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (i == parts.size() - 1 && Files.isRegularFile(path)) {
                // Add file to the hierarchy
                current.children.putIfAbsent(part, new FileHierarchyWindow(path));
            } else {
                // Construct the full path for the folder using relativePath
                Path folderPath = FileUtils.getPackFolderPath().resolve(String.join("/", parts.subList(0, i + 1)));

                // Add folder to the hierarchy
                current.children.putIfAbsent(part, new FileHierarchyWindow(folderPath));
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
                        LogWindow.addError(e.getMessage());
                    }
                }
                for (Map.Entry<String, FileHierarchyWindow> entry : children.entrySet()) {
                    entry.getValue().renderTree(entry.getKey());
                }

                if (isCreatingNewFile && filePath.equals(selectedFileForCreation)) {
                    ImGui.inputText("##FileNameInput", fileNameInput, ImGuiInputTextFlags.EnterReturnsTrue);
                    if (ImGui.isItemDeactivatedAfterEdit()) {
                        isCreatingNewFile = false;
                        selectedFileForCreation = null;
                        System.out.println(filePath);
                        Path newPath = filePath.resolve(fileNameInput.get());
                        FileUtils.saveSingleFile(newPath, FileUtils.getFileExtension(newPath.toString()), "", PackHelper.getCurrentPack());
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
        if (!isOpen.get() || ModelEditorWindow.isModelWindowFocused()) return;

        alreadyRenderedHoverThisFrame = false;

        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        ImGui.begin("File Hierarchy", isOpen);

        // Register the drop callback after window creation
        GLFWDropCallback dropCallback = new GLFWDropCallback() {
            @Override
            public void invoke(long window, int count, long names) {
                for (int i = 0; i < count; i++) {
                    String path = GLFWDropCallback.getName(names, i);
                    FileUtils.importFile(Path.of(path));
                }
            }
        };
        glfwSetDropCallback(MinecraftClient.getInstance().getWindow().getHandle(), dropCallback);

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

        int buttonSize = DisplayScaleHelper.getUIButtonSize();

        if (PackHelper.isValid() && getCachedHierarchy(getPackFolderPath()) != null) {
            ImGui.imageButton(inportIcon, buttonSize, buttonSize);
            if (ImGui.isItemClicked()) {
                // Logic to import a file
                String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified").toString();
                FileDialog.openFileDialog(defaultFolder, "Files").thenAccept(pathStr -> {
                    if (pathStr != null) {
                        Path path = Path.of(pathStr);
                        MinecraftClient.getInstance().submit(() -> FileUtils.importFile(path));
                    }
                });
            }
            ImGui.sameLine();
            ImGui.imageButton(deleteIcon, buttonSize, buttonSize);
            if (ImGui.isItemClicked()) {
                // Logic to delete a file
                ConfirmWindow.open("Are you sure you want to delete this file", "The file will be lost forever.", () -> FileUtils.deleteFile(selectedFile));
            }

            Path packPath = getPackFolderPath();
            if (packPath != null && Files.exists(packPath)) {
                FileHierarchyWindow root = getCachedHierarchy(packPath);
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
                            PackBrowserWindow.open.set(true);
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
                PackBrowserWindow.open.set(true);
            }
        }

        ImGui.end();

        lastFrameExtension = selectedExtension;
        lastFrameSearch = searchQuery.get();
    }

    public static Path getPackFolderPath() {
        if (PackHelper.isInvalid()) return null;
        return FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(PackHelper.getCurrentPack().getDisplayName().getString());
    }

    public static FileHierarchyWindow buildFileHierarchy(Path rootPath) {
        FileHierarchyWindow root = new FileHierarchyWindow(rootPath);
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> sortedPaths = paths
                    .filter(Files::exists)
                    .filter(path -> searchQuery.get().isEmpty() || path.toString().toLowerCase().contains(searchQuery.get().toLowerCase()))
                    .filter(path -> selectedExtension.equals("none") || path.toString().toLowerCase().endsWith(selectedExtension.toLowerCase()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path path : sortedPaths) {
                root.addFile(path);
            }
        } catch (IOException e) {
            LogWindow.addError(e.getMessage());
        }
        return root;
    }

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
                    CompletableFuture.runAsync(PackUtils::reloadPack);
                }
                ImGui.separator();
                if (ImGui.menuItem("Export")) {
                    selectedFile = path;
                    PackExporterWindow.isOpen.set(!PackExporterWindow.isOpen.get());
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
                ConfirmWindow.open("Are you sure you want to delete this file", "The file will be lost forever.", () -> FileUtils.deleteFile(path));
            }
            ImGui.endPopup();
        }
    }

    @FunctionalInterface
    public interface FileModifyAction {
        void execute(String fileName);
    }
}
