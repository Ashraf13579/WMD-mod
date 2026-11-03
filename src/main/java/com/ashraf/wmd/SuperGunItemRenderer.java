package com.ashraf.wmd;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public class SuperGunItemRenderer extends BlockEntityWithoutLevelRenderer {

    public SuperGunItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof SuperGunItem it)) return;
        MeshModel mesh = MeshModel.named(it.isCharge() ? SuperGun.CHARGE : SuperGun.SHELL);
        if (!mesh.has("part")) return;
        Minecraft mc = Minecraft.getInstance();
        float len = (it.isCharge() ? SuperGun.CHARGE_PX : SuperGun.SHELL_PX) / 16.0F;
        float k = 0.9F / len;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(k, k, k);
        poseStack.translate(0.0F, -len * 0.5F, 0.0F);
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

    private static SuperGunItemRenderer instance;

    @SubscribeEvent
    static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (instance == null) instance = new SuperGunItemRenderer();
                return instance;
            }
        }, Wmd.SHELL_SUPER_HE.get(), Wmd.SUPER_CHARGE.get());
    }
}
