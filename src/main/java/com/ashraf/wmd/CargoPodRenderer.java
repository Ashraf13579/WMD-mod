package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class CargoPodRenderer implements BlockEntityRenderer<CargoPodBlockEntity> {

    static final float PX = 1.0F / 16.0F;
    static final int GORES = 10;

    record Pose(float fans, float clamps, float hatch, boolean light, boolean rcs, float burn) {}

    @Override
    public void render(CargoPodBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("cargo_pod");
        Level level = be.getLevel();
        if (!mesh.has("pod") || level == null) return;
        float t = level.getGameTime() + pt;
        Direction dir = Station.spotDir(be.port());
        float u = be.arrive() > 0L ? Math.max(0.0F, Math.min(1.0F, (t - be.arrive()) / Balance.CARGO_ARRIVE_TICKS)) : 1.0F;
        float e = (1.0F - u) * (1.0F - u);
        ps.pushPose();
        portFrame(ps, dir);
        ps.translate(16.0F * e, 0.0F, 0.0F);
        podFrame(ps);
        float fans = smooth(u / 0.5F), clamps = u < 0.85F ? 1.0F : 1.0F - smooth((u - 0.85F) / 0.1F);
        boolean rcs = u < 0.92F && ((int) (t / 3.0F) % 3 != 0);
        draw(mesh, new Pose(fans, clamps, be.open() ? 1.0F : 0.0F, be.open(), rcs, 0.0F), ps, buffers, light, overlay, 255);
        ps.popPose();
        if (u < 1.0F && level.random.nextFloat() < 0.35F) {
            var n = dir.getNormal();
            double ox = n.getX() * (3.0 + 16.0 * e), oz = n.getZ() * (3.0 + 16.0 * e);
            level.addParticle(ParticleTypes.CLOUD, be.getBlockPos().getX() + 0.5 + ox + (level.random.nextFloat() - 0.5) * 3.0, be.getBlockPos().getY() + 1.5 + (level.random.nextFloat() - 0.5) * 3.0,
                    be.getBlockPos().getZ() + 0.5 + oz + (level.random.nextFloat() - 0.5) * 3.0, (level.random.nextFloat() - 0.5) * 0.2, (level.random.nextFloat() - 0.5) * 0.2, (level.random.nextFloat() - 0.5) * 0.2);
        }
    }

    static void portFrame(PoseStack ps, Direction dir) {
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (dir) { case SOUTH -> -90.0F; case WEST -> 180.0F; case NORTH -> 90.0F; default -> 0.0F; }));
    }

    static void podFrame(PoseStack ps) {
        ps.translate(1.5F, 1.5F, 0.0F);
        ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
    }

    static float smooth(float x) { x = Math.max(0.0F, Math.min(1.0F, x)); return x * x * (3.0F - 2.0F * x); }

    static void draw(MeshModel mesh, Pose p, PoseStack ps, MultiBufferSource buffers, int light, int overlay, int alpha) {
        bone(mesh, "pod", ps, buffers, light, overlay, alpha);

        ps.pushPose();
        about(ps, 21.0F, 36.0F, 0.0F, Axis.ZP, 100.0F * smooth(p.hatch()));
        bone(mesh, "hatch", ps, buffers, light, overlay, alpha);
        ps.popPose();
        if (mesh.has("cargo_led")) {
            if (p.light()) mesh.drawColoured("cargo_led", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay, 90, 170, 255);
            else mesh.drawColoured("cargo_led", ps.last(), buffers, light, overlay, 70, 80, 95);
        }

        for (int k = 0; k < 4; k++) {
            float s = k % 2 == 0 ? 1.0F : -1.0F, a = 40.0F * smooth(p.clamps());
            ps.pushPose();
            if (k < 2) about(ps, s * 30.6F, 50.0F, 0.0F, Axis.ZP, -a * s);
            else about(ps, 0.0F, 50.0F, s * 30.6F, Axis.XP, a * s);
            bone(mesh, "clamp" + k, ps, buffers, light, overlay, alpha);
            ps.popPose();
        }

        for (int k = 0; k < 4; k++)
            if (mesh.has("rcs" + k)) {
                if (p.rcs()) mesh.drawColoured("rcs" + k, ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay, 255, 245, 220);
                else mesh.drawColoured("rcs" + k, ps.last(), buffers, light, overlay, 50, 50, 55);
            }
        if (p.burn() > 0.0F && mesh.has("burn"))
            mesh.drawColoured("burn", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay, 255, (int) (120 + 100 * p.burn()), (int) (40 + 60 * p.burn()));

        float open = smooth(p.fans() / 0.45F), gores = smooth((p.fans() - 0.35F) / 0.65F);
        for (String a : new String[] { "p", "s" }) {
            float zs = a.equals("p") ? 1.0F : -1.0F;
            ps.pushPose();
            about(ps, 0.0F, 10.0F, zs * 22.0F, Axis.XP, -90.0F * zs * (1.0F - open));
            bone(mesh, "fan_" + a, ps, buffers, light, overlay, alpha);
            float tgt = -90.0F * zs;
            for (int k = 0; k < GORES; k++) {
                ps.pushPose();
                about(ps, 0.0F, 4.0F, zs * 48.0F, Axis.YP, (18.0F + 36.0F * k - tgt) * (1.0F - gores));
                bone(mesh, "fan_" + a + k, ps, buffers, light, overlay, alpha);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    static void about(PoseStack ps, float x, float y, float z, Axis axis, float deg) {
        ps.translate(x * PX, y * PX, z * PX);
        ps.mulPose(axis.rotationDegrees(deg));
        ps.translate(-x * PX, -y * PX, -z * PX);
    }

    private static void bone(MeshModel mesh, String b, PoseStack ps, MultiBufferSource buffers, int light, int overlay, int alpha) {
        if (!mesh.has(b)) return;
        if (alpha >= 255) mesh.draw(b, ps.last(), buffers, light, overlay);
        else mesh.drawTinted(b, ps.last(), buffers, light, overlay, 255, 255, 255, alpha, false);
    }

    @Override public boolean shouldRenderOffScreen(CargoPodBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(CargoPodBlockEntity be) { return new AABB(be.getBlockPos()).inflate(20.0, 6.0, 20.0); }
    @Override public int getViewDistance() { return 256; }
}
