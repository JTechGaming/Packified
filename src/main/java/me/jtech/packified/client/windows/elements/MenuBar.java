package me.jtech.packified.client.windows.elements;

import imgui.ImGui;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.util.FileDialog;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.helpers.TutorialHelper;
import me.jtech.packified.client.windows.*;
import me.jtech.packified.client.networking.packets.C2SInfoPacket;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
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
                ImGui.separator();
                if (ImGui.menuItem("Open Pack Folder")) {
                    FileUtils.openFileInExplorer(new File("resourcepacks/").toPath());
                }
                ImGui.separator();
                if (ImGui.menuItem("Preferences", "Ctrl+Alt+S", PreferencesWindow.isOpen.get())) {
                    PreferencesWindow.isOpen.set(!PreferencesWindow.isOpen.get());
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Window")) {
                if (ImGui.menuItem("Multiplayer", null, MultiplayerWindow.isOpen.get())) {
                    MultiplayerWindow.isOpen.set(!MultiplayerWindow.isOpen.get());
                }
                if (ImGui.menuItem("File Hierarchy", null, FileHierarchy.isOpen.get())) {
                    FileHierarchy.isOpen.set(!FileHierarchy.isOpen.get());
                }
                if (ImGui.menuItem("File Editor", null, EditorWindow.isOpen.get())) {
                    EditorWindow.isOpen.set(!EditorWindow.isOpen.get());
                }
                if (ImGui.menuItem("Log", null, LogWindow.isOpen.get())) {
                    LogWindow.isOpen.set(!LogWindow.isOpen.get());
                }
                ImGui.endMenu();
            }

            if (Packified.debugMode) {
                if (ImGui.beginMenu("Debug")) {
                    if (ImGui.menuItem("Send pack to self") && PackifiedClient.currentPack != null) {
                        PackUtils.sendFullPack(PackifiedClient.currentPack, MinecraftClient.getInstance().player.getUuid());
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Please do not use this......");
                    }
                    ImGui.endMenu();
                }
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("About")) {
                    System.out.println("Show About Window");
                }
                if (ImGui.menuItem("Toggle Debug Mode", null, Packified.debugMode)) {
                    Packified.debugMode = !Packified.debugMode;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text("This enables some debugging features.");
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
