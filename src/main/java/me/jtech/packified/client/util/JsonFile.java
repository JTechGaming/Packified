package me.jtech.packified.client.util;

import imgui.type.ImString;
import me.jtech.packified.client.windows.FileHierarchy;
import net.minecraft.util.Identifier;

public class JsonFile {
    private String fileName;
    private String content;
    private final FileHierarchy.FileType extension = FileHierarchy.FileType.JSON;
    private ImString editorContent;
    private Identifier identifier;

    public JsonFile(Identifier identifier, String content) {
        this.identifier = identifier;
        this.fileName = identifier.getPath();
        this.content = content;
        this.editorContent = new ImString(content);
    }

    public JsonFile(String fileName, String content) {
        this.fileName = fileName;
        this.content = content;
        this.editorContent = new ImString(content);
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public FileHierarchy.FileType getExtension() {
        return extension;
    }

    public ImString getEditorContent() {
        editorContent = new ImString(editorContent.get(), editorContent.getBufferSize() + 8);
        return editorContent;
    }

    public void setEditorContent(String editorContent) {
        this.editorContent.set(editorContent);
    }

    public boolean isModified() {
        return !editorContent.get().equals(content);
    }
}
