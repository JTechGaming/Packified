package me.jtech.packified.client.windows;

import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import imgui.ImGui;
import imgui.ImVec2;
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
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class EditorWindow {
    public static List<PackFile> openFiles = new ArrayList<>();
    public static ImBoolean isOpen = new ImBoolean(false);
    private static ImInt toolSize = new ImInt(1);

    private static float[] color = new float[]{1.0f, 1.0f, 1.0f};

    public static int modifiedFiles = 0;

    public static List<PackFile> changedAssets = new ArrayList<>();

    private static AtomicBoolean isPlaying = new AtomicBoolean(false);
    private static int audioSource = -1;
    private static int bufferId = -1;

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
                        FileDialog.openFileDialog(defaultFolder, "Files", "json", "png", "ogg").thenAccept(pathStr -> {
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
                                            openFiles.add(new PackFile(path.getFileName().toString(), content, ".json", editor));
                                        } else if (extension.equals(".ogg")) {
                                            byte[] bytes = Files.readAllBytes(path);

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
                    FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), currentFile.getTextEditor().getText());
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
                                if (changedAssets.get(j).getPath().equals(openFiles.get(i).getPath())) {
                                    changedAssets.remove(j);
                                    break;
                                }
                            }
                            changedAssets.add(openFiles.get(i));
                        }
                        currentFile = openFiles.get(i);
                        // Render the JSON editor for the current file
                        switch (openFiles.get(i).getExtension()) {
                            case ".json", ".mcmeta", ".fsh", ".vsh", ".properties", ".txt":
                                renderTextFileEditor(openFiles.get(i));
                                break;
                            case ".png":
                                renderImageFileEditor(openFiles.get(i));
                                break;
                            case ".ogg":
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
        // TODO implement audio playback
//        if (ImGui.button(isPlaying.get() ? "Pause" : "Play")) {
//            if (isPlaying.get()) {
//                if (audioClip != null) audioClip.stop();
//            } else {
//                playOggWithMinecraft(audioFile.getPath());
//            }
//            isPlaying.set(!isPlaying.get());
//        }

        // Get total duration
        float totalDuration = (audioClip != null) ? audioClip.getMicrosecondLength() / 1_000_000f : 0f;

        // Update the playhead if audio is playing
        if (isPlaying.get() && audioClip != null) {
            playbackPosition[0] = audioClip.getMicrosecondPosition() / 1_000_000f;
        }

        // Display waveform with playhead slider
        float[] waveform = convertToWaveform(audioFile.getSoundContent());
        if (waveform.length > 0) {
            ImGui.plotLines("Waveform", waveform, waveform.length); //, 0, null, -1.0f, 1.0f, new ImVec2(400, 100)

            // Playhead slider (draggable)
            if (ImGui.sliderFloat("Playback", playbackPosition, 0f, totalDuration)) {
                if (audioClip != null) {
                    audioClip.setMicrosecondPosition((long) (playbackPosition[0] * 1_000_000)); // Seek audio
                }
            }
        } else {
            ImGui.text("No waveform data available");
        }
    }

    private static float[] convertToWaveform(byte[] audioData) {
        int sampleSize = 2; // 16-bit PCM audio
        int numSamples = audioData.length / sampleSize;
        float[] waveform = new float[numSamples];

        ByteBuffer buffer = ByteBuffer.wrap(audioData);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // OGG uses little-endian format

        for (int i = 0; i < numSamples; i++) {
            short sample = buffer.getShort(); // Read 16-bit sample
            waveform[i] = sample / 32768f; // Normalize to [-1,1]
        }

        return waveform;
    }

    private static Clip audioClip;
    private static float[] playbackPosition = new float[]{0f}; // Current play position in seconds

    private static void renderTabPopup() {
        if (currentFile == null) {
            return;
        }
        if (ImGui.menuItem("Save", "Ctrl+S")) {
            // Logic to save the current JSON file
            if (currentFile.isModified()) {
                switch (currentFile.getExtension()) {
                    case ".json", ".mcmeta", ".fsh", ".vsh", ".properties", ".txt":
                        FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), currentFile.getTextEditor().getText());
                        break;
                    case ".png":
                        FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), FileUtils.encodeImageToBase64(currentFile.getImageEditorContent()));
                        break;
                    case ".ogg":
                        FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), FileUtils.encodeSoundToString(currentFile.getSoundEditorContent()));
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
                FileUtils.deleteFile(currentFile.getPath());
            });
        }
    }

    private static void renderTextFileEditor(PackFile file) {
        // Logic to render the JSON editor for the given file
        TextEditor textEditor = file.getTextEditor();
        // Set syntax highlighting
        switch (file.getExtension()) {
            case ".json", ".mcmeta":
                textEditor.setLanguageDefinition(createJsonLanguageDefinition());
                break;
            case ".fsh", ".vsh":
                textEditor.setLanguageDefinition(TextEditorLanguageDefinition.glsl());
                break;
            case ".properties", ".txt":
                textEditor.setLanguageDefinition(createTxtLanguageDefinition());
                break;
        }

        // Set error markers
        Map<Integer, String> errorMarkers = checkForErrors(file.getTextEditor().getText());
        textEditor.setErrorMarkers(errorMarkers);

        int[] customPalette = textEditor.getDarkPalette();

        // Custom palette colors
        customPalette[TextEditorPaletteIndex.Default] = ImGui.colorConvertFloat4ToU32(0.731f, 0.975f, 0.590f, 1.0f);
        customPalette[TextEditorPaletteIndex.LineNumber] = ImGui.colorConvertFloat4ToU32(0.541f, 0.541f, 0.541f, 1.0f);
        customPalette[TextEditorPaletteIndex.Punctuation] = ImGui.colorConvertFloat4ToU32(0.447f, 0.557f, 0.400f, 1.0f);
        customPalette[TextEditorPaletteIndex.Selection] = ImGui.colorConvertFloat4ToU32(0.345f, 0.345f, 0.345f, 1.0f);
        customPalette[TextEditorPaletteIndex.CurrentLineFill] = ImGui.colorConvertFloat4ToU32(0.063f, 0.063f, 0.063f, 1.0f);
        //customPalette[TextEditorPaletteIndex.Keyword] = ImGui.colorConvertFloat4ToU32(0.863f, 0.863f, 0.800f, 1.0f);
        //customPalette[TextEditorPaletteIndex.Default] = ImGui.colorConvertFloat4ToU32(0.447f, 0.557f, 0.400f, 1.0f);
        customPalette[TextEditorPaletteIndex.CurrentLineEdge] = ImGui.colorConvertFloat4ToU32(0.498f, 0.624f, 0.498f, 0.5f);
        customPalette[TextEditorPaletteIndex.Number] = ImGui.colorConvertFloat4ToU32(0.549f, 0.816f, 0.827f, 1.0f);
        //customPalette[TextEditorPaletteIndex.MultiLineComment] = ImGui.colorConvertFloat4ToU32(0.549f, 0.816f, 0.827f, 1.0f);

        //customPalette[TextEditorPaletteIndex.String] = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f);

        textEditor.setPalette(customPalette);

        // Render the editor
        textEditor.render("TextEditor");
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
            handleDrawingTools(bufferedImage, imageFile);
            imageFile.setContent(bufferedImage);
        } else {
            ImGui.text("Failed to load image");
        }
    }

    private static void handleDrawingTools(BufferedImage image, PackFile imageFile) {
        if (ImGui.isMouseDown(0)) { // If left-click is held
            // Get mouse position
            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();

            // Convert screen mouse coordinates to image coordinates
            int imgX = (int) ((mouseX - ImGui.getCursorScreenPosX()) / zoomFactor[0]);
            int imgY = (int) ((mouseY - ImGui.getCursorScreenPosY()) / zoomFactor[0]);

            if (imgX >= 0 && imgX < image.getWidth() && imgY >= 0 && imgY < image.getHeight()) {
                switch (currentTool) {
                    case PEN -> drawPen(image, imgX, imgY);
                    case PAINT_BUCKET -> fill(image, imgX, imgY);
                    case ERASER -> erase(image, imgX, imgY);
                    case SELECT -> { /* Future Selection Logic */ }
                }
                // Update the texture to reflect changes
                FileUtils.updateTexture(image, imageFile);
            }
        }
    }

    private static void drawPen(BufferedImage image, int x, int y) {
        int brushSize = toolSize.get();
        int rgb = colorToRGB(color);

        for (int i = -brushSize / 2; i < brushSize / 2; i++) {
            for (int j = -brushSize / 2; j < brushSize / 2; j++) {
                int px = x + i;
                int py = y + j;

                if (px >= 0 && px < image.getWidth() && py >= 0 && py < image.getHeight()) {
                    image.setRGB(px, py, rgb);
                }
            }
        }
    }

    private static void erase(BufferedImage image, int x, int y) {
        int brushSize = toolSize.get();
        int transparent = 0x00000000; // ARGB Transparent

        for (int i = -brushSize / 2; i < brushSize / 2; i++) {
            for (int j = -brushSize / 2; j < brushSize / 2; j++) {
                int px = x + i;
                int py = y + j;

                if (px >= 0 && px < image.getWidth() && py >= 0 && py < image.getHeight()) {
                    image.setRGB(px, py, transparent);
                }
            }
        }
    }

    private static void fill(BufferedImage image, int x, int y) {
        int targetColor = image.getRGB(x, y);
        int replacementColor = colorToRGB(color);

        if (targetColor == replacementColor) return; // Prevent infinite loop

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{x, y});

        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            int px = point[0];
            int py = point[1];

            if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) continue;
            if (image.getRGB(px, py) != targetColor) continue;

            image.setRGB(px, py, replacementColor);

            queue.add(new int[]{px + 1, py});
            queue.add(new int[]{px - 1, py});
            queue.add(new int[]{px, py + 1});
            queue.add(new int[]{px, py - 1});
        }
    }

    private static int colorToRGB(float[] color) {
        int r = (int) (color[0] * 255);
        int g = (int) (color[1] * 255);
        int b = (int) (color[2] * 255);
        return (r << 16) | (g << 8) | b;
    }

    public static void openTextFile(Path filePath, String content) {
        if (openFiles.stream().anyMatch(file -> file.getPath().equals(filePath))) {
            return; // Prevent opening the same file multiple times
        }

        TextEditor editor = new TextEditor();
        editor.setText(content);
        PackFile file = new PackFile(filePath, content, FileUtils.getFileExtension(filePath.getFileName().toString()), editor);
        openFiles.add(file);
        isOpen.set(true);
    }

    public static void openImageFile(Path filePath, BufferedImage content) {
        if (openFiles.stream().anyMatch(imageFile -> imageFile.getPath().equals(filePath))) {
            return; // Prevent opening the same image multiple times
        }

        openFiles.add(new PackFile(filePath, content));
        isOpen.set(true);
    }

    public static void openAudioFile(Path filePath, byte[] audioData) {
        if (openFiles.stream().anyMatch(file -> file.getPath().equals(filePath))) {
            System.out.println("file already open");
            return; // Prevent opening the same file multiple times
        }

        PackFile file = new PackFile(filePath, audioData);
        openFiles.add(file);
        isOpen.set(true);
    }


    private static TextEditorLanguageDefinition createJsonLanguageDefinition() {
        TextEditorLanguageDefinition lang = new TextEditorLanguageDefinition();
        lang.setName("JSON");

        // Keywords (optional — just for highlighting keys like "elements")
        lang.setKeywords(new String[]{"credit", "texture_size", "textures"});

        // Identifiers (for better tooltip descriptions or intellisense, optional)
        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("name", "North face");
        identifiers.put("from", "South face");
        identifiers.put("to", "UV mapping");
        identifiers.put("rotation", "Texture reference");
        identifiers.put("faces", "Texture reference");
        lang.setIdentifiers(identifiers);

        lang.setCommentStart("♸"); // No single-line comments
        lang.setCommentEnd("♼");

        //lang.setStringDelimiter("\"");
        //lang.setEscapeCharacter('\\');

        Map<String, Integer> tokenRegexStrings = new HashMap<>();
        tokenRegexStrings.put("[{}\\[\\],:]", TextEditorPaletteIndex.Punctuation);
        tokenRegexStrings.put("[0-9.-]", TextEditorPaletteIndex.Number);
        tokenRegexStrings.put("credit", TextEditorPaletteIndex.Keyword);
        tokenRegexStrings.put("texture_size", TextEditorPaletteIndex.Keyword);
        tokenRegexStrings.put("textures", TextEditorPaletteIndex.Keyword);
        tokenRegexStrings.put("name", TextEditorPaletteIndex.Identifier);
        tokenRegexStrings.put("from", TextEditorPaletteIndex.Identifier);
        tokenRegexStrings.put("to", TextEditorPaletteIndex.Identifier);
        tokenRegexStrings.put("rotation", TextEditorPaletteIndex.Identifier);
        tokenRegexStrings.put("faces", TextEditorPaletteIndex.Identifier);
        //tokenRegexStrings.put("[0-9.-]", TextEditorPaletteIndex.);
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
            // get the line number of the error
            // the line number is the first number in the error message
            try {
                String number = e.getMessage().replaceAll("[^0-9 ]", "");
                int lineNumber = Integer.parseInt(number.split(" ")[5]);
                String errorMessage = e.getMessage().split(" ")[0];
                errorMarkers.put(lineNumber, errorMessage);
            } catch (NumberFormatException ex) {
                System.err.println("Failed to parse error message: " + e.getMessage());
            }
        }
        return errorMarkers;
    }
}