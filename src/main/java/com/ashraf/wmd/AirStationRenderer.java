package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AirStationRenderer extends CopycatUnitRenderer<AirStationBlockEntity> {

    private static final Set<String> ATTACH = Set.of("housing", "store");
    private final float[] store;

    public AirStationRenderer(String name, int up, boolean rail) {
        super(name, up, -1, null);
        store = rail ? new float[] { 0.0F, 11.0F, 8.0F } : new float[] { 0.0F, 7.4F, 0.0F };
    }

    @Override protected Set<String> attachBones() { return ATTACH; }

    @Override
    public void render(AirStationBlockEntity be, float partialTick, PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        float gap = gap(be);
        poseStack.pushPose();
        if (gap > 0.0F) poseStack.translate(0.0F, gap, 0.0F);
        super.render(be, partialTick, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }

    static float gap(AirStationBlockEntity be) {
        Level lv = be.getLevel();
        if (lv == null) return 0.0F;
        Direction m = Machines.mount(be.getBlockState());
        BlockPos n = be.getBlockPos().relative(m);
        BlockState st = lv.getBlockState(n);
        if (st.isAir()) return 0.0F;
        VoxelShape sh = st.getCollisionShape(lv, n);
        if (sh.isEmpty()) return 0.0F;
        double g = m.getAxisDirection() == Direction.AxisDirection.POSITIVE ? sh.min(m.getAxis()) : 1.0 - sh.max(m.getAxis());
        return (float) Math.max(0.0, Math.min(15.0 / 16.0, g));
    }

    @Override
    protected void drawAttached(AirStationBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        super.drawAttached(be, poses, normals, buffers, light, overlay);
        Matrix4f m = poses.get("store");
        if (m == null || be.getLevel() == null) return;
        int kind = be.loadedShown();
        float drop = 0.0F, slide = 0.0F;
        if (kind < 0) {
            double since = be.getLevel().getGameTime() - be.fireAt();
            if (be.rail() && since >= 0 && since < Balance.STATION_SLIDE_TICKS) { kind = be.firedKind(); float t = (float) since / Balance.STATION_SLIDE_TICKS; slide = AamMeshes.lengthPx(AamItem.mesh(be.firedKind())) * t * t; }
            else if (!be.rail() && since >= 0 && since < Balance.STATION_DROP_TICKS) { kind = be.firedKind(); float t = (float) since / Balance.STATION_DROP_TICKS; drop = 9.0F * t * t; }
            else return;
        }
        if (AirStores.isDrop(kind)) {
            PoseStack dp = new PoseStack();
            dp.last().pose().set(m);
            dp.last().normal().set(normals.get("store"));
            dp.translate(store[0] / 16.0F, (store[1] - drop) / 16.0F, store[2] / 16.0F);
            AirStoreMeshes.draw(kind, dp, buffers, light);
            return;
        }
        String mesh = kind < 0 ? null : AamItem.mesh(kind);
        if (mesh == null) return;
        MeshModel mm = MeshModel.named(mesh);
        if (!mm.has("part")) return;
        int[] t = MissileParts.tint(AamItem.paint(kind));

        int n = be.countShown();
        float dx = Balance.RAIL_TWIN_DX_PX;
        if (be.rail() && n >= 2) {
            twin(mm, m, normals, buffers, light, t, -dx, 0.0F, mesh, drop);
            twin(mm, m, normals, buffers, light, t, dx, 0.0F, mesh, drop);
            return;
        }
        if (be.rail() && n == 1 && be.firedSlot() == 1) {
            twin(mm, m, normals, buffers, light, t, -dx, 0.0F, mesh, drop);
            double since = be.getLevel().getGameTime() - be.fireAt();
            if (since >= 0 && since < Balance.STATION_SLIDE_TICKS) {
                float k = (float) since / Balance.STATION_SLIDE_TICKS;
                twin(mm, m, normals, buffers, light, t, dx, AamMeshes.lengthPx(mesh) * k * k, mesh, drop);
            }
            return;
        }
        if (be.rail() && n == 0 && be.firedSlot() == 1) { twin(mm, m, normals, buffers, light, t, -dx, slide, mesh, drop); return; }
        twin(mm, m, normals, buffers, light, t, 0.0F, slide, mesh, drop);
    }

    private void twin(MeshModel mm, Matrix4f m, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int[] t, float x, float slide, String mesh, float drop) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(m);
        ps.last().normal().set(normals.get("store"));
        ps.translate((store[0] + x) / 16.0F, (store[1] - AamMeshes.hangPx(mesh) - drop) / 16.0F, (store[2] - slide) / 16.0F);
        mm.drawTinted("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
    }
}
