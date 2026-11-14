package com.ashraf.wmd;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

final class DeckConsoleRenderer implements BlockEntityRenderer<DeckConsoleBlockEntity> {

    static final float GLASS_X0 = -5.6F, GLASS_X1 = 5.6F, GLASS_Z0 = -6.4F, GLASS_Z1 = 1.4F, GLASS_Y = 19.7F;

    private final GunnerRenderers.Look<DeckConsoleBlockEntity> look = new GunnerRenderers.Look<>("deck_console", 2, 1, Set.of("housing"));

    @Override
    public void render(DeckConsoleBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        look.render(be, pt, ps, buf, light, overlay);
        if (!be.screenOn() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        CcRenderers.modelFrame(ps, f);
        ps.translate(0.0F, (float) DeckConsoleBlockEntity.PIN_Y, (float) DeckConsoleBlockEntity.PIN_Z);
        ps.mulPose(Axis.XP.rotationDegrees(be.lidDeg() * DeckConsoleBlockEntity.LID_SIGN));
        ps.translate(0.0F, -(float) DeckConsoleBlockEntity.PIN_Y, -(float) DeckConsoleBlockEntity.PIN_Z);
        ps.translate(0.0F, GLASS_Y, 0.0F);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        LiveScreenRenderer.Rect r = new LiveScreenRenderer.Rect(GLASS_X0, GLASS_X1, -GLASS_Z1, -GLASS_Z0, 0.0F);
        LiveScreenRenderer.WorldCanvas back = new LiveScreenRenderer.WorldCanvas(ps, buf, r, -0.05F);
        back.fill(0, 0, LiveScreenRenderer.WorldCanvas.BOX, back.height(), 0xF0081420);
        DeckConsolePicture.map(new LiveScreenRenderer.WorldCanvas(ps, buf, r, -0.09F), be.marks(), -1, 0L, -1, be.getLevel().getGameTime() + pt);
        ps.popPose();
    }

    @Override public AABB getRenderBoundingBox(DeckConsoleBlockEntity be) { return look.getRenderBoundingBox(be).inflate(0.5); }
    @Override public boolean shouldRenderOffScreen(DeckConsoleBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 96; }
}
