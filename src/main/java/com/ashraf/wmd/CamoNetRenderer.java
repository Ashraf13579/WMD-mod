package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class CamoNetRenderer implements BlockEntityRenderer<CamoNetBlockEntity> {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/block/camo_canopy.png"),
            THERMAL_TEX = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/block/thermal_canopy.png");
    private static final double CELL = 0.5, SAG = 0.3, TILE = 2.0, POLE_GAP = 8.0;
    private static final int SKIRT_ROWS = 3;

    @Override
    public void render(CamoNetBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        ps.pushPose();

        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        MeshModel crate = be.thermal() && MeshModel.named("thermal_net").has("base") ? MeshModel.named("thermal_net") : MeshModel.named("camo_net");
        crate.draw("base", ps.last(), buffers, light, overlay);
        ps.popPose();
        if (!be.has() || be.getLevel() == null) return;
        Level level = be.getLevel();
        BlockPos o = be.getBlockPos();
        double m = CamoNetBlockEntity.MARGIN;
        double X0 = be.x0() - m - o.getX(), X1 = be.x1() + 1 + m - o.getX(), Z0 = be.z0() - m - o.getZ(), Z1 = be.z1() + 1 + m - o.getZ();
        double ground = be.baseY() - o.getY(), H = be.roofY() - o.getY();
        int n = be.poleSections();

        java.util.List<double[]> poles = new java.util.ArrayList<>();
        int px = Math.max(1, (int) Math.ceil((X1 - X0) / POLE_GAP)), pz = Math.max(1, (int) Math.ceil((Z1 - Z0) / POLE_GAP));
        for (int i = 0; i <= px; i++) { poles.add(new double[] { X0 + (X1 - X0) * i / px, Z0 }); poles.add(new double[] { X0 + (X1 - X0) * i / px, Z1 }); }
        for (int j = 1; j < pz; j++) { poles.add(new double[] { X0, Z0 + (Z1 - Z0) * j / pz }); poles.add(new double[] { X1, Z0 + (Z1 - Z0) * j / pz }); }
        for (double[] c : poles) {
            int pl = LevelRenderer.getLightColor(level, BlockPos.containing(o.getX() + c[0], be.baseY() + 1, o.getZ() + c[1]));
            draw(ps, buffers, "camo_stake", c[0], ground, c[1], pl, overlay);
            for (int k = 0; k < n; k++) draw(ps, buffers, "camo_pole", c[0], ground + CamoNetBlockEntity.POLE_START + k, c[1], pl, overlay);
            draw(ps, buffers, "camo_pole_top", c[0], ground + CamoNetBlockEntity.POLE_START + n, c[1], pl, overlay);
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(be.thermal() ? THERMAL_TEX : TEX));
        PoseStack.Pose pose = ps.last();
        boolean torn = be.torn();
        int nx = Math.max(2, (int) Math.round((X1 - X0) / CELL)), nz = Math.max(2, (int) Math.round((Z1 - Z0) / CELL));
        int roofLight = LevelRenderer.getLightColor(level, BlockPos.containing(o.getX() + (X0 + X1) * 0.5, o.getY() + H + 0.5, o.getZ() + (Z0 + Z1) * 0.5));
        for (int i = 0; i < nx; i++) for (int j = 0; j < nz; j++) {
            if (torn && hash(i, j, 1) % 10 < 4) continue;
            double[] a = roof(X0, X1, Z0, Z1, H, i / (double) nx, j / (double) nz), b = roof(X0, X1, Z0, Z1, H, (i + 1) / (double) nx, j / (double) nz),
                    c = roof(X0, X1, Z0, Z1, H, (i + 1) / (double) nx, (j + 1) / (double) nz), d = roof(X0, X1, Z0, Z1, H, i / (double) nx, (j + 1) / (double) nz);
            quad(vc, pose, a, b, c, d, 0.0F, 1.0F, 0.0F, roofLight, o);
        }

        double hem = ground + 0.5;
        for (int side = 0; side < 4; side++) {
            boolean alongX = side < 2;
            int cells = alongX ? nx : nz;
            double ox = side == 2 ? -1 : side == 3 ? 1 : 0, oz = side == 0 ? -1 : side == 1 ? 1 : 0;
            int sl = LevelRenderer.getLightColor(level, BlockPos.containing(o.getX() + (side == 2 ? X0 - 0.5 : side == 3 ? X1 + 0.5 : (X0 + X1) * 0.5),
                    be.baseY() + 1, o.getZ() + (side == 0 ? Z0 - 0.5 : side == 1 ? Z1 + 0.5 : (Z0 + Z1) * 0.5)));
            for (int i = 0; i < cells; i++) for (int r = 0; r < SKIRT_ROWS; r++) {
                if (torn && hash(i, r, 2 + side) % 10 < 6) continue;
                double[] a = skirt(X0, X1, Z0, Z1, H, hem, side, i / (double) cells, r / (double) SKIRT_ROWS, ox, oz),
                        b = skirt(X0, X1, Z0, Z1, H, hem, side, (i + 1) / (double) cells, r / (double) SKIRT_ROWS, ox, oz),
                        c = skirt(X0, X1, Z0, Z1, H, hem, side, (i + 1) / (double) cells, (r + 1) / (double) SKIRT_ROWS, ox, oz),
                        d = skirt(X0, X1, Z0, Z1, H, hem, side, i / (double) cells, (r + 1) / (double) SKIRT_ROWS, ox, oz);
                quad(vc, pose, a, b, c, d, (float) ox, 0.0F, (float) oz, sl, o);
            }
        }
    }

    private static double[] roof(double X0, double X1, double Z0, double Z1, double H, double u, double v) {
        double x = X0 + (X1 - X0) * u, z = Z0 + (Z1 - Z0) * v;
        double y = H - SAG * (Math.sin(Math.PI * u) + Math.sin(Math.PI * v)) + 0.04 * Math.sin(u * 7.0 + v * 5.0) * Math.sin(Math.PI * u) * Math.sin(Math.PI * v);
        return new double[] { x, y, z };
    }

    private static double[] skirt(double X0, double X1, double Z0, double Z1, double H, double hem, int side, double t, double k, double ox, double oz) {
        double u = side == 2 ? 0.0 : side == 3 ? 1.0 : t, v = side == 0 ? 0.0 : side == 1 ? 1.0 : t;
        double[] top = roof(X0, X1, Z0, Z1, H, u, v);
        double bottom = hem + 0.25 * Math.sin(t * 9.0 + side) + 0.15 * Math.sin(t * 23.0);
        double lean = 0.4 * k * k;
        return new double[] { top[0] + ox * lean, top[1] + (bottom - top[1]) * k, top[2] + oz * lean };
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double[] a, double[] b, double[] c, double[] d, float nx, float ny, float nz, int light, BlockPos o) {
        vertex(vc, pose, a, nx, ny, nz, light, o);
        vertex(vc, pose, b, nx, ny, nz, light, o);
        vertex(vc, pose, c, nx, ny, nz, light, o);
        vertex(vc, pose, d, nx, ny, nz, light, o);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, double[] p, float nx, float ny, float nz, int light, BlockPos o) {
        double wx = p[0] + o.getX(), wy = p[1] + o.getY(), wz = p[2] + o.getZ();
        float u = (float) ((ny > 0.5F ? wx : nx != 0.0F ? wz : wx) / TILE), v = (float) ((ny > 0.5F ? wz : -wy) / TILE);
        vc.addVertex(pose, (float) p[0], (float) p[1], (float) p[2]).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static void draw(PoseStack ps, MultiBufferSource buffers, String mesh, double x, double y, double z, int light, int overlay) {
        MeshModel mm = MeshModel.named(mesh);
        if (!mm.has("part")) return;
        ps.pushPose();
        ps.translate(x, y, z);
        mm.draw("part", ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    private static int hash(int a, int b, int c) { return Math.abs((a * 73_856_093) ^ (b * 19_349_663) ^ (c * 83_492_791)) & 0x7FFF_FFFF; }

    @Override
    public AABB getRenderBoundingBox(CamoNetBlockEntity be) {
        AABB block = new AABB(be.getBlockPos());
        if (!be.has()) return block;
        double m = CamoNetBlockEntity.MARGIN + 1.0;
        return block.minmax(new AABB(be.x0() - m, be.baseY() - 1, be.z0() - m, be.x1() + 1 + m, be.roofY() + 1, be.z1() + 1 + m));
    }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public boolean shouldRenderOffScreen(CamoNetBlockEntity be) { return true; }

    static float clamp01(double v) { return (float) Mth.clamp(v, 0.0, 1.0); }
}
