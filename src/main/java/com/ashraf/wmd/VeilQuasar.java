package com.ashraf.wmd;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;
import net.minecraft.resources.ResourceLocation;

final class VeilQuasar {

    private VeilQuasar() {}

    static void spawn(String emitter, double x, double y, double z) {
        ParticleSystemManager mgr = VeilRenderSystem.renderer().getParticleManager();
        ParticleEmitter e = mgr.createEmitter(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, emitter));
        if (e == null) return;
        e.setPosition(x, y, z);
        mgr.addParticleSystem(e);
    }
}
