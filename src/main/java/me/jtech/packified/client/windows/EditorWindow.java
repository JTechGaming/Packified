package me.jtech.packified.client.windows;

import com.mojang.authlib.minecraft.client.ObjectMapper;
import imgui.ImGui;
import imgui.extension.texteditor.TextEditor;
import imgui.extension.texteditor.TextEditorLanguageDefinition;
import imgui.extension.texteditor.flag.TextEditorPaletteIndex;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Environment(EnvType.CLIENT)
public class EditorWindow {
    public static List<PackFile> openFiles = new ArrayList<>();
    public static ImBoolean isOpen = new ImBoolean(false);
    private static ImInt toolSize = new ImInt(1);

    private static float[] color = new float[]{1.0f, 1.0f, 1.0f};

    public static int modifiedFiles = 0;

    public static List<PackFile> changedAssets = new ArrayList<>();

    private enum Tool {
        PEN,
        PAINT_BUCKET,
        SELECT,
        ERASER
    }

    private static Tool currentTool = Tool.PEN;

    public static PackFile currentFile;

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
                                            String content = Files.readString(path);
                                            TextEditor editor = new TextEditor();
                                            editor.setText(content);
                                            openFiles.add(new PackFile(path.getFileName().toString(), content, FileHierarchy.FileType.JSON, editor));
                                        } else if (extension.equals(".ogg")) {
                                            byte[] bytes = Files.readAllBytes(path);
                                            String base64 = Base64.getEncoder().encodeToString(bytes);
                                            String content = "data:audio/ogg;base64," + base64;
                                            //openFiles.add(new PackFile(path.getFileName().toString(), content, FileHierarchy.FileType.OGG)); //TODO add audio support
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

            ImGui.beginChild("Toolbar", ImGui.getWindowWidth(), 40, false, ImGuiWindowFlags.HorizontalScrollbar);
            ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_save.png"), 24, 24);
            if (ImGui.isItemClicked()) {
                // Logic to save the current file
                if (currentFile != null) {
                    FileUtils.saveSingleFile(currentFile.getIdentifier(), FileUtils.getFileExtension(currentFile.getFileName()), currentFile.getTextEditorContent().get());
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save (Ctrl+S)");
            }
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_save-all.png"), 24, 24);
            if (ImGui.isItemClicked()) {
                // Logic to save all files
                FileUtils.saveAllFiles();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save All (Ctrl+Shift+S)");
            }
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_reload.png"), 24, 24);
            if (ImGui.isItemClicked()) {
                // Logic to save all files
                PackUtils.reloadPack();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Reload Pack (Ctrl+R)");
            }

            ImGui.endChild();

            if (ImGui.beginTabBar("FileEditorTabs", ImGuiTabBarFlags.Reorderable | ImGuiTabBarFlags.AutoSelectNewTabs | ImGuiTabBarFlags.TabListPopupButton)) {
                // Example of adding tabs dynamically
                for (int i = 0; i < openFiles.size(); i++) {
                    if (ImGui.beginTabItem(openFiles.get(i).getFileName() + "##" + i, ImGuiTabItemFlags.None | (openFiles.get(i).isModified() ? ImGuiTabItemFlags.UnsavedDocument : 0))) {
                        if (openFiles.get(i).isModified()) {
                            for (int j = 0; j < changedAssets.size(); j++) {
                                if (changedAssets.get(j).getIdentifier().equals(openFiles.get(i).getIdentifier())) {
                                    changedAssets.remove(j);
                                    break;
                                }
                            }
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
//                            case OGG:
//                                renderAudioFileEditor(openFiles.get(i));
//                                break;
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
        ImGui.text("The OGG file type can not be edited yet.");
    }

    private static void renderTabPopup() {
        if (currentFile == null) {
            return;
        }
        if (ImGui.menuItem("Save", "Ctrl+S")) {
            // Logic to save the current JSON file
            if (currentFile.isModified()) {
                switch (currentFile.getExtension()) {
                    case JSON, MC_META, FSH, VSH, PROPERTIES, TEXT:
                        FileUtils.saveSingleFile(currentFile.getIdentifier(), FileUtils.getFileExtension(currentFile.getFileName()), currentFile.getTextEditorContent().get());
                        break;
                    case PNG:
                        FileUtils.saveSingleFile(currentFile.getIdentifier(), FileUtils.getFileExtension(currentFile.getFileName()), FileUtils.encodeImageToBase64(currentFile.getImageEditorContent()));
                        break;
                    case OGG:
                        FileUtils.saveSingleFile(currentFile.getIdentifier(), FileUtils.getFileExtension(currentFile.getFileName()), FileUtils.encodeSoundToString(currentFile.getSoundEditorContent()));
                        break;
                }
            }
        }
        if (ImGui.menuItem("Undo", "Ctrl+Z") && currentFile.getTextEditor().canUndo()) {
            currentFile.getTextEditor().undo(1);
        }
        if (ImGui.menuItem("Redo", "Ctrl+Y") && currentFile.getTextEditor().canRedo()) {
            currentFile.getTextEditor().redo(1);
        }
        if (ImGui.menuItem("Save All", "Ctrl+Shift+S")) {
            // Logic to save all JSON files
            FileUtils.saveAllFiles();
        }
        if (ImGui.menuItem("Close", "Ctrl+W")) {
            // Logic to close the current tab
            if (currentFile.isModified()) {
                ConfirmWindow.open("close this file", "Any unsaved changes might be lost.", () -> {
                    modifiedFiles++;
                    openFiles.remove(currentFile);
                });
                return;
            }
            modifiedFiles++;
            openFiles.remove(currentFile);
        }
        if (openFiles.size() > 1) {
            if (ImGui.menuItem("Close All")) {
                // Logic to close all tabs
                for (PackFile file : openFiles) {
                    if (file.isModified()) {
                        ConfirmWindow.open("close all files", "Any unsaved changes might be lost.", () -> {
                            modifiedFiles += openFiles.size();
                            openFiles.clear();
                        });
                        return;
                    }
                }
                modifiedFiles += openFiles.size();
                openFiles.clear();
            }
        }
        if (ImGui.menuItem("Delete")) {
            // Logic to delete the current file
            ConfirmWindow.open("delete this file", "The file will be lost forever.", () -> {
                openFiles.remove(currentFile);
                FileUtils.deleteFile(currentFile.getIdentifier());
            });
        }
    }

    private static void renderTextFileEditor(PackFile file) {
        // Logic to render the JSON editor for the given file
        TextEditor textEditor = file.getTextEditor();

        // Set syntax highlighting
        switch (file.getExtension()) {
            case JSON, MC_META:
                textEditor.setLanguageDefinition(createJsonLanguageDefinition());
                break;
            case FSH, VSH:
                textEditor.setLanguageDefinition(TextEditorLanguageDefinition.glsl());
                break;
            case PROPERTIES, TEXT:
                textEditor.setLanguageDefinition(createTxtLanguageDefinition());
                break;
        }

        // Set error markers
        Map<Integer, String> errorMarkers = checkForErrors(file.getTextEditor().getText());
        file.getTextEditorContent().set(file.getTextEditor().getText());
        textEditor.setErrorMarkers(errorMarkers);

        textEditor.setPalette(textEditor.getDarkPalette());

        // Render the editor
        textEditor.render("TextEditor");
        //ImGui.inputTextMultiline("##source", file.getTextEditorContent(), ImGui.getWindowWidth() - 20, ImGui.getWindowHeight() - 40);
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
        TextEditor editor = new TextEditor();
        editor.setText(content);
        PackFile file = new PackFile(identifier, content, FileUtils.getExtension(identifier), editor);
        openFiles.add(file);
        isOpen.set(true);
    }

    public static void openImageFile(Identifier identifier, BufferedImage content) {
        if (openFiles.stream().anyMatch(imageFile -> imageFile.getIdentifier().equals(identifier))) {
            return;
        }
        openFiles.add(new PackFile(identifier, content));
        isOpen.set(true);
    }

    private static TextEditorLanguageDefinition createJsonLanguageDefinition() {
        TextEditorLanguageDefinition lang = new TextEditorLanguageDefinition();
        lang.setName("JSON");

        // Define keywords
        String[] keywords = new String[]{"elements", "name", "from", "to", "rotation", "faces"};
        lang.setKeywords(keywords);

        // Define identifiers
        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("north", "north");
        identifiers.put("east", "east");
        identifiers.put("south", "south");
        identifiers.put("west", "west");
        identifiers.put("up", "up");
        identifiers.put("down", "down");
        identifiers.put("uv", "uv");
        identifiers.put("texture", "texture");
        lang.setIdentifiers(identifiers);

        // Define token regex strings for symbols
        Map<String, Integer> tokenRegexStrings = new HashMap<>();
        //tokenRegexStrings.put("\"[^\"]*\"", TextEditorPaletteIndex.String);
        tokenRegexStrings.put("[{}\\[\\]:,]", TextEditorPaletteIndex.Punctuation);
        lang.setTokenRegexStrings(tokenRegexStrings);

        lang.setAutoIdentation(true);
        return lang;
    }

    private static TextEditorLanguageDefinition createTxtLanguageDefinition() {
        TextEditorLanguageDefinition lang = new TextEditorLanguageDefinition();
        lang.setName("Text");
        String[] keywords = new String[]{};
        lang.setKeywords(keywords);
        Map<String, String> identifiers = new HashMap<>();
        lang.setIdentifiers(identifiers);
        Map<String, String> preprocIdentifiers = new HashMap<>();
        lang.setPreprocIdentifiers(preprocIdentifiers);
        Map<String, Integer> tokenRegexStrings = new HashMap<>();
        lang.setTokenRegexStrings(tokenRegexStrings);
        lang.setAutoIdentation(false);
        return lang;
    }

    private static Map<Integer, String> checkForErrors(String content) {
        Map<Integer, String> errorMarkers = new HashMap<>();
        try {
            new com.google.gson.JsonParser().parse(content);
        } catch (com.google.gson.JsonSyntaxException e) {
            System.out.println(e.getMessage());
            // get the line number of the error
            // the line number is the first number in the error message
            String number = e.getMessage().replaceAll("[^0-9 ]","");
            int lineNumber = Integer.parseInt(number.split(" ")[5]);
            String errorMessage = e.getMessage().split(" ")[0];
            errorMarkers.put(lineNumber, errorMessage);
        }
        return errorMarkers;
    }
}
