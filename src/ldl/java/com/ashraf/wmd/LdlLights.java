package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class LdlLights implements DynamicLightsInitializer {

    private static final int MAX = 24;
    private static final double RANGE = 96.0, MOVE = 0.3;

    private static volatile DynamicLightBehaviorManager manager;

    public LdlLights() {}

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        manager = context.dynamicLightBehaviorManager();
        Wmd.LOGGER.info("[wmd] LambDynamicLights found: WMD's lights give block light while a shaderpack is on (client option lambDynamicLights)");
    }

    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager items) {}

    private static final Map<Object, Src> SRC = Statics.client(new HashMap<>());

    private record Cand(Object key, double x, double y, double z, double dx, double dy, double dz, boolean spot, float angle, int lum, double d2) {}

    static void sync(boolean want) {
        DynamicLightBehaviorManager m = manager;
        if (m == null) return;
        List<Cand> cands = new ArrayList<>();
        if (want) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            WmdLights.export((key, x, y, z, dx, dy, dz, spot, angle, reach, bright) -> {
                int lum = Mth.clamp(Math.round(Math.min(15.0F, reach) * Math.min(1.0F, bright)), 0, 15);
                if (lum <= 0) return;
                double d2 = cam.distanceToSqr(x, y, z);
                if (d2 > RANGE * RANGE) return;
                cands.add(new Cand(key, x, y, z, dx, dy, dz, spot, angle, lum, d2));
            });
            cands.sort((a, b) -> Double.compare(a.d2(), b.d2()));
            if (cands.size() > MAX) cands.subList(MAX, cands.size()).clear();
        }
        java.util.Set<Object> keep = new java.util.HashSet<>();
        for (Cand c : cands) {
            Src s = SRC.get(c.key());
            if (s == null) {
                s = new Src();
                s.set(c);
                SRC.put(c.key(), s);
                m.add(s);
            } else s.set(c);
            keep.add(c.key());
        }
        Iterator<Map.Entry<Object, Src>> it = SRC.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, Src> e = it.next();
            if (keep.contains(e.getKey())) continue;
            e.getValue().removed = true;
            m.remove(e.getValue());
            it.remove();
        }
    }

    private static final class Src implements DynamicLightBehavior {
        private record State(double x, double y, double z, double dx, double dy, double dz, boolean spot, double cos, int lum) {}

        private volatile State st;
        private volatile boolean changed = true;
        volatile boolean removed;

        void set(Cand c) {
            State o = st;
            double n = Math.sqrt(c.dx() * c.dx() + c.dy() * c.dy() + c.dz() * c.dz());
            boolean spot = c.spot() && n > 1.0E-6;
            double dx = spot ? c.dx() / n : 0.0, dy = spot ? c.dy() / n : 0.0, dz = spot ? c.dz() / n : 0.0;
            State s = new State(c.x(), c.y(), c.z(), dx, dy, dz, spot, Math.cos(Math.min(Math.PI, c.angle())), c.lum());
            if (o == null || o.lum() != s.lum() || o.spot() != s.spot()
                    || Math.abs(o.x() - s.x()) + Math.abs(o.y() - s.y()) + Math.abs(o.z() - s.z()) > MOVE
                    || spot && o.dx() * dx + o.dy() * dy + o.dz() * dz < 0.995) {
                st = s;
                changed = true;
            }
        }

        @Override
        public double lightAtPos(BlockPos pos, double falloff) {
            State s = st;
            if (s == null) return 0.0;
            double px = pos.getX() + 0.5 - s.x(), py = pos.getY() + 0.5 - s.y(), pz = pos.getZ() + 0.5 - s.z();
            double d = Math.sqrt(px * px + py * py + pz * pz);
            if (s.spot() && d > 0.75 && (px * s.dx() + py * s.dy() + pz * s.dz()) / d < s.cos()) return 0.0;
            return s.lum() - d * falloff;
        }

        @Override
        public BoundingBox getBoundingBox() {
            State s = st;
            if (s == null) return new BoundingBox(0, 0, 0, 0, 0, 0);
            double ex = s.x() + s.dx() * s.lum(), ey = s.y() + s.dy() * s.lum(), ez = s.z() + s.dz() * s.lum();
            return new BoundingBox(Mth.floor(Math.min(s.x(), ex)), Mth.floor(Math.min(s.y(), ey)), Mth.floor(Math.min(s.z(), ez)),
                    Mth.ceil(Math.max(s.x(), ex)), Mth.ceil(Math.max(s.y(), ey)), Mth.ceil(Math.max(s.z(), ez)));
        }

        @Override
        public boolean hasChanged() {
            boolean c = changed;
            changed = false;
            return c;
        }

        @Override
        public boolean isRemoved() { return removed; }
    }
}
