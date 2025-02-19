package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.packets.C2SSyncPackChanges;
import me.jtech.packified.SyncPacketData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PackUtils {
    private static List<ResourcePackProfile> resourcePacks;

    static {
        resourcePacks = MinecraftClient.getInstance().getResourcePackManager().getProfiles().stream().toList();
    }

    public static boolean hasPack() {
        return resourcePacks.size() > 1;
    }

    public static void reloadPack() {
        MinecraftClient.getInstance().reloadResources();
    }

    public static Path getPackFolder(ResourcePack pack) {
        for (ResourcePackProfile resourcePack : resourcePacks) {
            if (resourcePack.getId().equals(pack.getId())) {
                System.out.println(resourcePack.getId());
                return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(resourcePack.getDisplayName().getString());
            }
        }
        return null;
    }

    public static List<ResourcePackProfile> refresh() {
        Path resourcePacksPath = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        resourcePacks = MinecraftClient.getInstance().getResourcePackManager().getProfiles().stream()
                .filter(pack -> resourcePacksPath.resolve(legalizeName(pack.getDisplayName().getString())).toFile().exists() || pack.getDisplayName().getString().equalsIgnoreCase("Default"))
                .toList();
        return resourcePacks;
    }

    private static String legalizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static void sendPackChangesToPlayers(ResourcePackProfile currentPack) {
        if (currentPack == null) {
            return;
        }
        List<SyncPacketData.AssetData> changedAssets = new ArrayList<>();
        for (PackFile asset : EditorWindow.changedAssets) { //TODO changedassets might be reset at this point
            changedAssets.add(new SyncPacketData.AssetData(asset.getIdentifier(), FileUtils.getFileExtension(asset.getFileName()), FileUtils.getContent(asset)));
        }
        SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), changedAssets, FileUtils.getMCMetaContent(currentPack));
        Packified.LOGGER.info(data.toString());
        ClientPlayNetworking.send(new C2SSyncPackChanges(data, PackifiedClient.markedPlayers));
    }

    public static ResourcePackProfile getPack(String packName) {
        for (ResourcePackProfile resourcePack : refresh()) {
            if (resourcePack.getDisplayName().getString().equals(packName)) {
                return resourcePack;
            }
        }
        return null;
    }

    private static List<SyncPacketData.AssetData> assets = new ArrayList<>();

    public static List<SyncPacketData.AssetData> getAllAssets(ResourcePackProfile pack) {
        ResourcePack resourcePack = pack.createResourcePack();
        loadAssets(resourcePack, "atlases");
        loadAssets(resourcePack, "blockstates");
        loadAssets(resourcePack, "font");
        loadAssets(resourcePack, "lang");
        loadAssets(resourcePack, "models");
        loadAssets(resourcePack, "particles");
        loadAssets(resourcePack, "shaders");
        loadAssets(resourcePack, "sounds");
        loadAssets(resourcePack, "texts");
        loadAssets(resourcePack, "textures");
        List<SyncPacketData.AssetData> returnAssets = new ArrayList<>(assets);
        assets.clear();
        return returnAssets;
    }

    private static void loadAssets(ResourcePack resourcePack, String prefix) {
        String namespace = "minecraft";
        resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, prefix, (identifier, resourceSupplier) -> {
            try {
                InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, identifier)).get();
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                assets.add(new SyncPacketData.AssetData(identifier, FileUtils.getFileExtension(identifier.getPath()), content));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void checkPackType(ResourcePackProfile packProfile) {
        File resourcePackFolder = new File("resourcepacks/" + packProfile.getDisplayName().getString());
        if (FileUtils.isZipFile(resourcePackFolder)) {
            ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
            resourcePackManager.disable(packProfile.getId());
            File tempDir = new File(resourcePackFolder.getParent(), resourcePackFolder.getName().replace(".zip", ""));
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            try {
                FileUtils.unzipPack(resourcePackFolder, tempDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void loadPack(ResourcePackProfile currentPack) {
        ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
        resourcePackManager.enable(currentPack.getId());
    }

    public static void unloadPack(ResourcePackProfile currentPack) {
        // unloads the pack from the game so that the packs files can be modified
        ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
        resourcePackManager.disable(currentPack.getId());
    }
}
