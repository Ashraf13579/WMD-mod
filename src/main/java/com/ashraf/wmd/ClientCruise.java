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
public final class ClientCruise {

    private ClientCruise() {}

    static final String MESH = "cruise_missile";

    static final float LENGTH = 6.0F, WING_DEG = 90.0F, FIN_FOLDED = 0.35F;
    static final float[] WING_PIVOT = { 0.0F, 56.0F, 5.0F }, FIN_PIVOT = { 0.0F, 10.0F, 0.0F };
    static final int FIN_AT = 4, FIN_TICKS = 6, WING_AT = 10, WING_TICKS = 14, BOOST_TICKS = 40;

    static final float WING_SIGN = 1.0F;

    static void drawMissile(MissileBuild b, float wing, float fin, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(MESH);
        if (!mesh.has("body")) return;
        int[] t = MissileParts.tint(MissileParts.paintOf(b));
        int ov = OverlayTexture.NO_OVERLAY;
        mesh.drawTinted("body", ps.last(), buffers, light, ov, t[0], t[1], t[2], 255, false);
        float a = WING_SIGN * WING_DEG * Mth.clamp(wing, 0.0F, 1.0F);
        bone(mesh, "wing_r", pivot("wing_r", WING_PIVOT), a, 1.0F, t, ps, buffers, light);
        bone(mesh, "wing_l", pivot("wing_l", WING_PIVOT), -a, 1.0F, t, ps, buffers, light);
        bone(mesh, "fins", pivot("fins", FIN_PIVOT), 0.0F, FIN_FOLDED + (1.0F - FIN_FOLDED) * Mth.clamp(fin, 0.0F, 1.0F), t, ps, buffers, light);
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

    private static Quaternionf attitude(Vec3 dir, double bank) {
        Vector3f ny = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        Vector3f nx = new Vector3f(ny).cross(0.0F, 1.0F, 0.0F);
        if (nx.lengthSquared() < 1.0E-6F) nx.set(1.0F, 0.0F, 0.0F);
        nx.normalize();
        Vector3f nz = new Vector3f(nx).cross(ny).normalize();
        Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(nx, ny, nz));
        return q.rotateY((float) bank);
    }

    static void render(Flight f, double t, Vec3 p, Vec3 cam, PoseStack ps, MultiBufferSource buffers, ClientLevel level) {
        if (AntiShipMissiles.isAshm(f.build())) { ClientAshm.render(f, t, p, cam, ps, buffers, level); return; }
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
        drawMissile(f.build(), wing, fin, ps, buffers, light);
        ps.popPose();
    }

    private static final java.util.Set<Long> WHOOSHED = new java.util.HashSet<>();

    static void tickFx(ClientLevel level, Flight f, long now, Vec3 p, Vec3 prev, Vec3 cam, RandomSource rnd) {
        if (AntiShipMissiles.isAshm(f.build())) { ClientAshm.tickFx(level, f, now, p, prev, cam, rnd); return; }
        Vec3 back = prev.subtract(p);
        double len = back.length();
        if (len < 1.0E-4) return;
        Vec3 tail = p.add(back.scale(LENGTH * 0.5 / len));
        long since = now - f.start();
        double d = p.distanceTo(cam);
        if (since < BOOST_TICKS) {
            float k = 1.0F - since / (float) BOOST_TICKS;
            VeilBridge.glow(0x5C00_0000_0000_0000L ^ f.id(), tail.x, tail.y, tail.z, 1.0F, 0.7F, 0.35F, 10.0F, 2.0F * k, 0.35F, now);
            if (d < ClientConfig.trailRange()) {
                int n = Math.max(1, Math.min(ClientConfig.count(8, rnd), (int) (len / 0.8) + 1));
                for (int i = 0; i < n; i++) {
                    double u = (i + rnd.nextDouble()) / n;
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, tail.x + back.x * u + rnd.nextGaussian() * 0.15, tail.y + back.y * u + rnd.nextGaussian() * 0.15,
                            tail.z + back.z * u + rnd.nextGaussian() * 0.15, 0, -0.01, 0);
                }
                level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x, tail.y, tail.z, back.x * 0.05, back.y * 0.05, back.z * 0.05);
            }
        } else {
            VeilBridge.glow(0x5C00_0000_0000_0000L ^ f.id(), tail.x, tail.y, tail.z, 1.0F, 0.55F, 0.3F, 4.0F, 0.6F, 0.2F, now);
            if (d < 300.0 && now % 2 == 0)
                level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, tail.x + rnd.nextGaussian() * 0.05, tail.y + rnd.nextGaussian() * 0.05, tail.z + rnd.nextGaussian() * 0.05,
                        back.x * 0.02, 0.005, back.z * 0.02);
        }
        if (d < 64.0 && WHOOSHED.add(f.id())) WmdSound.play(SoundEvents.ELYTRA_FLYING, p.x, p.y, p.z, 3.0F, 1.6F);
        if (WHOOSHED.size() > 256) WHOOSHED.clear();
    }

    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {

        public ItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
            MissileBuild build = CruiseMissileItem.buildOf(stack);
            Minecraft mc = Minecraft.getInstance();
            boolean gui = ctx == ItemDisplayContext.GUI;
            float sy = (gui ? 0.80F : 1.6F) / LENGTH, sxz = gui ? sy * 1.8F : sy;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(sxz, sy, sxz);
            poseStack.translate(0.0, -LENGTH * 0.5, 0.0);
            if (gui) {
                Lighting.setupForFlatItems();
                MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource bs ? bs : mc.renderBuffers().bufferSource();
                drawMissile(build, 0.0F, 0.0F, poseStack, src, packedLight);
                src.endBatch();
                RenderSystem.enableDepthTest();
                Lighting.setupFor3DItems();
            } else {
                drawMissile(build, 0.0F, 0.0F, poseStack, buffers, packedLight);
            }
            poseStack.popPose();
        }
    }

    private static ItemRenderer missileRenderer;
    private static PartItemRenderer partRenderer;

    @SubscribeEvent
    static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (missileRenderer == null) missileRenderer = new ItemRenderer();
                return missileRenderer;
            }
        }, Wmd.CRUISE_MISSILE.get());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (partRenderer == null) partRenderer = new PartItemRenderer();
                return partRenderer;
            }
        }, Wmd.JET_ENGINE.get());
    }
}
