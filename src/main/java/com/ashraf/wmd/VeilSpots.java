package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;

final class VeilSpots {

    private VeilSpots() {}

    private static final class SpotEntry {
        final LightRenderHandle<SpotLightData> handle;
        long seen;

        SpotEntry(LightRenderHandle<SpotLightData> handle) { this.handle = handle; }
    }

    private static final Map<Long, SpotEntry> SPOTS = Statics.client(new HashMap<>());

    static void spot(long key, double x, double y, double z, double dx, double dy, double dz, float r, float g, float b, float angle, float distance, float brightness, long gameTime) {
        SpotEntry e = SPOTS.get(key);
        if (e != null && !e.handle.isValid()) { SPOTS.remove(key); e = null; }
        SpotLightData d;
        if (e == null) {
            d = new SpotLightData();
            e = new SpotEntry(VeilRenderSystem.renderer().getLightRenderer().addLight(d));
            SPOTS.put(key, e);
        }
        d = e.handle.getLightData();
        d.getPositionMutable().set(x, y, z);
        org.joml.Vector3f dir = new org.joml.Vector3f((float) dx, (float) dy, (float) dz);
        if (dir.lengthSquared() > 1.0E-9F) {
            dir.normalize();
            org.joml.Vector3f up = Math.abs(dir.y) > 0.98F ? new org.joml.Vector3f(0.0F, 0.0F, 1.0F) : new org.joml.Vector3f(0.0F, 1.0F, 0.0F);
            d.getOrientationMutable().identity().lookAlong(dir.mul(-1.0F), up);
        }
        d.setAngle(angle);
        d.setDistance(distance);
        d.setColor(r, g, b);
        d.setBrightness(brightness);
        d.markDirty();
        e.handle.markDirty();
        e.seen = gameTime;
    }

    static void sweep(long gameTime) {
        Iterator<SpotEntry> sp = SPOTS.values().iterator();
        while (sp.hasNext()) {
            SpotEntry e = sp.next();
            if (gameTime - e.seen > 5 || gameTime < e.seen) { e.handle.free(); sp.remove(); }
        }
    }

    static boolean isEmpty() { return SPOTS.isEmpty(); }

    static void clear() {
        for (SpotEntry e : SPOTS.values()) e.handle.free();
        SPOTS.clear();
    }
}
