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
import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PackUtils {
    private static List<ResourcePackProfile> resourcePacks;

    static {
        resourcePacks = MinecraftClient.getInstance().getResourcePackManager().getProfiles().stream().toList();
    }

    public static boolean hasPack() {
        return resourcePacks.size() > 1;
    }

    public static void reloadPack() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getResourcePackManager().scanPacks();
        List<ResourcePack> list = client.resourcePackManager.createResourcePacks();
        client.resourceReloadLogger.reload(ResourceReloadLogger.ReloadReason.UNKNOWN, list);
        client.resourceManager.reload(Util.getMainWorkerExecutor(), client, MinecraftClient.COMPLETED_UNIT_FUTURE, list);
        client.worldRenderer.reload();
        client.resourceReloadLogger.finish();
        client.serverResourcePackLoader.onReloadSuccess();
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

    public static void sendPackAsServerPack(ResourcePackProfile currentPack) {
        if (currentPack == null) {
            return;
        }
        List<PackFile> changedAssets = EditorWindow.changedAssets;
        accumulatePacketData(currentPack, changedAssets, data -> {
            ClientPlayNetworking.send(new C2SSyncPackChanges(data, List.of(new UUID(0, 0))));
        });
    }

    public static void sendPackChangesToPlayers(ResourcePackProfile currentPack) {
        if (currentPack == null) {
            return;
        }
        List<PackFile> changedAssets = EditorWindow.changedAssets;
        accumulatePacketData(currentPack, changedAssets, data -> {
            ClientPlayNetworking.send(new C2SSyncPackChanges(data, PackifiedClient.markedPlayers));
        });
    }

    private static void accumulatePacketData(ResourcePackProfile currentPack, List<PackFile> changedAssets, PacketAction action) {
        List<SyncPacketData.AssetData> assets = new ArrayList<>();
        for (int i = 0; i < changedAssets.size(); i++) {
            PackFile asset = changedAssets.get(i);
            String content = FileUtils.getContent(asset);
            Identifier identifier = asset.getIdentifier();
            String extension = FileUtils.getFileExtension(identifier.getPath());
            boolean finalAsset = i == changedAssets.size() - 1;
            if (content.length() + assets.stream().mapToInt(cAsset -> cAsset.assetData().length()).sum() > Packified.MAX_PACKET_SIZE) {
                SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), assets, FileUtils.getMCMetaContent(currentPack), finalAsset);
                action.execute(data);
                assets.clear();
            }
            if (content.length() > Packified.MAX_PACKET_SIZE) {
                // split the asset into multiple chunks
                int requiredChunks = (int) Math.ceil((double) content.length() / Packified.MAX_PACKET_SIZE);
                for (int a = 0; a < requiredChunks; a++) {
                    int start = a * Packified.MAX_PACKET_SIZE;
                    int end = Math.min((a + 1) * Packified.MAX_PACKET_SIZE, content.length());
                    String chunk = content.substring(start, end);
                    SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), List.of(new SyncPacketData.AssetData(identifier, extension, chunk, a == requiredChunks - 1)), FileUtils.getMCMetaContent(currentPack), (a == requiredChunks - 1) && finalAsset);
                    action.execute(data);
                }
                continue;
            }
            assets.add(new SyncPacketData.AssetData(identifier, extension, content, true));
            if (finalAsset) {
                SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), assets, FileUtils.getMCMetaContent(currentPack), true);
                action.execute(data);
                assets.clear();
            }
        }
    }

    public static ResourcePackProfile getPack(String packName) {
        for (ResourcePackProfile resourcePack : refresh()) {
            if (resourcePack.getDisplayName().getString().equals(packName)) {
                return resourcePack;
            }
        }
        return null;
    }

    public static void sendFullPack(ResourcePackProfile pack, UUID player) {
        List<SyncPacketData.AssetData> assets = new ArrayList<>();

        loadAssets(pack, "atlases", player, assets);
        loadAssets(pack, "blockstates", player, assets);
        loadAssets(pack, "font", player, assets);
        loadAssets(pack, "lang", player, assets);
        loadAssets(pack, "models", player, assets);
        loadAssets(pack, "particles", player, assets);
        loadAssets(pack, "shaders", player, assets);
        loadAssets(pack, "sounds", player, assets);
        loadAssets(pack, "texts", player, assets);
        loadAssets(pack, "textures", player, assets);
        SyncPacketData data = new SyncPacketData(pack.getDisplayName().getString(), assets, FileUtils.getMCMetaContent(pack), true);
        ClientPlayNetworking.send(new C2SSendFullPack(data, player));
        assets.clear();
    }

    private static void loadAssets(ResourcePackProfile currentPack, String prefix, UUID player, List<SyncPacketData.AssetData> assets) {
        String namespace = "minecraft";
        ResourcePack resourcePack = currentPack.createResourcePack();
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

                } else {
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }

                String extension = FileUtils.getFileExtension(identifier.getPath());
                if (content.length() + assets.stream().mapToInt(cAsset -> cAsset.assetData().length()).sum() > Packified.MAX_PACKET_SIZE) {
                    SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), assets, FileUtils.getMCMetaContent(currentPack), false);
                    ClientPlayNetworking.send(new C2SSendFullPack(data, player));
                    assets.clear();
                }
                if (content.length() > Packified.MAX_PACKET_SIZE) {
                    // split the asset into multiple chunks
                    int requiredChunks = (int) Math.ceil((double) content.length() / Packified.MAX_PACKET_SIZE);
                    for (int a = 0; a < requiredChunks; a++) {
                        int start = a * Packified.MAX_PACKET_SIZE;
                        int end = Math.min((a + 1) * Packified.MAX_PACKET_SIZE, content.length());
                        String chunk = content.substring(start, end);
                        SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), List.of(new SyncPacketData.AssetData(identifier, extension, chunk, a == requiredChunks - 1)), FileUtils.getMCMetaContent(currentPack), false);
                        ClientPlayNetworking.send(new C2SSendFullPack(data, player));
                    }
                    return;
                }
                assets.add(new SyncPacketData.AssetData(identifier, extension, content, true));
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

    public static int getCurrentPackFormat() {
        return SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES);
    }

    @FunctionalInterface
    public interface PacketAction {
        void execute(SyncPacketData data);
    }
}
