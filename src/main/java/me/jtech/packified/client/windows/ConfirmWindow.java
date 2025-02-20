package me.jtech.packified.client.windows;

import imgui.ImGui;

public class ConfirmWindow {
    public static boolean open = false;
    private static String actionText;
    private static String additionalText;
    private static ConfirmAction action;

    public static void open(String actionText, String additionalText, ConfirmAction action) {
        open = true;
        ConfirmWindow.actionText = actionText;
        ConfirmWindow.additionalText = additionalText;
        ConfirmWindow.action = action;
    }

    public static void render() {
        if (!open) return;
        if (ImGui.begin("ConfirmPopup")) {
            ImGui.text("Are you sure you want to " + actionText + "?");
            ImGui.text(additionalText);
            if (ImGui.button("Cancel")) {
                open = false;
            }
            if (ImGui.button("Confirm")) {
                open = false;
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
