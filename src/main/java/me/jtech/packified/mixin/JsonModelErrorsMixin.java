package me.jtech.packified.mixin;

import com.mojang.datafixers.util.Either;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

@Mixin(JsonUnbakedModel.Deserializer.class)
public class JsonModelErrorsMixin {
    @Inject(method = "resolveReference", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Identifier;tryParse(Ljava/lang/String;)Lnet/minecraft/util/Identifier;"), cancellable = true)
    private static void packified$resolveReference(Identifier id, String name, CallbackInfoReturnable<Either<SpriteIdentifier, String>> cir) {
        Identifier identifier = Identifier.tryParse(name);
        if (identifier == null) {
            CornerNotificationsHelper.addNotification("Error loading file", name + " is not valid resource location", new Color(0xff0033), 5.0f);
        }
    }
}
