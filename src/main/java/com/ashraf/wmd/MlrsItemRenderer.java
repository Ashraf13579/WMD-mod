package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public class MlrsItemRenderer extends BlockEntityWithoutLevelRenderer {

    static final String[] IDS = { "mlrs_pod", "mlrs_pod_guided" };

    private static final float HALF_H = 10.1F, LEN = 88.2F;

    public MlrsItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof MlrsPodItem it)) return;
        MeshModel mesh = MeshModel.named(it.type() == Artillery.GUIDED ? IDS[1] : IDS[0]);
        if (!mesh.has("part")) return;
        Minecraft mc = Minecraft.getInstance();
        float k = 0.9F / (LEN / 16.0F);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(k, k, k);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.translate(0.0F, -HALF_H / 16.0F, 0.0F);
        if (ctx == ItemDisplayContext.GUI) {
            Lighting.setupForFlatItems();
            MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource bs ? bs : mc.renderBuffers().bufferSource();
            mesh.draw("part", poseStack.last(), src, packedLight, packedOverlay);
            src.endBatch();
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
        } else {
            mesh.draw("part", poseStack.last(), buffers, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private static MlrsItemRenderer instance;

    @SubscribeEvent
    static void register(RegisterClientExtensionsEvent event) {
        net.minecraft.world.item.Item[] items = new net.minecraft.world.item.Item[IDS.length];
        for (int j = 0; j < IDS.length; j++) items[j] = ItemProcessBlockEntity.wmd(IDS[j]);
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (instance == null) instance = new MlrsItemRenderer();
                return instance;
            }
        }, items);
    }

    static String[] cleanIds(String[] ids) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.startsWith("mlrs_")) continue;
            if (ItemProcessBlockEntity.wmd(id) == Items.AIR) continue;
            out.add(id);
        }
        List<String> l = new ArrayList<>(out);
        return l.toArray(new String[0]);
    }
}
