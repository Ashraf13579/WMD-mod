package com.ashraf.wmd;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class DhFar {

    private DhFar() {}

    private static final Map<Long, IDhApiRenderableBoxGroup> GROUPS = new HashMap<>();
    private static IDhApiCustomRenderRegister register;

    private static final Color BODY = new Color(58, 62, 68), NOSE = new Color(175, 182, 192);

    static boolean hidden;

    private static boolean bound;

    static void hideFar(boolean hide) {
        if (hide && !bound) {
            bound = true;
            DhApi.events.bind(com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent.class,
                    new com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent() {
                        @Override
                        public void beforeRender(com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam<com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam> e) {
                            if (hidden) e.cancelEvent();
                        }
                    });

            DhApi.events.bind(com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent.class,
                    new com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent() {
                        @Override
                        public void beforeRender(com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam<com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam> e) {
                            if (hidden) e.cancelEvent();
                        }
                    });
            DhApi.events.bind(com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent.class,
                    new com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent() {
                        @Override
                        public void beforeRender(com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam<com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam> e) {
                            if (hidden) e.cancelEvent();
                        }
                    });
        }
        hidden = hide;
    }

    private static int clearFbo;
    private static boolean clearBroken;

    static void clearStale() {
        if (!hidden || clearBroken) return;
        com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy rp = DhApi.Delayed.renderProxy;
        if (rp == null || rp.getRenderingApi() != com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi.OPEN_GL) return;
        com.seibel.distanthorizons.api.objects.DhApiResult<Integer> c = rp.getDhColorTextureGlId();
        if (c == null || !c.success || c.payload == null || c.payload <= 0) return;
        int tex = c.payload;
        org.lwjgl.opengl.GL11.glGetError();
        if (org.lwjgl.opengl.GL.getCapabilities().glClearTexImage != 0L) {
            org.lwjgl.opengl.GL44.glClearTexImage(tex, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_FLOAT, new float[] { 0.0F, 0.0F, 0.0F, 0.0F });
        } else {
            int prev = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
            float[] cc = new float[4];
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE, cc);
            if (clearFbo == 0) clearFbo = com.mojang.blaze3d.platform.GlStateManager.glGenFramebuffers();
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, clearFbo);
            com.mojang.blaze3d.platform.GlStateManager._glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, tex, 0);
            com.mojang.blaze3d.platform.GlStateManager._clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            com.mojang.blaze3d.platform.GlStateManager._clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, false);
            com.mojang.blaze3d.platform.GlStateManager._clearColor(cc[0], cc[1], cc[2], cc[3]);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, prev);
        }
        int err = org.lwjgl.opengl.GL11.glGetError();
        if (err != 0) {
            clearBroken = true;
            com.ashraf.wmd.Wmd.LOGGER.warn("[cutscene] could not clear Distant Horizons' colour picture (GL error {}): its fade may show its old view", err);
        }
    }

    static void tick(ClientLevel level, Vec3 cam) {
        IDhApiWorldProxy proxy = DhApi.Delayed.worldProxy;
        if (DhApi.Delayed.customRenderObjectFactory == null || proxy == null || !proxy.worldLoaded()) {
            reset();
            return;
        }
        IDhApiCustomRenderRegister reg = findRegister(proxy, level);
        if (reg == null) {
            reset();
            return;
        }
        if (reg != register) {
            GROUPS.clear();
            register = reg;
        }
        DhBridge.ready = true;
        tickClouds(level);

        double near = ClientFlights.nearLimit();
        long now = level.getGameTime();
        for (Flight f : ClientFlights.FLIGHTS.values()) {
            boolean flying = now >= f.start() && now <= f.endTime();
            double d = f.position(now).distanceTo(cam);
            IDhApiRenderableBoxGroup g = GROUPS.get(f.id());
            if (flying && d > near) {
                if (g == null) GROUPS.put(f.id(), create(f, now, cam));
            } else if (g != null && (!flying || d < near * 0.95)) {
                register.remove(g.getId());
                GROUPS.remove(f.id());
            }
        }
        tickIntercepts(level, cam, near, now);
        tickMirrors();
        DhShells.tick(register, cam, Math.min(near, 512.0), now);
        GROUPS.entrySet().removeIf(e -> {
            if (ClientFlights.FLIGHTS.containsKey(e.getKey())) return false;
            register.remove(e.getValue().getId());
            return true;
        });
    }

    static void reset() {
        if (register != null) {
            for (Cloud c : CLOUDS) {
                try { register.remove(c.group.getId()); } catch (RuntimeException ignored) { }
            }
            for (IDhApiRenderableBoxGroup g : GROUPS.values()) {
                try { register.remove(g.getId()); } catch (RuntimeException ignored) { }
            }
        }
        if (register != null) {
            for (IDhApiRenderableBoxGroup g : SHOT_GROUPS.values()) { try { register.remove(g.getId()); } catch (RuntimeException ignored) { } }
            for (IDhApiRenderableBoxGroup g : BURST_GROUPS.values()) { try { register.remove(g.getId()); } catch (RuntimeException ignored) { } }
        }
        if (register != null) for (IDhApiRenderableBoxGroup g : MIRROR_GROUPS.values()) { try { register.remove(g.getId()); } catch (RuntimeException ignored) { } }
        MIRROR_GROUPS.clear();
        SHOT_GROUPS.clear();
        BURST_GROUPS.clear();
        DhShells.reset(register);
        GROUPS.clear();
        CLOUDS.clear();
        register = null;
        DhBridge.ready = false;
    }

    private static IDhApiCustomRenderRegister findRegister(IDhApiWorldProxy proxy, ClientLevel level) {
        IDhApiLevelWrapper w;
        try {
            w = proxy.getSinglePlayerLevel();
            if (w == null) {
                for (IDhApiLevelWrapper c : proxy.getAllLoadedLevelWrappers()) {
                    if (c.getWrappedMcObject() == level) { w = c; break; }
                }
            }
        } catch (IllegalStateException e) {
            return null;
        }
        return w == null ? null : w.getRenderRegister();
    }

    private static IDhApiRenderableBoxGroup create(Flight f, long now, Vec3 cam) {
        Vec3 p = f.position(now);
        IDhApiRenderableBoxGroup g = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(
                Wmd.MODID + ":missile", new DhApiVec3d(p.x, p.y, p.z), build(f, now, p, cam));
        g.setSkyLight(15);
        g.setBlockLight(6);
        final long id = f.id();
        g.setPreRenderFunc(param -> update(id, g));
        register.add(g);
        return g;
    }

    private static void update(long id, IDhApiRenderableBoxGroup g) {
        Minecraft mc = Minecraft.getInstance();
        Flight f = ClientFlights.FLIGHTS.get(id);
        if (f == null || mc.level == null) { g.setActive(false); return; }
        double t = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        if (t < f.start() || t > f.endTime()) { g.setActive(false); return; }
        Vec3 p = f.position(t);
        g.setActive(true);
        g.setOriginBlockPos(new DhApiVec3d(p.x, p.y, p.z));
        List<DhApiRenderableBox> boxes = build(f, t, p, mc.gameRenderer.getMainCamera().getPosition());
        g.clear();
        g.addAll(boxes);
        g.triggerBoxChange();
    }

    private static DhApiRenderableBox cube(Vec3 c, double size, Color color, EDhApiBlockMaterial material) {
        double h = size * 0.5;
        return new DhApiRenderableBox(new DhApiVec3d(c.x - h, c.y - h, c.z - h), new DhApiVec3d(c.x + h, c.y + h, c.z + h), color, material);
    }

    private static List<DhApiRenderableBox> build(Flight f, double t, Vec3 p, Vec3 cam) {
        List<DhApiRenderableBox> boxes = new ArrayList<>(64);
        double s = Mth.clamp(p.distanceTo(cam) / 2500.0, 0.6, 1.2);
        Vec3 step = f.position(t + 1.0).subtract(p);
        double speed = step.length();
        Vec3 dir = speed < 1.0E-6 ? new Vec3(0, 1, 0) : step.scale(1.0 / speed);

        double length = Missiles.height(f.tier()) * s;
        double w = Missiles.radiusUnits(f.tier()) / 8.0 * s;
        int n = Math.max(4, (int) Math.round(length / w));
        double gap = length / n;
        for (int i = 0; i < n; i++) {
            Vec3 c = dir.scale((i - (n - 1) / 2.0) * gap);
            boxes.add(cube(c, w, i == n - 1 ? NOSE : BODY, EDhApiBlockMaterial.METAL));
        }

        Color[] flame = { new Color(255, 190, 80), new Color(255, 125, 35), new Color(200, 65, 20) };
        for (int k = 0; k < flame.length; k++) {
            Vec3 c = dir.scale(-(length * 0.5 + (k + 0.6) * w * 0.8));
            boxes.add(cube(c, w * (1.0 - k * 0.18), flame[k], EDhApiBlockMaterial.ILLUMINATED));
        }

        double dt = Mth.clamp(w * 0.9 / Math.max(speed, 0.05), 1.0, 12.0);
        for (int k = 1; k <= 28; k++) {
            double tk = t - k * dt - 1.0;
            if (tk < f.start()) break;
            Vec3 c = f.position(tk).subtract(p);
            int alpha = Math.max(10, 120 - k * 4);
            boxes.add(cube(c, w * (0.45 + k * 0.008), new Color(170, 170, 176, alpha), EDhApiBlockMaterial.STONE));
        }
        return boxes;
    }

    private static final Map<Long, IDhApiRenderableBoxGroup> SHOT_GROUPS = new HashMap<>();
    private static final Map<ClientIntercepts.Burst, IDhApiRenderableBoxGroup> BURST_GROUPS = new HashMap<>();
    private static final long BURST_TICKS = 50;

    private static void tickIntercepts(ClientLevel level, Vec3 cam, double near, long now) {
        for (ClientIntercepts.Shot s : ClientIntercepts.SHOTS.values()) {
            boolean flying = now >= s.start() && now <= s.start() + s.duration();
            boolean far = ClientIntercepts.position(s, now).distanceTo(cam) > near;
            IDhApiRenderableBoxGroup g = SHOT_GROUPS.get(s.id());
            if (flying && far && g == null) {
                Vec3 p = ClientIntercepts.position(s, now);
                g = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(Wmd.MODID + ":interceptor", new DhApiVec3d(p.x, p.y, p.z), buildShot(s, now, p, cam));
                g.setSkyLight(15);
                g.setBlockLight(6);
                final IDhApiRenderableBoxGroup gg = g;
                final long id = s.id();
                g.setPreRenderFunc(param -> updateShot(id, gg));
                register.add(g);
                SHOT_GROUPS.put(s.id(), g);
            } else if (g != null && (!flying || !far)) {
                register.remove(g.getId());
                SHOT_GROUPS.remove(s.id());
            }
        }
        SHOT_GROUPS.entrySet().removeIf(e -> {
            if (ClientIntercepts.SHOTS.containsKey(e.getKey())) return false;
            register.remove(e.getValue().getId());
            return true;
        });
        for (ClientIntercepts.Burst b : ClientIntercepts.BURSTS) {
            if (now - b.tick() > BURST_TICKS || BURST_GROUPS.containsKey(b)) continue;
            Vec3 c = new Vec3(b.x(), b.y(), b.z());
            IDhApiRenderableBoxGroup g = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(Wmd.MODID + ":burst", new DhApiVec3d(c.x, c.y, c.z), buildBurst(b, 0, cam));
            g.setSkyLight(15);
            g.setBlockLight(15);
            g.setPreRenderFunc(param -> updateBurst(b, g));
            register.add(g);
            BURST_GROUPS.put(b, g);
        }
        BURST_GROUPS.entrySet().removeIf(e -> {
            if (now - e.getKey().tick() <= BURST_TICKS + 5) return false;
            register.remove(e.getValue().getId());
            return true;
        });
    }

    private static void updateShot(long id, IDhApiRenderableBoxGroup g) {
        Minecraft mc = Minecraft.getInstance();
        ClientIntercepts.Shot s = ClientIntercepts.SHOTS.get(id);
        if (s == null || mc.level == null) { g.setActive(false); return; }
        double t = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        if (t < s.start() || t > s.start() + s.duration()) { g.setActive(false); return; }
        Vec3 p = ClientIntercepts.position(s, t);
        g.setActive(true);
        g.setOriginBlockPos(new DhApiVec3d(p.x, p.y, p.z));
        List<DhApiRenderableBox> boxes = buildShot(s, t, p, mc.gameRenderer.getMainCamera().getPosition());
        g.clear();
        g.addAll(boxes);
        g.triggerBoxChange();
    }

    private static List<DhApiRenderableBox> buildShot(ClientIntercepts.Shot s, double t, Vec3 p, Vec3 cam) {
        List<DhApiRenderableBox> boxes = new ArrayList<>(40);
        double sc = Mth.clamp(p.distanceTo(cam) / 2000.0, 0.8, 2.0);
        Vec3 step = ClientIntercepts.position(s, t + 1.0).subtract(p);
        double speed = step.length();
        Vec3 dir = speed < 1.0E-6 ? new Vec3(0, 1, 0) : step.scale(1.0 / speed);
        double w = 1.2 * sc;
        for (int i = 0; i < 3; i++) boxes.add(cube(dir.scale((i - 1) * w), w, i == 2 ? NOSE : new Color(235, 238, 242), EDhApiBlockMaterial.METAL));
        for (int k = 0; k < 3; k++) boxes.add(cube(dir.scale(-(w * 2.0 + k * w * 0.8)), w * (1.0 - k * 0.2), k == 0 ? new Color(255, 200, 90) : new Color(255, 130, 40), EDhApiBlockMaterial.ILLUMINATED));
        double dt = Mth.clamp(w * 0.9 / Math.max(speed, 0.05), 1.0, 10.0);
        for (int k = 1; k <= 24; k++) {
            double tk = t - k * dt - 1.0;
            if (tk < s.start()) break;
            Vec3 c = ClientIntercepts.position(s, tk).subtract(p);
            boxes.add(cube(c, w * (0.5 + k * 0.01), new Color(200, 200, 206, Math.max(10, 130 - k * 5)), EDhApiBlockMaterial.STONE));
        }
        return boxes;
    }

    private static void updateBurst(ClientIntercepts.Burst b, IDhApiRenderableBoxGroup g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { g.setActive(false); return; }
        double age = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false) - b.tick();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean far = cam.distanceTo(new Vec3(b.x(), b.y(), b.z())) > Math.max(ClientFlights.nearLimit(), ClientBlastGeo.FAR_VIEW);
        g.setActive(far && age < BURST_TICKS);
        if (!far || age >= BURST_TICKS) return;
        List<DhApiRenderableBox> boxes = buildBurst(b, age, cam);
        g.clear();
        g.addAll(boxes);
        g.triggerBoxChange();
    }

    private static List<DhApiRenderableBox> buildBurst(ClientIntercepts.Burst b, double age, Vec3 cam) {
        List<DhApiRenderableBox> boxes = new ArrayList<>(60);
        double dist = cam.distanceTo(new Vec3(b.x(), b.y(), b.z()));
        double sc = Mth.clamp(dist / 1500.0, 1.0, 4.0);
        double u = Mth.clamp(age / BURST_TICKS, 0.0, 1.0);
        double radius = (b.kill() ? 22.0 : 9.0) * sc * (1.0 - Math.pow(1.0 - Math.min(1.0, u * 3.0), 2));
        double size = (b.kill() ? 12.0 : 5.0) * sc * (1.0 - u * 0.7);
        int alpha = (int) (255 * (1.0 - u * 0.8));
        for (int i = 0; i < 30; i++) {
            double a = hash(i, 1, 7, 91), c = hash(i, 2, 7, 91) * 2 - 1, d = hash(i, 3, 7, 91);
            double th = a * Math.PI * 2, rr = Math.sqrt(1 - c * c);
            Vec3 pos = new Vec3(Math.cos(th) * rr, c, Math.sin(th) * rr).scale(radius * (0.4 + 0.6 * d));
            Color col = u < 0.35 ? new Color(255, 245, 210, alpha) : u < 0.7 ? new Color(255, 160, 60, alpha) : new Color(120, 110, 105, alpha);
            boxes.add(cube(pos, size * (0.6 + 0.6 * d), col, u < 0.7 ? EDhApiBlockMaterial.ILLUMINATED : EDhApiBlockMaterial.STONE));
        }
        boxes.add(cube(Vec3.ZERO, size * 1.4, new Color(255, 255, 235, alpha), EDhApiBlockMaterial.ILLUMINATED));
        return boxes;
    }

    private static final Map<Integer, IDhApiRenderableBoxGroup> MIRROR_GROUPS = new HashMap<>();

    private static void tickMirrors() {
        for (Map.Entry<Integer, Object[]> en : MirrorPayloads.BEAMS.entrySet()) {
            if ((long) en.getValue()[1] < 0 || MIRROR_GROUPS.containsKey(en.getKey())) continue;
            MirrorPayloads.Beam b = (MirrorPayloads.Beam) en.getValue()[0];
            final int slot = en.getKey();
            IDhApiRenderableBoxGroup g = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(
                    Wmd.MODID + ":mirror", new DhApiVec3d(b.x(), b.y(), b.z()), buildMirror(b.state() == 2));
            g.setSkyLight(15);
            g.setBlockLight(15);
            g.setPreRenderFunc(param -> updateMirror(slot, g));
            register.add(g);
            MIRROR_GROUPS.put(slot, g);
        }
        MIRROR_GROUPS.entrySet().removeIf(e -> {
            if (MirrorPayloads.BEAMS.containsKey(e.getKey())) return false;
            register.remove(e.getValue().getId());
            return true;
        });
    }

    private static void updateMirror(int slot, IDhApiRenderableBoxGroup g) {
        Minecraft mc = Minecraft.getInstance();
        Object[] e = MirrorPayloads.BEAMS.get(slot);
        if (e == null || mc.level == null || (long) e[1] < 0) { g.setActive(false); return; }
        double t = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        double[] s = ClientMirror.pos(e, t);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean far = Math.hypot(cam.x - s[0], cam.z - s[2]) > ClientBlastGeo.FAR_VIEW;
        g.setActive(far);
        if (!far) return;
        g.setOriginBlockPos(new DhApiVec3d(s[0], s[1], s[2]));
        boolean burn = ((MirrorPayloads.Beam) e[0]).state() == 2;
        g.clear();
        g.addAll(buildMirror(burn));
        g.triggerBoxChange();
    }

    private static List<DhApiRenderableBox> buildMirror(boolean burn) {
        List<DhApiRenderableBox> boxes = new ArrayList<>(40);
        double size = burn ? 7.0 : 2.5;
        for (int i = 0; i < 35; i++) {
            int a = burn ? Math.max(90, 255 - i * 4) : Math.max(40, 140 - i * 3);
            boxes.add(cube(new Vec3(0.0, 6.0 + i * 12.0, 0.0), size * (1.0 - i * 0.008), new Color(255, burn ? 228 : 210, burn ? 170 : 140, a), EDhApiBlockMaterial.ILLUMINATED));
        }
        if (burn) boxes.add(cube(new Vec3(0.0, 2.0, 0.0), 14.0, new Color(255, 240, 200, 230), EDhApiBlockMaterial.ILLUMINATED));
        return boxes;
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static final long CLOUD_TICKS = 2000;
    private static final double CLOUD_MIN_DIST = ClientBlastGeo.FAR_VIEW;

    private static final class Cloud {
        final double x, y, z;
        final int tier;
        final WarheadType type;
        final long start;
        IDhApiRenderableBoxGroup group;
        long lastBuild = Long.MIN_VALUE;

        Cloud(double x, double y, double z, int tier, WarheadType type, long start) { this.x = x; this.y = y; this.z = z; this.tier = tier; this.type = type; this.start = start; }
    }

    private static void tickClouds(ClientLevel level) {
        long now = level.getGameTime();
        for (DhBridge.CloudRequest r : DhBridge.PENDING) {
            Cloud c = new Cloud(r.x(), r.y(), r.z(), r.tier(), r.type(), r.start());
            c.group = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(
                    Wmd.MODID + ":cloud", new DhApiVec3d(c.x, c.y, c.z), buildCloud(c, Math.max(0, now - c.start)));
            c.group.setSkyLight(15);
            c.group.setBlockLight(8);
            c.group.setPreRenderFunc(param -> updateCloud(c));
            register.add(c.group);
            CLOUDS.add(c);
        }
        DhBridge.PENDING.clear();
        CLOUDS.removeIf(c -> {
            if (now - c.start < CLOUD_TICKS) return false;
            register.remove(c.group.getId());
            return true;
        });
    }

    private static void updateCloud(Cloud c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { c.group.setActive(false); return; }
        long now = mc.level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean far = cam.distanceTo(new Vec3(c.x, c.y, c.z)) > CLOUD_MIN_DIST;
        c.group.setActive(far && now - c.start < CLOUD_TICKS);
        if (!far || now / 4 == c.lastBuild) return;
        c.lastBuild = now / 4;
        List<DhApiRenderableBox> boxes = buildCloud(c, now - c.start);
        c.group.clear();
        c.group.addAll(boxes);
        c.group.triggerBoxChange();
    }

    private static double hash(int a, int b, int c, long seed) {
        long h = a * 73856093L ^ b * 19349663L ^ c * 83492791L ^ seed * 2654435761L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0;
    }

    private static List<DhApiRenderableBox> worldEvent(List<DhApiRenderableBox> out, WarheadType t, double R, double sec, long seed) {
        if (sec > 45.0) return out;
        boolean rg = t == WarheadType.RAILGUN, rod = t == WarheadType.ORBITAL_ROD, ast = t == WarheadType.ASTEROID;
        double cell = Math.max(3.0, R / 6.0);
        int[] fc = rg ? new int[] { 170, 215, 255 } : rod ? new int[] { 255, 250, 235 } : new int[] { 255, 170, 80 };

        if (sec < 1.2) {
            double fr = R * (ast ? 1.6 : 1.0) * (0.5 + 0.5 * sec / 1.2), a = 1.0 - sec / 1.2;
            int n = (int) Math.ceil(fr / cell);
            for (int i = -n; i <= n; i++) for (int j = 0; j <= n; j++) for (int k = -n; k <= n; k++) {
                double d = Math.sqrt(i * i + j * j + k * k) * cell / fr;
                if (d > 1.0 || d < 0.75) continue;
                out.add(cube(new Vec3(i * cell, j * cell, k * cell), cell * 1.2, new Color(fc[0], fc[1], fc[2], Math.max(10, (int) (230 * a))), EDhApiBlockMaterial.ILLUMINATED));
            }
        }

        double fbR = R * (ast ? 1.1 : rg ? 0.45 : 0.3) * Math.min(1.0, sec / 0.6), heat = Math.max(0.0, 1.0 - sec / 6.0);
        if (sec < 6.0 && fbR > cell) {
            int n = (int) Math.ceil(fbR / cell);
            for (int i = -n; i <= n; i++) for (int j = 0; j <= n; j++) for (int k = -n; k <= n; k++) {
                double d = Math.sqrt(i * i + j * j + k * k) * cell / fbR;
                if (d > 1.0 || d < 0.6) continue;
                out.add(cube(new Vec3(i * cell, j * cell, k * cell), cell * 1.2, tint(Math.max(heat, 0.5), 1.0, Math.max(10, (int) (255 * heat))), EDhApiBlockMaterial.ILLUMINATED));
            }
        }

        final double EJ_T = 8.0;
        int rays = ast ? 200 : 150;
        double hMax = R * (rg ? 2.2 : rod ? 2.6 : 1.8), dMax = R * (rg ? 1.2 : rod ? 1.4 : 2.5);
        for (int r = 0; r < rays; r++) {
            double az = hash(r, 11, 1, seed) * Math.PI * 2, h = hash(r, 12, 2, seed), d0 = R * (0.3 + 0.3 * hash(r, 13, 3, seed));
            double hm = hMax * (0.5 + 0.7 * h), dm = dMax * (0.5 + 0.8 * hash(r, 14, 4, seed)), size = cell * (0.8 + 0.6 * hash(r, 15, 5, seed));
            for (int k = 0; k < 3; k++) {
                double tau = sec - 0.08 - k * 0.35;
                if (tau < 0.0) continue;
                double u = Math.min(1.0, tau / EJ_T), y = 4.0 * hm * u * (1.0 - u), dd = d0 + dm * u;
                double fade = tau < EJ_T ? 1.0 : Math.max(0.0, 1.0 - (tau - EJ_T) / 12.0);
                if (fade <= 0.0 || k > 0 && tau >= EJ_T) continue;
                double hot = Math.max(0.0, 1.0 - tau / 3.0);
                out.add(cube(new Vec3(Math.cos(az) * dd, Math.max(cell * 0.5, y), Math.sin(az) * dd), size * (k == 0 ? 1.0 : 0.7),
                        hot > 0.3 ? tint(hot, 1.0, Math.max(10, (int) (230 * fade))) : new Color(112, 102, 94, Math.max(10, (int) ((k == 0 ? 220 : 140) * fade))),
                        hot > 0.3 ? EDhApiBlockMaterial.ILLUMINATED : EDhApiBlockMaterial.STONE));
            }
        }

        if (sec < 10.0) {
            double reach = R * 4.0 * (1.0 - Math.pow(1.0 - sec / 10.0, 2.0)), fade = 1.0 - sec / 10.0;
            int n = (int) Math.min(320, Math.max(16, Math.round(2 * Math.PI * reach / cell)));
            for (int a = 0; a < n; a++) {
                double ang = a * 2 * Math.PI / n, jit = (hash(a, 1, 7, seed) - 0.5) * cell;
                out.add(cube(new Vec3(Math.cos(ang) * (reach + jit), cell * 0.5, Math.sin(ang) * (reach + jit)), cell * 1.3, new Color(236, 228, 212, Math.max(10, (int) (190 * fade))), EDhApiBlockMaterial.SNOW));
            }
        }

        if (sec > 1.5) {
            double grow = Math.min(1.0, (sec - 1.5) / 8.0), rise = Math.min(1.0, (sec - 1.5) / 5.0), life = sec < 30.0 ? 1.0 : Math.max(0.0, 1.0 - (sec - 30.0) / 15.0);
            double pr = R * (rg ? 2.0 : rod ? 2.4 : 4.0) * (1.0 - Math.pow(1.0 - grow, 2.0)), ph = R * (rg ? 0.5 : rod ? 0.6 : 1.0) * rise;
            if (life > 0.0 && pr > cell && ph > cell * 0.5) {
                int naz = 40, nel = 6;
                for (int i = 0; i < naz; i++) for (int j = 0; j < nel; j++) {
                    double az = (i + 0.5 * (j % 2)) * 2 * Math.PI / naz, el = (j + 0.5) / nel * Math.PI / 2, lump = 0.8 + 0.4 * hash(i, j, 21, seed);
                    double rr = pr * Math.cos(el) * lump, yy = ph * Math.sin(el) * lump + cell * 0.5;
                    double bob = Math.sin(sec * 0.6 + i * 0.7 + j) * cell * 0.3;
                    out.add(cube(new Vec3(Math.cos(az) * rr, yy + bob, Math.sin(az) * rr), cell * (1.8 + 0.6 * lump), new Color(150, 140, 128, Math.max(10, (int) (150 * life))), EDhApiBlockMaterial.STONE));
                }
            }
        }

        if (sec < 20.0) {
            double g = 1.0 - sec / 20.0;
            int n = (int) Math.min(120, Math.max(12, Math.round(2 * Math.PI * R / cell)));
            Color gc = rod ? new Color(120, 255, 170, Math.max(10, (int) (200 * g))) : new Color(255, 120, 40, Math.max(10, (int) (200 * g)));
            for (int a = 0; a < n; a++) {
                double ang = a * 2 * Math.PI / n, rr = R * (0.8 + 0.2 * hash(a, 31, 2, seed));
                out.add(cube(new Vec3(Math.cos(ang) * rr, cell * 0.3, Math.sin(ang) * rr), cell, gc, EDhApiBlockMaterial.ILLUMINATED));
            }
        }
        return out;
    }

    private static Color tint(double heat, double shade, int alpha) {
        double r = (88 + (255 - 88) * heat) * shade, g = (82 + (135 - 82) * heat) * shade, b = (78 + (40 - 78) * heat) * shade;
        return new Color(Mth.clamp((int) r, 0, 255), Mth.clamp((int) g, 0, 255), Mth.clamp((int) b, 0, 255), alpha);
    }

    private static List<DhApiRenderableBox> buildPlume(Cloud cl, long age) {
        List<DhApiRenderableBox> out = new ArrayList<>(700);
        WarheadType t = cl.type;
        double sec = age / 20.0;
        double base = Missiles.craterRadius(cl.tier);
        double R = Math.max(3.0, base * t.crater);
        long seed = cl.start;
        if (t == WarheadType.EMP) {
            double life = Math.max(0.0, 1.0 - sec / 14.0);
            if (life <= 0.0) return out;
            double zone = base * t.area, rr = zone * Math.min(1.0, sec / 4.0), cell = Math.max(4.0, zone / 40.0);
            int n = (int) Math.min(520, Math.max(12, Math.round(2 * Math.PI * rr / cell)));
            for (int a = 0; a < n; a++) {
                double ang = a * 2 * Math.PI / n, jit = (hash(a, 2, 5, seed) - 0.5) * cell;
                out.add(cube(new Vec3(Math.cos(ang) * (rr + jit), cell * 0.6, Math.sin(ang) * (rr + jit)), cell * 1.4, new Color(150, 200, 255, Math.max(10, (int) (230 * life))), EDhApiBlockMaterial.ILLUMINATED));
            }
            return out;
        }
        if (t == WarheadType.CHAFF) {
            double life = sec > 70 ? Math.max(0.0, 1.0 - (sec - 70) / 30.0) : 1.0;
            if (life <= 0.0) return out;
            double zone = WarheadFx.CHAFF_RADIUS * Math.min(1.0, 0.2 + sec / 25.0), cell = Math.max(5.0, zone / 10.0);
            int nx = (int) Math.ceil(zone / cell);
            for (int i = -nx; i <= nx; i++) for (int k = -nx; k <= nx; k++) {
                double dx = i * cell, dz = k * cell, h = hash(i, 3, k, seed);
                if (dx * dx + dz * dz > zone * zone || h < 0.35) continue;
                out.add(cube(new Vec3(dx, 22.0 + h * 10.0, dz), cell * 1.5, new Color(205, 210, 222, Math.max(10, (int) (120 * life))), EDhApiBlockMaterial.SNOW));
            }
            return out;
        }
        if (t == WarheadType.RAILGUN || t == WarheadType.ORBITAL_ROD || t == WarheadType.ASTEROID) return worldEvent(out, t, R, sec, seed);
        double life = sec > 25 ? Math.max(0.0, 1.0 - (sec - 25) / 15.0) : 1.0;
        if (life <= 0.0) return out;
        int alpha = Math.max(10, (int) (230 * life));
        double cell = Math.max(3.0, R / 4.0), size = cell * 1.2;
        double heat = Math.max(0.0, 1.0 - sec / 10.0);
        double colH, colR, domeR = 0.0, heatMin = 0.0;
        boolean column = true;
        switch (t) {
            case INCENDIARY -> { colH = R * 6; colR = R * 0.5; heatMin = 0.6; }
            case FRAG -> { colH = 0; colR = 0; column = false; }
            case THERMOBARIC -> { colH = R * 3; colR = R * 0.6; domeR = R; }
            case BREACHER -> { colH = R * 7; colR = R * 0.22; }
            case BUNKER -> { colH = R * 10; colR = R * 0.4; }
            case HE -> { colH = R * 3; colR = R * 0.9; }
            case ANTIMATTER -> { colH = 0; colR = 0; column = false; domeR = R; }
            case ORBITAL_ROD -> { colH = R * 6; colR = R * 0.22; domeR = R * 0.3; }
            case ASTEROID -> { colH = R * 5; colR = R * 0.5; domeR = R * 1.1; }
            case RAILGUN -> { colH = R * 8; colR = R * 0.18; domeR = R * 0.8; }
            default -> { colH = R * 5; colR = R * 0.35; }
        }

        double fbR = (domeR > 0 ? domeR : R * 0.5) * Math.min(1.0, sec / 2.0);
        if (sec < 8 && fbR > cell) {
            int n = (int) Math.ceil(fbR / cell);
            for (int i = -n; i <= n; i++) for (int j = 0; j <= n; j++) for (int k = -n; k <= n; k++) {
                double d = Math.sqrt(i * i + j * j + k * k) * cell / fbR;
                if (d > 1.0 || d < 0.6) continue;
                out.add(cube(new Vec3(i * cell, j * cell, k * cell), size, tint(Math.max(heat, 0.6), 1.0, 255), EDhApiBlockMaterial.ILLUMINATED));
            }
        }

        if (column) {
            double top = colH * Math.min(1.0, sec / 8.0);
            for (double y = cell * 0.5; y < top; y += cell) {
                double f = y / colH;
                double rs = colR * (0.6 + 0.4 * (1.0 - f));
                int n = rs < cell * 1.5 ? 1 : (int) Math.round(2 * Math.PI * rs / cell);
                for (int a = 0; a < n; a++) {
                    double ang = a * 2 * Math.PI / n + f * 2.0, h = hash(a, (int) (y / cell), 3, seed);
                    Vec3 c = n == 1 ? new Vec3(0, y, 0) : new Vec3(Math.cos(ang) * rs, y, Math.sin(ang) * rs);
                    double hot = Math.max(heatMin, heat * (1.0 - 0.5 * f));
                    boolean glow = hot > 0.45 && h < 0.6;
                    out.add(cube(c, size, tint(hot, 0.8 + 0.3 * h, alpha), glow ? EDhApiBlockMaterial.ILLUMINATED : (t == WarheadType.BUNKER || t == WarheadType.BREACHER ? EDhApiBlockMaterial.DIRT : EDhApiBlockMaterial.STONE)));
                }
            }
        }

        if (sec < 12) {
            double reach = R * 3.0 * (1.0 - Math.pow(1.0 - sec / 12.0, 2.0)), fade = 1.0 - sec / 12.0;
            int n = (int) Math.min(300, Math.max(10, Math.round(2 * Math.PI * reach / cell)));
            for (int a = 0; a < n; a++) {
                double ang = a * 2 * Math.PI / n, jit = (hash(a, 1, 7, seed) - 0.5) * cell;
                out.add(cube(new Vec3(Math.cos(ang) * (reach + jit), cell * 0.5, Math.sin(ang) * (reach + jit)), cell * 1.3, new Color(228, 218, 202, Math.max(10, (int) (170 * fade))), EDhApiBlockMaterial.SNOW));
            }
        }
        return out;
    }

    private static List<DhApiRenderableBox> buildCloud(Cloud cl, long age) {
        if (!cl.type.mushroom()) return buildPlume(cl, age);
        List<DhApiRenderableBox> out = new ArrayList<>(2000);
        double sec = age / 20.0;
        double height = Missiles.craterRadius(cl.tier) * 16.0;
        double cell = Math.max(4.0, height / 32.0);
        double rise = sec >= 40 ? 1.0 : 1.0 - Math.pow(1.0 - sec / 40.0, 2);
        double grow = Math.min(1.0, sec / 30.0);
        double heat = Math.max(0.0, 1.0 - sec / 28.0);
        double life = sec > 70 ? Math.max(0.0, 1.0 - (sec - 70) / 30.0) : 1.0;
        int alpha = Math.max(10, (int) (235 * life));
        double size = cell * 1.2 * (0.35 + 0.65 * life);
        long seed = cl.start;

        if (sec < 14) {
            double rb = height * 0.11 * Math.min(1.0, sec / 3.0);
            int n = (int) Math.ceil(rb / cell);
            for (int i = -n; i <= n; i++) for (int j = 0; j <= n; j++) for (int k = -n; k <= n; k++) {
                double d = Math.sqrt(i * i + j * j + k * k) * cell / Math.max(rb, 1.0);
                if (d > 1.0 || d < 0.6) continue;
                out.add(cube(new Vec3(i * cell, j * cell, k * cell), size, tint(Math.max(heat, 0.6), 1.0, 255), EDhApiBlockMaterial.ILLUMINATED));
            }
        }

        if (sec < 32) {
            double reach = height * 1.9;
            double f = 1.0 - Math.pow(1.0 - sec / 32.0, 2.2);
            double rr = reach * f;
            double fade = 1.0 - sec / 32.0;
            int n = (int) Math.min(600, Math.max(12, Math.round(2 * Math.PI * rr / cell)));
            int wa = Math.max(10, (int) (190 * fade));
            for (int a = 0; a < n; a++) {
                double ang = a * 2 * Math.PI / n;
                double jit = (hash(a, 1, 7, seed) - 0.5) * cell * 1.5;
                double cx = Math.cos(ang), cz = Math.sin(ang);
                for (int layer = 0; layer < 2; layer++) {
                    out.add(cube(new Vec3(cx * (rr + jit), cell * (0.5 + layer), cz * (rr + jit)), cell * 1.5, new Color(228, 218, 202, wa), EDhApiBlockMaterial.SNOW));
                }
                if (rr > cell * 3) {
                    out.add(cube(new Vec3(cx * (rr - cell * 2.5 + jit), cell * 0.6, cz * (rr - cell * 2.5 + jit)), cell * 1.6, new Color(150, 125, 98, Math.max(10, (int) (150 * fade))), EDhApiBlockMaterial.SAND));
                }
            }
        }
        if (rise < 0.02) return out;

        double top = height * rise;
        double rc = height * 0.28 * Math.max(0.12, grow);
        double ry = rc * 0.55;
        double hc = Math.max(ry, top - ry);

        for (double y = cell * 0.5; y < hc - ry * 0.3; y += cell) {
            double t = y / hc;
            double rs = height * (0.035 + 0.075 * Math.pow(1.0 - t, 4)) * (0.6 + 0.4 * grow);
            int n = rs < cell * 1.5 ? 1 : (int) Math.round(2 * Math.PI * rs / cell);
            for (int a = 0; a < n; a++) {
                double ang = a * 2 * Math.PI / n + t * 2.0;
                Vec3 c = n == 1 ? new Vec3(0, y, 0) : new Vec3(Math.cos(ang) * rs, y, Math.sin(ang) * rs);
                double h = hash(a, (int) (y / cell), 3, seed);
                boolean glow = heat > 0.45 && h < 0.6;
                out.add(cube(c, size, tint(heat * (1.0 - 0.5 * t), 0.8 + 0.3 * h, alpha), glow ? EDhApiBlockMaterial.ILLUMINATED : EDhApiBlockMaterial.STONE));
            }
        }

        if (rise > 0.08) {
            int nx = (int) Math.ceil(rc / cell), ny = (int) Math.ceil(ry / cell);
            for (int i = -nx; i <= nx; i++) for (int j = -ny; j <= ny; j++) for (int k = -nx; k <= nx; k++) {
                double x = i * cell, y = j * cell, z = k * cell;
                double d = (x * x + z * z) / (rc * rc) + (y * y) / (ry * ry);
                double h = hash(i, j, k, seed);
                if (d > 1.0 + (h - 0.5) * 0.25 || d < 0.55) continue;
                double up = Mth.clamp((y / ry + 1.0) * 0.5, 0.0, 1.0);
                boolean glow = heat > 0.45 && h < 0.5;
                out.add(cube(new Vec3(x, hc + y, z), size, tint(heat * (0.4 + 0.6 * (1.0 - up)), 0.65 + 0.4 * up * (0.7 + 0.3 * h), alpha),
                        glow ? EDhApiBlockMaterial.ILLUMINATED : EDhApiBlockMaterial.STONE));
            }
        }
        return out;
    }
}
