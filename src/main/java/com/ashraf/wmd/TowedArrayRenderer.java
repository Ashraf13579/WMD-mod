package com.ashraf.wmd;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TowedArrayRenderer extends StagedUnitRenderer<TowedArrayWinchBlockEntity> {

    private static final int CABLE = 0xFF262C33, ARRAY = 0xFF3D8BFF, TAIL = 0xFFE0E6EE;

    public TowedArrayRenderer() { super("towed_array_winch", 2); }

    @Override
    public void render(TowedArrayWinchBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, poseStack, buffers, light, overlay);
        if (be.getLevel() == null) return;
        double tt = be.getLevel().getGameTime() + partialTick;
        List<Vec3> pts = be.clientPoints(tt);
        if (pts.size() < 2) return;
        Vec3 c = Vec3.atCenterOf(be.getBlockPos());
        Vec3 cw = ShipFrame.drawnWorld(be.getLevel(), be.getBlockPos(), c, partialTick);
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose p = poseStack.last();

        double[] s = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) s[i] = s[i - 1] + pts.get(i).distanceTo(pts.get(i - 1));
        double arrayFrom = s[s.length - 1] - Balance.TOWED_ARRAY_LEN;
        Vec3 prev = local(be, pts.get(0), c, cw, partialTick);
        for (int i = 1; i < pts.size(); i++) {
            Vec3 cur = local(be, pts.get(i), c, cw, partialTick);
            if (s[i] <= arrayFrom || s[i - 1] >= arrayFrom) seg(vc, p, prev, cur, s[i - 1] >= arrayFrom);
            else {
                double k = (arrayFrom - s[i - 1]) / Math.max(1.0E-6, s[i] - s[i - 1]);
                Vec3 m = prev.add(cur.subtract(prev).scale(k));
                seg(vc, p, prev, m, false);
                seg(vc, p, m, cur, true);
            }
            prev = cur;
        }

        Vec3 a = local(be, pts.get(pts.size() - 2), c, cw, partialTick), b = prev;
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() > 1.0E-6) {
            Vec3 e = b.add(d.normalize().scale(1.2));
            CcRenderers.line(vc, p, (float) b.x, (float) b.y, (float) b.z, (float) e.x, (float) e.y, (float) e.z, TAIL);
        }
    }

    private static Vec3 local(TowedArrayWinchBlockEntity be, Vec3 w, Vec3 c, Vec3 cw, float pt) {
        Vec3 d = ShipFrame.dirInFrame(be.getLevel(), be.getBlockPos(), w.subtract(cw), pt);
        return c.add(d).subtract(be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
    }

    private static void seg(VertexConsumer vc, PoseStack.Pose p, Vec3 a, Vec3 b, boolean array) {
        CcRenderers.line(vc, p, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, array ? ARRAY : CABLE);
        if (array) CcRenderers.line(vc, p, (float) a.x, (float) a.y - 0.06F, (float) a.z, (float) b.x, (float) b.y - 0.06F, (float) b.z, ARRAY);
    }

    @Override
    public AABB getRenderBoundingBox(TowedArrayWinchBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Balance.TOWED_LENGTH + 4.0);
    }

    @Override
    public boolean shouldRenderOffScreen(TowedArrayWinchBlockEntity be) { return true; }
}
