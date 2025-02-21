package me.jtech.packified.client;

import me.jtech.packified.Packified;
import me.jtech.packified.SyncPacketData;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackFile;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.windows.ConfirmWindow;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.packets.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameModeSelectionScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class PackifiedClient implements ClientModInitializer {
    public static final String MOD_ID = Packified.MOD_ID;
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String version = Packified.version;

    boolean shouldRender = false;
    private static KeyBinding keyBinding;

    public static ResourcePackProfile currentPack;

    public static List<UUID> markedPlayers = new ArrayList<>();
    public static Map<UUID, String> playerPacks = new HashMap<>();

    private static final List<List<SyncPacketData.AssetData>> chunkedAssetsBuffer = new ArrayList<>();
    //TODO fix known incompatibility: shared resources mod
    //TODO fix known incompatibility: ImmediatelyFast
    //TODO file->new file creates a malformed path with a duplicate namespace
    //TODO fix the select pack screen being extremely laggy

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
        });
        // Prevent Minecraft from locking the cursor when clicking
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (shouldRender) {
                if (keyBinding.wasPressed()) {
                    toggleVisibility();
                }
                KeyBinding.unpressAll();
                unlockCursor();

                handleKeypresses();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CSyncPackChanges.ID, (payload, context) -> {
            // Logic to apply the pack changes
            ResourcePackProfile pack = PackUtils.getPack(payload.packetData().packName());
            if (pack == null) {
                ClientPlayNetworking.send(new C2SRequestFullPack(payload.packetData().packName(), payload.player()));
                return;
            }
            ResourcePackProfile oldPack = currentPack;
            currentPack = pack;
            List<SyncPacketData.AssetData> assets = payload.packetData().assets();
            for (SyncPacketData.AssetData asset : assets) {
                FileUtils.saveSingleFile(asset.identifier(), asset.extension(), asset.assetData());
            }
            FileUtils.setMCMetaContent(pack, payload.packetData().metadata());

            // Reload the pack
            PackUtils.reloadPack();

            currentPack = oldPack;
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CSendFullPack.ID, (payload, context) -> {
            // Logic to fully download, add and apply the pack
            SyncPacketData data = payload.packetData();

            if (data.chunks() > 1) {
                chunkedAssetsBuffer.add(new ArrayList<>());
                chunkedAssetsBuffer.get(data.chunkIndex()).addAll(data.assets());
                if (chunkedAssetsBuffer.get(data.chunkIndex()).size() == data.assets().size()) {
                    if (data.chunkIndex() == data.chunks() - 1) {
                        List<SyncPacketData.AssetData> assets = new ArrayList<>();
                        for (List<SyncPacketData.AssetData> chunk : chunkedAssetsBuffer) {
                            assets.addAll(chunk);
                        }
                        chunkedAssetsBuffer.clear();
                        data = new SyncPacketData(data.packName(), assets, data.metadata(), 1, 0);
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }

            // Create the pack
            FileUtils.createPack(data.packName(), data.assets(), data.metadata());
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
        });
    }

    private boolean isSaveKeyPressed() {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrlPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean sPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_S);
        return ctrlPressed && sPressed;
    }

    boolean saveKeyPressed = false;
    boolean closeKeyPressed = false;
    boolean reloadKeyPressed = false;

    private void handleKeypresses() {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrlPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shiftPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT);

        if (ctrlPressed) {
            if (currentPack == null || EditorWindow.openFiles.isEmpty() || EditorWindow.currentFile == null) {
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
                    FileUtils.saveSingleFile(EditorWindow.currentFile.getIdentifier(), EditorWindow.currentFile.getExtension().getExtension(), FileUtils.getContent(EditorWindow.currentFile));
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
            if (InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_R)) {
                if (reloadKeyPressed) {
                    return;
                }
                reloadKeyPressed = true;
                PackUtils.reloadPack();
            } else {
                reloadKeyPressed = false;
            }
        }
    }

    private void toggleVisibility() {
        shouldRender = !shouldRender;

        if (shouldRender) {
            ImGuiImplementation.aspectRatio = (float) MinecraftClient.getInstance().getWindow().getWidth() / MinecraftClient.getInstance().getWindow().getHeight();
            unlockCursor();
        } else {
            lockCursor();
        }

        GameMode gameMode = shouldRender ? GameMode.SPECTATOR : getPreviousGameMode();
        MinecraftClient client = MinecraftClient.getInstance();
        assert client.player != null;
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

        ImGuiImplementation.shouldRender = shouldRender;
    }

    private static void unlockCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        client.mouse.unlockCursor();
    }

    private GameMode getPreviousGameMode() {
        ClientPlayerInteractionManager clientPlayerInteractionManager = MinecraftClient.getInstance().interactionManager;
        GameMode gameMode = clientPlayerInteractionManager.getPreviousGameMode();
        if (gameMode != null) {
            return gameMode;
        } else {
            return clientPlayerInteractionManager.getCurrentGameMode() == GameMode.CREATIVE ? GameMode.SURVIVAL : GameMode.CREATIVE;
        }
    }

    private static void lockCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        client.mouse.lockCursor();
    }
}
