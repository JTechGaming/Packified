package me.jtech.packified.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import imgui.ImGui;
import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.helpers.NotificationHelper;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.helpers.TutorialHelper;
import me.jtech.packified.client.helpers.VersionControlHelper;
import me.jtech.packified.client.networking.packets.C2SHasMod;
import me.jtech.packified.client.networking.packets.C2SInfoPacket;
import me.jtech.packified.client.networking.packets.C2SRequestFullPack;
import me.jtech.packified.client.networking.PacketSender;
import me.jtech.packified.Packified;
import me.jtech.packified.client.util.SyncPacketData;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.*;
import me.jtech.packified.client.windows.ModelEditorWindow;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.client.windows.LogWindow;
import me.jtech.packified.networking.packets.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Environment(EnvType.CLIENT)
public class PackifiedClient implements ClientModInitializer {
    public static final String MOD_ID = Packified.MOD_ID;
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String version = Packified.version;

    public static boolean shouldRender = false;
    private static KeyBinding openMenuKeybind;

    public static List<UUID> markedPlayers = new ArrayList<>();
    public static Map<UUID, String> playerPacks = new HashMap<>();

    private static boolean isFirstPacket = true;

    public static boolean reloaded = false;
    public static boolean loading = false;

    public static final RenderPipeline VIEWPORT_RESIZE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("packified", "pipeline/blit_screen"))
                    .withVertexShader(Identifier.of("packified", "core/blit_screen"))
                    .withFragmentShader(Identifier.of("packified", "core/blit_screen"))
                    .withSampler("InSampler")
                    .withDepthWrite(false)
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final KeyBinding.Category PACKIFIED_EDITOR_CATEGORY = KeyBinding.Category.create(Packified.identifier("editor"));

    @Override
    public void onInitializeClient() {
        openMenuKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.packified.menu", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_F8, // The keycode of the key
                PACKIFIED_EDITOR_CATEGORY // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openMenuKeybind.wasPressed()) {
                toggleVisibility();
            }
            PacketSender.processQueue();

            if (reloaded) {
                reloaded = false;
                loading = false;
                sendBlockUpdateToLoadedChunks();
            }
        });

        TutorialHelper.init();

        // Prevent Minecraft from locking the cursor when clicking
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (shouldRender) {
                if (openMenuKeybind.wasPressed()) {
                    toggleVisibility();
                }
                if (!ImGuiImplementation.grabbed) {
                    KeyBinding.unpressAll();
                    unlockCursor();
                }

                handleKeypresses();
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "before_chat"), (context, tickCounter) -> {
            if (loading) {
                int x = 10, y = 10; // Position on screen
                int width = 24, height = 24; // Size of the icon
                float angle = ((float) Util.getMeasuringTimeMs() / 8) % 360;

                context.getMatrices().pushMatrix();
                context.getMatrices().translate(x + (float) width /2, y + (float) height /2); // Move to the center of the icon
                context.getMatrices().rotate(angle); // Rotate around the center
                context.getMatrices().translate((float) -width /2, (float) -height /2);

                Identifier texture = Identifier.of(MOD_ID, "textures/ui/reload.png");
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, width, height, width, height);

                context.getMatrices().popMatrix();
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
            PackHelper.updateCurrentPack(pack);

            SyncPacketData data = payload.packetData();
            accumulativeZipDownload(data, notification.get());
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CSendFullPack.ID, (payload, context) -> {
            CompletableFuture.runAsync(() -> {
                SyncPacketData data = payload.packetData();

                if (isFirstPacket) {
                    isFirstPacket = false;
                    notification.set(NotificationHelper.addNotification(
                            "Loading pack: " + data.packName(),
                            "Downloading...",
                            5000,
                            0,
                            data.totalChunks()
                    ));
                }

                accumulativeZipDownload(data, notification.get());

                if (data.lastChunk()) {
                    isFirstPacket = true;
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CRequestFullPack.ID, (payload, context) -> {
            String packName = payload.packName();
            if (packName.equals("!!currentpack!!")) {
                if (PackHelper.isInvalid()) {
                    return;
                }
                packName = PackHelper.getCurrentPack().getDisplayName().getString();
            }
            ResourcePackProfile pack = PackUtils.getPack(packName);
            if (pack == null) {
                return;
            }
            PackUtils.sendFullPackZipped(pack, payload.player());
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

        ClientPlayNetworking.registerGlobalReceiver(S2CPushResponsePacket.ID, (payload, context) -> {
            if (!payload.success()) {
                LogWindow.addError("Push failed: " + payload.error());
                return;
            }

            VersionControlHelper.markAllAsPushedUpTo(payload.newHeadVersion());
            LogWindow.addInfo("Push successful!! New remote HEAD: " + payload.newHeadVersion());
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CFetchResponsePacket.ID, (payload, context) -> {
            VersionControlHelper.integrateRemoteCommits(
                    payload.headVersion(),
                    payload.commits()
            );

            LogWindow.addInfo("Fetched " + payload.commits().size() + " commits");
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CInfoPacket.ID, (payload, context) -> {
            playerPacks.remove(payload.player());
            playerPacks.put(payload.player(), payload.info());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.send(new C2SHasMod(version));
            if (PackHelper.isValid()) {
                ClientPlayNetworking.send(new C2SInfoPacket(PackHelper.getCurrentPack().getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
            }
        });

        LogWindow.addInfo("Packified Client initialized");
    }

    public static void sendBlockUpdateToLoadedChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world == null) return;

        int renderDistance = client.options.getViewDistance().getValue() * 2 + 2;
        ChunkPos chunkPos = client.player.getChunkPos();

        for (int i = 0; i < renderDistance; i++) {
            for (int j = 0; j < renderDistance; j++) {
                int chunkX = chunkPos.x + i - renderDistance / 2;
                int chunkZ = chunkPos.z + j - renderDistance / 2;
                int ySections = ChunkSectionPos.getSectionCoord(client.world.getHeight());
                for (int chunkY = 0; chunkY < ySections; chunkY++) {
                    client.worldRenderer.scheduleChunkRender(chunkX, chunkY, chunkZ);
                }
            }
        }
    }

    private static final Map<String, Map<Integer, byte[]>> zipChunksMap = new HashMap<>();
    private static String currentPackName = null;

    private static void accumulativeZipDownload(SyncPacketData data, NotificationHelper.Notification notification) {
        String packName = data.packName();

        // Initialize tracking for this pack if first chunk
        if (data.chunkIndex() == 0) {
            currentPackName = packName;
            zipChunksMap.put(packName, new HashMap<>());

            // Create the pack folder if it doesn't exist
            File packFolder = new File("resourcepacks/" + packName);
            if (!packFolder.exists()) {
                packFolder.mkdirs();
            }

            if (notification == null) {
                notification = NotificationHelper.addNotification(
                        "Loading pack: " + packName,
                        "Downloading...",
                        5000,
                        0,
                        data.totalChunks()
                );
            }
        }

        // Store this chunk
        zipChunksMap.get(packName).put(data.chunkIndex(), data.zipChunk());

        // Update progress
        if (notification != null) {
            notification.setProgress(data.chunkIndex() + 1);
        }

        if (Packified.debugMode) {
            LogWindow.addPackDownloadInfo("Received chunk " + (data.chunkIndex() + 1) + " / " + data.totalChunks());
        }

        // Check if we have all chunks
        Map<Integer, byte[]> chunks = zipChunksMap.get(packName);
        if (chunks.size() == data.totalChunks() || data.lastChunk()) {
            System.out.println("Last chunk, assembling zip");
            // Reassemble the zip file
            try {
                // Calculate total size
                int totalSize = 0;
                for (byte[] chunk : chunks.values()) {
                    totalSize += chunk.length;
                }

                // Combine all chunks in order
                byte[] completeZip = new byte[totalSize];
                int offset = 0;
                for (int i = 0; i < data.totalChunks(); i++) {
                    byte[] chunk = chunks.get(i);
                    if (chunk == null) {
                        LogWindow.addWarning("Potentially missing chunks " + i + " for pack: " + packName);
                    }
                    System.arraycopy(chunk, 0, completeZip, offset, chunk.length);
                    offset += chunk.length;
                }

                // Save to temp file and unzip
                Path tempZipPath = Files.createTempFile("received_pack_", ".zip");
                Files.write(tempZipPath, completeZip);

                // Unzip to pack folder
                File packFolder = new File("resourcepacks/" + packName);
                unzipPack(tempZipPath.toFile(), packFolder);

                // Delete temp zip
                Files.delete(tempZipPath);

                // Save metadata
                if (!data.metadata().isEmpty()) {
                    FileUtils.setMCMetaContent(PackUtils.getPack(packName), data.metadata());
                }

                // Clean up
                zipChunksMap.remove(packName);
                currentPackName = null;

                // Reload pack
                CompletableFuture.runAsync(PackUtils::reloadPack);

                if (notification != null) {
                    notification.setProgress(notification.getMaxProgress());
                }

                LogWindow.addInfo("Pack " + packName + " downloaded and extracted successfully!");

            } catch (IOException e) {
                LogWindow.addError("Failed to reassemble and extract pack: " + e.getMessage());
                e.printStackTrace();
                zipChunksMap.remove(packName);
            }
        }
    }

    private static void unzipPack(File zipFile, File targetDir) throws IOException {
        // Clear existing files in target directory (except pack.mcmeta which will be overwritten)
        if (targetDir.exists()) {
            File[] files = targetDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().equals("pack.mcmeta")) {
                        deleteRecursively(file);
                    }
                }
            }
        }
        targetDir.mkdirs();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                File destFile = new File(targetDir, zipEntry.getName());

                if (zipEntry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    // Create parent directories
                    destFile.getParentFile().mkdirs();

                    // Write file
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        file.delete();
    }

    private boolean saveKeyPressed = false;
    private boolean closeKeyPressed = false;
    private boolean reloadKeyPressed = false;
    private boolean enterGameKeyPressed = false;

    private void handleKeypresses() {
        Window window = MinecraftClient.getInstance().getWindow();
        boolean ctrlPressed = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shiftPressed = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

        if (ctrlPressed) {
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_F6)) {
                if (enterGameKeyPressed) {
                    return;
                }
                enterGameKeyPressed = true;
                ImGuiImplementation.enterGameKeyToggled = !ImGuiImplementation.enterGameKeyToggled;
            } else {
                enterGameKeyPressed = false;
            }

            if (PackHelper.isInvalid()) {
                return;
            }

            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_R)) {
                if (reloadKeyPressed) {
                    return;
                }
                reloadKeyPressed = true;
                CompletableFuture.runAsync(PackUtils::reloadPack);
            } else {
                reloadKeyPressed = false;
            }

            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S)) {
                if (saveKeyPressed) {
                    return;
                }
                saveKeyPressed = true;
                if (shiftPressed) {
                    // Handle Ctrl+Shift+S
                    FileUtils.saveAllFiles();
                } else {
                    // Handle Ctrl+S
                    if (ModelEditorWindow.isModelWindowFocused()) {
                        ModelEditorWindow.saveCurrentModel();
                    } else {
                        if (EditorWindow.openFiles.isEmpty() || EditorWindow.currentFile == null) {
                            return;
                        }
                        FileUtils.saveSingleFile(EditorWindow.currentFile.getPath(), EditorWindow.currentFile.getExtension(), FileUtils.getContent(EditorWindow.currentFile), PackHelper.getCurrentPack());
                    }
                }
            } else {
                saveKeyPressed = false;
            }
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W)) {
                if (closeKeyPressed) {
                    return;
                }
                closeKeyPressed = true;
                // Handle Ctrl+W
                if (shiftPressed) {
                    if (ModelEditorWindow.isModelWindowOpen()) {
                        ModelEditorWindow.closeCurrentModel();
                    }
                    if (EditorWindow.openFiles.isEmpty() || EditorWindow.currentFile == null) {
                        return;
                    }
                    for (PackFile file : EditorWindow.openFiles) {
                        if (file.isModified()) {
                            ConfirmWindow.open("Are you sure you want to close all files", "Any unsaved changes might be lost.", () -> {
                                EditorWindow.modifiedFiles += EditorWindow.openFiles.size();
                                EditorWindow.openFiles.clear();
                            });
                            return;
                        }
                    }
                    EditorWindow.modifiedFiles += EditorWindow.openFiles.size();
                    EditorWindow.openFiles.clear();
                } else {
                    if (ModelEditorWindow.isModelWindowFocused()) {
                        ModelEditorWindow.closeCurrentModel();
                        return;
                    }
                    if (EditorWindow.openFiles.isEmpty() || EditorWindow.currentFile == null) {
                        return;
                    }
                    if (EditorWindow.currentFile.isModified()) {
                        ConfirmWindow.open("Are you sure you want to close this file", "Any unsaved changes might be lost.", () -> {
                            EditorWindow.modifiedFiles++;
                            EditorWindow.openFiles.remove(EditorWindow.currentFile);
                            EditorWindow.audioPlayer.stop();
                            EditorWindow.waveform = new float[0];
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
        if (gameMode.equals(GameMode.CREATIVE)) {
            client.player.networkHandler.sendChatCommand("gamemode creative");
        } else if (gameMode.equals(GameMode.SURVIVAL)) {
            client.player.networkHandler.sendChatCommand("gamemode survival");
        } else if (gameMode.equals(GameMode.SPECTATOR)) {
            client.player.networkHandler.sendChatCommand("gamemode spectator");
        } else if (gameMode.equals(GameMode.ADVENTURE)) {
            client.player.networkHandler.sendChatCommand("gamemode adventure");
        } else {
            LOGGER.error("Unknown game mode: {}", gameMode);
        }
    }

    private static void unlockCursor() {
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
