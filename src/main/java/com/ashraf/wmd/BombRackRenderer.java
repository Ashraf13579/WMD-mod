package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;

public class BombRackRenderer extends StagedUnitRenderer<BombRackBlockEntity> {

    private final ResourceLocation geo;

    public BombRackRenderer(String name, int up, int reach) {
        super(name, up, reach);
        this.geo = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/" + name + ".geo.json");
    }

    @Override
    public void render(BombRackBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, pt, ps, buffers, light, overlay);
        if (!GeckoLibCache.getBakedModels().containsKey(geo)) return;
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        ps.pushPose();
        ps.translate(0.5F, 0.0F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - f.toYRot()));
        for (int i = 0; i < be.slots(); i++) {
            BombType t = be.kind(i);
            int c = be.count(i);
            if (t == null || c <= 0) continue;
            MeshModel mesh = MeshModel.named(t.mesh);
            if (!mesh.has("part")) continue;
            int n = be.heavy && !t.heavy ? Math.min(c, BombRackBlockEntity.BAY_TAIL_Z.length) : 1;
            for (int j = 0; j < n; j++) {
                float x = be.heavy ? (t.heavy ? BombRackBlockEntity.HEAVY_LANE_X[i] : BombRackBlockEntity.BAY_ROW_X[i]) : BombRackBlockEntity.RACK_X;
                float tail = be.heavy ? (t.heavy ? BombRackBlockEntity.BAY_MID_Z + t.length / 2.0F : BombRackBlockEntity.BAY_TAIL_Z[j]) : BombRackBlockEntity.RACK_TAIL_Z;
                ps.pushPose();
                ps.translate(x / 16.0F, (BombRackBlockEntity.BEAM_Y + t.radius) / 16.0F, tail / 16.0F);
                ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                BombRenderer.drawWhole(mesh, ps.last(), buffers, light);
                if (t == BombType.GLIDE) { mesh.draw("wing_l", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY); mesh.draw("wing_r", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY); }
                ps.popPose();
            }
        }
        ps.popPose();
    }
}
