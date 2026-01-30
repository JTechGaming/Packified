package me.jtech.packified.client.util;

import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.networking.PacketSender;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.networking.packets.C2SInfoPacket;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.client.networking.packets.C2SSendFullPack;
import me.jtech.packified.client.networking.packets.C2SSyncPackChanges;
import me.jtech.packified.client.windows.LogWindow;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.ResourceReloadLogger;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class PackUtils {
    private static List<ResourcePackProfile> resourcePacks;

    static {
        resourcePacks = MinecraftClient.getInstance().getResourcePackManager().getProfiles().stream().toList();
    }

    public static boolean hasPack() {
        return resourcePacks.size() > 1;
    }

    private static final Executor workerExecutor = Executors.newSingleThreadExecutor();

    public static void reloadPack() {
        PackifiedClient.loading = true;
        MinecraftClient client = MinecraftClient.getInstance();
        PackifiedClient.LOGGER.info("Reloading pack");
        client.getResourcePackManager().scanPacks();
        List<ResourcePack> list = client.resourcePackManager.createResourcePacks();
        client.resourceReloadLogger.reload(ResourceReloadLogger.ReloadReason.UNKNOWN, list);
        client.resourceManager.reload(workerExecutor, client, MinecraftClient.COMPLETED_UNIT_FUTURE, list).whenComplete().thenRun(() -> PackifiedClient.reloaded = true);
        client.resourceReloadLogger.finish();
        client.serverResourcePackLoader.onReloadSuccess();
    }

    public static Path getPackFolder(ResourcePack pack) {
        for (ResourcePackProfile resourcePack : resourcePacks) {
            if (resourcePack.getId().equals(pack.getId())) {
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
                .filter(pack -> {
                    String legalizedName = legalizeName(pack.getDisplayName().getString());
                    try (Stream<Path> paths = Files.walk(resourcePacksPath)) {
                        return paths.anyMatch(path -> path.getFileName().toString().equals(legalizedName));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return true;
                    }
                })
                .toList();

        return resourcePacks;
    }


    public static List<ResourcePackProfile> refreshInternalPacks() {
        ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
        resourcePackManager.scanPacks();
        Path resourcePacksPath = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");

        resourcePacks = resourcePackManager.getProfiles().stream()
                .filter(pack -> !resourcePacksPath.resolve(legalizeName(pack.getDisplayName().getString())).toFile().exists())
                .toList();
        return resourcePacks;
    }

    public static String legalizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9()' ._-]", "_");
    }

    public static void sendPackAsServerPack(ResourcePackProfile currentPack) {
        if (currentPack == null) {
            return;
        }
        List<PackFile> changedAssets = EditorWindow.changedAssets;
        accumulatePacketData(currentPack, changedAssets, data -> {
            PacketSender.queuePacket(new C2SSyncPackChanges(data, List.of(new UUID(0, 0))));
        });
    }

    public static void sendPackChangesToPlayers(ResourcePackProfile currentPack) {
        if (currentPack == null) {
            return;
        }
        List<PackFile> changedAssets = EditorWindow.changedAssets;
        accumulatePacketData(currentPack, changedAssets, data -> {
            PacketSender.queuePacket(new C2SSyncPackChanges(data, PackifiedClient.markedPlayers));
        });
    }

    private static void accumulatePacketData(ResourcePackProfile currentPack, List<PackFile> changedAssets, PacketAction action) {
        List<SyncPacketData.AssetData> assets = new ArrayList<>();
        int predictedPacketAmount = (int) Math.ceil((double) changedAssets.stream().mapToInt(asset -> Math.toIntExact(asset.getPath().toFile().length())).sum() / Packified.MAX_PACKET_SIZE);
        for (int i = 0; i < changedAssets.size(); i++) {
            PackFile asset = changedAssets.get(i);
            String content = FileUtils.getContent(asset);
            Path path = asset.getPath();
            String extension = FileUtils.getFileExtension(path.getFileName().toString());
            boolean finalAsset = i == changedAssets.size() - 1;
            if (content.length() > Packified.MAX_PACKET_SIZE) {
                // split the asset into multiple chunks
                int requiredChunks = (int) Math.ceil((double) content.length() / Packified.MAX_PACKET_SIZE);
                for (int a = 0; a < requiredChunks; a++) {
                    int start = a * Packified.MAX_PACKET_SIZE;
                    int end = Math.min((a + 1) * Packified.MAX_PACKET_SIZE, content.length());
                    String chunk = content.substring(start, end);
                    SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), List.of(new SyncPacketData.AssetData(path, extension, chunk, a == requiredChunks - 1)), FileUtils.getMCMetaContent(currentPack), (a == requiredChunks - 1), false, predictedPacketAmount);
                    action.execute(data);
                }
                continue;
            }
            if (content.length() + assets.stream().mapToInt(cAsset -> cAsset.assetData().length()).sum() > Packified.MAX_PACKET_SIZE) {
                SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), assets, FileUtils.getMCMetaContent(currentPack), true, false, predictedPacketAmount);
                action.execute(data);
                assets.clear();
            }
            assets.add(new SyncPacketData.AssetData(path, extension, content, true));
            if (finalAsset) {
                SyncPacketData data = new SyncPacketData(currentPack.getDisplayName().getString(), assets, FileUtils.getMCMetaContent(currentPack), true, true, predictedPacketAmount);
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
        List<SyncPacketData.AssetData> assets = new CopyOnWriteArrayList<>();

        Path packPath = getPackFolderPath(pack);

        if (packPath == null || !Files.exists(packPath)) {
            System.err.println("Invalid resource pack path: " + packPath);
            return;
        }

        boolean first = true;
        int predictPacketAmount = predictPacketAmount(packPath);

        try (Stream<Path> paths = Files.walk(packPath)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                try (InputStream inputStream = Files.newInputStream(path)) {
                    String content;
                    String extension = FileUtils.getFileExtension(path.getFileName().toString());

                    if (extension.equals(".png")) {
                        BufferedImage image = ImageIO.read(inputStream);
                        if (image == null) {
                            LogWindow.addError("Failed to read image: " + path);
                        }
                        content = FileUtils.encodeImageToBase64(image);
                    } else if (extension.equals(".ogg")) {
                        content = new String(inputStream.readAllBytes());
                    } else {
                        content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    }

                    Path relativePath = packPath.relativize(path); // Get relative path from root (root being the pack folder)

                    if (content.length() >= Packified.MAX_PACKET_SIZE) {
                        // Split the asset into multiple chunks
                        int requiredChunks = (int) Math.ceil((double) content.length() / Packified.MAX_PACKET_SIZE);
                        for (int a = 0; a < requiredChunks; a++) {
                            int start = a * Packified.MAX_PACKET_SIZE;
                            int end = Math.min((a + 1) * Packified.MAX_PACKET_SIZE, content.length());
                            String chunk = content.substring(start, end);
                            String meta = "";
                            if (first) {
                                meta = FileUtils.getMCMetaContent(pack);
                                first = false;
                            }
                            SyncPacketData data = new SyncPacketData(pack.getDisplayName().getString(), List.of(new SyncPacketData.AssetData(relativePath, extension, chunk, a == requiredChunks - 1)), meta, a == requiredChunks - 1, false, predictPacketAmount);
                            sendFullPackPacket(data, player);
                        }
                        continue;
                    }

                    if (content.length() + assets.stream().mapToInt(cAsset -> cAsset.assetData().length()).sum() >= Packified.MAX_PACKET_SIZE) {
                        List<SyncPacketData.AssetData> finalAssets = new ArrayList<>(assets);
                        Collections.copy(finalAssets, assets);
                        String meta = "";
                        if (first) {
                            meta = FileUtils.getMCMetaContent(pack);
                            first = false;
                        }
                        SyncPacketData data = new SyncPacketData(pack.getDisplayName().getString(), finalAssets, meta, true, false, predictPacketAmount);
                        sendFullPackPacket(data, player);
                        assets.clear();
                    }

                    assets.add(new SyncPacketData.AssetData(relativePath, extension, content, true));
                } catch (IOException e) {
                    System.err.println("Error processing file: " + path);
                    e.printStackTrace();
                }
            }

            List<SyncPacketData.AssetData> finalAssets = new ArrayList<>(assets);
            Collections.copy(finalAssets, assets);
            SyncPacketData data = new SyncPacketData(pack.getDisplayName().getString(), finalAssets, FileUtils.getMCMetaContent(pack), true, true, 0);
            sendFullPackPacket(data, player);
            assets.clear();
        } catch (IOException e) {
            LogWindow.addError("Failed to access resource pack files: " + pack.getDisplayName().getString() + e);
        }
    }

    private static int predictPacketAmount(Path packPath) {
        int packetAmount = 0;
        try (Stream<Path> paths = Files.walk(packPath)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                if (content.length() >= Packified.MAX_PACKET_SIZE) {
                    packetAmount += (int) Math.ceil((double) content.length() / Packified.MAX_PACKET_SIZE);
                } else {
                    packetAmount++;
                }
            }
        } catch (IOException e) {
            LogWindow.addWarning("Failed to predict packet amount for resource pack: " + packPath + " - " + e.getMessage());
        }
        return packetAmount;
    }

    private static void sendFullPackPacket(SyncPacketData data, UUID player) {
        PacketSender.queuePacket(new C2SSendFullPack(data, player));
    }

    private static Path getPackFolderPath(ResourcePackProfile packProfile) {
        return FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(packProfile.getDisplayName().getString());
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
                LogWindow.addError("Failed to unzip resource pack: " + packProfile.getDisplayName().getString() + e);
            }
        }
    }

    public static void loadPack(ResourcePackProfile currentPack) {
        ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
        resourcePackManager.enable(currentPack.getId());

        CompletableFuture.runAsync(PackUtils::reloadPack);
        if (PackHelper.isValid() && MinecraftClient.getInstance().player != null) {
            ClientPlayNetworking.send(new C2SInfoPacket(PackHelper.getCurrentPack().getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
        }
    }

    public static void unloadPack(ResourcePackProfile currentPack) {
        // unloads the pack from the game so that the packs files can be modified
        ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
        resourcePackManager.disable(currentPack.getId());
    }

    public static void createPack(String packName, String description, int packVersionIndex, int packVersionEndIndex) {
        File folder = new File("resourcepacks/" + packName);
        folder.mkdirs();
        // make the pack.mcmeta file
        File mcmeta = new File(folder, "pack.mcmeta");
        try {
            mcmeta.createNewFile();
            String packVersion = getAllPackVersions(packVersionIndex, packVersionEndIndex);
            String packMcmetaContent = "{\"pack\": {\"pack_format\": " + packVersion + "\"description\": \"" + description + "\"}}";
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

        List<ResourcePackProfile> refresh = refresh();
        for (ResourcePackProfile resourcePack : refresh) {
            if (resourcePack.getDisplayName().getString().equalsIgnoreCase(packName)) {
                PackHelper.updateCurrentPack(resourcePack);
                return;
            }
        }
    }

    private static String getAllPackVersions(int startIndex, int endIndex) {
        String output = endIndex + ",";
        if (startIndex == endIndex) {
            return output;
        }
        output += "\"supported_versions\": [" + startIndex + ", " + endIndex + "],";
        return output;
    }

    public static void exportPack() {
        String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified/exports").toString();
        File folderFile = Path.of(defaultFolder).toFile();
        folderFile.mkdirs();
        FileDialog.saveFileDialog(defaultFolder, PackHelper.getCurrentPack().getDisplayName().getString() + ".zip", "json", "png").thenAccept(pathStr -> {
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

    @FunctionalInterface
    public interface PacketAction {
        void execute(SyncPacketData data);
    }
}
