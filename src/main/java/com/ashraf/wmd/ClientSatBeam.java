package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientSatBeam {

    private ClientSatBeam() {}

    static final double LEN = 1200.0, VIEW = 768.0, FADE = 0.3;
    private static final double CORE = 0.03, GLOW = 0.11, DOT = 0.22, HALO = 0.6;

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { SatBeamPayload.clear(); DesigStatusPayload.clear(); }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SatBeamPayload.view().isEmpty() || Minecraft.getInstance().level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        BufferBuilder bb = null;
        for (SatBeamPayload.Beam b : SatBeamPayload.view()) {
            Vec3 spot = new Vec3(b.x(), b.y(), b.z());
            if (spot.distanceToSqr(cam) > VIEW * VIEW) continue;
            Vec3 dir = new Vec3(b.dx(), b.dy(), b.dz()).normalize();
            double len = b.len() > 0.0F ? b.len() : LEN, fade = b.drone() != 0 ? 0.08 : FADE;
            if (b.drone() != 0) {
                Vec3 top = droneAt(b.drone(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
                if (top != null) { Vec3 u = top.subtract(spot); len = Math.max(1.0, u.length()); dir = u.scale(1.0 / len); }
            }
            Vec3 ax = dir.cross(Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), ay = dir.cross(ax).normalize();
            Vec3 lo = spot.subtract(cam), mid = spot.add(dir.scale(len * (1.0 - fade))).subtract(cam), hi = spot.add(dir.scale(len)).subtract(cam);
            if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Vec3 a : new Vec3[] { ax, ay }) {
                strip(bb, lo, mid, a, GLOW, 1.0F, 0.12F, 0.08F, 0.27F, 0.27F);
                strip(bb, mid, hi, a, GLOW, 1.0F, 0.12F, 0.08F, 0.27F, 0.0F);
                strip(bb, lo, mid, a, CORE, 1.0F, 0.35F, 0.27F, 0.92F, 0.92F);
                strip(bb, mid, hi, a, CORE, 1.0F, 0.35F, 0.27F, 0.92F, 0.0F);
            }
            Vec3 d = lo.add(dir.scale(0.05));
            for (Vec3[] sq : new Vec3[][] { { ax, ay }, { ax, dir }, { ay, dir } }) {
                square(bb, d, sq[0], sq[1], HALO, 1.0F, 0.16F, 0.12F, 0.35F);
                square(bb, d, sq[0], sq[1], DOT, 1.0F, 0.78F, 0.7F, 1.0F);
            }
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, true);
    }

    private static Vec3 droneAt(int id, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering())
            if (e instanceof StrikeDroneEntity d && d.droneId() == id) return d.getPosition(pt);
        return null;
    }

    private static void strip(BufferBuilder bb, Vec3 p, Vec3 q, Vec3 a, double w, float r, float g, float b, float ap, float aq) {
        Vec3 s = a.scale(w);
        Vec3 p0 = p.subtract(s), p1 = p.add(s), q1 = q.add(s), q0 = q.subtract(s);
        v(bb, p0, r, g, b, ap); v(bb, p1, r, g, b, ap); v(bb, q1, r, g, b, aq); v(bb, q0, r, g, b, aq);
        v(bb, q0, r, g, b, aq); v(bb, q1, r, g, b, aq); v(bb, p1, r, g, b, ap); v(bb, p0, r, g, b, ap);
    }

    private static void square(BufferBuilder bb, Vec3 c, Vec3 u, Vec3 w, double h, float r, float g, float b, float a) {
        Vec3 su = u.scale(h), sw = w.scale(h);
        Vec3 c0 = c.subtract(su).subtract(sw), c1 = c.add(su).subtract(sw), c2 = c.add(su).add(sw), c3 = c.subtract(su).add(sw);
        v(bb, c0, r, g, b, a); v(bb, c1, r, g, b, a); v(bb, c2, r, g, b, a); v(bb, c3, r, g, b, a);
        v(bb, c3, r, g, b, a); v(bb, c2, r, g, b, a); v(bb, c1, r, g, b, a); v(bb, c0, r, g, b, a);
    }

    private static void v(BufferBuilder bb, Vec3 p, float r, float g, float b, float a) { bb.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a); }

    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || SatBeamPayload.view().isEmpty()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long t = mc.level.getGameTime();
        int i = 0;
        for (SatBeamPayload.Beam b : SatBeamPayload.view()) {
            i++;
            Vec3 spot = new Vec3(b.x(), b.y(), b.z());
            if (spot.distanceToSqr(cam) > VIEW * VIEW) continue;
            VeilBridge.glow(0x5A00_0000_0000_0000L ^ i, spot.x, spot.y + 0.3, spot.z, 1.0F, 0.15F, 0.1F, 7.0F, 2.6F * (0.8F + 0.2F * (float) Math.sin(t * 0.9)), 0.3F, t);
        }
    }

}
