package me.jtech.packified.client.util;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.texture.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL11.GL_CLAMP;

public class SafeTextureLoader {

    private static final Map<Path, Pair<NativeImageBackedTexture, Integer>> textureCache = new HashMap<>();

    public static void garbageCollect() {
        for (Map.Entry<Path, Pair<NativeImageBackedTexture, Integer>> entry : textureCache.entrySet()) {
            GpuTexture gpu = entry.getValue().getLeft().getGlTexture();
            if (gpu.isClosed()) {
                textureCache.remove(entry.getKey());
            }
        }
    }

    public static int load(Path path) {
        if (path == null) return -1;

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

                NativeImageBackedTexture tex = new NativeImageBackedTexture(nativeImage::toString, nativeImage);
                TextureManager manager = MinecraftClient.getInstance().getTextureManager();
                Identifier id = Identifier.of("imgui", "custom/" + path.getFileName().toString().replace(".", "_"));

                manager.registerTexture(id, tex);

                int glId = ((GlTexture) tex.getGlTexture()).getGlId();

                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);

                // Disable linear filtering
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

                // Prevent tiling if panned/zoomed past edges
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL_CLAMP);

                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

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
