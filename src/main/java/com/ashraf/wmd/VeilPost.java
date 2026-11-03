package com.ashraf.wmd;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.resources.ResourceLocation;

final class VeilPost {

    private VeilPost() {}

    private static final ResourceLocation PIPE = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "blast");

    private static float bloom, haze;
    private static double hx, hy, hz, hr;
    private static boolean shockOn;
    private static float lens;
    private static double lx, ly, lz, lr;
    private static double sx, sy, sz;
    private static long shockStart;
    private static final double SHOCK_SPEED = 90.0;
    private static final double SHOCK_LIFE = 3.0;

    private static boolean registered;

    private static void register() {
        if (registered) return;
        registered = true;
        VeilEventPlatform.INSTANCE.preVeilPostProcessing((name, pipeline, context) -> {
            if (!PIPE.equals(name)) return;
            set(pipeline.getUniform("BloomStrength"), bloom);
            set(pipeline.getUniform("HazeStrength"), haze);
            ShaderUniformAccess h = pipeline.getUniform("Haze");
            if (h != null) h.setVector((float) hx, (float) hy, (float) hz, (float) hr);
            double t = (System.nanoTime() - shockStart) / 1.0E9;
            boolean on = shockOn && t < SHOCK_LIFE;
            float fade = on ? (float) Math.pow(1.0 - t / SHOCK_LIFE, 1.4) : 0.0F;
            double radius = t * SHOCK_SPEED;
            ShaderUniformAccess s = pipeline.getUniform("Shock");
            if (s != null) s.setVector((float) sx, (float) sy, (float) sz, (float) radius);
            ShaderUniformAccess p = pipeline.getUniform("ShockParams");
            if (p != null) p.setVector((float) (3.0 + radius * 0.1), fade, 0.0F, 0.0F);
            ShaderUniformAccess lu = pipeline.getUniform("Lens");
            if (lu != null) lu.setVector((float) lx, (float) ly, (float) lz, (float) lr);
            ShaderUniformAccess lp = pipeline.getUniform("LensParams");
            if (lp != null) lp.setVector(1.7F, lens, 0.0F, 0.0F);
        });
    }

    private static void set(ShaderUniformAccess u, float v) {
        if (u != null) u.setFloat(v);
    }

    static void bloom(float strength) { bloom = Math.max(bloom, strength); }

    static void haze(double x, double y, double z, double radius, float strength) {
        if (ClientCutscene.active()) return;
        if (strength >= haze || strength > 0.05F && haze < 0.05F) {
            hx = x; hy = y; hz = z; hr = radius;
        }
        haze = Math.max(haze, strength);
    }

    static void lens(double x, double y, double z, double r, float strength) {
        lx = x; ly = y; lz = z; lr = r;
        lens = Math.max(lens, strength);
    }

    static void shock(double x, double y, double z) {
        sx = x; sy = y; sz = z;
        shockStart = System.nanoTime();
        shockOn = true;
    }

    static void tick() {
        register();
        bloom *= 0.88F;
        haze *= 0.8F;
        lens *= 0.5F;
        if (shockOn && (System.nanoTime() - shockStart) / 1.0E9 > SHOCK_LIFE) shockOn = false;
        boolean want = bloom > 0.03F || haze > 0.03F || shockOn || lens > 0.02F;
        PostProcessingManager pm = VeilRenderSystem.renderer().getPostProcessingManager();
        if (want) {
            if (!pm.isActive(PIPE)) pm.add(PIPE);
        } else if (pm.isActive(PIPE)) {
            pm.remove(PIPE);
        }
    }

    static void clear() {
        bloom = 0; haze = 0; shockOn = false; lens = 0;
        VeilRenderSystem.renderer().getPostProcessingManager().remove(PIPE);
    }
}
