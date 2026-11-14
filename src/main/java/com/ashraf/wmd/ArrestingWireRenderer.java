package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ArrestingWireRenderer implements BlockEntityRenderer<ArrestingWireBlockEntity> {

    private static final float HALF = 0.035F, CR = 0.16F, CG = 0.17F, CB = 0.19F;

    private final Look look = new Look();

    @Override
    public void render(ArrestingWireBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        look.render(be, partialTick, ps, buffers, light, overlay);
        if (!be.master() || be.getLevel() == null) return;
        Vec3[] e = be.ends();
        Vec3 o = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 a = e[0].subtract(o), b = e[1].subtract(o);
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        int state = be.wireState();
        if (state == ArrestingWireBlockEntity.CAUGHT && be.tipLocal() != null) {
            Vec3 t = be.tipLocal().subtract(o);
            strand(vc, m, a, t, light);
            strand(vc, m, t, b, light);
            return;
        }
        if (state == ArrestingWireBlockEntity.RESET) {
            double k = Math.min(1.0, (be.getLevel().getGameTime() - be.stateAt() + partialTick) / (double) Balance.WIRE_RESET_TICKS);
            Vec3 mid = a.add(b).scale(0.5).add(0.0, -Balance.WIRE_UP * (1.0 - k), 0.0);
            strand(vc, m, a, mid, light);
            strand(vc, m, mid, b, light);
            return;
        }
        strand(vc, m, a, b, light);
    }

    private static void strand(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 q, int light) {
        Vec3 d = q.subtract(p);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 u = Math.abs(d.normalize().y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = d.cross(u).normalize().scale(HALF), s2 = d.cross(s1).normalize().scale(HALF);
        ribbon(vc, m, p, q, s1, light);
        ribbon(vc, m, p, q, s2, light);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 q, Vec3 s, int light) {
        vc.addVertex(m, (float) (p.x - s.x), (float) (p.y - s.y), (float) (p.z - s.z)).setColor(CR, CG, CB, 1.0F);
        vc.addVertex(m, (float) (p.x + s.x), (float) (p.y + s.y), (float) (p.z + s.z)).setColor(CR, CG, CB, 1.0F);
        vc.addVertex(m, (float) (q.x - s.x), (float) (q.y - s.y), (float) (q.z - s.z)).setColor(CR, CG, CB, 1.0F);
        vc.addVertex(m, (float) (q.x + s.x), (float) (q.y + s.y), (float) (q.z + s.z)).setColor(CR, CG, CB, 1.0F);
    }

    @Override
    public AABB getRenderBoundingBox(ArrestingWireBlockEntity be) {
        AABB box = look.getRenderBoundingBox(be);
        if (!be.master()) return box;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        return box.expandTowards(f.getStepX() * be.span(), 1.0, f.getStepZ() * be.span()).inflate(1.0);
    }

    @Override public boolean shouldRenderOffScreen(ArrestingWireBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Look extends StagedUnitRenderer<ArrestingWireBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing");
        private BlockState skin;

        Look() { super("arresting_wire", 1, 1); }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(ArrestingWireBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(ArrestingWireBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null || !poses.containsKey("housing")) return;
            CopycatSkin.draw(MeshModel.named("arresting_wire"), "housing", poses.get("housing"), normals.get("housing"), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }
}
