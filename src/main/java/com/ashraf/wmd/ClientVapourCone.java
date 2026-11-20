package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.joml.Matrix4d;
import org.joml.Vector3d;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientVapourCone {

    private ClientVapourCone() {}

    private static final int VC_RISE = 5, VC_HOLD = 22, VC_LIFE = 36, VC_PULL_LIFE = 12, SIDES = 28, RINGS = 6;
    private static final double REACH = 600.0;
    private static final ByteBufferBuilder BUF = new ByteBufferBuilder(1 << 16);

    private record Cone(UUID ship, int kind, Vector3d plotCenter, Vector3d plotDir, double r, double len, long start, long seed) {}

    private static final List<Cone> CONES = Statics.client(new ArrayList<>());
    private static final List<double[]> THUMPS = Statics.client(new ArrayList<>());

    static void add(JetVapourPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        if (p.kind() == JetVapourPayload.PULL) {
            for (int i = 0; i < CONES.size(); i++) {
                Cone c = CONES.get(i);
                if (c.ship().equals(p.ship()) && c.kind() == JetVapourPayload.PULL) {
                    CONES.set(i, new Cone(c.ship(), c.kind(), c.plotCenter(), c.plotDir(), c.r(), c.len(), now, c.seed()));
                    return;
                }
            }
        }
        double[] box = ShipFrame.craftBox(level, p.ship());
        Matrix4d m = ShipFrame.craftMatrix(level, p.ship(), 1.0F);
        if (box == null || m == null) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3d c = new Vector3d((box[0] + box[3]) * 0.5, (box[1] + box[4]) * 0.5, (box[2] + box[5]) * 0.5);
        if (cam.distanceToSqr(c.x, c.y, c.z) > REACH * REACH) return;
        Matrix4d inv = new Matrix4d(m).invert();
        Vector3d pc = inv.transformPosition(new Vector3d(c));
        Vector3d pd = inv.transformDirection(new Vector3d(p.dx(), p.dy(), p.dz()));
        if (pd.lengthSquared() < 1.0E-8) return;
        pd.normalize();
        double size = Math.cbrt(Math.max(1.0, (box[3] - box[0]) * (box[4] - box[1]) * (box[5] - box[2])));
        boolean pull = p.kind() == JetVapourPayload.PULL;
        double r = Math.max(2.5, Math.min(16.0, size * (pull ? 0.7 : 0.9))), len = Math.max(3.0, Math.min(22.0, size * (pull ? 0.9 : 1.2)));
        if (CONES.size() > 32) CONES.remove(0);
        CONES.add(new Cone(p.ship(), p.kind(), pc, pd, r, len, now, level.random.nextLong()));
    }

    static void thump(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(new Vec3(x, y, z));
        if (d > Balance.JET_BOOM_R) return;
        double k = 1.0 - d / Balance.JET_BOOM_R;
        long start = level.getGameTime() + (long) (d / 17.0);
        if (THUMPS.size() > 8) THUMPS.remove(0);
        THUMPS.add(new double[] { start, start + 10, 0.25 + 0.55 * k * k });
    }

    static float shake() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || THUMPS.isEmpty()) return 0.0F;
        long now = level.getGameTime();
        float s = 0.0F;
        for (Iterator<double[]> it = THUMPS.iterator(); it.hasNext();) {
            double[] t = it.next();
            if (now > t[1]) { it.remove(); continue; }
            if (now < t[0]) continue;
            double u = (now - t[0]) / (t[1] - t[0]);
            s = Math.max(s, (float) (t[2] * (1.0 - u) * (1.0 - u)));
        }
        return s;
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CONES.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        BufferBuilder bb = new BufferBuilder(BUF, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Iterator<Cone> it = CONES.iterator(); it.hasNext();) {
            Cone c = it.next();
            double age = now - c.start();
            int life = c.kind() == JetVapourPayload.PULL ? VC_PULL_LIFE : VC_LIFE;
            if (age > life || age < -2.0) { it.remove(); continue; }
            Matrix4d m = ShipFrame.craftMatrix(level, c.ship(), pt);
            if (m == null) { it.remove(); continue; }
            Vector3d o = m.transformPosition(new Vector3d(c.plotCenter()));
            Vector3d ax = m.transformDirection(new Vector3d(c.plotDir()));
            if (ax.lengthSquared() < 1.0E-8 || cam.distanceToSqr(o.x, o.y, o.z) > REACH * REACH) continue;
            ax.normalize();
            any |= cone(bb, cam, c, o, ax, age, now);
        }
        ClientBlastGeo.draw(event, bb, any, false);
    }

    private static boolean cone(BufferBuilder bb, Vec3 cam, Cone c, Vector3d o, Vector3d ax, double age, double now) {
        boolean pull = c.kind() == JetVapourPayload.PULL;
        double a, grow, shred;
        if (pull) {
            double u = age / VC_PULL_LIFE;
            a = (0.55 + 0.3 * Math.sin(now * 2.1 + c.seed() % 7)) * (u < 0.7 ? 1.0 : 1.0 - (u - 0.7) / 0.3);
            grow = 0.9; shred = 0.15;
        } else if (age < VC_RISE) { a = age / VC_RISE; grow = 0.55 + 0.45 * age / VC_RISE; shred = 0.0; }
        else if (age < VC_HOLD) { a = 1.0; grow = 1.0; shred = 0.0; }
        else { double u = (age - VC_HOLD) / (VC_LIFE - VC_HOLD); a = 1.0 - u; grow = 1.0 + 0.15 * u; shred = u; }
        if (a <= 0.01) return false;
        double dCam = cam.distanceTo(new Vec3(o.x, o.y, o.z));
        if (dCam < c.r()) a *= 0.45;
        Vector3d ref = Math.abs(ax.y) > 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
        Vector3d u = new Vector3d(ax).cross(ref).normalize(), v = new Vector3d(ax).cross(u).normalize();
        double r = c.r() * grow, len = c.len() * grow, r0 = r * 0.22, ahead = len * 0.08;
        java.util.Random rnd = new java.util.Random(c.seed());
        double da = Math.PI * 2.0 / SIDES;
        for (int s = 0; s < SIDES; s++) {
            boolean torn = rnd.nextDouble() < shred * 0.9;
            double reach = torn ? 0.35 + 0.4 * rnd.nextDouble() : 1.0 - 0.12 * rnd.nextDouble() * shred;
            double a0 = s * da, a1 = a0 + da;
            for (int g = 0; g < RINGS; g++) {
                double f0 = (double) g / RINGS * reach, f1 = (double) (g + 1) / RINGS * reach;
                float al0 = (float) (a * alpha(f0, pull)), al1 = (float) (a * alpha(f1, pull));
                if (al0 <= 0.004F && al1 <= 0.004F) continue;
                vtx(bb, cam, at(o, ax, u, v, ahead - len * f0, a0, r0 + (r - r0) * Math.sqrt(f0)), al0);
                vtx(bb, cam, at(o, ax, u, v, ahead - len * f0, a1, r0 + (r - r0) * Math.sqrt(f0)), al0);
                vtx(bb, cam, at(o, ax, u, v, ahead - len * f1, a1, r0 + (r - r0) * Math.sqrt(f1)), al1);
                vtx(bb, cam, at(o, ax, u, v, ahead - len * f1, a0, r0 + (r - r0) * Math.sqrt(f1)), al1);
            }
        }
        return true;
    }

    private static double alpha(double f, boolean pull) {
        double front = f < 0.12 ? f / 0.12 : 1.0;
        return (pull ? 0.32 : 0.5) * front * Math.pow(1.0 - Math.min(1.0, f), 1.4);
    }

    private static Vector3d at(Vector3d o, Vector3d ax, Vector3d u, Vector3d v, double s, double ang, double r) {
        return new Vector3d(o).fma(s, ax).fma(Math.cos(ang) * r, u).fma(Math.sin(ang) * r, v);
    }

    private static void vtx(BufferBuilder bb, Vec3 cam, Vector3d p, float a) {
        bb.addVertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(0.96F, 0.97F, 1.0F, a);
    }
}
