package me.jtech.packified.client.windows;

import com.google.gson.*;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import me.jtech.packified.client.helpers.ExternalEditorHelper;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import me.jtech.packified.client.util.FileUtils;
import me.jtech.packified.client.util.SafeTextureLoader;
import me.jtech.packified.client.windows.popups.ConfirmWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Environment(EnvType.CLIENT)
public class ModelEditorWindow {
    // Data classes for minecraft model JSON
    public record Rotation(float angle, String axis, List<Float> origin) { }
    public record ModelElement(String name, List<Float> from, List<Float> to, Map<String, Face> faces, Rotation rotation) { }
    record Face(String texture, List<Float> uv) { }
    record DisplayElement(List<Float> rotation, List<Float> translation, List<Float> scale) { }
    record GroupElement(String name, List<Float> origin, int color, List<GroupChild> children) { }
    record BlockModel(String format_version, int[] texture_size, Map<String, String> textures, List<ModelElement> elements, Map<String, DisplayElement> display, List<GroupElement> groups) { }
    record HistoryChange(List<Float> rotation, List<Float> translation, List<Float> scale) { }

    static class BatchedMesh {
        int vao, vbo, ebo;
        int indexCount;
    }

    // OpenGL data
    private static int fbo = 0;
    private static int colorTex = 0;
    private static int depthRbo = 0;
    private static int fbWidth = 0, fbHeight = 0;
    private static int shaderProgram = 0;

    // camera state
    private static float camDistance = 1.0f;
    private static float camYaw = 0f;
    private static float camPitch = 20f;
    private static final Vector3f target = new Vector3f(0, 0, 0);

    private static boolean capturingMouse = false;

    private static double lastMouseX = 0;
    private static double lastMouseY = 0;

    // selection state
    private static int selectedElementIndex = -1; // -1 = none
    private static int previouslySelectedElementIndex = -1;
    //private static boolean dragging = false;
    //private static int draggingAxis = -1; // 0=x,1=y,2=z or -1 none

    // grid data
    private static int gridVao = 0;
    private static int colorShader = 0;
    private static int gridVertexCount = 0;
    private static boolean gridInitialized = false;

    private static Path selectedTexturePath;

    public static ImBoolean isOpen = new ImBoolean(false);

    private static boolean renderedLastFrame = false;
    private static boolean closeNextFrame = false;

    // model data
    private static boolean modelUploaded = false;
    private static final Map<Integer, BatchedMesh> batchedMeshes = new HashMap<>();
    private static final Map<String, BufferedImage> loadedTextures = new java.util.HashMap<>();
    private static BlockModel loadedModel;
    private static Path loadedModelPath;
    private static boolean unsavedChanges = false;

    private static int modelMvpLoc = -1;
    private static int modelSelLoc = -1;

    private static int gridMvpLoc = -1;

    private static final Matrix4f mvp = new Matrix4f();

    private static final Stack<HistoryChange> undoHistory = new Stack<>();
    private static final Stack<HistoryChange> redoHistory = new Stack<>();

    private static final int[][] FACE_UV_ORDER = {
            {0, 1, 2, 3}, // north
            {1, 0, 3, 2}, // south
            {1, 0, 3, 2}, // east
            {0, 1, 2, 3}, // west
            {0, 1, 2, 3}, // up
            {1, 0, 3, 2}  // down
    };

    private static final float[][] UV_CORNERS = {
            {0, 1}, // bottom-left
            {1, 1}, // bottom-right
            {1, 0}, // top-right
            {0, 0}  // top-left
    };

    public static void loadModel(String path) {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(GroupChild.class, new GroupChildAdapter())
                    .create();
            loadedModel = gson.fromJson(reader, BlockModel.class);
            loadedModelPath = Path.of(path);
            selectedTexturePath = null;
            modelMvpLoc = 0;
            loadTextures();
            modelUploaded = false;
            CornerNotificationsHelper.addNotification("Model loaded successfully" , "Loaded model with " + loadedModel.elements().size() + " elements.", LogWindow.LogType.SUCCESS.getColor(), 4f);
        } catch (Exception e) {
            PackifiedClient.LOGGER.error(e.getMessage());
        }
    }

    public static void loadTextures() {
        Map<String ,String> texMap = loadedModel.textures();
        for (Map.Entry<String, String> entry : texMap.entrySet()) {
            Path fullPath = FileUtils.getPackFolderPath().resolve("assets/minecraft/textures/" + entry.getValue() + ".png");
            if (fullPath.toFile().exists()) {
                try {
                    BufferedImage texture = ImageIO.read(fullPath.toFile());
                    loadedTextures.put(entry.getKey(), texture);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                ConfirmWindow.open("Missing Texture",
                        "Texture file not found: \n" + fullPath + "\nSome textures may not display correctly.", () -> { } );
            }
        }
    }

    private static void ensureShaders() {
        if (shaderProgram == 0) {
            shaderProgram = createTextureShader();
        }
        if (colorShader == 0) {
            colorShader = createColorShader();
        }
    }

    private static void ensureFramebuffer(int width, int height) {
        if (width == fbWidth && height == fbHeight && fbo != 0) return;

        // Delete old
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            GL11.glDeleteTextures(colorTex);
            GL30.glDeleteRenderbuffers(depthRbo);
        }

        // Create framebuffer
        fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        // Color texture
        colorTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, colorTex, 0);

        // Depth buffer
        depthRbo = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRbo);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRbo);

        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer incomplete!");
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        fbWidth = width;
        fbHeight = height;
    }

    public static void render() {
        if (!isOpen.get()) return;

        if (closeNextFrame) {
            loadedModel = null;
            loadedModelPath = null;
            loadedTextures.clear();
            closeNextFrame = false;
        }

        renderedLastFrame = false;

        if (ImGui.begin("Model Editor", isOpen,ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | (unsavedChanges ? ImGuiWindowFlags.UnsavedDocument : ImGuiWindowFlags.None))) {
            renderedLastFrame = true;

            int targetW = (int) Math.max(1, ImGui.getWindowContentRegionMaxX() - ImGui.getWindowContentRegionMinX() + ImGui.getStyle().getWindowPaddingX() + 25);
            int targetH = (int) Math.max(1, ImGui.getWindowContentRegionMaxY() - ImGui.getWindowContentRegionMinY() + ImGui.getStyle().getWindowPaddingY() + 21);

            ensureFramebuffer(targetW, targetH);

            float aspect = (fbHeight == 0) ? 1.0f : (float) fbWidth / (float) fbHeight;
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70.0f), aspect, 0.1f, 100f);
            Matrix4f view = getViewMatrix();
            Matrix4f model = new Matrix4f().identity(); // scale and rotation

            projection.mul(view, mvp).mul(model);

            // Render scene to framebuffer
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, fbWidth, fbHeight);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glClearColor(0.1f, 0.1f, 0.1f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

            if (fbWidth > 0 && fbHeight > 0) {
                renderScene();
            }

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            // Draw framebuffer into ImGui as an image. This creates the ImGui item.
            ImGui.setCursorPos(ImGui.getCursorPosX() - ImGui.getStyle().getWindowPaddingX(),
                    ImGui.getCursorPosY() - ImGui.getStyle().getWindowPaddingY());
            ImGui.image(colorTex,
                    targetW,
                    targetH,
                    0, 1, 1, 0);

            if (ImGui.isItemHovered() && ImGui.isMouseClicked(0)) {
                // mouse pos relative to ImGui item
                float mx = ImGui.getIO().getMousePosX() - ImGui.getItemRectMinX();
                float my = ImGui.getIO().getMousePosY() - ImGui.getItemRectMinY();

                if (loadedModel != null) {
                    Vector3f rayOrigin = new Vector3f(getCameraPosition());
                    Vector3f rayDir = getRayFromMouse(mx, my, fbWidth, fbHeight, projection, view);

                    int bestIndex = -1;
                    float bestDist = Float.MAX_VALUE;
                    for (int i = 0; i < loadedModel.elements().size(); i++) {
                        ModelElement e = loadedModel.elements().get(i);
                        Vector3f min = new Vector3f(e.from().get(0) / 16f - 0.5f, e.from().get(1) / 16f - 0.5f, e.from().get(2) / 16f - 0.5f);
                        Vector3f max = new Vector3f(e.to().get(0) / 16f - 0.5f, e.to().get(1) / 16f - 0.5f, e.to().get(2) / 16f - 0.5f);
                        boolean intersects = rayIntersectsElement(rayOrigin, rayDir, e);
                        if (intersects) {
                            float dist = new Vector3f(min).add(max).mul(0.5f).distance(rayOrigin);
                            if (dist < bestDist) {
                                bestDist = dist;
                                bestIndex = i;
                            }
                        }
                    }
                    if (bestIndex >= 0) {
                        previouslySelectedElementIndex = selectedElementIndex;
                        selectedElementIndex = bestIndex;
                    }
                }
            }

            // prevents dragging the window
            updateCameraAfterImage();

            renderSideBars();
        }

        ImGui.end();
    }

    private static void renderSideBars() {
        ImGui.begin("Model Elements");

        alreadyRenderedHoverThisFrame = false;

        if (loadedModel != null) {
            if (loadedModel.groups() != null && !loadedModel.groups().isEmpty()) {
                for (GroupElement rootGroup : loadedModel.groups()) {
                    renderGroupChild(new GroupChild.Group(rootGroup));
                }
            } else {
                for (int i = 0; i < loadedModel.elements().size(); i++) {
                    ImGui.pushID(i);
                    ModelElement e = loadedModel.elements().get(i);
                    String label = e.name() != null ? e.name() : "cube";
                    if (ImGui.selectable(label, selectedElementIndex == i)) {
                        previouslySelectedElementIndex = selectedElementIndex;
                        selectedElementIndex = i;
                    }
                    ImGui.popID();
                }
            }
        } else {
            ImGuiImplementation.centeredText("No model loaded.");
        }
        ImGui.end();

        ImGui.begin("Element Properties");
        if (selectedElementIndex >= 0 && loadedModel != null) {
            ModelElement e = loadedModel.elements().get(selectedElementIndex);
            float[] from = {e.from().get(0), e.from().get(1), e.from().get(2)};
            float[] to = {e.to().get(0), e.to().get(1), e.to().get(2)};

            float[] position = { from[0], from[1], from[2] };
            float[] size = { to[0] - from[0], to[1] - from[1], to[2] - from[2] };
            float[] pivot = { e.rotation() != null && e.rotation().origin() != null && e.rotation().origin().size() == 3 ? e.rotation().origin().get(0) : 0f,
                    e.rotation() != null && e.rotation().origin() != null && e.rotation().origin().size() == 3 ? e.rotation().origin().get(1) : 0f,
                    e.rotation() != null && e.rotation().origin() != null && e.rotation().origin().size() == 3 ? e.rotation().origin().get(2) : 0f };
            float[] rotation = { e.rotation() != null ? e.rotation().angle() * (e.rotation().axis().equals("x") ? 1 : 0) : 0f,
                    e.rotation() != null ? e.rotation().angle() * (e.rotation().axis().equals("y") ? 1 : 0) : 0f,
                    e.rotation() != null ? e.rotation().angle() * (e.rotation().axis().equals("z") ? 1 : 0) : 0f };

            boolean changeMade = false;

            if (ImGui.sliderFloat3("Position", position, -32.0f, 32.0f)) {
                e.from().set(0, position[0]);
                e.from().set(1, position[1]);
                e.from().set(2, position[2]);
                e.to().set(0, position[0] + size[0]);
                e.to().set(1, position[1] + size[1]);
                e.to().set(2, position[2] + size[2]);
                changeMade = true;
            }
            if (ImGui.sliderFloat3("Size", size, -32.0f, 32.0f)) {
                e.to().set(0, position[0] + size[0]);
                e.to().set(1, position[1] + size[1]);
                e.to().set(2, position[2] + size[2]);
                changeMade = true;
            }
            if (ImGui.sliderFloat3("Origin", pivot, -32.0f, 32.0f)) {
                if (e.rotation() == null) {
                    e = new ModelElement(e.name(), e.from(), e.to(), e.faces(), new Rotation(0, "y", List.of(pivot[0], pivot[1], pivot[2])));
                    List<ModelElement> elements = new ArrayList<>(loadedModel.elements());
                    elements.set(selectedElementIndex, e);
                    loadedModel = new BlockModel(loadedModel.format_version(), loadedModel.texture_size(), loadedModel.textures(), elements, loadedModel.display(), loadedModel.groups());
                } else {
                    if (e.rotation().origin() != null) {
                        e.rotation().origin().set(0, pivot[0]);
                        e.rotation().origin().set(1, pivot[1]);
                        e.rotation().origin().set(2, pivot[2]);
                    }
                }
                changeMade = true;
            }
            if (ImGui.sliderFloat3("Rotation", rotation, 0.0f, 360.0f)) {
                if (e.rotation() == null) {
                    e = new ModelElement(e.name(), e.from(), e.to(), e.faces(), new Rotation(0, "y", List.of(pivot[0], pivot[1], pivot[2])));
                    List<ModelElement> elems = new ArrayList<>(loadedModel.elements());
                    elems.set(selectedElementIndex, e);
                    loadedModel = new BlockModel(loadedModel.format_version(), loadedModel.texture_size(), loadedModel.textures(), elems, loadedModel.display(), loadedModel.groups());
                }
                if (rotation[0] != 0f) {
                    // Create a new Rotation record with updated values
                    Rotation newRot = new Rotation(rotation[0], "x", List.of(pivot[0], pivot[1], pivot[2]));
                    e = new ModelElement(e.name(), e.from(), e.to(), e.faces(), newRot);
                } else if (rotation[1] != 0f) {
                    Rotation newRot = new Rotation(rotation[1], "y", List.of(pivot[0], pivot[1], pivot[2]));
                    e = new ModelElement(e.name(), e.from(), e.to(), e.faces(), newRot);
                } else if (rotation[2] != 0f) {
                    Rotation newRot = new Rotation(rotation[2], "z", List.of(pivot[0], pivot[1], pivot[2]));
                    e = new ModelElement(e.name(), e.from(), e.to(), e.faces(), newRot);
                } else {
                    Rotation newRot = new Rotation(0f, "y", List.of(pivot[0], pivot[1], pivot[2]));
                    e = new ModelElement(e.name(), e.from(), e.to(), e.faces(), newRot);
                }
                changeMade = true;
            }

            // Update the elements in the model
            List<ModelElement> elements = new ArrayList<>(loadedModel.elements());
            if (changeMade) {
                unsavedChanges = true;
                modelUploaded = false; // force re-upload of model to GPU next frame
            }
            elements.set(selectedElementIndex, e);
            loadedModel = new BlockModel(loadedModel.format_version(), loadedModel.texture_size(), loadedModel.textures(), elements, loadedModel.display(), loadedModel.groups());
        } else {
            ImGuiImplementation.centeredText("No element selected.");
        }
        ImGui.end();

        ImGui.begin("Textures");
        if (loadedModel != null && !loadedTextures.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, BufferedImage> entry : loadedTextures.entrySet()) {
                String key = entry.getKey();
                if (loadedModel.textures() != null) {
                    String textureName = loadedModel.textures().get(key);
                    if (names.contains(textureName)) {
                        continue;
                    }
                    names.add(textureName);
                    boolean selected = ImGui.selectable("Texture: " + textureName);
                    Path texturePath = FileUtils.getPackFolderPath().resolve("assets/minecraft/textures/" + textureName + ".png");
                    renderHoverTooltip(key, texturePath);
                    if (selected) {
                        selectedTexturePath = texturePath;
                    }
                }
            }
        } else {
            ImGuiImplementation.centeredText("No textures loaded.");
        }
        ImGui.end();

        ImGui.begin("UV Editor");
        if (selectedTexturePath != null && loadedModel != null) {
            int textureId = SafeTextureLoader.load(selectedTexturePath);
            int canvasSize = (int) Math.min(ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY());
            if (textureId != -1) {
                ImGui.image(textureId, canvasSize, canvasSize);
                if (ImGui.beginPopupContextItem("UV Texture")) {
                    if (ImGui.menuItem("Edit in internal image editor")) {
                        FileUtils.openFile(selectedTexturePath);
                    }
                    if (ExternalEditorHelper.findImageEditor().isPresent()) {
                        if (ImGui.menuItem("Open in external editor: " + ExternalEditorHelper.findImageEditor().get().getFileName().toString().replace(".exe", ""))) {
                            ExternalEditorHelper.openFileWithEditor(ExternalEditorHelper.findImageEditor().get(), selectedTexturePath);
                        }
                    }
                    ImGui.endPopup();
                }
            }
            // Draw UV grid overlay
            ImDrawList drawList = ImGui.getWindowDrawList();
            ImVec2 p = ImGui.getItemRectMin();
            float x = p.x;
            float y = p.y;
            float gridSize = loadedModel.texture_size() != null && loadedModel.texture_size().length == 2 ? loadedModel.texture_size()[0] : 16;
            float step = canvasSize / gridSize;
            for (int i = 0; i <= gridSize; i++) {
                float pos = i * step;
                drawList.addLine(x + pos, y, x + pos, y + canvasSize, 0x88FFFFFF);
                drawList.addLine(x, y + pos, x + canvasSize, y + pos, 0x88FFFFFF);
            }
        } else {
            ImGuiImplementation.centeredText("Select a texture to view.");
        }
        ImGui.end();
    }

    private static void renderGroupChild(GroupChild child) {

        if (child instanceof GroupChild.Group(GroupElement group)) {
            int flags = ImGuiTreeNodeFlags.SpanFullWidth | ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.OpenOnDoubleClick;

            boolean open = ImGui.treeNodeEx(group.name() + "##" + System.identityHashCode(group), flags);

            if (open) {
                for (GroupChild subChild : group.children()) {
                    renderGroupChild(subChild);
                }
                ImGui.treePop();
            }
        } else if (child instanceof GroupChild.Id(int id)) {
            if (id < 0 || id >= loadedModel.elements().size()) return;

            ModelElement e = loadedModel.elements().get(id);

            ImGui.pushID(id);
            boolean selected = (selectedElementIndex == id);
            String label = e.name() != null ? e.name() : "cube";

            if (ImGui.selectable(label, selected)) {
                previouslySelectedElementIndex = selectedElementIndex;
                selectedElementIndex = id;
            }
            ImGui.popID();
        }
    }

    private static float itemHoverTime = 0.0f;
    private static boolean alreadyRenderedHoverThisFrame = false;

    private static void renderHoverTooltip(String name, Path filePath) {
        if (alreadyRenderedHoverThisFrame) return; // Prevent multiple tooltips from rendering in the same frame

        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenBlockedByPopup) && !ImGui.isPopupOpen(name)) {
            itemHoverTime += ImGui.getIO().getDeltaTime();

            if (itemHoverTime > 1.1f) itemHoverTime = 1.0f;

            if (itemHoverTime >= 1.0f) {
                alreadyRenderedHoverThisFrame = true;

                ImGui.beginTooltip();

                ImGui.setNextWindowSize(200, 200);
                if (FileUtils.getFileExtension(filePath.getFileName().toString()).equalsIgnoreCase(".png")) {
                    int textureId = SafeTextureLoader.load(filePath);
                    if (textureId != -1) {
                        ImGui.image(textureId, 100, 100);
                    }
                }
                ImGui.text(String.format("""
                                Path: %s
                                Size: %s
                                Type: %s""",
                        FileUtils.getRelativePackPath(filePath),
                        FileUtils.formatFileSize(FileUtils.getFileSize(filePath)),
                        FileUtils.formatExtension(FileUtils.getFileExtension(name))));

                ImGui.endTooltip();
            } else {
                itemHoverTime += ImGui.getIO().getDeltaTime();
            }
        } else {
            if (itemHoverTime >= 1.0f && itemHoverTime < 2.5f) {
                itemHoverTime += ImGui.getIO().getDeltaTime();
            }
        }
    }

    private static Vector3f getRayFromMouse(float mx, float my, int fbWidth, int fbHeight, Matrix4f projection, Matrix4f view) {
        // Convert to NDC
        float x = (2.0f * mx) / fbWidth - 1.0f;
        float y = 1.0f - (2.0f * my) / fbHeight; // note: y is inverted
        Vector3f rayNdc = new Vector3f(x, y, -1.0f); // forward in NDC

        // Clip space
        Vector3f rayClip = new Vector3f(rayNdc.x, rayNdc.y, -1.0f);

        // Eye space
        Matrix4f invProj = new Matrix4f();
        projection.invert(invProj);
        Vector3f rayEye = invProj.transformPosition(new Vector3f(rayClip.x, rayClip.y, -1.0f));
        rayEye.z = -1.0f; // forward
        rayEye.normalize();

        // World space
        Matrix4f invView = new Matrix4f();
        view.invert(invView);
        Vector3f rayWorld = invView.transformDirection(rayEye);
        rayWorld.normalize();

        return rayWorld;
    }

    private static void updateCameraAfterImage() {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean rightDown = ImGui.isItemHovered() && ImGui.isMouseDown(1);
        boolean middleDown = ImGui.isItemHovered() && ImGui.isMouseDown(2);
        boolean shiftDown = ImGui.getIO().getKeyShift();

        // Start capture
        if (rightDown && !capturingMouse) {
            capturingMouse = true;
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

            // Initialize last mouse pos (prevents first-frame jump)
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(windowHandle, xpos, ypos);
            lastMouseX = xpos[0];
            lastMouseY = ypos[0];
        }

        // Stop capture
        if (!rightDown && capturingMouse) {
            capturingMouse = false;
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }

        if (rightDown) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(windowHandle, xpos, ypos);

            float dx = (float) (xpos[0] - lastMouseX);
            float dy = (float) (ypos[0] - lastMouseY);

            lastMouseX = xpos[0];
            lastMouseY = ypos[0];

            if (shiftDown) {
                // --- Pan ---
                float panSpeed = 0.002f * camDistance;
                Vector3f camPos = getCameraPosition();
                Vector3f forward = new Vector3f(target).sub(camPos).normalize();
                Vector3f right = forward.cross(new Vector3f(0, 1, 0), new Vector3f()).normalize();
                Vector3f up = new Vector3f(0, 1, 0);

                target.fma(-dx * panSpeed, right);
                target.fma(dy * panSpeed, up);
            } else {
                // --- Orbit ---
                camYaw += dx * 0.3f;
                camPitch += dy * 0.3f;
                camPitch = Math.max(-89, Math.min(89, camPitch));
            }
        }

        // Zoom (wheel handled by ImGui IO)
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0f && ImGui.isItemHovered()) {
            camDistance += wheel * -0.1f;
            camDistance = Math.max(0.0f, Math.min(20.0f, camDistance));
        }
    }

    private static void renderGizmo() {
        if (selectedElementIndex < 0) return;
        ModelElement e = loadedModel.elements().get(selectedElementIndex);
        Vector3f from = new Vector3f(e.from().get(0) / 16f - 0.5f, e.from().get(1) / 16f - 0.5f, e.from().get(2) / 16f - 0.5f);
        Vector3f to = new Vector3f(e.to().get(0) / 16f - 0.5f, e.to().get(1) / 16f - 0.5f, e.to().get(2) / 16f - 0.5f);
        Vector3f center = new Vector3f();
        from.add(to, center).mul(0.5f);

        // small lines length
        float len = Math.max(0.1f, Math.max(Math.abs(to.x - from.x) * 1.25f, Math.max(Math.abs(to.y - from.y) * 1.25f, Math.abs(to.z - from.z))) * 1.25f);

        // draw three colored lines with existing color shader: X red, Y green, Z blue
        GL20.glUseProgram(colorShader);
        if (modelMvpLoc == 0) {
            modelMvpLoc = GL20.glGetUniformLocation(colorShader, "uMVP");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            ModelEditorWindow.mvp.get(fb);
            GL20.glUniformMatrix4fv(modelMvpLoc, false, fb);
        }
        float[] lines = {
                // X axis (red)
                center.x, center.y, center.z, 1, 0, 0,
                center.x + len, center.y, center.z, 1, 0, 0,
                // Y axis (green)
                center.x, center.y, center.z, 0, 1, 0,
                center.x, center.y + len, center.z, 0, 1, 0,
                // Z axis (blue)
                center.x, center.y, center.z, 0, 0, 1,
                center.x, center.y, center.z + len, 0, 0, 1
        };
        int tmpVao = GL30.glGenVertexArrays();
        int tmpVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(tmpVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tmpVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, lines, GL15.GL_DYNAMIC_DRAW);
        int stride = 6 * Float.BYTES;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL20.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDrawArrays(GL11.GL_LINES, 0, 6);
        GL30.glBindVertexArray(0);
        GL15.glDeleteBuffers(tmpVbo);
        GL30.glDeleteVertexArrays(tmpVao);
        GL20.glUseProgram(0);
        GL20.glDepthFunc(GL11.GL_LESS);
    }

    private enum GizmoMode { NONE, TRANSLATE, ROTATE, SCALE }
    private static final GizmoMode currentGizmoMode = GizmoMode.NONE;

    private static void renderGizmoAttachment(float[] gizmoLines, Matrix4f mvp) {
        switch (currentGizmoMode) {
            case TRANSLATE -> {
                // Render arrows for translation gizmo
            }
            case ROTATE -> {
                // Render rotation gizmo
            }
            case SCALE -> {
                // Render scale gizmo stub
            }
        }
    }

    // Computes the current camera position in world space
    private static Vector3f getCameraPosition() {
        float yawRad = (float) Math.toRadians(camYaw);
        float pitchRad = (float) Math.toRadians(camPitch);

        float x = (float) (camDistance * Math.cos(pitchRad) * Math.cos(yawRad));
        float y = (float) (camDistance * Math.sin(pitchRad));
        float z = (float) (camDistance * Math.cos(pitchRad) * Math.sin(yawRad));

        return new Vector3f(x, y, z).add(target);
    }

    private static Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(getCameraPosition(), target, new Vector3f(0, 1, 0));
    }

    private static void renderScene() {
        ensureShaders();

        // Use program BEFORE setting uniforms
        GL20.glUseProgram(shaderProgram);

        if (modelMvpLoc < 0) {
            modelMvpLoc = GL20.glGetUniformLocation(shaderProgram, "uMVP");
            modelSelLoc = GL20.glGetUniformLocation(shaderProgram, "uSelected");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            mvp.get(fb);
            GL20.glUniformMatrix4fv(modelMvpLoc, false, fb);
        }

        GL20.glUniform1i(modelSelLoc, selectedElementIndex);

        renderGrid();

        if (loadedModel != null) {
            renderBlockModel(loadedModel);

            renderGizmo();
        }
    }

    private static void setupGrid() {
        final float MODEL_UNIT = 1.0f / 16.0f;

        final int TILE_SIZE = 16; // each tile is 16x16 units, which is used to make sure the size is uniform so that the center tile can have a 16x16 grid
        final int GRID_TILES = 3; // 3x3

        final float HALF_WORLD = (GRID_TILES * TILE_SIZE) / 2.0f; // 24
        final float HALF_TILE = TILE_SIZE / 2.0f;                 // 8

        final float HALF_WORLD_W = HALF_WORLD * MODEL_UNIT;
        final float HALF_TILE_W  = HALF_TILE  * MODEL_UNIT;

        // Line counts
        int outerLinesPerAxis = GRID_TILES + 1; // 4
        int innerLinesPerAxis = TILE_SIZE + 1;  // 17

        int totalLines =
                (outerLinesPerAxis * 2) +   // outer grid
                        (innerLinesPerAxis * 2);    // inner grid

        float[] verts = new float[totalLines * 2 * 6];
        int idx = 0;

        // 3x3 grid
        for (int i = 0; i <= GRID_TILES; i++) {
            float pos = (-HALF_WORLD + i * TILE_SIZE) * MODEL_UNIT;

            float[] color = new float[]{0.4f, 0.4f, 0.4f};

            // Lines parallel to Z
            idx = putLine(verts, idx,
                    pos, 0, -HALF_WORLD_W,
                    pos, 0,  HALF_WORLD_W,
                    color);

            // Lines parallel to X
            idx = putLine(verts, idx,
                    -HALF_WORLD_W, 0, pos,
                    HALF_WORLD_W, 0, pos,
                    color);
        }

        // Center tile, contains a 16x16 grid
        for (int i = 0; i <= TILE_SIZE; i++) {
            float pos = (-HALF_TILE + i) * MODEL_UNIT;

            float[] color = (i == HALF_TILE)
                    ? new float[]{1f, 1f, 1f}   // axes
                    : new float[]{0.7f, 0.7f, 0.7f};

            // Lines parallel to Z
            idx = putLine(verts, idx,
                    pos, 0, -HALF_TILE_W,
                    pos, 0,  HALF_TILE_W,
                    color);

            // Lines parallel to X
            idx = putLine(verts, idx,
                    -HALF_TILE_W, 0, pos,
                    HALF_TILE_W, 0, pos,
                    color);
        }

        gridVertexCount = totalLines * 2;

        // VAO and VBO setup
        gridVao = GL30.glGenVertexArrays();
        int gridVbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(gridVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, gridVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);

        int stride = 6 * Float.BYTES;

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(0);

        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL30.glBindVertexArray(0);

        gridInitialized = true;
    }

    private static int putLine(
            float[] verts, int idx,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float[] color) {

        verts[idx++] = x1;
        verts[idx++] = y1;
        verts[idx++] = z1;
        verts[idx++] = color[0];
        verts[idx++] = color[1];
        verts[idx++] = color[2];

        verts[idx++] = x2;
        verts[idx++] = y2;
        verts[idx++] = z2;
        verts[idx++] = color[0];
        verts[idx++] = color[1];
        verts[idx++] = color[2];

        return idx;
    }

    private static void renderGrid() {
        if (!gridInitialized) {
            setupGrid();
        }
        GL20.glUseProgram(colorShader);

        if (gridMvpLoc < 0) {
            gridMvpLoc = GL20.glGetUniformLocation(colorShader, "uMVP");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            mvp.get(fb);
            GL20.glUniformMatrix4fv(gridMvpLoc, false, fb);
        }

        GL30.glBindVertexArray(gridVao);
        GL11.glDrawArrays(GL11.GL_LINES, 0, gridVertexCount);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);
    }

    private static float[] @NotNull [] calcPositions(Vector3f from, Vector3f to) {
        float x1 = from.x / 16f - 0.5f;
        float y1 = from.y / 16f - 0.5f;
        float z1 = from.z / 16f - 0.5f;
        float x2 = to.x / 16f - 0.5f;
        float y2 = to.y / 16f - 0.5f;
        float z2 = to.z / 16f - 0.5f;

        // 6 faces × 4 verts each = 24
        return new float[][]{
                // front (z2)
                {x1, y1, z2}, {x2, y1, z2}, {x2, y2, z2}, {x1, y2, z2},
                // back (z1)
                {x2, y1, z1}, {x1, y1, z1}, {x1, y2, z1}, {x2, y2, z1},
                // right (x2)
                {x2, y1, z2}, {x2, y1, z1}, {x2, y2, z1}, {x2, y2, z2},
                // left (x1)
                {x1, y1, z1}, {x1, y1, z2}, {x1, y2, z2}, {x1, y2, z1},
                // top (y2)
                {x1, y2, z2}, {x2, y2, z2}, {x2, y2, z1}, {x1, y2, z1},
                // bottom (y1)
                {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}
        };
    }

    private static void uploadBlockModel(BlockModel model) {
        batchedMeshes.clear();
        if (model == null || model.elements() == null) return;

        Map<Integer, List<Float>> vertexData = new HashMap<>();
        Map<Integer, List<Integer>> indexData = new HashMap<>();
        Map<Integer, Integer> indexCursor = new HashMap<>();

        for (int ei = 0; ei < model.elements().size(); ei++) {
            ModelElement e = model.elements().get(ei);

            Vector3f from = new Vector3f(e.from().get(0), e.from().get(1), e.from().get(2));
            Vector3f to   = new Vector3f(e.to().get(0),   e.to().get(1),   e.to().get(2));

            float[][] pos = calcPositions(from, to);
            String[] faceNames = {"north","south","east","west","up","down"};

            for (int f = 0; f < 6; f++) {
                Face face = e.faces().get(faceNames[f]);
                if (face == null) continue;

                String texKey = face.texture().substring(1);
                String texPath = model.textures().get(texKey);
                int textureId = SafeTextureLoader.load(
                        FileUtils.getPackFolderPath()
                                .resolve("assets/minecraft/textures/" + texPath + ".png")
                );

                vertexData.computeIfAbsent(textureId, k -> new ArrayList<>());
                indexData.computeIfAbsent(textureId, k -> new ArrayList<>());
                indexCursor.putIfAbsent(textureId, 0);

                float u1 = 0, v1 = 0, u2 = 1, v2 = 1;
                if (face.uv() != null && model.texture_size() != null) {
                    float tw = model.texture_size()[0];
                    float th = model.texture_size()[1];
                    u1 = face.uv().get(0) / tw;
                    v1 = 1 - face.uv().get(1) / th;
                    u2 = face.uv().get(2) / tw;
                    v2 = 1 - face.uv().get(3) / th;
                }

                int base = indexCursor.get(textureId);

                for (int v = 0; v < 4; v++) {
                    Vector3f p = new Vector3f(
                            pos[f * 4 + v][0],
                            pos[f * 4 + v][1],
                            pos[f * 4 + v][2]
                    );

                    applyRotation(p, e);

                    vertexData.get(textureId).add(p.x);
                    vertexData.get(textureId).add(p.y);
                    vertexData.get(textureId).add(p.z);

                    int uvIndex = FACE_UV_ORDER[f][v];
                    float u = (UV_CORNERS[uvIndex][0] == 0) ? u1 : u2;
                    float vTex = (UV_CORNERS[uvIndex][1] == 0) ? v1 : v2;

                    vertexData.get(textureId).add(u);
                    vertexData.get(textureId).add(vTex);
                    vertexData.get(textureId).add((float) ei);
                }

                indexData.get(textureId).add(base);
                indexData.get(textureId).add(base + 1);
                indexData.get(textureId).add(base + 2);
                indexData.get(textureId).add(base);
                indexData.get(textureId).add(base + 2);
                indexData.get(textureId).add(base + 3);

                indexCursor.put(textureId, base + 4);
            }
        }

        // Upload GPU buffers
        for (int tex : vertexData.keySet()) {
            BatchedMesh mesh = new BatchedMesh();

            float[] verts = toFloatArray(vertexData.get(tex));
            int[] inds = indexData.get(tex).stream().mapToInt(i -> i).toArray();

            mesh.vao = GL30.glGenVertexArrays();
            mesh.vbo = GL15.glGenBuffers();
            mesh.ebo = GL15.glGenBuffers();
            mesh.indexCount = inds.length;

            GL30.glBindVertexArray(mesh.vao);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);

            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, inds, GL15.GL_STATIC_DRAW);

            int stride = 6 * Float.BYTES;
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, stride, 5 * Float.BYTES);
            GL20.glEnableVertexAttribArray(2);

            GL30.glBindVertexArray(0);

            batchedMeshes.put(tex, mesh);
        }

        modelUploaded = true;
    }

    private static float[] toFloatArray(List<Float> floats) {
        float[] result = new float[floats.size()];
        for (int i = 0; i < floats.size(); i++) {
            result[i] = floats.get(i);
        }
        return result;
    }

    private static Vector3f applyRotation(
            Vector3f v,
            ModelElement e
    ) {
        if (e.rotation() == null) return v;

        Rotation r = e.rotation();
        if (r.origin() == null || r.origin().size() != 3) return v;

        Vector3f origin = new Vector3f(
                r.origin().get(0) / 16f - 0.5f,
                r.origin().get(1) / 16f - 0.5f,
                r.origin().get(2) / 16f - 0.5f
        );

        // translate to pivot
        v.sub(origin);

        float angleRad = (float) Math.toRadians(r.angle());

        switch (r.axis()) {
            case "x" -> v.rotateX(angleRad);
            case "y" -> v.rotateY(angleRad);
            case "z" -> v.rotateZ(angleRad);
        }

        // translate back
        v.add(origin);

        return v;
    }

    private static void renderBlockModel(BlockModel model) {
        if (!modelUploaded) uploadBlockModel(model);

        GL20.glUseProgram(shaderProgram);

        GL20.glUniformMatrix4fv(modelMvpLoc, false, mvp.get(new float[16]));
        GL20.glUniform1i(modelSelLoc, selectedElementIndex);

        for (var entry : batchedMeshes.entrySet()) {
            if (entry.getKey() > 0) { // Don't bind texture if the model doesn't have one
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, entry.getKey());
            }

            BatchedMesh mesh = entry.getValue();
            GL30.glBindVertexArray(mesh.vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, mesh.indexCount, GL11.GL_UNSIGNED_INT, 0);
        }

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    private static int createColorShader() {
        String vertexSrc = """ 
            #version 150 core
            in vec3 aPos;
            in vec3 aColor;
            out vec3 vColor;
            uniform mat4 uMVP;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vColor = aColor;
            }
         """;

        String fragmentSrc = """
            #version 150 core
            in vec3 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(vColor, 1.0);
            }
         """;

        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSrc);
        GL20.glCompileShader(vertexShader);

        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSrc);
        GL20.glCompileShader(fragmentShader);

        if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Vertex shader compile error:\n" +
                    GL20.glGetShaderInfoLog(vertexShader));
        }

        if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Fragment shader compile error:\n" +
                    GL20.glGetShaderInfoLog(fragmentShader));
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glBindAttribLocation(program, 0, "aPos");
        GL20.glBindAttribLocation(program, 1, "aColor");
        GL20.glLinkProgram(program);

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        return program;
    }

    private static int createTextureShader() {
        String vertexSrc = """
                    #version 150 core
                    in vec3 aPos;
                    in vec2 aUV;
                    in float aElement;
                    out vec2 vUV;
                    flat out int vElement;
                
                    uniform mat4 uMVP;
                
                    void main() {
                        gl_Position = uMVP * vec4(aPos, 1.0);
                        vUV = aUV;
                        vElement = int(aElement);
                    }
        """;

        String fragmentSrc = """
                     #version 150 core
                     in vec2 vUV;
                     flat in int vElement;
                     out vec4 fragColor;
                
                     uniform sampler2D uTexture;
                     uniform int uSelected;
                
                     void main() {
                         vec4 tex = texture(uTexture, vUV);
                         if (vElement == uSelected) {
                             tex.rgb -= 0.3;
                         }
                         fragColor = tex;
                     }
        """;

        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSrc);
        GL20.glCompileShader(vertexShader);

        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSrc);
        GL20.glCompileShader(fragmentShader);

        if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Vertex shader compile error:\n" +
                    GL20.glGetShaderInfoLog(vertexShader));
        }

        if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Fragment shader compile error:\n" +
                    GL20.glGetShaderInfoLog(fragmentShader));
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glBindAttribLocation(program, 0, "aPos");
        GL20.glBindAttribLocation(program, 1, "aUV");
        GL20.glBindAttribLocation(program, 2, "aElement");
        GL20.glLinkProgram(program);

        int texLoc = GL20.glGetUniformLocation(shaderProgram, "uTexture");
        GL20.glUniform1i(texLoc, 0);

        modelSelLoc = GL20.glGetUniformLocation(shaderProgram, "uSelected");

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        return program;
    }

    public static class GroupChildAdapter implements JsonDeserializer<GroupChild>, JsonSerializer<GroupChild> {
        @Override
        public GroupChild deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                return new GroupChild.Id(json.getAsInt());
            } else if (json.isJsonObject()) {
                GroupElement group = context.deserialize(json, GroupElement.class);
                return new GroupChild.Group(group);
            }
            throw new JsonParseException("Unknown child type: " + json);
        }

        @Override
        public JsonElement serialize(GroupChild src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            if (src instanceof GroupChild.Id(int id)) {
                return new JsonPrimitive(id);
            } else if (src instanceof GroupChild.Group(GroupElement group1)) {
                return context.serialize(group1);
            }
            throw new JsonParseException("Unknown GroupChild subtype: " + src);
        }
    }

    public sealed interface GroupChild permits GroupChild.Id, GroupChild.Group {
        record Id(int id) implements GroupChild {}
        record Group(GroupElement group) implements GroupChild {}
    }

    public static void saveCurrentModel() {
        if (loadedModel == null) return;

        if (!Files.exists(loadedModelPath)) {
            loadedModelPath.getParent().toFile().mkdirs();
            try {
                Files.createFile(loadedModelPath);
            } catch (IOException e) {
                LogWindow.addError("Failed to create save file for model: " + loadedModelPath.getFileName() + " -> " + e.getMessage());
            }
        }

        try (Writer writer = Files.newBufferedWriter(loadedModelPath)) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(GroupChild.class, new GroupChildAdapter())
                    .create();
            gson.toJson(loadedModel, writer);

            unsavedChanges = false;
        } catch (Exception e) {
            LogWindow.addError("Failed to write save data for model: " + loadedModelPath.getFileName() + " -> " + e.getMessage());
        }

        LogWindow.addLog("Successfully saved model!", LogWindow.LogType.SUCCESS.getColor());
    }

    public static void closeCurrentModel() {
        if (unsavedChanges) {
            ConfirmWindow.open("Do you want to save this file first", "Otherwise, changes might be lost.", () -> {
                saveCurrentModel();

                closeNextFrame = true;
                unsavedChanges = false;
            }, () -> {
                ConfirmWindow.open("Are you sure you want to close this file", "Any unsaved changes might be lost.", () -> {
                    closeNextFrame = true;
                    unsavedChanges = false;
                });
            });
        } else {
            loadedModel = null;
            loadedModelPath = null;
        }
    }

    private static boolean rayIntersectsElement(
            Vector3f rayOrigin,
            Vector3f rayDir,
            ModelElement e
    ) {
        Vector3f localOrigin = new Vector3f(rayOrigin);
        Vector3f localDir = new Vector3f(rayDir);

        if (e.rotation() != null) {
            Rotation r = e.rotation();

            Vector3f pivot = new Vector3f(
                    r.origin().get(0) / 16f - 0.5f,
                    r.origin().get(1) / 16f - 0.5f,
                    r.origin().get(2) / 16f - 0.5f
            );

            localOrigin.sub(pivot);

            float angle = (float) Math.toRadians(-r.angle());
            switch (r.axis()) {
                case "x" -> {
                    localOrigin.rotateX(angle);
                    localDir.rotateX(angle);
                }
                case "y" -> {
                    localOrigin.rotateY(angle);
                    localDir.rotateY(angle);
                }
                case "z" -> {
                    localOrigin.rotateZ(angle);
                    localDir.rotateZ(angle);
                }
            }

            localOrigin.add(pivot);
        }

        Vector3f min = new Vector3f(
                e.from().get(0) / 16f - 0.5f,
                e.from().get(1) / 16f - 0.5f,
                e.from().get(2) / 16f - 0.5f
        );

        Vector3f max = new Vector3f(
                e.to().get(0) / 16f - 0.5f,
                e.to().get(1) / 16f - 0.5f,
                e.to().get(2) / 16f - 0.5f
        );

        return rayIntersectsAABB(localOrigin, localDir, min, max);
    }

    public static boolean rayIntersectsAABB(Vector3f rayOrigin, Vector3f rayDir, Vector3f min, Vector3f max) {
        float tMin = (min.x - rayOrigin.x) / rayDir.x;
        float tMax = (max.x - rayOrigin.x) / rayDir.x;
        if (tMin > tMax) {
            float tmp = tMin;
            tMin = tMax;
            tMax = tmp;
        }

        float tyMin = (min.y - rayOrigin.y) / rayDir.y;
        float tyMax = (max.y - rayOrigin.y) / rayDir.y;
        if (tyMin > tyMax) {
            float tmp = tyMin;
            tyMin = tyMax;
            tyMax = tmp;
        }

        if ((tMin > tyMax) || (tyMin > tMax)) return false;
        if (tyMin > tMin) tMin = tyMin;
        if (tyMax < tMax) tMax = tyMax;

        float tzMin = (min.z - rayOrigin.z) / rayDir.z;
        float tzMax = (max.z - rayOrigin.z) / rayDir.z;
        if (tzMin > tzMax) {
            float tmp = tzMin;
            tzMin = tzMax;
            tzMax = tmp;
        }

        return (!(tMin > tzMax)) && (!(tzMin > tMax));
    }

    public static boolean isModelWindowOpen() {
        return isOpen.get();
    }

    public static boolean isModelWindowFocused() {
        return isOpen.get() && renderedLastFrame;
    }
}
