package me.jtech.packified.mixin.compat;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("malilib")
@Pseudo
@Mixin(targets = "fi.dy.masa.malilib.event.InputEventHandler")
public class MalilibCompatMixin {
    @Inject(method = "onKeyInput", at = @At("HEAD"), cancellable = true)
    private static void onKeyInput(int keyCode, int scanCode, int modifiers, int action, MinecraftClient mc, CallbackInfoReturnable<Boolean> cir) {
        if (ImGuiImplementation.isActiveInternal()) {
            cir.setReturnValue(false);
        }
    }
}
