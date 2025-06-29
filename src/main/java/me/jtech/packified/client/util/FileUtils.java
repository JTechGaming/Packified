package me.jtech.packified.client.util;

import com.google.gson.JsonObject;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.windows.*;
import me.jtech.packified.SyncPacketData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class FileUtils {
    public static String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return fileName.substring(lastIndexOf);
    }

    public static String getFileExtensionName(String fileName, String[] supportedExtensions) {
        String extension = getFileExtension(fileName);
        for (String supportedExtension : supportedExtensions) {
            if (extension.equals(supportedExtension)) {
                return supportedExtension;
            }
        }
        return null;
    }

    public static String getExtension(Path path) {
        return getFileExtension(path.getFileName().toString());
    }

    public static String getFileExtensionName(String fileName) {
        return getFileExtensionName(fileName, FileHierarchy.extensions);
    }

    public static boolean isSupportedFileExtension(String fileName, String[] supportedExtensions) {
        String extension = getFileExtension(fileName);
        for (String supportedExtension : supportedExtensions) {
            if (extension.equals(supportedExtension)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSupportedFileExtension(String fileName) {
        return isSupportedFileExtension(fileName, FileHierarchy.extensions);
    }

    public static void deleteFile(Path path) {
        // delete file
        EditorWindow.openFiles.removeIf(file -> file.getPath().equals(path));
        File file = path.toFile();
        System.out.println(file.getPath());
        if (file.exists()) {
            if (file.canWrite()) {
                if (file.isDirectory()) {
                    // Delete folder
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            deleteFile(f.toPath());
                        }
                    }
                }
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void importFile(Path path) {
        // import file
        File file = path.toFile();
        if (file.exists()) {
            if (file.isDirectory()) {
                // Import folder
                String folderName = file.getName();
                File[] files = file.listFiles();
                if (files != null) {
                    List<Path> fileList = Arrays.stream(files).map(File::toPath).toList();
                    SelectFolderWindow.open(folderName, fileList);
                }
            } else {
                // Import file
                String fileName = file.getName();
                String extension = getFileExtensionName(fileName);
                if (extension == null) {
                    System.err.println("Unsupported file extension: " + fileName);
                    return;
                }
                String content = null;
                BufferedImage image;
                if (extension.equals(".png")) {
                    System.out.println("Importing image: " + fileName);
                    try {
                        image = ImageIO.read(file);
                        if (image != null) {
                            SelectFolderWindow.open(fileName, extension, encodeImageToBase64(image));
                        } else {
                            System.err.println("Failed to load image: " + fileName);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        content = Files.readString(file.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (content == null) {
                        System.err.println("Failed to read file: " + file.getAbsolutePath());
                        return;
                    }
                    SelectFolderWindow.open(fileName, extension, content);
                }
            }
        } else {
            System.err.println("File not found: " + file.getAbsolutePath());
        }
    }

    public static void openFile(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            System.err.println("File not found: " + filePath);
            return;
        }

        String extension = getFileExtension(filePath.getFileName().toString());

        if (extension == null) {
            System.err.println("Unsupported Filetype: " + filePath);
            return;
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            String content = "";
            switch (extension) {
                case ".json", ".txt", ".mcmeta", ".properties", ".vsh", ".fsh", ".bbmodel", ".bbmodel.json" -> {
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(filePath, content);
                }
                case ".png" -> {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image != null) {
                        EditorWindow.openImageFile(filePath, image);
                    } else {
                        System.err.println("Failed to load image: " + filePath);
                    }
                }
                case ".ogg" -> {
                    byte[] audioData = Files.readAllBytes(filePath);
                    EditorWindow.openAudioFile(filePath, audioData);
                }
                default -> System.err.println("Unsupported file type: " + extension);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveAllFiles() {
        if (PackifiedClient.currentPack == null) {
            System.err.println("No pack selected");
            return;
        }
        File resourcePackFolder = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString());
        makePackBackup(resourcePackFolder);
        for (PackFile file : EditorWindow.openFiles) {
            System.out.println(file.getFileName());
            saveFile(file.getPath(), file.getExtension(), getContent(file));
        }
    }

    public static void saveSingleFile(Path path, String fileType, String content, ResourcePackProfile pack) {
        // Before saving, make a backup of the file
        File resourcePackFolder = new File("resourcepacks/" + pack.getDisplayName().getString());
        if (!resourcePackFolder.exists()) {
            try {
                Files.createDirectories(resourcePackFolder.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        makePackBackup(resourcePackFolder);
        saveFile(path, fileType, content);
    }

    private static void saveFile(Path path, String fileType, String content) {
        try {
            // Get the resource pack folder path
            Path resourcePackFolder = getPackFolderPath();

            if (resourcePackFolder == null || !Files.exists(resourcePackFolder)) {
                System.err.println("Resource pack folder not found: " + resourcePackFolder);
                return;
            }

            // Check if the pack is a ZIP file
            if (isZipFile(resourcePackFolder.toFile())) {
                return;
            }

            // Determine the actual file path inside the pack
            Path targetFile = resourcePackFolder.resolve(path);

            // Ensure parent directories exist
            Files.createDirectories(targetFile.getParent());

            if (!Files.exists(targetFile)) {
                Files.createFile(targetFile);
            }

            // Handle different file types
            if (fileType.equals(".json")) {
                Files.write(targetFile, content.getBytes(StandardCharsets.UTF_8));
            } else if (fileType.equals(".png")) {
                BufferedImage image = FileUtils.decodeBase64ToImage(content);
                if (image != null) {
                    ImageIO.write(image, "png", targetFile.toFile());
                } else {
                    System.err.println("No image found for: " + path);
                }
            }

            // Update the file in the editor if it's open
            for (PackFile file : EditorWindow.openFiles) {
                if (file.getPath().equals(path)) {
                    file.saveFile();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Path getPackFolderPath() {
        if (PackifiedClient.currentPack == null) return null;
        return FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(PackifiedClient.currentPack.getDisplayName().getString());
    }

    public static void saveFolder(Path path) {
        // Save folder
        File folder = path.toFile();
        if (folder.isDirectory()) {
            if (!folder.exists()) {
                // Save folder
                folder.mkdirs();
            }
        }
    }

    public static boolean isZipFile(File file) {
        return file.isFile() && file.getName().endsWith(".zip");
    }

    public static void unzipPack(File zipFile, File targetDir) throws IOException {
        try (FileSystem zipFileSystem = FileSystems.newFileSystem(zipFile.toPath(), Map.of("create", "true"))) {
            Path root = zipFileSystem.getPath("/");
            Files.walk(root).forEach(path -> {
                try {
                    Path destPath = targetDir.toPath().resolve(root.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.copy(path, destPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        PackUtils.refresh();
        PackifiedClient.currentPack = PackUtils.getPack(targetDir.getName().replace(".zip", ""));
        ResourcePackManager resourcePackManager = MinecraftClient.getInstance().getResourcePackManager();
        resourcePackManager.enable(PackifiedClient.currentPack.getId());
    }

    public static void zip(File targetDir, String fileName) throws IOException {
        // zips the resource pack
        File zipFile = new File(targetDir, fileName.replace(".zip", "") + ".zip");
        File folder = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString());

        // get everything in the folder and write it to the zip file
        try (FileSystem zipFileSystem = FileSystems.newFileSystem(URI.create("jar:" + zipFile.toURI()), Map.of("create", "true"))) {
            Path root = zipFileSystem.getPath("/");
            Files.walk(folder.toPath()).forEach(path -> {
                try {
                    Path destPath = root.resolve(folder.toPath().relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.copy(path, destPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public static void removeZipFileFromOptions(File zipFile) {
        if (zipFile.exists()) {
            if (zipFile.canWrite()) {
                try {
                    Files.delete(zipFile.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                zipFile.deleteOnExit();
            }
        }
    }

    public static void makePackBackup(File resourcePackFolder) {
        try {
            Path backupDir = FabricLoader.getInstance().getConfigDir().resolve("packified-backups");

            int maxBackupCount = PreferencesWindow.maxBackupCount.get(); // Maximum number of backups to keep

            // Clean up old backups if they exceed the maximum count
            if (maxBackupCount > 0) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir)) {
                    List<Path> backups = new ArrayList<>();
                    for (Path entry : stream) {
                        backups.add(entry);
                    }
                    if (backups.size() > maxBackupCount) {
                        // Sort by last modified time and delete the oldest ones
                        backups.sort(Comparator.comparingLong(path -> {
                            try {
                                return Files.getLastModifiedTime(path).toMillis();
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to retrieve last modified time for backup", e);
                            }
                        }));
                        for (int i = 0; i < backups.size() - maxBackupCount; i++) {
                            Files.delete(backups.get(i));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (!backupDir.toFile().exists()) {
                Files.createDirectories(backupDir);
            }

            String baseName = resourcePackFolder.getName(); // get the folder name
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date()); // get the current date
            String folderNamePattern = baseName + "_" + date + "_"; // create the backup folder name pattern for the current date
            int highestId = 0;
            // Find the highest backup ID
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir)) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString();
                    if (fileName.startsWith(folderNamePattern)) {
                        String idString = fileName.replace(folderNamePattern, "").replace(".zip", "");
                        try {
                            int id = Integer.parseInt(idString);
                            if (id > highestId) {
                                highestId = id;
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            String backupFileName = folderNamePattern + (highestId + 1) + ".zip";
            Path backupFilePath = backupDir.resolve(backupFileName);

            try (FileSystem zipFileSystem = FileSystems.newFileSystem(URI.create("jar:" + backupFilePath.toUri()), Map.of("create", "true"))) {
                Path root = zipFileSystem.getPath("/");
                Files.walk(resourcePackFolder.toPath()).forEach(path -> {
                    try {
                        Path destPath = root.resolve(resourcePackFolder.toPath().relativize(path).toString());
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destPath);
                        } else {
                            Files.copy(path, destPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadIdentifierPackAssets(ResourcePackProfile resourcePackProfile) {
        ResourcePack resourcePack = resourcePackProfile.createResourcePack();
        for (String namespace : resourcePack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
            FabricLoader.getInstance().getGameDir().resolve("resourcepacks")
                    .resolve(PackUtils.legalizeName(resourcePackProfile.getDisplayName().getString()))
                    .resolve(namespace).toFile().mkdirs();
            resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "", (identifier, resourceSupplier) ->
                    createFileFromIdentifier(resourcePackProfile, namespace, "", identifier)
            );
        }

        constructPackMetaData(resourcePackProfile);
    }

    private static void constructPackMetaData(ResourcePackProfile packProfile) {
        Path packMcMetaPath = FabricLoader.getInstance().getGameDir().resolve("resourcepacks")
                .resolve(PackUtils.legalizeName(packProfile.getDisplayName().getString()))
                .resolve("pack.mcmeta");
        Path packPngPath = FabricLoader.getInstance().getGameDir().resolve("resourcepacks")
                .resolve(PackUtils.legalizeName(packProfile.getDisplayName().getString()))
                .resolve("pack.png");
        packMcMetaPath.getParent().toFile().mkdirs();

        String content = "{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": " + SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES) + ",\n" +
                "    \"description\": \"" + packProfile.getDescription().getString() + "\"\n" +
                "  }\n" +
                "}";

        BufferedImage image = ImGuiImplementation.bufferedImageFromIdentifier(Identifier.of(packProfile.getId(), "icon.png"));

        if (!Files.exists(packMcMetaPath)) {
            try {
                Files.createFile(packMcMetaPath);
                Files.writeString(packMcMetaPath, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create pack.mcmeta file", e);
            }
        }

        if (!Files.exists(packPngPath) && image != null) {
            try {
                Files.createFile(packPngPath);
                ImageIO.write(image, "png", packPngPath.toFile());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create pack.png file", e);
            }
        }
    }

    private static void createFileFromIdentifier(ResourcePackProfile packProfile, String namespace, String prefix, Identifier identifier) {
        ResourcePack resourcePack = packProfile.createResourcePack();
        try {
            LogWindow.addDebugInfo("Creating file for identifier: " + identifier + " in namespace: " + namespace + " with prefix: " + prefix);
            InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, identifier)).get();
            Path targetPath = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PackUtils.legalizeName(packProfile.getDisplayName().getString()))
                    .resolve(namespace).resolve(prefix)
                    .resolve(identifier.getPath());
            targetPath.toFile().mkdirs();
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void clearBackups() {
        // Clear backups
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("packified-backups");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                Files.delete(entry);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getFileSize(Path path) {
        // Get the asset content from the PackifiedClient.currentPack
        if (path == null) {
            return 0;
        }
        File file = path.toFile();
        if (file.exists()) {
            return (int) file.length();
        }
        return 0;
    }

    public static String formatFileSize(int size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return size / 1024 + " KB";
        } else {
            return size / (1024 * 1024) + " MB";
        }
    }

    public static String formatExtension(String extension) {
        return switch (extension) {
            case ".json" -> "JSON";
            case ".png" -> "Image";
            case ".ogg" -> "Sound";
            case ".mcmeta" -> "Meta";
            case ".txt" -> "Text";
            case ".properties" -> "Properties";
            case ".vsh" -> "Vertex Shader";
            case ".fsh" -> "Fragment Shader";
            case ".bbmodel" -> "Blockbench Model";
            case ".bbmodel.json" -> "Blockbench Model JSON";
            default -> "Unknown";
        };
    }


    public static String getContent(PackFile file) {
        String extension = file.getExtension();

        if (extension.equals(".json") || extension.equals(".txt") || extension.equals(".mcmeta")) {
            return file.getTextEditor().getText();
        } else if (extension.equals(".png")) {
            return encodeImageToBase64(file.getImageEditorContent());
        } else if (extension.equals(".ogg")) {
            return ""; // TODO: Handle sound content if needed
        } else {
            Packified.LOGGER.error("Unsupported file type: {}", extension);
            return "";
        }
    }

    public static String encodeImageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static BufferedImage decodeBase64ToImage(String content) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(content);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
            return ImageIO.read(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String encodeSoundToString(byte[] audio) {
        if (audio == null || audio.length == 0) {
            System.err.println("Error: Audio data is empty or null.");
            return "";
        }
        return Base64.getEncoder().encodeToString(audio);
    }

    public static String getMCMetaContent(ResourcePackProfile pack) {
        try {
            File resourcePackFolder = new File("resourcepacks/" + PackUtils.legalizeName(pack.getDisplayName().getString()));
            File targetFile = new File(resourcePackFolder, "pack.mcmeta");
            return Files.readString(targetFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setMCMetaContent(ResourcePackProfile pack, String content) {
        try {
            File resourcePackFolder = new File("resourcepacks/" + PackUtils.legalizeName(pack.getDisplayName().getString()));
            File targetFile = new File(resourcePackFolder, "pack.mcmeta");
            Files.write(targetFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            CompletableFuture.runAsync(PackUtils::reloadPack);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createPack(String packName, List<SyncPacketData.AssetData> assets, String metadata) {
        System.out.println("Creating pack: " + packName);
        // Create the resource pack folder
        File resourcePackFolder = new File("resourcepacks/" + packName);
        if (!resourcePackFolder.exists()) {
            resourcePackFolder.mkdirs();
        }

        // Create the assets folder
        File assetsFolder = new File(resourcePackFolder, "assets");
        if (!assetsFolder.exists()) {
            assetsFolder.mkdirs();
        }

        // Create the minecraft folder
        File minecraftFolder = new File(assetsFolder, "minecraft");
        if (!minecraftFolder.exists()) {
            minecraftFolder.mkdirs();
        }

        // Create the pack.mcmeta file
        File mcmetaFile = new File(resourcePackFolder, "pack.mcmeta");
        try {
            Files.write(mcmetaFile.toPath(), metadata.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create the assets
        for (SyncPacketData.AssetData asset : assets) {
            File targetFile = asset.path().toFile();
            targetFile.getParentFile().mkdirs();
            //Files.write(targetFile.toPath(), asset.assetData().getBytes(StandardCharsets.UTF_8));
            if (asset.extension().equals(".json")) {
                // Save JSON file
                try {
                    Files.write(targetFile.toPath(), asset.assetData().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (asset.extension().equals(".png")) {
                // Save PNG image
                BufferedImage image = FileUtils.decodeBase64ToImage(asset.assetData());
                if (image != null) {
                    try {
                        ImageIO.write(image, "png", targetFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    LogWindow.addError("Failed to decode image for: " + asset.path());
                }
            }
        }

        List<ResourcePackProfile> profiles = PackUtils.refresh();
        for (ResourcePackProfile profile : profiles) {
            if (profile.getDisplayName().getString().equals(packName)) {
                PackifiedClient.currentPack = profile;
                break;
            }
        }
        if (!Objects.equals(PackifiedClient.currentPack.getDisplayName().getString(), packName)) {
            LogWindow.addWarning("Pack with name '" + packName + "' not found in the list of resource packs.");
        }

        // Reload the resource packs
        CompletableFuture.runAsync(PackUtils::reloadPack);
    }

    public static Path validateIdentifier(String path) {
        try {
            if (path == null) {
                return null;
            }
            path = path.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
            return Path.of(path);
        } catch (InvalidIdentifierException e) {
            return Path.of(path);
        }
    }

    public static void openFileInExplorer(Path path) {
        File file = path.toFile();
        try {
            Packified.LOGGER.info(file.getAbsolutePath());
            ProcessBuilder processBuilder = new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath());
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void moveFile(Path originalPath, String newRelativePath) {
        // move file
        boolean wasOpen = EditorWindow.openFiles.stream().anyMatch(file -> file.getPath().equals(originalPath));
        EditorWindow.openFiles.removeIf(file -> file.getPath().equals(originalPath));
        if (newRelativePath.isEmpty()) {
            System.out.println("New path is empty");
            return;
        }

        Path packFolderPath = getPackFolderPath();
        if (packFolderPath == null) {
            System.err.println("Resource pack folder is not set.");
            return;
        }

        Path newFilePath = packFolderPath.resolve(newRelativePath);

        try {
            // Ensure new file does not already exist
            if (Files.exists(newFilePath)) {
                LogWindow.addWarning("Could not move file: File with new path already exists -> " + newFilePath);
                return;
            }

            // Create parent directories if needed
            Files.createDirectories(newFilePath.getParent());

            // Rename the file safely
            Files.move(originalPath, newFilePath, StandardCopyOption.ATOMIC_MOVE);

            // Update open files in the editor
            if (wasOpen) {
                openFile(newFilePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to move file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void renameFile(Path originalPath, String newRelativePath) {
        boolean wasOpen = EditorWindow.openFiles.stream().anyMatch(file -> file.getPath().equals(originalPath));
        EditorWindow.openFiles.removeIf(file -> file.getPath().equals(originalPath));
        if (newRelativePath.isEmpty()) {
            System.out.println("New name is empty");
            return;
        }

        Path packFolderPath = getPackFolderPath();
        if (packFolderPath == null) {
            System.err.println("Resource pack folder is not set.");
            return;
        }

        Path newFilePath = originalPath.getParent().resolve(newRelativePath);
        System.out.println(newFilePath);

        try {
            // Ensure new file does not already exist
            if (Files.exists(newFilePath)) {
                System.err.println("Cannot rename: File with new name already exists -> " + newFilePath);
                return;
            }

            // Create parent directories if needed
            Files.createDirectories(newFilePath.getParent());

            // Rename the file safely
            Files.move(originalPath, newFilePath, StandardCopyOption.ATOMIC_MOVE);

            // Update open files in the editor
            if (wasOpen) {
                openFile(newFilePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to rename file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getRelativePackPath(Path filePath) {
        Path packFolderPath = getPackFolderPath();

        if (packFolderPath == null) {
            System.err.println("Resource pack folder is not set.");
            return filePath.toString();
        }

        return packFolderPath.relativize(filePath).toString();
    }

    private static final Map<Path, Integer> folderFileCountCache = new HashMap<>();

    public static int getFolderFileCount(Path folderPath) {
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            return 0;
        }

        // Return cached result if available
        if (folderFileCountCache.containsKey(folderPath)) {
            return folderFileCountCache.get(folderPath);
        }

        try {
            int count = (int) Files.walk(folderPath)
                    .filter(Files::isRegularFile)
                    .count();
            folderFileCountCache.put(folderPath, count);
            return count;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Map<Path, Integer> folderFileSizeCache = new HashMap<>();

    public static int getFolderSize(Path folderPath) {
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            return 0;
        }
        // Return cached result if available
        if (folderFileSizeCache.containsKey(folderPath)) {
            return folderFileSizeCache.get(folderPath);
        }

        try {
            int size = (int) Files.walk(folderPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> path.toFile().length())
                    .sum();
            folderFileSizeCache.put(folderPath, size);
            return size;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readFile(Path path) {
        // Read file content
        if (path == null || !Files.exists(path)) {
            System.err.println("File not found: " + path);
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void generateFolderStructure() {
        generateFolderStructure("textures/");
        generateFolderStructure("textures/item/");
        generateFolderStructure("textures/block/");
        generateFolderStructure("textures/models/");
        generateFolderStructure("textures/entity/");
        generateFolderStructure("textures/colormap/");
        generateFolderStructure("textures/misc/");
        generateFolderStructure("textures/effect/");
        generateFolderStructure("textures/entity/entity_type");
        generateFolderStructure("textures/environment/");
        generateFolderStructure("textures/gui/");
        generateFolderStructure("textures/gui/texture/");
        generateFolderStructure("textures/map/");
        generateFolderStructure("textures/mob_effect/");
        generateFolderStructure("textures/particle/");
        generateFolderStructure("textures/painting/");
        generateFolderStructure("textures/trims/");
        generateFolderStructure("textures/trims/color_palettes/");
        generateFolderStructure("textures/trims/entity/");
        generateFolderStructure("textures/trims/entity/humanoid/");
        generateFolderStructure("textures/trims/entity/humanoid_leggings/");
        generateFolderStructure("textures/trims/items/");
        generateFolderStructure("models/");
        generateFolderStructure("models/item/");
        generateFolderStructure("models/block/");
        generateFolderStructure("sounds/");
        generateFolderStructure("sounds/block");
        generateFolderStructure("sounds/records");
        generateFolderStructure("lang/");
        generateFolderStructure("shaders/");
        generateFolderStructure("shaders/programs/");
        generateFolderStructure("shaders/core/");
        generateFolderStructure("font/");
        generateFolderStructure("font/include");
        generateFolderStructure("atlases/");
        generateFolderStructure("texts/");
        generateFolderStructure("items/");
    }

    public static void generateFolderStructure(String s) {
        // Generate folder structure based on the provided string
        Path currentPath = getPackFolderPath();
        currentPath = currentPath.resolve("assets/minecraft/").resolve(s);
        System.out.println(currentPath);

        currentPath.toFile().mkdirs();
    }
}
