package me.jtech.packified.client.util;

import com.mojang.blaze3d.textures.GpuTexture;
import me.jtech.packified.Packified;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL11.GL_CLAMP;

@Environment(EnvType.CLIENT)
public class SafeTextureLoader {

    private static final Map<Path, Pair<NativeImageBackedTexture, Integer>> textureCache = new HashMap<>();

    public static void garbageCollect() {
        for (Map.Entry<Path, Pair<NativeImageBackedTexture, Integer>> entry : new HashMap<>(textureCache).entrySet()) {
            GpuTexture gpu;
            try {
                gpu = entry.getValue().getLeft().getGlTexture();
            } catch (IllegalStateException e) {
                textureCache.remove(entry.getKey());
                continue;
            }
            if (gpu.isClosed()) {
                textureCache.remove(entry.getKey());
            }
        }
    }

    public static int load(Path path) {
        return load(path, false);
    }

    public static int load(Path path, boolean invertColors) {
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
                        int abgr = getAbgr(invertColors, argb);

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
                Packified.LOGGER.error("Error loading texture from path: {}", path, e);
                return -1;
            }

        } catch (IOException e) {
            Packified.LOGGER.error("IO Error loading texture from path: {}", path, e);
            return -1;
        }
    }

    public static int loadFromIdentifier(Identifier id) {
        return loadFromIdentifier(id, false);
    }

    public static int loadFromIdentifier(Identifier id, boolean invertColors) {
        if (id == null) return -1;

        if (textureCache.containsKey(Path.of(id.toTranslationKey()))) {
            return textureCache.get(Path.of(id.toTranslationKey())).getRight(); // Return cached textureId
        }

        try {
            if (MinecraftClient.getInstance().getResourceManager().getResource(id).isEmpty()) {
                System.err.println("Resource not found: " + id);
                return -1;
            }
            BufferedImage img = ImageIO.read(MinecraftClient.getInstance().getResourceManager().getResource(id).get().getInputStream());
            if (img == null) {
                System.err.println("Failed to load image: " + id);
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
                        int abgr = getAbgr(invertColors, argb);

                        nativeImage.setColor(x, y, abgr);
                    }
                }

                NativeImageBackedTexture tex = new NativeImageBackedTexture(nativeImage::toString, nativeImage);
                TextureManager manager = MinecraftClient.getInstance().getTextureManager();

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

                textureCache.put(Path.of(id.toTranslationKey()), new Pair<>(tex, glId));
                return glId;

            } catch (Exception e) {
                Packified.LOGGER.error("Error loading texture from identifier: {}", id, e);
                return -1;
            }

        } catch (IOException e) {
            Packified.LOGGER.error("IO Error loading texture from identifier: {}", id, e);
            return -1;
        }
    }

    private static int getAbgr(boolean invertColors, int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int abgr = (a << 24) | (b << 16) | (g << 8) | r; // Convert to ABGR format for OpenGL

        if (invertColors) {
            r = 255 - r;
            g = 255 - g;
            b = 255 - b;
            abgr = (a << 24) | (b << 16) | (g << 8) | r;
        }
        return abgr;
    }

    public static void clearCache() {
        for (Pair<NativeImageBackedTexture, Integer> pair : textureCache.values()) {
            pair.getLeft().close(); // deletes texture
        }
        textureCache.clear();
    }
}
