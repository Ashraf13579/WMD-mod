package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class StaticLineFx {

    private StaticLineFx() {}

    private static final ResourceLocation STEEL = ResourceLocation.withDefaultNamespace("textures/block/light_gray_concrete.png");
    private static final ResourceLocation DARK = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");
    private static final ResourceLocation LINE = ResourceLocation.withDefaultNamespace("textures/block/blue_concrete.png");

    private static final class State {
        double len = 4.0;
        long measuredAt = Long.MIN_VALUE;
        final Map<UUID, long[]> chutes = new HashMap<>();
        int spent;
    }

    private static final Map<BlockPos, State> STATES = Statics.client(new HashMap<>());

    static void draw(CargoRampBlockEntity r, float theta, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        Level level = r.getLevel();
        if (level == null || !r.staticLineOn() || r.width() == 0) { if (level != null) STATES.remove(r.getBlockPos()); return; }
        State st = STATES.computeIfAbsent(r.getBlockPos().immutable(), k -> new State());
        long now = level.getGameTime();
        int w = r.width(), h = r.height();
        double uc = w / 2.0, cy = h - Balance.STATIC_CABLE_DROP, a0 = -0.45;
        if (now - st.measuredAt >= 20 || now < st.measuredAt) { st.len = measure(level, r, uc, h); st.measuredAt = now; }
        double a1 = -st.len;

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - r.facing().toYRot()));
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();

        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(STEEL)), dark = buffers.getBuffer(RenderType.entityCutoutNoCull(DARK));
        tube(steel, m, nm, model(a0, uc, cy), model(a1, uc, cy), 0.022F, light);
        for (double a = a0 - 0.3; a >= a1 + 0.2; a -= 2.0) tube(dark, m, nm, model(a, uc, cy - 0.04), model(a, uc, h), 0.035F, light);
        tube(dark, m, nm, model(a1 + 0.05, uc, cy), model(a1 + 0.05, uc, h), 0.04F, light);
        tube(dark, m, nm, model(a0, uc, cy), model(a0, uc, h), 0.04F, light);

        if (theta >= 30.0F && r.opened()) {
            VertexConsumer line = buffers.getBuffer(RenderType.entityCutoutNoCull(LINE));

            double[] tip = r.tip(theta);
            for (Player p : level.players()) {
                if (p.isSpectator() || p.isPassenger()) continue;
                Vec3 wp = p.getPosition(pt);
                double[] l = r.local(AnchorClient.toPlot(level, r.getBlockPos(), wp, pt));
                if (l[0] < a1 - 0.5 || l[0] > tip[0] + 0.5 || l[1] < -0.5 || l[1] > w + 0.5 || l[2] < Math.min(tip[1], 0.0) - 1.0 || l[2] > h) continue;
                Vec3 back = AnchorClient.toPlot(level, r.getBlockPos(), wp.add(0.0, 1.3, 0.0).subtract(p.getViewVector(pt).multiply(0.3, 0.0, 0.3)), pt);
                double[] b = r.local(back);
                double ha = Math.max(a1, Math.min(a0, b[0] - 0.6));
                sag(line, m, nm, model(ha, uc, cy - 0.03), model(b[0], b[1], b[2]), 0.18, light);
                tube(dark, m, nm, model(ha - 0.06, uc, cy - 0.01), model(ha + 0.06, uc, cy - 0.01), 0.04F, light);
            }

            AABB box = new AABB(r.getBlockPos()).inflate(Balance.STATIC_SEARCH_R);
            for (ParachuteEntity c : level.getEntitiesOfClass(ParachuteEntity.class, box)) {
                long[] s = st.chutes.get(c.getUUID());
                if (s == null) {
                    if (c.tickCount > 4) continue;
                    double[] l = r.local(AnchorClient.toPlot(level, r.getBlockPos(), c.position(), pt));
                    if (l[0] < -1.0 || l[0] > tip[0] + Balance.STATIC_DOOR_R) continue;
                    st.chutes.put(c.getUUID(), s = new long[] { now, 0 });
                }
                long age = now - s[0];
                if (age <= Balance.STATIC_TAUT_TICKS) {
                    double[] l = r.local(AnchorClient.toPlot(level, r.getBlockPos(), c.getPosition(pt).add(0.0, 1.6, 0.0), pt));
                    tube(line, m, nm, model(a0, uc, cy - 0.03), model(l[0], l[1], l[2]), 0.02F, light);
                } else if (s[1] == 0) { s[1] = 1; st.spent = Math.min(Balance.STATIC_SPENT_MAX, st.spent + 1); }
            }
            for (Iterator<Map.Entry<UUID, long[]>> it = st.chutes.entrySet().iterator(); it.hasNext(); ) if (now - it.next().getValue()[0] > 400) it.remove();

            for (int i = 0; i < st.spent; i++) {
                double t = (now + pt) * 0.35 + i * 1.7, du = (i - (st.spent - 1) / 2.0) * 0.12;
                Vec3 p0 = model(a0, uc + du, cy - 0.03);
                Vec3 mid = model(a0 + 0.9, uc + du + Math.sin(t) * 0.15, cy - 0.55 + Math.cos(t * 1.3) * 0.08);
                Vec3 end = model(a0 + 1.9, uc + du + Math.sin(t + 1.1) * 0.35, cy - 1.05 + Math.sin(t * 0.9) * 0.2);
                tube(line, m, nm, p0, mid, 0.018F, light);
                tube(line, m, nm, mid, end, 0.018F, light);
                tube(dark, m, nm, end, end.add(0.0, -0.12, 0.0), 0.05F, light);
            }
        } else st.spent = 0;
        ps.popPose();
    }

    private static double measure(Level level, CargoRampBlockEntity r, double uc, int h) {
        double max = Balance.STATIC_CABLE_MAX;
        for (double a = -1.0; a >= -max; a -= 1.0) {
            Vec3 g = r.gridPoint(a, uc, h - 0.5);
            BlockPos bp = BlockPos.containing(g);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) return Math.max(1.5, -a - 0.6);
        }
        return max;
    }

    private static Vec3 model(double a, double u, double y) { return new Vec3(u - 0.5, y, -(a + 0.5)); }

    private static void sag(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 a, Vec3 b, double drop, int light) {
        Vec3 mid = a.add(b).scale(0.5).add(0.0, -drop * Math.min(1.0, a.distanceTo(b) / 3.0), 0.0);
        tube(vc, m, nm, a, mid, 0.02F, light);
        tube(vc, m, nm, mid, b, 0.02F, light);
    }

    private static void tube(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 pa, Vec3 pb, float r, int light) {
        Vector3f a = new Vector3f((float) pa.x, (float) pa.y, (float) pa.z), b = new Vector3f((float) pb.x, (float) pb.y, (float) pb.z);
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-3F) return;
        d.div(len);
        Vector3f up = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f s = new Vector3f(d).cross(up).normalize().mul(r);
        Vector3f t = new Vector3f(d).cross(s).normalize().mul(r);
        Vector3f[] ring = { new Vector3f(s).add(t), new Vector3f(s).sub(t), new Vector3f(s).negate().sub(t), new Vector3f(s).negate().add(t) };
        for (int i = 0; i < 4; i++) {
            Vector3f r0 = ring[i], r1 = ring[(i + 1) % 4];
            Vector3f n = nm.transform(new Vector3f(r0).add(r1).normalize());
            vert(vc, m, n, new Vector3f(a).add(r0), 0.0F, 0.0F, light);
            vert(vc, m, n, new Vector3f(a).add(r1), 1.0F, 0.0F, light);
            vert(vc, m, n, new Vector3f(b).add(r1), 1.0F, len, light);
            vert(vc, m, n, new Vector3f(b).add(r0), 0.0F, len, light);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vector3f n, Vector3f p, float u, float v, int light) {
        vc.addVertex(m, p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
    }
}
