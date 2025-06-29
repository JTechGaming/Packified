package me.jtech.packified.client.uiElements;

import imgui.ImGui;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.FileDialog;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.util.TutorialHelper;
import me.jtech.packified.client.windows.*;
import me.jtech.packified.packets.C2SInfoPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class MenuBar {
    private static List<ResourcePackProfile> packs = new ArrayList<>();
    private static boolean firstOpenPack = true;
    private static boolean firstOpenInternalPack = true;

    public static void render() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.beginMenu("New")) {
                    if (ImGui.menuItem("Pack")) {
                        EditorWindow.openFiles.clear();
                        PackifiedClient.currentPack = null;
                        PackCreationWindow.isOpen.set(!PackCreationWindow.isOpen.get());
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
                        PackUtils.exportPack();
                    }
                }
                ImGui.separator();
                if (ImGui.menuItem("Backups")) {
                    BackupWindow.open.set(!BackupWindow.open.get());
                }
                ImGui.separator();
                if (ImGui.beginMenu("Open Pack")) {
                    if (firstOpenPack) {
                        packs = PackUtils.refresh();
                        firstOpenPack = false;
                    }
                    for (ResourcePackProfile pack : packs) {
                        if (ImGui.menuItem(pack.getDisplayName().getString())) {
                            EditorWindow.openFiles.clear();
                            PackifiedClient.currentPack = pack;
                            PackUtils.checkPackType(pack);
                            ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();

                            if (PackifiedClient.currentPack != null) {
                                resourcePackManager.enable(PackifiedClient.currentPack.getDisplayName().getString());
                                ClientPlayNetworking.send(new C2SInfoPacket(PackifiedClient.currentPack.getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
                            }
                        }
                    }
                    ImGui.endMenu();
                } else {
                    firstOpenPack = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Open a pack from your resource packs folder");
                }
                if (ImGui.beginMenu("Open Internal Pack")) {
                    if (firstOpenInternalPack) {
                        packs = PackUtils.refreshInternalPacks();
                        firstOpenInternalPack = false;
                    }
                    for (ResourcePackProfile pack : packs) {
                        if (ImGui.menuItem(pack.getDisplayName().getString())) {
                            ConfirmWindow.open("do that?", "You are about to load a pack from another mod. Before you do this, make sure that '" + pack.getDisplayName().getString() + "' has a license that allows you to look at/modify it's resources. Continue at your own risk.", () -> {
                                FileUtils.loadIdentifierPackAssets(pack);
                                EditorWindow.openFiles.clear();
                                PackUtils.refresh();
                                PackifiedClient.currentPack = PackUtils.getPack(PackUtils.legalizeName(pack.getDisplayName().getString()));
                                ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();

                                if (PackifiedClient.currentPack != null) {
                                    resourcePackManager.enable(PackifiedClient.currentPack.getDisplayName().getString());
                                    ClientPlayNetworking.send(new C2SInfoPacket(PackifiedClient.currentPack.getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
                                }
                            });
                        }
                    }
                    ImGui.endMenu();
                } else {
                    firstOpenInternalPack = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Open one of the internal resource packs (like packs from the game or mods)");
                }
                ImGui.separator();
                if (ImGui.menuItem("Exit")) {
                    MinecraftClient.getInstance().scheduleStop();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Force Reload")) {
                    CompletableFuture.runAsync(PackUtils::reloadPack);
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
                if (ImGui.menuItem("Preferences")) {
                    PreferencesWindow.isOpen.set(!PreferencesWindow.isOpen.get());
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Window")) {
                if (ImGui.menuItem("Multiplayer")) {
                    MultiplayerWindow.isOpen.set(!MultiplayerWindow.isOpen.get());
                }
                if (ImGui.menuItem("File Hierarchy")) {
                    FileHierarchy.isOpen.set(!FileHierarchy.isOpen.get());
                }
                if (ImGui.menuItem("File Editor")) {
                    EditorWindow.isOpen.set(!EditorWindow.isOpen.get());
                }
                if (ImGui.menuItem("Log")) {
                    LogWindow.isOpen.set(!LogWindow.isOpen.get());
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
                if (ImGui.menuItem("Reset Tutorial")) {
                    ConfirmWindow.open("reset the tutorial", "This will reset the tutorial progress.", () -> {
                        TutorialHelper.resetTutorial();
                        TutorialHelper.updateTutorialConfig();
                        TutorialHelper.isOpen.set(true);
                    });
                }
                ImGui.endMenu();
            }

            ImGui.endMainMenuBar();
        }
    }

}
