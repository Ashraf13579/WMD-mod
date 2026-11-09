package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

public class LaidMineRenderer extends EntityRenderer<LaidMineEntity> {

    public LaidMineRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(LaidMineEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        int kind = e.kind();
        Direction f = Direction.fromYRot(e.getYRot());
        float t = e.tickCount + pt;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.11F) * 4.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.cos(t * 0.09F) * 3.0F));
        float k = e.startScale();
        if (k < 1.0F) {
            float g = Math.min(1.0F, t / LaidMineEntity.GROW_TICKS), sc = k + (1.0F - k) * g * g * (3.0F - 2.0F * g);
            ps.scale(sc, sc, sc);
        }
        drawLump(kind, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    static void drawLump(int kind, PoseStack ps, MultiBufferSource buffers, int light, int o) {
        ps.pushPose();
        if (kind == SeaMineBlockEntity.K_BOTTOM) {
            MeshModel m = MeshModel.named("mine_bottom");
            if (m.has("base")) m.draw("base", ps.last(), buffers, light, o);
            if (m.has("cap")) m.draw("cap", ps.last(), buffers, light, o);
        } else {
            MeshModel anchor = MeshModel.named("mine_anchor");
            if (anchor.has("base")) anchor.draw("base", ps.last(), buffers, light, o);
            ps.translate(0.0F, SeaMineBlockEntity.FAIRLEAD_PX / 16.0F, 0.0F);
            if (kind == SeaMineBlockEntity.K_CAPTOR) {
                MeshModel m = MeshModel.named("mine_captor");
                ps.translate(0.0F, 0.0F, -16.0F / 16.0F);
                if (m.has("base")) m.draw("base", ps.last(), buffers, light, o);
                if (m.has("cap")) m.draw("cap", ps.last(), buffers, light, o);
            } else {
                MeshModel m = MeshModel.named("mine_influence");
                ps.translate(-8.0F / 16.0F, 0.0F, -8.0F / 16.0F);
                if (m.has("base")) m.draw("base", ps.last(), buffers, light, o);
            }
        }
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LaidMineEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
