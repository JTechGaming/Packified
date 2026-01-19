package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.type.ImBoolean;
import me.jtech.packified.client.windows.popups.ModelPickerWindow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class WorldWindow {
    public static ImBoolean isOpen = new ImBoolean(true);

    public static void render() {
        if (!isOpen.get() || ModelEditorWindow.isModelWindowFocused()) return;
        if (ImGui.begin("World")) {
            if (MinecraftClient.getInstance().player != null) {
                ItemStack stack = MinecraftClient.getInstance().player.getMainHandStack();
                if (stack == null || stack.isEmpty()) {
                    ImGui.text("Hold an item to add a custom model to it");
                } else {
                    if (ImGui.button("Add custom model to item")) {
                        ModelPickerWindow.open();
                    }
                }
            }
        }
        ImGui.end();
    }
}
