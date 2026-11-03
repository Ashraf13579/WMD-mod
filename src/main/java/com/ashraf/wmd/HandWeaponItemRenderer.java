package com.ashraf.wmd;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public class HandWeaponItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final float[][] PLACE = {
            { 0.0F, 0.0F, 0.0F, 45.0F, 25.0F, 0.62F },
            { 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5F },
            { 0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 0.6F },
            { 0.0F, 0.18F, -0.15F, 0.0F, 0.0F, 1.0F },
            { 0.2F, -0.1F, -0.8F, 0.0F, 0.0F, 0.6F },
    };

    public HandWeaponItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof HandLauncherItem it)) return;
        MeshModel mesh = MeshModel.named(it.kind() == HandLock.MANPADS ? "manpads_launcher" : "javelin_launcher");
        if (!mesh.has("part")) return;
        Minecraft mc = Minecraft.getInstance();
        boolean aiming = mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem() == stack;
        float[] p = switch (ctx) {
            case GUI -> PLACE[0];
            case GROUND -> PLACE[1];
            case FIXED -> PLACE[2];
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> PLACE[3];
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> PLACE[4];
            default -> PLACE[1];
        };
        if (aiming && ctx.firstPerson()) return;
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ps.pushPose();
        ps.translate(0.5F + (left ? -p[0] : p[0]), 0.5F + p[1], 0.5F + p[2]);
        ps.mulPose(Axis.YP.rotationDegrees(p[3]));
        ps.mulPose(Axis.XP.rotationDegrees(p[4]));
        ps.scale(p[5], p[5], p[5]);
        if (ctx == ItemDisplayContext.GUI) {
            Lighting.setupForFlatItems();
            MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource bs ? bs : mc.renderBuffers().bufferSource();
            mesh.draw("part", ps.last(), src, light, overlay);
            src.endBatch();
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
        } else {
            mesh.draw("part", ps.last(), buffers, light, overlay);
        }
        ps.popPose();
    }

    private static HandWeaponItemRenderer instance;

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Reg {
        @SubscribeEvent
        static void register(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (instance == null) instance = new HandWeaponItemRenderer();
                    return instance;
                }

                @Override
                public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                    return HumanoidModel.ArmPose.CROSSBOW_HOLD;
                }
            }, Wmd.MANPADS_LAUNCHER.get(), Wmd.JAVELIN_LAUNCHER.get());
        }
    }

    @SubscribeEvent
    static void fov(ComputeFovModifierEvent event) {
        if (event.getPlayer().isUsingItem() && event.getPlayer().getUseItem().getItem() instanceof HandLauncherItem it)
            event.setNewFovModifier(event.getNewFovModifier() * (it.kind() == HandLock.MANPADS ? 0.75F : 0.5F));
    }
}
