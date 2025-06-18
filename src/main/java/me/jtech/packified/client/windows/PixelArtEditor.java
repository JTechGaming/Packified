package me.jtech.packified.client.windows;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import org.lwjgl.BufferUtils;

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

public class PixelArtEditor {
    private BufferedImage image;
    private int textureId = -1;
    private float imagePosX;
    private float imagePosY;

    private float[] currentColor = {1.0f, 0.0f, 0.0f}; // Default color (red)
    private float[] currentAlpha = {1.0f}; // Default to fully opaque
    private float scale = 1.0f; // Initial zoom scale

    private final float MIN_SCALE = 2.0f;
    private final float MAX_SCALE = 64.0f;

    private final Stack<BufferedImage> undoStack = new Stack<>();
    private final Stack<BufferedImage> redoStack = new Stack<>();

    private enum Tool {
        PEN, PAINT_BUCKET, SELECT, ERASER
    }

    private Tool currentTool = Tool.PEN;
    private ImInt toolSize = new ImInt(1);

    private boolean isDrawing = false;
    private int lastPixelX = -1;
    private int lastPixelY = -1;

    private Path currentFile = null; // Current path to file being edited

    private float zoomSensitivity = 1.0f; // Adjust sensitivity for zooming
    private float panSensitivity = 5.0f; // Adjust sensitivity for panning

    private ImInt floodFillTolerance = new ImInt(20); // Tolerance for flood fill color matching

    public boolean wasModified = false; // Track if the image was modified

    // Load image and create OpenGL texture
    public void loadImage(BufferedImage img, Path path) {
        currentFile = path;
        if (textureId != -1) {
            glDeleteTextures(textureId);
        }

        image = img;

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        updateTexture();
    }

    private void updateTexture() {
        if (image == null || textureId == -1) return;

        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                buffer.put((byte) (pixel & 0xFF));         // Blue
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
            }
        }
        buffer.flip();

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    // Render the image in an ImGui window and handle pixel editing
    public void render() {
        if (image == null || textureId == -1) {
            //ImGui.begin("Pixel Art Editor");
            ImGui.text("No image loaded.");
            ImGui.end();
            return;
        }

        //ImGui.begin("Pixel Art Editor", ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        ImGuiIO io = ImGui.getIO();
        int width = image.getWidth();
        int height = image.getHeight();

        // Handle zoom with Ctrl + Scroll
        if (ImGui.isWindowHovered()) {
            float scrollDelta = io.getMouseWheel();
            if (scrollDelta != 0.0f) {
                if (io.getKeyCtrl()) {
                    scale += scrollDelta * zoomSensitivity; // Adjust sensitivity as needed
                    if (scale < MIN_SCALE) scale = MIN_SCALE;
                    if (scale > MAX_SCALE) scale = MAX_SCALE;
                } else if (io.getKeyShift()) {
                    // Adjust image position with Shift + Scroll
                    imagePosX -= scrollDelta * panSensitivity; // Adjust sensitivity as needed
                } else {
                    // Adjust image position with regular scroll
                    imagePosY -= scrollDelta * panSensitivity; // Adjust sensitivity as needed
                }
            }
        }

        // Tool Buttons
        ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_pencil.png"), 14, 14);
        if (ImGui.isItemClicked()) {
            currentTool = Tool.PEN;
        }
        ImGui.sameLine();
        ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_bucket.png"), 14, 14);
        if (ImGui.isItemClicked()) {
            currentTool = Tool.PAINT_BUCKET;
        }
        if (ImGui.beginPopupContextItem("Floodfill Settings")) {
            ImGui.inputInt("Tolerance", floodFillTolerance);
            ImGui.endPopup();
        }
        ImGui.sameLine();
        ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_select.png"), 14, 14);
        if (ImGui.isItemClicked()) {
            currentTool = Tool.SELECT;
        }
        ImGui.sameLine();
        ImGui.imageButton(ImGuiImplementation.loadTexture("textures/ui/neu_eraser.png"), 14, 14);
        if (ImGui.isItemClicked()) {
            currentTool = Tool.ERASER;
        }
        ImGui.sameLine();
        ImGui.inputInt("Tool Size", toolSize);
        ImGui.colorEdit3("Color Picker", currentColor);
        ImGui.sliderFloat("Opacity", currentAlpha, 0.0f, 1.0f);

        if (ImGui.button("Save Image")) {
            saveImage(image, currentFile);
        }
        ImGui.sameLine();
        if (ImGui.button("Undo (Ctrl+Z)")) {
            undo();
        }
        ImGui.sameLine();
        if (ImGui.button("Redo (Ctrl+Y)")) {
            redo();
        }

        ImGui.image(textureId, width * scale, height * scale);

        imagePosX = ImGui.getItemRectMinX();
        imagePosY = ImGui.getItemRectMinY();

        boolean mouseHovered = ImGui.isItemHovered();
        boolean leftMouseDown = ImGui.isMouseDown(0);

        if (mouseHovered && leftMouseDown) {
            float mouseX = io.getMousePosX();
            float mouseY = io.getMousePosY();

            float localX = (mouseX - imagePosX) / scale;
            float localY = (mouseY - imagePosY) / scale;

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
                lastPixelX = pixelX;
                lastPixelY = pixelY;
                updateTexture();
            }
            if (currentTool == Tool.PAINT_BUCKET) {
                floodFill(pixelX, pixelY);
            }
        } else {
            isDrawing = false;
        }

        // Handle undo/redo shortcuts
        if (io.getKeyCtrl() && ImGui.isKeyPressed('Z', false)) {
            undo();
        }
        if (io.getKeyCtrl() && ImGui.isKeyPressed('Y', false)) {
            redo();
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

    private void floodFill(int startX, int startY) {
        int targetColor = image.getRGB(startX, startY);
        int alpha = (int) (currentAlpha[0] * 255);
        int fillColor = (alpha << 24) | ((int) (currentColor[0] * 255) << 16) |
                ((int) (currentColor[1] * 255) << 8) | (int) (currentColor[2] * 255);

        if (targetColor == fillColor) return;

        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(startX, startY));

        int width = image.getWidth();
        int height = image.getHeight();

        while (!queue.isEmpty()) {
            Point p = queue.remove();
            int x = p.x;
            int y = p.y;

            if (x < 0 || x >= width || y < 0 || y >= height) continue;
            if (!colorsMatch(image.getRGB(x, y), targetColor, floodFillTolerance.get())) continue; // Allow 10 tolerance

            image.setRGB(x, y, fillColor);

            // Update texture on GPU
            ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(4);
            pixelBuffer.put((byte) ((fillColor >> 16) & 0xFF));
            pixelBuffer.put((byte) ((fillColor >> 8) & 0xFF));
            pixelBuffer.put((byte) (fillColor & 0xFF));
            pixelBuffer.put((byte) ((fillColor >> 24) & 0xFF));
            pixelBuffer.flip();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);

            // Add neighboring pixels
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

    private void drawBrush(int centerX, int centerY) {
        int halfSize = toolSize.get() / 2;
        int width = image.getWidth();
        int height = image.getHeight();

        int alpha = (int) (currentAlpha[0] * 255);

        int drawColor = (currentTool == Tool.ERASER) ? 0x00000000 :
                (alpha << 24) | ((int) (currentColor[0] * 255) << 16) |
                        ((int) (currentColor[1] * 255) << 8) |
                        (int) (currentColor[2] * 255);

        for (int y = centerY - halfSize; y <= centerY + halfSize; y++) {
            for (int x = centerX - halfSize; x <= centerX + halfSize; x++) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    int existingColor = image.getRGB(x, y);
                    int blendedColor = getBlendedColor(existingColor, drawColor, alpha, currentAlpha);

                    image.setRGB(x, y, blendedColor);

                    ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(4);
                    pixelBuffer.put((byte) ((drawColor >> 16) & 0xFF));
                    pixelBuffer.put((byte) ((drawColor >> 8) & 0xFF));
                    pixelBuffer.put((byte) (drawColor & 0xFF));
                    pixelBuffer.put((byte) ((drawColor >> 24) & 0xFF));
                    pixelBuffer.flip();

                    glBindTexture(GL_TEXTURE_2D, textureId);
                    glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);
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
        int blendedAlpha = Math.min(255, (int) (alpha + existingAlpha * inverseAlpha));

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
            updateTexture();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(copyImage(image));
            image = redoStack.pop();
            updateTexture();
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
}