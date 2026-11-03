package com.ashraf.wmd;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAsatDebris {

    private ClientAsatDebris() {}

    private static final double DIST = 100.0;
    private static final int DEBRIS_N = 18;

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { AsatFxView.clear(); }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        long now = OrbitView.now();
        var events = AsatFxView.live(now);
        if (events.isEmpty()) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float night = Mth.clamp((level.getStarBrightness(pt) - 0.05F) * 3.0F, 0.0F, 1.0F);
        float bright = 0.55F + 0.45F * night;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double dist = Math.min(DIST, mc.gameRenderer.getDepthFar() * 0.8);
        double k = dist / DIST;
        BufferBuilder bb = null;
        for (AsatFxView.Event e : events) {
            double[] sky = OrbitMath.sky(e.sat(), cam.x, cam.z, e.ms());
            if (sky[1] < 3.0) continue;
            double age = (now - e.ms()) / 1000.0;
            if (age < 0) continue;
            Vec3 c = new Vec3(sky[2] * dist, sky[3] * dist, sky[4] * dist);
            if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            if (age < 1.5) {
                float h = (float) (k * (e.hit() ? 2.2 : 1.1) * Math.min(1.0, age / 0.3 + 0.2));
                float a = (float) (bright * (1.0 - age / 1.5));
                sprite(bb, c, left, up, h, 1.0F, 0.95F, 0.8F, a);
            }
            if (!e.hit()) continue;

            Vec3 dir = new Vec3(sky[2], sky[3], sky[4]);
            Vec3 e1 = dir.cross(new Vec3(0.0, 1.0, 0.0));
            e1 = e1.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : e1.normalize();
            Vec3 e2 = e1.cross(dir).normalize();
            long seed = e.sat().id() * 0x9E3779B97F4A7C15L + e.ms();
            for (int i = 0; i < DEBRIS_N; i++) {
                long hsh = (seed + i * 0xBF58476D1CE4E5B9L);
                hsh ^= hsh >>> 31; hsh *= 0x94D049BB133111EBL; hsh ^= hsh >>> 29;
                double ang = ((hsh & 0xFFFF) / 65535.0) * Math.PI * 2.0, sp = 0.6 + ((hsh >>> 16) & 0xFF) / 255.0 * 1.4;
                double fall = 0.10 + ((hsh >>> 24) & 0xFF) / 255.0 * 0.12;
                double life = 9.0 + ((hsh >>> 32) & 0xFF) / 255.0 * 9.0;
                if (age > life) continue;
                Vec3 p1 = piece(c, e1, e2, ang, sp, fall, age, k), p0 = piece(c, e1, e2, ang, sp, fall, Math.max(0.0, age - 0.8), k);
                double u = age / life;
                float r = 1.0F, g = (float) Mth.clamp(0.75 - u * 0.6, 0.15, 0.75), b = (float) Mth.clamp(0.35 - u * 0.35, 0.05, 0.35);
                float a = (float) (bright * (1.0 - u) * (u < 0.05 ? u / 0.05 : 1.0));
                streak(bb, p0, p1, (float) (k * 0.10), r, g, b, a);
            }
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, true);
    }

    private static Vec3 piece(Vec3 c, Vec3 e1, Vec3 e2, double ang, double sp, double fall, double t, double k) {
        double out = sp * t * k * (1.0 - Math.min(0.6, t * 0.03));
        double drop = fall * t * t * k;
        return c.add(e1.scale(Math.cos(ang) * out)).add(e2.scale(Math.sin(ang) * out)).add(0.0, -drop, 0.0);
    }

    private static void sprite(BufferBuilder bb, Vec3 p, Vector3f left, Vector3f up, float h, float r, float g, float b, float a) {
        float lx = left.x * h, ly = left.y * h, lz = left.z * h, ux = up.x * h, uy = up.y * h, uz = up.z * h;
        float x = (float) p.x, y = (float) p.y, z = (float) p.z;
        bb.addVertex(x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, b, a);
        bb.addVertex(x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, b, a);
        bb.addVertex(x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, b, a);
        bb.addVertex(x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, b, a);
    }

    private static void streak(BufferBuilder bb, Vec3 a, Vec3 b, float w, float r, float g, float bl, float alpha) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(b);
        double len = side.length();
        if (len < 1.0E-6) return;
        side = side.scale(w / len);
        bb.addVertex((float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(r, g, bl, 0.0F);
        bb.addVertex((float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(r, g, bl, 0.0F);
        bb.addVertex((float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor(r, g, bl, alpha);
        bb.addVertex((float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor(r, g, bl, alpha);
    }
}
