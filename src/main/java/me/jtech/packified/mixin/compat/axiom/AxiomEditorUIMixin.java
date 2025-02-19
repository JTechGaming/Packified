package me.jtech.packified.mixin.compat.axiom;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import me.jtech.packified.client.imgui.ImGuiImplementation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@IfModLoaded("axiom")
@Pseudo
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public class AxiomEditorUIMixin {

    @Shadow
    private static boolean enabled;

    @Inject(method = "isActiveInternal", at = @At("HEAD"), cancellable = true)
    private static void isActiveInternal(CallbackInfoReturnable<Boolean> cir) {
        if (ImGuiImplementation.isActive()) {
            enabled = false;
            cir.setReturnValue(false);
        }
    }

}
