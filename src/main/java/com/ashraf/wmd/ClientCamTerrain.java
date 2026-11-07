package com.ashraf.wmd;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCamTerrain {

    private ClientCamTerrain() {}

    static final int MAX = 2000;
    static final double AHEAD = 96.0, DRAW = 480.0;

    private static final class Chunk {
        final int cx, cz; final short[] h; final int[] rgb; VertexBuffer vb; boolean built;
        Chunk(CamChunkPayload p) { cx = p.cx(); cz = p.cz(); h = p.h(); rgb = p.rgb(); }
    }

    private static final Map<Long, Chunk> CACHE = new LinkedHashMap<>(256, 0.75F, false);
    private static boolean active;

    private static int drawn;

    public static int cached() { return CACHE.size(); }
    public static int drawn() { return drawn; }

    public static void onChunk(CamChunkPayload p) {
        if (!active) return;
        Chunk old = CACHE.put(ChunkPos.asLong(p.cx(), p.cz()), new Chunk(p));
        if (old != null && old.vb != null) old.vb.close();
        if (CACHE.size() > MAX) {
            Iterator<Chunk> it = CACHE.values().iterator();
            Chunk c = it.next();
            if (c.vb != null) c.vb.close();
            it.remove();
        }
    }

    private static void clear() {
        for (Chunk c : CACHE.values()) if (c.vb != null) c.vb.close();
        CACHE.clear();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean cut = false;
        boolean cctv = mc.level != null && ClientCctv.active();
        boolean watching = mc.level != null && ClientMissileCam.watching() > 0 || cut || cctv;
        if (!watching) {
            if (active) { active = false; clear(); if (mc.getConnection() != null) PacketDistributor.sendToServer(new CamTerrainRequestPayload(0, 0, true)); }
            return;
        }
        active = true;
        if (mc.level.getGameTime() % 20 != 0) return;
        if (cut) { Vec3 f = ClientCutscene.focus(); PacketDistributor.sendToServer(new CamTerrainRequestPayload((int) Math.floor(f.x), (int) Math.floor(f.z), false)); return; }
        if (cctv) { Vec3 l = ClientCctv.camPos(); PacketDistributor.sendToServer(new CamTerrainRequestPayload((int) Math.floor(l.x), (int) Math.floor(l.z), false)); return; }
        double yaw = Math.toRadians(ClientMissileCam.camYaw());
        Vec3 c = ClientMissileCam.camPos();
        PacketDistributor.sendToServer(new CamTerrainRequestPayload((int) Math.floor(c.x - Math.sin(yaw) * AHEAD), (int) Math.floor(c.z + Math.cos(yaw) * AHEAD), false));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || !active || CACHE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        double dh = DhBridge.farRadius(), dh2 = dh * dh;
        double px = mc.player != null ? mc.player.getX() : cam.x, pz = mc.player != null ? mc.player.getZ() : cam.z;
        int n = 0;
        for (Chunk c : java.util.List.copyOf(CACHE.values())) {

            if (mc.level.getChunkSource().getChunk(c.cx, c.cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null) continue;
            double mx = c.cx * 16 + 8 - cam.x, mz = c.cz * 16 + 8 - cam.z;
            if (mx * mx + mz * mz > DRAW * DRAW) continue;
            double qx = c.cx * 16 + 8 - px, qz = c.cz * 16 + 8 - pz;
            if (dh > 0.0 && qx * qx + qz * qz < dh2) continue;
            if (!c.built) build(c);
            if (c.vb == null) continue;
            Matrix4f mv = new Matrix4f(event.getModelViewMatrix()).translate((float) (c.cx * 16 - cam.x), (float) -cam.y, (float) (c.cz * 16 - cam.z));
            c.vb.bind();
            c.vb.drawWithShader(mv, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            n++;
        }
        drawn = n;
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    private static void build(Chunk c) {
        c.built = true;
        int n = CamTerrain.CELLS, w = CamTerrain.CELL;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Chunk east = CACHE.get(ChunkPos.asLong(c.cx + 1, c.cz)), west = CACHE.get(ChunkPos.asLong(c.cx - 1, c.cz)),
              south = CACHE.get(ChunkPos.asLong(c.cx, c.cz + 1)), north = CACHE.get(ChunkPos.asLong(c.cx, c.cz - 1));
        for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) {
            int idx = k * n + i;
            if (c.h[idx] == Short.MIN_VALUE) continue;
            int col = c.rgb[idx];
            float r = ((col >> 16) & 255) / 255.0F, g = ((col >> 8) & 255) / 255.0F, b = (col & 255) / 255.0F;
            if ((col >> 24 & 1) != 0) { r = r * 0.6F + 0.05F; g = g * 0.6F + 0.15F; b = b * 0.6F + 0.4F; }
            float top = c.h[idx], x0 = i * w, z0 = k * w;
            int nh = k > 0 ? c.h[idx - n] : north != null ? north.h[(n - 1) * n + i] : c.h[idx];
            float sh = c.h[idx] > nh ? 1.0F : c.h[idx] < nh ? 0.82F : 0.92F;
            v(bb, x0, top, z0, r * sh, g * sh, b * sh); v(bb, x0, top, z0 + w, r * sh, g * sh, b * sh);
            v(bb, x0 + w, top, z0 + w, r * sh, g * sh, b * sh); v(bb, x0 + w, top, z0, r * sh, g * sh, b * sh);
            float ex = nb(c, i + 1, k, east, 0, k), wx = nb(c, i - 1, k, west, n - 1, k), sz = nb(c, i, k + 1, south, i, 0), nz = nb(c, i, k - 1, north, i, n - 1);
            side(bb, top, ex, x0 + w, z0 + w, x0 + w, z0, r, g, b, 0.72F);
            side(bb, top, wx, x0, z0, x0, z0 + w, r, g, b, 0.68F);
            side(bb, top, sz, x0, z0 + w, x0 + w, z0 + w, r, g, b, 0.62F);
            side(bb, top, nz, x0 + w, z0, x0, z0, r, g, b, 0.78F);
        }
        MeshData md = bb.build();
        if (md == null) return;
        c.vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        c.vb.bind();
        c.vb.upload(md);
        VertexBuffer.unbind();
    }

    private static float nb(Chunk c, int i, int k, Chunk other, int oi, int ok) {
        int n = CamTerrain.CELLS;
        if (i >= 0 && k >= 0 && i < n && k < n) return c.h[k * n + i];
        if (other != null && other.h[ok * n + oi] != Short.MIN_VALUE) return other.h[ok * n + oi];
        return c.h[Math.max(0, Math.min(n - 1, k)) * n + Math.max(0, Math.min(n - 1, i))] - 6;
    }

    private static void side(BufferBuilder bb, float top, float low, float ax, float az, float bx, float bz, float r, float g, float b, float sh) {
        if (low >= top) return;
        v(bb, ax, top, az, r * sh, g * sh, b * sh); v(bb, bx, top, bz, r * sh, g * sh, b * sh);
        v(bb, bx, low, bz, r * sh * 0.8F, g * sh * 0.8F, b * sh * 0.8F); v(bb, ax, low, az, r * sh * 0.8F, g * sh * 0.8F, b * sh * 0.8F);
    }

    private static void v(BufferBuilder bb, float x, float y, float z, float r, float g, float b) { bb.addVertex(x, y, z).setColor(r, g, b, 1.0F); }
}
