package me.jtech.packified.client.uiElements;

import imgui.ImGui;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.FileDialog;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.windows.BackupWindow;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.packets.C2SInfoPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class MenuBar {
    private static List<ResourcePackProfile> packs = new ArrayList<>();
    private static boolean first = true;

    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.beginMenu("New")) {
                    if (ImGui.menuItem("Pack")) {
                        EditorWindow.openFiles.clear();
                        PackifiedClient.currentPack = null;
                        PackUtils.createPack();
                    }
                    ImGui.endMenu();
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
                                        FileUtils.zip(folderPath.toFile(), path.getFileName().toString());
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
                    if (first) {
                        packs = PackUtils.refresh();
                        first = false;
                    }
                    for (ResourcePackProfile pack : packs) {
                        if (ImGui.menuItem(pack.getDisplayName().getString())) {
                            EditorWindow.openFiles.clear();
                            PackifiedClient.currentPack = pack;
                            PackUtils.checkPackType(pack);
                            if (PackifiedClient.currentPack != null) {
                                ClientPlayNetworking.send(new C2SInfoPacket(PackifiedClient.currentPack.getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
                            }
                        }
                    }
                    ImGui.endMenu();
                } else {
                    first = true;
                }
                ImGui.separator();
                if (ImGui.menuItem("Exit")) {
                    MinecraftClient.getInstance().scheduleStop();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Force Reload")) {
                    PackUtils.reloadPack();
                }
                ImGui.separator();
                if (ImGui.menuItem("Undo", "CTRL+Z")) {
                    if (EditorWindow.currentFile != null) {
                        EditorWindow.currentFile.getTextEditor().undo(1);
                    }
                }
                if (ImGui.menuItem("Redo", "CTRL+Y")) {
                    if (EditorWindow.currentFile != null) {
                        EditorWindow.currentFile.getTextEditor().redo(1);
                    }
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Preferences")) {
                if (ImGui.menuItem("Settings")) {
                    System.out.println("Settings clicked");
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("About")) {
                    System.out.println("Show About Window");
                }
                if (ImGui.menuItem("Toggle Debug Mode")) {
                    Packified.debugMode = !Packified.debugMode;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text("Is: " + Packified.debugMode);
                    ImGui.endTooltip();
                }
                ImGui.endMenu();
            }

            ImGui.endMainMenuBar();
        }
    }
}
