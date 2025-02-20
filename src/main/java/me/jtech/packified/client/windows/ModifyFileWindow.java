package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.type.ImString;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ModifyFileWindow {
    public static boolean open = false;
    private static String text;
    private static Identifier identifier;
    private static FileHierarchy.FileModifyAction action;
    private static ImString fileName;

    public static void open(String text, Identifier identifier, FileHierarchy.FileModifyAction action) {
        open = true;
        ModifyFileWindow.text = text;
        ModifyFileWindow.identifier = identifier;
        ModifyFileWindow.action = action;
        ModifyFileWindow.fileName = new ImString(identifier.getPath());
    }

    public static void render() {
        if (!open) return;
        if (ImGui.begin("ModifyFilePopup")) {
            ImGui.inputText("File Name:", fileName);
            ImGui.sameLine();
            if (ImGui.button(text)) {
                // Create the file
                open = false;
                action.execute(fileName.get());
            }
            ImGui.end();
        }
    }
}
