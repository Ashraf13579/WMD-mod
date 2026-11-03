package com.ashraf.wmd;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CctvRenderers {

    private CctvRenderers() {}

    private static boolean ownerSneakingNear(net.minecraft.world.level.block.entity.BlockEntity be, Owned o) {
        var p = Minecraft.getInstance().player;
        return p != null && p.isShiftKeyDown() && o.getOwner() != null && o.getOwner().equals(p.getUUID()) && p.distanceToSqr(be.getBlockPos().getCenter()) < 256.0;
    }

    public static class Sensor extends StagedUnitRenderer<PerimeterSensorBlockEntity> {
        public Sensor() { super("perimeter_sensor", 1); }

        @Override
        public void render(PerimeterSensorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!ownerSneakingNear(be, be)) return;
            Vec3 o = Vec3.atLowerCornerOf(be.getBlockPos()), a = be.lens().subtract(o);
            VertexConsumer vc = buf.getBuffer(RenderType.lines());
            PoseStack.Pose pose = ps.last();
            if (be.partner() != null) {
                Vec3 b = Vec3.atBottomCenterOf(be.partner()).add(0.0, PerimeterSensorBlockEntity.LENS_Y / 16.0, 0.0).subtract(o);
                seg(vc, pose, a, b, 0.9F);
                return;
            }
            Vec3 f = be.front();
            double r = Balance.SENSOR_CONE, t = Math.tan(Math.toRadians(Balance.SENSOR_CONE_DEG));
            Vec3 side = new Vec3(-f.z, 0.0, f.x);
            seg(vc, pose, a, a.add(f.scale(r)), 0.7F);
            seg(vc, pose, a, a.add(f.add(side.scale(t)).normalize().scale(r)), 0.5F);
            seg(vc, pose, a, a.add(f.add(side.scale(-t)).normalize().scale(r)), 0.5F);
        }

        @Override public boolean shouldRenderOffScreen(PerimeterSensorBlockEntity be) { return true; }

        @Override
        public AABB getRenderBoundingBox(PerimeterSensorBlockEntity be) { return new AABB(be.getBlockPos()).inflate(Balance.SENSOR_BEAM); }
    }

    private static void seg(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float alpha) {
        Vec3 n = b.subtract(a).normalize();
        vc.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(1.0F, 0.15F, 0.1F, alpha).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(1.0F, 0.15F, 0.1F, alpha * 0.4F).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    public static class Floodlight extends StagedUnitRenderer<FloodlightBlockEntity> {
        public Floodlight() { super("floodlight", 1); }

        @Override
        public void render(FloodlightBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.onShown() || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Vec3 o = Vec3.atLowerCornerOf(be.getBlockPos()), from = be.pivot(), d = be.dir(be.yaw(pt), be.pitch(pt));
            Vec3 end = be.beamEnd();
            double len = end == null ? Balance.FLOOD_LEN : Math.max(1.0, end.distanceTo(from));
            Vec3 a = from.add(d.scale(0.3)).subtract(o);
            Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 u = d.cross(up).normalize(), v = d.cross(u).normalize();
            VertexConsumer vc = buf.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();
            int seg = 14;
            double r0 = 0.2, r1 = 0.2 + len * 0.22;
            for (int k = 0; k < seg; k++) {
                double t0 = Math.PI * 2 * k / seg, t1 = Math.PI * 2 * (k + 1) / seg;
                Vec3 p0 = a.add(u.scale(Math.cos(t0) * r0)).add(v.scale(Math.sin(t0) * r0)), p1 = a.add(u.scale(Math.cos(t1) * r0)).add(v.scale(Math.sin(t1) * r0));
                Vec3 e = a.add(d.scale(len));
                Vec3 q0 = e.add(u.scale(Math.cos(t0) * r1)).add(v.scale(Math.sin(t0) * r1)), q1 = e.add(u.scale(Math.cos(t1) * r1)).add(v.scale(Math.sin(t1) * r1));
                quad(vc, m, p0, p1, q1, q0);
            }
        }

        private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 q1, Vec3 q0) {
            vc.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(1.0F, 0.95F, 0.8F, 0.16F);
            vc.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(1.0F, 0.95F, 0.8F, 0.16F);
            vc.addVertex(m, (float) q1.x, (float) q1.y, (float) q1.z).setColor(1.0F, 0.95F, 0.8F, 0.0F);
            vc.addVertex(m, (float) q0.x, (float) q0.y, (float) q0.z).setColor(1.0F, 0.95F, 0.8F, 0.0F);
        }

        @Override public boolean shouldRenderOffScreen(FloodlightBlockEntity be) { return true; }

        @Override
        public AABB getRenderBoundingBox(FloodlightBlockEntity be) { return new AABB(be.getBlockPos()).inflate(Balance.FLOOD_LEN); }
    }

    private static final float[][] SCREENS = { { 8.6F, 21.2F, 17.8F, 25.0F }, { -5.2F, 7.4F, 17.8F, 25.0F }, { 8.6F, 21.2F, 9.8F, 17.0F }, { -5.2F, 7.4F, 9.8F, 17.0F } };
    private static final float SCR_Z = -3.65F;

    public static class Monitor extends StagedUnitRenderer<CcSecurityMonitorBlockEntity> {
        public Monitor() { super("cc_security_monitor", 2); }

        @Override
        public void render(CcSecurityMonitorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isOnline() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            CcRenderers.modelFrame(ps, f);
            double tt = be.getLevel().getGameTime() + pt;
            VertexConsumer q = buf.getBuffer(RenderType.debugQuads());
            Matrix4f m = ps.last().pose();
            for (int k = 0; k < 4; k++) {
                float[] s = SCREENS[k];
                int st = be.screenState(k);
                float x0 = s[0] + 0.3F, x1 = s[1] - 0.3F, y0 = s[2] + 0.3F, y1 = s[3] - 0.3F, h = y1 - y0;
                CcRenderers.rect(q, m, x0, y0, x1, y1, SCR_Z, CcRenderers.screenBg(be, st == CcSecurityMonitorBlockEntity.NONE ? 0xF0060A10 : 0xF00A1A16));
                if (st == CcSecurityMonitorBlockEntity.NONE) continue;
                if (st == CcSecurityMonitorBlockEntity.LIVE) {

                    CcRenderers.rect(q, m, x0, y0, x1, y0 + h * 0.45F, SCR_Z - 0.02F, 0xC0203A28);
                    CcRenderers.rect(q, m, x0, y0 + h * 0.45F, x1, y1, SCR_Z - 0.02F, 0xC0182A34);
                    float sy = y1 - (float) ((tt * 0.12 + k * 0.7) % 1.0) * h;
                    CcRenderers.rect(q, m, x0, sy - 0.25F, x1, sy + 0.25F, SCR_Z - 0.04F, 0x50A0FFC0);
                    if (((long) (tt / 10.0)) % 2 == 0) CcRenderers.rect(q, m, x0 + 0.5F, y1 - 1.4F, x0 + 1.3F, y1 - 0.6F, SCR_Z - 0.05F, 0xFFFF3030);
                    CcRenderers.text(ps, buf, "CAM " + (k + 1), (x0 + x1) / 2.0F, y0 + 1.0F, SCR_Z - 0.06F, h * 0.16F, 0xFFC0E0C8);
                } else {
                    long seed = (long) (tt / 2.0) + k * 131L;
                    for (int n = 0; n < 18; n++) {
                        long hsh = (seed * 31 + n) * 0x9E3779B97F4A7C15L;
                        float u = ((hsh >>> 8) & 1023) / 1023.0F, v = ((hsh >>> 20) & 1023) / 1023.0F;
                        float sx = x0 + u * (x1 - x0 - 1.2F), sy = y0 + v * (h - 0.4F);
                        CcRenderers.rect(q, m, sx, sy, sx + 1.2F, sy + 0.4F, SCR_Z - 0.03F, 0x70FFFFFF);
                    }
                    CcRenderers.text(ps, buf, st == CcSecurityMonitorBlockEntity.STATIC ? "STATIC" : "NO SIGNAL", (x0 + x1) / 2.0F, (y0 + y1) / 2.0F, SCR_Z - 0.06F, h * 0.18F, 0xFF8090A0);
                }
                if (be.screenAlert() == k && ((long) (tt / 6.0)) % 2 == 0) {
                    int red = 0xFFFF3030;
                    CcRenderers.rect(q, m, x0 - 0.3F, y0 - 0.3F, x1 + 0.3F, y0, SCR_Z - 0.07F, red);
                    CcRenderers.rect(q, m, x0 - 0.3F, y1, x1 + 0.3F, y1 + 0.3F, SCR_Z - 0.07F, red);
                    CcRenderers.rect(q, m, x0 - 0.3F, y0, x0, y1, SCR_Z - 0.07F, red);
                    CcRenderers.rect(q, m, x1, y0, x1 + 0.3F, y1, SCR_Z - 0.07F, red);
                }
            }
            ps.popPose();
        }
    }
}
