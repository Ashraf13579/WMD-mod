package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCargo {

    private ClientCargo() {}

    private record Show(CargoPayloads.Release r, Direction dir) {}
    private static final List<Show> LIVE = new ArrayList<>();
    private static final int CLAMP = 20, BACK = 140, TURN = 220, BURN = 300;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CargoPayloads.Release p;
        while ((p = CargoPayloads.PENDING.poll()) != null) LIVE.add(new Show(p, Station.spotDir(p.port())));
        if (mc.level == null) { LIVE.clear(); return; }
        long now = mc.level.getGameTime();
        RandomSource rnd = mc.level.random;
        for (Iterator<Show> it = LIVE.iterator(); it.hasNext(); ) {
            Show s = it.next();
            long age = now - s.r().start();
            if (age > Balance.CARGO_RELEASE_TICKS) { it.remove(); continue; }
            Vec3 at = centre(s, age);
            if (age == BACK - 120 || age == TURN - 60) sound(mc, at, WmdSounds.OR_EVA_THRUST.get(), 2.0F, 0.7F);
            if (age == TURN) sound(mc, at, WmdSounds.CR_BURN.get(), 3.0F, 1.0F);
            if (age == 320) ClientPlanet.streak(now);
            if (age >= CLAMP && age < TURN && rnd.nextFloat() < 0.5F)
                mc.level.addParticle(ParticleTypes.CLOUD, at.x + (rnd.nextFloat() - 0.5) * 3.0, at.y + (rnd.nextFloat() - 0.5) * 3.0, at.z + (rnd.nextFloat() - 0.5) * 3.0,
                        (rnd.nextFloat() - 0.5) * 0.2, (rnd.nextFloat() - 0.5) * 0.2, (rnd.nextFloat() - 0.5) * 0.2);
            if (age >= TURN && age < BURN) {
                for (int i = 0; i < 3; i++) {
                    mc.level.addParticle(ParticleTypes.FLAME, at.x + (rnd.nextFloat() - 0.5) * 0.6, at.y + 2.2, at.z + (rnd.nextFloat() - 0.5) * 0.6, (rnd.nextFloat() - 0.5) * 0.05, 0.35 + rnd.nextFloat() * 0.2, (rnd.nextFloat() - 0.5) * 0.05);
                    mc.level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + (rnd.nextFloat() - 0.5), at.y + 3.0, at.z + (rnd.nextFloat() - 0.5), 0.0, 0.25, 0.0);
                }
            }
        }
    }

    private static void sound(Minecraft mc, Vec3 at, net.minecraft.sounds.SoundEvent e, float vol, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(e, SoundSource.BLOCKS, vol, pitch, RandomSource.create(), at.x, at.y, at.z));
    }

    private static float back(float age) { return 10.0F * CargoPodRenderer.smooth((age - CLAMP) / (BACK - CLAMP)); }

    private static float drop(float age) {
        if (age < TURN) return 0.0F;
        float b = age - TURN;
        return 0.0025F * b * b;
    }

    private static Vec3 centre(Show s, float age) {
        var n = s.dir().getNormal();
        double out = back(age) - 0.1;
        return new Vec3(s.r().pos().getX() + 0.5 + n.getX() * out, s.r().pos().getY() + 1.5 - drop(age), s.r().pos().getZ() + 0.5 + n.getZ() * out);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MeshModel mesh = MeshModel.named("cargo_pod");
        if (!mesh.has("pod")) return;
        float t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Show s : LIVE) {
            float age = t - s.r().start();
            if (age < 0.0F || age > Balance.CARGO_RELEASE_TICKS) continue;
            var pos = s.r().pos();
            ps.pushPose();
            ps.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            CargoPodRenderer.portFrame(ps, s.dir());
            ps.translate(back(age), -drop(age), 0.0F);

            float pitch = 90.0F * CargoPodRenderer.smooth((age - BACK) / (TURN - BACK));
            ps.translate(-0.1F, 1.5F, 0.0F);
            ps.mulPose(Axis.ZP.rotationDegrees(pitch));
            ps.translate(0.1F, -1.5F, 0.0F);
            CargoPodRenderer.podFrame(ps);
            float burn = age >= TURN && age < BURN ? Math.min(1.0F, (age - TURN) / 10.0F) : age >= BURN ? Math.max(0.0F, 1.0F - (age - BURN) / 20.0F) : 0.0F;
            int alpha = age < BURN ? 255 : (int) (255 * Math.max(0.05F, 1.0F - (age - BURN) / (Balance.CARGO_RELEASE_TICKS - BURN)));
            boolean rcs = age >= CLAMP && age < TURN && ((int) (t / 3.0F) % 3 != 0);
            CargoPodRenderer.draw(mesh, new CargoPodRenderer.Pose(1.0F, Math.min(1.0F, age / CLAMP), 0.0F, false, rcs, burn), ps, buffers, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, alpha);
            ps.popPose();
        }
        buffers.endBatch();
    }
}
