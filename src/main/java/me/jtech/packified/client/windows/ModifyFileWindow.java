package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.type.ImString;
import me.jtech.packified.client.util.FileUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class ModifyFileWindow {
    public static boolean open = false;
    private static String text;
    private static Path path;
    private static FileHierarchy.FileModifyAction action;
    private static ImString fileName;

    public static void open(String text, Path path, FileHierarchy.FileModifyAction action) {
        open = true;
        ModifyFileWindow.text = text;
        ModifyFileWindow.path = path;
        ModifyFileWindow.action = action;
        ModifyFileWindow.fileName = new ImString(FileUtils.getRelativePackPath(path));
    }

    public static void render() {
        if (!open) return;
        if (ImGui.begin("ModifyFilePopup")) {
            fileName = new ImString(fileName.get(), fileName.getLength() + 8);
            ImGui.inputText("File Name:", fileName);
            ImGui.sameLine();
            if (ImGui.button(text)) {
                // Create the file
                open = false;
                action.execute(fileName.get());
            }
        }
        ImGui.end();
    }
}
