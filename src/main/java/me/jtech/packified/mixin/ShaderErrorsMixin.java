package me.jtech.packified.mixin;

import com.google.common.collect.ImmutableMap;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import net.minecraft.client.gl.ShaderLoader;
//import net.minecraft.client.gl.ShaderProgramDefinition;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(ShaderLoader.class)
public class ShaderErrorsMixin {

//    @Inject(method = "loadDefinition", at = @At("TAIL"))
//    private static void packified$notifyShaderWarnings(Identifier id, Resource resource, ImmutableMap.Builder<Identifier, ShaderProgramDefinition> builder, CallbackInfo ci) {
//        ShaderProgramDefinition definition = builder.build().get(id);
//        if (definition != null) {
//            definition.uniforms().forEach(uniform -> {
//                if (uniform.values().isEmpty()) {
//                    CornerNotificationsHelper.addNotification(
//                            "Shader Warning",
//                            "Shader '" + id + "' could not find uniform named " + uniform.name(),
//                            new Color(0xffcc00),
//                            5.0f
//                    );
//                } else {
//                    definition.samplers().forEach(sampler -> {
//                        // Notify about the sampler name
//                        CornerNotificationsHelper.addNotification(
//                                "Shader Info",
//                                "Shader '" + id + "' has sampler named " + sampler.name(),
//                                new Color(0x00ccff),
//                                5.0f
//                        );
//                    });
//                }
//            });
//
//            definition.samplers().forEach(sampler -> {
//                // Notify about the sampler name
//                CornerNotificationsHelper.addNotification(
//                        "Shader Info",
//                        "Shader '" + id + "' has sampler named " + sampler.name(),
//                        new Color(0x00ccff),
//                        5.0f
//                );
//            });
//        }
//    }
}
