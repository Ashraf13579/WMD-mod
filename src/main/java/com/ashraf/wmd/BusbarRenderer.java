package com.ashraf.wmd;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

final class BusbarRenderer implements BlockEntityRenderer<BusbarBlockEntity> {

    private final HullPenetratorRenderer skin = new HullPenetratorRenderer();

    private static final ModelResourceLocation CORE = part("busbar_core"), CORE_LIT = part("busbar_core_lit"), ARM = part("busbar_arm"),
            PLUG = part("busbar_plug"), PLUG_UP = part("busbar_plug_up"), PLUG_DOWN = part("busbar_plug_down"), ARM_DOWN = part("busbar_arm_down"),
            ARM_UP = part("busbar_arm_up");

    private static final ModelResourceLocation[] MODELS = { CORE, ARM, PLUG, ARM_UP, ARM_DOWN, PLUG_UP, PLUG_DOWN, CORE };

    private static ModelResourceLocation part(String n) { return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "block/" + n)); }

    @EventBusSubscriber(modid = Wmd.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static final class Models {
        private Models() {}
        @SubscribeEvent
        static void register(ModelEvent.RegisterAdditional event) {
            for (ModelResourceLocation m : new ModelResourceLocation[] { CORE, CORE_LIT, ARM, PLUG, PLUG_UP, PLUG_DOWN, ARM_DOWN, ARM_UP }) event.register(m);
        }
    }

    @Override
    public void render(BusbarBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.getBlockState();
        Level level = be.getLevel();
        if (level != null && st.hasProperty(BusbarBlock.WALLRUN) && st.getValue(BusbarBlock.WALLRUN)) wallRun(level, be.getBlockPos(), st, ps, buffers, light, overlay);
        skin.render(be, partialTick, ps, buffers, light, overlay);
    }

    private void wallRun(Level level, BlockPos pos, BlockState st, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        List<BusbarBlock.Part> parts = BusbarBlock.parts(level, pos, st);
        if (parts == null) return;
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        boolean lit = st.getValue(BusbarBlock.LIT);
        for (BusbarBlock.Part p : parts) {
            ModelResourceLocation m = lit && (p.model() == BusbarBlock.P_CORE || p.model() == BusbarBlock.P_CORE_SHORT) ? CORE_LIT : MODELS[p.model()];
            ps.pushPose();
            ps.mulPose(BusbarBlock.partMatrix(p));
            render(vc, ps, m, st, light, overlay);
            ps.popPose();
        }
    }

    private static void render(VertexConsumer vc, PoseStack ps, ModelResourceLocation m, BlockState st, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(m);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), vc, st, model, 1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, Sheets.cutoutBlockSheet());
    }

    @Override public AABB getRenderBoundingBox(BusbarBlockEntity be) { return new AABB(be.getBlockPos()).inflate(0.1); }
}
