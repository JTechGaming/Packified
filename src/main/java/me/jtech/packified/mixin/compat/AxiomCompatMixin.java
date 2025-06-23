package me.jtech.packified.mixin.compat;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("axiom")
@Pseudo
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public class AxiomCompatMixin {

    @Shadow
    private static boolean enabled;

    @Inject(method = "isActiveInternal", at = @At("HEAD"), cancellable = true)
    private static void isActiveInternal(CallbackInfoReturnable<Boolean> cir) {
        if (ImGuiImplementation.isActiveInternal()) {
            enabled = false;
            cir.setReturnValue(false);
        }
    }
}