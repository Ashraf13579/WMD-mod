package com.ashraf.wmd;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ClientSatSilhouette {

    private ClientSatSilhouette() {}

    private static final double DIST = 100.0;

    private static final float BODY = 0.10F, WING_IN = 0.13F, WING_OUT = 0.42F, WING_W = 0.06F;
    private static final float R = 0.10F, G = 0.11F, B = 0.13F;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || OrbitView.sats().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float night = Mth.clamp((level.getStarBrightness(pt) - 0.05F) * 3.0F, 0.0F, 1.0F);
        float day = (1.0F - night) * (1.0F - level.getRainLevel(pt) * 0.8F);
        if (day <= 0.02F) return;
        Vec3 cam = event.getCamera().getPosition();
        double dist = Math.min(DIST, mc.gameRenderer.getDepthFar() * 0.8);
        float k = (float) (dist / DIST);
        long now = OrbitView.now();
        BufferBuilder bb = null;
        for (OrbitMath.Sat s : OrbitView.sats()) {
            if (now >= s.endMs() || !OrbitMath.isOverhead(s, cam.x, cam.z, now)) continue;
            double[] sky = OrbitMath.sky(s, cam.x, cam.z, now);
            if (sky[1] < 5.0) continue;
            double[] ahead = OrbitMath.sky(s, cam.x, cam.z, now + 2000L);
            Vector3f v = new Vector3f((float) sky[2], (float) sky[3], (float) sky[4]);

            Vector3f m = new Vector3f((float) (ahead[2] - sky[2]), (float) (ahead[3] - sky[3]), (float) (ahead[4] - sky[4]));
            m.sub(new Vector3f(v).mul(m.dot(v)));
            if (m.lengthSquared() < 1.0E-10F) m.set(1.0F, 0.0F, 0.0F).sub(new Vector3f(v).mul(v.x));
            m.normalize();
            Vector3f n = new Vector3f(v).cross(m).normalize();
            float a = 0.8F * day;
            if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float cx = (float) (sky[2] * dist), cy = (float) (sky[3] * dist), cz = (float) (sky[4] * dist);
            quad(bb, cx, cy, cz, m, n, -BODY * k, BODY * k, -BODY * k, BODY * k, a);
            quad(bb, cx, cy, cz, m, n, -WING_W * k, WING_W * k, WING_IN * k, WING_OUT * k, a);
            quad(bb, cx, cy, cz, m, n, -WING_W * k, WING_W * k, -WING_OUT * k, -WING_IN * k, a);
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, false);
    }

    private static void quad(BufferBuilder bb, float cx, float cy, float cz, Vector3f m, Vector3f n, float m0, float m1, float n0, float n1, float a) {
        bb.addVertex(cx + m.x * m0 + n.x * n0, cy + m.y * m0 + n.y * n0, cz + m.z * m0 + n.z * n0).setColor(R, G, B, a);
        bb.addVertex(cx + m.x * m1 + n.x * n0, cy + m.y * m1 + n.y * n0, cz + m.z * m1 + n.z * n0).setColor(R, G, B, a);
        bb.addVertex(cx + m.x * m1 + n.x * n1, cy + m.y * m1 + n.y * n1, cz + m.z * m1 + n.z * n1).setColor(R, G, B, a);
        bb.addVertex(cx + m.x * m0 + n.x * n1, cy + m.y * m0 + n.y * n1, cz + m.z * m0 + n.z * n1).setColor(R, G, B, a);
    }
}
