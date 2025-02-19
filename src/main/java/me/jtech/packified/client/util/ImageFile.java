package me.jtech.packified.client.util;

import me.jtech.packified.client.windows.FileHierarchy;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class ImageFile {
    private String fileName;
    private BufferedImage content;
    private final FileHierarchy.FileType extension = FileHierarchy.FileType.PNG;
    private BufferedImage editorContent;
    private Identifier identifier;

    public ImageFile(Identifier identifier, BufferedImage content) {
        this.identifier = identifier;
        this.fileName = identifier.getPath();
        this.content = content;
        this.editorContent = content;
    }

    public ImageFile(String fileName, BufferedImage content) {
        this.fileName = fileName;
        this.content = content;
        this.editorContent = content;
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
        return content;
    }

    public void setContent(BufferedImage content) {
        this.content = content;
    }

    public FileHierarchy.FileType getExtension() {
        return extension;
    }

    public BufferedImage getEditorContent() {
        return editorContent;
    }

    public void setEditorContent(BufferedImage editorContent) {
        this.editorContent = editorContent;
    }

    public boolean isModified() {
        return content != editorContent;
    }
}
