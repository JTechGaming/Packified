package me.jtech.packified.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.jtech.packified.client.windows.LogWindow;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(SpriteAtlasTexture.class)
public class SpriteAtlasInfoMixin {
    @Final
    @Shadow private Identifier id;

    @Shadow private Map<Identifier, Sprite> sprites;

    @Inject(method = "upload", at = @At("HEAD"))
    private void packified$notifyOnAtlasUpload(CallbackInfo ci, @Local(argsOnly = true) SpriteLoader.StitchResult stitchResult) {
        LogWindow.addPackReloadInfo(String.format("Created: %sx%sx%s %s-atlas", stitchResult.width(), stitchResult.height(), stitchResult.mipLevel(), this.id));
    }

    @Inject(method = "upload", at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(Ljava/lang/Object;)Ljava/lang/String;"))
    private void packified$notifyOnAtlasMissingTextureSprite(CallbackInfo ci, @Local(argsOnly = true) SpriteLoader.StitchResult stitchResult) {
        String idValue = String.valueOf(this.id);
        LogWindow.addWarning("Atlas '" + idValue + "' (" + this.sprites.size() + " sprites) has no missing texture sprite");
    }
}
