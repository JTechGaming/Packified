package me.jtech.packified.client.uiElements;

import imgui.ImGui;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.PackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class MenuBar {
    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Something")) {
                    System.out.println("Something clicked");
                }
                ImGui.separator();
                if (ImGui.menuItem("Export")) {
                    System.out.println("Export clicked");
                }
                ImGui.separator();
                if (ImGui.menuItem("Open")) {
                    System.out.println("Settings clicked");
                }
                if (ImGui.beginMenu("Open Pack")) {
                    for (int i = 0; i < PackUtils.refresh().size(); i++) {
                        if (ImGui.menuItem(PackUtils.refresh().get(i).getDisplayName().getString())) {
                            PackifiedClient.currentPack = PackUtils.refresh().get(i);
                        }
                    }
                    ImGui.endMenu();
                }
                ImGui.separator();
                if (ImGui.menuItem("Exit")) {
                    MinecraftClient.getInstance().scheduleStop();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Preferences")) {
                if (ImGui.menuItem("Settings")) {
                    System.out.println("Settings clicked");
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Undo", "CTRL+Z")) {
                    System.out.println("Undo clicked");
                }
                if (ImGui.menuItem("Redo", "CTRL+Y")) {
                    System.out.println("Redo clicked");
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("About")) {
                    System.out.println("Show About Window");
                }
                ImGui.endMenu();
            }

            ImGui.endMainMenuBar();
        }
    }
}
