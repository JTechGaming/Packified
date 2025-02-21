package me.jtech.packified.client.util;

import imgui.extension.texteditor.TextEditor;
import imgui.type.ImString;
import me.jtech.packified.Packified;
import me.jtech.packified.client.windows.FileHierarchy;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;

public class PackFile {
    private String fileName;
    private BufferedImage imageContent;
    private String textContent;
    private OggAudioStream soundContent;
    private FileHierarchy.FileType extension;
    private BufferedImage imageEditorContent;
    private ImString textEditorContent;
    private OggAudioStream soundEditorContent;
    private Identifier identifier;
    private TextEditor textEditor;

    public PackFile(Identifier identifier, BufferedImage content) {
        this.identifier = identifier;
        this.fileName = identifier.getPath();
        this.imageContent = content;
        this.imageEditorContent = content;
        this.extension = FileHierarchy.FileType.PNG;
    }

    public PackFile(String fileName, BufferedImage content) {
        this.fileName = fileName;
        this.imageContent = content;
        this.imageEditorContent = content;
        this.extension = FileHierarchy.FileType.PNG;
    }

    public PackFile(Identifier identifier, String content, FileHierarchy.FileType extension, TextEditor textEditor) {
        this.identifier = identifier;
        this.fileName = identifier.getPath();
        this.textContent = content;
        this.textEditorContent = new ImString(content, Packified.MAX_FILE_SIZE);
        this.extension = extension;
        this.textEditor = textEditor;
    }

    public PackFile(String fileName, String content, FileHierarchy.FileType extension, TextEditor textEditor) {
        this.fileName = fileName;
        this.textContent = content;
        this.textEditorContent = new ImString(content, Packified.MAX_FILE_SIZE);
        this.extension = extension;
        this.textEditor = textEditor;
    }

    public PackFile(Identifier identifier, OggAudioStream content) {
        this.identifier = identifier;
        this.fileName = identifier.getPath();
        this.soundContent = content;
        this.soundEditorContent = content;
        this.extension = FileHierarchy.FileType.OGG;
    }

    public PackFile(String fileName, OggAudioStream content) {
        this.fileName = fileName;
        this.soundContent = content;
        this.soundEditorContent = content;
        this.extension = FileHierarchy.FileType.OGG;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Identifier identifier) {
        this.identifier = identifier;
        this.fileName = identifier.getPath();
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

    public FileHierarchy.FileType getExtension() {
        return extension;
    }

    public BufferedImage getImageEditorContent() {
        return imageEditorContent;
    }

    public void setEditorContent(BufferedImage editorContent) {
        this.imageEditorContent = editorContent;
    }

    public boolean isModified() {
        if (extension == FileHierarchy.FileType.PNG) {
            return !imageContent.equals(imageEditorContent);
        } else if (extension == FileHierarchy.FileType.JSON) {
            return !textContent.equals(textEditorContent.get());
        } else if (extension == FileHierarchy.FileType.OGG) {
            return !soundContent.equals(soundEditorContent);
        }
        return false;
    }

    public ImString getTextEditorContent() {
        //textEditorContent = new ImString(textEditorContent.get(), textEditorContent.getBufferSize() + 8);
        return textEditorContent;
    }

    public void setEditorContent(String editorContent) {
        this.textEditorContent.set(editorContent);
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public OggAudioStream getSoundContent() {
        return soundContent;
    }

    public void setSoundContent(OggAudioStream soundContent) {
        this.soundContent = soundContent;
    }

    public OggAudioStream getSoundEditorContent() {
        return soundEditorContent;
    }

    public void setSoundEditorContent(OggAudioStream soundEditorContent) {
        this.soundEditorContent = soundEditorContent;
    }

    public BufferedImage getImageContent() {
        return imageContent;
    }

    public void saveFile() {
        if (extension == FileHierarchy.FileType.PNG) {
            imageContent = imageEditorContent;
        } else if (extension == FileHierarchy.FileType.JSON) {
            textContent = textEditorContent.get();
        } else if (extension == FileHierarchy.FileType.OGG) {
            soundContent = soundEditorContent;
        }
    }

    public TextEditor getTextEditor() {
        return textEditor;
    }
}
