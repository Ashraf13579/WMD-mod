package com.ashraf.wmd;

import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientSlbm {

    private ClientSlbm() {}

    static final String SLBM = "slbm", CAPSULE = "cruise_capsule";

    static final float SPIKE_IN = 5.0F / 16.0F;
    static final float SLBM_LEN = 6.0F, CAPSULE_H = 6.25F;

    static void drawSlbm(int paint, float spike, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(SLBM);
        if (!mesh.has("body")) return;
        int[] t = MissileParts.tint(paint);
        mesh.drawTinted("body", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
        if (!mesh.has("spike")) return;
        ps.pushPose();
        ps.translate(0.0F, -SPIKE_IN * (1.0F - Mth.clamp(spike, 0.0F, 1.0F)), 0.0F);
        mesh.drawTinted("spike", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
        ps.popPose();
    }

    static void drawCapsule(boolean withCap, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(CAPSULE);
        if (!mesh.has("body")) return;
        mesh.draw("body", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        if (withCap && mesh.has("cap")) mesh.draw("cap", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
    }

    static void renderFlight(Flight f, double t, Vec3 p, Vec3 cam, PoseStack ps, MultiBufferSource buffers, ClientLevel level) {
        Vec3 a = f.position(t + 1.0).subtract(p);
        if (a.lengthSqr() < 1.0E-8) return;
        Vec3 d = a.normalize();
        int light = p.y > level.getMaxBuildHeight() ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(p));
        ps.pushPose();
        ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) d.x, (float) d.y, (float) d.z));
        ps.translate(0.0F, -SLBM_LEN * 0.5F, 0.0F);
        drawSlbm(MissileParts.paintOf(f.build()), 1.0F, ps, buffers, light);
        ps.popPose();
    }

    public static final class SlbmRenderer extends EntityRenderer<SlbmEntity> {
        public SlbmRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.0F; }

        @Override
        public boolean shouldRender(SlbmEntity e, Frustum frustum, double x, double y, double z) { return true; }

        @Override
        public void render(SlbmEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
            Vec3 at = e.climbPos(pt);
            ps.pushPose();
            if (at != null) ps.translate(at.x - here.x, at.y - here.y, at.z - here.z);
            drawSlbm(e.paint(), e.spike01(pt), ps, buffers, e.phase() == SlbmEntity.CLIMB ? LightTexture.FULL_BRIGHT : light);
            ps.popPose();
            super.render(e, entityYaw, pt, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(SlbmEntity e) { return InventoryMenu.BLOCK_ATLAS; }
    }

    public static final class CapsuleRenderer extends EntityRenderer<CapsuleEntity> {
        public CapsuleRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.0F; }

        @Override
        public boolean shouldRender(CapsuleEntity e, Frustum frustum, double x, double y, double z) { return true; }

        @Override
        public void render(CapsuleEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            boolean drop = e.phase() == CapsuleEntity.DROP;
            Vec3 d = e.dir();
            ps.pushPose();
            float tilt = e.tilt(pt);
            if (tilt > 0.0F) {
                ps.translate(0.0F, CAPSULE_H * 0.5F, 0.0F);
                ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(tilt), (float) -d.z, 0.0F, (float) d.x));
                ps.translate(0.0F, -CAPSULE_H * 0.5F, 0.0F);
            }
            drawCapsule(!drop, ps, buffers, light);
            ps.popPose();
            if (drop) {
                double k = e.age(pt);
                Vec3 c = e.capPos(k);
                int top = NavalLayer.waterTop(e.level(), Mth.floor(c.x), Mth.floor(c.z));
                if (k < 120 && (top == NavalLayer.NO_WATER || c.y > top + 0.3)) {
                    Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
                    MeshModel mesh = MeshModel.named(CAPSULE);
                    if (mesh.has("cap")) {
                        float pv = 94.0F / 16.0F;
                        ps.pushPose();
                        ps.translate(c.x - here.x, c.y - here.y, c.z - here.z);
                        ps.mulPose(Axis.XP.rotationDegrees((float) (k * 23.0)));
                        ps.mulPose(Axis.ZP.rotationDegrees((float) (k * 11.0)));
                        ps.translate(0.0F, -pv, 0.0F);
                        mesh.draw("cap", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
                        ps.popPose();
                    }
                }
            }
            super.render(e, entityYaw, pt, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(CapsuleEntity e) { return InventoryMenu.BLOCK_ATLAS; }
    }

    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        private final boolean slbm;

        public ItemRenderer(boolean slbm) { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); this.slbm = slbm; }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
            Minecraft mc = Minecraft.getInstance();
            boolean gui = ctx == ItemDisplayContext.GUI;
            float len = slbm ? SLBM_LEN : CAPSULE_H;
            float sy = (gui ? 0.80F : 1.6F) / len, sxz = gui ? sy * 1.8F : sy;
            int paint = slbm ? MissileParts.paintOf(SlbmItem.buildOf(stack)) : 0;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(sxz, sy, sxz);
            poseStack.translate(0.0, -len * 0.5, 0.0);
            MultiBufferSource src = buffers;
            if (gui) { Lighting.setupForFlatItems(); src = buffers instanceof MultiBufferSource.BufferSource bs ? bs : mc.renderBuffers().bufferSource(); }
            if (slbm) drawSlbm(paint, 0.0F, poseStack, src, packedLight);
            else drawCapsule(true, poseStack, src, packedLight);
            if (gui) {
                if (src instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
                RenderSystem.enableDepthTest();
                Lighting.setupFor3DItems();
            }
            poseStack.popPose();
        }
    }

    private static ItemRenderer slbmItem, capsuleItem;

    @SubscribeEvent
    static void registerItems(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (slbmItem == null) slbmItem = new ItemRenderer(true);
                return slbmItem;
            }
        }, Wmd.SLBM.get());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (capsuleItem == null) capsuleItem = new ItemRenderer(false);
                return capsuleItem;
            }
        }, Wmd.CRUISE_CAPSULE.get());
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Wmd.SLBM_ENTITY.get(), SlbmRenderer::new);
        event.registerEntityRenderer(Wmd.CAPSULE_ENTITY.get(), CapsuleRenderer::new);
    }
}
