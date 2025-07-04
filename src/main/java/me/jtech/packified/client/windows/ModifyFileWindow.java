package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class ModifyFileWindow {
    public static ImBoolean open = new ImBoolean(false);
    private static String text;
    private static Path path;
    private static FileHierarchy.FileModifyAction action;
    private static ImString fileName;

    public static void open(String text, Path path, FileHierarchy.FileModifyAction action) {
        open.set(true);
        ModifyFileWindow.text = text;
        ModifyFileWindow.path = path;
        ModifyFileWindow.action = action;
        ModifyFileWindow.fileName = new ImString(FileUtils.getRelativePackPath(path));
    }

    public static void render() {
        if (!open.get()) return;

        // Set position to center
        ImVec2 centerPos = ImGuiImplementation.getLastWindowCenterPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        if (ImGui.begin("ModifyFilePopup", open)) {
            fileName = new ImString(fileName.get(), fileName.getLength() + 8);
            ImGui.inputText("File Name:", fileName);
            ImGui.sameLine();
            if (ImGui.button(text)) {
                // Create the file
                open.set(false);
                action.execute(fileName.get());
            }
        }
        ImGui.end();
    }
}
