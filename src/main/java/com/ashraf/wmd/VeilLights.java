package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
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

    private static final Map<Long, SpotEntry> SPOTS = Statics.client(new HashMap<>());

    private static final class SpotEntry {
        final LightRenderHandle<SpotLightData> handle;
        long seen;

        SpotEntry(LightRenderHandle<SpotLightData> handle) { this.handle = handle; }
    }
    private static final List<Flash> FLASHES = Statics.client(new ArrayList<>());

    private static final net.minecraft.resources.ResourceLocation BUFFER_HOLD = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lights_hold");
    private static final int BUFFER_HOLD_TICKS = 1200;
    private static boolean holding;
    private static long lastUsed;
    private static boolean droppedLogged;

    private static final int NEED = foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType.encode(
            foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType.ALBEDO, foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType.NORMAL);

    private static void hold(long gameTime) {
        lastUsed = gameTime;
        if (holding) {
            try { if ((VeilRenderSystem.renderer().getActiveBuffers() & NEED) == NEED) return; } catch (RuntimeException e) { return; }
            if (!droppedLogged) { droppedLogged = true; Wmd.LOGGER.info("[wmd] Veil dropped the albedo / normal buffers the lights need: asking again (logged once)"); }
        }
        try {
            VeilRenderSystem.renderer().enableBuffers(BUFFER_HOLD, foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType.ALBEDO,
                    foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType.NORMAL);
        } catch (RuntimeException ignored) {}
        holding = true;
    }

    private static void release() {
        if (!holding) return;
        holding = false;
        try { VeilRenderSystem.renderer().disableBuffers(BUFFER_HOLD); } catch (RuntimeException ignored) {}
    }

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
        hold(gameTime);
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

    static void spot(long key, double x, double y, double z, double dx, double dy, double dz, float r, float g, float b, float angle, float distance, float brightness, long gameTime) {
        hold(gameTime);
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

    static void flash(double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, int duration, long gameTime) {
        hold(gameTime);
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
        Iterator<SpotEntry> sp = SPOTS.values().iterator();
        while (sp.hasNext()) {
            SpotEntry e = sp.next();
            if (gameTime - e.seen > 5 || gameTime < e.seen) { e.handle.free(); sp.remove(); }
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
        if (holding && LIGHTS.isEmpty() && SPOTS.isEmpty() && FLASHES.isEmpty() && (gameTime - lastUsed > BUFFER_HOLD_TICKS || gameTime < lastUsed)) release();
    }

    private static boolean gridOk = true;
    static void prepareShadowGrid() {
        if (!gridOk) return;
        try {
            if (foundry.veil.impl.client.render.light.VoxelShadowGrid.getTextureId() != 0) return;
            com.mojang.blaze3d.platform.GlStateManager._glBindBuffer(0x88EC, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(0x0CF2, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(0x0CF3, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(0x0CF4, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(0x806D, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(0x806E, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(0x0CF5, 4);
            foundry.veil.impl.client.render.light.VoxelShadowGrid.setup();
        } catch (Throwable t) {
            gridOk = false;
            Wmd.LOGGER.warn("Veil shadow grid pre-create unavailable ({}); Veil makes it itself", t.toString());
        }
    }

    static void clear() {
        release();
        for (Entry e : LIGHTS.values()) e.handle.free();
        LIGHTS.clear();
        for (SpotEntry e : SPOTS.values()) e.handle.free();
        SPOTS.clear();
        for (Flash f : FLASHES) f.handle.free();
        FLASHES.clear();
    }
}
