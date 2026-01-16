package me.jtech.packified.mixin;

import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePackOrganizer.AbstractPack.class)
public class test {
    @Inject(method = "getIconId", at = @At("RETURN"))
    private void getIconId(CallbackInfoReturnable<Identifier> cir) {
        //System.out.println(cir.getReturnValue());
    }
}
