package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientDomeMarkers {

    private ClientDomeMarkers() {}

    private static final double DRAW_RANGE = 640.0;
    private static final float PILLAR_H = 48.0F, PILLAR_W = 0.45F;

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { DomeMarkersPayload.clear(); }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var domes = DomeMarkersPayload.view();
        if (domes.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double tt = level.getGameTime() + pt;
        boolean blink = ((long) (tt / 10.0)) % 2 == 0;
        BufferBuilder bb = null;
        for (DomeMarkersPayload.Entry d : domes) {
            for (int i = 0; i < IronDome.MARKERS; i++) {
                int[] m = IronDome.marker(i, d.cx(), d.cz(), d.r());
                double dx = m[0] + 0.5 - cam.x, dz = m[1] + 0.5 - cam.z;
                if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) continue;
                double gy = level.hasChunkAt(new BlockPos(m[0], level.getMinBuildHeight(), m[1]))
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, m[0], m[1]) : cam.y - 2.0;
                boolean ok = (d.okMask() >> i & 1) != 0;
                float r = ok ? 0.25F : 1.0F, g = ok ? 0.65F : 0.22F, b = ok ? 1.0F : 0.18F;
                float a = ok ? 0.55F : (d.active() || blink ? 0.6F : 0.25F);
                float pulse = 0.85F + 0.15F * (float) Math.sin(tt * 0.12 + i);
                if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                float x = (float) dx, y = (float) (gy - cam.y), z = (float) dz;
                if (ClientConfig.domePillars()) pillar(bb, x, y, z, PILLAR_W, PILLAR_H, r, g, b, a * pulse);
                ring(bb, x, y + 0.08F, z, Balance.DOME_MARKER_TOL + 0.5F, (float) (tt * 0.02 + i), r, g, b, a * 0.8F);
            }
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, true);
    }

    private static void pillar(BufferBuilder bb, float x, float y, float z, float w, float h, float r, float g, float b, float a) {
        for (int k = 0; k < 2; k++) {
            float ox = k == 0 ? w : 0.0F, oz = k == 0 ? 0.0F : w;
            bb.addVertex(x - ox, y, z - oz).setColor(r, g, b, a);
            bb.addVertex(x + ox, y, z + oz).setColor(r, g, b, a);
            bb.addVertex(x + ox, y + h, z + oz).setColor(r, g, b, 0.0F);
            bb.addVertex(x - ox, y + h, z - oz).setColor(r, g, b, 0.0F);
        }

        float c = w * 0.35F;
        for (int k = 0; k < 2; k++) {
            float ox = k == 0 ? c : 0.0F, oz = k == 0 ? 0.0F : c;
            bb.addVertex(x - ox, y, z - oz).setColor(1.0F, 1.0F, 1.0F, a);
            bb.addVertex(x + ox, y, z + oz).setColor(1.0F, 1.0F, 1.0F, a);
            bb.addVertex(x + ox, y + h * 0.35F, z + oz).setColor(r, g, b, 0.0F);
            bb.addVertex(x - ox, y + h * 0.35F, z - oz).setColor(r, g, b, 0.0F);
        }
    }

    private static void ring(BufferBuilder bb, float x, float y, float z, float rad, float turn, float r, float g, float b, float a) {
        int n = 12;
        float th = 0.12F;
        for (int i = 0; i < n; i++) {
            double a0 = turn + Math.PI * 2.0 * i / n, a1 = a0 + Math.PI * 2.0 / n * 0.6;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            bb.addVertex(x + c0 * (rad - th), y, z + s0 * (rad - th)).setColor(r, g, b, a);
            bb.addVertex(x + c1 * (rad - th), y, z + s1 * (rad - th)).setColor(r, g, b, a);
            bb.addVertex(x + c1 * (rad + th), y, z + s1 * (rad + th)).setColor(r, g, b, a);
            bb.addVertex(x + c0 * (rad + th), y, z + s0 * (rad + th)).setColor(r, g, b, a);
        }
    }
}
