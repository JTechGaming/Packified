package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.internal.ImGuiWindow;
import imgui.type.ImBoolean;
import me.jtech.packified.client.imgui.ImGuiImplementation;

public class ConfirmWindow {
    public static ImBoolean open = new ImBoolean(false);
    private static String actionText;
    private static String additionalText;
    private static ConfirmAction action;

    public static void open(String actionText, String additionalText, ConfirmAction action) {
        open.set(true);
        ConfirmWindow.actionText = actionText;
        ConfirmWindow.additionalText = additionalText;
        ConfirmWindow.action = action;
    }

    public static void render() {
        if (!open.get()) return;

        ImGuiImplementation.pushWindowCenterPos();

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getLastWindowCenterPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        if (ImGui.begin("ConfirmPopup", open)) {
            ImGuiImplementation.centeredText("Are you sure you want to " + actionText + "?");
            ImGui.spacing();
            ImGui.spacing();
            ImGuiImplementation.centeredText(additionalText);
            ImGui.spacing();
            ImGui.spacing();
            ImGui.spacing();
            if (ImGui.button("Cancel")) {
                open.set(false);
            }
            ImGui.sameLine();
            ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - ImGui.calcTextSize("Confirm  ").x);
            if (ImGui.button("Confirm")) {
                open.set(false);
                action.execute();
            }
            ImGui.end();
        }
    }

    @FunctionalInterface
    public interface ConfirmAction {
        void execute();
    }
}
