package me.jtech.packified.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.jtech.packified.client.windows.LogWindow;
import net.minecraft.client.font.UnihexFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.zip.ZipEntry;

@Mixin(UnihexFont.Loader.class)
public class UnihexFontLoaderMixin {
    @Inject(method = "loadHexFile", at = @At(value = "INVOKE", target = "Ljava/util/zip/ZipEntry;getName()Ljava/lang/String;"))
    private void packified$notifyHexFontLoad(InputStream stream, CallbackInfoReturnable<UnihexFont> cir, @Local ZipEntry zipEntry) {
        String string = zipEntry.getName();
        if (string.endsWith(".hex")) {
            LogWindow.addPackReloadInfo(String.format("Found %s, loading", string));
        }
    }
}
