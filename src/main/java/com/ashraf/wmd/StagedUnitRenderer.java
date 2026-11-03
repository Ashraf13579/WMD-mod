package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.GeckoLibCache;

public class StagedUnitRenderer<T extends ProcessUnitBlockEntity> extends RefineryUnitRenderer<T> {

    private final ResourceLocation geo;
    private final int up, reach;

    public StagedUnitRenderer(String name, int up) { this(name, up, -1); }

    public StagedUnitRenderer(String name, int up, int reach) {
        super(name, up);
        this.geo = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/" + name + ".geo.json");
        this.up = up;
        this.reach = reach;
    }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!GeckoLibCache.getBakedModels().containsKey(geo)) return;
        poseStack.pushPose();

        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        if (Machines.mount(be.getBlockState()) != net.minecraft.core.Direction.UP) return MountRender.box(be.getBlockPos(), Math.max(up, reach) + 1);
        if (reach < 0) return super.getRenderBoundingBox(be);
        return new AABB(be.getBlockPos()).inflate(reach, 0, reach).expandTowards(0, up + 1, 0).expandTowards(0, -1, 0);
    }
}
