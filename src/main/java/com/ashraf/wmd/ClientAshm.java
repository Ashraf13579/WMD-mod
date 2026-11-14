package com.ashraf.wmd;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAshm {

    private ClientAshm() {}

    static final String MESH = "ashm";

    static final float LENGTH = 3.3F, LUG_TOP = 6.4F, TOP_Z = 3.0F, WING_DEG = 90.0F, FIN_STOWED = 0.30F;
    static final float[] WING_PIVOT = { 0.0F, 30.0F, 3.6F }, FIN_PIVOT = { 0.0F, 6.0F, 0.0F };
    static final int FIN_AT = 2, FIN_TICKS = 4, WING_AT = 4, WING_TICKS = 8;

    static final float WING_SIGN = 1.0F;

    static final int PAINT = 3;

    static void drawMissile(float wing, float fin, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(MESH);
        if (!mesh.has("body")) return;
        int[] t = MissileParts.tint(PAINT);
        mesh.drawTinted("body", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
        float a = WING_SIGN * WING_DEG * Mth.clamp(wing, 0.0F, 1.0F);
        bone(mesh, "wing_r", pivot("wing_r", WING_PIVOT), a, 1.0F, t, ps, buffers, light);
        bone(mesh, "wing_l", pivot("wing_l", WING_PIVOT), -a, 1.0F, t, ps, buffers, light);
        bone(mesh, "fins", pivot("fins", FIN_PIVOT), 0.0F, FIN_STOWED + (1.0F - FIN_STOWED) * Mth.clamp(fin, 0.0F, 1.0F), t, ps, buffers, light);
    }

    private static float[] pivot(String bone, float[] fallback) { return SatelliteMesh.pivot(MESH, bone, fallback); }

    private static void bone(MeshModel mesh, String bone, float[] pv, float rz, float sxz, int[] t, PoseStack ps, MultiBufferSource buffers, int light) {
        if (!mesh.has(bone)) return;
        ps.pushPose();
        ps.translate(pv[0] / 16.0F, pv[1] / 16.0F, pv[2] / 16.0F);
        if (rz != 0.0F) ps.mulPose(Axis.ZP.rotationDegrees(rz));
        if (sxz != 1.0F) ps.scale(sxz, 1.0F, sxz);
        ps.translate(-pv[0] / 16.0F, -pv[1] / 16.0F, -pv[2] / 16.0F);
        mesh.drawTinted(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
        ps.popPose();
    }

    static void drawStore(PoseStack ps, MultiBufferSource buffers, int light, boolean inBay) {
        ps.pushPose();
        ps.translate(0.0F, -(inBay ? TOP_Z : LUG_TOP) / 16.0F, LENGTH * 0.5F);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        drawMissile(0.0F, 0.0F, ps, buffers, light);
        ps.popPose();
    }

    private static Quaternionf attitude(Vec3 dir, double bank) {
        Vector3f ny = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        Vector3f nx = new Vector3f(ny).cross(0.0F, 1.0F, 0.0F);
        if (nx.lengthSquared() < 1.0E-6F) nx.set(1.0F, 0.0F, 0.0F);
        nx.normalize();
        Vector3f nz = new Vector3f(nx).cross(ny).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(nx, ny, nz)).rotateY((float) bank);
    }

    static void render(Flight f, double t, Vec3 p, Vec3 cam, PoseStack ps, MultiBufferSource buffers, ClientLevel level) {
        Vec3 a = f.position(t + 1.0).subtract(p);
        if (a.lengthSqr() < 1.0E-8) return;
        Vec3 b = f.position(t + 3.0).subtract(f.position(t + 2.0));
        double turn = a.x * b.z - a.z * b.x;
        double hl = Math.max(1.0E-6, Math.hypot(a.x, a.z) * Math.hypot(b.x, b.z));
        double bank = Mth.clamp(turn / hl * 14.0, -0.6, 0.6);
        double since = t - f.start();
        float fin = (float) ((since - FIN_AT) / FIN_TICKS), wing = (float) ((since - WING_AT) / WING_TICKS);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
        ps.pushPose();
        ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        ps.mulPose(attitude(a, bank));
        ps.translate(0.0, -LENGTH * 0.5, 0.0);
        drawMissile(wing, fin, ps, buffers, light);
        ps.popPose();
    }

    private static final java.util.Set<Long> WHOOSHED = new java.util.HashSet<>();

    static void tickFx(ClientLevel level, Flight f, long now, Vec3 p, Vec3 prev, Vec3 cam, RandomSource rnd) {
        Vec3 back = prev.subtract(p);
        double len = back.length();
        if (len < 1.0E-4) return;
        Vec3 tail = p.add(back.scale(LENGTH * 0.5 / len));
        double d = p.distanceTo(cam);
        VeilBridge.glow(0x5D00_0000_0000_0000L ^ f.id(), tail.x, tail.y, tail.z, 1.0F, 0.55F, 0.3F, 4.0F, 0.7F, 0.2F, now);
        if (d < 300.0 && now % 2 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, tail.x + rnd.nextGaussian() * 0.05, tail.y + rnd.nextGaussian() * 0.05, tail.z + rnd.nextGaussian() * 0.05,
                    back.x * 0.02, 0.005, back.z * 0.02);
        if (now - f.start() < 3 && d < 300.0)
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, p.x, p.y + 0.3, p.z, 0.0, -0.02, 0.0);
        if (d < ClientConfig.trailRange()) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int x = Mth.floor(p.x), z = Mth.floor(p.z), y0 = Mth.floor(p.y);
            for (int y = y0; y >= y0 - 7; y--) {
                m.set(x, y, z);
                if (!level.getFluidState(m).is(FluidTags.WATER)) continue;
                double sy = y + 1.0;
                int n = ClientConfig.count(3, rnd);
                for (int i = 0; i < n; i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.SPLASH, true, p.x + back.x * rnd.nextDouble() + rnd.nextGaussian() * 0.4, sy,
                            p.z + back.z * rnd.nextDouble() + rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.05, 0.2, rnd.nextGaussian() * 0.05);
                if (now % 3 == 0) level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, tail.x, sy + 0.2, tail.z, back.x * 0.03, 0.02, back.z * 0.03);
                break;
            }
        }
        if (d < 48.0 && WHOOSHED.add(f.id())) WmdSound.play(SoundEvents.ELYTRA_FLYING, p.x, p.y, p.z, Balance.SND_RELEASE, 1.8F);
        if (WHOOSHED.size() > 256) WHOOSHED.clear();
    }

    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {

        public ItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
            Minecraft mc = Minecraft.getInstance();
            boolean gui = ctx == ItemDisplayContext.GUI;
            float sy = (gui ? 0.80F : 1.2F) / LENGTH, sxz = gui ? sy * 1.6F : sy;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(sxz, sy, sxz);
            poseStack.translate(0.0, -LENGTH * 0.5, 0.0);
            if (gui) {
                Lighting.setupForFlatItems();
                MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource bs ? bs : mc.renderBuffers().bufferSource();
                drawMissile(0.0F, 0.0F, poseStack, src, packedLight);
                src.endBatch();
                RenderSystem.enableDepthTest();
                Lighting.setupFor3DItems();
            } else {
                drawMissile(0.0F, 0.0F, poseStack, buffers, packedLight);
            }
            poseStack.popPose();
        }
    }

    private static ItemRenderer itemRenderer;

    @SubscribeEvent
    static void register(RegisterClientExtensionsEvent event) {
        if (!AirGate.AVAILABLE) return;
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) itemRenderer = new ItemRenderer();
                return itemRenderer;
            }
        }, AntiShipItems.ASHM.get());
    }
}
