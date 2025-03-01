package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.client.windows.FileHierarchy;
import me.jtech.packified.SyncPacketData;
import me.jtech.packified.client.windows.SelectFolderWindow;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.resource.*;
import net.minecraft.text.Text;
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

    public static FileHierarchy.FileType getExtension(Identifier identifier) {
        return FileHierarchy.FileType.fromExtension(getFileExtension(identifier.getPath()));
    }

    public static String getFileExtensionName(String fileName) {
        return getFileExtensionName(fileName, FileHierarchy.supportedFileExtensions);
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
        return isSupportedFileExtension(fileName, FileHierarchy.supportedFileExtensions);
    }

    public static void deleteFile(Identifier identifier) {
        // delete file
        File file = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + "/assets/" + identifier.getNamespace() + "/" + identifier.getPath());
        System.out.println(file.getPath());
        if (file.exists()) {
            if (file.canWrite()) {
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
                    List<File> fileList = Arrays.asList(files);
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

    public static void moveFile(Identifier identifier, String newPath) {
        // move file
        EditorWindow.openFiles.removeIf(file -> file.getIdentifier().equals(identifier));
        File file = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + "/assets/" + identifier.getNamespace() + "/" + identifier.getPath());
        System.out.println(file.getPath());
        if (file.exists()) {
            if (file.canWrite()) {
                try {
                    //Rename the file
                    File newFile = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + "/assets/" + identifier.getNamespace() + "/" + newPath);
                    Files.move(file.toPath(), newFile.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void renameFile(Identifier identifier, String newName) {
        // rename file
        if (newName.isEmpty()) {
            System.out.println("New name is empty");
            return;
        }
        System.out.println(newName);
        EditorWindow.openFiles.removeIf(file -> file.getIdentifier().equals(identifier));
        File file = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + "/assets/" + identifier.getNamespace() + "/" + identifier.getPath());
        System.out.println(file.getPath());
        if (file.exists()) {
            if (file.canWrite()) {
                try {
                    //Rename the file
                    File newFile = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + "/assets/" + identifier.getNamespace() + "/" + newName);
                    Files.move(file.toPath(), newFile.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void openFile(Identifier identifier, FileHierarchy.FileType fileType) {
        // Get the asset content from the PackifiedClient.currentPack
        try (ResourcePack resourcePack = PackifiedClient.currentPack.createResourcePack()) {
            InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, identifier)).get();
            if (fileType == null) {
                System.err.println("Unsupported Filetype: " + identifier);
                return;
            }
            String content = "";
            switch (fileType) {
                case JSON:
                    // open JSON file
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case PNG:
                    // open PNG file
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image != null) {
                        EditorWindow.openImageFile(identifier, image);
                    } else {
                        System.err.println("Failed to load image: " + identifier);
                    }
                    break;
                case OGG:
                    // open OGG file
                    //TODO implement this
                    break;
                case TEXT:
                    // open TEXT file
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case PROPERTIES:
                    // open PROPERTIES file
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case VSH:
                    // open VSH file
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case FSH:
                    // open FSH file
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case BB_MODEL:
                    // open BB_MODEL file
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case BB_MODEL_JSON:
                    // open BB_MODEL_JSON file
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case MC_META:
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    // open MC_META file
                    EditorWindow.openTextFile(identifier, content);
                    break;
                default:
                    System.err.println("Unsupported file type: " + fileType);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendDebugChatMessage(String message) {
        if (Packified.debugMode) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(message));
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
            saveFile(file.getIdentifier(), file.getExtension().getExtension(), getContent(file));
        }
    }

    public static void saveSingleFile(Identifier identifier, String fileType, String content) {
        // Before saving, make a backup of the file
        File resourcePackFolder = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString());
        makePackBackup(resourcePackFolder);
        saveFile(identifier, fileType, content);
    }

    private static void saveFile(Identifier identifier, String fileType, String content) {
        try {
            System.out.println(identifier);
            // Get the resource pack folder path
            File resourcePackFolder = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString());
            sendDebugChatMessage("Resource pack folder: " + resourcePackFolder.getAbsolutePath());

            if (!resourcePackFolder.exists()) {
                System.err.println("Resource pack folder not found: " + resourcePackFolder.getAbsolutePath());
                sendDebugChatMessage("Resource pack folder not found: " + resourcePackFolder.getAbsolutePath());
                return;
            }
            sendDebugChatMessage("Resource pack folder found: " + resourcePackFolder.getAbsolutePath());

            // Convert identifier to file path (e.g., "assets/minecraft/textures/block/stone.png")
            String targetFilePath = "assets/" + identifier.getNamespace() + "/" + identifier.getPath();
            sendDebugChatMessage("Target file: " + targetFilePath);

            if (isZipFile(resourcePackFolder)) {
                //maybe don't do this, but it's the best solution for now
                sendDebugChatMessage("Resource pack is a zip file, cannot save to it: " + resourcePackFolder.getAbsolutePath());
                return;
            } else {
                File zipFolder = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + ".zip");
                removeZipFileFromOptions(zipFolder);
                // Save to folder
                File targetFile = new File(resourcePackFolder, targetFilePath);

                // Ensure parent directories exist
                targetFile.getParentFile().mkdirs();

                if (!targetFile.exists()) {
                    sendDebugChatMessage("Target file not found, creating a new one: " + targetFile.getAbsolutePath());
                    targetFile.createNewFile();
                }

                if (fileType.equals(".json")) {
                    // Save JSON file
                    try {
                        Files.write(targetFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    sendDebugChatMessage("JSON content saved: " + content);
                } else if (fileType.equals(".png")) {
                    // Save PNG image
                    //BufferedImage image = EditorWindow.getEditedImage(identifier); // Get edited image from UI
                    //TODO get edited image from UI (if the image is edited) <- image editor not implemented yet
                    BufferedImage image = FileUtils.decodeBase64ToImage(content);
                    if (image != null) {
                        try {
                            ImageIO.write(image, "png", targetFile);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        sendDebugChatMessage("Image saved: " + targetFile.getAbsolutePath());
                    } else {
                        System.err.println("No image found for: " + identifier);
                        sendDebugChatMessage("No image found for: " + identifier);
                    }
                }
            }

            sendDebugChatMessage("File saved: " + targetFilePath);

            for (PackFile file : EditorWindow.openFiles) {
                if (file.getIdentifier().equals(identifier)) {
                    file.saveFile();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveFolder(Identifier identifier) {
        // Save folder
        File folder = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + "/assets/" + identifier.getNamespace() + "/" + identifier.getPath());
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

    public static int getFileSize(Identifier identifier) {
        // Get the asset content from the PackifiedClient.currentPack
        if (identifier == null) {
            return 0;
        }
        try (ResourcePack resourcePack = PackifiedClient.currentPack.createResourcePack()) {
            InputSupplier<InputStream> opened = resourcePack.open(ResourceType.CLIENT_RESOURCES, identifier);
            if (opened == null) {
                return 0;
            }
            InputStream inputStream = opened.get();
            return inputStream.available();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            return 0;
        }
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
        FileHierarchy.FileType fileType = FileHierarchy.FileType.fromExtension(extension);
        if (fileType == null) {
            return "Unknown";
        }
        return switch (fileType) {
            case JSON -> "Json";
            case PNG -> "Image";
            case OGG -> "Sound";
            case MC_META -> "Meta";
            case TEXT -> "Text";
            case PROPERTIES -> "PROPERTIES";
            case VSH -> "Vertex Shader";
            case FSH -> "Fragment Shader";
            case BB_MODEL -> "Blockbench Model";
            case BB_MODEL_JSON -> "Blockbench Model Json";
        };
    }

    public static String getContent(PackFile file) {
        if (file.getExtension().getExtension().equals(FileHierarchy.FileType.JSON.getExtension())) {
            return file.getTextEditor().getText();
        } else if (file.getExtension().getExtension().equals(FileHierarchy.FileType.PNG.getExtension())) {
            return encodeImageToBase64(file.getImageEditorContent());
        } else if (file.getExtension().getExtension().equals(FileHierarchy.FileType.OGG.getExtension())) {
            //return file.getSoundContent();
            return "";
        } else {
            Packified.LOGGER.error("Unsupported file type: {}", file.getExtension().getExtension());
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

    private static BufferedImage decodeBase64ToImage(String content) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(content);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
            return ImageIO.read(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String encodeSoundToString(OggAudioStream audio) {
        //TODO implement this
        return "";
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
            File targetFile = new File(minecraftFolder, asset.identifier().getPath());
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
                    System.err.println("No image found for: " + asset.identifier());
                    sendDebugChatMessage("No image found for: " + asset.identifier());
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

    public static Identifier validateIdentifier(String path) {
        try {
            if (path == null) {
                return null;
            }
            path = path.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
            return Identifier.of(path);
        } catch (InvalidIdentifierException e) {
            return null;
        }
    }

    public static void openFileInExplorer(Identifier identifier) {
        File file = new File("resourcepacks/" + PackifiedClient.currentPack.getDisplayName().getString() + "/assets/" + identifier.getNamespace() + "/" + identifier.getPath());
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath());
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
