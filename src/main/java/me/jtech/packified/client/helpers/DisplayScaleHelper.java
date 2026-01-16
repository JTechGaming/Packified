package me.jtech.packified.client.helpers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class DisplayScaleHelper {
    /**
     * Get the appropriate UI button size based on the display scale.
     * TODO add a setting to multiply the UI size by a custom factor
     */
    public static int getUIButtonSize() {
        // Determine UI button size based on display scale
        float scale = getDisplayScale();
        if (scale >= 2.0f) {
            return 22;
        } else if (scale >= 1.5f) {
            return 18;
        } else {
            return 14;
        }
    }

    /**
     * This is essentially a function to get the factor by which the display is scaled from 1920x1080.
     * For example, if the display resolution is 3840x2160, the scale would be 2.0.
     * If the display resolution is 2560x1440, the scale would be approximately 1.33.
     */
    private static float getDisplayScale() {
        int width = MinecraftClient.getInstance().getWindow().width;
        int height = MinecraftClient.getInstance().getWindow().height;
        float scaleX = (float) width / 1920f;
        float scaleY = (float) height / 1080f;
        return Math.min(scaleX, scaleY);
    }

    /**
     * Get the ideal font size based on the display scale.
     */
    public static int getIdealFontSize() {
        float scale = getDisplayScale();
        if (scale >= 2.0f) {
            return 18;
        } else if (scale >= 1.5f) {
            return 16;
        } else {
            return 14;
        }
    }
}
