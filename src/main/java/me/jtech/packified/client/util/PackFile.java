package me.jtech.packified.client.util;

import imgui.extension.texteditor.TextEditor;
import imgui.extension.texteditor.TextEditorLanguageDefinition;
import me.jtech.packified.client.windows.PixelArtEditor;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static me.jtech.packified.client.windows.EditorWindow.*;

public class PackFile {
    private String fileName;
    private BufferedImage imageContent;
    private String textContent;
    private byte[] soundContent;
    private String extension;
    private BufferedImage imageEditorContent;
    private byte[] soundEditorContent;
    private Path path;
    private TextEditor textEditor;
    private PixelArtEditor pixelArtEditor;

    public PackFile(Path path, BufferedImage content) {
        this.path = path;
        this.fileName = path.getFileName().toString();
        this.imageContent = content;
        this.imageEditorContent = content;
        this.extension = ".png";
        this.pixelArtEditor = new PixelArtEditor();
        this.pixelArtEditor.loadImage(content, path);
    }

    public PackFile(String fileName, BufferedImage content) {
        this.fileName = fileName;
        this.imageContent = content;
        this.imageEditorContent = content;
        this.extension = ".png";
        this.pixelArtEditor = new PixelArtEditor();
        this.pixelArtEditor.loadImage(content, path);
    }

    public PackFile(Path path, String content, String extension, TextEditor textEditor) {
        this.path = path;
        this.fileName = path.getFileName().toString();
        this.textContent = content;
        this.extension = extension;
        this.textEditor = textEditor;
        switch (getExtension()) {
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
    }

    public PackFile(String fileName, String content, String extension, TextEditor textEditor) {
        this.fileName = fileName;
        this.textContent = content;
        this.extension = extension;
        this.textEditor = textEditor;
        switch (getExtension()) {
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
    }

    public PackFile(Path path, byte[] content) {
        this.path = path;
        this.fileName = this.path.getFileName().toString();
        this.soundContent = content;
        this.soundEditorContent = content;
        this.extension = ".ogg";
    }

    public PackFile(String fileName, byte[] content) {
        this.fileName = fileName;
        this.soundContent = content;
        this.soundEditorContent = content;
        this.extension = ".ogg";
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
        this.fileName = path.getFileName().toString();
    }

    public String getFileName() {
        return fileName;
    }

    public String getExtension() {
        return extension;
    }

    public BufferedImage getImageEditorContent() {
        return imageEditorContent;
    }

    public boolean isModified() {
        return switch (extension) {
            case ".png" -> pixelArtEditor.wasModified;
            case ".json" -> !textContent.equals(textEditor.getText());
            case ".ogg" -> !soundContent.equals(soundEditorContent);
            default -> false;
        };
    }

    public byte[] getSoundContent() {
        return soundContent;
    }

    public byte[] getSoundEditorContent() {
        return soundEditorContent;
    }

    public void saveFile() {
        switch (extension) {
            case ".png" -> pixelArtEditor.wasModified = false;
            case ".json" -> textContent = textEditor.getText();
            case ".ogg" -> soundContent = soundEditorContent;
        }
    }

    public TextEditor getTextEditor() {
        return textEditor;
    }

    public void setImageEditorContent(String content) {
        this.imageEditorContent = FileUtils.decodeBase64ToImage(content);
    }

    public PixelArtEditor getPixelArtEditor() {
        return pixelArtEditor;
    }
}
