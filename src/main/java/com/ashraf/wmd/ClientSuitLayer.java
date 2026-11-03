package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final float NECK = 24.0F / 16.0F;

    public ClientSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float limbAmount, float partial, float age, float yaw, float pitch) {
        boolean helmet = p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof EvaSuitItem, torso = p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof EvaSuitItem;
        if (!helmet && !torso) return;
        MeshModel m = MeshModel.named("space_suit");
        if (helmet) {
            ps.pushPose();
            getParentModel().head.translateAndRotate(ps);
            place(ps);
            m.draw("helmet", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if (torso) {
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            place(ps);
            m.draw("pack", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    private static void place(PoseStack ps) {
        ps.translate(0.0F, NECK, 0.0F);
        ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
    }

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Reg {
        @SubscribeEvent
        @SuppressWarnings("unchecked")
        static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer r)
                    ((LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) r).addLayer(new ClientSuitLayer(r));
            }
        }
    }
}
