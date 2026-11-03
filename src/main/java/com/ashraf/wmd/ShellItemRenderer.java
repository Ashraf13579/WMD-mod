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
public class ShellItemRenderer extends BlockEntityWithoutLevelRenderer {

    static final float TRUE_SCALE = 1.0F;

    static float lengthPx(int gun) { return gun == Artillery.SUPER_GUN ? 52.0F : gun == Artillery.HOWITZER ? 17.0F : gun == Artillery.MORTAR ? 12.0F : 5.25F; }

    public ShellItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof ArtShellItem it)) return;
        String name = it.isPropellant() ? "propellant_charge" : Artillery.shellId(it.gun(), it.type());
        MeshModel mesh = MeshModel.named(name);
        if (!mesh.has("part")) return;
        Minecraft mc = Minecraft.getInstance();
        float len = lengthPx(it.gun()) / 16.0F;
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

    private static ShellItemRenderer instance;

    @SubscribeEvent
    static void register(RegisterClientExtensionsEvent event) {
        String[] ids = new String[Artillery.GUNS.length * Artillery.TYPES.length + 1];
        int i = 0;
        for (int g = 0; g < Artillery.GUNS.length; g++) for (int t = 0; t < Artillery.TYPES.length; t++) ids[i++] = Artillery.shellId(g, t);
        ids[i] = "propellant_charge";
        java.util.List<String> more = new java.util.ArrayList<>(java.util.List.of(ids));
        for (int t = 0; t < Artillery.SPECIAL.length; t++) more.add(Artillery.shellId(Artillery.HOWITZER, Artillery.SPECIAL_BASE + t));
        more.add(Artillery.shellId(Artillery.SUPER_GUN, Artillery.SUPER_NUKE));
        ids = MlrsItemRenderer.cleanIds(more.toArray(new String[0]));
        net.minecraft.world.item.Item[] items = new net.minecraft.world.item.Item[ids.length];
        for (int j = 0; j < ids.length; j++) items[j] = ItemProcessBlockEntity.wmd(ids[j]);
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (instance == null) instance = new ShellItemRenderer();
                return instance;
            }
        }, items);
    }
}
