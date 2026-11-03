package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;

final class VeilLights {

    private VeilLights() {}

    private static final class Entry {
        final LightRenderHandle<PointLightData> handle;
        long seen;

        Entry(LightRenderHandle<PointLightData> handle) { this.handle = handle; }
    }

    private static final class Flash {
        final float brightness, radius;
        final long start;
        final int duration;
        final LightRenderHandle<PointLightData> handle;

        Flash(LightRenderHandle<PointLightData> handle, float brightness, float radius, long start, int duration) {
            this.handle = handle; this.brightness = brightness; this.radius = radius; this.start = start; this.duration = duration;
        }
    }

    private static final Map<Long, Entry> LIGHTS = Statics.client(new HashMap<>());
    private static final List<Flash> FLASHES = Statics.client(new ArrayList<>());

    private static LightRenderHandle<PointLightData> add(double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter) {
        PointLightData data = new PointLightData();
        data.setPosition(x, y, z);
        data.setRadius(radius);
        data.setColor(r, g, b);
        data.setBrightness(brightness);
        if (inscatter > 0.0F) data.setInscatteringStrength(inscatter);
        return VeilRenderSystem.renderer().getLightRenderer().addLight(data);
    }

    static void glow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, long gameTime) {
        Entry e = LIGHTS.get(key);
        if (e != null && !e.handle.isValid()) { LIGHTS.remove(key); e = null; }
        if (e == null) {
            e = new Entry(add(x, y, z, r, g, b, radius, brightness, inscatter));
            LIGHTS.put(key, e);
        } else {
            PointLightData d = e.handle.getLightData();
            d.setPosition(x, y, z);
            d.setRadius(radius);
            d.setColor(r, g, b);
            d.setBrightness(brightness);
            d.markDirty();
            e.handle.markDirty();
        }
        e.seen = gameTime;
    }

    static void flash(double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, int duration, long gameTime) {
        FLASHES.add(new Flash(add(x, y, z, r, g, b, radius, brightness, inscatter), brightness, radius, gameTime, duration));
    }

    static void sweep(long gameTime) {
        Iterator<Entry> it = LIGHTS.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (gameTime - e.seen > 5 || gameTime < e.seen) {
                e.handle.free();
                it.remove();
            }
        }
        Iterator<Flash> fl = FLASHES.iterator();
        while (fl.hasNext()) {
            Flash f = fl.next();
            long age = gameTime - f.start;
            if (age < 0 || age >= f.duration || !f.handle.isValid()) {
                f.handle.free();
                fl.remove();
                continue;
            }
            float k = 1.0F - age / (float) f.duration;
            PointLightData d = f.handle.getLightData();
            d.setBrightness(f.brightness * k * k);
            d.setRadius(f.radius * (0.6F + 0.4F * k));
            d.markDirty();
            f.handle.markDirty();
        }
    }

    static void clear() {
        for (Entry e : LIGHTS.values()) e.handle.free();
        LIGHTS.clear();
        for (Flash f : FLASHES) f.handle.free();
        FLASHES.clear();
    }
}
