package me.jtech.packified.mixin;

import imgui.ImGui;
import me.jtech.packified.client.helpers.WindowSizeTracker;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = Framebuffer.class, priority = 800)
public abstract class FramebufferMixin {
    @Inject(method = "blitToScreen()V", at = @At("HEAD"), cancellable = true)
    public void blitToScreen(CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().getFramebuffer() && ImGuiImplementation.isActive()) {
            var window = MinecraftClient.getInstance().getWindow();
            int paddingX = (int) ImGui.getStyle().getWindowPaddingX();
            int paddingY = (int) ImGui.getStyle().getWindowPaddingY();
            float frameLeft = (float) (ImGuiImplementation.getFrameX() - paddingX) / ImGuiImplementation.getViewportSizeX();
            float frameTop = (float) (ImGuiImplementation.getFrameY() - paddingY) / ImGuiImplementation.getViewportSizeY();
            float frameWidth = (float) Math.max(1, (ImGuiImplementation.getFrameWidth() + paddingX * 2)) / ImGuiImplementation.getViewportSizeX();
            float frameHeight = (float) Math.max(1, (ImGuiImplementation.getFrameHeight() + paddingY * 2)) / ImGuiImplementation.getViewportSizeY();

            ImGuiImplementation.blit((Framebuffer) (Object) this, WindowSizeTracker.getWidth(window), WindowSizeTracker.getHeight(window),
                    frameLeft, frameTop,
                    frameLeft+frameWidth, frameTop+frameHeight);
            ci.cancel();
        }
    }
}
