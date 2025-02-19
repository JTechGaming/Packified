package me.jtech.packified.client;

import me.jtech.packified.Packified;
import me.jtech.packified.SyncPacketData;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.packets.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class PackifiedClient implements ClientModInitializer {
    public static final String MOD_ID = Packified.MOD_ID;
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String version = Packified.version;

    boolean shouldRender = false;
    private static KeyBinding keyBinding;

    public static ResourcePackProfile currentPack;

    AtomicBoolean tracker = new AtomicBoolean(false);

    public static List<UUID> markedPlayers = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imlib.spook", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_F8, // The keycode of the key
                "category.imlib.test" // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.wasPressed()) {
                toggleVisibility();
            }
        });
        // Prevent Minecraft from locking the cursor when clicking
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (shouldRender) {
                KeyBinding.unpressAll();
                unlockCursor();
                if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_F8)) {
                    if (tracker.get()) {
                        toggleVisibility();
                    }
                } else {
                    tracker.set(true);
                }
                handleSaveKeyPress();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CSyncPackChanges.ID, (payload, context) -> {
            // Logic to apply the pack changes
            ResourcePackProfile pack = PackUtils.getPack(payload.packetData().getPackName());
            if (pack == null) {
                ClientPlayNetworking.send(new C2SRequestFullPack(payload.packetData().getPackName(), payload.player()));
                return;
            }
            ResourcePackProfile oldPack = currentPack;
            currentPack = pack;
            List<SyncPacketData.AssetData> assets = payload.packetData().getAssets();
            for (SyncPacketData.AssetData asset : assets) {
                FileUtils.saveFile(asset.getIdentifier(), asset.getExtension(), asset.getAssetData());
            }
            FileUtils.setMCMetaContent(pack, payload.packetData().getMetadata());

            // Reload the pack
            PackUtils.reloadPack();

            currentPack = oldPack;
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CSendFullPack.ID, (payload, context) -> {
            // Logic to fully download, add and apply the pack
            SyncPacketData data = payload.packetData();
            String packName = data.getPackName();
            List<SyncPacketData.AssetData> assets = data.getAssets();

            // Create the pack
            FileUtils.createPack(packName, assets, data.getMetadata());
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
            List<SyncPacketData.AssetData> allAssets = PackUtils.getAllAssets(pack);
            SyncPacketData data = new SyncPacketData(pack.getDisplayName().getString(), allAssets, FileUtils.getMCMetaContent(pack));
            ClientPlayNetworking.send(new C2SSendFullPack(data, payload.player()));
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

    private void handleSaveKeyPress() {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrlPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shiftPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean sPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_S);

        if (ctrlPressed && sPressed) {
            if (shiftPressed) {
                // Handle Ctrl+Shift+S
                LOGGER.info("Ctrl+Shift+S pressed");
            } else {
                // Handle Ctrl+S
                LOGGER.info("Ctrl+S pressed");
            }
        }
    }

    private void toggleVisibility() {
        tracker.set(false);

        shouldRender = !shouldRender;

        if (shouldRender) {
            ImGuiImplementation.aspectRatio = (float) MinecraftClient.getInstance().getWindow().getWidth() / MinecraftClient.getInstance().getWindow().getHeight();
            unlockCursor();
        } else {
            lockCursor();
        }

        MinecraftClient.getInstance().interactionManager.setGameMode(shouldRender ? GameMode.SPECTATOR : GameMode.CREATIVE);
        ImGuiImplementation.shouldRender = shouldRender;
    }

    private static void unlockCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        client.mouse.unlockCursor();
    }

    private static void lockCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        client.mouse.lockCursor();
    }
}
