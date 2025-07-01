package me.jtech.packified.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import me.jtech.packified.client.helpers.WindowSizeTracker;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
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

    @Shadow
    protected int colorAttachment;

    @Inject(method = "draw(II)V", at = @At("HEAD"), cancellable = true)
    public void blitToScreen(int width, int height, CallbackInfo ci) {
        if ((Object)this == MinecraftClient.getInstance().getFramebuffer() && ImGuiImplementation.isActive()) {
            var window = MinecraftClient.getInstance().getWindow();
            int paddingX = (int) ImGui.getStyle().getWindowPaddingX();
            int paddingY = (int) ImGui.getStyle().getWindowPaddingY();
            float frameLeft = (float) (ImGuiImplementation.getFrameX()-paddingX) / ImGuiImplementation.getViewportSizeX();
            float frameTop = (float) (ImGuiImplementation.getFrameY()-paddingY) / ImGuiImplementation.getViewportSizeY();
            float frameWidth = (float) Math.max(1, (ImGuiImplementation.getFrameWidth()+paddingX*2)) / ImGuiImplementation.getViewportSizeX();
            float frameHeight = (float) Math.max(1, (ImGuiImplementation.getFrameHeight()+paddingY*2)) / ImGuiImplementation.getViewportSizeY();

            int realWidth = WindowSizeTracker.getWidth(window);
            int realHeight = WindowSizeTracker.getHeight(window);

            RenderSystem.assertOnRenderThread();
            GlStateManager._colorMask(true, true, true, false);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._viewport((int)(realWidth * frameLeft), (int)(realHeight * (1 - (frameTop+frameHeight))),
                    Math.max(1, (int)(realWidth * frameWidth)), Math.max(1, (int)(realHeight * frameHeight)));
            GlStateManager._disableBlend();
            ShaderProgram shaderInstance = Objects.requireNonNull(
                    RenderSystem.setShader(ShaderProgramKeys.BLIT_SCREEN), "Blit shader not loaded"
            );
            shaderInstance.addSamplerTexture("InSampler", this.colorAttachment);
            shaderInstance.bind();
            BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.BLIT_SCREEN);
            bufferBuilder.vertex(0.0f, 0.0f, 0.0f);
            bufferBuilder.vertex(1.0f, 0.0f, 0.0f);
            bufferBuilder.vertex(1.0f, 1.0f, 0.0f);
            bufferBuilder.vertex(0.0f, 1.0f, 0.0f);
            BufferRenderer.draw(bufferBuilder.end());
            shaderInstance.unbind();
            GlStateManager._depthMask(true);
            GlStateManager._colorMask(true, true, true, true);
            ci.cancel();
        }
    }

}
