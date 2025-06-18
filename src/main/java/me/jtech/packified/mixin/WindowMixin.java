package me.jtech.packified.mixin;

import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin { //todo vertical mouse pos fix works, but horizontal mouse pos fix doesn't work, need to figure out why
    @Shadow
    public int framebufferWidth;
    @Shadow
    public int framebufferHeight;

    @Shadow
    public int width;
    @Shadow
    public int height;

    @Shadow
    private int windowedWidth;

    @Unique
    private float calculateWidthScaleFactor() {
        return Math.max(1/8f, Math.min(8f, (float) this.framebufferWidth / this.width));
    }

    @Unique
    private float calculateHeightScaleFactor() {
        return Math.max(1/8f, Math.min(8f, (float) this.framebufferHeight / this.height));
    }

    @Inject(method = "getWidth", at=@At("HEAD"), cancellable = true)
    public void getWidth(CallbackInfoReturnable<Integer> cir) {
        if (ImGuiImplementation.isActive()) {
            cir.setReturnValue(ImGuiImplementation.getNewGameWidth(this.calculateWidthScaleFactor()));
        }
    }

    @Inject(method = "getHeight", at=@At("HEAD"), cancellable = true)
    public void getHeight(CallbackInfoReturnable<Integer> cir) {
        if (ImGuiImplementation.isActive()) {
            cir.setReturnValue(ImGuiImplementation.getNewGameHeight(this.calculateHeightScaleFactor()));
        }
    }
}
