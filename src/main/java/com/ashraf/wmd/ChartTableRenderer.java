package com.ashraf.wmd;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

final class ChartTableRenderer implements BlockEntityRenderer<ChartTableBlockEntity> {

    private final GunnerRenderers.Look<ChartTableBlockEntity> look = new GunnerRenderers.Look<>("chart_table", 1, 2, Set.of("housing"));

    @Override
    public void render(ChartTableBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        look.render(be, pt, ps, buf, light, overlay);
        if (!be.lit() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        CcRenderers.modelFrame(ps, f);
        ps.translate(0.0F, ChartTableBlockEntity.GY, 0.0F);
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        LiveScreenRenderer.Rect r = new LiveScreenRenderer.Rect(ChartTableBlockEntity.GX0, ChartTableBlockEntity.GX1, ChartTableBlockEntity.GZ0, ChartTableBlockEntity.GZ1, 0.0F);
        ChartGlass.paint(new LiveScreenRenderer.WorldCanvas(ps, buf, r, -0.05F), be.glass(), Balance.CHART_GLASS_W, Balance.CHART_GLASS_D, be.pageX0(), be.pageZ0(),
                Balance.CHART_PAGE_W, Balance.CHART_PAGE_D, be.shipX(), be.shipZ(), be.heading(), be.route(), be.track(), be.night(), be.letGo(),
                be.getLevel().getGameTime() + pt);
        ps.popPose();
    }

    @Override public AABB getRenderBoundingBox(ChartTableBlockEntity be) { return look.getRenderBoundingBox(be).inflate(0.5); }
    @Override public boolean shouldRenderOffScreen(ChartTableBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 64; }
}
