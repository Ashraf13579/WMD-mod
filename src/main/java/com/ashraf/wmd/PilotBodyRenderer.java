package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class PilotBodyRenderer extends EntityRenderer<PilotBodyEntity> {

    private final PlayerModel<Player> wide, slim;

    public PilotBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        shadowRadius = 0.5F;
    }

    private static PlayerSkin skin(PilotBodyEntity e) {
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null || e.owner() == null ? null : conn.getPlayerInfo(e.owner());
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(e.owner() == null ? e.getUUID() : e.owner());
    }

    @Override
    public void render(PilotBodyEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        PlayerSkin s = skin(e);
        PlayerModel<Player> m = s.model() == PlayerSkin.Model.SLIM ? slim : wide;
        m.young = false;
        m.crouching = false;
        m.attackTime = 0.0F;
        m.head.xRot = 0.45F;
        m.hat.copyFrom(m.head);
        m.rightArm.xRot = -0.7F; m.leftArm.xRot = -0.7F;
        m.rightArm.yRot = -0.2F; m.leftArm.yRot = 0.2F;
        m.rightSleeve.copyFrom(m.rightArm); m.leftSleeve.copyFrom(m.leftArm);
        m.rightLeg.xRot = 0.0F; m.leftLeg.xRot = 0.0F;
        m.rightPants.copyFrom(m.rightLeg); m.leftPants.copyFrom(m.leftLeg);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - e.getYRot()));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        m.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(s.texture())), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PilotBodyEntity e) { return skin(e).texture(); }
}
