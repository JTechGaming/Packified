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
import java.io.FileOutputStream;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        ResourcePackManager resourcePackManager =
                MinecraftClient.getInstance().getResourcePackManager();

        resourcePackManager.scanPacks();

        Path resourcePacksPath =
                FabricLoader.getInstance().getGameDir().resolve("resourcepacks");

        Set<String> existingPackNames;
        try (Stream<Path> paths = Files.list(resourcePacksPath)) {
            existingPackNames = paths
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            e.printStackTrace();
            // keep all profiles if filesystem read fails
            resourcePacks = List.copyOf(resourcePackManager.getProfiles());
            return resourcePacks;
        }

        resourcePacks = resourcePackManager.getProfiles().stream()
                .filter(pack -> {
                    String legalizedName =
                            legalizeName(pack.getDisplayName().getString());
                    return existingPackNames.contains(legalizedName);
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
//        accumulatePacketData(currentPack, changedAssets, data -> {
//            PacketSender.queuePacket(new C2SSyncPackChanges(data, List.of(new UUID(0, 0))));
//        });
    }

    public static void sendPackChangesToPlayers(ResourcePackProfile currentPack) {
        if (currentPack == null) {
            return;
        }
        List<PackFile> changedAssets = EditorWindow.changedAssets;
//        accumulatePacketData(currentPack, changedAssets, data -> {
//            PacketSender.queuePacket(new C2SSyncPackChanges(data, PackifiedClient.markedPlayers));
//        });
    }

    public static void sendFullPackZipped(ResourcePackProfile pack, UUID player) {
        Path packPath = getPackFolderPath(pack);

        if (packPath == null || !Files.exists(packPath)) {
            LogWindow.addError("Invalid resource pack path: " + packPath);
            return;
        }

        try {
            // Create a temporary zip file
            Path tempZipPath = Files.createTempFile("pack_transfer_", ".zip");

            // Zip the entire pack folder
            try (FileOutputStream fos = new FileOutputStream(tempZipPath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                // Set compression level for better speed/size balance
                zos.setLevel(Deflater.BEST_SPEED);

                Files.walk(packPath)
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Path relativePath = packPath.relativize(path);
                                ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace("\\", "/"));
                                zos.putNextEntry(zipEntry);
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                LogWindow.addError("Failed to add file to zip: " + path + " - " + e.getMessage());
                            }
                        });
            }

            // Read the zip file as bytes
            byte[] zipData = Files.readAllBytes(tempZipPath);

            // Delete temp file
            Files.delete(tempZipPath);

            // Calculate chunks needed
            int totalChunks = (int) Math.ceil((double) zipData.length / Packified.MAX_PACKET_SIZE);

            LogWindow.addInfo("Sending pack as zip: " + pack.getDisplayName().getString() +
                    " (" + formatFileSize(zipData.length) + " in " + totalChunks + " chunks)");

            // Send chunks
            for (int i = 0; i < totalChunks; i++) {
                int start = i * Packified.MAX_PACKET_SIZE;
                int end = Math.min((i + 1) * Packified.MAX_PACKET_SIZE, zipData.length);

                byte[] chunk = new byte[end - start];
                System.arraycopy(zipData, start, chunk, 0, end - start);

                String metadata = (i == 0) ? FileUtils.getMCMetaContent(pack) : "";

                SyncPacketData data = new SyncPacketData(
                        pack.getDisplayName().getString(),
                        chunk,
                        i,
                        totalChunks,
                        i == totalChunks - 1,
                        metadata
                );

                sendFullPackPacket(data, player);
            }

            LogWindow.addInfo("Pack zip sent successfully: " + totalChunks + " chunks");

        } catch (IOException e) {
            LogWindow.addError("Failed to zip and send pack: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String formatFileSize(int size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
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

        CompletableFuture
                .runAsync(PackUtils::reloadPack)
                .thenRun(() ->
                        MinecraftClient.getInstance().execute(() -> {
                                    if (PackHelper.isValid() && MinecraftClient.getInstance().player != null) {
                                        ClientPlayNetworking.send(new C2SInfoPacket(PackHelper.getCurrentPack().getDisplayName().getString(), MinecraftClient.getInstance().player.getUuid()));
                                    }
                                }
                        )
                );
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
        exportPackWith(List.of());
    }

    public static void exportPackWith(List<String> enabledPaths) {
        String defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("packified/exports").toString();
        File folderFile = Path.of(defaultFolder).toFile();
        folderFile.mkdirs();
        FileDialog.saveFileDialog(defaultFolder, PackHelper.getCurrentPack().getDisplayName().getString() + ".zip", "").thenAccept(pathStr -> {
            if (pathStr != null) {
                Path path = Path.of(pathStr);
                Path folderPath = path.getParent();
                MinecraftClient.getInstance().submit(() -> {
                    try {
                        FileUtils.zip(folderPath.toFile(), path.getFileName().toString(), enabledPaths);
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
