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
}
