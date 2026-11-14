package com.ashraf.wmd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ashraf.wmd.ClientPodThermal;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

@Mixin(Minecraft.class)
public abstract class GlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void wmd$thermalGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ClientPodThermal.glows(entity)) cir.setReturnValue(true);
    }
}
