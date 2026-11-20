package com.ashraf.wmd.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.ashraf.wmd.ClientCloak;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;

@Pseudo
@Mixin(targets = { "dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher",
        "dev.ryanhcode.sable.sublevel.render.dispatcher.FancySubLevelRenderDispatcher" }, remap = false)
public abstract class SableCloakRenderMixin {

    @ModifyVariable(method = { "renderSectionLayer", "renderAfterSections", "renderBlockEntities", "updateCulling" }, at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 0, remap = false)
    private Iterable<ClientSubLevel> wmd$skipCloaked(Iterable<ClientSubLevel> sublevels) {
        if (sublevels == null || !ClientCloak.anyCraftHidden()) return sublevels;
        List<ClientSubLevel> out = new ArrayList<>();
        for (ClientSubLevel s : sublevels) if (s == null || !ClientCloak.craftHidden(s.getUniqueId())) out.add(s);
        return out;
    }
}
