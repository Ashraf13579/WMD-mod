package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientIon {

    private ClientIon() {}

    private static final Map<BlockPos, IonThrusterBlockEntity> LIVE = Statics.client(new HashMap<>());
    private static final Map<BlockPos, UUID> SHIP = Statics.client(new HashMap<>());
    private static Level seen;

    static void track(IonThrusterBlockEntity be) {
        BlockPos p = be.getBlockPos().immutable();
        if (LIVE.put(p, be) == null) SHIP.put(p, SableBridge.shipIdAt(be.getLevel(), p));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != seen) { seen = mc.level; LIVE.clear(); SHIP.clear(); }
        if (mc.level == null || LIVE.isEmpty()) return;
        LIVE.values().removeIf(be -> be.isRemoved() || be.getLevel() != mc.level);
        SHIP.keySet().retainAll(LIVE.keySet());
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Map.Entry<BlockPos, IonThrusterBlockEntity> e : LIVE.entrySet()) {
            IonThrusterBlockEntity be = e.getValue();
            if (!be.beamOn()) continue;
            UUID ship = SHIP.get(e.getKey());
            if (ship != null && ClientCloak.craftHidden(ship)) continue;
            any |= beam(bb, cam, be, pt, now);
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static boolean beam(BufferBuilder bb, Vec3 cam, IonThrusterBlockEntity be, float pt, double now) {
        float s = Mth.clamp(be.beamNow(pt), 0.0F, 1.0F);
        double ay = be.axisY(), z0 = IonThrusterBlockEntity.EXIT_Z + 0.02;
        double len = Balance.ION_BEAM_MIN + (Balance.ION_BEAM_MAX - Balance.ION_BEAM_MIN) * s;
        double y = Math.toRadians(be.yawNow(pt)), p = Math.toRadians(be.pitchNow(pt));

        double dx = -Math.sin(y), dy = -Math.sin(p), dz = Math.cos(y) * Math.cos(p);
        Level lv = be.getLevel();
        Vec3 a = world(be, lv, IonThrusterBlockEntity.AX, ay, z0, pt);
        Vec3 b = world(be, lv, IonThrusterBlockEntity.AX + dx * len, ay + dy * len, z0 + dz * len, pt);
        Vec3 side = world(be, lv, IonThrusterBlockEntity.AX + 1.0, ay, z0, pt).subtract(a), up = world(be, lv, IonThrusterBlockEntity.AX, ay + 1.0, z0, pt).subtract(a);
        if (a.distanceToSqr(cam) > 200.0 * 200.0) return false;
        Vec3 d = b.subtract(a);
        float flick = 0.88F + 0.12F * Mth.sin((float) (now * 1.7 + be.getBlockPos().asLong() % 13));

        ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 0.22 + 0.1 * s, 0.75F, 0.95F, 1.0F, 0.95F * s * flick, 0.05F * s);
        Vec3 m = a.add(d.scale(0.85));
        ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, m.x, m.y, m.z, 0.5 + 0.2 * s, 0.35F, 0.75F, 1.0F, 0.55F * s * flick, 0.0F);
        ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 1.15 + 0.3 * s, 0.18F, 0.5F, 1.0F, 0.22F * s, 0.0F);

        int n = 4;
        double drift = (now * (0.05 + 0.12 * s)) % 1.0;
        for (int i = 0; i < n; i++) {
            double f = (i + drift) / n;
            Vec3 c = a.add(d.scale(f));
            double r = 0.52 * (1.0 - 0.35 * f) + 0.06 * len / Balance.ION_BEAM_MAX * f;
            float al = (float) (0.75 * s * (1.0 - f) * flick);
            ring(bb, cam, c, side, up, r, al);
        }

        Vec3 g = world(be, lv, IonThrusterBlockEntity.AX, ay, z0 - 0.02, pt);
        ring(bb, cam, g, side, up, 0.45, 0.6F * s);
        ring(bb, cam, g, side, up, 0.3, 0.8F * s);
        return true;
    }

    private static void ring(BufferBuilder bb, Vec3 cam, Vec3 c, Vec3 side, Vec3 up, double r, float a) {
        if (a < 0.01F) return;
        Vec3 u = side.normalize(), v = up.normalize();
        Vec3 prev = null;
        for (int k = 0; k <= 16; k++) {
            double t = k * Math.PI * 2.0 / 16.0;
            Vec3 q = c.add(u.scale(Math.cos(t) * r)).add(v.scale(Math.sin(t) * r));
            if (prev != null) ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, q.x, q.y, q.z, 0.06, 0.55F, 0.9F, 1.0F, a, a);
            prev = q;
        }
    }

    private static Vec3 world(IonThrusterBlockEntity be, Level lv, double lx, double ly, double lz, float pt) {
        return ShipFrame.drawnWorld(lv, be.getBlockPos(), be.plotPoint(lx, ly, lz), pt);
    }
}
