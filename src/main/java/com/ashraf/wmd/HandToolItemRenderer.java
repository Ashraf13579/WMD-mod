package com.ashraf.wmd;

import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public class HandToolItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final float PX = 1.0F / 16.0F;

    private static final float[][][] PLACE = {
            {
                    { 0.0F, 0.0F, 0.0F, 45.0F, 25.0F, 0.75F },
                    { 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6F },
                    { 0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 0.7F },
                    { 0.0F, -0.05F, -0.2F, 0.0F, 0.0F, 1.0F },
                    { 0.12F, -0.12F, -0.55F, 0.0F, 0.0F, 0.75F },
            }, {
                    { 0.0F, 0.0F, 0.0F, 45.0F, 25.0F, 1.1F },
                    { 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.8F },
                    { 0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 1.0F },
                    { 0.0F, 0.0F, -0.15F, 0.0F, 0.0F, 1.0F },
                    { 0.15F, -0.08F, -0.45F, 0.0F, 0.0F, 0.9F },
            } };

    private static final float[] TB_TRIGGER = { 0.0F, -0.3F, 2.0F }, TB_FAN = { 1.95F, 1.5F, 2.6F }, TB_SIGHT = { 0.0F, 3.3F, 2.6F };
    private static final float[] TB_RING_Z = { -3.9F, -4.9F, -5.9F };
    private static final float[] TB_PRONG_A = { 90.0F, 210.0F, 330.0F };
    private static final float TB_PRONG_R = 1.65F, TB_PRONG_Z = -7.85F, TB_AXIS_Y = 0.9F, TB_PRONG_OPEN = 24.0F;
    private static final float[] PC_TRIGGER = { 0.0F, -0.3F, 1.1F }, PC_FINS = { 0.0F, 1.0F, -2.0F }, PC_VALVE = { 0.0F, 3.45F, -1.3F }, PC_NEEDLE = { 0.95F, 3.45F, -0.55F };
    private static final float[] PC_GUIDE_L = { -1.05F, 1.0F, -3.4F }, PC_GUIDE_R = { 1.05F, 1.0F, -3.4F };
    private static final float[] PC_VENT_Y = { 0.55F, 1.15F, 1.75F };
    private static final float PC_VENT_X = -1.55F, PC_VENT_Z = 0.5F, PC_GUIDE_OPEN = 28.0F, PC_VENT_OPEN = 65.0F;

    private static HandToolItemRenderer instance;

    public HandToolItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        boolean tractor = stack.getItem() instanceof TractorBeamItem;
        if (!tractor && !(stack.getItem() instanceof PlasmaCutterItem)) return;
        MeshModel mesh = MeshModel.named(tractor ? "tractor_beam" : "plasma_cutter");
        if (!mesh.has(tractor ? "tb_body" : "pc_body")) return;
        Minecraft mc = Minecraft.getInstance();
        LivingEntity holder = ctx.firstPerson() ? mc.player : (ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) ? ClientHandTools.drawing : null;
        ClientHandTools.Anim an = holder instanceof Player pl ? ClientHandTools.anim(pl.getId()) : null;
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float[] p = PLACE[tractor ? 0 : 1][switch (ctx) {
            case GUI -> 0;
            case GROUND -> 1;
            case FIXED -> 2;
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> 3;
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> 4;
            default -> 1;
        }];
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ps.pushPose();
        ps.translate(0.5F + (left ? -p[0] : p[0]), 0.5F + p[1], 0.5F + p[2]);
        ps.mulPose(Axis.YP.rotationDegrees(p[3]));
        ps.mulPose(Axis.XP.rotationDegrees(p[4]));
        ps.scale(p[5], p[5], p[5]);
        if (ctx.firstPerson() && an != null) {
            float kick = an.l(an.kickO, an.kick, pt);
            ps.translate(0.0F, 0.0F, 0.06F * kick);
            if (!tractor && an.arcing) ps.translate(0.004F * (float) Math.sin(mc.player.tickCount * 2.7), 0.004F * (float) Math.cos(mc.player.tickCount * 3.1), 0.0F);
        }
        MultiBufferSource.BufferSource gui = null;
        if (ctx == ItemDisplayContext.GUI) {
            Lighting.setupForFlatItems();
            gui = buffers instanceof MultiBufferSource.BufferSource bs ? bs : mc.renderBuffers().bufferSource();
            buffers = gui;
        }
        if (tractor) tractor(mesh, ps, buffers, light, overlay, an, pt, stack);
        else cutter(mesh, ps, buffers, light, overlay, an, pt, stack);
        if (gui != null) {
            gui.endBatch();
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
        }
        ps.popPose();
    }

    static void drawRest(ItemStack stack, PoseStack ps, MultiBufferSource buf, int light, int ov) {
        boolean tractor = stack.getItem() instanceof TractorBeamItem;
        MeshModel mesh = MeshModel.named(tractor ? "tractor_beam" : "plasma_cutter");
        if (!mesh.has(tractor ? "tb_body" : "pc_body")) return;
        if (tractor) tractor(mesh, ps, buf, light, ov, null, 0.0F, stack);
        else cutter(mesh, ps, buf, light, ov, null, 0.0F, stack);
    }

    private static void tractor(MeshModel m, PoseStack ps, MultiBufferSource buf, int light, int ov, ClientHandTools.Anim an, float pt, ItemStack stack) {
        float prong = an == null ? 0.0F : an.l(an.prongO, an.prong, pt), sight = an == null ? 0.0F : an.l(an.sightO, an.sight, pt);
        float ring = an == null ? 0.0F : an.l(an.ringO, an.ring, pt), cap = an == null ? 0.0F : an.l(an.capO, an.cap, pt);
        float pulse = an == null ? 0.0F : an.l(an.pulseO, an.pulse, pt), fan = an == null ? 0.0F : an.l(an.fanO, an.fan, pt);
        float trig = an == null ? 0.0F : an.l(an.trigO, an.trig, pt);
        boolean on = an != null && an.beam > 0.05F;
        float charge = HandTech.fe(stack) / (float) HandTech.cap(stack);
        m.draw("tb_body", ps.last(), buf, light, ov);
        bone(m, ps, buf, light, ov, "trigger", TB_TRIGGER, Axis.XP, -18.0F * trig);
        bone(m, ps, buf, light, ov, "fan", TB_FAN, Axis.XP, fan);
        ps.pushPose();
        turn(ps, TB_SIGHT, Axis.XP, -80.0F * (1.0F - sight));
        m.draw("sight", ps.last(), buf, light, ov);
        glow(m, ps, buf, light, ov, "sightglow", sight > 0.9F);
        ps.popPose();
        for (int k = 0; k < 4; k++) glow(m, ps, buf, light, ov, "gauge" + k, charge > k * 0.25F + 0.02F);
        for (String s : new String[] { "l", "r" }) {
            ps.pushPose();
            ps.translate(0.0F, 0.0F, 0.9F * cap * PX);
            m.draw("cap_" + s, ps.last(), buf, light, ov);
            ps.popPose();
            glow(m, ps, buf, light, ov, "capglow_" + s, cap > 0.5F);
        }
        for (int k = 0; k < 3; k++) {
            ps.pushPose();
            ps.translate(0.0F, 0.0F, -0.25F * pulse * PX);
            turn(ps, new float[] { 0.0F, TB_AXIS_Y, TB_RING_Z[k] }, Axis.ZP, ring * (k % 2 == 0 ? 1.0F : -1.0F) * (1.0F + 0.3F * k));
            m.draw("ring" + k, ps.last(), buf, light, ov);
            glow(m, ps, buf, light, ov, "ringglow" + k, on);
            ps.popPose();
        }
        glow(m, ps, buf, light, ov, "lensglow", on);
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(TB_PRONG_A[k]);
            float[] piv = { (float) (TB_PRONG_R * Math.cos(a)), (float) (TB_PRONG_R * Math.sin(a)) + TB_AXIS_Y, TB_PRONG_Z };
            ps.pushPose();
            ps.translate(piv[0] * PX, piv[1] * PX, piv[2] * PX);
            ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(TB_PRONG_OPEN * prong), prongAxisX(a), prongAxisY(a), 0.0F));
            ps.translate(-piv[0] * PX, -piv[1] * PX, -piv[2] * PX);
            m.draw("prong" + k, ps.last(), buf, light, ov);
            glow(m, ps, buf, light, ov, "ptip" + k, on);
            ps.popPose();
        }
    }

    static float prongAxisX(double a) { return (float) (prongSign(a) * -Math.sin(a)); }
    static float prongAxisY(double a) { return (float) (prongSign(a) * Math.cos(a)); }

    private static double prongSign(double a) {
        org.joml.Vector3f tip = new org.joml.Vector3f(0.0F, 0.0F, -1.0F);
        new Quaternionf().rotationAxis((float) Math.toRadians(10.0), (float) -Math.sin(a), (float) Math.cos(a), 0.0F).transform(tip);
        return tip.x * Math.cos(a) + tip.y * Math.sin(a) > 0.0 ? 1.0 : -1.0;
    }

    private static void cutter(MeshModel m, PoseStack ps, MultiBufferSource buf, int light, int ov, ClientHandTools.Anim an, float pt, ItemStack stack) {
        float shroud = an == null ? 0.0F : an.l(an.shroudO, an.shroud, pt), guides = an == null ? 0.0F : an.l(an.guidesO, an.guides, pt);
        float vents = an == null ? (HandTech.overheated(stack) ? 1.0F : 0.0F) : an.l(an.ventsO, an.vents, pt);
        float fins = an == null ? 0.0F : an.l(an.finsO, an.fins, pt), valve = an == null ? 0.0F : an.l(an.valveO, an.valve, pt);
        float needle = an == null ? 0.0F : an.l(an.needleO, an.needle, pt), trig = an == null ? 0.0F : an.l(an.trigO, an.trig, pt);
        boolean arcing = an != null && an.arcing;
        int heat = HandTech.heat(stack);
        float charge = HandTech.fe(stack) / (float) HandTech.cap(stack);
        m.draw("pc_body", ps.last(), buf, light, ov);
        bone(m, ps, buf, light, ov, "trigger", PC_TRIGGER, Axis.XP, -18.0F * trig);
        bone(m, ps, buf, light, ov, "fins", PC_FINS, Axis.ZP, fins);
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 0.5F * shroud * PX);
        m.draw("shroud", ps.last(), buf, light, ov);
        ps.popPose();
        if (arcing) glow(m, ps, buf, light, ov, "tipglow", true);
        ps.pushPose();
        turn(ps, PC_GUIDE_L, Axis.YP, PC_GUIDE_OPEN * guides);
        m.draw("guide_l", ps.last(), buf, light, ov);
        glow(m, ps, buf, light, ov, "gtip_l", arcing && guides > 0.5F);
        ps.popPose();
        ps.pushPose();
        turn(ps, PC_GUIDE_R, Axis.YP, -PC_GUIDE_OPEN * guides);
        m.draw("guide_r", ps.last(), buf, light, ov);
        glow(m, ps, buf, light, ov, "gtip_r", arcing && guides > 0.5F);
        ps.popPose();
        for (int k = 0; k < 3; k++) bone(m, ps, buf, light, ov, "vent" + k, new float[] { PC_VENT_X, PC_VENT_Y[k], PC_VENT_Z }, Axis.ZP, -PC_VENT_OPEN * vents);
        if (heat > Balance.PCU_HEAT_MAX * 0.7F) glow(m, ps, buf, light, ov, "ventglow", true);
        bone(m, ps, buf, light, ov, "valve", PC_VALVE, Axis.ZP, 90.0F * valve);
        bone(m, ps, buf, light, ov, "needle", PC_NEEDLE, Axis.XP, -110.0F * needle);
        float hk = Math.min(1.0F, heat / (float) Balance.PCU_HEAT_MAX);
        for (int k = 0; k < 4; k++) {
            boolean lit = hk > k * 0.25F + 0.02F;
            if (lit && k >= 2) {
                ps.pushPose();
                m.drawColoured("heat" + k, ps.last(), buf, LightTexture.FULL_BRIGHT, ov, 255, 120, 40);
                ps.popPose();
            } else glow(m, ps, buf, light, ov, "heat" + k, lit);
            glow(m, ps, buf, light, ov, "fe" + k, charge > k * 0.25F + 0.02F);
        }
    }

    private static void turn(PoseStack ps, float[] pv, Axis axis, float deg) {
        ps.translate(pv[0] * PX, pv[1] * PX, pv[2] * PX);
        ps.mulPose(axis.rotationDegrees(deg));
        ps.translate(-pv[0] * PX, -pv[1] * PX, -pv[2] * PX);
    }

    private static void bone(MeshModel m, PoseStack ps, MultiBufferSource buf, int light, int ov, String b, float[] pv, Axis axis, float deg) {
        ps.pushPose();
        turn(ps, pv, axis, deg);
        m.draw(b, ps.last(), buf, light, ov);
        ps.popPose();
    }

    private static void glow(MeshModel m, PoseStack ps, MultiBufferSource buf, int light, int ov, String b, boolean lit) {
        if (lit) m.draw(b, ps.last(), buf, LightTexture.FULL_BRIGHT, ov);
        else m.drawColoured(b, ps.last(), buf, light, ov, 70, 80, 95);
    }

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Reg {
        @SubscribeEvent
        static void register(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (instance == null) instance = new HandToolItemRenderer();
                    return instance;
                }

                @Override
                public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                    if (stack.getItem() instanceof TractorBeamItem) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
                    return entity.isUsingItem() && entity.getUseItem() == stack ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
                }
            }, HandTech.TRACTOR_BEAM.get(), HandTech.PLASMA_CUTTER.get());
        }
    }
}
