package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientGravityWell {

    private ClientGravityWell() {}

    private static final int RIPPLE_TICKS = 22, SMEAR_TICKS = 18, TETHER_TICKS = 14, FLASH_TICKS = 12, WHOOP_TICKS = 30;
    private static final ByteBufferBuilder GLOW = new ByteBufferBuilder(1 << 16), SOFT = new ByteBufferBuilder(1 << 15);

    private record Field(Vec3 c, int r, float share, long seen) {}
    private record Ripple(Vec3 c, int r, Vec3 at, long start) {}
    private record Tether(Vec3 c, List<Vec3> pts, long seen) {}
    private record Flash(Vec3 c, long start) {}

    private static final Map<Long, Field> FIELDS = Statics.client(new HashMap<>());
    private static final List<Ripple> RIPPLES = Statics.client(new ArrayList<>());
    private static final Map<Long, Tether> TETHERS = Statics.client(new HashMap<>());
    private static final List<Flash> FLASHES = Statics.client(new ArrayList<>());

    static void field(BlockPos pos, Vec3 c, int r, float share, long now) {
        if (FIELDS.size() > 32) FIELDS.clear();
        FIELDS.put(pos.asLong(), new Field(c, r, share, now));

        VeilBridge.lens(c.x, c.y, c.z, CORE_LENS_R, LENS_STRENGTH * share);
        VeilBridge.dome(c.x, c.y, c.z, r, DOME_STRENGTH * share);
    }

    static final double CORE_LENS_R = 0.65;
    static final float LENS_STRENGTH = 0.55F, DOME_STRENGTH = 0.6F;

    static void onPayload(GravityWellPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        switch (p.kind()) {
            case GravityWellBlockEntity.FX_BEND -> {
                if (RIPPLES.size() > 16) RIPPLES.remove(0);
                RIPPLES.add(new Ripple(p.centre(), p.radius(), p.pts().isEmpty() ? p.centre() : p.pts().get(0), now));
                VeilBridge.domePulse();
                Minecraft.getInstance().getSoundManager().play(new Whoop(p.centre()));
            }
            case GravityWellBlockEntity.FX_COLLAPSE -> {
                if (FLASHES.size() > 8) FLASHES.remove(0);
                FLASHES.add(new Flash(p.centre(), now));
                FIELDS.remove(p.gen().asLong());
            }
            case GravityWellBlockEntity.FX_TETHER -> {
                if (TETHERS.size() > 16) TETHERS.clear();
                TETHERS.put(p.gen().asLong(), new Tether(p.centre(), List.copyOf(p.pts()), now));
            }
            default -> {}
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || (FIELDS.isEmpty() && RIPPLES.isEmpty() && TETHERS.isEmpty() && FLASHES.isEmpty())) return;
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        BufferBuilder glow = new BufferBuilder(GLOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder soft = new BufferBuilder(SOFT, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyG = false, anyS = false;
        double far = Balance.GWG_FX_RANGE;
        for (Iterator<Field> it = FIELDS.values().iterator(); it.hasNext();) {
            Field f = it.next();
            if (tick - f.seen() > 3) { it.remove(); continue; }
            double d = f.c().distanceTo(cam);
            if (d > far + f.r()) continue;
            float s = f.share();

            if (d < 160.0) {
                ClientBlastGeo.disc(soft, cam, lv, uv, f.c().x, f.c().y, f.c().z, 0.85 + 0.15 * Math.sin(now * 0.07), 0.01F, 0.01F, 0.03F, 0.55F * s, 20);
                for (int k = 0; k < 3; k++) {
                    double a = now * (0.021 + 0.007 * k) + k * 2.1, tilt = 0.35 + 0.3 * k;
                    Vec3 e1 = new Vec3(Math.cos(a), 0.0, Math.sin(a)), n = new Vec3(-Math.sin(a) * Math.sin(tilt), Math.cos(tilt), Math.cos(a) * Math.sin(tilt));
                    Vec3 e2 = n.cross(e1).normalize();
                    ring(glow, cam, f.c(), e1, e2, 1.5 + 0.6 * k, 0.06, 0.25F, 0.45F, 1.0F, (0.55F - 0.12F * k) * s, 48);
                }
                anyG = true; anyS = true;
            }

            double R = f.r() * Math.min(1.0, s * 1.05);
            boolean warp = VeilBridge.active();
            float al = (float) (0.10 + 0.04 * Math.sin(now * 0.09)) * s * (warp ? 1.0F : 1.8F);
            Vec3 ex = new Vec3(1, 0, 0), ez = new Vec3(0, 0, 1);
            if (!warp) ring(glow, cam, f.c().add(0.0, R * 0.87, 0.0), ex, ez, R * 0.5, 0.4 + R * 0.005, 0.3F, 0.5F, 1.0F, al * 0.6F, 64);
            ring(glow, cam, f.c(), ex, ez, R, 0.5 + R * 0.006, 0.3F, 0.5F, 1.0F, al, 96);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double y = R * 0.5 * sgn, rr = R * Math.sqrt(0.75);
                ring(glow, cam, f.c().add(0.0, y, 0.0), ex, ez, rr, 0.4 + R * 0.005, 0.3F, 0.5F, 1.0F, al * 0.7F, 80);
            }
            anyG = true;
        }
        for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext();) {
            Ripple r = it.next();
            double age = now - r.start();
            if (age > Math.max(RIPPLE_TICKS, SMEAR_TICKS) + 2) { it.remove(); continue; }
            if (age < 0.0) continue;
            double u = age / RIPPLE_TICKS;
            if (u < 1.0) {
                double R = Math.max(1.0, Math.sqrt(u) * r.r());
                float fade = (float) ((1.0 - u) * (1.0 - u));
                Vec3[] ax = axes(r.at().subtract(r.c()));
                ring(glow, cam, r.c(), new Vec3(1, 0, 0), new Vec3(0, 0, 1), R, 0.5 + R * 0.02, 0.45F, 0.65F, 1.0F, 0.7F * fade, 96);
                ring(glow, cam, r.c(), ax[0], ax[1], R, 0.5 + R * 0.02, 0.45F, 0.65F, 1.0F, 0.55F * fade, 96);
                ring(soft, cam, r.c(), new Vec3(1, 0, 0), new Vec3(0, 1, 0), R * 0.98, R * 0.08, 0.6F, 0.75F, 1.0F, 0.18F * fade, 64);
                anyG = true; anyS = true;
            }
            double v = age / SMEAR_TICKS;
            if (v < 1.0) {
                float fade = (float) (1.0 - v);
                Vec3[] ax = axes(r.at().subtract(cam));
                ring(glow, cam, r.at(), ax[0], ax[1], 3.0 + 6.0 * v, 0.4, 0.55F, 0.75F, 1.0F, 0.8F * fade, 40);
                for (int k = 0; k < 3; k++) {
                    double a0 = age * 0.35 + k * 2.094;
                    Vec3 prev = null;
                    for (int j = 0; j <= 8; j++) {
                        double a = a0 + j * 0.18, rad = 1.5 + j * 0.6 + v * 3.0;
                        Vec3 p = r.at().add(ax[0].scale(Math.cos(a) * rad)).add(ax[1].scale(Math.sin(a) * rad));
                        if (prev != null) ClientBlastGeo.streak(glow, cam, prev.x, prev.y, prev.z, p.x, p.y, p.z, 0.12, 0.4F, 0.6F, 1.0F, 0.7F * fade, 0.2F * fade);
                        prev = p;
                    }
                }
                ClientBlastGeo.disc(glow, cam, lv, uv, r.at().x, r.at().y, r.at().z, 1.5 + 2.0 * (1.0 - v), 0.7F, 0.85F, 1.0F, 0.9F * fade, 18);
                anyG = true;
            }
        }
        for (Iterator<Tether> it = TETHERS.values().iterator(); it.hasNext();) {
            Tether t = it.next();
            if (tick - t.seen() > TETHER_TICKS) { it.remove(); continue; }
            float fade = (float) (1.0 - (now - t.seen()) / TETHER_TICKS);
            RandomSource rnd = RandomSource.create(tick / 2);
            for (Vec3 p : t.pts()) {
                Vec3 d = p.subtract(t.c());
                Vec3[] ax = axes(d);
                Vec3 prev = t.c();
                for (int j = 1; j <= 10; j++) {
                    double s = j / 10.0, w = Math.sin(s * Math.PI) * (1.2 + 0.4 * Math.sin(now * 0.4 + j));
                    Vec3 q = j == 10 ? p : t.c().add(d.scale(s)).add(ax[0].scale(w * Math.sin(now * 0.3 + j * 0.9))).add(ax[1].scale(w * 0.6 * rnd.nextGaussian() * 0.3));
                    ClientBlastGeo.streak(glow, cam, prev.x, prev.y, prev.z, q.x, q.y, q.z, 0.18, 0.3F, 0.5F, 1.0F, 0.65F * fade, 0.65F * fade);
                    prev = q;
                }
                anyG = true;
            }
        }
        for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext();) {
            Flash f = it.next();
            double age = now - f.start();
            if (age > FLASH_TICKS) { it.remove(); continue; }
            double u = Math.max(0.0, age / FLASH_TICKS);
            ClientBlastGeo.disc(glow, cam, lv, uv, f.c().x, f.c().y, f.c().z, 7.0 * (1.0 - u) + 0.5, 0.75F, 0.88F, 1.0F, (float) (0.95 * (1.0 - u)), 24);
            VeilBridge.glow(0x47574C46L ^ f.start(), f.c().x, f.c().y, f.c().z, 0.5F, 0.7F, 1.0F, 40.0F, (float) (5.0 * (1.0 - u)), tick);
            anyG = true;
        }
        ClientBlastGeo.draw(event, soft, anyS, false);
        ClientBlastGeo.draw(event, glow, anyG, true);
    }

    private static Vec3[] axes(Vec3 d) {
        Vec3 n = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize();
        return new Vec3[] { u, n.cross(u).normalize() };
    }

    private static void ring(BufferBuilder bb, Vec3 cam, Vec3 c, Vec3 e1, Vec3 e2, double R, double w, float r, float g, float b, float a, int n) {
        double da = Math.PI * 2.0 / n;
        for (int i = 0; i < n; i++) {
            double t0 = i * da, t1 = t0 + da;
            Vec3 d0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0))), d1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
            Vec3 i0 = c.add(d0.scale(R - w)), i1 = c.add(d1.scale(R - w)), m0 = c.add(d0.scale(R)), m1 = c.add(d1.scale(R)), o0 = c.add(d0.scale(R + w)), o1 = c.add(d1.scale(R + w));
            ClientBlastGeo.quad(bb, cam, i0, i1, m1, m0, r, g, b, 0.0F, r, g, b, a);
            ClientBlastGeo.quad(bb, cam, m0, m1, o1, o0, r, g, b, a, r, g, b, 0.0F);
        }
    }

    private static final class Whoop extends AbstractTickableSoundInstance {
        private int age;

        Whoop(Vec3 at) {
            super(WmdSounds.GW_WHOOP.get(), SoundSource.BLOCKS, RandomSource.create());
            x = at.x; y = at.y; z = at.z;
            volume = Balance.SND_RELEASE;
            pitch = 1.0F;
            looping = false;
        }

        @Override
        public void tick() {
            age++;
            float u = Math.min(1.0F, age / (float) WHOOP_TICKS);
            pitch = 1.0F;
            volume = Balance.SND_RELEASE * (age < WHOOP_TICKS - 6 ? 1.0F : Math.max(0.0F, (WHOOP_TICKS - age) / 6.0F));
            if (age >= WHOOP_TICKS) stop();
        }
    }
}
