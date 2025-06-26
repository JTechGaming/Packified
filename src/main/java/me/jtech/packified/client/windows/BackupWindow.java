package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourcePackProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class BackupWindow {
    public static ImBoolean open = new ImBoolean(false);

    public static void render() {
        if (!open.get()) {
            return;
        }

        // Set position to center of viewport
        ImVec2 centerPos = ImGuiImplementation.getCenterViewportPos();
        ImGui.setNextWindowPos(centerPos.x, centerPos.y, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());

        if (ImGui.begin("Backups", open, ImGuiWindowFlags.MenuBar)) {
            ResourcePackProfile pack = PackifiedClient.currentPack;
            if (pack == null) {
                ImGui.setCursorPos((ImGui.getWindowWidth() - ImGui.calcTextSize("No pack loaded").x) / 2, (ImGui.getWindowHeight() - ImGui.getTextLineHeightWithSpacing()) / 2);
                ImGui.text("No pack loaded");
                // Centered button to load a pack
                ImGui.setCursorPos((ImGui.getWindowWidth() - ImGui.calcTextSize("Load Pack").x) / 2, (ImGui.getWindowHeight() - ImGui.getTextLineHeightWithSpacing()) / 2 + ImGui.getTextLineHeightWithSpacing());
                if (ImGui.button("Load Pack")) {
                    SelectPackWindow.open.set(true);
                }
                ImGui.end();
                return;
            }
            Path backupDir = FabricLoader.getInstance().getConfigDir().resolve("packified-backups");
            // Get all backups for the current pack
            try (Stream<Path> paths = Files.walk(backupDir)) {
                AtomicInteger amount = new AtomicInteger();
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().contains(pack.getDisplayName().getString()))
                        .forEach(path -> {
                            // Process each file
                            amount.getAndIncrement();
                            if (ImGui.selectable(path.getFileName().toString().replace(pack.getDisplayName().getString() + "-", "").replace(".zip", ""), true, ImGuiSelectableFlags.AllowDoubleClick)) {
                                if (ImGui.isMouseDoubleClicked(0)) {
                                    try {
                                        FileUtils.unzipPack(path.toFile(), PackUtils.getPackFolder(pack.createResourcePack()).toFile());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    open.set(false);
                                }
                            }
                            if (ImGui.beginPopupContextItem(path.getFileName().toString())) {
                                if (ImGui.menuItem("Restore")) {
                                    try {
                                        EditorWindow.openFiles.clear();
                                        FileUtils.unzipPack(path.toFile(), PackUtils.getPackFolder(pack.createResourcePack()).toFile());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    open.set(false);
                                }
                                if (ImGui.menuItem("Delete")) {
                                    // Delete file
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                ImGui.separator();
                                if (ImGui.menuItem("Open in Explorer")) {
                                    FileUtils.openFileInExplorer(path);
                                }
                                ImGui.separator();
                                if (ImGui.menuItem("Delete All")) {
                                    // Delete file
                                    ConfirmWindow.open("delete all backups", "They will be lost forever", FileUtils::clearBackups);
                                }
                                ImGui.endPopup();
                            }
                        });
                if (amount.get() == 0) {
                    ImGui.setCursorPos((ImGui.getWindowWidth() - ImGui.calcTextSize("No backups found").x) / 2, (ImGui.getWindowHeight() - ImGui.getTextLineHeightWithSpacing()) / 2 + ImGui.getTextLineHeightWithSpacing());
                    ImGui.text("No backups found");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ImGui.end();
    }
}
