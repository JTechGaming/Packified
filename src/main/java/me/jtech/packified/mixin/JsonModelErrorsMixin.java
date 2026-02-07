package me.jtech.packified.mixin;

import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(ModelTextures.class)
public class JsonModelErrorsMixin {
    @Inject(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Identifier;tryParse(Ljava/lang/String;)Lnet/minecraft/util/Identifier;"), cancellable = true)
    private static void packified$add(String textureId, String spriteId, ModelTextures.Textures.Builder builder, CallbackInfo ci) {
        Identifier identifier = Identifier.tryParse(spriteId);
        if (identifier == null) {
            CornerNotificationsHelper.addNotification("Error loading file", spriteId + " is not valid resource location", new Color(0xff0033), 5.0f);
        }
    }
}
