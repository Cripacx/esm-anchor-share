package com.cripacx.esmanchorshare.mixin;

import com.cripacx.esmanchorshare.client.ServerAnchorCache;
import com.leobeliik.extremesoundmuffler.utils.Anchor;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ESM asks {@link Anchor#getMuffling} for every sound that no global setting muffled. When none of the player's
 * own anchors matched, server anchors get their turn; ESM's on/off toggle and "disable anchors" option still apply.
 */
@Mixin(value = Anchor.class, remap = false)
public abstract class EsmAnchorMixin {

    @Inject(method = "getMuffling", at = @At("RETURN"), cancellable = true)
    private static void esmanchorshare$applyServerAnchors(SoundInstance sound, CallbackInfoReturnable<Double> cir) {
        if (cir.getReturnValue() == 1D) {
            double volume = ServerAnchorCache.muffling(sound);
            if (volume != 1D) cir.setReturnValue(volume);
        }
    }
}
