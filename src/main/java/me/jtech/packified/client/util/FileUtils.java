package me.jtech.packified.client.util;

import imgui.ImGui;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.windows.*;
import me.jtech.packified.client.windows.popups.SelectFolderWindow;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FileUtils {
    public static String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return fileName.substring(lastIndexOf);
    }

    public static String getExtensionFromPath(Path path) {
        return getFileExtension(path.getFileName().toString());
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
                    LogWindow.addError("Failed to delete file: " + e.getMessage());
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
                String extension = getFileExtension(fileName);
                String content = null;
                BufferedImage image;
                if (extension.equals(".png")) {
                    LogWindow.addInfo("Importing image file: " + fileName);
                    try {
                        image = ImageIO.read(file);
                        if (image != null) {
                            SelectFolderWindow.open(fileName, extension, encodeImageToBase64(image));
                        } else {
                            CornerNotificationsHelper.addNotification("Failed to load image: " + fileName, "failed to import file", Color.RED, 3);
                            LogWindow.addError("Failed to load image: " + fileName);
                        }
                    } catch (IOException e) {
                        LogWindow.addError("Failed to read image file: " + fileName + " - " + e.getMessage());
                    }
                } else if(extension.equals(".ogg")) {
                    LogWindow.addInfo("Importing sound file: " + fileName);
                    try {
                        byte[] audioData = Files.readAllBytes(file.toPath());
                        SelectFolderWindow.open(fileName, extension, encodeSoundToString(audioData));
                    } catch (IOException e) {
                        LogWindow.addError("Failed to read sound file: " + fileName + " - " + e.getMessage());
                    }
                } else {  // json, txt, etc. basically just all text files
                    try {
                        content = Files.readString(file.toPath());
                    } catch (IOException e) {
                        LogWindow.addError("Failed to read file: " + fileName + " - " + e.getMessage());
                    }
                    if (content == null) {
                        CornerNotificationsHelper.addNotification("Failed to read file: " + fileName, "failed to import file", Color.RED, 3);
                        LogWindow.addError("Failed to read file: " + fileName);
                        return;
                    }
                    SelectFolderWindow.open(fileName, extension, content);
                }
            }
        } else {
            CornerNotificationsHelper.addNotification("File not found: " + path, "failed to import file", Color.RED, 3);
            LogWindow.addError("File not found: " + path);
        }
    }

    public static void openFile(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            LogWindow.addError("File not found: " + filePath);
            return;
        }

        String extension = getFileExtension(filePath.getFileName().toString());

        if (extension.isBlank()) {
            LogWindow.addError("Invalid file type: [BLANK EXTENSION ERROR] at -> " + filePath);
            return;
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            String content;
            switch (extension) {
                case ".png" -> {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image != null) {
                        EditorWindow.openImageFile(filePath, image);
                    } else {
                        LogWindow.addError("Failed to open image file: " + filePath);
                    }
                }
                case ".ogg" -> {
                    byte[] audioData = Files.readAllBytes(filePath);
                    EditorWindow.openAudioFile(filePath, audioData);
                }
                default -> {  // json, txt, etc. basically just all text files
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(filePath, content);
                }
            }
        } catch (IOException e) {
            LogWindow.addError("Failed to open file: " + filePath + " - " + e.getMessage());
            return;
        }

        ImGui.setWindowFocus("File Editor");
    }

    public static void saveAllFiles() {
        if (PackifiedClient.currentPack == null) {
            LogWindow.addError("No resource pack is currently loaded.");
            return;
        }
        File resourcePackFolder = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString());
        makePackBackup(resourcePackFolder);
        for (PackFile file : EditorWindow.openFiles) {
            saveFile(file.getPath(), file.getExtension(), getContent(file));
        }

        if (ModelEditorWindow.isModelWindowOpen()) {
            ModelEditorWindow.saveCurrentModel();
        }
    }

    public static boolean saveSingleFile(Path path, String fileType, String content, ResourcePackProfile pack) {
        // Before saving, make a backup of the file
        File resourcePackFolder = new File("resourcepacks/" + pack.getDisplayName().getString());
        if (!resourcePackFolder.exists()) {
            try {
                Files.createDirectories(resourcePackFolder.toPath());
            } catch (IOException e) {
                LogWindow.addError("Failed to create resource pack folder: " + e.getMessage());
                return false;
            }
        }
        makePackBackup(resourcePackFolder);
        saveFile(path, fileType, content);
        return true;
    }

    private static void saveFile(Path path, String fileType, String content) {
        try {
            // Get the resource pack folder path
            Path resourcePackFolder = getPackFolderPath();

            if (resourcePackFolder == null || !Files.exists(resourcePackFolder)) {
                LogWindow.addError("Resource pack folder does not exist.");
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
            if (fileType.equals(".ogg")) {
                byte[] audioData = Base64.getDecoder().decode(content);
                Files.write(targetFile, audioData);
            } else if (fileType.equals(".png")) {
                BufferedImage image = FileUtils.decodeBase64ToImage(content);
                if (image != null) {
                    ImageIO.write(image, "png", targetFile.toFile());
                } else {
                    LogWindow.addError("Failed to decode image for saving: " + path);
                }
            } else {  // json, txt, etc. basically just all text files
                Files.write(targetFile, content.getBytes());
            }

            // Update the file in the editor if it's open
            for (PackFile file : EditorWindow.openFiles) {
                if (file.getPath().equals(path)) {
                    file.saveFile();
                    break;
                }
            }
        } catch (IOException e) {
            LogWindow.addError("Failed to save file: " + e.getMessage());
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
                    LogWindow.addError("Failed to unzip file: " + e.getMessage());
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
                    LogWindow.addError("Failed to zip file: " + e.getMessage());
                }
            });
        }
    }

    @Deprecated
    public static void removeZipFileFromOptions(File zipFile) {
        if (zipFile.exists()) {
            if (zipFile.canWrite()) {
                try {
                    Files.delete(zipFile.toPath());
                } catch (IOException e) {
                    LogWindow.addError("Failed to delete zip file: " + e.getMessage());
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

            if (!backupDir.toFile().exists()) {
                Files.createDirectories(backupDir);
                backupDir.toFile().mkdirs();
            }

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
                                LogWindow.addError("Failed to retrieve last modified time for backup" + e);
                                return Long.MAX_VALUE; // If we can't get the time, treat it as the oldest
                            }
                        }));
                        for (int i = 0; i < backups.size() - maxBackupCount; i++) {
                            Files.delete(backups.get(i));
                        }
                    }
                } catch (IOException e) {
                    LogWindow.addError("Failed to clean up old backups: " + e.getMessage());
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
                LogWindow.addError("Failed to read backup directory: " + e.getMessage());
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
                        LogWindow.addError("Failed to create backup file: " + e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            LogWindow.addError("Failed to create backup: " + e.getMessage());
        }
    }

    public static void loadIdentifierPackAssets(ResourcePackProfile resourcePackProfile) {
        ResourcePack resourcePack = resourcePackProfile.createResourcePack();
        Path base = FabricLoader.getInstance().getGameDir()
                .resolve("resourcepacks")
                .resolve(PackUtils.legalizeName(resourcePackProfile.getDisplayName().getString()));

        for (String namespace : resourcePack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
            System.out.println(namespace);
            try {
                Files.createDirectories(base.resolve(namespace));
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "", ((identifier, inputStreamInputSupplier) -> {
                System.out.println(identifier);
            }));

            resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "", (identifier, resourceSupplier) -> {
                // identifier.getPath() is the path inside the namespace (e.g. "textures/block/stone.png")
                Path out = base.resolve(namespace).resolve(identifier.getPath());
                try {
                    Files.createDirectories(out.getParent());
                    try (InputStream is = resourceSupplier.get()) {
                        Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
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
                "    \"pack_format\": " + SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES) + ",\n" +
                "    \"description\": \"" + packProfile.getDescription().getString() + "\"\n" +
                "  }\n" +
                "}";

        BufferedImage image = ImGuiImplementation.bufferedImageFromIdentifier(Identifier.of(packProfile.getId(), "icon.png"));

        if (!Files.exists(packMcMetaPath)) {
            try {
                Files.createFile(packMcMetaPath);
                Files.writeString(packMcMetaPath, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LogWindow.addError("Failed to create pack.mcmeta file" + e);
            }
        }

        if (!Files.exists(packPngPath) && image != null) {
            try {
                Files.createFile(packPngPath);
                ImageIO.write(image, "png", packPngPath.toFile());
            } catch (IOException e) {
                LogWindow.addError("Failed to create pack.png file" + e);
            }
        }
    }

    @Deprecated
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
            LogWindow.addError("Failed to create file for identifier: " + identifier + " in namespace: " + namespace + " with prefix: " + prefix + ". Error: " + e.getMessage());
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
            LogWindow.addError("Failed to clear backups: " + e.getMessage());
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
            case ".glsl" -> "OpenGL Shading Language";
            case ".lang" -> "Language File";
            case ".bbmodel" -> "Blockbench Model";
            case ".bbmodel.json" -> "Blockbench Model JSON";
            default -> "Unknown";
        };
    }


    public static String getContent(PackFile file) {
        String extension = file.getExtension();

        if (extension.equals(".png")) {
            return encodeImageToBase64(file.getImageEditorContent());
        } else if (extension.equals(".ogg")) {
            return ""; // TODO: Handle sound content if needed
        } else {  // json, txt, etc. basically just all text files
            return file.getTextEditor().getText();
        }
    }

    public static String encodeImageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            LogWindow.addError("Failed to encode image to Base64: " + e.getMessage());
            return null;
        }
    }

    public static BufferedImage decodeBase64ToImage(String content) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(content);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
            return ImageIO.read(inputStream);
        } catch (IOException e) {
            LogWindow.addError("Failed to decode Base64 to image: " + e.getMessage());
            return null;
        }
    }

    public static String encodeSoundToString(byte[] audio) {
        if (audio == null || audio.length == 0) {
            LogWindow.addError("Audio data is null or empty.");
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
            LogWindow.addError("Failed to read pack.mcmeta file: " + e.getMessage());
            return null;
        }
    }

    public static void setMCMetaContent(ResourcePackProfile pack, String content) {
        try {
            File resourcePackFolder = new File("resourcepacks/" + PackUtils.legalizeName(pack.getDisplayName().getString()));
            File targetFile = new File(resourcePackFolder, "pack.mcmeta");
            Files.write(targetFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            CompletableFuture.runAsync(PackUtils::reloadPack);
        } catch (IOException e) {
            LogWindow.addError("Failed to write pack.mcmeta file: " + e.getMessage());
        }
    }

    public static void createPack(String packName, List<SyncPacketData.AssetData> assets, String metadata) {
        LogWindow.addInfo("Creating pack: " + packName);
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
            LogWindow.addError("Failed to write pack.mcmeta file: " + e.getMessage());
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
                    LogWindow.addError("Failed to write JSON file for: " + asset.path() + " - " + e.getMessage());
                }
            } else if (asset.extension().equals(".png")) {
                // Save PNG image
                BufferedImage image = FileUtils.decodeBase64ToImage(asset.assetData());
                if (image != null) {
                    try {
                        ImageIO.write(image, "png", targetFile);
                    } catch (IOException e) {
                        LogWindow.addError("Failed to write PNG image for: " + asset.path() + " - " + e.getMessage());
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
            LogWindow.addError("Failed to open file in explorer: " + e.getMessage());
        }
    }

    public static void moveFile(Path originalPath, String newRelativePath) {
        // move file
        boolean wasOpen = EditorWindow.openFiles.stream().anyMatch(file -> file.getPath().equals(originalPath));
        EditorWindow.openFiles.removeIf(file -> file.getPath().equals(originalPath));
        if (newRelativePath.isEmpty()) {
            LogWindow.addError("New path is empty");
            return;
        }

        Path packFolderPath = getPackFolderPath();
        if (packFolderPath == null) {
            LogWindow.addError("Resource pack folder is not set.");
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
            LogWindow.addError("Failed to move file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void renameFile(Path originalPath, String newRelativePath) {
        boolean wasOpen = EditorWindow.openFiles.stream().anyMatch(file -> file.getPath().equals(originalPath));
        EditorWindow.openFiles.removeIf(file -> file.getPath().equals(originalPath));
        if (newRelativePath.isEmpty()) {
            LogWindow.addError("New name is empty");
            return;
        }

        Path packFolderPath = getPackFolderPath();
        if (packFolderPath == null) {
            LogWindow.addError("Resource pack folder is not set.");
            return;
        }

        Path newFilePath = originalPath.getParent().resolve(newRelativePath);
        System.out.println(newFilePath);

        try {
            // Ensure new file does not already exist
            if (Files.exists(newFilePath)) {
                LogWindow.addError("Cannot rename: File with new name already exists -> " + newFilePath);
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
            LogWindow.addError("Failed to rename file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getRelativePackPath(Path filePath) {
        Path packFolderPath = getPackFolderPath();

        if (packFolderPath == null) {
            LogWindow.addError("Resource pack folder is not set.");
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
            LogWindow.addError("Failed to count files in folder: " + e.getMessage());
            return 0;
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
            LogWindow.addError("Failed to calculate folder size: " + e.getMessage());
            return 0;
        }
    }

    public static String readFile(Path path) {
        // Read file content
        if (path == null || !Files.exists(path)) {
            LogWindow.addError("File not found: " + path);
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LogWindow.addError("Failed to read file: " + e.getMessage());
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
        LogWindow.addInfo("Generating folder: " + currentPath);

        currentPath.toFile().mkdirs();
    }
}
