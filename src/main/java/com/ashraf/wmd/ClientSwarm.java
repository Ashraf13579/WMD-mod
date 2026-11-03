package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientSwarm {

    private ClientSwarm() {}

    private static final float PX = 1.0F / 16.0F;
    private static final double DRAW_RANGE = 512.0;

    private static final class Salvo {
        final Swarm.Launch s;
        final boolean[] gone;
        int left;
        Salvo(Swarm.Launch s) { this.s = s; gone = new boolean[s.cells().length]; left = gone.length; }
    }

    private static final Map<Integer, Salvo> LIVE = new HashMap<>();
    private static ClientLevel seenLevel;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); Swarm.PENDING.clear(); seenLevel = null; return; }
        if (mc.level != seenLevel) { seenLevel = mc.level; LIVE.clear(); }
        for (CustomPacketPayload p; (p = Swarm.PENDING.poll()) != null; ) {
            if (p instanceof Swarm.Launch l) LIVE.put(l.id(), new Salvo(l));
            else if (p instanceof Swarm.Kill k) {
                Salvo v = LIVE.get(k.id());
                if (v != null && k.k() >= 0 && k.k() < v.gone.length && !v.gone[k.k()]) { v.gone[k.k()] = true; v.left--; }
                if (!k.boom()) for (int i = 0; i < 6; i++)
                    mc.level.addParticle(i < 2 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.POOF, k.x(), k.y(), k.z(),
                            (mc.level.random.nextDouble() - 0.5) * 0.2, mc.level.random.nextDouble() * 0.1, (mc.level.random.nextDouble() - 0.5) * 0.2);
                if (v != null && v.left <= 0) LIVE.remove(k.id());
            }
        }
        long now = mc.level.getGameTime();
        LIVE.values().removeIf(v -> now > v.s.t0() + 6000L);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MeshModel m = MeshModel.named("swarm_drone");
        if (!m.has("sd_body")) return;
        double t = ClientClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 cam = event.getCamera().getPosition();
        Vector3f left = event.getCamera().getLeftVector(), up = event.getCamera().getUpVector();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        int ov = OverlayTexture.NO_OVERLAY, full = LightTexture.FULL_BRIGHT;
        for (Salvo v : LIVE.values()) {
            Swarm.Launch s = v.s;
            for (int k = 0; k < v.gone.length; k++) {
                if (v.gone[k]) continue;
                Vec3 p = Swarm.path(s, k, t, null);
                if (p == null || p.distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
                Vec3 ahead = Swarm.path(s, k, t + 0.5, null), dir = ahead == null ? new Vec3(0, 1, 0) : ahead.subtract(p);
                dir = dir.lengthSqr() < 1.0E-8 ? new Vec3(0, -1, 0) : dir.normalize();
                double u = t - Swarm.launchTick(s, k);
                float wing = (float) Math.max(0.0, Math.min(1.0, (u - 4.0) / 6.0));
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
                ps.pushPose();
                ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                ps.mulPose(new Quaternionf().rotationTo(new Vector3f(1, 0, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)));
                m.draw("sd_body", ps.last(), buffers, light, ov);
                if (m.has("sd_glow")) m.draw("sd_glow", ps.last(), buffers, full, ov);
                if (m.has("sd_wing_r")) m.draw("sd_wing_r", ps.last(), buffers, light, ov);
                if (m.has("sd_wing_f")) {
                    ps.pushPose();
                    ps.translate(1.0F * PX, 1.5F * PX, 0.0F);
                    ps.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-88.0 * (1.0 - wing))));
                    ps.translate(-1.0F * PX, -1.5F * PX, 0.0F);
                    m.draw("sd_wing_f", ps.last(), buffers, light, ov);
                    ps.popPose();
                }
                if (m.has("sd_prop")) {
                    ps.pushPose();
                    ps.mulPose(new Quaternionf().rotationX((float) ((t * 2.6 + k) % (Math.PI * 2.0))));
                    m.draw("sd_prop", ps.last(), buffers, light, ov);
                    ps.popPose();
                }
                ps.popPose();
                Vec3 tail = p.subtract(dir.scale(0.45));
                ClientBlastGeo.disc(bb, cam, left, up, tail.x, tail.y, tail.z, 0.35, 0.35F, 0.6F, 1.0F, 0.55F);
                any = true;
            }
        }
        buffers.endBatch();
        ClientBlastGeo.draw(event, bb, any, true);
    }
}
