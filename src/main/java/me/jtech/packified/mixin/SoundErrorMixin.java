package me.jtech.packified.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.jtech.packified.client.helpers.CornerNotificationsHelper;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class SoundErrorMixin {
    @Final
    @Shadow
    private SoundManager loader;

    @Inject(method = "reloadSounds", at = @At(value = "INVOKE", target = "Lnet/minecraft/sound/SoundEvent;id()Lnet/minecraft/util/Identifier;"))
    private void packified$reloadSounds(CallbackInfo ci, @Local SoundEvent soundEvent) {
        Identifier identifier = soundEvent.id();
        if (this.loader.get(identifier) == null) {
            CornerNotificationsHelper.addNotification("Sound Warning", "Missing sound for event: " + Registries.SOUND_EVENT.getId(soundEvent),
                    new java.awt.Color(0xffcc00), 5.0f);
        }
    }
}
