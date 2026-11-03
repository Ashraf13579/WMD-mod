package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.joml.Matrix4f;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientLaser {

    private ClientLaser() {}

    static final int BEAM_TICKS = Balance.LASER_FIRE_TICKS - Balance.LASER_HIT_AT, GROUND_TICKS = 25;
    private record Shot(Vec3 from, Vec3 to, long start, boolean hit, boolean ground) {}
    private static final List<Shot> LIVE = new ArrayList<>();

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CustomPacketPayload p;
        while ((p = LaserPayloads.PENDING.poll()) != null) {
            if (mc.level == null) continue;
            long now = mc.level.getGameTime();
            if (p instanceof LaserPayloads.Beam b) {
                LIVE.add(new Shot(b.from(), b.to(), now, b.hit(), false));
                VeilBridge.flash(b.from().x, b.from().y, b.from().z, 0.6F, 0.8F, 1.0F, 24.0F, 3.0F, BEAM_TICKS);
                if (b.hit()) VeilBridge.flash(b.to().x, b.to().y, b.to().z, 1.0F, 0.85F, 0.7F, 30.0F, 3.0F, BEAM_TICKS + 10);
            } else if (p instanceof LaserPayloads.Ground gr) {
                Vec3 at = new Vec3(gr.x(), gr.y(), gr.z());
                LIVE.add(new Shot(at.add(0.0, 360.0, 0.0), at, now, true, true));
                VeilBridge.flash(at.x, at.y, at.z, 0.8F, 0.85F, 1.0F, 40.0F, 3.0F, 30);
                mc.getSoundManager().play(new SimpleSoundInstance(WmdSounds.LZ_HIT_FAR.get(), SoundSource.BLOCKS, 3.0F, 1.0F, RandomSource.create(), at.x, at.y, at.z));
            }
        }
        if (mc.level == null) { LIVE.clear(); return; }
        long now = mc.level.getGameTime();
        RandomSource rnd = mc.level.random;
        for (Iterator<Shot> it = LIVE.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            long age = now - s.start();
            if (age > (s.ground() ? GROUND_TICKS : BEAM_TICKS)) { it.remove(); continue; }
            if (s.hit()) {
                for (int i = 0; i < (s.ground() ? 8 : 4); i++)
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, s.to().x + (rnd.nextFloat() - 0.5) * 2.0, s.to().y + (rnd.nextFloat() - 0.5) * 2.0, s.to().z + (rnd.nextFloat() - 0.5) * 2.0,
                            (rnd.nextFloat() - 0.5) * 0.6, (rnd.nextFloat() - 0.5) * 0.6, (rnd.nextFloat() - 0.5) * 0.6);
                if (age == 2) {
                    mc.level.addParticle(ParticleTypes.FLASH, s.to().x, s.to().y, s.to().z, 0, 0, 0);
                    for (int i = 0; i < 20; i++)
                        mc.level.addParticle(s.ground() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.LAVA, s.to().x, s.to().y, s.to().z, (rnd.nextFloat() - 0.5) * 0.5, rnd.nextFloat() * 0.4, (rnd.nextFloat() - 0.5) * 0.5);
                }
            }
            if (!s.ground() && age < 3) mc.level.addParticle(ParticleTypes.END_ROD, s.from().x, s.from().y, s.from().z, 0, 0, 0);
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Shot s : LIVE) {
            float age = t - s.start(), len = s.ground() ? GROUND_TICKS : BEAM_TICKS;
            if (age < 0.0F || age > len) continue;
            float k = Math.min(1.0F, age / 2.0F) * (1.0F - Math.max(0.0F, (age - len + 6.0F) / 6.0F));
            float pulse = 0.85F + 0.15F * (float) Math.sin(age * 2.4F);
            float w = s.ground() ? 1.6F : 1.0F;
            beam(b, m, s.from().subtract(cam), s.to().subtract(cam), 2.2F * w * pulse, 0.35F, 0.45F, 1.0F, 0.35F * k);
            beam(b, m, s.from().subtract(cam), s.to().subtract(cam), 0.9F * w * pulse, 0.6F, 0.85F, 1.0F, 0.7F * k);
            beam(b, m, s.from().subtract(cam), s.to().subtract(cam), 0.32F * w, 1.0F, 1.0F, 1.0F, 1.0F * k);
            any = true;
        }
        if (any) BufferUploader.drawWithShader(b.buildOrThrow()); else b.build();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void beam(BufferBuilder b, Matrix4f m, Vec3 a, Vec3 c, float w, float r, float g, float bl, float alpha) {
        Vec3 dir = c.subtract(a);
        Vec3 mid = a.add(c).scale(0.5);
        Vec3 side = dir.cross(mid).normalize().scale(w);
        if (!Double.isFinite(side.x)) return;
        b.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(r, g, bl, alpha);
        b.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(r, g, bl, alpha);
        b.addVertex(m, (float) (c.x + side.x), (float) (c.y + side.y), (float) (c.z + side.z)).setColor(r, g, bl, alpha * 0.8F);
        b.addVertex(m, (float) (c.x - side.x), (float) (c.y - side.y), (float) (c.z - side.z)).setColor(r, g, bl, alpha * 0.8F);
    }
}
