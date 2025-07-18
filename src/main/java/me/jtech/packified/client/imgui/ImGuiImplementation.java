package me.jtech.packified.client.imgui;

import com.mojang.blaze3d.platform.GlStateManager;
import imgui.*;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.internal.ImGuiContext;
import me.jtech.packified.Packified;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import me.jtech.packified.client.helpers.NotificationHelper;
import me.jtech.packified.client.windows.elements.MenuBar;
import me.jtech.packified.client.util.IniUtil;
import me.jtech.packified.client.helpers.TutorialHelper;
import me.jtech.packified.client.windows.*;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import me.jtech.packified.client.windows.popups.SelectFolderWindow;
import me.jtech.packified.client.windows.popups.SelectPackWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.util.Window;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static org.lwjgl.glfw.GLFW.*;

@Environment(EnvType.CLIENT)
@ApiStatus.Internal
public class ImGuiImplementation {
    public final static CustomImGuiImplGlfw imGuiImplGlfw = new CustomImGuiImplGlfw();
    private final static CustomImGuiImplGl3 imGuiImplGl3 = new CustomImGuiImplGl3();
    public static boolean initialized = false;

    private static int frameX = 0;
    private static int frameY = 0;
    private static int frameWidth = 1;
    private static int frameHeight = 1;
    private static int viewportSizeX = 1;
    private static int viewportSizeY = 1;

    public static int oldFrameWidth = 1;
    public static int oldFrameHeight = 1;

    public static boolean shouldRender = false;

    private static ImGuiContext imGuiContext = null;

    private static boolean activeLastFrame = false;

    public static float aspectRatio = 1;

    public static boolean grabbed = false;

    public static List<ImFont> loadedFonts = new ArrayList<>();
    public static List<String> loadedFontNames = new ArrayList<>();
    public static ImFont currentFont = null;

    public static void create(final long handle) {
        if (initialized) {
            throw new IllegalStateException("Imlib initialized twice");
        }
        initialized = true;
        long oldImGuiContext = -1;
        if (ImGui.getCurrentContext().isValidPtr()) oldImGuiContext = ImGui.getCurrentContext().ptr;
        imGuiContext = new ImGuiContext(ImGui.createContext().ptr);
        ImGui.setCurrentContext(imGuiContext);

        ImGui.createContext();
        ImPlot.createContext();

        IniUtil.setupIni();

        final ImGuiIO data = ImGui.getIO();
        data.setIniFilename(Packified.MOD_ID + ".ini");
        data.setFontGlobalScale(1F);

        ImguiThemes.setDeepDarkTheme();

        // font initialization
        float sizeScalar = 1.5f; // Render a higher quality font texture (for sizing)

        loadedFonts.add(ImGui.getFont());
        loadedFontNames.add("Default Font");
        currentFont = ImGui.getFont();

        for (Identifier identifier : findFonts()) {
            final ImFontConfig fontConfig = new ImFontConfig();

            String name = buildFontDisplayName(identifier);

            fontConfig.setName(name);
            fontConfig.setGlyphOffset(0, 0);

            short[] glyphRanges = new short[] {
                    0x0020, 0x00FF, // Basic Latin + Latin-1 Supplement
                    0x0000 // End of ranges
            };
            ImFont font = data.getFonts().addFontFromMemoryTTF(
                    loadFont(identifier.getPath().replace("fonts/", "")),
                    16 * sizeScalar,
                    fontConfig,
                    glyphRanges
            );
            font.setScale(1 / sizeScalar);
            loadedFonts.add(font);
            loadedFontNames.add(name);

            fontConfig.destroy();
        }

        data.getFonts().build();

        ImGui.getIO().setFontGlobalScale(PreferencesWindow.fontSize.get() / 14.0f);
        currentFont = loadedFonts.get(PreferencesWindow.selectedFont.get());

        data.setConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.ViewportsEnable);
        data.setConfigMacOSXBehaviors(MinecraftClient.IS_SYSTEM_MAC);

        imGuiImplGlfw.init(handle, true);
        imGuiImplGl3.init();

        ImGuiContext currentContext = ImGui.getCurrentContext();
        if (oldImGuiContext != -1) currentContext.ptr = oldImGuiContext;
        ImGui.setCurrentContext(currentContext);
    }

    private static List<Identifier> findFonts() {
        Predicate<Identifier> fontFilter = id -> id.getPath().startsWith("fonts/") && (id.getPath().endsWith(".ttf") || id.getPath().endsWith(".otf"));
        return MinecraftClient.getInstance().getResourceManager().findResources("fonts", fontFilter
        ).keySet().stream().toList();
    }

    private static byte[] loadFont(String name) {
        try {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(Packified.identifier("fonts/" + name));
            if (resource.isEmpty()) throw new MissingResourceException("Missing font: " + name, "Font", "");
            try (InputStream is = resource.get().getInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String buildFontDisplayName(Identifier identifier) {
        String baseName = identifier.getPath()
                .replace("fonts/", "")
                .replace(".ttf", "")
                .replace(".otf", "");
        String[] parts = baseName.split("-");
        StringBuilder displayName = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (i > 0) {
                displayName.append(" (");
            }
            // Capitalize the first letter and make the rest lowercase
            displayName.append(part.substring(0, 1).toUpperCase());
            if (part.length() > 1) {
                displayName.append(part.substring(1).toLowerCase());
            }
            if (i > 0) {
                displayName.append(")");
            }
        }
        return displayName.toString().trim();
    }

    public static void draw() {
        long oldImGuiContext = ImGui.getCurrentContext().ptr;
        ImGui.setCurrentContext(imGuiContext);

        try {
            drawInternal();
        } finally {
            ImGuiContext currentContext = ImGui.getCurrentContext();
            currentContext.ptr = oldImGuiContext;
            ImGui.setCurrentContext(currentContext);
        }
    }

    private static void drawInternal() {
        int oldFrameX = frameX;
        int oldFrameY = frameY;
        int oldFrameWidth = frameWidth;
        int oldFrameHeight = frameHeight;

        if (!initialized) {
            throw new IllegalStateException("Tried to use Imgui while it was not initialized");
        }

        if (!isActiveInternal()) {
            transitionActiveState(false);
            return;
        }

        // start frame
        imGuiImplGl3.newFrame();
        imGuiImplGlfw.newFrame(); // Handle keyboard and mouse interactions
        ImGui.newFrame();

        ImGui.pushFont(currentFont);

        // do rendering logic
        MenuBar.render();

        if (!ImGuiImplementation.isActiveInternal()) {
            ImGui.render();

            ImGuiImplementation.transitionActiveState(false);
            return;
        }

        // Setup docking
        ImGui.setNextWindowBgAlpha(0);
        int mainDock = ImGui.dockSpaceOverViewport(ImGui.getMainViewport(), ImGuiDockNodeFlags.NoDockingInCentralNode);
        imgui.internal.ImGui.dockBuilderGetCentralNode(mainDock).addLocalFlags(imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar);

        ImGui.setNextWindowDockID(mainDock);

        if (ImGui.begin("Main", ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoNavInputs | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoSavedSettings)) {
            float minX = ImGui.getWindowContentRegionMinX();
            float maxX = ImGui.getWindowContentRegionMaxX();
            float minY = ImGui.getWindowContentRegionMinY();
            float maxY = ImGui.getWindowContentRegionMaxY();

            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                frameX = (int) (ImGui.getWindowPosX() - ImGui.getWindowViewport().getPosX() + minX);
                frameY = (int) (ImGui.getWindowPosY() - ImGui.getWindowViewport().getPosY() + minY);
            } else {
                frameX = (int) (ImGui.getWindowPosX() + minX);
                frameY = (int) (ImGui.getWindowPosY() + minY);
            }
            frameWidth = (int) Math.max(1, maxX - minX);
            frameHeight = (int) Math.max(1, maxY - minY);
            viewportSizeX = (int) ImGui.getMainViewport().getSizeX();
            viewportSizeY = (int) ImGui.getMainViewport().getSizeY();

            float aspectRatio = ImGui.getMainViewport().getSizeX() / ImGui.getMainViewport().getSizeY();

            float currentAspectRatio = frameWidth / (float) frameHeight;

            if (currentAspectRatio < aspectRatio) {
                int newHeight = (int) (frameWidth / aspectRatio);
                frameY += (frameHeight - newHeight) / 2;
                frameHeight = newHeight;
            } else if (currentAspectRatio > aspectRatio) {
                int newWidth = (int) (frameHeight * aspectRatio);
                frameX += (frameWidth - newWidth) / 2;
                frameWidth = newWidth;
            }

            ImGuiImplementation.setFrameWidth(frameWidth);
            ImGuiImplementation.setFrameHeight(frameHeight);
            ImGuiImplementation.setViewportSizeX(viewportSizeX);
            ImGuiImplementation.setViewportSizeY(viewportSizeY);
            ImGuiImplementation.setFrameX(frameX);
            ImGuiImplementation.setFrameY(frameY);

            if (ImGui.isWindowHovered() && ImGui.isMouseClicked(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                // If the main window is clicked, we grab the mouse
                MinecraftClient client = MinecraftClient.getInstance();
                GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                grabbed = true;
                client.mouse.lockCursor();
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (!client.mouse.wasRightButtonClicked()) {
                if (grabbed) {
                    grabbed = false;
                    client.mouse.unlockCursor();
                    GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                }
            } else {
                if (MinecraftClient.getInstance().currentScreen != null) {
                    GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                } else {
                    GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                }

                ImGui.setWindowFocus("Main");
                ImGui.getIO().setWantCaptureKeyboard(false);
                ImGui.getIO().setWantCaptureMouse(false);
            }
        }

        ImGui.end();

        // Window rendering
        LogWindow.render();
        EditorWindow.render();
        FileHierarchy.render();
        BackupWindow.render();
        SelectPackWindow.render();
        MultiplayerWindow.render();
        SelectFolderWindow.render();
        ModifyFileWindow.render();
        ConfirmWindow.render();
        NotificationHelper.render();
        CornerNotificationsHelper.render();
        PreferencesWindow.render();
        PackCreationWindow.render();
        AssetInspectorWindow.render();
        //ModelEditorWindow.show(new ImBoolean(true));

        TutorialHelper.render();

        ImGui.popFont();

        // end frame
        ImGui.render();
        imGuiImplGl3.renderDrawData(ImGui.getDrawData());

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final long pointer = GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();

            GLFW.glfwMakeContextCurrent(pointer);
        }

        transitionActiveState(true);
    }

    @ApiStatus.Internal
    public static void dispose() {
        clearTextureCache();
        FileHierarchy.clearCache();

        imGuiImplGl3.shutdown();

        ImGui.destroyContext();
        ImPlot.destroyContext(ImPlot.getCurrentContext());
    }

    private static final Map<String, Integer> textureCache = new HashMap<>();

    public static int loadTextureFromOwnIdentifier(String identifierPath) {
        // Check if the texture is already cached
        if (textureCache.containsKey(identifierPath)) {
            return textureCache.get(identifierPath);
        }

        try {
            Resource resource = MinecraftClient.getInstance().getResourceManager()
                    .getResource(Identifier.of(Packified.MOD_ID, identifierPath)).get();
            BufferedImage image = ImageIO.read(resource.getInputStream());
            int textureId = fromBufferedImage(image);

            // Cache the texture ID
            if (textureId != -1) {
                textureCache.put(identifierPath, textureId);
            }

            return textureId;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static void clearTextureCache() {
        // Clear cached textures
        textureCache.forEach((key, textureId) -> GlStateManager._deleteTexture(textureId));
        textureCache.clear();
    }

    public static BufferedImage bufferedImageFromIdentifier(Identifier identifier) {
        try {
            if (MinecraftClient.getInstance().getResourceManager().getResource(identifier).isEmpty()) {
                return null; // Resource not found
            }
            Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(identifier).get();
            return ImageIO.read(resource.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int loadTextureFromPath(Path filePath) {
        try {
            BufferedImage image = ImageIO.read(filePath.toFile());
            return fromBufferedImage(image);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static BufferedImage getBufferedImageFromPath(Path filePath) {
        try {
            return ImageIO.read(filePath.toFile());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int loadTextureFromBufferedImage(BufferedImage image) {
        return fromBufferedImage(image);
    }

    //Can be used to load buffered images in ImGui
    public static int fromBufferedImage(BufferedImage image) {
        if (image == null) {
            return -1;
        }
        final int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

        final ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int pixel = pixels[y * image.getWidth() + x];

                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }

        buffer.flip();

        final int texture = GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.getWidth(), image.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        return texture;
    }

    public static void transitionActiveState(boolean active) {
        if (activeLastFrame == active) return;
        activeLastFrame = active;

        // Recalculate the size of the gameplay window
        Window window = MinecraftClient.getInstance().getWindow();
        if (window.getWidth() > 0 && window.getWidth() <= 32768 && window.getHeight() > 0 && window.getHeight() <= 32768) {
            MinecraftClient.getInstance().onResolutionChanged();
        }
        imGuiImplGlfw.ungrab();

        if (!activeLastFrame) {
            // Make sure the vanilla grab state is correct
            if (MinecraftClient.getInstance().interactionManager != null) {
                if (MinecraftClient.getInstance().currentScreen == null) {
                    MinecraftClient.getInstance().mouse.unlockCursor();
                    MinecraftClient.getInstance().mouse.lockCursor();
                } else {
                    MinecraftClient.getInstance().mouse.lockCursor();
                    MinecraftClient.getInstance().mouse.unlockCursor();
                }
                MinecraftClient.getInstance().mouse.onResolutionChanged();
            }
        } else {
            // Forcefully ungrab the cursor
            long handle = ImGui.getMainViewport().getPlatformHandle();
            if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                GLFW.glfwSetCursorPos(handle, ImGui.getMainViewport().getSizeX() / 2f, ImGui.getMainViewport().getSizeY() / 2f);
            }
        }
    }

    public static boolean isActive() {
        return activeLastFrame;
    }

    public static boolean isActiveInternal() {
        if (!shouldRender) {
            return false;
        }
        if (MinecraftClient.getInstance().options.hudHidden) {
            return false;
        }
        ClientPlayerInteractionManager gameMode = MinecraftClient.getInstance().interactionManager;
        if (gameMode == null) return false;
        if (MinecraftClient.getInstance().world == null) return false;
        if (MinecraftClient.getInstance().player == null) return false;
        if (MinecraftClient.getInstance().getOverlay() != null) return false;
        return true;
    }

    public static int getFrameX() {
        return frameX;
    }

    public static void setFrameX(int frameX) {
        ImGuiImplementation.frameX = frameX;
    }

    public static int getFrameY() {
        return frameY;
    }

    public static void setFrameY(int frameY) {
        ImGuiImplementation.frameY = frameY;
    }

    public static int getFrameWidth() {
        return frameWidth;
    }

    public static void setFrameWidth(int frameWidth) {
        ImGuiImplementation.frameWidth = frameWidth;
    }

    public static int getFrameHeight() {
        return frameHeight;
    }

    public static void setFrameHeight(int frameHeight) {
        ImGuiImplementation.frameHeight = frameHeight;
    }

    public static int getViewportSizeX() {
        return viewportSizeX;
    }

    public static void setViewportSizeX(int viewportSizeX) {
        ImGuiImplementation.viewportSizeX = viewportSizeX;
    }

    public static int getViewportSizeY() {
        return viewportSizeY;
    }

    public static void setViewportSizeY(int viewportSizeY) {
        ImGuiImplementation.viewportSizeY = viewportSizeY;
    }

    public static double getNewMouseX(double x) {
        return x - frameX;
    }

    public static double getNewMouseY(double y) {
        return y - frameY;
    }

    public static int getNewGameWidth(float scale) {
        return Math.max(1, Math.round(frameWidth * scale)) + frameX;
    }

    public static int getNewGameHeight(float scale) {
        return Math.max(1, Math.round(frameHeight * scale)) + frameY;
    }

    public static ImVec2 getCenterViewportPos() {
        return new ImVec2(frameX + frameWidth / 2f, frameY + frameHeight / 2f);
    }

    private static ImVec2 lastCenterPos = null;

    public static void pushWindowCenterPos() {
        lastCenterPos = getCurrentWindowCenterPos();
    }

    public static ImVec2 getLastWindowCenterPos() {
        if (lastCenterPos == null) {
            return getCenterViewportPos();
        }
        return lastCenterPos;
    }

    public static ImVec2 getCurrentWindowCenterPos() {
        //return new ImVec2(ImGui.getWindowPosX() + ImGui.getWindowSizeX() / 2f, ImGui.getWindowPosY() + ImGui.getWindowSizeY() / 2f);
        return getCenterViewportPos();
    }

    public static boolean shouldModifyViewport() {
        return isActive();
    }

    public static void centeredText(String text) {
        float win_width = ImGui.getWindowWidth();
        float text_width = ImGui.calcTextSize(text).x;

        // calculate the indentation that centers the text on one line, relative
        // to window left, regardless of the `ImGuiStyleVar_WindowPadding` value
        float text_indentation = (win_width - text_width) * 0.5f;

        // if text is too long to be drawn on one line, `text_indentation` can
        // become too small or even negative, so we check a minimum indentation
        float min_indentation = 20.0f;
        if (text_indentation <= min_indentation) {
            text_indentation = min_indentation;
        }

        ImGui.sameLine(text_indentation);
        ImGui.pushTextWrapPos(win_width - text_indentation);
        ImGui.textWrapped(text);
        ImGui.popTextWrapPos();
    }
}