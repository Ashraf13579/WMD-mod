package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCapsuleDescent {

    private ClientCapsuleDescent() {}

    private static final class D {
        final CrewCapsulePayloads.Descent p;
        boolean roared, boomed, popped;
        D(CrewCapsulePayloads.Descent p) { this.p = p; }
    }

    private static final List<D> LIVE = new ArrayList<>();
    private static D riding;
    private static Marker cam;
    private static boolean hidGui;
    private static float yaw, pitch;
    static final float SPLIT = 0.55F;

    static boolean riding() { return riding != null; }

    static double cloudWhite(double t) {
        if (riding == null) return 0.0;
        double y = pos(riding.p, t).y;
        return y > 150.0 && y < 230.0 ? 0.85 * Math.sin(Math.PI * (y - 150.0) / 80.0) : 0.0;
    }

    static Vec3 pos(CrewCapsulePayloads.Descent d, double t) {
        double u = Mth.clamp((t - d.start()) / d.ticks(), 0.0, 1.0);
        double alt, off;
        if (u < SPLIT) {
            double v = u / SPLIT, k = 1.0 - (1.0 - v) * (1.0 - v);
            alt = Mth.lerp(k, 700.0, 90.0);
            off = Mth.lerp(k, -500.0, -15.0);
        } else {
            double v = (u - SPLIT) / (1.0 - SPLIT);
            alt = Mth.lerp(v, 90.0, 0.0);
            off = Mth.lerp(v, -15.0, 0.0);
        }
        return new Vec3(d.x() + off, d.groundY() + alt, d.z());
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CrewCapsulePayloads.Open open = CrewCapsulePayloads.pendingOpen;
        if (open != null && mc.player != null) { CrewCapsulePayloads.pendingOpen = null; mc.setScreen(new CrewCapsuleScreen(open)); }
        CrewCapsulePayloads.Descent nd;
        while (mc.level != null && (nd = CrewCapsulePayloads.PENDING_DESCENTS.poll()) != null) {
            D d = new D(nd);
            LIVE.add(d);
            if (nd.camera() && mc.level != null && mc.player != null) startRide(mc, d);
        }
        ClientLevel level = mc.level;
        if (level == null) { LIVE.clear(); stopRide(); return; }
        long now = level.getGameTime();
        var rnd = level.random;
        for (Iterator<D> it = LIVE.iterator(); it.hasNext(); ) {
            D d = it.next();
            double u = (now - d.p.start()) / (double) d.p.ticks();
            Vec3 c = pos(d.p, now);
            if (!d.roared) { d.roared = true; WmdSound.playNF(WmdSounds.OR_CAPSULE_REENTRY.get(), WmdSounds.OR_CAPSULE_REENTRY_FAR.get(), 160.0, c.x, c.y, c.z, 30.0F, 1.0F); }
            if (!d.boomed && u >= 0.35) { d.boomed = true; WmdSound.play(WmdSounds.SONIC_BOOM.get(), d.p.x(), d.p.groundY() + 20, d.p.z(), 40.0F, 1.0F); }
            if (!d.popped && u >= SPLIT) { d.popped = true; WmdSound.play(WmdSounds.CHUTE_POP.get(), c.x, c.y + 8, c.z, 6.0F, 0.8F); }
            if (u < SPLIT * 0.85) {
                for (int i = 0; i < 4; i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, c.x + rnd.nextGaussian() * 0.8, c.y + rnd.nextGaussian() * 0.8, c.z + rnd.nextGaussian() * 0.8, -0.3, 0.6, 0.0);
                level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, c.x - 2.0, c.y + 3.0, c.z, -0.1, 0.2, 0.0);
                VeilBridge.glow(0x43A5_0000_0000_0000L ^ d.p.start(), c.x, c.y, c.z, 1.0F, 0.6F, 0.3F, 24.0F, 3.0F, 0.5F, now);
            }
            if (u > 1.05) { it.remove(); if (d == riding) stopRide(); }
        }
    }

    private static void startRide(Minecraft mc, D d) {
        stopRide();
        riding = d;
        cam = new Marker(EntityType.MARKER, mc.level);
        cam.setPos(pos(d.p, mc.level.getGameTime()));
        mc.setCameraEntity(cam);
        hidGui = !mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(new ClientCrewRide.Ride());
        ClientReentry.descentStarted(mc.level.getGameTime());
    }

    private static void stopRide() {
        Minecraft mc = Minecraft.getInstance();
        if (riding == null) return;
        riding = null;
        cam = null;
        if (hidGui) mc.options.hideGui = false;
        hidGui = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (mc.screen instanceof ClientCrewRide.Ride) mc.setScreen(null);
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (riding == null || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { stopRide(); return; }
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double u = (t - riding.p.start()) / riding.p.ticks();
        Vec3 c = pos(riding.p, t);
        Vec3 eye;
        if (u < SPLIT) {
            Vec3 dir = pos(riding.p, t + 4.0).subtract(c);
            dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(1, -1, 0).normalize() : dir.normalize();
            eye = c.subtract(dir.scale(16.0)).add(0.0, 5.0, 7.0);
        } else {
            double a = t * 0.01;
            eye = c.add(Math.cos(a) * 16.0, 4.0 + 6.0 * (1.0 - (u - SPLIT) / (1.0 - SPLIT)), Math.sin(a) * 16.0);
        }
        cam.setPos(eye);
        cam.xo = eye.x; cam.yo = eye.y; cam.zo = eye.z;
        Vec3 d = c.add(0.0, 1.2, 0.0).subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        pitch = (float) -Math.toDegrees(Math.atan2(d.y, h));
        cam.setYRot(yaw); cam.setXRot(pitch); cam.yRotO = yaw; cam.xRotO = pitch;
        if (!(mc.screen instanceof ClientCrewRide.Ride)) mc.setScreen(new ClientCrewRide.Ride());
    }

    @SubscribeEvent
    static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (riding == null) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        MeshModel mesh = MeshModel.named("crew_capsule");
        if (!mesh.has("capsule")) return;
        for (D d : LIVE) {
            double u = (t - d.p.start()) / d.p.ticks();
            if (u < 0.0 || u > 1.0) continue;
            Vec3 c = pos(d.p, t), v = pos(d.p, t + 2.0).subtract(c);
            ps.pushPose();
            ps.translate(c.x - camPos.x, c.y - camPos.y, c.z - camPos.z);
            if (u < SPLIT && v.lengthSqr() > 1.0E-6) {
                Vector3f dir = new Vector3f((float) -v.x, (float) -v.y, (float) -v.z).normalize();
                ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
            } else {
                float sway = (float) Math.sin(t * 0.08) * 6.0F;
                ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sway));
            }
            int light = LightTexture.FULL_BRIGHT;
            mesh.draw("capsule", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            if (mesh.has("hatch")) mesh.draw("hatch", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            if (u >= SPLIT && mesh.has("chutes")) {
                float k = (float) Mth.clamp((u - SPLIT) * d.p.ticks() / 12.0, 0.15, 1.0);
                ps.pushPose();
                ps.translate(0.0F, 2.6F, 0.0F);
                ps.scale(k, k, k);
                ps.translate(0.0F, -2.6F, 0.0F);
                mesh.draw("chutes", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            ps.popPose();
            if (u < SPLIT * 0.9) {
                ClientBlastGeo.beginFrame();
                BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                Vec3 back = c.subtract(v.normalize().scale(60.0));
                ClientBlastGeo.streak(bb, camPos, c.x, c.y, c.z, back.x, back.y, back.z, 5.0, 1.0F, 0.55F, 0.25F, 0.85F, 0.0F);
                ClientBlastGeo.streak(bb, camPos, c.x, c.y, c.z, back.x, back.y, back.z, 1.6, 1.0F, 0.95F, 0.8F, 1.0F, 0.0F);
                ClientBlastGeo.draw(event, bb, true, true);
            }
        }
        buffers.endBatch();
    }
}
