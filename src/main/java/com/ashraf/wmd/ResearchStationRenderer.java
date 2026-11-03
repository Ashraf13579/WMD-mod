package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;

public class ResearchStationRenderer extends RefineryUnitRenderer<ResearchStationBlockEntity> {

    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/research_station.geo.json");

    public ResearchStationRenderer() { super("research_station", 1); }

    @Override
    public void render(ResearchStationBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!GeckoLibCache.getBakedModels().containsKey(GEO)) return;
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }
}
