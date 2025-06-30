package me.jtech.packified.mixin;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.jtech.packified.client.CornerNotificationsHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.entity.LoadedBlockEntityModels;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.render.model.SpriteAtlasManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;
import java.util.Map;
import java.util.stream.Collectors;

@Mixin(BakedModelManager.class)
public class ModelTextureErrorsMixin {
    @Inject(method = "bake", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/BakedModelManager;toStateMap(Ljava/util/Map;Lnet/minecraft/client/render/model/BakedModel;)Ljava/util/Map;"))
    private static void packified$logMissingTextures(CallbackInfoReturnable<BakedModelManager.BakingResult> cir) {
        // Log missing textures
        Multimap<String, SpriteIdentifier> multimap = HashMultimap.create();
        Multimap<String, String> multimap2 = HashMultimap.create();

        multimap.asMap().forEach((modelName, sprites) -> {
            String missingTextures = sprites.stream()
                    .sorted(SpriteIdentifier.COMPARATOR)
                    .map(spriteId -> "    " + spriteId.getAtlasId() + ":" + spriteId.getTextureId())
                    .collect(Collectors.joining("\n"));
            CornerNotificationsHelper.addNotification(
                    "Missing Textures",
                    "Model: " + modelName + "\n" + missingTextures,
                    new Color(0xff0033),
                    5.0f
            );
        });

        multimap2.asMap().forEach((modelName, textureIds) -> {
            String missingReferences = textureIds.stream()
                    .sorted()
                    .map(textureId -> "    " + textureId)
                    .collect(Collectors.joining("\n"));
            CornerNotificationsHelper.addNotification(
                    "Missing Texture References",
                    "Model: " + modelName + "\n" + missingReferences,
                    new Color(0xff0033),
                    5.0f
            );
        });
    }
}
