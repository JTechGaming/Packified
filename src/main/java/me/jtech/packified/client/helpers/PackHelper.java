package me.jtech.packified.client.helpers;

import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.windows.EditorWindow;
import net.minecraft.resource.ResourcePackProfile;

public class PackHelper {
    private static ResourcePackProfile currentPack;

    public static ResourcePackProfile getCurrentPack() {
        return currentPack;
    }

    public static void updateCurrentPack(ResourcePackProfile currentPack) {
        EditorWindow.openFiles.clear();
        if (PackHelper.isValid()) {
            PackUtils.unloadPack(PackHelper.getCurrentPack());
        }
        PackUtils.refresh();

        PackHelper.currentPack = currentPack;

        ModConfig.savePackStatus(currentPack);
        PackUtils.loadPack(currentPack); // This also sends packet, enables pack, and reloads packs
        PackUtils.checkPackType(currentPack);
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
}
