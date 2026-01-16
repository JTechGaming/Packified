package me.jtech.packified.client.windows;

import com.google.gson.*;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import me.jtech.packified.client.PackifiedClient;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ModelEditorWindow {
    // Data classes for minecraft model JSON
    public record Rotation(float angle, String axis, List<Float> origin) { }
    public record ModelElement(String name, List<Float> from, List<Float> to, Map<String, Face> faces, Rotation rotation) { }
    record Face(String texture, List<Float> uv) { }
    record DisplayElement(List<Float> rotation, List<Float> translation, List<Float> scale) { }
    record GroupElement(String name, List<Float> origin, int color, List<GroupChild> children) { }
    record BlockModel(String format_version, int[] texture_size, Map<String, String> textures, List<ModelElement> elements, Map<String, DisplayElement> display, List<GroupElement> groups) { }

    // OpenGL data
    private static int fbo = 0;
    private static int colorTex = 0;
    private static int depthRbo = 0;
    private static int fbWidth = 0, fbHeight = 0;
    private static int shaderProgram = 0;

    // camera state
    private static float camDistance = 5.0f;
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
    private static int gridShader = 0;
    private static int gridVertexCount = 0;
    private static boolean gridInitialized = false;

    private static Path selectedTexturePath;

    public static ImBoolean isOpen = new ImBoolean(false);

    // model data
    private static class Cube {
        float[] vertices; // pos(3) + color(3)
        int[] indices;
        int vao, vbo, ebo;
    }
    private static class Mesh {
        float[] vertices; // pos(3) + color(3)
        int[] indices;
    }
    private static boolean modelUploaded = false;
    private static final List<Cube> cubes = new ArrayList<>();
    private static final Map<String, BufferedImage> loadedTextures = new java.util.HashMap<>();
    private static BlockModel loadedModel;

    private static final Matrix4f mvp = new Matrix4f();

    public static void loadModel(String path) {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(GroupChild.class, new GroupChildDeserializer())
                    .create();
            loadedModel = gson.fromJson(reader, BlockModel.class);
            loadTextures();
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

        if (ImGui.begin("Model Editor", isOpen,ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            int targetW = (int) Math.max(1, ImGui.getWindowContentRegionMaxX() - ImGui.getWindowContentRegionMinX() + ImGui.getStyle().getWindowPaddingX() + 25);
            int targetH = (int) Math.max(1, ImGui.getWindowContentRegionMaxY() - ImGui.getWindowContentRegionMinY() + ImGui.getStyle().getWindowPaddingY() + 21);

            ensureFramebuffer(targetW, targetH);

            float aspect = (fbHeight == 0) ? 1.0f : (float) fbWidth / (float) fbHeight;
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70.0f), aspect, 0.1f, 100f);
            Matrix4f view = getViewMatrix();
            Matrix4f model = new Matrix4f().identity(); // you can scale/rotate model here if needed

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
                        boolean intersects = rayIntersectsAABB(rayOrigin, rayDir, min, max);
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
                        //System.out.println("Picked element=" + selectedElementIndex);
                    }
                }
            }

            // Now process camera input — only when the image item is hovered (prevents dragging the window)
            updateCameraAfterImage();

            renderSideBars();
        }

        ImGui.end();
    }

    private static int j = 0;

    private static void renderSideBars() {
        ImGui.begin("Model Elements");

        alreadyRenderedHoverThisFrame = false;

        if (loadedModel != null) {
            // Hierarchy with groups
            j = 0;
            if (loadedModel.groups() != null && !loadedModel.groups().isEmpty()) {
                for (GroupChild child : loadedModel.groups().getFirst().children()) {
                    renderGroupChild(child, 0);
                }
            } else {
                for (int i = 0; i < loadedModel.elements().size(); i++) {
                    ModelElement e = loadedModel.elements().get(i);
                    String label = e.name != null ? e.name() : "cube";
                    if (ImGui.selectable(label, selectedElementIndex == i)) {
                        previouslySelectedElementIndex = selectedElementIndex;
                        selectedElementIndex = i;
                    }
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

            if (ImGui.inputFloat3("Position", position)) {
                e.from().set(0, position[0]);
                e.from().set(1, position[1]);
                e.from().set(2, position[2]);
                e.to().set(0, position[0] + size[0]);
                e.to().set(1, position[1] + size[1]);
                e.to().set(2, position[2] + size[2]);
            }
            if (ImGui.inputFloat3("Size", size)) {
                e.to().set(0, position[0] + size[0]);
                e.to().set(1, position[1] + size[1]);
                e.to().set(2, position[2] + size[2]);
            }
            if (ImGui.inputFloat3("Origin", pivot)) {
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
            }
            if (ImGui.inputFloat3("Rotation", rotation)) {
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
            }

            // Update the elements in the model
            List<ModelElement> elements = new ArrayList<>(loadedModel.elements());
            elements.set(selectedElementIndex, e);
            loadedModel = new BlockModel(loadedModel.format_version(), loadedModel.texture_size(), loadedModel.textures(), elements, loadedModel.display(), loadedModel.groups());
            modelUploaded = false; // force re-upload of model to GPU next frame
        } else {
            ImGuiImplementation.centeredText("No element selected.");
        }
        ImGui.end();

        ImGui.begin("Textures");
        if (!loadedTextures.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, BufferedImage> entry : loadedTextures.entrySet()) {
                String key = entry.getKey();
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
        } else {
            ImGuiImplementation.centeredText("No textures loaded.");
        }
        ImGui.end();

        ImGui.begin("UV Editor");
        if (selectedTexturePath != null) {
            int textureId = SafeTextureLoader.load(selectedTexturePath);
            int canvasSize = (int) Math.min(ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY());
            if (textureId != -1) {
                ImGui.image(textureId, canvasSize, canvasSize);
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

    private static void renderGroupChild(GroupChild child, int i) {
        j++;
        if (child instanceof GroupChild.Group) {
            GroupElement group = ((GroupChild.Group) child).group();
            boolean nodeOpen = ImGui.treeNodeEx(group.name()+"##"+j, ImGuiTreeNodeFlags.SpanFullWidth);
            if (nodeOpen) {
                for (GroupChild subChild : group.children()) {
                    renderGroupChild(subChild, i + 1);
                }
                ImGui.treePop();
            }
        } else if (child instanceof GroupChild.Id) {
            int id = ((GroupChild.Id) child).id();
            if (id < 0 || id >= loadedModel.elements().size()) return;
            ModelElement e = loadedModel.elements().get(id);
            String label = e.name != null ? e.name() : "cube##" + id;
            if (ImGui.selectable(label, selectedElementIndex == loadedModel.elements().indexOf(e))) {
                previouslySelectedElementIndex = selectedElementIndex;
                selectedElementIndex = loadedModel.elements().indexOf(e);
            }
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
        boolean leftDown = ImGui.isItemHovered() && ImGui.isMouseDown(0);
        boolean shiftDown = ImGui.getIO().getKeyShift();

        // Start capture
        if (leftDown && !capturingMouse) {
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
        if (!leftDown && capturingMouse) {
            capturingMouse = false;
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }

        if (leftDown) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(windowHandle, xpos, ypos);

            float dx = (float) (xpos[0] - lastMouseX);
            float dy = (float) (ypos[0] - lastMouseY);

            lastMouseX = xpos[0];
            lastMouseY = ypos[0];

            if (shiftDown) {
                // --- Pan ---
                float panSpeed = 0.01f * camDistance;
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
            camDistance += wheel * -0.5f;
            camDistance = Math.max(1.0f, Math.min(50.0f, camDistance));
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

        // draw three colored lines with your existing color shader: X red, Y green, Z blue
        // Create a small temporary line VBO or use immediate GL_LINES if acceptable
        GL20.glUseProgram(shaderProgram);
        int mvpLoc = GL20.glGetUniformLocation(shaderProgram, "uMVP");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            ModelEditorWindow.mvp.get(fb);
            GL20.glUniformMatrix4fv(mvpLoc, false, fb);
        }
        // build simple lines in a float[] and draw them (pos+color) as you do for grid
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
        // upload to a temporary buffer and draw as GL_LINES (copy pattern from setupGrid)
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
        shaderProgram = createShader();

        // Use program BEFORE setting uniforms
        GL20.glUseProgram(shaderProgram);

        int loc = GL20.glGetUniformLocation(shaderProgram, "uMVP");
        if (loc >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                mvp.get(fb);
                GL20.glUniformMatrix4fv(loc, false, fb);
            }
        }

        renderGrid();

        if (loadedModel != null) {
            renderBlockModel(loadedModel);

            renderGizmo();
        }
    }

    private static void setupGrid() {
        // Number of lines: (2N+1) along X and Z
        int totalLines = (2 * 10 + 1) * 2 * 2; // 2 vertices per line, 2 axes
        float[] verts = new float[totalLines * 6]; // 3 pos + 3 color per vertex
        int idx = 0;

        for (int i = -10; i <= 10; i++) {
            // color: center line vs regular
            float[] color = (i == 0) ? new float[]{1f, 1f, 1f} : new float[]{0.5f, 0.5f, 0.5f};

            // Line parallel to Z (constant X)
            verts[idx++] = i * (float) 1.0;
            verts[idx++] = 0;
            verts[idx++] = -10 * (float) 1.0;
            verts[idx++] = color[0];
            verts[idx++] = color[1];
            verts[idx++] = color[2];
            verts[idx++] = i * (float) 1.0;
            verts[idx++] = 0;
            verts[idx++] = 10 * (float) 1.0;
            verts[idx++] = color[0];
            verts[idx++] = color[1];
            verts[idx++] = color[2];

            // Line parallel to X (constant Z)
            verts[idx++] = -10 * (float) 1.0;
            verts[idx++] = 0;
            verts[idx++] = i * (float) 1.0;
            verts[idx++] = color[0];
            verts[idx++] = color[1];
            verts[idx++] = color[2];
            verts[idx++] = 10 * (float) 1.0;
            verts[idx++] = 0;
            verts[idx++] = i * (float) 1.0;
            verts[idx++] = color[0];
            verts[idx++] = color[1];
            verts[idx++] = color[2];
        }

        gridVertexCount = totalLines;

        // Create VAO/VBO
        gridVao = GL30.glGenVertexArrays();
        int gridVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(gridVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, gridVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);

        int stride = 6 * Float.BYTES;

        // Position
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(0);
        // Color
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL30.glBindVertexArray(0);

        // Shader
        gridShader = createShader(); // can reuse your previous shader
        gridInitialized = true;
    }

    private static void renderGrid() {
        if (!gridInitialized) {
            setupGrid(); // 10 lines each side, spacing 1.0
        }
        GL20.glUseProgram(gridShader);

        int loc = GL20.glGetUniformLocation(gridShader, "uMVP");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            ModelEditorWindow.mvp.get(fb);
            GL20.glUniformMatrix4fv(loc, false, fb);
        }

        GL30.glBindVertexArray(gridVao);
        GL11.glDrawArrays(GL11.GL_LINES, 0, gridVertexCount);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);
    }

    private static Mesh buildCube(Vector3f from, Vector3f to, Rotation rotation, Map<String, Face> faces) {
        float[][] positions = calcPositions(from, to);

        // Per-face colors based on texture (if any)
        float[][] colors = new float[6][3];
        String[] faceNames = {"north", "east", "south", "west", "up", "down"};
        for (int i = 0; i < 6; i++) {
            Face face = faces.get(faceNames[i]);
            if (face != null && face.texture() != null) {
                BufferedImage tex = loadedTextures.get(face.texture().substring(1)); // remove leading #
                if (tex != null) {
                    // take color from uv
                    List<Float> uv = face.uv();
                    int u = Math.min(tex.getWidth() - 1, Math.max(0, Math.round(uv.get(0))));
                    int v = Math.min(tex.getHeight() - 1, Math.max(0, Math.round(uv.get(1))));
                    int rgb = tex.getRGB(u, v);
                    float r = ((rgb >> 16) & 0xFF) / 255f;
                    float g = ((rgb >> 8) & 0xFF) / 255f;
                    float b = (rgb & 0xFF) / 255f;
                    colors[i] = new float[]{r, g, b};
                } else {
                    colors[i] = new float[]{0.8f, 0.8f, 0.8f}; // default gray
                }
            } else {
                colors[i] = new float[]{0.8f, 0.8f, 0.8f}; // default gray
            }
        }

        // Apply rotation if any
        if (rotation != null) {
            Vector3f rotOrigin = new Vector3f(rotation.origin.get(0) / 16f - 0.5f,
                    rotation.origin.get(1) / 16f - 0.5f,
                    rotation.origin.get(2) / 16f - 0.5f);
            float angleRad = (float) Math.toRadians(rotation.angle);
            Vector3f axis = switch (rotation.axis) {
                case "x" -> new Vector3f(1, 0, 0);
                case "z" -> new Vector3f(0, 0, 1);
                default -> new Vector3f(0, 1, 0); // y
            };
            for (int i = 0; i < positions.length; i++) {
                Vector3f pos = new Vector3f(positions[i][0], positions[i][1], positions[i][2]);
                pos.sub(rotOrigin).rotateAxis(angleRad, axis.x, axis.y, axis.z).add(rotOrigin);
                positions[i][0] = pos.x;
                positions[i][1] = pos.y;
                positions[i][2] = pos.z;
            }
        }

        float[] verts = new float[24 * 6]; // pos(3) + color(3)
        int vi = 0;
        for (int face = 0; face < 6; face++) {
            for (int v = 0; v < 4; v++) {
                float[] pos = positions[face * 4 + v];
                float[] col = colors[face];
                verts[vi++] = pos[0];
                verts[vi++] = pos[1];
                verts[vi++] = pos[2];
                verts[vi++] = col[0];
                verts[vi++] = col[1];
                verts[vi++] = col[2];
            }
        }

        // 6 faces × 2 triangles × 3 indices = 36
        int[] idx = new int[36];
        int ii = 0;
        for (int face = 0; face < 6; face++) {
            int base = face * 4;
            idx[ii++] = base;
            idx[ii++] = base + 1;
            idx[ii++] = base + 2;
            idx[ii++] = base;
            idx[ii++] = base + 2;
            idx[ii++] = base + 3;
        }

        Mesh m = new Mesh();
        m.vertices = verts;
        m.indices = idx;
        return m;
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
        cubes.clear();
        if (model == null || model.elements() == null || model.elements().isEmpty()) return;
        for (ModelElement e : model.elements()) {
            Vector3f from = new Vector3f(e.from().get(0), e.from().get(1), e.from().get(2));
            Vector3f to = new Vector3f(e.to().get(0), e.to().get(1), e.to().get(2));
            Mesh cube = buildCube(from, to, e.rotation(), e.faces());
            Cube c = new Cube();
            c.vertices = cube.vertices;
            c.indices = cube.indices;
            c.vao = GL30.glGenVertexArrays();
            c.vbo = GL15.glGenBuffers();
            c.ebo = GL15.glGenBuffers();
            GL30.glBindVertexArray(c.vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, c.vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, c.vertices, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, c.ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, c.indices, GL15.GL_STATIC_DRAW);
            int stride = 6 * Float.BYTES;
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
            GL30.glBindVertexArray(0);
            cubes.add(c);
        }

        modelUploaded = true;
    }

    private static void renderBlockModel(BlockModel model) {
        if (!modelUploaded) {
            uploadBlockModel(model);
        }
        GL20.glUseProgram(shaderProgram);
        int loc = GL20.glGetUniformLocation(shaderProgram, "uMVP");
        if (loc >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                ModelEditorWindow.mvp.get(fb);
                GL20.glUniformMatrix4fv(loc, false, fb);
            }
        }
        for (Cube c : cubes) {
            // See if the cube is selected
            boolean isSelected = cubes.indexOf(c) == selectedElementIndex;
            boolean wasSelected = cubes.indexOf(c) == previouslySelectedElementIndex;
            if (isSelected) {
                for (int i = 0; i < c.vertices.length / 6; i++) {
                    c.vertices[i * 6 + 3] = 1.0f; // R
                    c.vertices[i * 6 + 4] = 1.0f; // G
                    c.vertices[i * 6 + 5] = 0.0f; // B
                }
                // Re-upload modified vertex data
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, c.vbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, c.vertices, GL15.GL_STATIC_DRAW);
            }
//            if (wasSelected) {
//                for (int i = 0; i < c.vertices.length / 6; i++) {
//                    c.vertices[i * 6 + 3] -= 0.3f; // R
//                    c.vertices[i * 6 + 4] -= 0.3f; // G
//                    c.vertices[i * 6 + 5] -= 0.0f; // B
//                }
//                // Re-upload modified vertex data
//                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, c.vbo);
//                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, c.vertices, GL15.GL_STATIC_DRAW);
//            }

            GL30.glBindVertexArray(c.vao);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, c.ebo); // If vbo is used for indices, otherwise bind the correct EBO
            GL11.glDrawElements(GL11.GL_TRIANGLES, c.indices.length, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
        }
        GL20.glUseProgram(0);
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

    private static int createShader() {
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

    public static class GroupChildDeserializer implements JsonDeserializer<GroupChild> {
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
    }

    public sealed interface GroupChild permits GroupChild.Id, GroupChild.Group {
        record Id(int id) implements GroupChild {}
        record Group(GroupElement group) implements GroupChild {}
    }
}
