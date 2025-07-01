package me.jtech.packified.client;

import imgui.ImGui;
import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.helpers.NotificationHelper;
import me.jtech.packified.client.helpers.TutorialHelper;
import me.jtech.packified.client.networking.PacketSender;
import me.jtech.packified.Packified;
import me.jtech.packified.client.util.SyncPacketData;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.*;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.client.windows.LogWindow;
import me.jtech.packified.client.networking.packets.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Environment(EnvType.CLIENT)
public class PackifiedClient implements ClientModInitializer {
    public static final String MOD_ID = Packified.MOD_ID;
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String version = Packified.version;
    public static boolean reloaded = false;

    public static boolean shouldRender = false;
    private static KeyBinding keyBinding;

    public static ResourcePackProfile currentPack;

    public static List<UUID> markedPlayers = new ArrayList<>();
    public static Map<UUID, String> playerPacks = new HashMap<>();

    private static final List<SyncPacketData.AssetData> chunkedAssetsBuffer = new ArrayList<>();
    private static boolean isFirstPacket = true;

    //TODO fix known incompatibility: shared resources mod
    //TODO fix known incompatibility: ImmediatelyFast ???
    //TODO fix known incompatibility: Essential Mod

    @Override
    public void onInitializeClient() {
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.packified.menu", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_F8, // The keycode of the key
                "category.packified.editor" // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.wasPressed()) {
                toggleVisibility();
            }
            PacketSender.processQueue();

            if (reloaded) {
                reloaded = false;
                sendBlockUpdateToLoadedChunks();
            }
        });

        TutorialHelper.init();

        // Prevent Minecraft from locking the cursor when clicking
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (shouldRender) {
                if (keyBinding.wasPressed()) {
                    toggleVisibility();
                }
                if (!ImGuiImplementation.grabbed) {
                    KeyBinding.unpressAll();
                    unlockCursor();
                }

                handleKeypresses();
            }
        });

        AtomicReference<NotificationHelper.Notification> notification = new AtomicReference<>();

        ClientPlayNetworking.registerGlobalReceiver(S2CSyncPackChanges.ID, (payload, context) -> {
            // Logic to apply the pack changes
            ResourcePackProfile pack = PackUtils.getPack(payload.packetData().packName());
            if (pack == null) {
                ClientPlayNetworking.send(new C2SRequestFullPack(payload.packetData().packName(), payload.player()));
                return;
            }
            currentPack = pack;

            SyncPacketData data = payload.packetData();
            accumulativeAssetDownload(data, pack, notification.get());
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CSendFullPack.ID, (payload, context) -> {
            // Logic to fully download, add and apply the pack
            SyncPacketData data = payload.packetData();
            if (isFirstPacket) {
                isFirstPacket = false;
                // Create the pack
                FileUtils.createPack(data.packName(), List.of(), data.metadata());

                notification.set(NotificationHelper.addNotification("Loading pack: " + data.packName(), "Downloading assets...", 5000, 0, data.packetAmount()));
            }
            if (notification.get() != null) {
                notification.get().setProgress(notification.get().getProgress() + 1);
            }

            LogWindow.addPackDownloadInfo("Downloading pack from server: " + payload.packetData().packName());
            LogWindow.addPackDownloadInfo(notification.get().getProgress() + " / " + notification.get().getMaxProgress());

            accumulativeAssetDownload(data, currentPack, notification.get());
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CRequestFullPack.ID, (payload, context) -> {
            String packName = payload.packName();
            if (packName.equals("!!currentpack!!")) {
                if (currentPack == null) {
                    return;
                }
                packName = currentPack.getDisplayName().getString();
            }
            ResourcePackProfile pack = PackUtils.getPack(packName);
            if (pack == null) {
                return;
            }
            PackUtils.sendFullPack(pack, payload.player());
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CPlayerHasMod.ID, (payload, context) -> {
            LOGGER.info("Player {} has the mod installed: {}", payload.specificPlayer(), payload.moddedPlayers().contains(payload.specificPlayer()));
            Packified.moddedPlayers = payload.moddedPlayers();
            markedPlayers = payload.moddedPlayers();
            if (payload.moddedPlayers().contains(payload.specificPlayer())) {
                markedPlayers.add(payload.specificPlayer());
            } else {
                markedPlayers.remove(payload.specificPlayer());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CInfoPacket.ID, (payload, context) -> {
            playerPacks.remove(payload.player());
            playerPacks.put(payload.player(), payload.info());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.send(new C2SHasMod(version));
            if (PackifiedClient.currentPack != null) {
                ClientPlayNetworking.send(new C2SInfoPacket(currentPack.getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
            }
        });

        LogWindow.addInfo("Packified Client initialized");
    }

    public static void sendBlockUpdateToLoadedChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        int renderDistance = client.options.getViewDistance().getValue() * 2;
        ChunkPos chunkPos = client.player.getChunkPos();

        for (int i = 0; i < renderDistance; i++) {
            for (int j = 0; j < renderDistance; j++) {
                int chunkX = chunkPos.x + i - renderDistance / 2;
                int chunkZ = chunkPos.z + j - renderDistance / 2;
                if (!client.world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                int ySections = ChunkSectionPos.getSectionCoord(client.world.getHeight());
                for (int chunkY = 0; chunkY < ySections; chunkY++) {
                    client.worldRenderer.scheduleChunkRender(chunkX, chunkY, chunkZ, true);
                }
            }
        }
    }

    private static void accumulativeAssetDownload(SyncPacketData data, ResourcePackProfile pack, NotificationHelper.Notification notification) {
        for (SyncPacketData.AssetData asset : data.assets()) {
            if (!asset.finalChunk()) {
                if (bufferContainsAsset(asset.path())) {
                    int index = bufferGetAsset(asset.path());
                    chunkedAssetsBuffer.get(index).setAssetData(chunkedAssetsBuffer.get(index).assetData() + asset.assetData());
                } else {
                    chunkedAssetsBuffer.add(asset);
                }
                continue;
            }
            SyncPacketData.AssetData assetData;
            if (bufferContainsAsset(asset.path())) {
                int index = bufferGetAsset(asset.path());
                assetData = chunkedAssetsBuffer.get(index);
                assetData.setAssetData(assetData.assetData() + asset.assetData());
                chunkedAssetsBuffer.remove(index);
            } else {
                assetData = asset;
            }
            PackifiedClient.LOGGER.info(assetData.path().toString());
            FileUtils.saveSingleFile(assetData.path(), assetData.extension(), assetData.assetData(), pack);
        }
        if (data.finalChunk()) {
            chunkedAssetsBuffer.clear();
        }
        if (data.lastData()) {
            PackifiedClient.LOGGER.info("lastData");
            FileUtils.setMCMetaContent(pack, data.metadata());
            CompletableFuture.runAsync(PackUtils::reloadPack);
            if (notification != null) {
                notification.setProgress(notification.getMaxProgress());
            }
            isFirstPacket = true;
            LogWindow.addInfo("Pack " + pack.getDisplayName().getString() + " downloaded successfully!");
        }
    }

    public static int bufferGetAsset(Path path) {
        for (SyncPacketData.AssetData asset : chunkedAssetsBuffer) {
            if (asset.path().equals(path)) {
                return chunkedAssetsBuffer.indexOf(asset);
            }
        }
        return 0;
    }

    public static boolean bufferContainsAsset(Path path) {
        for (SyncPacketData.AssetData asset : chunkedAssetsBuffer) {
            if (asset.path().equals(path)) {
                return true;
            }
        }
        return false;
    }

    boolean saveKeyPressed = false;
    boolean closeKeyPressed = false;
    boolean reloadKeyPressed = false;

    private void handleKeypresses() {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrlPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shiftPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT);

        if (ctrlPressed) {
            if (currentPack == null) {
                return;
            }

            if (InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_R)) {
                if (reloadKeyPressed) {
                    return;
                }
                reloadKeyPressed = true;
                CompletableFuture.runAsync(PackUtils::reloadPack);
            } else {
                reloadKeyPressed = false;
            }

            if (EditorWindow.openFiles.isEmpty() || EditorWindow.currentFile == null) {
                return;
            }
            if (InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_S)) {
                if (saveKeyPressed) {
                    return;
                }
                saveKeyPressed = true;
                if (shiftPressed) {
                    // Handle Ctrl+Shift+S
                    FileUtils.saveAllFiles();
                } else {
                    // Handle Ctrl+S
                    FileUtils.saveSingleFile(EditorWindow.currentFile.getPath(), EditorWindow.currentFile.getExtension(), FileUtils.getContent(EditorWindow.currentFile), currentPack);
                }
            } else {
                saveKeyPressed = false;
            }
            if (InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_W)) {
                if (closeKeyPressed) {
                    return;
                }
                closeKeyPressed = true;
                // Handle Ctrl+W
                if (shiftPressed) {
                    for (PackFile file : EditorWindow.openFiles) { // Maybe move these into methods in EditorWindow
                        if (file.isModified()) {
                            ConfirmWindow.open("close all files", "Any unsaved changes might be lost.", () -> {
                                EditorWindow.modifiedFiles += EditorWindow.openFiles.size();
                                EditorWindow.openFiles.clear();
                            });
                            return;
                        }
                    }
                    EditorWindow.modifiedFiles += EditorWindow.openFiles.size();
                    EditorWindow.openFiles.clear();
                } else {
                    if (EditorWindow.currentFile.isModified()) {
                        ConfirmWindow.open("close this file", "Any unsaved changes might be lost.", () -> {
                            EditorWindow.modifiedFiles++;
                            EditorWindow.openFiles.remove(EditorWindow.currentFile);
                        });
                        return;
                    }
                    EditorWindow.modifiedFiles++;
                    EditorWindow.openFiles.remove(EditorWindow.currentFile);
                }
            } else {
                closeKeyPressed = false;
            }
        }
    }

    private void toggleVisibility() {
        shouldRender = !shouldRender;

        if (shouldRender) {
            ImGuiImplementation.aspectRatio = (float) MinecraftClient.getInstance().getWindow().getWidth() / MinecraftClient.getInstance().getWindow().getHeight();
            ImGui.setWindowFocus("Main");
            unlockCursor();
        } else {
            lockCursor();
        }

        if (!(boolean) ModConfig.getBoolean("stayincreative", false)) {
            GameMode gameMode = shouldRender ? GameMode.SPECTATOR : getPreviousGameMode();
            changeGameMode(gameMode);
        }

        ImGuiImplementation.shouldRender = shouldRender;
    }

    public static void changeGameMode(GameMode gameMode) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player.hasPermissionLevel(2)) {
            if (gameMode.equals(GameMode.CREATIVE)) {
                client.player.networkHandler.sendCommand("gamemode creative");
            } else if (gameMode.equals(GameMode.SURVIVAL)) {
                client.player.networkHandler.sendCommand("gamemode survival");
            } else if (gameMode.equals(GameMode.SPECTATOR)) {
                client.player.networkHandler.sendCommand("gamemode spectator");
            } else if (gameMode.equals(GameMode.ADVENTURE)) {
                client.player.networkHandler.sendCommand("gamemode adventure");
            } else {
                LOGGER.error("Unknown game mode: {}", gameMode);
            }
        }
    }

    public static void unlockCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        client.mouse.unlockCursor();
    }

    public static GameMode getPreviousGameMode() {
        ClientPlayerInteractionManager clientPlayerInteractionManager = MinecraftClient.getInstance().interactionManager;
        GameMode gameMode = clientPlayerInteractionManager.getPreviousGameMode();
        if (gameMode != null) {
            return gameMode;
        } else {
            return clientPlayerInteractionManager.getCurrentGameMode() == GameMode.CREATIVE ? GameMode.SURVIVAL : GameMode.CREATIVE;
        }
    }

    public static void lockCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        client.mouse.lockCursor();
    }
}
