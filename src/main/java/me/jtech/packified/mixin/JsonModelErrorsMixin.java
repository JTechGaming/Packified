package me.jtech.packified.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import me.jtech.packified.client.CornerNotificationsHelper;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

@Mixin(ModelTextures.class)
public class JsonModelErrorsMixin {
    @Inject(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Identifier;tryParse(Ljava/lang/String;)Lnet/minecraft/util/Identifier;"), cancellable = true)
    private static void packified$add(Identifier atlasTexture, String textureId, String value, ModelTextures.Textures.Builder builder, CallbackInfo ci) {
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) {
            CornerNotificationsHelper.addNotification("Error loading file", value + " is not valid resource location", new Color(0xff0033), 5.0f);
        }
    }
}
