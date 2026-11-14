package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

public class TurntableRenderer implements BlockEntityRenderer<TurntableBlockEntity> {

    static final float ARROW_AT = 24.0F / 16.0F, PAD_AT = -24.0F / 16.0F;

    static final double RING_OUT = 4.0 / 16.0;
    static final int K = 96;

    @Override
    public void render(TurntableBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        MeshModel mm = MeshModel.named("turntable");
        if (!mm.has("plate")) return;
        boolean head = be.getBlockState().getValue(TurntableBlock.HEAD);
        if (!head || be.size() <= 0) {
            if (!coveredByHead(level, be)) lone(be, mm, ps, buffers, light, overlay);
            if (!head) return;
        }
        if (mm.has("base")) {
            ps.pushPose();
            ps.translate(0.5, 0.0, 0.5);
            mm.draw("base", ps.last(), buffers, light, overlay);
            ps.popPose();
        }
        int n = be.size();
        if (n <= 0) return;
        BlockPos o = be.getBlockPos();
        double now = level.getGameTime() + pt, ang = be.angle(now), r = be.radius(), h = n / 2.0;
        if (be.turning()) TurntableWalk.seen(o);
        List<float[]> tops = tops(mm);
        BlockState skin = be.copycat().shown();
        Skin sk = skin == null ? null : skin(skin, level, o);

        VertexConsumer vc = sk != null ? buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)) : mm.buffer(buffers);
        if (vc != null) {
            PoseStack.Pose pose = ps.last();
            double ro = r + RING_OUT - 0.01;
            for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) {
                double ox = x + 0.5 - h, oz = z + 0.5 - h;
                double near = Math.hypot(Math.max(Math.abs(ox) - 0.5, 0.0), Math.max(Math.abs(oz) - 0.5, 0.0)), far = Math.hypot(Math.abs(ox) + 0.5, Math.abs(oz) + 0.5);
                if (far <= ro * Math.cos(Math.PI / K)) continue;
                int l = LevelRenderer.getLightColor(level, new BlockPos(o.getX() + x, o.getY() + 1, o.getZ() + z));
                for (float[] q : tops) {
                    double[][] poly = rect(q, h + ox, h + oz);
                    if (near >= ro) cell(vc, pose, poly, q, h + ox, h + oz, sk, l, overlay);
                    else for (int k = 0; k < K; k++) {
                        double[][] w = outsideWedge(poly, h, h, ro, k);
                        if (w != null) cell(vc, pose, w, q, h + ox, h + oz, sk, l, overlay);
                    }
                }
            }
            ps.pushPose();
            ps.translate(h, 0.0, h);
            ps.mulPose(Axis.YP.rotationDegrees((float) -ang));
            pose = ps.last();
            double c = Math.cos(Math.toRadians(ang)), s = Math.sin(Math.toRadians(ang));
            for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) {
                double ox = x + 0.5 - h, oz = z + 0.5 - h;
                double near = Math.hypot(Math.max(Math.abs(ox) - 0.5, 0.0), Math.max(Math.abs(oz) - 0.5, 0.0)), far = Math.hypot(Math.abs(ox) + 0.5, Math.abs(oz) + 0.5);
                if (near >= r) continue;
                double wx = ox * c - oz * s, wz = ox * s + oz * c;
                int l = LevelRenderer.getLightColor(level, new BlockPos(o.getX() + (int) Math.floor(h + wx), o.getY() + 1, o.getZ() + (int) Math.floor(h + wz)));
                for (float[] q : tops) {
                    double[][] poly = rect(q, ox, oz);
                    if (far > r * Math.cos(Math.PI / K)) poly = insideDisc(poly, r);
                    if (poly != null) cell(vc, pose, poly, q, ox, oz, sk, l, overlay);
                }
            }
            ps.popPose();
        }

        vc = mm.buffer(buffers);
        if (vc != null) {
            ps.pushPose();
            ps.translate(h, 0.0, h);
            PoseStack.Pose pose = ps.last();
            List<float[]> ring = quads(mm, "ring"), lamp = quads(mm, "lamp");
            int pieces = Math.max(24, (int) Math.ceil(2.0 * Math.PI * r * 2.0));
            for (int k = 0; k < pieces; k++) {
                double a0 = 360.0 * k / pieces, a1 = 360.0 * (k + 1) / pieces, am = Math.toRadians((a0 + a1) / 2.0);
                int l = LevelRenderer.getLightColor(level, new BlockPos(o.getX() + (int) Math.floor(h + Math.sin(am) * (r + 0.12)), o.getY() + 1, o.getZ() + (int) Math.floor(h - Math.cos(am) * (r + 0.12))));
                for (float[] q : ring) bent(vc, pose, q, r, a0, a1, 0.25, 255, 255, 255, l, overlay);
            }
            int cur = Math.floorMod((int) Math.round(be.target() / 90.0), 4);
            boolean blinkOn = !be.turning() || (level.getGameTime() / 6) % 2 == 0;
            for (int k = 0; k < 4; k++) {
                double a = 90.0 * k, half = Math.toDegrees(0.25 / (r + 0.15));
                boolean lit = k == cur && blinkOn;
                int l = lit ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, new BlockPos(o.getX() + (int) Math.floor(h + Math.sin(Math.toRadians(a)) * (r + 0.12)),
                        o.getY() + 1, o.getZ() + (int) Math.floor(h - Math.cos(Math.toRadians(a)) * (r + 0.12))));
                for (float[] q : lamp) bent(vc, pose, q, r, a - half, a + half, 0.25, lit ? 255 : 60, lit ? 255 : 72, lit ? 255 : 96, l, overlay);
            }
            ps.popPose();
        }

        int lc = LevelRenderer.getLightColor(level, new BlockPos(o.getX() + (int) Math.floor(h), o.getY() + 1, o.getZ() + (int) Math.floor(h)));
        ps.pushPose();
        ps.translate(h, 0.0, h);
        ps.mulPose(Axis.YP.rotationDegrees((float) -ang));
        if (mm.has("hub")) mm.draw("hub", ps.last(), buffers, lc, overlay);
        if (mm.has("arrow")) {
            ps.pushPose();
            ps.translate(-ARROW_AT, 0.0, -(r - 0.75));
            mm.draw("arrow", ps.last(), buffers, lc, overlay);
            ps.popPose();
        }
        if (mm.has("pad")) {
            int np = Math.max(6, (int) Math.round(2.0 * Math.PI * 0.7 * r / 2.5));
            for (int k = 0; k < np; k++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees((float) (-(k + 0.5) * 360.0 / np)));
                ps.translate(-PAD_AT, 0.0, -0.7 * r);
                mm.draw("pad", ps.last(), buffers, lc, overlay);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static List<float[]> tops(MeshModel mm) {
        List<float[]> l = new ArrayList<>();
        mm.forEachQuad("plate", (v, i) -> {
            float ny = (v[i + 6] + v[i + 14] + v[i + 22] + v[i + 30]) * 0.25F;
            if (ny > 0.9F) { float[] c = new float[32]; System.arraycopy(v, i, c, 0, 32); l.add(c); }
        });
        return l;
    }

    private static List<float[]> quads(MeshModel mm, String bone) {
        List<float[]> l = new ArrayList<>();
        mm.forEachQuad(bone, (v, i) -> { float[] c = new float[32]; System.arraycopy(v, i, c, 0, 32); l.add(c); });
        return l;
    }

    private static double[][] rect(float[] q, double cx, double cz) {
        double x0 = Math.min(Math.min(q[0], q[8]), Math.min(q[16], q[24])), x1 = Math.max(Math.max(q[0], q[8]), Math.max(q[16], q[24]));
        double z0 = Math.min(Math.min(q[2], q[10]), Math.min(q[18], q[26])), z1 = Math.max(Math.max(q[2], q[10]), Math.max(q[18], q[26]));
        return new double[][] { { cx + x0, cx + x1, cx + x1, cx + x0 }, { cz + z0, cz + z0, cz + z1, cz + z1 } };
    }

    static double[][] clip(double[][] p, double a, double b, double c) {
        if (p == null) return null;
        int m = p[0].length;
        double[] xs = new double[m * 2 + 2], zs = new double[m * 2 + 2];
        int k = 0;
        for (int i = 0; i < m; i++) {
            int j = (i + 1) % m;
            double xi = p[0][i], zi = p[1][i], xj = p[0][j], zj = p[1][j];
            double di = a * xi + b * zi + c, dj = a * xj + b * zj + c;
            if (di >= 0.0) { xs[k] = xi; zs[k] = zi; k++; }
            if ((di >= 0.0) != (dj >= 0.0)) {
                double t = di / (di - dj);
                xs[k] = xi + (xj - xi) * t; zs[k] = zi + (zj - zi) * t; k++;
            }
        }
        if (k < 3) return null;
        return new double[][] { java.util.Arrays.copyOf(xs, k), java.util.Arrays.copyOf(zs, k) };
    }

    static double[][] insideDisc(double[][] p, double r) {
        for (int k = 0; k < K && p != null; k++) {
            double a0 = 2.0 * Math.PI * k / K, a1 = 2.0 * Math.PI * (k + 1) / K;
            double x0 = r * Math.cos(a0), z0 = r * Math.sin(a0), x1 = r * Math.cos(a1), z1 = r * Math.sin(a1);
            double nx = -(z1 - z0), nz = x1 - x0;
            p = clip(p, nx, nz, -(nx * x0 + nz * z0));
        }
        return p;
    }

    static double[][] outsideWedge(double[][] p, double cx, double cz, double r, int k) {
        double a0 = 2.0 * Math.PI * k / K, a1 = 2.0 * Math.PI * (k + 1) / K;
        double d0x = Math.cos(a0), d0z = Math.sin(a0), d1x = Math.cos(a1), d1z = Math.sin(a1);

        double[][] w = clip(p, -d0z, d0x, d0z * cx - d0x * cz);
        w = clip(w, d1z, -d1x, -(d1z * cx - d1x * cz));
        if (w == null) return null;
        double mx = (d0x + d1x), mz = (d0z + d1z), ml = Math.hypot(mx, mz);
        mx /= ml; mz /= ml;
        double e = r * Math.cos(Math.PI / K);
        return clip(w, mx, mz, -(mx * cx + mz * cz) - e);
    }

    private static void cell(VertexConsumer vc, PoseStack.Pose pose, double[][] poly, float[] q, double ccx, double ccz, Skin sk, int light, int overlay) {
        int m = poly[0].length;
        float[] us = new float[m], vs = new float[m];
        double x0 = Math.min(Math.min(q[0], q[8]), Math.min(q[16], q[24])), x1 = Math.max(Math.max(q[0], q[8]), Math.max(q[16], q[24]));
        double z0 = Math.min(Math.min(q[2], q[10]), Math.min(q[18], q[26])), z1 = Math.max(Math.max(q[2], q[10]), Math.max(q[18], q[26]));
        for (int i = 0; i < m; i++) {
            double lx = poly[0][i] - ccx, lz = poly[1][i] - ccz;
            if (sk != null) {
                us[i] = sk.sprite().getU((float) clamp01(lx + 0.5));
                vs[i] = sk.sprite().getV((float) clamp01(lz + 0.5));
            } else {
                double fx = x1 - x0 < 1.0E-6 ? 0.0 : (lx - x0) / (x1 - x0), fz = z1 - z0 < 1.0E-6 ? 0.0 : (lz - z0) / (z1 - z0);
                float[] uv = bilinear(q, x0, x1, z0, z1, fx, fz);
                us[i] = uv[0]; vs[i] = uv[1];
            }
        }
        int cr = sk == null ? 255 : sk.r(), cg = sk == null ? 255 : sk.g(), cb = sk == null ? 255 : sk.b();
        float y = (q[1] + q[9] + q[17] + q[25]) * 0.25F;
        Matrix4f m4 = pose.pose();
        Vector3f nrm = pose.normal().transform(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f());
        for (int i = 1; i + 1 < m; i++) {
            int[] ix = { 0, i, i + 1, i + 1 };
            for (int j : ix) vc.addVertex(m4, (float) poly[0][j], y, (float) poly[1][j]).setColor(cr, cg, cb, 255).setUv(us[j], vs[j]).setOverlay(overlay).setLight(light)
                    .setNormal(nrm.x, nrm.y, nrm.z);
        }
    }

    private static double clamp01(double x) { return x < 0.0 ? 0.0 : Math.min(x, 1.0); }

    private static float[] bilinear(float[] q, double x0, double x1, double z0, double z1, double fx, double fz) {
        float[][] c = new float[4][];
        for (int k = 0; k < 32; k += 8) {
            int ix = (q[k] - x0 > (x1 - x0) * 0.5 ? 1 : 0) + (q[k + 2] - z0 > (z1 - z0) * 0.5 ? 2 : 0);
            c[ix] = new float[] { q[k + 3], q[k + 4] };
        }
        for (int i = 0; i < 4; i++) if (c[i] == null) c[i] = new float[] { q[3], q[4] };
        double u = (c[0][0] * (1 - fx) + c[1][0] * fx) * (1 - fz) + (c[2][0] * (1 - fx) + c[3][0] * fx) * fz;
        double v = (c[0][1] * (1 - fx) + c[1][1] * fx) * (1 - fz) + (c[2][1] * (1 - fx) + c[3][1] * fx) * fz;
        return new float[] { (float) u, (float) v };
    }

    private static void bent(VertexConsumer vc, PoseStack.Pose pose, float[] q, double r, double a0, double a1, double halfW, int cr, int cg, int cb, int light, int overlay) {
        Matrix4f m4 = pose.pose();
        Matrix3f m3 = pose.normal();
        Vector3f n = new Vector3f();
        for (int k = 0; k < 32; k += 8) {
            double s = Math.max(0.0, Math.min(1.0, (q[k] + halfW) / (2.0 * halfW)));
            double th = Math.toRadians(a1 - s * (a1 - a0)), rho = r + q[k + 2];
            double sn = Math.sin(th), cs = Math.cos(th);
            float x = (float) (rho * sn), z = (float) (-rho * cs);
            float nx = q[k + 5], ny = q[k + 6], nz = q[k + 7];
            n.set((float) (nx * -cs + nz * sn), ny, (float) (nx * -sn + nz * -cs)).mul(m3);
            vc.addVertex(m4, x, q[k + 1], z).setColor(cr, cg, cb, 255).setUv(q[k + 3], q[k + 4]).setOverlay(overlay).setLight(light).setNormal(n.x, n.y, n.z);
        }
    }

    private record Skin(TextureAtlasSprite sprite, int r, int g, int b) {}

    private static Skin skin(BlockState s, Level level, BlockPos pos) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(s);
        List<BakedQuad> qs = model.getQuads(s, Direction.UP, RandomSource.create(42L), ModelData.EMPTY, null);
        BakedQuad q = qs.isEmpty() ? null : qs.get(0);
        TextureAtlasSprite sp = q != null ? q.getSprite() : model.getParticleIcon(ModelData.EMPTY);
        int col = 0xFFFFFF;
        if (q != null && q.isTinted()) col = Minecraft.getInstance().getBlockColors().getColor(s, level, pos, q.getTintIndex());
        return new Skin(sp, (col >> 16) & 255, (col >> 8) & 255, col & 255);
    }

    private static boolean coveredByHead(Level level, TurntableBlockEntity be) {
        long now = level.getGameTime();
        if (be.coverAt != Long.MIN_VALUE && now >= be.coverAt && now - be.coverAt < 20) return be.covered;
        be.coverAt = now;
        BlockPos p = be.getBlockPos();
        int n = 0;
        while (n++ <= Balance.TT_MAX && level.getBlockState(p.west()).getBlock() instanceof TurntableBlock) p = p.west();
        n = 0;
        while (n++ <= Balance.TT_MAX && level.getBlockState(p.north()).getBlock() instanceof TurntableBlock) p = p.north();
        BlockPos o = be.getBlockPos();
        boolean c = level.getBlockEntity(p) instanceof TurntableBlockEntity h && h.getBlockState().getValue(TurntableBlock.HEAD) && h.size() > 0
                && o.getX() >= p.getX() && o.getX() < p.getX() + h.size() && o.getZ() >= p.getZ() && o.getZ() < p.getZ() + h.size();
        be.covered = c;
        return c;
    }

    private static void lone(TurntableBlockEntity be, MeshModel mm, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        BlockState skin = be.copycat().shown();
        if (skin != null && be.getLevel() != null) CopycatSkin.draw(mm, "plate", ps.last().pose(), ps.last().normal(), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        else mm.draw("plate", ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TurntableBlockEntity be) {
        BlockPos o = be.getBlockPos();
        int n = Math.max(1, be.size());
        return new AABB(o.getX() - 1, o.getY() - 1, o.getZ() - 1, o.getX() + n + 1, o.getY() + 3, o.getZ() + n + 1);
    }

    @Override public boolean shouldRenderOffScreen(TurntableBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 256; }
}
