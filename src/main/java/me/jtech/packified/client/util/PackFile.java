package me.jtech.packified.client.util;

import imgui.extension.texteditor.TextEditor;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

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

    public PackFile(Path path, BufferedImage content) {
        this.path = path;
        this.fileName = path.getFileName().toString();
        this.imageContent = content;
        this.imageEditorContent = content;
        this.extension = ".png";
    }

    public PackFile(String fileName, BufferedImage content) {
        this.fileName = fileName;
        this.imageContent = content;
        this.imageEditorContent = content;
        this.extension = ".png";
    }

    public PackFile(Path path, String content, String extension, TextEditor textEditor) {
        this.path = path;
        this.fileName = path.getFileName().toString();
        this.textContent = content;
        this.extension = extension;
        this.textEditor = textEditor;
    }

    public PackFile(String fileName, String content, String extension, TextEditor textEditor) {
        this.fileName = fileName;
        this.textContent = content;
        this.extension = extension;
        this.textEditor = textEditor;
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

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public BufferedImage getContent() {
        return imageContent;
    }

    public void setContent(BufferedImage content) {
        this.imageContent = content;
    }

    public String getExtension() {
        return extension;
    }

    public BufferedImage getImageEditorContent() {
        return imageEditorContent;
    }

    public void setEditorContent(BufferedImage editorContent) {
        this.imageEditorContent = editorContent;
    }

    public boolean isModified() {
        return switch (extension) {
            case ".png" -> !imageContent.equals(imageEditorContent);
            case ".json" -> !textContent.equals(textEditor.getText());
            case ".ogg" -> !soundContent.equals(soundEditorContent);
            default -> false;
        };
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public byte[] getSoundContent() {
        return soundContent;
    }

    public void setSoundContent(byte[] soundContent) {
        this.soundContent = soundContent;
    }

    public byte[] getSoundEditorContent() {
        return soundEditorContent;
    }

    public void setSoundEditorContent(byte[] soundEditorContent) {
        this.soundEditorContent = soundEditorContent;
    }

    public BufferedImage getImageContent() {
        return imageContent;
    }

    public void saveFile() {
        switch (extension) {
            case ".png" -> imageContent = imageEditorContent;
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
}
