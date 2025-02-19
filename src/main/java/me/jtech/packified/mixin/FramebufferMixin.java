package me.jtech.packified.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import me.jtech.packified.client.WindowSizeTracker;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Environment(EnvType.CLIENT)
@Mixin(value = Framebuffer.class, priority = 800)
public abstract class FramebufferMixin {

    int iFrameWidth = ImGuiImplementation.getFrameWidth();
    int iFrameHeight = ImGuiImplementation.getFrameHeight();
    int frameX = ImGuiImplementation.getFrameX();
    int frameY = ImGuiImplementation.getFrameY();
    int viewportSizeX = ImGuiImplementation.getViewportSizeX();
    int viewportSizeY = ImGuiImplementation.getViewportSizeY();

    @Shadow
    protected int colorAttachment;

    @Inject(method = "draw(II)V", at = @At("HEAD"), cancellable = true)
    public void blitToScreen(int width, int height, CallbackInfo ci) {
        int paddingX = (int) ImGui.getStyle().getWindowPaddingX();
        int paddingY = (int) ImGui.getStyle().getWindowPaddingY();
        viewportSizeX = ImGuiImplementation.getViewportSizeX();
        viewportSizeY = ImGuiImplementation.getViewportSizeY();
        frameX = ImGuiImplementation.getFrameX()-paddingX;
        iFrameWidth = ImGuiImplementation.getFrameWidth()+paddingX*2;
        iFrameHeight = ImGuiImplementation.getFrameHeight()+paddingY*2;
        frameY = paddingY + viewportSizeY - iFrameHeight - ImGuiImplementation.getFrameY();
        if ((Object)this == MinecraftClient.getInstance().getFramebuffer() && ImGuiImplementation.isActive()) {
            RenderSystem.assertOnRenderThread();
            GlStateManager._colorMask(true, true, true, false);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._viewport(frameX, frameY, iFrameWidth, iFrameHeight); // This actually changes the size of the viewport
            GlStateManager._disableBlend();
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            ShaderProgram shaderProgram = (ShaderProgram)Objects.requireNonNull(minecraftClient.gameRenderer.blitScreenProgram, "Blit shader not loaded");
            shaderProgram.addSampler("DiffuseSampler", this.colorAttachment);
            shaderProgram.bind();
            BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.BLIT_SCREEN);
            bufferBuilder.vertex(0.0F, 0.0F, 0.0F);
            bufferBuilder.vertex(1.0F, 0.0F, 0.0F);
            bufferBuilder.vertex(1.0F, 1.0F, 0.0F);
            bufferBuilder.vertex(0.0F, 1.0F, 0.0F);
            BufferRenderer.draw(bufferBuilder.end());
            shaderProgram.unbind();
            GlStateManager._depthMask(true);
            GlStateManager._colorMask(true, true, true, true);
            ci.cancel(); // Cancel the original method with the default behavior
        }
    }

}
