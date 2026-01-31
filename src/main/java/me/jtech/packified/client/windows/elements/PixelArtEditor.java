package me.jtech.packified.client.windows.elements;

import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import me.jtech.packified.Packified;
import me.jtech.packified.client.helpers.DisplayScaleHelper;
import me.jtech.packified.client.util.SafeTextureLoader;
import me.jtech.packified.client.windows.EditorWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import static org.lwjgl.opengl.GL11.*;

@Environment(EnvType.CLIENT)
public class PixelArtEditor {
    private BufferedImage image;
    private int textureId = -1;
    private float cameraX;
    private float cameraY;

    private float[] currentColor = {1.0f, 0.0f, 0.0f}; // Default color (red)
    private float[] currentAlpha = {1.0f}; // Default to fully opaque
    private float scale = 1.0f; // Initial zoom scale

    private final float MAX_SCALE = 64.0f;

    private final Stack<BufferedImage> undoStack = new Stack<>();
    private final Stack<BufferedImage> redoStack = new Stack<>();
    private boolean finalizedSelection = false;

    private enum Tool {
        PEN, PAINT_BUCKET, SELECT, ERASER
    }

    private enum SelectionMode {
        RECTANGLE, CIRCLE, LASSO, MAGIC_WAND
    }

    private SelectionMode selectionMode = SelectionMode.RECTANGLE;

    private Tool currentTool = Tool.PEN;
    private ImInt toolSize = new ImInt(1);

    private boolean isDrawing = false;
    private int lastPixelX = -1;
    private int lastPixelY = -1;

    private Path currentFile = null; // Current path to file being edited

    private float zoomSensitivity = 1.0f; // sensitivity for zooming
    private float panSensitivity = 10.0f; // sensitivity for panning

    private boolean panning = false;
    private float panStartX, panStartY;

    private ImInt magicWandTolerance = new ImInt(20); // Tolerance for flood fill color matching

    public boolean wasModified = false; // Track if the image was modified

    private boolean firstRender = true; // Track if this is the first render to clamp image position

    private Point selectionStart = null;
    private Point selectionEnd = null;

    private boolean textureDirty = false;
    private int dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY;

    // Load image and create OpenGL texture
    private NativeImageBackedTexture texture;

    public void loadImage(BufferedImage img, Path path) {
        currentFile = path;
        if (img == null) {
            System.err.println("Cannot load null image");
            return;
        }

        // Dispose previous GPU texture properly
        if (texture != null) {
            texture.close();
            texture = null;
            textureId = -1;
        }

        // Keep Java image copy for UI operations
        this.image = img;

        // Convert to TYPE_INT_ARGB if needed
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = converted.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            img = converted;
            this.image = img;
        }

        // Build a NativeImage and fill it once, then hand it to NativeImageBackedTexture
        NativeImage nativeImage = new NativeImage(img.getWidth(), img.getHeight(), true);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g2 = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g2 << 8) | r;
                nativeImage.setColor(x, y, abgr);
            }
        }

        // Construct texture (this will call createTexture(...) and upload() in the constructor)
        texture = new NativeImageBackedTexture(nativeImage::toString, nativeImage);

        // Record GL id for ImGui rendering (safe on render thread)
        RenderSystem.assertOnRenderThread();
        textureId = ((GlTexture) texture.getGlTexture()).getGlId();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        // Disable linear filtering (use nearest neighbor)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // Prevent tiling if you pan/zoom past edges
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0); // unbind for safety
    }

    public BufferedImage getImage() {
        return image;
    }

    public void dispose() {
        if (texture != null) {
            texture.close();
            texture = null;
            textureId = -1;
        }
    }

    ImInt createNewImageWidth = new ImInt(16);
    ImInt createNewImageHeight = new ImInt(16);

    // Render the image in an ImGui window and handle pixel editing
    public void render() {
        RenderSystem.assertOnRenderThread();
        if (image == null || textureId == -1) {
            //ImGui.begin("Pixel Art Editor");
            ImGui.text("Create new image:");
            ImGui.inputInt("Width", createNewImageWidth);
            ImGui.inputInt("Height", createNewImageHeight);
            if (ImGui.button("Create")) {
                BufferedImage newImage = new BufferedImage(createNewImageWidth.get(), createNewImageHeight.get(), BufferedImage.TYPE_INT_ARGB);
                loadImage(newImage, currentFile); // No path for new images
                firstRender = true; // Reset first render state
            }
            ImGui.end();
            return;
        }

        if (firstRender) {
            firstRender = false;
            scale = computeMinScale();
            cameraX = 0;
            cameraY = 0;
            clampImagePosition();
        }

        ImGuiIO io = ImGui.getIO();
        int width = image.getWidth();
        int height = image.getHeight();

        int buttonSize = DisplayScaleHelper.getUIButtonSize();
        
        // Tool Buttons
        if (currentTool == Tool.PEN) {
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, 0xFFFF0000);
        }
        ImGui.imageButton(SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_pencil.png")), buttonSize, buttonSize);
        if (currentTool == Tool.PEN) {
            ImGui.popStyleVar();
            ImGui.popStyleColor();
        }
        if (ImGui.isItemClicked()) {
            currentTool = Tool.PEN;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Pen Tool");
        }
        ImGui.sameLine();
        if (currentTool == Tool.PAINT_BUCKET) {
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, 0xFFFF0000);
        }
        ImGui.imageButton(SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_bucket.png")), buttonSize, buttonSize);
        if (currentTool == Tool.PAINT_BUCKET) {
            ImGui.popStyleVar();
            ImGui.popStyleColor();
        }
        if (ImGui.isItemClicked()) {
            currentTool = Tool.PAINT_BUCKET;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Paint Bucket Tool");
        }
        ImGui.sameLine();
        if (currentTool == Tool.SELECT) {
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, 0xFFFF0000);
        }
        ImGui.imageButton(SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_select.png")), buttonSize, buttonSize);
        if (currentTool == Tool.SELECT) {
            ImGui.popStyleVar();
            ImGui.popStyleColor();
        }
        if (ImGui.isItemClicked()) {
            currentTool = Tool.SELECT;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Selection Tool (This tool is still in development)");
        }
        if (ImGui.beginPopupContextItem("Selection Mode")) {
            for (SelectionMode mode : SelectionMode.values()) {
                if (ImGui.menuItem(mode.name(), null, mode == selectionMode)) {
                    selectionMode = mode;
                }
            }
            if (selectionMode == SelectionMode.MAGIC_WAND) {
                ImGui.separator();
                ImGui.inputInt("Tolerance", magicWandTolerance);
            }
            ImGui.endPopup();
        }
        ImGui.sameLine();
        if (currentTool == Tool.ERASER) {
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, 0xFFFF0000);
        }
        ImGui.imageButton(SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_eraser.png")), buttonSize, buttonSize);
        if (currentTool == Tool.ERASER) {
            ImGui.popStyleVar();
            ImGui.popStyleColor();
        }
        if (ImGui.isItemClicked()) {
            currentTool = Tool.ERASER;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Eraser Tool");
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(150);
        ImGui.inputInt("Tool Size", toolSize);
        ImGui.sameLine();
        ImGui.setNextItemWidth(350);
        ImGui.colorEdit3("Color Picker", currentColor);
        ImGui.sameLine();
        ImGui.setNextItemWidth(120);
        ImGui.sliderFloat("Opacity", currentAlpha, 0.0f, 1.0f);

        ImGui.beginChild("Canvas##", 0, 0, false, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        // Handle zoom with Ctrl + Scroll
        if (ImGui.isWindowHovered() && io.getMouseWheel() != 0.0f && io.getKeyCtrl()) {
            float oldScale = scale;
            float zoom = io.getMouseWheel() * zoomSensitivity;

            float minScale = computeMinScale();
            scale = Math.max(minScale, Math.min(MAX_SCALE, scale + zoom));
            float scaleRatio = scale / oldScale;

            float mouseX = io.getMousePosX();
            float mouseY = io.getMousePosY();

            float imageX = mouseX - ImGui.getItemRectMinX() - cameraX;
            float imageY = mouseY - ImGui.getItemRectMinY() - cameraY;

            cameraX -= imageX * (scaleRatio - 1.0f);
            cameraY -= imageY * (scaleRatio - 1.0f);

            clampImagePosition();
        }

        if (ImGui.isWindowHovered() && ImGui.isMouseDown(2)) { // middle mouse
            if (!panning) {
                panning = true;
                panStartX = io.getMousePosX();
                panStartY = io.getMousePosY();
            } else {
                float dx = io.getMousePosX() - panStartX;
                float dy = io.getMousePosY() - panStartY;

                cameraX += dx;
                cameraY += dy;

                panStartX = io.getMousePosX();
                panStartY = io.getMousePosY();

                clampImagePosition();
            }
        } else {
            panning = false;
        }

        ImVec2 canvasPos = new ImVec2(ImGui.getCursorPosX() + cameraX, ImGui.getCursorPosY() + cameraY);
        ImGui.setCursorPos(canvasPos.x, canvasPos.y);

        ImGui.image(textureId, width * scale, height * scale);

        renderPixelGuidelineGrid(canvasPos);

        renderBrushPreview();

        boolean mouseHovered = ImGui.isItemHovered();
        boolean leftMouseDown = ImGui.isMouseDown(0);

        if (mouseHovered && leftMouseDown) {
            float mouseX = io.getMousePosX();
            float mouseY = io.getMousePosY();

            float imageScreenX = ImGui.getItemRectMinX();
            float imageScreenY = ImGui.getItemRectMinY();

            float localX = (mouseX - imageScreenX) / scale;
            float localY = (mouseY - imageScreenY) / scale;

            int pixelX = (int) localX;
            int pixelY = (int) localY;

            if (!isDrawing) {
                saveState(); // Save for undo
                isDrawing = true;
                lastPixelX = pixelX;
                lastPixelY = pixelY;
            }

            if (currentTool == Tool.PEN || currentTool == Tool.ERASER) {
                drawInterpolatedLine(lastPixelX, lastPixelY, pixelX, pixelY);

                markDirtyRect(
                        Math.max(0, Math.min(lastPixelX, pixelX)),
                        Math.max(0, Math.min(lastPixelY, pixelY)),
                        Math.min(image.getWidth() - 1, Math.max(lastPixelX, pixelX)),
                        Math.min(image.getHeight() - 1, Math.max(lastPixelY, pixelY))
                );

                lastPixelX = pixelX;
                lastPixelY = pixelY;
            }
            if (currentTool == Tool.PAINT_BUCKET) {
                floodFill(pixelX, pixelY);
            }
        } else {
            isDrawing = false;
        }

        if (currentTool == Tool.SELECT) {
            if (selectionMode == SelectionMode.RECTANGLE) {
                //renderSelection();
                //handleSelection();
            }
        }

        // Handle undo/redo shortcuts
        if (io.getKeyCtrl() && ImGui.isKeyPressed('Z', false)) {
            undo();
        }
        if (io.getKeyCtrl() && ImGui.isKeyPressed('Y', false)) {
            redo();
        }

        ImGui.endChild();

        if (textureDirty) {
            texture.upload();
            textureDirty = false;
        }
    }

    private void renderPixelGuidelineGrid(ImVec2 canvasPos) {
        if (image == null || !EditorWindow.showGrid) return;

        int width = image.getWidth();
        int height = image.getHeight();

        // Draw vertical lines
        for (int x = 0; x < width; x += toolSize.get()) {
            ImGui.getWindowDrawList().addLine(
                    ImGui.getItemRectMinX() + x * scale - cameraX,
                    ImGui.getItemRectMinY(),
                    ImGui.getItemRectMinX() + x * scale - cameraX,
                    ImGui.getItemRectMinY() + height * scale,
                    setColorOpacity(0xFF000000, scaleLineOpacity()), 1.0f);
        }

        // Draw horizontal lines
        for (int y = 0; y < height; y += toolSize.get()) {
            ImGui.getWindowDrawList().addLine(
                    ImGui.getItemRectMinX(),
                    ImGui.getItemRectMinY() + y * scale - cameraY,
                    ImGui.getItemRectMinX() + width * scale,
                    ImGui.getItemRectMinY() + y * scale - cameraY,
                    setColorOpacity(0xFF000000, scaleLineOpacity()), 1.0f);
        }
    }

    private int scaleLineOpacity() {
        int max = 0xFF;
        int min = 0x00;
        int scaledOpacity = (int) (max * (1.05f - (scale / MAX_SCALE)));
        return Math.max(min, Math.min(max, scaledOpacity));
    }

    private int setColorOpacity(int color, float opacity) {
        int alpha = (int) (opacity * 255);
        return (alpha << 24) | (color & 0x00FFFFFF); // Set alpha while keeping RGB
    }

    private void clampImagePosition() {
        float imgW = image.getWidth() * scale;
        float imgH = image.getHeight() * scale;

        float winW = ImGui.getWindowWidth();
        float winH = ImGui.getWindowHeight();

        if (imgW <= winW) {
            cameraX = (winW - imgW) * 0.5f;
        } else {
            cameraX = Math.min(0, Math.max(winW - imgW, cameraX));
        }

        if (imgH <= winH) {
            cameraY = (winH - imgH) * 0.5f;
        } else {
            cameraY = Math.min(0, Math.max(winH - imgH, cameraY));
        }
    }

    private void renderSelection() {
        if (selectionStart != null && selectionEnd != null) {
            int x1 = Math.min(selectionStart.x, selectionEnd.x);
            int y1 = Math.min(selectionStart.y, selectionEnd.y);
            int x2 = Math.max(selectionStart.x, selectionEnd.x);
            int y2 = Math.max(selectionStart.y, selectionEnd.y);

            ImGui.getWindowDrawList().addRect(
                    ImGui.getItemRectMinX() + x1 * scale - cameraX,
                    ImGui.getItemRectMinY() + y1 * scale - cameraY,
                    ImGui.getItemRectMinX() + x2 * scale - cameraX,
                    ImGui.getItemRectMinY() + y2 * scale - cameraY,
                    0xFF00FF00, // Green color
                    0.0f,       // No rounding
                    0           // Thickness
            );
        }
    }

    private void handleSelection() {
        ImGuiIO io = ImGui.getIO();
        boolean mouseHovered = ImGui.isItemHovered();
        boolean leftMouseDown = ImGui.isMouseDown(0);

        if (mouseHovered && leftMouseDown) {
            if (finalizedSelection) {
                selectionStart = null;
                selectionEnd = null;
            }
            float mouseX = io.getMousePosX();
            float mouseY = io.getMousePosY();

            float imageScreenX = ImGui.getItemRectMinX();
            float imageScreenY = ImGui.getItemRectMinY();

            float localX = (mouseX - imageScreenX) / scale;
            float localY = (mouseY - imageScreenY) / scale;

            int pixelX = (int) localX;
            int pixelY = (int) localY;

            if (selectionStart == null) {
                selectionStart = new Point(pixelX, pixelY);
            }
            selectionEnd = new Point(pixelX, pixelY);
        } else if (!leftMouseDown) {
            finalizedSelection = true;
        }

        if (ImGui.getIO().getKeysDown(GLFW.GLFW_KEY_DELETE)) {
            if (selectionStart != null && selectionEnd != null) {
                deleteSelection();
                selectionStart = null;
                selectionEnd = null;
            }
        }
    }

    private void deleteSelection() {
        int x1 = Math.min(selectionStart.x, selectionEnd.x);
        int y1 = Math.min(selectionStart.y, selectionEnd.y);
        int x2 = Math.max(selectionStart.x, selectionEnd.x);
        int y2 = Math.max(selectionStart.y, selectionEnd.y);

        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                    image.setRGB(x, y, 0x00000000);
                    markDirty(x, y);
                }
            }
        }
    }

    private void drawInterpolatedLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            drawBrush(x0, y0);

            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    // Flood fill: modify BufferedImage and nativeImage, then upload once at the end
    private void floodFill(int startX, int startY) {
        if (image == null || texture == null) return;
        RenderSystem.assertOnRenderThread();

        int width = image.getWidth();
        int height = image.getHeight();

        int targetColor = image.getRGB(startX, startY);
        int alpha = (int) (currentAlpha[0] * 255);
        int fillColor = (alpha << 24) | ((int) (currentColor[0] * 255) << 16) |
                ((int) (currentColor[1] * 255) << 8) | (int) (currentColor[2] * 255);

        if (targetColor == fillColor) return;

        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(startX, startY));

        NativeImage nativeImage = texture.getImage();
        if (nativeImage == null) {
            while (!queue.isEmpty()) {
                Point p = queue.remove();
                int x = p.x, y = p.y;
                if (x < 0 || x >= width || y < 0 || y >= height) continue;
                if (!colorsMatch(image.getRGB(x, y), targetColor, magicWandTolerance.get())) continue;
                image.setRGB(x, y, fillColor);
                markDirty(x, y);

                queue.add(new Point(x + 1, y));
                queue.add(new Point(x - 1, y));
                queue.add(new Point(x, y + 1));
                queue.add(new Point(x, y - 1));
            }
            return;
        }

        while (!queue.isEmpty()) {
            Point p = queue.remove();
            int x = p.x, y = p.y;
            if (x < 0 || x >= width || y < 0 || y >= height) continue;
            if (!colorsMatch(image.getRGB(x, y), targetColor, magicWandTolerance.get())) continue;

            image.setRGB(x, y, fillColor);

            int a = (fillColor >> 24) & 0xFF;
            int r = (fillColor >> 16) & 0xFF;
            int g2 = (fillColor >> 8) & 0xFF;
            int b = fillColor & 0xFF;
            int abgr = (a << 24) | (b << 16) | (g2 << 8) | r;
            nativeImage.setColor(x, y, abgr);
            markDirty(x, y);

            queue.add(new Point(x + 1, y));
            queue.add(new Point(x - 1, y));
            queue.add(new Point(x, y + 1));
            queue.add(new Point(x, y - 1));
        }
    }


    private static boolean colorsMatch(int colorA, int colorB, int tolerance) {
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        return Math.abs(rA - rB) <= tolerance &&
                Math.abs(gA - gB) <= tolerance &&
                Math.abs(bA - bB) <= tolerance;
    }

    // When painting (brush) — batch changes locally into image and into the NativeImage, then upload once.
    private void drawBrush(int centerX, int centerY) {
        if (image == null || texture == null) return;
        RenderSystem.assertOnRenderThread(); // ensure we are on render thread when touching texture.getImage() and upload()

        int halfSize = toolSize.get() / 2;
        int width = image.getWidth();
        int height = image.getHeight();

        int alphaByte = (int) (currentAlpha[0] * 255);
        int drawColor = (currentTool == Tool.ERASER) ? 0x00000000 :
                (alphaByte << 24) | ((int) (currentColor[0] * 255) << 16) |
                        ((int) (currentColor[1] * 255) << 8) |
                        (int) (currentColor[2] * 255);

        NativeImage nativeImage = texture.getImage();
        if (nativeImage == null) {
            // fallback: change BufferedImage and call updateTexture later
            for (int y = centerY - halfSize; y <= centerY + halfSize; y++) {
                for (int x = centerX - halfSize; x <= centerX + halfSize; x++) {
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        image.setRGB(x, y, drawColor);
                        markDirty(x, y);
                    }
                }
            }
            return;
        }

        // Modify both the BufferedImage model and the native image in memory
        for (int y = centerY - halfSize; y <= centerY + halfSize; y++) {
            for (int x = centerX - halfSize; x <= centerX + halfSize; x++) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    // set Java model for undo / saving
                    int existingColor = image.getRGB(x, y);
                    int blendedColor = getBlendedColor(existingColor, drawColor, alphaByte, currentAlpha);
                    image.setRGB(x, y, blendedColor);

                    // Compose abgr for native image
                    int a = (blendedColor >> 24) & 0xFF;
                    int r = (blendedColor >> 16) & 0xFF;
                    int g2 = (blendedColor >> 8) & 0xFF;
                    int b = blendedColor & 0xFF;
                    int abgr = (a << 24) | (b << 16) | (g2 << 8) | r;

                    nativeImage.setColor(x, y, abgr);
                    markDirty(x, y);
                }
            }
        }
    }


    private static int getBlendedColor(int existingColor, int drawColor, int alpha, float[] currentAlpha) {
        int existingAlpha = (existingColor >> 24) & 0xFF;
        int existingRed = (existingColor >> 16) & 0xFF;
        int existingGreen = (existingColor >> 8) & 0xFF;
        int existingBlue = existingColor & 0xFF;

        int red = (drawColor >> 16) & 0xFF;
        int green = (drawColor >> 8) & 0xFF;
        int blue = drawColor & 0xFF;

        float alphaFactor = currentAlpha[0];
        float inverseAlpha = 1.0f - alphaFactor;

        int blendedRed = (int) (red * alphaFactor + existingRed * inverseAlpha);
        int blendedGreen = (int) (green * alphaFactor + existingGreen * inverseAlpha);
        int blendedBlue = (int) (blue * alphaFactor + existingBlue * inverseAlpha);
        float aSrc = alpha / 255f;
        float aDst = existingAlpha / 255f;
        int blendedAlpha = (int)((aSrc + aDst * (1 - aSrc)) * 255);

        return (blendedAlpha << 24) | (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
    }

    private void saveState() {
        if (image == null) return;

        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        // Correct way to copy pixel data
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                copy.setRGB(x, y, image.getRGB(x, y));
            }
        }

        undoStack.push(copy);
        redoStack.clear();

        wasModified = true; // Mark as modified
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(copyImage(image));
            image = undoStack.pop();
            rebuildNativeImageFromBuffered();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(copyImage(image));
            image = redoStack.pop();
            rebuildNativeImageFromBuffered();
        }
    }

    private static BufferedImage copyImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Fast and safe way to copy pixel data
        int[] pixels = new int[width * height];
        source.getRGB(0, 0, width, height, pixels, 0, width);
        copy.setRGB(0, 0, width, height, pixels, 0, width);

        return copy;
    }

    private static void saveImage(BufferedImage image, Path path) {
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void markDirty(int x, int y) {
        if (!textureDirty) {
            dirtyMinX = dirtyMaxX = x;
            dirtyMinY = dirtyMaxY = y;
            textureDirty = true;
        } else {
            dirtyMinX = Math.min(dirtyMinX, x);
            dirtyMinY = Math.min(dirtyMinY, y);
            dirtyMaxX = Math.max(dirtyMaxX, x);
            dirtyMaxY = Math.max(dirtyMaxY, y);
        }
    }

    private void markDirtyRect(int x0, int y0, int x1, int y1) {
        if (!textureDirty) {
            dirtyMinX = x0;
            dirtyMinY = y0;
            dirtyMaxX = x1;
            dirtyMaxY = y1;
            textureDirty = true;
        } else {
            dirtyMinX = Math.min(dirtyMinX, x0);
            dirtyMinY = Math.min(dirtyMinY, y0);
            dirtyMaxX = Math.max(dirtyMaxX, x1);
            dirtyMaxY = Math.max(dirtyMaxY, y1);
        }
    }

    private boolean getMousePixel(ImVec2 outPixel) {
        ImGuiIO io = ImGui.getIO();

        if (!ImGui.isItemHovered()) return false;

        float imageScreenX = ImGui.getItemRectMinX();
        float imageScreenY = ImGui.getItemRectMinY();

        float localX = (io.getMousePosX() - imageScreenX) / scale;
        float localY = (io.getMousePosY() - imageScreenY) / scale;

        int px = (int) Math.floor(localX);
        int py = (int) Math.floor(localY);

        if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight())
            return false;

        outPixel.x = px;
        outPixel.y = py;
        return true;
    }

    private void renderBrushPreview() {
        if (currentTool != Tool.PEN && currentTool != Tool.ERASER) return;

        ImVec2 pixel = new ImVec2();
        if (!getMousePixel(pixel)) return;

        int half = toolSize.get() / 2;

        float x0 = ImGui.getItemRectMinX() + (pixel.x - half) * scale;
        float y0 = ImGui.getItemRectMinY() + (pixel.y - half) * scale;
        float x1 = ImGui.getItemRectMinX() + (pixel.x + half + 1) * scale;
        float y1 = ImGui.getItemRectMinY() + (pixel.y + half + 1) * scale;

        int color = (currentTool == Tool.ERASER)
                ? 0x80FF0000   // semi-transparent red
                : 0x8000FF00;  // semi-transparent green

        ImGui.getWindowDrawList().addRect(
                x0, y0, x1, y1,
                color,
                0.0f,
                0,
                2.0f
        );
    }

    private void rebuildNativeImageFromBuffered() {
        NativeImage nativeImage = texture.getImage();
        if (nativeImage == null) return;

        int w = image.getWidth();
        int h = image.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setColor(x, y, abgr);
            }
        }

        markDirtyRect(0, 0, w - 1, h - 1);
    }

    private float computeMinScale() {
        float winW = ImGui.getWindowWidth();
        float winH = ImGui.getWindowHeight();

        float scaleX = winW / image.getWidth();
        float scaleY = winH / image.getHeight();

        return Math.min(scaleX, scaleY);
    }
}