package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class NavLightRenderer implements BlockEntityRenderer<NavLightBlockEntity> {

    static final String MESH = "nav_light";
    static final String[] HEADS = { "pod", "dome", "lamp" };

    static final float[][] RGB = { { 1.0F, 0.13F, 0.08F }, { 0.15F, 1.0F, 0.35F }, { 1.0F, 0.97F, 0.9F }, { 0.95F, 0.97F, 1.0F }, { 1.0F, 0.18F, 0.1F }, { 1.0F, 0.93F, 0.78F } };

    private static final Map<String, float[]> CENTRE = new HashMap<>();

    static float[] centre(MeshModel m, String bone) {
        float[] c = CENTRE.get(bone);
        if (c != null) return c;
        float[] b = { Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE };
        m.forEachQuad(bone, (v, i) -> { for (int k = 0; k < 32; k += 8) { b[0] = Math.min(b[0], v[i + k]); b[1] = Math.max(b[1], v[i + k]); b[2] = Math.min(b[2], v[i + k + 2]); b[3] = Math.max(b[3], v[i + k + 2]); } });
        if (b[0] > b[1]) return new float[2];
        c = new float[] { (b[0] + b[1]) * 0.5F, (b[2] + b[3]) * 0.5F };
        CENTRE.put(bone, c);
        return c;
    }

    static float flash(NavLightBlock.Func f, double t, boolean hung) {
        if (f == NavLightBlock.Func.STROBE) {
            int p = (int) Math.floor(t) % Balance.NL_STROBE_PERIOD;
            return p < 2 || (p >= 5 && p < 7) ? 1.0F : 0.0F;
        }
        if (f == NavLightBlock.Func.BEACON) {
            double ph = ((t + (hung ? Balance.NL_BEACON_PERIOD * 0.5 : 0.0)) % Balance.NL_BEACON_PERIOD) / Balance.NL_BEACON_PERIOD;
            double c = Math.max(0.0, Math.cos(ph * Math.PI * 2.0));
            return (float) (c * c * c);
        }
        return 1.0F;
    }

    @Override
    public void render(NavLightBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level lv = be.getLevel();
        BlockState s = be.getBlockState();
        if (lv == null || !(s.getBlock() instanceof NavLightBlock)) return;
        MeshModel m = MeshModel.named(MESH);
        NavLightBlock.Func func = s.getValue(NavLightBlock.FUNC);
        String head = HEADS[func.head];
        if (!m.has(head)) return;
        BlockPos pos = be.getBlockPos();
        float[] f = NavLightBlock.frame(lv, pos, s);
        double t = lv.getGameTime() + partialTick;
        boolean dark = ClientHemp.darkAt(pos.getX() + 0.5, pos.getZ() + 0.5);
        boolean on = be.lit() && !dark;
        float k = on ? flash(func, t, s.getValue(NavLightBlock.ATTACH) == Direction.UP) : 0.0F;
        float[] c = RGB[func.ordinal()];

        float[] mc = centre(m, head);
        ps.pushPose();
        ps.translate(f[0], f[1], f[2]);
        Matrix4f rot = new Matrix4f(f[3], f[4], f[5], 0.0F, f[6], f[7], f[8], 0.0F, -f[9], -f[10], -f[11], 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        ps.last().pose().mul(rot);
        ps.last().normal().mul(new Matrix3f(rot));
        ps.translate(-mc[0], 0.0F, -mc[1]);
        m.draw(head, ps.last(), buffers, light, overlay);
        if (m.has(head + "_lens")) {
            boolean glow = k > 0.12F;
            int g = glow ? Math.round(110 + 145 * k) : 70;
            m.drawTinted(head + "_lens", ps.last(), buffers, glow ? 0xF000F0 : light, overlay, Math.round(c[0] * g), Math.round(c[1] * g), Math.round(c[2] * g), 255, false);
        }
        ps.popPose();
        if (!on) return;

        float[] lp = NavLightBlock.lensPoint(f, func);

        Vec3 w = drawnAt(ps, lp[0], lp[1], lp[2]);
        float bk = Balance.NL_BRIGHT[be.bright()];
        long key = 0x4C49_0000_0000_0000L ^ pos.asLong(), now = lv.getGameTime();
        switch (func) {
            case STROBE -> VeilBridge.machineGlow(key, w.x, w.y, w.z, c[0], c[1], c[2], Balance.NL_STROBE_R * bk, Balance.NL_STROBE_B * bk * k, now);
            case BEACON -> VeilBridge.machineGlow(key, w.x, w.y, w.z, c[0], c[1], c[2], Balance.NL_BEACON_R * bk, Balance.NL_BEACON_B * bk * k, now);
            default -> VeilBridge.machineGlow(key, w.x, w.y, w.z, c[0], c[1], c[2], Balance.NL_POOL_R * bk, Balance.NL_POOL_B * bk, now);
        }
        if (func == NavLightBlock.Func.LANDING) {
            Vec3 ahead = drawnAt(ps, lp[0] + f[9], lp[1] + f[10], lp[2] + f[11]);
            VeilBridge.spot(key ^ 0x0100_0000_0000_0000L, w.x, w.y, w.z, ahead.x - w.x, ahead.y - w.y, ahead.z - w.z, c[0], c[1], c[2],
                    (float) Math.toRadians(Balance.NL_SPOT_ANGLE), Balance.NL_SPOT_DIST * bk, Balance.NL_SPOT_B * bk, now);
        }
        glint(ps, buffers, lp, w, c, k, func == NavLightBlock.Func.STROBE);
    }

    static Vec3 drawnAt(PoseStack ps, float x, float y, float z) {
        Vector4f q = new Vector4f(x, y, z, 1.0F).mul(ps.last().pose());
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return new Vec3(cam.x + q.x, cam.y + q.y, cam.z + q.z);
    }

    private static void glint(PoseStack ps, MultiBufferSource buffers, float[] lp, Vec3 w, float[] c, float k, boolean strobe) {
        if (k < 0.05F) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        double d = w.distanceTo(cam.getPosition());
        if (d < Balance.NL_GLINT_FROM) return;
        float fade = (float) Math.min(1.0, (d - Balance.NL_GLINT_FROM) / Balance.NL_GLINT_FADE);
        float sz = (float) (d * Balance.NL_GLINT_PX) * (strobe ? Balance.NL_GLINT_FLASH : 1.0F);
        Vector4f q = new Vector4f(lp[0], lp[1], lp[2], 1.0F).mul(ps.last().pose());
        Vector3f l = new Vector3f(cam.getLeftVector()).mul(sz), u = new Vector3f(cam.getUpVector()).mul(sz);
        int a = Math.round(255 * fade * Math.min(1.0F, k)), r = Math.round(c[0] * 255), g = Math.round(c[1] * 255), b = Math.round(c[2] * 255);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        float[][] p = { { q.x + l.x, q.y + l.y, q.z + l.z }, { q.x + u.x, q.y + u.y, q.z + u.z }, { q.x - l.x, q.y - l.y, q.z - l.z }, { q.x - u.x, q.y - u.y, q.z - u.z } };
        for (int i = 0; i < 4; i++) vc.addVertex(p[i][0], p[i][1], p[i][2]).setColor(r, g, b, a);
        for (int i = 3; i >= 0; i--) vc.addVertex(p[i][0], p[i][1], p[i][2]).setColor(r, g, b, a);
    }

    @Override public AABB getRenderBoundingBox(NavLightBlockEntity be) { return new AABB(be.getBlockPos()).inflate(1.0); }
    @Override public boolean shouldRenderOffScreen(NavLightBlockEntity be) { return true; }
    @Override public int getViewDistance() { return Balance.NL_VIEW; }
}
