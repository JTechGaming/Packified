package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Environment(EnvType.CLIENT)
public class EditorWindow {
    public static List<PackFile> openFiles = new ArrayList<>();
    public static ImBoolean isOpen = new ImBoolean(false);
    private static ImInt toolSize = new ImInt(1);

    private static float[] color = new float[]{1.0f, 1.0f, 1.0f};

    public static List<PackFile> changedAssets = new ArrayList<>();

    private enum Tool {
        PEN,
        PAINT_BUCKET,
        SELECT,
        ERASER
    }

    private static Tool currentTool = Tool.PEN;

    private static PackFile currentFile;

    public static void render() {
        // Editor window code
        if (ImGui.begin("File Editor", isOpen, ImGuiWindowFlags.MenuBar)) {
            if (ImGui.beginMenuBar()) {
                if (ImGui.beginMenu("File")) {
                    if (ImGui.menuItem("Open")) {
                        String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified").toString();
                        FileDialog.openFileDialog(defaultFolder, "Files", "json", "png").thenAccept(pathStr -> {
                            if (pathStr != null) {
                                Path path = Path.of(pathStr);
                                MinecraftClient.getInstance().submit(() -> {
                                    try {
                                        String extension = FileUtils.getFileExtension(path.getFileName().toString());
                                        if (extension.equals(".png")) {
                                            BufferedImage image = ImageIO.read(path.toFile());
                                            openFiles.add(new PackFile(path.getFileName().toString(), image));
                                        } else if (extension.equals(".json")) {
                                            openFiles.add(new PackFile(path.getFileName().toString(), Files.readString(path), FileHierarchy.FileType.JSON));
                                        } else if (extension.equals(".ogg")) {
                                            byte[] bytes = Files.readAllBytes(path);
                                            String base64 = Base64.getEncoder().encodeToString(bytes);
                                            String content = "data:audio/ogg;base64," + base64;
                                            openFiles.add(new PackFile(path.getFileName().toString(), content, FileHierarchy.FileType.OGG)); //TODO add audio support
                                        }
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            }
                        });
                    }
                    if (!openFiles.isEmpty()) {
                        renderTabPopup();
                    }
                    ImGui.endMenu();
                }
                ImGui.endMenuBar();
            }

            if (ImGui.beginTabBar("FileEditorTabs", ImGuiTabBarFlags.Reorderable | ImGuiTabBarFlags.AutoSelectNewTabs | ImGuiTabBarFlags.TabListPopupButton)) {
                // Example of adding tabs dynamically
                for (int i = 0; i < openFiles.size(); i++) {
                    if (ImGui.beginTabItem(openFiles.get(i).getFileName() + "##" + i, ImGuiTabItemFlags.None | (openFiles.get(i).isModified() ? ImGuiTabItemFlags.UnsavedDocument : 0))) {
                        if (openFiles.get(i).isModified()) {
                            changedAssets.add(openFiles.get(i));
                        }
                        currentFile = openFiles.get(i);
                        // Render the JSON editor for the current file
                        switch (openFiles.get(i).getExtension()) {
                            case JSON, MC_META, FSH, VSH, PROPERTIES, TEXT:
                                renderTextFileEditor(openFiles.get(i));
                                break;
                            case PNG:
                                renderImageFileEditor(openFiles.get(i));
                                break;
                            case OGG:
                                renderAudioFileEditor(openFiles.get(i));
                                break;
                            default:
                                ImGui.text("The " + openFiles.get(i).getExtension() + " file type can not be edited yet.");
                                break;
                        }
                        ImGui.endTabItem();
                    }
                    if (ImGui.beginPopupContextItem(openFiles.get(i).getFileName())) {
                        renderTabPopup();
                        ImGui.endPopup();
                    }
                }
                ImGui.endTabBar();
            }
        }
        ImGui.end();
    }

    private static void renderAudioFileEditor(PackFile audioFile) {
        // Logic to render the audio editor for the given file
        //TODO add audio editor tools, play, pause, stop, etc.
        ImGui.text("Audio Editor");
    }

    private static void renderTabPopup() {
        if (ImGui.menuItem("Save")) { //TODO more file options
            // Logic to save the current JSON file
            FileUtils.saveFile(openFiles.get(0).getIdentifier(), FileUtils.getFileExtension(openFiles.get(0).getFileName()), openFiles.get(0).getTextEditorContent().get());
            //TODO make this work with all file types (not just string based)
        }
        if (ImGui.menuItem("Close")) {
            // Logic to close the current tab
            openFiles.remove(currentFile);
        }
    }

    private static void renderTextFileEditor(PackFile file) {
        // Logic to render the JSON editor for the given file
        //TODO add text editor tools, highlight syntax, json validation, etc.
        // can switch here to different editors based on file type,
        // for example, a text editor for .txt files, a json editor for .json files, etc.
        ImGui.inputTextMultiline("##source", file.getTextEditorContent(), ImGui.getWindowWidth() - 20, ImGui.getWindowHeight() - 40);
    }

    private static float[] zoomFactor = new float[]{1.0f};

    private static void renderImageFileEditor(PackFile imageFile) {
        // Image editor tools
        //TODO make all these tools work
        if (ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_pencil.png"), 14, 14)) {
            currentTool = Tool.PEN;
        }
        ImGui.sameLine();
        if (ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_bucket.png"), 14, 14)) {
            currentTool = Tool.PAINT_BUCKET;
        }
        ImGui.sameLine();
        if (ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_select.png"), 14, 14)) {
            currentTool = Tool.SELECT;
        }
        ImGui.sameLine();
        if (ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_eraser.png"), 14, 14)) {
            currentTool = Tool.ERASER;
        }
        ImGui.sameLine();
        ImGui.inputInt("Tool Size", toolSize);
        ImGui.colorEdit3("Color Picker", color);

        // Load the image from the file content
        BufferedImage bufferedImage = imageFile.getContent();
        if (bufferedImage != null) {
            int textureId = ImGuiImplementation.fromBufferedImage(bufferedImage);

            // Add zoom controls
            ImGui.sliderFloat("Zoom", zoomFactor, 0.1f, 5.0f);

            // Apply zoom factor
            float width = bufferedImage.getWidth() * zoomFactor[0];
            float height = bufferedImage.getHeight() * zoomFactor[0];

            ImGui.image(textureId, width, height);

            // Handle drawing tools
            handleDrawingTools(bufferedImage);
            imageFile.setContent(bufferedImage);
        } else {
            ImGui.text("Failed to load image");
        }
    }

    private static void handleDrawingTools(BufferedImage image) {
        // Logic to handle drawing tools
        if (ImGui.isMouseClicked(0)) {
            int mouseX = (int) (ImGui.getMousePosX() / zoomFactor[0]);
            int mouseY = (int) (ImGui.getMousePosY() / zoomFactor[0]);

            switch (currentTool) {
                case PEN:
                    drawPen(image, mouseX, mouseY);
                    break;
                case PAINT_BUCKET:
                    fill(image, mouseX, mouseY);
                    break;
                case SELECT:
                    // Handle selection logic
                    break;
                case ERASER:
                    erase(image, mouseX, mouseY);
                    break;
            }
        }
    }

    private static void drawPen(BufferedImage image, int x, int y) {
        for (int i = 0; i < toolSize.get(); i++) {
            for (int j = 0; j < toolSize.get(); j++) {
                if (x + i < image.getWidth() && y + j < image.getHeight()) {
                    image.setRGB(x + i, y + j, colorToRGB(color));
                }
            }
        }
    }

    private static void erase(BufferedImage image, int x, int y) {
        for (int i = 0; i < toolSize.get(); i++) {
            for (int j = 0; j < toolSize.get(); j++) {
                if (x + i < image.getWidth() && y + j < image.getHeight()) {
                    image.setRGB(x + i, y + j, 0x00000000); // Transparent
                }
            }
        }
    }

    private static void fill(BufferedImage image, int x, int y) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                image.setRGB(i, j, colorToRGB(color));
            }
        }
    }

    private static int colorToRGB(float[] color) {
        int r = (int) (color[0] * 255);
        int g = (int) (color[1] * 255);
        int b = (int) (color[2] * 255);
        return (r << 16) | (g << 8) | b;
    }

    public static void openTextFile(Identifier identifier, String content) {
        if (openFiles.stream().anyMatch(file -> file.getIdentifier().equals(identifier))) {
            return;
        }
        openFiles.add(new PackFile(identifier, content, FileUtils.getExtension(identifier)));
        isOpen.set(true);
    }

    public static void openImageFile(Identifier identifier, BufferedImage content) {
        if (openFiles.stream().anyMatch(imageFile -> imageFile.getIdentifier().equals(identifier))) {
            return;
        }
        openFiles.add(new PackFile(identifier, content));
        isOpen.set(true);
    }
}
