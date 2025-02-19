package me.jtech.packified.client.util;

import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.client.windows.FileHierarchy;
import me.jtech.packified.SyncPacketData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;

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
        //TODO add delete file logic
    }

    public static void importFile(Identifier identifier) {
        // import file
        //TODO add import file logic
    }

    public static void exportFile(Identifier identifier) {
        // export file
        //TODO add export file logic
    }

    public static void moveFile(Identifier identifier) {
        // move file
        //TODO add move file logic
    }

    public static void openFile(Identifier identifier, FileHierarchy.FileType fileType) {
        // Get the asset content from the PackifiedClient.currentPack
        try (ResourcePack resourcePack = PackifiedClient.currentPack.createResourcePack()) {
            InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, identifier)).get();
            if (fileType == null) {
                System.err.println("Unsupported Filetype: " + identifier);
                return;
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            switch (fileType) {
                case JSON:
                    // open JSON file
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
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case PROPERTIES:
                    // open PROPERTIES file
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case VSH:
                    // open VSH file
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case FSH:
                    // open FSH file
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case BB_MODEL:
                    // open BB_MODEL file
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case BB_MODEL_JSON:
                    // open BB_MODEL_JSON file
                    EditorWindow.openTextFile(identifier, content);
                    break;
                case MC_META:
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

    public static void saveFile(Identifier identifier, String fileType, String content) {
        //TODO make this work when the resource pack is a zip file
        try {
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
            File targetFile = new File(resourcePackFolder, "assets/" + identifier.getNamespace() + "/" + identifier.getPath());
            sendDebugChatMessage("Target file: " + targetFile.getAbsolutePath());

            if (!targetFile.exists()) {
                sendDebugChatMessage("Target file not found, creating a new one: " + targetFile.getAbsolutePath());
                targetFile.createNewFile();
            }

            // Ensure parent directories exist
            targetFile.getParentFile().mkdirs();

            // Before saving, make a backup of the file
            makePackBackup(targetFile);

            if (fileType.equals(".json")) {
                // Save JSON file
                Files.write(targetFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                sendDebugChatMessage("JSON content saved: " + content);
            } else if (fileType.equals(".png")) {
                // Save PNG image
                //BufferedImage image = EditorWindow.getEditedImage(identifier); // Get edited image from UI
                //TODO get edited image from UI
                BufferedImage image = null;
                if (image != null) {
                    ImageIO.write(image, "png", targetFile);
                    sendDebugChatMessage("Image saved: " + targetFile.getAbsolutePath());
                } else {
                    System.err.println("No edited image found for: " + identifier);
                    sendDebugChatMessage("No edited image found for: " + identifier);
                }
            }

            sendDebugChatMessage("File saved: " + targetFile.getAbsolutePath());

            for (PackFile file : EditorWindow.openFiles) {
                if (file.getIdentifier().equals(identifier)) {
                    file.saveFile();
                    break;
                }
            }

            // Reload Resource Packs
            PackUtils.reloadPack();
            sendDebugChatMessage("Resource pack reloaded");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void makePackBackup(File targetFile) {
        try {
            // Get the resource pack folder path
            sendDebugChatMessage("Making a backup of the file: " + targetFile.getAbsolutePath());
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("packified-backups");

            if (!dir.toFile().exists()) {
                Files.createDirectories(dir);
            }
            sendDebugChatMessage("Backup folder: " + dir);

            String baseName = targetFile.getName().replace(getFileExtension(targetFile.getName()), ""); // get the file name without the extension
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date()); // get the current date
            String fileNamePattern = baseName + "_" + date + "_"; // create the backup file name pattern for the current date
            int highestId = 0;
            sendDebugChatMessage("Backup file name pattern: " + fileNamePattern);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString();
                    if (fileName.startsWith(fileNamePattern)) {
                        String idStr = fileName.substring(fileNamePattern.length(), fileName.lastIndexOf('.')); // get the backup ID from the file name
                        try {
                            int id = Integer.parseInt(idStr); // get the backup ID
                            if (id > highestId) { // if the current backup ID is higher than the highest backup ID
                                highestId = id; // set the highest backup ID to the current backup ID
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            sendDebugChatMessage("Highest backup ID: " + highestId);

            String name = fileNamePattern + (highestId + 1) + getFileExtension(targetFile.getName()); // create the backup file name

            sendDebugChatMessage("Backup file name: " + name);

            try {
                Files.copy(targetFile.toPath(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING); // copy the file to the backup folder
                sendDebugChatMessage("Backup created: " + dir.resolve(name));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void restorePackBackup() {
        //TODO implement this method
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

    public static int getFileSize(Identifier identifier) {
        // Get the asset content from the PackifiedClient.currentPack
        if (identifier == null) {
            return 0;
        }
        try (ResourcePack resourcePack = PackifiedClient.currentPack.createResourcePack()) {
            InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, identifier)).get();
            return inputStream.available();
        } catch (IOException e) {
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
        switch (fileType) {
            case JSON:
                return "Json";
            case PNG:
                return "Image";
            case OGG:
                return "Sound";
            case MC_META:
                return "Meta";
            case TEXT:
                return "Text";
            case PROPERTIES:
                return "PROPERTIES";
            case VSH:
                return "Vertex Shader";
            case FSH:
                return "Fragment Shader";
            case BB_MODEL:
                return "Blockbench Model";
            case BB_MODEL_JSON:
                return "Blockbench Model Json";
            default:
                return "Unknown";
        }
    }

    public static String getContent(PackFile file) {
        if (file.getExtension().getExtension().equals(FileHierarchy.FileType.JSON.getExtension())) {
            return file.getTextContent();
        } else if (file.getExtension().getExtension().equals(FileHierarchy.FileType.PNG.getExtension())) {
            return encodeImageToBase64(file.getImageContent());
        } else if (file.getExtension().getExtension().equals(FileHierarchy.FileType.OGG.getExtension())) {
            //return file.getSoundContent();
            return "";
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + file.getExtension().getExtension());
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

    public static String getMCMetaContent(ResourcePackProfile pack) {
        try (ResourcePack resourcePack = pack.createResourcePack()) {
            InputStream inputStream = Objects.requireNonNull(resourcePack.open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla("pack.mcmeta"))).get();
            String out = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println(out);
            return out;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setMCMetaContent(ResourcePackProfile pack, String content) {
        try (ResourcePack resourcePack = pack.createResourcePack()) {
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

        // Create the pack.mcmeta file
        File mcmetaFile = new File(resourcePackFolder, "pack.mcmeta");
        try {
            Files.write(mcmetaFile.toPath(), metadata.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create the assets
        for (SyncPacketData.AssetData asset : assets) {
            File targetFile = new File(assetsFolder, asset.getIdentifier().getPath());
            try {
                Files.write(targetFile.toPath(), asset.getAssetData().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Put the pack in the resource pack list
        PackUtils.refresh();

        // Reload the resource packs
        PackUtils.reloadPack();
    }
}
