package com.ashraf.wmd;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class TowBarClient {

    private TowBarClient() {}

    static final String MESH = "tow_bar";
    private static final String[] BONES = { "grip", "slide1", "slide2", "head" };

    static void draw(MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, float extPx) {
        float e = Math.max(0.0F, Math.min(3.0F * Balance.TOW_STAGE_PX, extPx)) / 16.0F;
        for (int k = 0; k < BONES.length; k++) {
            if (!mesh.has(BONES[k])) continue;
            ps.pushPose();
            ps.translate(0.0F, 0.0F, -e * k / 3.0F);
            mesh.draw(BONES[k], ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    static boolean hookedStack(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || TowBar.CLIENT_TOWED.isEmpty()) return false;
        for (Long l : TowBar.CLIENT_TOWED) {
            if (!(mc.level.getBlockEntity(BlockPos.of(l)) instanceof LandingGearBlockEntity g) || g.towBy() == null) continue;
            Player p = mc.level.getPlayerByUUID(g.towBy());
            if (p != null && (p.getMainHandItem() == stack || p.getOffhandItem() == stack)) return true;
        }
        return false;
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || TowBar.CLIENT_TOWED.isEmpty() || !AirGate.on()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        MeshModel mesh = MeshModel.named(MESH);
        if (!mesh.has("grip")) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Long l : new java.util.ArrayList<>(TowBar.CLIENT_TOWED)) {
            BlockPos pos = BlockPos.of(l);
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof LandingGearBlockEntity g) || g.towBy() == null) { TowBar.CLIENT_TOWED.remove(l); continue; }
            Player p = level.getPlayerByUUID(g.towBy());
            if (p == null) continue;
            Vec3 axle = ShipFrame.drawnWorld(level, pos, axlePlot(g), pt);
            Vec3 hand = hand(p, pt);
            Vec3 d = axle.subtract(hand);
            double len = d.length();
            if (len < 0.3 || len > Balance.TOW_LEN_MAX + Balance.TOW_SNAP + 4.0) continue;
            ps.pushPose();
            ps.translate(hand.x - cam.x, hand.y - cam.y, hand.z - cam.z);
            ps.mulPose(Axis.YP.rotation((float) Math.atan2(-d.x, -d.z)));
            ps.mulPose(Axis.XP.rotation((float) Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len)))));
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(hand.add(d.scale(0.5))));
            draw(mesh, ps, buffers, light, (float) (len * 16.0 - Balance.TOW_BAR_PX0));
            ps.popPose();
        }
        buffers.endBatch();
    }

    static Vec3 axlePlot(LandingGearBlockEntity g) {
        Direction l = g.legDir(), sd = g.sideDir(), ad = g.aftDir();
        double d = 0.5 + g.reach() - g.comp * Balance.GEAR_STROKE - g.wheelR(), so = g.sideOffset(), ao = g.aftOffset();
        return Vec3.atCenterOf(g.getBlockPos()).add(l.getStepX() * d + sd.getStepX() * so + ad.getStepX() * ao,
                l.getStepY() * d + sd.getStepY() * so + ad.getStepY() * ao, l.getStepZ() * d + sd.getStepZ() * so + ad.getStepZ() * ao);
    }

    static Vec3 hand(Player p, float pt) {
        Vec3 b = p.getPosition(pt);
        double yaw = Math.toRadians(net.minecraft.util.Mth.lerp(pt, p.yBodyRotO, p.yBodyRot));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        return b.add(fx * 0.3 + rx * 0.3, Balance.TOW_HAND_Y, fz * 0.3 + rz * 0.3);
    }

    private static final float[][] PLACE = {
            { 0.0F, 0.0F, 0.0F, 45.0F, 20.0F, 0.42F },
            { 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.45F },
            { 0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 0.5F },
            { 0.0F, 0.1F, 0.1F, 0.0F, -30.0F, 1.0F },
            { 0.25F, -0.25F, -0.35F, 0.0F, -25.0F, 0.6F },
    };

    static final class TowBarItemRenderer extends BlockEntityWithoutLevelRenderer {
        TowBarItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
            MeshModel mesh = MeshModel.named(MESH);
            if (!mesh.has("grip")) return;
            boolean hand = ctx.firstPerson() || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            if (hand && hookedStack(stack)) return;
            float[] p = switch (ctx) {
                case GUI -> PLACE[0];
                case GROUND -> PLACE[1];
                case FIXED -> PLACE[2];
                case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> PLACE[3];
                case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> PLACE[4];
                default -> PLACE[1];
            };
            boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            ps.pushPose();
            ps.translate(0.5F + (left ? -p[0] : p[0]), 0.5F + p[1], 0.5F + p[2]);
            ps.mulPose(Axis.YP.rotationDegrees(p[3]));
            ps.mulPose(Axis.XP.rotationDegrees(p[4]));
            ps.scale(p[5], p[5], p[5]);
            if (!hand) ps.translate(0.0F, 0.0F, Balance.TOW_BAR_PX0 / 32.0F);
            if (ctx == ItemDisplayContext.GUI) {
                Lighting.setupForFlatItems();
                MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource bs ? bs : Minecraft.getInstance().renderBuffers().bufferSource();
                draw(mesh, ps, src, light, 0.0F);
                src.endBatch();
                RenderSystem.enableDepthTest();
                Lighting.setupFor3DItems();
            } else {
                draw(mesh, ps, buffers, light, 0.0F);
            }
            ps.popPose();
        }
    }

    private static TowBarItemRenderer instance;

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Reg {
        @SubscribeEvent
        static void register(RegisterClientExtensionsEvent event) {
            if (!AirGate.AVAILABLE) return;
            event.registerItem(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (instance == null) instance = new TowBarItemRenderer();
                    return instance;
                }
            }, AirBlocks.TOW_BAR.get());
        }
    }
}
