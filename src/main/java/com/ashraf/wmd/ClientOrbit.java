package com.ashraf.wmd;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientOrbit {

    private ClientOrbit() {}

    private static final double DIST = 100.0;
    private static final float SIZE = 0.16F, FLASH_SIZE = 0.30F;

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { OrbitView.clear(); }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        ClientSatSilhouette.render(event);
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || OrbitView.sats().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float night = Mth.clamp((level.getStarBrightness(pt) - 0.05F) * 3.0F, 0.0F, 1.0F);
        if (night <= 0.0F) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double dist = Math.min(DIST, mc.gameRenderer.getDepthFar() * 0.8);
        long now = OrbitView.now();
        double tick = level.getGameTime() + pt;
        BufferBuilder bb = null;
        for (OrbitMath.Sat s : OrbitView.sats()) {
            if (now >= s.endMs() || !OrbitMath.isOverhead(s, cam.x, cam.z, now)) continue;
            double[] sky = OrbitMath.sky(s, cam.x, cam.z, now);
            if (sky[1] < 5.0) continue;
            float[] col = colour(s.type());

            boolean flash = ((long) tick + s.id() * 11L) % 30L < 4L;
            float a = night * (flash ? 1.0F : 0.55F), h = (float) (dist / DIST) * (flash ? FLASH_SIZE : SIZE);
            if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float cx = (float) (sky[2] * dist), cy = (float) (sky[3] * dist), cz = (float) (sky[4] * dist);
            float lx = left.x * h, ly = left.y * h, lz = left.z * h, ux = up.x * h, uy = up.y * h, uz = up.z * h;
            bb.addVertex(cx - lx - ux, cy - ly - uy, cz - lz - uz).setColor(col[0], col[1], col[2], a);
            bb.addVertex(cx + lx - ux, cy + ly - uy, cz + lz - uz).setColor(col[0], col[1], col[2], a);
            bb.addVertex(cx + lx + ux, cy + ly + uy, cz + lz + uz).setColor(col[0], col[1], col[2], a);
            bb.addVertex(cx - lx + ux, cy - ly + uy, cz - lz + uz).setColor(col[0], col[1], col[2], a);
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, true);
    }

    private static float[] colour(String type) {
        return switch (type) {
            case "nav" -> new float[] { 0.45F, 0.65F, 1.0F };
            case "relay" -> new float[] { 0.45F, 1.0F, 0.55F };
            case "interceptor" -> new float[] { 1.0F, 0.25F, 0.2F };
            case "designator" -> new float[] { 1.0F, 0.12F, 0.1F };
            default -> new float[] { 1.0F, 1.0F, 1.0F };
        };
    }
}
