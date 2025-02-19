package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.internal.flag.ImGuiItemFlags;
import imgui.type.ImString;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileDialog;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

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

    public void renderTree(String name) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        if (children.isEmpty()) { // File
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
                drawFolder();
                for (Map.Entry<String, FileHierarchy> entry : children.entrySet()) {
                    entry.getValue().renderTree(entry.getKey());
                }
                ImGui.treePop();
            }

            renderRightClickPopup(name, identifier, true);

            if (ImGui.beginDragDropTarget()) {
                if (ImGui.acceptDragDropPayload("DND_FILE") != null) {
                    String payload = ImGui.getDragDropPayload();
                    // Logic to move the file to the new location
                    FileUtils.sendDebugChatMessage("Moving file " + payload + " to " + identifier);
                    moveFile(payload, identifier);
                }
                ImGui.endDragDropTarget();
            }
        }
    }

    private static void drawFile(String name, Identifier identifier) {
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
            if (ImGui.beginMenu("New")) {
                if (ImGui.menuItem("File")) {
                    // Create a new file
                    //TODO add create file logic
                    if (ImGui.beginPopupContextItem()) {
                        ImGui.inputText("File Name:", new ImString());
                        ImGui.sameLine();
                        if (ImGui.button("Create")) {
                            // Create the file
                            //TODO add create file logic
                        }
                        ImGui.endPopup();
                    }
                }
                if (ImGui.menuItem("Folder")) {
                    // Create a new folder
                    //TODO add create folder logic
                    if (ImGui.beginPopupContextItem()) {
                        ImGui.inputText("File Name:", new ImString());
                        ImGui.sameLine();
                        if (ImGui.button("Create")) {
                            // Create the file
                            //TODO add create file logic
                        }
                        ImGui.endPopup();
                    }
                }
                ImGui.endMenu();
            }
            if (!isFolder) {
                if (ImGui.menuItem("Open")) {
                    selectedName = name;
                    selectedIdentifier = identifier;
                    // Open file
                    FileUtils.openFile(identifier, FileUtils.getExtension(identifier));
                }
            }
            if (ImGui.menuItem("Delete")) {
                // Delete file
                FileUtils.deleteFile(identifier);
            }
            ImGui.endPopup();
        }
    }

    public static void moveFile(String sourceIdentifier, Identifier targetIdentifier) {
        // Logic to move the file from sourceIdentifier to targetIdentifier
        try (ResourcePack resourcePack = PackifiedClient.currentPack.createResourcePack()) {
            InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, Identifier.of(sourceIdentifier))).get();
            byte[] fileData = inputStream.readAllBytes();

            // Save the file data to the new location
            // This is a placeholder for the actual save logic
            // You need to implement the logic to save the fileData to the targetIdentifier location
            //TODO implement moving logic
            FileUtils.saveFile(targetIdentifier, FileUtils.getFileExtension(targetIdentifier.getPath()), new String(fileData, StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            //if (ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_import.png"), 14, 14)) {
            if (ImGui.button("Import")) {
                // Logic to import a file
                String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified").toString();
                FileDialog.openFileDialog(defaultFolder, "Files", "json", "png").thenAccept(pathStr -> {
                    System.out.println("Opening file: " + pathStr);
                    if (pathStr != null) {
                        Path path = Path.of(pathStr);
                        MinecraftClient.getInstance().submit(() -> {
                            try {
                                String extension = FileUtils.getFileExtension(path.getFileName().toString());
                                if (extension.equals(".png")) {
                                    BufferedImage image = ImageIO.read(path.toFile());
                                    //openImageFiles.add(new ImageFile(path.getFileName().toString(), image));
                                    //TODO add file to the hierarchy and to the pack
                                } else if (extension.equals(".json")) {
                                    //openJsonFiles.add(new JsonFile(path.getFileName().toString(), Files.readString(path)));
                                    //TODO add file to the hierarchy and to the pack
                                } else if (extension.equals(".ogg")) {
                                    //openSoundFiles.add(new SoundFile(path.getFileName().toString(), Files.readAllBytes(path)));
                                    //TODO add file to the hierarchy and to the pack
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                });
            }
            ImGui.sameLine();
            if (ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_delete.png"), 14, 14)) {
                // Logic to delete a file
                FileUtils.deleteFile(selectedIdentifier);
            }
            if (ImGui.beginTable("FileHierarchyTable", 3, ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.NoBordersInBody
                    | ImGuiTableFlags.BordersOuter | ImGuiTableFlags.ScrollX | ImGuiTableFlags.ScrollY)) {
                ImGui.tableSetupColumn("Name");
                ImGui.tableSetupColumn("Size");
                ImGui.tableSetupColumn("Type");
                ImGui.tableHeadersRow();
                ImGui.tableNextRow();
                drawFolder();
                if (ImGui.treeNode(PackifiedClient.currentPack.getDisplayName().getString())) {
                    ImGui.tableNextRow();
                    drawFolder();
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
                            root.renderTree(namespace);

                            ImGui.treePop(); // Close "assets"
                        }
                    }
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    String mcmeta = "pack.mcmeta";
                    if (ImGui.selectable(mcmeta, selectedName.equals(mcmeta), ImGuiSelectableFlags.AllowDoubleClick | ImGuiDragDropFlags.SourceExtern)) {
                        if (ImGui.isMouseDoubleClicked(0)) {
                            selectedName = mcmeta;
                            selectedIdentifier = Identifier.of("pack.mcmeta"); // TODO this doesnt work
                            // Open file
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
                            selectedIdentifier = Identifier.of("pack.png"); // TODO this doesnt work
                            // Open file
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

        if (ImGui.beginPopupContextItem("FileHierarchyRightClickPopup")) {
            if (ImGui.beginMenu("New")) {
                if (ImGui.menuItem("File")) {
                    // Create a new file
                    //TODO add create file logic
                    if (ImGui.beginPopupContextItem()) {
                        ImGui.inputText("File Name:", new ImString());
                        ImGui.sameLine();
                        if (ImGui.button("Create")) {
                            // Create the file
                            //TODO add create file logic
                        }
                        ImGui.endPopup();
                    }
                }
                if (ImGui.menuItem("Folder")) {
                    // Create a new folder
                    //TODO add create folder logic
                    if (ImGui.beginPopupContextItem()) {
                        ImGui.inputText("File Name:", new ImString());
                        ImGui.sameLine();
                        if (ImGui.button("Create")) {
                            // Create the file
                            //TODO add create file logic
                        }
                        ImGui.endPopup();
                    }
                }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("Load Pack")) {
                for (int i = 0; i < PackUtils.refresh().size(); i++) {
                    if (ImGui.menuItem(PackUtils.refresh().get(i).getDisplayName().getString())) {
                        EditorWindow.openFiles.clear();
                        PackifiedClient.currentPack = PackUtils.refresh().get(i);
                    }
                }
                ImGui.endMenu();
            }
            ImGui.endPopup();
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

    public static void drawFolder() {
        ImGui.tableSetColumnIndex(1);
        ImGui.text("---");
        ImGui.tableSetColumnIndex(2);
        ImGui.text("Folder");
        ImGui.tableSetColumnIndex(0);
    }
}