package me.jtech.packified.client.uiElements;

import imgui.ImGui;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.FileDialog;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.windows.BackupWindow;
import me.jtech.packified.client.windows.EditorWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class MenuBar {
    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Something")) {
                    System.out.println("Something clicked");
                }
                ImGui.separator();
                if (ImGui.beginMenu("Import")) {
                    if (ImGui.menuItem("Pack")) {
                        String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified").toString();
                        FileDialog.openFileDialog(defaultFolder, "Pack Zip", "zip").thenAccept(pathStr -> {
                            if (pathStr != null) {
                                Path path = Path.of(pathStr);
                                MinecraftClient.getInstance().submit(() -> {
                                    File zipFolder = new File("resourcepacks/" + path.getFileName().toString().replace(".zip", ""));
                                    try {
                                        FileUtils.unzipPack(path.toFile(), zipFolder);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        });
                    }
                    if (PackifiedClient.currentPack != null) {
                        if (ImGui.menuItem("File")) {
                            String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified").toString();
                            FileDialog.openFileDialog(defaultFolder, "Files", "json", "png").thenAccept(pathStr -> {
                                if (pathStr != null) {
                                    Path path = Path.of(pathStr);
                                    MinecraftClient.getInstance().submit(() -> {
                                        FileUtils.importFile(path);
                                    });
                                }
                            });
                        }
                    }
                    ImGui.endMenu();
                }
                if (PackifiedClient.currentPack != null) {
                    if (ImGui.menuItem("Export")) {
                        String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified/exports").toString();
                        File folderFile = Path.of(defaultFolder).toFile();
                        folderFile.mkdirs();
                        FileDialog.saveFileDialog(defaultFolder, PackifiedClient.currentPack.getDisplayName().getString() + ".zip", "json", "png").thenAccept(pathStr -> {
                            if (pathStr != null) {
                                Path path = Path.of(pathStr);
                                Path folderPath = path.getParent();
                                MinecraftClient.getInstance().submit(() -> {
                                    try {
                                        FileUtils.zip(PackifiedClient.currentPack, folderPath.toFile(), path.getFileName().toString());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        });
                    }
                }
                ImGui.separator();
                if (ImGui.menuItem("Backups")) {
                    BackupWindow.open = !BackupWindow.open;
                }
                ImGui.separator();
                if (ImGui.beginMenu("Open Pack")) {
                    for (int i = 0; i < PackUtils.refresh().size(); i++) {
                        if (ImGui.menuItem(PackUtils.refresh().get(i).getDisplayName().getString())) {
                            EditorWindow.openFiles.clear();
                            PackifiedClient.currentPack = PackUtils.refresh().get(i);
                            PackUtils.checkPackType(PackUtils.refresh().get(i));
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
