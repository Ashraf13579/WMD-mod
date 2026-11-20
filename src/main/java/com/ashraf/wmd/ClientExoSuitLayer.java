package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientExoSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    static final String MESH = "exo_suit";
    private static final float NECK = 24.0F / 16.0F, PX = 1.0F / 16.0F;
    static final float JET_SIGN = 1.0F, CORE_SIGN = 1.0F;

    private static final float[] CORE = { 0.0F, 18.8F, 7.1F }, JET_L = { 3.3F, 14.2F, 6.0F }, JET_R = { -3.3F, 14.2F, 6.0F };
    private static final float NOZZLE_Y = 10.75F, FLAME_LEN = 7.0F;

    public ClientExoSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float limbAmount, float partial, float age, float yaw, float pitch) {
        if (p.isInvisible() || !ExoSuitItem.anyPiece(p)) return;
        MeshModel m = MeshModel.named(MESH);
        if (!m.has("torso")) return;
        boolean head = ExoSuitItem.wears(p, EquipmentSlot.HEAD), chest = ExoSuitItem.wears(p, EquipmentSlot.CHEST),
                legs = ExoSuitItem.wears(p, EquipmentSlot.LEGS), feet = ExoSuitItem.wears(p, EquipmentSlot.FEET);
        ClientExoSuit.Anim an = ClientExoSuit.anim(p);
        ClientExoMods.MAnim ma = ClientExoMods.anim(p);
        float charge = ExoSuitItem.charge(p);
        float bright = brightness(p, charge, chest);
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        int ov = OverlayTexture.NO_OVERLAY;
        if (head) {
            begin(ps, pm.head);
            m.draw("helmet", ps.last(), buffers, light, ov);
            glow(m, "visor", ps, buffers, light, bright);
            glow(m, "lt_helmet", ps, buffers, light, bright);
            if (ExoModule.THERMAL.fitted(p)) {
                float th = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pThermal, ma.thermal, partial);
                ps.pushPose();
                turn(ps, THERM_HINGE, Axis.XP, 105.0F * th);
                m.draw("m_thermal", ps.last(), buffers, light, ov);
                glow(m, "m_thermalglow", ps, buffers, light, bright * (ma != null && ma.thermalOn ? 1.0F : 0.25F));
                ps.popPose();
            }
            if (ExoModule.REBREATHER.fitted(p)) {
                m.draw("m_rebreather", ps.last(), buffers, light, ov);
                glow(m, "m_lamp", ps, buffers, light, p.isUnderWater() ? bright : bright * 0.3F);
            }
            ps.popPose();
        }
        if (chest || legs) {
            begin(ps, pm.body);
            if (legs) {
                m.draw("hips", ps.last(), buffers, light, ov);
                if (ExoModule.BARRIER.fitted(p)) { m.draw("m_barrier_h", ps.last(), buffers, light, ov); glow(m, "m_barrierglow_h", ps, buffers, light, barrierGlow(ma, bright)); }
            }
            if (chest) {
                m.draw("torso", ps.last(), buffers, light, ov);
                glow(m, "lt_torso", ps, buffers, light, bright);
                for (int k = 0; k < 4; k++) if (charge > k * 0.25F + (k == 0 ? 0.0F : 0.001F)) glow(m, "gauge" + k, ps, buffers, light, Math.max(bright, 0.35F));
                float core = an == null ? 0.0F : an.core(partial);
                ps.pushPose();
                turn(ps, CORE, Axis.ZP, CORE_SIGN * core);
                m.draw("core", ps.last(), buffers, light, ov);
                ps.popPose();
                glow(m, "coreglow", ps, buffers, light, bright);
                float jet = an == null ? 0.0F : an.jet(partial);
                float heat = an == null ? 0.0F : an.heat;
                float flame = an == null ? 0.0F : an.flame(partial);
                for (int s = 0; s < 2; s++) {
                    String side = s == 0 ? "l" : "r";
                    ps.pushPose();
                    turn(ps, s == 0 ? JET_L : JET_R, Axis.XP, JET_SIGN * jet);
                    m.draw("jet_" + side, ps.last(), buffers, light, ov);
                    if (heat > 0.05F) m.drawColoured("ltjet_" + side, ps.last(), buffers, light, ov, 255, (int) (90 + 120 * heat), (int) (40 + 60 * heat));
                    if (flame > 0.01F) flame(ps, buffers, (s == 0 ? JET_L : JET_R)[0], (s == 0 ? JET_L : JET_R)[2], flame, p.tickCount + s * 7);
                    ps.popPose();
                }
                if (ExoModule.WINGS.fitted(p)) wings(m, ps, buffers, light, p, ma, partial, bright);
                if (ExoModule.CLOAK.fitted(p)) {
                    m.draw("m_cloak", ps.last(), buffers, light, ov);
                    boolean on = p == net.minecraft.client.Minecraft.getInstance().player && ClientExoMods.cloaked();
                    glow(m, "m_cloakglow", ps, buffers, light, bright * (on ? 0.6F + 0.4F * Mth.sin(p.tickCount * 0.4F) : 0.3F));
                }
            }
            ps.popPose();
        }
        if (chest) {
            float sh = (an == null ? 0.0F : an.shoulder(partial)) * Balance.EXO_SHOULDER_PX * PX;
            for (int s = 0; s < 2; s++) {
                String side = s == 0 ? "l" : "r";
                begin(ps, s == 0 ? pm.rightArm : pm.leftArm);
                m.draw("arm_" + side, ps.last(), buffers, light, ov);
                glow(m, "ltarm_" + side, ps, buffers, light, bright);
                ps.pushPose();
                ps.translate(0.0F, sh, 0.0F);
                m.draw("shoulder_" + side, ps.last(), buffers, light, ov);
                glow(m, "ltsh_" + side, ps, buffers, light, bright);
                if (s == 1 && ExoModule.MISSILES.fitted(p)) pod(m, ps, buffers, light, p, ma, partial, bright);
                if (s == 0 && ExoModule.RF.fitted(p)) {
                    m.draw("m_rf", ps.last(), buffers, light, ov);
                    float rf = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pRf, ma.rf, partial);
                    ps.pushPose();
                    turn(ps, RF_PIVOT, Axis.XP, 90.0F * rf);
                    m.draw("m_rfmast", ps.last(), buffers, light, ov);
                    glow(m, "m_rfglow", ps, buffers, light, bright * (ma != null && ma.rfOn ? 0.7F + 0.3F * Mth.sin(p.tickCount * 0.5F) : 0.25F));
                    ps.popPose();
                }
                ps.popPose();
                if (s == 0 && ExoModule.CANNON.fitted(p)) cannon(m, ps, buffers, light, ma, partial, bright);
                ps.popPose();
            }
        }
        if (legs || feet) {
            float shin = (an == null ? 0.0F : an.shin(partial)) * Balance.EXO_SHIN_PX * PX;
            for (int s = 0; s < 2; s++) {
                String side = s == 0 ? "l" : "r";
                begin(ps, s == 0 ? pm.rightLeg : pm.leftLeg);
                if (legs) {
                    m.draw("leg_" + side, ps.last(), buffers, light, ov);
                    if (ExoModule.BARRIER.fitted(p)) { m.draw("m_barrier_" + side, ps.last(), buffers, light, ov); glow(m, "m_barrierglow_" + side, ps, buffers, light, barrierGlow(ma, bright)); }
                    ps.pushPose();
                    ps.translate(0.0F, shin, 0.0F);
                    m.draw("shin_" + side, ps.last(), buffers, light, ov);
                    ps.popPose();
                }
                if (feet) {
                    m.draw("boot_" + side, ps.last(), buffers, light, ov);
                    glow(m, "ltboot_" + side, ps, buffers, light, bright);
                    if (ExoModule.MAGGRIP.fitted(p)) {
                        float mg = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pMag, ma.mag, partial);
                        ps.pushPose();
                        turn(ps, new float[] { s == 0 ? 4.7F : -4.7F, 0.0F, 0.0F }, Axis.ZP, (s == 0 ? -25.0F : 25.0F) * mg);
                        m.draw("m_mag_" + side, ps.last(), buffers, light, ov);
                        glow(m, "m_magglow_" + side, ps, buffers, light, bright * (0.35F + 0.65F * mg));
                        ps.popPose();
                    }
                }
                ps.popPose();
            }
        }
    }

    private static final float[] THERM_HINGE = { 0.0F, 31.7F, -5.0F }, RF_PIVOT = { 7.0F, 25.9F, 2.0F }, POD = { -7.2F, 26.0F, 2.8F }, LID = { -7.2F, 28.4F, -2.6F };

    private static float barrierGlow(ClientExoMods.MAnim ma, float bright) {
        return ma != null && ma.barrierT < Balance.EXO_BARRIER_FLASH ? 1.0F : bright * 0.45F;
    }

    private static void cannon(MeshModel m, PoseStack ps, MultiBufferSource buffers, int light, ClientExoMods.MAnim ma, float pt, float bright) {
        float orb = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pOrb, ma.orb, pt), barrel = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pBarrel, ma.barrel, pt);
        float recoil = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pRecoil, ma.recoil, pt);
        float cool = ma == null ? 0.0F : Math.max(0.0F, 1.0F - ma.fireT / (float) Balance.EXO_CANNON_COOL);
        m.draw("m_cannon", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        glow(m, "m_cannonglow", ps, buffers, light, Math.max(bright * 0.2F, Math.max(orb, cool * 0.7F)) * (bright > 0 ? 1.0F : 0.0F));
        ps.pushPose();
        ps.translate(0.0F, (-2.4F * barrel + 0.6F * recoil) * PX, 0.0F);
        m.draw("m_barrel", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        glow(m, "m_barrelglow", ps, buffers, light, Math.max(bright * 0.2F, orb));
        ps.popPose();
    }

    private static void pod(MeshModel m, PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, ClientExoMods.MAnim ma, float pt, float bright) {
        float pod = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pPod, ma.pod, pt), lid = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pLid, ma.lid, pt);
        ps.pushPose();
        turn(ps, POD, Axis.XP, -18.0F * pod);
        m.draw("m_pod", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        if (ExoSuitItem.chest(p).getOrDefault(ExoSuitBlocks.EXO_AMMO.get(), 0) > 0) m.draw("m_rockets", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        turn(ps, LID, Axis.XP, -100.0F * lid);
        m.draw("m_podlid", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static void wings(MeshModel m, PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, ClientExoMods.MAnim ma, float pt, float bright) {
        m.draw("m_wingroot", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        float w = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pWings, ma.wings, pt), pod = ma == null ? 0.0F : ClientExoMods.MAnim.l(ma.pWpod, ma.wpod, pt);
        boolean burn = p.isFallFlying() || (ma != null && ma.hover);
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "l" : "r";
            float sg = s == 0 ? 1.0F : -1.0F;
            ps.pushPose();
            turn(ps, new float[] { sg * 4.85F, 22.6F, 4.7F }, Axis.ZP, sg * 100.0F * w);
            m.draw("m_wing_" + side, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            glow(m, "m_wingglow_" + side, ps, buffers, light, bright);
            turn(ps, new float[] { sg * 4.85F, 11.6F, 5.6F }, Axis.YP, sg * 90.0F * pod);
            m.draw("m_wpod_" + side, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            if (burn && w > 0.8F) {
                m.drawColoured("m_wpodglow_" + side, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, 255, 200, 140);
                if (bright > 0.0F) podFlame(ps, buffers, sg * 4.85F, 11.6F, 7.3F, (p.isFallFlying() ? 1.0F : 0.6F) * (0.85F + 0.15F * Mth.sin(p.tickCount * 1.3F + s)));
            }
            ps.popPose();
        }
    }

    private static void podFlame(PoseStack ps, MultiBufferSource buffers, float x, float y, float z0, float k) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = ps.last();
        for (int layer = 0; layer < 2; layer++) {
            float r = (layer == 0 ? 0.95F : 0.45F) * PX, len = (layer == 0 ? 7.0F : 4.0F) * k * PX;
            int cr = layer == 0 ? 40 : 230, cg = layer == 0 ? 120 : 245, cb = 255, a = layer == 0 ? 150 : 210;
            float cx = x * PX, cy = y * PX, zz = z0 * PX;
            for (int i = 0; i < 8; i++) {
                float a0 = Mth.TWO_PI * i / 8, a1 = Mth.TWO_PI * (i + 1) / 8;
                vc.addVertex(pose, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, zz).setColor(cr, cg, cb, a);
                vc.addVertex(pose, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, zz).setColor(cr, cg, cb, a);
                vc.addVertex(pose, cx, cy, zz + len).setColor(cr, cg, cb, 0);
                vc.addVertex(pose, cx, cy, zz + len).setColor(cr, cg, cb, 0);
            }
        }
    }

    static float brightness(LivingEntity p, float charge, boolean chest) {
        if (!chest || charge <= 0.0F) return 0.0F;
        if (charge >= Balance.EXO_LOW) return 1.0F;
        return (p.tickCount / 2) % 7 == 0 ? 0.12F : 0.35F;
    }

    private static void glow(MeshModel m, String bone, PoseStack ps, MultiBufferSource buffers, int light, float b) {
        if (b <= 0.0F) return;
        int c = Mth.clamp((int) (255 * b), 0, 255);
        m.drawColoured(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, c, c, c);
    }

    private static void begin(PoseStack ps, ModelPart part) {
        ps.pushPose();
        part.translateAndRotate(ps);
        PartPose rest = part.getInitialPose();
        ps.translate(-rest.x * PX, -rest.y * PX, -rest.z * PX);
        ps.translate(0.0F, NECK, 0.0F);
        ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
    }

    private static void turn(PoseStack ps, float[] pv, Axis axis, float deg) {
        ps.translate(pv[0] * PX, pv[1] * PX, pv[2] * PX);
        ps.mulPose(axis.rotationDegrees(deg));
        ps.translate(-pv[0] * PX, -pv[1] * PX, -pv[2] * PX);
    }

    private static void flame(PoseStack ps, MultiBufferSource buffers, float x, float z, float k, int seed) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        float wob = 0.85F + 0.15F * Mth.sin(seed * 1.7F);
        cone(vc, ps, x, z, 1.15F, FLAME_LEN * k * wob, 40, 120, 255, 150);
        cone(vc, ps, x, z, 0.55F, FLAME_LEN * 0.55F * k * wob, 230, 245, 255, 210);
    }

    private static void cone(VertexConsumer vc, PoseStack ps, float x, float z, float r, float len, int cr, int cg, int cb, int a) {
        PoseStack.Pose pose = ps.last();
        float y0 = NOZZLE_Y * PX, y1 = (NOZZLE_Y - len) * PX, cx = x * PX, cz = z * PX, rr = r * PX;
        int n = 8;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float x0 = cx + Mth.cos(a0) * rr, z0 = cz + Mth.sin(a0) * rr, x1 = cx + Mth.cos(a1) * rr, z1 = cz + Mth.sin(a1) * rr;
            vc.addVertex(pose, x0, y0, z0).setColor(cr, cg, cb, a);
            vc.addVertex(pose, x1, y0, z1).setColor(cr, cg, cb, a);
            vc.addVertex(pose, cx, y1, cz).setColor(cr, cg, cb, 0);
            vc.addVertex(pose, cx, y1, cz).setColor(cr, cg, cb, 0);
        }
    }

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Reg {
        @SubscribeEvent
        @SuppressWarnings("unchecked")
        static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer r)
                    ((LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) r).addLayer(new ClientExoSuitLayer(r));
            }
        }
    }
}
