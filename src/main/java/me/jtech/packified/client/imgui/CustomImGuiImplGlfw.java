package me.jtech.packified.client.imgui;

import imgui.glfw.ImGuiImplGlfw;
import me.jtech.packified.Packified;
import me.jtech.packified.client.PackifiedClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.glfw.GLFW.*;

@Environment(EnvType.CLIENT)
public class CustomImGuiImplGlfw extends ImGuiImplGlfw {
    public MouseHandledBy grabbed = null;
    private final double[] grabbedOriginalMouseX = new double[1];
    private final double[] grabbedOriginalMouseY = new double[1];
    private int ignoreMouseMovements = 0;
    private long mainWindowPtr;
    private int grabLinkedKey = -1;

    public MouseHandledBy getMouseHandledBy() {
        return grabbed;
    }

    public static enum MouseHandledBy {
        EDITOR_GRABBED,
        IMGUI,
        GAME,
        BOTH;

        private MouseHandledBy() {
        }

        public boolean allowImgui() {
            return this == IMGUI || this == BOTH;
        }

        public boolean allowGame() {
            return this == GAME || this == BOTH;
        }
    }

    public boolean isGrabbed() {
        return this.grabbed != null;
    }

    public void ungrab() {
//        if (this.grabbed != null) {
//            this.grabbed = MouseHandledBy.IMGUI;
//            GLFW.glfwSetInputMode(this.mainWindowPtr, 208897, 212993);
//            GLFW.glfwSetCursorPos(this.mainWindowPtr, this.grabbedOriginalMouseX[0], this.grabbedOriginalMouseY[0]);
//        }
    }

    public void setGrabbed(boolean passthroughToGame, int grabLinkedKey, boolean releaseGrabOnUp, double x, double y) {
        if (grabLinkedKey != 0) {
            if (grabLinkedKey < 0) {
                if (GLFW.glfwGetMouseButton(this.mainWindowPtr, -grabLinkedKey-1) == GLFW_RELEASE) {
                    this.ungrab();
                    return;
                }
            } else if (GLFW.glfwGetKey(this.mainWindowPtr, grabLinkedKey) == GLFW_RELEASE) {
                this.ungrab();
                return;
            }
        }
        if (this.grabbed != null) return;
        this.grabbed = passthroughToGame ? MouseHandledBy.GAME : MouseHandledBy.EDITOR_GRABBED;

        if (grabLinkedKey != 0) {
            this.grabLinkedKey = grabLinkedKey;
            //this.releaseGrabOnUp = releaseGrabOnUp;
        }
        if (x >= 0 && y >= 0) {
            this.grabbedOriginalMouseX[0] = x;
            this.grabbedOriginalMouseY[0] = y;
        } else {
            GLFW.glfwGetCursorPos(this.mainWindowPtr, this.grabbedOriginalMouseX, this.grabbedOriginalMouseY);
        }
        GLFW.glfwSetInputMode(this.mainWindowPtr, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        this.ignoreMouseMovements = 2;
        MinecraftClient.getInstance().mouse.onResolutionChanged();
    }

//    public MouseHandledBy getMouseHandledBy() {
//        if (!EditorUI.isActive()) {
//            return MouseHandledBy.GAME;
//        } else if (this.grabbed != null) {
//            return this.grabbed;
//        } else {
//            return EditorUI.getIO().getWantCaptureMouse() ? MouseHandledBy.IMGUI : MouseHandledBy.BOTH;
//        }
//    }

    @Override
    public boolean init(long windowId, boolean installCallbacks) {
        this.mainWindowPtr = windowId;
        return super.init(windowId, installCallbacks);
    }
}