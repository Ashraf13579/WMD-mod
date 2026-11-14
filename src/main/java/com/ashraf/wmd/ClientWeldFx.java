package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientWeldFx {

    private ClientWeldFx() {}

    private static final class Arc { Vec3 at; long seen, born; float seed; }
    private static final class Spark { Vec3 pos, vel, prev; int life; final int max; Spark(Vec3 p, Vec3 v, int l) { pos = p; prev = p; vel = v; life = l; max = l; } }

    private static final Map<Long, Arc> ARCS = Statics.client(new HashMap<>());
    private static final List<Spark> SPARKS = Statics.client(new ArrayList<>());

    static void arc(long key, Vec3 at, long gameTime) {
        Arc a = ARCS.computeIfAbsent(key, k -> { Arc n = new Arc(); n.born = gameTime; n.seed = (float) (k % 997); return n; });
        a.at = at;
        a.seen = gameTime;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { if (!ARCS.isEmpty()) ARCS.clear(); if (!SPARKS.isEmpty()) SPARKS.clear(); return; }
        if (ARCS.isEmpty() && SPARKS.isEmpty()) return;
        long now = level.getGameTime();
        RandomSource r = level.random;
        for (Iterator<Arc> it = ARCS.values().iterator(); it.hasNext(); ) {
            Arc a = it.next();
            if (now - a.seen > Balance.WELD_FX_HOLD + Balance.WELD_FX_AFTERGLOW || now < a.seen - 40) { it.remove(); continue; }
            if (now - a.seen > Balance.WELD_FX_HOLD) continue;
            int n = Balance.WELD_FX_SPARKS + (r.nextInt(4) == 0 ? 6 : 0);
            for (int i = 0; i < n && SPARKS.size() < 300; i++) {
                double sp = 0.08 + r.nextDouble() * 0.22;
                Vec3 v = new Vec3(r.nextGaussian(), 0.4 + r.nextDouble() * 0.8, r.nextGaussian()).normalize().scale(sp);
                SPARKS.add(new Spark(a.at, v, 6 + r.nextInt(12)));
            }
        }
        for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            if (--s.life <= 0) { it.remove(); continue; }
            s.prev = s.pos;
            s.pos = s.pos.add(s.vel);
            s.vel = s.vel.scale(0.92).add(0.0, -0.045, 0.0);
            if (s.vel.y < 0 && !level.getBlockState(net.minecraft.core.BlockPos.containing(s.pos)).getCollisionShape(level, net.minecraft.core.BlockPos.containing(s.pos)).isEmpty()) {
                s.pos = s.prev;
                s.vel = new Vec3(s.vel.x * 0.5, -s.vel.y * 0.3, s.vel.z * 0.5);
            }
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (ARCS.isEmpty() && SPARKS.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + pt;
        RandomSource r = level.random;
        ClientBlastGeo.beginFrame();
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Arc a : ARCS.values()) {
            if (a.at == null || cam.distanceToSqr(a.at) > Balance.WELD_FX_RANGE * Balance.WELD_FX_RANGE) continue;
            double since = now - a.seen;
            if (since > Balance.WELD_FX_HOLD) {
                float k = (float) Math.max(0.0, 1.0 - (since - Balance.WELD_FX_HOLD) / Balance.WELD_FX_AFTERGLOW);
                ClientBlastGeo.disc(glow, cam, left, up, a.at.x, a.at.y, a.at.z, 0.12, 1.0F, 0.45F, 0.12F, 0.7F * k, 8);
                any = true;
                continue;
            }
            float f = 0.55F + 0.45F * r.nextFloat();
            if (r.nextInt(7) == 0) f *= 0.35F;
            ClientBlastGeo.disc(glow, cam, left, up, a.at.x, a.at.y, a.at.z, 1.1 * (0.8 + 0.4 * f), 0.35F, 0.55F, 1.0F, 0.22F * f, 12);
            ClientBlastGeo.disc(glow, cam, left, up, a.at.x, a.at.y, a.at.z, 0.35 * (0.7 + 0.6 * f), 0.75F, 0.8F, 1.0F, 0.75F * f, 10);
            ClientBlastGeo.disc(glow, cam, left, up, a.at.x, a.at.y, a.at.z, 0.10 + 0.05 * f, 1.0F, 0.97F, 1.0F, 1.0F, 8);

            for (int i = 0; i < 3; i++) {
                Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(0.08 + r.nextDouble() * 0.12);
                Vec3 m = a.at.add(d.scale(0.5)).add(r.nextGaussian() * 0.03, r.nextGaussian() * 0.03, r.nextGaussian() * 0.03), e = a.at.add(d);
                ClientBlastGeo.streak(glow, cam, a.at.x, a.at.y, a.at.z, m.x, m.y, m.z, 0.012, 0.85F, 0.8F, 1.0F, 0.9F * f, 0.7F * f);
                ClientBlastGeo.streak(glow, cam, m.x, m.y, m.z, e.x, e.y, e.z, 0.010, 0.85F, 0.8F, 1.0F, 0.7F * f, 0.0F);
            }
            any = true;
        }
        for (Spark s : SPARKS) {
            if (cam.distanceToSqr(s.pos) > Balance.WELD_FX_RANGE * Balance.WELD_FX_RANGE) continue;
            Vec3 p = s.prev.lerp(s.pos, pt), tail = p.subtract(s.vel.scale(1.6));
            float u = (float) s.life / s.max;
            float g = 0.45F + 0.5F * u, b = 0.1F + 0.75F * u * u;
            ClientBlastGeo.streak(glow, cam, tail.x, tail.y, tail.z, p.x, p.y, p.z, 0.014, 1.0F, g, b, 0.0F, Math.min(1.0F, 0.4F + u));
            any = true;
        }
        ClientBlastGeo.draw(event, glow, any, true);
    }
}
