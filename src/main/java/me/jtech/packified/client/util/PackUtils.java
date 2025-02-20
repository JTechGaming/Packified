package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.packets.C2SSendFullPack;
import me.jtech.packified.packets.C2SSyncPackChanges;
import me.jtech.packified.SyncPacketData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
        resourcePackManager.scanPacks();
        Path resourcePacksPath = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        resourcePacks = resourcePackManager.getProfiles().stream()
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
        SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), changedAssets, FileUtils.getMCMetaContent(currentPack), 1, 0);
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

    private static List<List<SyncPacketData.AssetData>> assets = new ArrayList<>();
    private static int currentChunk = 0;
    public static List<List<SyncPacketData.AssetData>> getAllAssets(ResourcePackProfile pack) {
        ResourcePack resourcePack = pack.createResourcePack();
        assets.add(new ArrayList<>());
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
        List<List<SyncPacketData.AssetData>> returnAssets = new ArrayList<>(assets);
        assets.clear();
        currentChunk = 0;
        return returnAssets;
    }

    private static void loadAssets(ResourcePack resourcePack, String prefix) {
        String namespace = "minecraft";
        resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, prefix, (identifier, resourceSupplier) -> {
            try {
                InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, identifier)).get();
                String content;
                if (FileUtils.getFileExtension(identifier.getPath()).equals(".png")) {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image == null) {
                        System.out.println("Failed to read image: " + identifier);
                        throw new IOException("Failed to read image");
                    }
                    content = FileUtils.encodeImageToBase64(image);
                } else if (FileUtils.getFileExtension(identifier.getPath()).equals(".ogg")) {
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8); // TODO fix audio

                } else{
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                // Check if the current data in the chunk is too big
                if (content.length() + assets.get(currentChunk).stream().mapToInt(asset -> asset.assetData().length()).sum() > Packified.MAX_PACKET_SIZE) {
                    currentChunk++;
                    assets.add(new ArrayList<>());
                }
                assets.get(currentChunk).add(new SyncPacketData.AssetData(identifier, FileUtils.getFileExtension(identifier.getPath()), content));
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

    public static void createPack() {
        FileDialog.saveFileDialog(new File("resourcepacks/").getPath(), "New Pack.zip", "zip").thenAccept(pathStr -> {
            if (pathStr != null) {
                Path path = Path.of(pathStr);
                File folder = new File("resourcepacks/" + path.getFileName().toString().replace(".zip", ""));
                folder.mkdirs();
                // make the pack.mcmeta file
                File mcmeta = new File(folder, "pack.mcmeta");
                try {
                    mcmeta.createNewFile();
                    String fileName = path.getFileName().toString().replace(".zip", "");
                    String packMcmetaContent = "{\"pack\": {\"pack_format\": " + getCurrentPackFormat() + ",\"description\": \"" + fileName + "\"}}";
                    Files.write(mcmeta.toPath(), packMcmetaContent.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                File assetsFolder = new File(folder, "assets");
                File mcAssetsRoot = new File(assetsFolder, ".mcassetsroot");
                try {
                    assetsFolder.mkdirs();
                    mcAssetsRoot.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                File minecraftFolder = new File(assetsFolder, "minecraft");
                try {
                    minecraftFolder.mkdirs();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                refresh();
            }
        });
    }

    public static void sendFullPack(ResourcePackProfile pack, UUID player) {
        List<List<SyncPacketData.AssetData>> assets = PackUtils.getAllAssets(pack);
        System.out.println("Split the pack into " + assets.size() + " chunks");
        for (int i = 0; i < assets.size(); i++) {
            SyncPacketData data = new SyncPacketData(pack.getDisplayName().getString(), assets.get(i), FileUtils.getMCMetaContent(pack), assets.size(), i);
            ClientPlayNetworking.send(new C2SSendFullPack(data, player));
        }
    }

    public static int getCurrentPackFormat() {
        return SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES);
    }
}
