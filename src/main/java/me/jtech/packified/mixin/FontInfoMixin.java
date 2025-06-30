package me.jtech.packified.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import me.jtech.packified.client.windows.LogWindow;
import net.minecraft.client.font.FontLoader;
import net.minecraft.client.font.FontManager;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;
import java.util.List;

@Mixin(FontManager.class)
public class FontInfoMixin {
    @Inject(method = "loadFontProviders", at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/Resource;getPackId()Ljava/lang/String;", shift = At.Shift.AFTER))
    private static void packified$logFontLoadError(List<Resource> fontResources, Identifier id, CallbackInfoReturnable<List<Pair<FontManager.FontKey, FontLoader.Provider>>> cir,
                                                   @Local Resource resource) {
        try (Reader reader = resource.getReader()) {
            JsonElement jsonElement = FontManager.GSON.fromJson(reader, JsonElement.class);
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                throw new JsonParseException("Expected a JSON object");
            }
        } catch (Exception e) {
            LogWindow.addWarning(String.format("Unable to load font '%s' in %s in resourcepack: '%s'", id, "fonts.json", resource.getPackId()));
        }
    }
}
