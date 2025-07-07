package me.jtech.packified.client.util;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class SafeTextureLoader {

    private static final Map<Path, Pair<NativeImageBackedTexture, Integer>> textureCache = new HashMap<>();

    public static int load(Path path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path).getRight(); // Return cached textureId
        }

        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) {
                System.err.println("Failed to load image: " + path);
                return -1;
            }

            // Convert to ARGB if needed
            if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage converted = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                converted.getGraphics().drawImage(img, 0, 0, null);
                img = converted;
            }

            try (NativeImage nativeImage = new NativeImage(img.getWidth(), img.getHeight(), true)) {
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        int argb = img.getRGB(x, y);
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        int abgr = (a << 24) | (b << 16) | (g << 8) | r; // Convert to ABGR format for OpenGL
                        nativeImage.setColor(x, y, abgr);
                    }
                }

                NativeImageBackedTexture tex = new NativeImageBackedTexture(nativeImage);
                TextureManager manager = MinecraftClient.getInstance().getTextureManager();
                Identifier id = Identifier.of("imgui", "custom/" + path.getFileName().toString().replace(".", "_"));

                manager.registerTexture(id, tex);

                int glId = tex.getGlId(); // For use with ImGui

                textureCache.put(path, new Pair<>(tex, glId));
                return glId;

            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static void clearCache() {
        for (Pair<NativeImageBackedTexture, Integer> pair : textureCache.values()) {
            pair.getLeft().close(); // deletes texture
        }
        textureCache.clear();
    }
}
