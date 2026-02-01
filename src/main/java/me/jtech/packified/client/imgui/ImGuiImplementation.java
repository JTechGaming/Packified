package me.jtech.packified.client.imgui;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import imgui.*;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.glfw.ImGuiImplGlfw;
import imgui.internal.ImGuiContext;
import imgui.internal.flag.ImGuiDockNodeFlags;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.config.ModConfig;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import me.jtech.packified.client.helpers.NotificationHelper;
import me.jtech.packified.client.helpers.PackHelper;
import me.jtech.packified.client.util.PackUtils;
import me.jtech.packified.client.util.SafeTextureLoader;
import me.jtech.packified.client.windows.elements.MenuBar;
import me.jtech.packified.client.util.IniUtil;
import me.jtech.packified.client.helpers.TutorialHelper;
import me.jtech.packified.client.windows.*;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import me.jtech.packified.client.windows.popups.ModelPickerWindow;
import me.jtech.packified.client.windows.popups.SelectFolderWindow;
import me.jtech.packified.client.windows.popups.PackBrowserWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.input.SystemKeycodes;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.*;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
    public final static ImGuiImplGlfw imGuiImplGlfw = new ImGuiImplGlfw();
    private final static CustomImGuiImplGl3 imGuiImplGl3 = new CustomImGuiImplGl3();
    public static boolean initialized = false;

    private static int frameX = 0;
    private static int frameY = 0;
    private static int frameWidth = 1;
    private static int frameHeight = 1;
    private static int viewportSizeX = 1;
    private static int viewportSizeY = 1;

    public static float minX;
    public static float maxX;
    public static float minY;
    public static float maxY;

    public static boolean shouldRender = false;

    private static ImGuiContext imGuiContext = null;

    private static boolean activeLastFrame = false;

    public static float aspectRatio = 1;

    public static boolean grabbed = false;

    public static List<ImFont> loadedFonts = new ArrayList<>();
    public static List<String> loadedFontNames = new ArrayList<>();
    public static ImFont currentFont = null;

    private static final Map<String, Integer> textureCache = Collections.synchronizedMap(new HashMap<>());
    public static boolean enterGameKeyToggled = false;

    public static boolean firstFrameAfterInit = true;
    private static boolean runGarbageCollectionNextFrame;

    /**
     * PLEASE DO NOT CALL THIS METHOD!
     * This method is already called during initialization to properly set up ImGui.
     */
    public static void create() {
        if (initialized) {
            throw new IllegalStateException("Packified UI initialized twice");
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
        // Load file explorers from config
        Map<Integer, ModConfig.FileExplorerInfo> explorers = ModConfig.getDockConfig().explorers;
        for (int explorerID : explorers.keySet()) {
            FileExplorerWindow explorer = new FileExplorerWindow(explorerID, Path.of(explorers.get(explorerID).lastDirectory));
            explorer.isOpen.set(explorers.get(explorerID).isOpen);
        }

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

            short[] glyphRanges = new short[]{
                    0x0020, 0x00FF, // Basic Latin + Latin-1 Supplement
                    0x0000 // End of ranges
            };
            ImFont font = data.getFonts().addFontFromMemoryTTF(
                    loadFont(identifier),
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
        if (PreferencesWindow.selectedFont.get() >= loadedFonts.size()) {
            PreferencesWindow.selectedFont.set(0);
        }
        currentFont = loadedFonts.get(PreferencesWindow.selectedFont.get());

        data.setConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.ViewportsEnable);
        data.setConfigMacOSXBehaviors(SystemKeycodes.IS_MAC_OS);

        imGuiImplGlfw.init(MinecraftClient.getInstance().getWindow().getHandle(), true);
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

    private static byte[] loadFont(Identifier identifier) {
        try {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(identifier);
            if (resource.isEmpty())
                throw new MissingResourceException("Missing font: " + identifier.getPath(), "Font", "");
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
        if (!initialized) {
            throw new IllegalStateException("Tried to use Imgui while it was not initialized");
        }

        if (!isActiveInternal()) {
            openOrClose(false);
            return;
        }

        if (firstFrameAfterInit) {
            firstFrameAfterInit = false;

            // Load last opened pack
            String lastPackName = ModConfig.getDockConfig().lastOpenedPack;
            if (lastPackName != null && !lastPackName.isBlank()) {
                ResourcePackProfile pack = PackUtils.getPack(lastPackName);
                if (pack != null) {
                    MinecraftClient.getInstance().execute(() ->
                            PackHelper.updateCurrentPack(pack)
                    );
                }
            }
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

            ImGuiImplementation.openOrClose(false);
            return;
        }

        // Setup Docking
        ImGui.setNextWindowBgAlpha(0f);
        ImGuiViewport viewport = ImGui.getMainViewport();

        // Make the host window cover the main viewport
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        ImGui.setNextWindowViewport(viewport.getID());

        // Remove rounding so dockspace fills cleanly
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);

        int hostWindowFlags = ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoBackground
                | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.MenuBar
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.NoDocking;

        ImGui.begin("DockHost", hostWindowFlags);

        ImGui.setCursorPosX(ImGui.getCursorPosX() - ImGui.getStyle().getFramePaddingX());
        ImGui.setCursorPosY(ImGui.getCursorPosY() - ImGui.getStyle().getFramePaddingY() * 2.4f); // Adjust for the padding between menu bar and dockspace

        ImGui.popStyleVar(2);

        int mainDock = ImGui.getID("MainDockSpace");
        ImGui.dockSpace(mainDock, 0.0f, 0.0f, ImGuiDockNodeFlags.NoWindowMenuButton);
        ImGui.end();

        ImGui.setNextWindowDockID(mainDock);

        if (ImGui.begin("Main", ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoNavInputs | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoSavedSettings)) {

            minX = ImGui.getWindowContentRegionMinX();
            maxX = ImGui.getWindowContentRegionMaxX();
            minY = ImGui.getWindowContentRegionMinY();
            maxY = ImGui.getWindowContentRegionMaxY();

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

            if (ImGui.isWindowHovered() && ImGui.isMouseClicked(GLFW.GLFW_MOUSE_BUTTON_RIGHT) || enterGameKeyToggled) {
                MinecraftClient client = MinecraftClient.getInstance();
                GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                ImGuiIO io = ImGui.getIO();
                io.addConfigFlags(ImGuiConfigFlags.NoMouse);
                grabbed = true;
                client.mouse.lockCursor();
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (!client.mouse.wasRightButtonClicked() && !enterGameKeyToggled) {
                if (grabbed) {
                    grabbed = false;
                    client.mouse.unlockCursor();
                    GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    ImGuiIO io = ImGui.getIO();
                    io.removeConfigFlags(ImGuiConfigFlags.NoMouse);
                }
            } else {
                if (MinecraftClient.getInstance().currentScreen != null) {
                    GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                } else {
                    GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                }

                ImGui.setWindowFocus("DockHost");
                ImGui.getIO().setWantCaptureKeyboard(false);
                ImGui.getIO().setWantCaptureMouse(false);
            }
        }
        ImGui.end();

        if (runGarbageCollectionNextFrame) {
            SafeTextureLoader.garbageCollect();
            runGarbageCollectionNextFrame = false;
        }

        // Window rendering
        LogWindow.render();
        EditorWindow.render();
        FileHierarchyWindow.render();
        BackupWindow.render();
        PackBrowserWindow.render();
        VersionControlWindow.render();
        WorldWindow.render();
        MultiplayerWindow.render();
        SelectFolderWindow.render();
        ModifyFileWindow.render();
        ConfirmWindow.render();
        NotificationHelper.render();
        CornerNotificationsHelper.render();
        PreferencesWindow.render();
        PackCreationWindow.render();
        PackExporterWindow.render();
        AssetInspectorWindow.render();
        FileExplorerWindow.renderAll();
        ModelEditorWindow.render();
        ModelPickerWindow.render();

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

        openOrClose(true);
    }

    public static void saveExplorers() {
        Map<Integer, ModConfig.FileExplorerInfo> explorers = new HashMap<>();
        List<FileExplorerWindow> explorerList = FileExplorerWindow.explorers.stream().filter(explorer -> !explorer.allowFolderSelection).toList();

        for (FileExplorerWindow explorer : explorerList) {
            ModConfig.FileExplorerInfo info = new ModConfig.FileExplorerInfo();
            info.isOpen = explorer.isOpen.get();
            if (explorer.getCurrentDirectory() != null) {
                info.lastDirectory = explorer.getCurrentDirectory().toString();
            }
            explorers.put(explorer.i, info);
        }
        ModConfig.DockConfig dockConfig = ModConfig.getDockConfig();
        dockConfig.explorers = explorers;
        ModConfig.saveDockConfig(dockConfig);
    }

    /**
     * PLEASE DO NOT CALL THIS METHOD!
     * This method is already called by PackifiedClient during shutdown to properly dispose of ImGui resources.
     */
    public static void dispose() {
        saveExplorers();

        clearTextureCache();
        FileHierarchyWindow.clearCache();

        imGuiImplGl3.shutdown();

        ImGui.destroyContext();
        ImPlot.destroyContext(ImPlot.getCurrentContext());
    }

    public static int loadTextureFromOwnIdentifier(String identifierPath) {
        // Check if the texture is already cached
        if (textureCache.containsKey(identifierPath)) {
            return textureCache.get(identifierPath);
        }

        try {
            if (MinecraftClient.getInstance().getResourceManager()
                    .getResource(Identifier.of(Packified.MOD_ID, identifierPath)).isEmpty()) {
                throw new IllegalArgumentException("Provided texture could not be found: " + identifierPath);
            }
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
            LogWindow.addError(e.getMessage());
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
            LogWindow.addError(e.getMessage());
        }
        return null;
    }

    public static int loadTextureFromPath(Path filePath) {
        try {
            BufferedImage image = ImageIO.read(filePath.toFile());
            return fromBufferedImage(image);
        } catch (IOException e) {
            LogWindow.addError(e.getMessage());
            return -1;
        }
    }

    public static BufferedImage getBufferedImageFromPath(Path filePath) {
        try {
            return ImageIO.read(filePath.toFile());
        } catch (IOException e) {
            LogWindow.addError(e.getMessage());
            return null;
        }
    }

    public static int loadTextureFromBufferedImage(BufferedImage image) {
        return fromBufferedImage(image);
    }

    public static boolean menuItemWithIcon(int glTextureId, String label, float iconSizeX, float iconSizeY) {
        ImGui.pushID(label); // isolate ID space
        // Draw the icon (GL texture created by ImGuiImplementation.fromBufferedImage / loadTextureFromOwnIdentifier)
        ImGui.image(glTextureId, iconSizeX, iconSizeY);
        ImGui.sameLine(); // put text to the right of the icon
        boolean activated = ImGui.menuItem(label);
        ImGui.popID();
        return activated;
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
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.getWidth(), image.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        return texture;
    }

    private static ProjectionMatrix2 projectionBuffers = null;
    private static Framebuffer outputFramebuffer = null;

    public static void blit(Framebuffer framebuffer, int width, int height, float x1, float y1, float x2, float y2) {
        if (projectionBuffers == null) {
            projectionBuffers = new ProjectionMatrix2("Blit render target", 1000.0f, 3000.0f, true);
        }
        if (outputFramebuffer == null) {
            outputFramebuffer = new SimpleFramebuffer(null, 1, 1, true);
        }

        GlStateManager._viewport(0, 0, width, height); // Resize the viewport itself

        // Resize the framebuffer if the viewport size has changed
        if (outputFramebuffer.textureWidth != width || outputFramebuffer.textureHeight != height) {
            outputFramebuffer.resize(width, height);
        }

        GpuTexture colorAttachment = outputFramebuffer.getColorAttachment();
        if (colorAttachment != null && !colorAttachment.isClosed()) { // Clear the color attachment if it exists (so set color to 0)
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(colorAttachment, 0);
        }
        GpuTexture depthAttachment = outputFramebuffer.getDepthAttachment();
        if (depthAttachment != null && !depthAttachment.isClosed()) { // Clear the depth attachment if it exists (so set depth to 1)
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthAttachment, 1.0f);
        }

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(new Matrix4f().translation(0.0f, 0.0f, -1500.0f)); // Ensure the following will be visible within the orthographic projection
        RenderSystem.setProjectionMatrix(projectionBuffers.set(width, height), ProjectionType.ORTHOGRAPHIC); // Update the projection matrix to the new size

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        // Draw a quad that fills up the area where the viewport needs to be
        builder.vertex(width * x1, height * y2, 0.0f).texture(0.0f, 0.0f);
        builder.vertex(width * x2, height * y2, 0.0f).texture(1.0f, 0.0f);
        builder.vertex(width * x2, height * y1, 0.0f).texture(1.0f, 1.0f);
        builder.vertex(width * x1, height * y1, 0.0f).texture(0.0f, 1.0f);
        try (BuiltBuffer meshData = builder.end()) {
            // -------------------------------------------------------
            // Derived from the >=1.21.5 FrameBuffer.drawBlit() method
            RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(6);
            GpuBuffer vertexBuffer = VertexFormats.POSITION_TEXTURE.uploadImmediateVertexBuffer(meshData.getBuffer());

            GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms().write(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                    new Vector3f(), new Matrix4f());

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", outputFramebuffer.getColorAttachmentView(), OptionalInt.empty())) {
                renderPass.setPipeline(PackifiedClient.VIEWPORT_RESIZE_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
                renderPass.setVertexBuffer(0, vertexBuffer);
                renderPass.setIndexBuffer(gpuBuffer, shapeIndexBuffer.getIndexType());
                renderPass.bindTexture("InSampler", framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().getRepeated(FilterMode.NEAREST));
                renderPass.drawIndexed(0, 0, 6, 1);
            }
            // -------------------------------------------------------
        }

        RenderSystem.setProjectionMatrix(RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getProjectionType());
        modelViewStack.popMatrix();

        outputFramebuffer.blitToScreen(); // Blit the temporary framebuffer to the screen instead of the main framebuffer
    }

    public static void openOrClose(boolean open) {
        if (activeLastFrame == open) return;
        activeLastFrame = open;

        if (!activeLastFrame) {
            // Fix grab state
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
            // ungrab the cursor
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
        return MinecraftClient.getInstance().getOverlay() == null;
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

    public static int getNewGameWidth(float scale) {
        return Math.max(1, Math.round(frameWidth * scale)) + frameX;
    }

    public static int getNewGameHeight(float scale) {
        return Math.max(1, Math.round(frameHeight * scale)) + frameY;
    }

    public static ImVec2 getCenterViewportPos() {
        return ImGui.getMainViewport().getCenter();
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

    public static void whiteSpace(int size) {
        for (int i=0; i<size;i++) {
            ImGui.spacing();
        }
    }

    public static int getFileIconTextureId(String extension) {
        switch (extension) {
            case ".json" -> {
                return SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_model.png"), true);
            }
            case ".png", ".jpg", ".jpeg", ".bmp", ".gif" -> {
                return SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_image.png"), true);
            }
            case "folder" -> {
                return SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_folder.png"), true);
            }
            default -> {
                return SafeTextureLoader.loadFromIdentifier(Packified.identifier("textures/ui/neu_file.png"), true);
            }
        }
    }

    public static void runGarbageCollection() {
        runGarbageCollectionNextFrame = true;
    }
}