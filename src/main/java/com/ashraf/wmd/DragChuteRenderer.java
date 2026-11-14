package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DragChuteRenderer implements BlockEntityRenderer<DragChuteBlockEntity> {

    private static final float HALF = 0.03F, CR = 0.18F, CG = 0.19F, CB = 0.21F, PX = 1.0F / 16.0F;

    private final Look look = new Look();

    @Override
    public void render(DragChuteBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        look.render(be, partialTick, ps, buffers, light, overlay);
        if (be.chuteState() != DragChuteBlockEntity.OUT || be.getLevel() == null) return;
        MeshModel chute = MeshModel.named("eject_chute");
        if (!chute.has("chute")) return;
        Direction aft = be.aft();
        Vec3 dir = new Vec3(aft.getStepX(), aft.getStepY(), aft.getStepZ());
        Vec3 fit = new Vec3(0.5, 7.0 * PX, 0.5).add(dir.scale(6.6 * PX));
        float age = (float) (be.getLevel().getGameTime() - be.chuteStateAt()) + partialTick;
        float open = Math.min(1.0F, age / Balance.CHUTE_OPEN_TICKS);
        double trail = 1.0 + (Balance.CHUTE_TRAIL - 1.0) * Math.min(1.0, age / (Balance.CHUTE_OPEN_TICKS * 0.6));
        Vec3 link = fit.add(dir.scale(trail));

        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        strand(vc, ps.last().pose(), fit, link);

        ps.pushPose();
        ps.translate(link.x, link.y, link.z);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(dir.x, dir.z)));
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        float sway = (float) Math.sin(age * 0.11) * 4.0F * open;
        ps.mulPose(Axis.ZP.rotationDegrees(sway));
        float s = (float) Balance.CHUTE_SCALE, up = Math.min(1.0F, open * 2.0F), spread = 0.15F + 0.85F * Math.max(0.0F, open * 2.0F - 1.0F);
        ps.scale(s * spread, s * up, s * spread);
        chute.draw("chute", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static void strand(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 q) {
        Vec3 d = q.subtract(p);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 u = Math.abs(d.normalize().y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = d.cross(u).normalize().scale(HALF), s2 = d.cross(s1).normalize().scale(HALF);
        ribbon(vc, m, p, q, s1);
        ribbon(vc, m, p, q, s2);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 q, Vec3 s) {
        vc.addVertex(m, (float) (p.x - s.x), (float) (p.y - s.y), (float) (p.z - s.z)).setColor(CR, CG, CB, 1.0F);
        vc.addVertex(m, (float) (p.x + s.x), (float) (p.y + s.y), (float) (p.z + s.z)).setColor(CR, CG, CB, 1.0F);
        vc.addVertex(m, (float) (q.x - s.x), (float) (q.y - s.y), (float) (q.z - s.z)).setColor(CR, CG, CB, 1.0F);
        vc.addVertex(m, (float) (q.x + s.x), (float) (q.y + s.y), (float) (q.z + s.z)).setColor(CR, CG, CB, 1.0F);
    }

    @Override
    public AABB getRenderBoundingBox(DragChuteBlockEntity be) {
        return look.getRenderBoundingBox(be).inflate(Balance.CHUTE_TRAIL + 4.0);
    }

    @Override public boolean shouldRenderOffScreen(DragChuteBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Look extends StagedUnitRenderer<DragChuteBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing", "housing_lid");
        private BlockState skin;

        Look() { super("drag_chute", 1, 1); }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(DragChuteBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(DragChuteBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            for (String b : ATTACH)
                if (poses.containsKey(b)) CopycatSkin.draw(MeshModel.named("drag_chute"), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }
}
