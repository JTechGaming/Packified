package me.jtech.packified.client.helpers;

import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.util.PackWatcher;
import me.jtech.packified.client.windows.EditorWindow;
import me.jtech.packified.client.windows.FileHierarchyWindow;
import me.jtech.packified.client.windows.LogWindow;
import net.minecraft.resource.ResourcePackProfile;

import java.io.IOException;

// todo make PackHelper abstract so it can work for respack, shader pack, and datapack
public class PackHelper {
    private static ResourcePackProfile currentPack;
    private static PackWatcher watcher;

    public static ResourcePackProfile getCurrentPack() {
        return currentPack;
    }

    public static void updateCurrentPack(ResourcePackProfile currentPack) {
        EditorWindow.openFiles.clear();
        if (PackHelper.isValid()) {
            PackUtils.unloadPack(PackHelper.getCurrentPack());
            disposeWatcher();
        }
        PackUtils.refresh();

        PackHelper.currentPack = currentPack;

        ModConfig.savePackStatus(currentPack);
        PackUtils.loadPack(currentPack); // This also sends packet, enables pack, and reloads packs
        PackUtils.checkPackType(currentPack);

        VersionControlHelper.init(currentPack.getDisplayName().getString());

        if (watcher == null) {
            try {
                watcher = new PackWatcher(FileUtils.getPackFolderPath());
                watcher.start();
            } catch (IOException e) {
                LogWindow.addError(e.getMessage());
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (watcher != null) watcher.stop();
                } catch (IOException e) {
                    LogWindow.addError(e.getMessage());
                }
            }));
        }
    }

    public static void closePack() {
        PackHelper.currentPack = null;
    }

    public static boolean isValid() {
        return PackHelper.currentPack != null;
    }

    public static boolean isInvalid() {
        return PackHelper.currentPack == null;
    }

    public static PackWatcher getWatcher() {
        return watcher;
    }

    public static void disposeWatcher() {
        try {
            watcher.stop();
        } catch (IOException e) {
            LogWindow.addError(e.getMessage());
        }
        watcher = null;
        FileHierarchyWindow.cachedHierarchy = null; // Reset the cached hierarchy
        LogWindow.addDebugInfo("PackWatcher: Stopped and reset due to root path change.");
    }
}
