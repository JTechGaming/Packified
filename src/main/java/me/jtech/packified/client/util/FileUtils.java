package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.windows.*;
import me.jtech.packified.SyncPacketData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
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

    public static void sendDebugChatMessage(String message) {
//        if (Packified.debugMode) {
//            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(message));
//        }
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
            sendDebugChatMessage("Resource pack folder: " + resourcePackFolder);

            if (resourcePackFolder == null || !Files.exists(resourcePackFolder)) {
                System.err.println("Resource pack folder not found: " + resourcePackFolder);
                sendDebugChatMessage("Resource pack folder not found: " + resourcePackFolder);
                return;
            }
            sendDebugChatMessage("Resource pack folder found: " + resourcePackFolder);

            // Check if the pack is a ZIP file
            if (isZipFile(resourcePackFolder.toFile())) {
                sendDebugChatMessage("Resource pack is a zip file, cannot save to it: " + resourcePackFolder);
                return;
            }

            // Determine the actual file path inside the pack
            Path targetFile = resourcePackFolder.resolve(path);
            sendDebugChatMessage("Target file: " + targetFile);

            // Ensure parent directories exist
            Files.createDirectories(targetFile.getParent());

            if (!Files.exists(targetFile)) {
                sendDebugChatMessage("Target file not found, creating a new one: " + targetFile);
                Files.createFile(targetFile);
            }

            // Handle different file types
            if (fileType.equals(".json")) {
                Files.write(targetFile, content.getBytes(StandardCharsets.UTF_8));
                sendDebugChatMessage("JSON content saved: " + content);
            } else if (fileType.equals(".png")) {
                BufferedImage image = FileUtils.decodeBase64ToImage(content);
                if (image != null) {
                    ImageIO.write(image, "png", targetFile.toFile());
                    sendDebugChatMessage("Image saved: " + targetFile);
                } else {
                    System.err.println("No image found for: " + path);
                    sendDebugChatMessage("No image found for: " + path);
                }
            }

            sendDebugChatMessage("File successfully saved: " + targetFile);

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
        for (ResourcePackProfile resourcePack : PackUtils.refresh()) {
            System.out.println(resourcePack.getDisplayName().getString());
        }
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
            sendDebugChatMessage("Making a backup of the resource pack: " + resourcePackFolder.getAbsolutePath());
            Path backupDir = FabricLoader.getInstance().getConfigDir().resolve("packified-backups");

            int maxBackupCount = PreferencesWindow.maxBackupCount.get(); // Maximum number of backups to keep

            // Clean up old backups if they exceed the maximum count
            if (maxBackupCount > 0) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir)) {
                    List<Path> backups = new ArrayList<>();
                    for (Path entry : stream) {
                        backups.add(entry);
                    }
                    if (backups.size() >= maxBackupCount) {
                        // Sort by last modified time and delete the oldest ones
                        backups.sort(Comparator.comparingLong(path -> {
                            try {
                                return Files.getLastModifiedTime(path).toMillis();
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to retrieve last modified time for backup", e);
                            }
                        }));
                        for (int i = 0; i < backups.size() - maxBackupCount + 1; i++) {
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
            sendDebugChatMessage("Backup folder: " + backupDir);

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

            sendDebugChatMessage("Backup folder name pattern: " + folderNamePattern);

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

            sendDebugChatMessage("Backup created: " + backupFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Not used
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
            File resourcePackFolder = new File("resourcepacks/" + pack.getDisplayName().getString());
            File targetFile = new File(resourcePackFolder, "pack.mcmeta");
            return Files.readString(targetFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setMCMetaContent(ResourcePackProfile pack, String content) {
        try {
            File resourcePackFolder = new File("resourcepacks/" + pack.getDisplayName().getString());
            File targetFile = new File(resourcePackFolder, "pack.mcmeta");
            Files.write(targetFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            PackUtils.reloadPack();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getPackPngContent(ResourcePackProfile pack) {
        try (ResourcePack resourcePack = pack.createResourcePack()) {
            InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla("pack.png"))).get();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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
                sendDebugChatMessage("JSON content saved: " + asset.assetData());
            } else if (asset.extension().equals(".png")) {
                // Save PNG image
                BufferedImage image = FileUtils.decodeBase64ToImage(asset.assetData());
                if (image != null) {
                    try {
                        ImageIO.write(image, "png", targetFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    sendDebugChatMessage("Image saved: " + targetFile.getAbsolutePath());
                } else {
                    System.err.println("No image found for: " + asset.path());
                    sendDebugChatMessage("No image found for: " + asset.path());
                }
            }
        }

        sendDebugChatMessage("Resource pack created: " + packName);

        List<ResourcePackProfile> profiles = PackUtils.refresh();
        for (ResourcePackProfile profile : profiles) {
            if (profile.getDisplayName().getString().equals(packName)) {
                PackifiedClient.currentPack = profile;
                sendDebugChatMessage("Resource pack loaded: " + packName);
                break;
            }
        }
        if (!Objects.equals(PackifiedClient.currentPack.getDisplayName().getString(), packName)) {
            sendDebugChatMessage("Resource pack not found: " + packName);
        }

        // Reload the resource packs
        PackUtils.reloadPack();
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
                LogWindow.addInfo("Could not move file: File with new path already exists -> " + newFilePath);
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

    public static String getRelativePackPath(Path filePath, String name) {
        Path packFolderPath = getPackFolderPath();
        if (packFolderPath == null) {
            System.err.println("Resource pack folder is not set.");
            return filePath.toString();
        }
        return filePath.resolve(name).toString();
    }

    public static void updateTexture(BufferedImage image, PackFile imageFile) {
        // Update the texture
        String content = encodeImageToBase64(image);
        imageFile.setImageEditorContent(content);
        imageFile.saveFile();
    }

    public static int getFolderFileCount(Path folderPath) {
        int fileCount = 0;
        if (!folderPath.toFile().exists()) {
            return 0;
        }
        try {
            fileCount = (int) Files.walk(folderPath)
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileCount;
    }

    public static int getFolderSize(Path folderPath) {
        int totalSize = 0;
        if (!folderPath.toFile().exists()) {
            return 0;
        }
        try {
            totalSize = (int) Files.walk(folderPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> path.toFile().length())
                    .sum();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return totalSize;
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
    }

    public static void generateFolderStructure(String s) {
        // Generate folder structure based on the provided string
        Path currentPath = getPackFolderPath();
        currentPath = currentPath.resolve("assets/minecraft/").resolve(s);
        System.out.println(currentPath);

        currentPath.toFile().mkdirs();
    }
}
