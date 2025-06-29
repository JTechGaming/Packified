package me.jtech.packified.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.jtech.packified.client.CornerNotificationsHelper;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderProgram.class)
public class ShaderErrorsMixin {
    @Shadow @Final private String name;
    @Shadow @Final private int glRef;

    @Inject(method = "loadReferences", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlUniform;getName()Ljava/lang/String;"))
    private void packified$notifyWarnUniform(CallbackInfo ci, @Local GlUniform glUniform) {
        String string2 = glUniform.getName();
        int k = GlUniform.getUniformLocation(this.glRef, string2);
        if (k == -1) {
            CornerNotificationsHelper.addNotification("Shader Warning", "Shader '" + name + "' Could not find uniform named " + string2,
                    new java.awt.Color(0xffcc00), 5.0f);
        }
    }

    @Inject(method = "loadReferences", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private void packified$notifyWarnSampler(CallbackInfo ci, @Local String string) {
        CornerNotificationsHelper.addNotification("Shader Warning", "Shader '" + name + "' Could not find sampler named " + string,
                new java.awt.Color(0xffcc00), 5.0f);
    }
}
