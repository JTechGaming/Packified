package me.jtech.packified.mixin;

import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin {
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

    @Shadow @Final private long handle;

    @Shadow private double scaleFactor;

    @Shadow private int scaledWidth;
    @Shadow private int scaledHeight;

    @Unique
    private float calculateWidthScaleFactor() {
        return Math.max(1/8f, Math.min(8f, (float) this.framebufferWidth / this.width));
    }

    @Unique
    private float calculateHeightScaleFactor() {
        return Math.max(1/8f, Math.min(8f, (float) this.framebufferHeight / this.height));
    }

//    @Inject(method = "getFramebufferWidth", at=@At("HEAD"), cancellable = true)
//    public void getFramebufferWidth(CallbackInfoReturnable<Integer> cir) {
//        if (ImGuiImplementation.shouldModifyViewport()) {
//            cir.setReturnValue(ImGuiImplementation.getNewGameWidth(this.calculateWidthScaleFactor()));
//        }
//    }
//
//    @Inject(method = "getFramebufferHeight", at=@At("HEAD"), cancellable = true)
//    public void getFramebufferHeight(CallbackInfoReturnable<Integer> cir) {
//        if (ImGuiImplementation.shouldModifyViewport()) {
//            cir.setReturnValue(ImGuiImplementation.getNewGameHeight(this.calculateHeightScaleFactor()));
//        }
//    }

    @Inject(method = "getWidth", at=@At("HEAD"), cancellable = true)
    public void getScreenWidth(CallbackInfoReturnable<Integer> cir) {
        if (ImGuiImplementation.shouldModifyViewport()) {
            cir.setReturnValue(ImGuiImplementation.getNewGameWidth(1));
        }
    }

    @Inject(method = "getHeight", at=@At("HEAD"), cancellable = true)
    public void getScreenHeight(CallbackInfoReturnable<Integer> cir) {
        if (ImGuiImplementation.shouldModifyViewport()) {
            cir.setReturnValue(ImGuiImplementation.getNewGameHeight(1));
        }
    }

//    @Inject(method = "onWindowSizeChanged", at=@At("HEAD"), cancellable = true)
//    public void onWindowSizeChanged(long l, int i, int j, CallbackInfo ci) {
//        if (l != this.handle) {
//            ci.cancel();
//        }
//    }
//
//    @Inject(method = "calculateScaleFactor", at=@At("HEAD"), cancellable = true)
//    public void calculateScaleFactor(int scale, boolean forceEven, CallbackInfoReturnable<Integer> cir) {
//        if (ImGuiImplementation.shouldModifyViewport()) {
//            int fbw = ImGuiImplementation.getNewGameWidth(this.calculateWidthScaleFactor());
//            int fbh = ImGuiImplementation.getNewGameHeight(this.calculateHeightScaleFactor());
//
//            int j = 1;
//            while (j != scale && j < fbw && j < fbh && fbw / (j + 1) >= 320 && fbh / (j + 1) >= 240) {
//                j++;
//            }
//            if (forceEven && j % 2 != 0) {
//                j++;
//            }
//            cir.setReturnValue(j);
//        }
//    }
//
//    @Inject(method = "setScaleFactor", at=@At("HEAD"), cancellable = true)
//    public void setScaleFactor(double d, CallbackInfo ci) {
//        if (ImGuiImplementation.shouldModifyViewport()) {
//            int fbw = ImGuiImplementation.getNewGameWidth(this.calculateWidthScaleFactor());
//            int fbh = ImGuiImplementation.getNewGameHeight(this.calculateHeightScaleFactor());
//
//            this.scaleFactor = d;
//            int i = (int)((double)fbw / d);
//            this.scaledWidth = (double)fbw / d > (double)i ? i + 1 : i;
//            int j = (int)((double)fbh / d);
//            this.scaledHeight = (double)fbh / d > (double)j ? j + 1 : j;
//
//            ci.cancel();
//        }
//    }
}
