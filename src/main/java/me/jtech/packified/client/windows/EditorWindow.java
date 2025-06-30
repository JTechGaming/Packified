package me.jtech.packified.client.windows;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import imgui.ImGui;
import imgui.extension.texteditor.TextEditor;
import imgui.extension.texteditor.TextEditorLanguageDefinition;
import imgui.extension.texteditor.flag.TextEditorPaletteIndex;
import imgui.flag.*;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class EditorWindow {
    public static List<PackFile> openFiles = new ArrayList<>();
    public static ImBoolean isOpen = new ImBoolean(true);

    public static int modifiedFiles = 0;

    public static List<PackFile> changedAssets = new ArrayList<>();

    private static AtomicBoolean isPlaying = new AtomicBoolean(false);

    public static PackFile currentFile;
    public static boolean showGrid;

    public static void render() {
        if (!isOpen.get()) {
            return; // If the window is not open, do not render
        }

        // Editor window code
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        if (ImGui.begin("File Editor", isOpen, ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            ImGuiImplementation.pushWindowCenterPos();

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
                                            openFiles.add(new PackFile(path.getFileName().toString(), bytes));
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
                if (currentFile != null && currentFile.getExtension().equalsIgnoreCase(".png")) {
                    if (ImGui.beginMenu("Image")) {
                        if (ImGui.menuItem("Show Grid", null, showGrid)) {
                            showGrid = !showGrid; // Toggle grid visibility
                        }
                        ImGui.endMenu();
                    }
                }
                ImGui.endMenuBar();
            }

            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0x00000000);
            ImGui.beginChild("Toolbar", ImGui.getWindowWidth(), 40, false, ImGuiWindowFlags.HorizontalScrollbar);
            ImGui.imageButton(ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_save.png"), 24, 24);
            if (ImGui.isItemClicked()) {
                // Logic to save the current file
                if (currentFile != null) {
                    FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), currentFile.getTextEditor().getText(), PackifiedClient.currentPack);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save (Ctrl+S)");
            }
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_save-all.png"), 24, 24);
            if (ImGui.isItemClicked()) {
                // Logic to save all files
                FileUtils.saveAllFiles();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save All (Ctrl+Shift+S)");
            }
            ImGui.sameLine();
            ImGui.imageButton(ImGuiImplementation.loadTextureFromOwnIdentifier("textures/ui/neu_reload.png"), 24, 24);
            if (ImGui.isItemClicked()) {
                // Logic to save all files
                CompletableFuture.runAsync(PackUtils::reloadPack);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Reload Pack (Ctrl+R)");
            }

            ImGui.endChild();
            ImGui.popStyleColor();

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

    private static AudioPlayer audioPlayer = new AudioPlayer();

    private static void renderAudioFileEditor(PackFile audioFile) {
        float[] waveform = convertToWaveform(audioFile.getSoundContent());
        if (waveform.length > 0) {
            ImGui.plotLines("Waveform", waveform, waveform.length); //, 0, null, -1.0f, 1.0f, new ImVec2(400, 100)
        } else {
            ImGui.text("No waveform data available");
        }
        int sampleRate = 44100; // Default sample rate for OGG files, can be adjusted based on actual data
        int channels = 1; // Assuming stereo audio, adjust if mono
        if (!audioPlayer.loaded) {
            audioPlayer.load(waveform, sampleRate, channels);
        }
        if (ImGui.button("Play")) {
            if (!audioPlayer.isPlaying()) {
                audioPlayer.play();
            }
        }
        if (!audioPlayer.isPlaying()) {
            // idk clean up or smth
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

    private static void renderTabPopup() {
        if (currentFile == null) {
            return;
        }
        if (ImGui.menuItem("Save", "Ctrl+S")) {
            // Logic to save the current JSON file
            if (currentFile.isModified()) {
                switch (currentFile.getExtension()) {
                    case ".json", ".mcmeta", ".fsh", ".vsh", ".properties", ".txt":
                        FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), currentFile.getTextEditor().getText(), PackifiedClient.currentPack);
                        break;
                    case ".png":
                        FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), FileUtils.encodeImageToBase64(currentFile.getImageEditorContent()), PackifiedClient.currentPack);
                        break;
                    case ".ogg":
                        FileUtils.saveSingleFile(currentFile.getPath(), FileUtils.getFileExtension(currentFile.getFileName()), FileUtils.encodeSoundToString(currentFile.getSoundEditorContent()), PackifiedClient.currentPack);
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

        // Set error markers
        Map<Integer, String> errorMarkers = checkForErrors(file.getTextEditor().getText());
        textEditor.setErrorMarkers(errorMarkers);

        int[] customPalette = textEditor.getDarkPalette();

        customPalette[TextEditorPaletteIndex.Default] = ImGui.colorConvertFloat4ToU32(0.731f, 0.975f, 0.590f, 1.0f);
        customPalette[TextEditorPaletteIndex.LineNumber] = ImGui.colorConvertFloat4ToU32(0.541f, 0.541f, 0.541f, 1.0f);
        customPalette[TextEditorPaletteIndex.Punctuation] = ImGui.colorConvertFloat4ToU32(0.969f, 0.616f, 0.427f, 1.0f);
        customPalette[TextEditorPaletteIndex.Selection] = ImGui.colorConvertFloat4ToU32(0.345f, 0.345f, 0.345f, 1.0f);
        customPalette[TextEditorPaletteIndex.CurrentLineFill] = ImGui.colorConvertFloat4ToU32(0.063f, 0.063f, 0.063f, 1.0f);
        customPalette[TextEditorPaletteIndex.CurrentLineEdge] = ImGui.colorConvertFloat4ToU32(0.498f, 0.624f, 0.498f, 0.5f);
        customPalette[TextEditorPaletteIndex.Keyword] = ImGui.colorConvertFloat4ToU32(0.863f, 0.863f, 0.800f, 1.0f);
        customPalette[TextEditorPaletteIndex.Number] = ImGui.colorConvertFloat4ToU32(0.549f, 0.816f, 0.827f, 1.0f);
        customPalette[TextEditorPaletteIndex.String] = ImGui.colorConvertFloat4ToU32(0.902f, 0.753f, 0.416f, 1.0f);
        customPalette[TextEditorPaletteIndex.Identifier] = ImGui.colorConvertFloat4ToU32(0.6f, 0.85f, 0.7f, 1.0f);

        textEditor.setPalette(customPalette);

        // Render the editor
        textEditor.render("TextEditor");
    }

    private static void renderImageFileEditor(PackFile imageFile) {
        imageFile.getPixelArtEditor().render();
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


    public static TextEditorLanguageDefinition createJsonLanguageDefinition() {
        TextEditorLanguageDefinition lang = new TextEditorLanguageDefinition();
        lang.setName("JSON");

        // Keywords specific to Blockbench format
        String[] keywords = new String[]{
                "credit", "texture_size", "textures", "elements",
                "from", "to", "rotation", "angle", "axis", "origin",
                "faces", "uv", "texture", "north", "east", "south", "west", "up", "down", "particle"
        };

        // Identifiers
        String[] identifiers = {
                // Root-level properties
                "credit", "String field indicating the model origin (e.g., 'Made with Blockbench')",
                "texture_size", "Array of two integers representing texture width and height",
                "textures", "Map of texture references with the names of the textures used, and the particle texture",
                "elements", "Array of cuboid elements that make up the model",

                // Element object fields
                "name", "Name of the element, used for identification",
                "from", "3D vector indicating the starting (min) corner of the cuboid",
                "to", "3D vector indicating the ending (max) corner of the cuboid",
                "rotation", "Rotation of the element",

                // Rotation object fields
                "angle", "Angle of rotation in degrees",
                "axis", "Axis of rotation: 'x', 'y', or 'z'",
                "origin", "Origin point for the rotation (3D vector)",

                // Faces and textures
                "faces", "Object mapping of sides (north, south, etc.) to face definitions",
                "uv", "Array of four numbers specifying UV coordinates for a face",
                "texture", "Reference to a texture, unless explicitly named it is usually prefixed with '#' (e.g., '#0')",

                // Face directions
                "north", "The north-facing side of the model",
                "south", "The south-facing side of the model",
                "east", "The east-facing side of the model",
                "west", "The west-facing side of the model",
                "up", "The top side of the model",
                "down", "The bottom side of the model",

                // Special texture reference
                "particle", "The texture to use for the particle this model displays"
        };
        Map<String, String> identifiersMap = new HashMap<>(identifiers.length / 2);
        for (int i = 0; i < identifiers.length; i += 2) {
            identifiersMap.put(identifiers[i], identifiers[i + 1]);
        }
        lang.setIdentifiers(identifiersMap);

        lang.setCommentStart("♸"); // No single-line comments
        lang.setCommentEnd("♼");

        //lang.setStringDelimiter("\"");
        //lang.setEscapeCharacter('\\');

        Map<String, Integer> tokenRegexStrings = new HashMap<>();
        tokenRegexStrings.put("[{}\\[\\],:]", TextEditorPaletteIndex.Punctuation);
        tokenRegexStrings.put("[0-9.-]", TextEditorPaletteIndex.Number);
        for (String identifier : identifiersMap.keySet()) {
            // Add identifiers to the regex map
            tokenRegexStrings.put("\\b" + identifier + "\\b", TextEditorPaletteIndex.Identifier);
        }
        //tokenRegexStrings.put("[0-9.-]", TextEditorPaletteIndex.);
        lang.setTokenRegexStrings(tokenRegexStrings);

        lang.setAutoIdentation(true);

        return lang;
    }

    public static TextEditorLanguageDefinition createTxtLanguageDefinition() {
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

    protected static Map<Integer, String> checkForErrors(String content) {
        Map<Integer, String> errorMarkers = new HashMap<>();
        JsonFactory factory = new JsonFactory();
        try (JsonParser parser = factory.createParser(content)) {
            while (parser.nextToken() != null) {
                // If it reaches here, the current line in the JSON is valid
            }
        } catch (JsonParseException e) {
            JsonLocation location = e.getLocation();
            int lineNumber = location.getLineNr() - 1;
            String errorMessage = e.getOriginalMessage();
            errorMarkers.put(lineNumber, errorMessage);
        } catch (IOException e) {
            errorMarkers.put(0, "Unknown error reading content");
        }
        return errorMarkers;
    }
}