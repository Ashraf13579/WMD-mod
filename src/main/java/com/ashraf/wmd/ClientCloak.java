package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCloak {

    private ClientCloak() {}
    private static VertexBuffer reusedVb;

    private static final Map<UUID, Long> SHIPS = Statics.client(new HashMap<>()), PLAYERS = Statics.client(new HashMap<>());
    private static final Map<UUID, Long> SHIPS_LEFT = Statics.client(new HashMap<>()), PLAYERS_LEFT = Statics.client(new HashMap<>());

    private static volatile Set<UUID> hiddenShips = Set.of(), hiddenPlayers = Set.of();

    static void receive(CloakPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        apply(SHIPS, SHIPS_LEFT, new HashSet<>(p.ships()), now);
        apply(PLAYERS, PLAYERS_LEFT, new HashSet<>(p.players()), now);
        refresh(now);
    }

    private static void apply(Map<UUID, Long> in, Map<UUID, Long> left, Set<UUID> now, long t) {
        for (UUID id : now) if (!in.containsKey(id)) { in.put(id, t); left.remove(id); }
        in.keySet().removeIf(id -> { if (now.contains(id)) return false; left.put(id, t); return true; });
    }

    private static void refresh(long now) {
        Set<UUID> s = new HashSet<>(), p = new HashSet<>();
        for (Map.Entry<UUID, Long> e : SHIPS.entrySet()) if (now - e.getValue() >= Balance.CLOAK_SHIMMER_TICKS) s.add(e.getKey());
        for (Map.Entry<UUID, Long> e : PLAYERS.entrySet()) if (now - e.getValue() >= Balance.CLOAK_SHIMMER_TICKS) p.add(e.getKey());
        hiddenShips = s.isEmpty() ? Set.of() : s;
        hiddenPlayers = p.isEmpty() ? Set.of() : p;
        SHIPS_LEFT.values().removeIf(t -> now - t > Balance.CLOAK_SHIMMER_TICKS || now < t);
        PLAYERS_LEFT.values().removeIf(t -> now - t > Balance.CLOAK_SHIMMER_TICKS || now < t);
    }

    public static boolean anyCraftHidden() { return !hiddenShips.isEmpty(); }

    public static boolean craftHidden(UUID id) { Set<UUID> s = hiddenShips; return !s.isEmpty() && s.contains(id); }

    public static boolean playerHidden(UUID id) { Set<UUID> s = hiddenPlayers; return !s.isEmpty() && s.contains(id); }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { hiddenShips = Set.of(); hiddenPlayers = Set.of(); return; }
        if (SHIPS.isEmpty() && PLAYERS.isEmpty() && SHIPS_LEFT.isEmpty() && PLAYERS_LEFT.isEmpty() && hiddenShips.isEmpty() && hiddenPlayers.isEmpty()) return;
        refresh(level.getGameTime());
    }

    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (playerHidden(event.getEntity().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (SHIPS.isEmpty() && PLAYERS.isEmpty() && SHIPS_LEFT.isEmpty() && PLAYERS_LEFT.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int[] n = { 0 };
        for (Map.Entry<UUID, Long> e : SHIPS.entrySet()) { AABB b = craftBox(level, e.getKey()); if (b != null) draw(bb, cam, b, t - e.getValue(), t, false, n); }
        for (Map.Entry<UUID, Long> e : SHIPS_LEFT.entrySet()) { AABB b = craftBox(level, e.getKey()); if (b != null) draw(bb, cam, b, t - e.getValue(), t, true, n); }
        for (Map.Entry<UUID, Long> e : PLAYERS.entrySet()) { AABB b = playerBox(level, e.getKey(), pt); if (b != null) draw(bb, cam, b, t - e.getValue(), t, false, n); }
        for (Map.Entry<UUID, Long> e : PLAYERS_LEFT.entrySet()) { AABB b = playerBox(level, e.getKey(), pt); if (b != null) draw(bb, cam, b, t - e.getValue(), t, true, n); }
        MeshData md = bb.build();
        if (md == null || n[0] == 0) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (reusedVb == null) reusedVb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        reusedVb.bind();
        reusedVb.upload(md);
        reusedVb.drawWithShader(new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static AABB craftBox(ClientLevel level, UUID id) {
        double[] b = ShipFrame.craftBox(level, id);
        return b == null ? null : new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    private static AABB playerBox(ClientLevel level, UUID id, float pt) {
        Player p = level.getPlayerByUUID(id);
        if (p == null || p == Minecraft.getInstance().player) return null;
        Vec3 at = p.getPosition(pt);
        double w = p.getBbWidth() * 0.5 + 0.1;
        return new AABB(at.x - w, at.y, at.z - w, at.x + w, at.y + p.getBbHeight() + 0.1, at.z + w);
    }

    private static void draw(BufferBuilder bb, Vec3 cam, AABB b, double age, double t, boolean left, int[] n) {
        double sh = Balance.CLOAK_SHIMMER_TICKS;
        if (age < 0) age = 0;
        boolean shimmer = age < sh;
        if (left && !shimmer) return;
        double d = Math.sqrt(b.distanceToSqr(cam));
        if (!shimmer && d > Balance.CLOAK_HAZE_R) return;
        int bands = shimmer ? 7 : 4;
        double h = b.getYsize();
        float a = shimmer ? (float) (0.45 * Math.sin(Math.PI * Math.min(1.0, age / sh))) + 0.12F
                : (float) (0.09 * (1.0 - d / Balance.CLOAK_HAZE_R));
        for (int i = 0; i < bands; i++) {
            double f = shimmer ? ((age / sh) * 1.6 + i / (double) bands) % 1.0 : (i + 0.5) / bands + 0.06 * Math.sin(t * 0.07 + i * 1.9);
            double y = b.minY + h * Math.max(0.0, Math.min(1.0, f));
            double thick = shimmer ? Math.max(0.08, h * 0.035) : Math.max(0.05, h * 0.02);
            double wob = (shimmer ? 0.08 : 0.15) * Math.sin(t * 0.31 + i * 2.3);
            band(bb, cam, b.inflate(0.06 + wob * 0.5), y, thick, wob, 0.55F, 0.85F, 1.0F, a);
            n[0]++;
        }
    }

    private static void band(BufferBuilder bb, Vec3 cam, AABB b, double y, double th, double wob, float r, float g, float bl, float a) {
        double x0 = b.minX - wob - cam.x, x1 = b.maxX + wob - cam.x, z0 = b.minZ + wob - cam.z, z1 = b.maxZ - wob - cam.z;
        float y0 = (float) (y - th * 0.5 - cam.y), y1 = (float) (y + th * 0.5 - cam.y);
        double[][] c = { { x0, z0 }, { x1, z0 }, { x1, z1 }, { x0, z1 } };
        for (int k = 0; k < 4; k++) {
            double[] p = c[k], q = c[(k + 1) % 4];
            bb.addVertex((float) p[0], y0, (float) p[1]).setColor(r, g, bl, 0.0F);
            bb.addVertex((float) q[0], y0, (float) q[1]).setColor(r, g, bl, 0.0F);
            bb.addVertex((float) q[0], y1, (float) q[1]).setColor(r, g, bl, a);
            bb.addVertex((float) p[0], y1, (float) p[1]).setColor(r, g, bl, a);
        }
    }
}
