package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import org.joml.Matrix4f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
final class WmdLights {

    private WmdLights() {}

    private static final int[] LIGHTS_MAX = { 0, 6, 12 }, RAYS_POINT = { 0, 260, 700 }, RAYS_SPOT = { 0, 320, 800 }, STEP_BUDGET = { 0, 30_000, 90_000 };
    private static final double[] R_MAX = { 0, 10.0, 16.0 }, SPOT_MAX = { 0, 40.0, 64.0 };
    private static final int REGATHER = 40, RING = 1, RING_SPOT = 2, CRAFTS_MAX = 6;
    private static final double DRAW_RANGE = 160.0;

    private static final float PACK_K = 0.6F;

    private static final class Face {
        final int bx, by, bz;
        final float[] p, uv;
        final float nx, ny, nz, tr, tg, tb;

        final java.util.UUID craft;

        Face(int bx, int by, int bz, float[] p, float[] uv, float nx, float ny, float nz, float tr, float tg, float tb, java.util.UUID craft) {
            this.bx = bx; this.by = by; this.bz = bz; this.p = p; this.uv = uv; this.nx = nx; this.ny = ny; this.nz = nz; this.tr = tr; this.tg = tg; this.tb = tb;
            this.craft = craft;
        }
    }

    private static final class Light {

        double x, y, z, dx, dy, dz;
        double tx, ty, tz, tdx, tdy, tdz, qx, qy, qz, qdx, qdy, qdz;
        long tick = Long.MIN_VALUE, callFrame = Long.MIN_VALUE;
        float r, g, b, radius, bright, inscatter, angle, dist;
        boolean spot, flash;
        long seen, start;
        int duration;
        float flashBright, flashRadius;

        List<Face> faces;
        double gx, gy, gz, gdx, gdy, gdz, greach;
        long gatheredAt = Long.MIN_VALUE / 2;
    }

    private static final Map<Long, Light> LIGHTS = Statics.client(new HashMap<>());
    private static final List<Light> FLASHES = Statics.client(new ArrayList<>());

    private static boolean underPack() {
        try { return ClientConfig.WMD_LIGHTS_UNDER_SHADERS.get(); } catch (RuntimeException e) { return false; }
    }

    private static int quality() {
        try { return ClientConfig.WMD_LIGHTS.get().ordinal(); } catch (RuntimeException e) { return 2; }
    }

    static void glow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, long gameTime) {
        if (quality() == 0) return;
        Light l = LIGHTS.computeIfAbsent(key, k -> new Light());
        l.spot = false;
        place(l, x, y, z, 0.0, 0.0, 0.0, gameTime);
        l.r = r; l.g = g; l.b = b; l.radius = radius; l.bright = brightness; l.inscatter = inscatter; l.seen = gameTime;
    }

    static void spot(long key, double x, double y, double z, double dx, double dy, double dz, float r, float g, float b, float angle, float distance, float brightness, long gameTime) {
        if (quality() == 0) return;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return;
        Light l = LIGHTS.computeIfAbsent(key, k -> new Light());
        l.spot = true;
        place(l, x, y, z, dx / len, dy / len, dz / len, gameTime);
        l.r = r; l.g = g; l.b = b; l.angle = angle; l.dist = distance; l.radius = distance; l.bright = brightness; l.inscatter = 0.0F; l.seen = gameTime;
    }

    static void flash(double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, int duration, long gameTime) {
        if (quality() == 0 || duration <= 0) return;
        Light l = new Light();
        l.flash = true;
        place(l, x, y, z, 0.0, 0.0, 0.0, gameTime);
        l.r = r; l.g = g; l.b = b; l.radius = radius; l.bright = brightness; l.inscatter = inscatter;
        l.flashBright = brightness; l.flashRadius = radius; l.start = gameTime; l.duration = duration;
        FLASHES.add(l);
    }

    private static void place(Light l, double x, double y, double z, double dx, double dy, double dz, long gameTime) {
        if (l.tick == Long.MIN_VALUE) { l.qx = x; l.qy = y; l.qz = z; l.qdx = dx; l.qdy = dy; l.qdz = dz; }
        else if (gameTime != l.tick) { l.qx = l.tx; l.qy = l.ty; l.qz = l.tz; l.qdx = l.tdx; l.qdy = l.tdy; l.qdz = l.tdz; }
        l.tick = gameTime;
        l.tx = x; l.ty = y; l.tz = z; l.tdx = dx; l.tdy = dy; l.tdz = dz;
        l.callFrame = frameNo;
        if (l.faces == null) { l.x = x; l.y = y; l.z = z; l.dx = dx; l.dy = dy; l.dz = dz; }
    }

    private static long frameNo;

    private static void drawnPlace(Light l, float pt) {
        if (l.flash || l.callFrame == frameNo) { l.x = l.tx; l.y = l.ty; l.z = l.tz; l.dx = l.tdx; l.dy = l.tdy; l.dz = l.tdz; return; }
        l.x = l.qx + (l.tx - l.qx) * pt; l.y = l.qy + (l.ty - l.qy) * pt; l.z = l.qz + (l.tz - l.qz) * pt;
        double dx = l.qdx + (l.tdx - l.qdx) * pt, dy = l.qdy + (l.tdy - l.qdy) * pt, dz = l.qdz + (l.tdz - l.qdz) * pt, n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (n > 1.0E-6) { l.dx = dx / n; l.dy = dy / n; l.dz = dz / n; } else { l.dx = l.tdx; l.dy = l.tdy; l.dz = l.tdz; }
    }

    static void sweep(long gameTime) {
        LIGHTS.values().removeIf(l -> gameTime - l.seen > 5 || gameTime < l.seen);
        Iterator<Light> it = FLASHES.iterator();
        while (it.hasNext()) {
            Light f = it.next();
            long age = gameTime - f.start;
            if (age < 0 || age >= f.duration) { it.remove(); continue; }
            float k = 1.0F - age / (float) f.duration;
            f.bright = f.flashBright * k * k;
            f.radius = f.flashRadius * (0.6F + 0.4F * k);
        }
    }

    static void clear() {
        LIGHTS.clear();
        FLASHES.clear();
    }

    private static boolean irisLooked;
    private static Object irisApi;
    private static java.lang.reflect.Method irisShadow;

    private static boolean shadowPass() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                if (net.neoforged.fml.ModList.get().isLoaded("iris")) {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = c.getMethod("getInstance").invoke(null);
                    irisShadow = c.getMethod("isRenderingShadowPass");
                }
            } catch (Throwable t) { irisShadow = null; }
        }
        if (irisShadow == null) return false;
        try { return Boolean.TRUE.equals(irisShadow.invoke(irisApi)); } catch (Throwable t) { irisShadow = null; return false; }
    }

    private static boolean failed;

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || failed) return;
        if (LIGHTS.isEmpty() && FLASHES.isEmpty()) return;
        int q = quality();
        if (q == 0 || shadowPass()) return;

        if (VeilBridge.shaderPackOn() && !underPack()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        try {
            draw(event, mc, level, q);
        } catch (Throwable t) {
            failed = true;
            Wmd.LOGGER.error("[wmd] WMD's own lights disabled for this session", t);
        }
    }

    private static void draw(RenderLevelStageEvent event, Minecraft mc, ClientLevel level, int q) {
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        long now = level.getGameTime();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        frameNo++;
        for (Light l : LIGHTS.values()) drawnPlace(l, pt);
        for (Light l : FLASHES) drawnPlace(l, pt);

        List<Light> all = new ArrayList<>(LIGHTS.values());
        all.addAll(FLASHES);
        all.removeIf(l -> l.bright <= 0.001F || dist(l, cam) > DRAW_RANGE + l.radius);
        all.sort((a, b) -> Double.compare(score(b, cam), score(a, cam)));
        if (all.size() > LIGHTS_MAX[q]) all = all.subList(0, LIGHTS_MAX[q]);
        if (all.isEmpty()) return;

        int budget = STEP_BUDGET[q];
        List<Light> order = new ArrayList<>(all);
        order.sort((a, b) -> Long.compare(a.gatheredAt, b.gatheredAt));
        for (Light l : order) {
            if (!needsGather(l, now, q)) continue;
            if (budget <= 0 && l.faces != null) continue;
            budget -= gather(level, mc, l, q, now, pt);
        }
        boolean pack = VeilBridge.shaderPackOn();
        float k = pack ? PACK_K : 1.0F;

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        Map<java.util.UUID, org.joml.Matrix4d> poses = new HashMap<>();
        for (Light l : all) {
            if (l.faces == null) continue;
            for (Face f : l.faces) {
                org.joml.Matrix4d m = null;
                if (f.craft != null) {
                    m = poses.computeIfAbsent(f.craft, id -> ShipFrame.craftMatrix(level, id, pt));
                    if (m == null) continue;
                }
                quads += emit(bb, f, m, l, cam, k, q);
            }
        }
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix()), proj = new Matrix4f(event.getProjectionMatrix());
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(mv);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-3.0F, -3.0F);
        try {
            MeshData md = bb.build();
            if (md != null) {
                if (quads > 0) {
                    RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                    RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
                    BufferUploader.drawWithShader(md);
                } else md.close();
            }

            BufferBuilder hb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            int tris = 0;
            for (Light l : all) if (!l.spot && l.inscatter > 0.0F) tris += halo(hb, l, camera, cam, k);
            MeshData hd = hb.build();
            if (hd != null) {
                if (tris > 0) {
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    BufferUploader.drawWithShader(hd);
                } else hd.close();
            }
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static double dist(Light l, Vec3 cam) { return Math.sqrt((l.x - cam.x) * (l.x - cam.x) + (l.y - cam.y) * (l.y - cam.y) + (l.z - cam.z) * (l.z - cam.z)); }

    private static double score(Light l, Vec3 cam) { return l.bright * Math.max(1.0, l.radius) / Math.max(4.0, dist(l, cam)); }

    private static double reach(Light l, int q) { return l.spot ? Math.min(l.dist, SPOT_MAX[q]) : Math.min(l.radius, R_MAX[q]); }

    private static boolean needsGather(Light l, long now, int q) {
        if (l.faces == null) return true;
        if (l.flash) return false;
        double moved = Math.abs(l.x - l.gx) + Math.abs(l.y - l.gy) + Math.abs(l.z - l.gz);
        if (moved > (l.spot ? 1.0 : 0.5)) return true;
        if (l.spot && Math.abs(l.dx - l.gdx) + Math.abs(l.dy - l.gdy) + Math.abs(l.dz - l.gdz) > 0.03) return true;
        if (Math.abs(reach(l, q) - l.greach) > 0.5) return true;
        return now - l.gatheredAt >= REGATHER;
    }

    private static final class Space {
        final java.util.UUID craft;
        final double ox, oy, oz;
        final org.joml.Matrix4d inv;

        Space(java.util.UUID craft, double ox, double oy, double oz, org.joml.Matrix4d inv) { this.craft = craft; this.ox = ox; this.oy = oy; this.oz = oz; this.inv = inv; }
    }

    private static int gather(ClientLevel level, Minecraft mc, Light l, int q, long now, float pt) {
        double reach = reach(l, q);
        int n = l.spot ? RAYS_SPOT[q] : RAYS_POINT[q];
        Set<Long> keys = new HashSet<>();
        List<Face> out = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int[] steps = { 0 };

        List<Space> spaces = new ArrayList<>();
        spaces.add(new Space(null, l.x, l.y, l.z, null));
        for (java.util.UUID id : ShipFrame.craftsNear(level, new Vec3(l.x, l.y, l.z), reach + 1.0)) {
            if (spaces.size() > CRAFTS_MAX) break;
            org.joml.Matrix4d m = ShipFrame.craftMatrix(level, id, pt);
            if (m == null) continue;
            org.joml.Matrix4d inv = new org.joml.Matrix4d(m).invert();
            org.joml.Vector3d o = inv.transformPosition(new org.joml.Vector3d(l.x, l.y, l.z));
            spaces.add(new Space(id, o.x, o.y, o.z, inv));
        }

        double ax = l.dx, ay = l.dy, az = l.dz, ux, uy, uz;
        if (Math.abs(ay) < 0.9) { ux = az; uy = 0.0; uz = -ax; } else { ux = 0.0; uy = -az; uz = ay; }
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul;
        double vx = ay * uz - az * uy, vy = az * ux - ax * uz, vz = ax * uy - ay * ux;
        double cosMax = Math.cos(Math.min(Math.PI * 0.49, l.angle));
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        double[] hit = new double[5], best = new double[5];
        org.joml.Vector3d d3 = new org.joml.Vector3d();
        for (int i = 0; i < n; i++) {
            double rx, ry, rz;
            if (l.spot) {
                double c = 1.0 - (1.0 - cosMax) * (i + 0.5) / n, sn = Math.sqrt(Math.max(0.0, 1.0 - c * c)), ph = golden * i;
                double px = Math.cos(ph) * sn, py = Math.sin(ph) * sn;
                rx = ax * c + ux * px + vx * py; ry = ay * c + uy * px + vy * py; rz = az * c + uz * px + vz * py;
            } else {
                double yy = 1.0 - 2.0 * (i + 0.5) / n, sn = Math.sqrt(Math.max(0.0, 1.0 - yy * yy)), ph = golden * i;
                rx = Math.cos(ph) * sn; ry = yy; rz = Math.sin(ph) * sn;
            }
            Space bestSpace = null;
            best[0] = Double.MAX_VALUE;
            for (Space sp : spaces) {
                double ddx = rx, ddy = ry, ddz = rz;
                if (sp.inv != null) {
                    sp.inv.transformDirection(d3.set(rx, ry, rz)).normalize();
                    ddx = d3.x; ddy = d3.y; ddz = d3.z;
                }
                if (march(level, mp, sp.ox, sp.oy, sp.oz, ddx, ddy, ddz, Math.min(reach, best[0]), hit, steps) && hit[0] < best[0]) {
                    System.arraycopy(hit, 0, best, 0, 5);
                    bestSpace = sp;
                }
            }
            if (bestSpace == null) continue;
            int x = (int) best[1], y = (int) best[2], z = (int) best[3];
            Direction face = Direction.values()[(int) best[4]];
            addFace(level, mc, bestSpace, x, y, z, face, reach, mp, keys, out);

            int ring = l.spot ? RING_SPOT : RING;
            for (int a = -ring; a <= ring; a++)
                for (int b = -ring; b <= ring; b++) {
                    if (a == 0 && b == 0) continue;
                    int nx2, ny2, nz2;
                    if (face.getAxis() == Direction.Axis.Y) { nx2 = x + a; ny2 = y; nz2 = z + b; }
                    else if (face.getAxis() == Direction.Axis.X) { nx2 = x; ny2 = y + a; nz2 = z + b; }
                    else { nx2 = x + a; ny2 = y + b; nz2 = z; }
                    steps[0]++;
                    if (!opaque(level, mp, nx2, ny2, nz2)) continue;
                    if (opaque(level, mp, nx2 + face.getStepX(), ny2 + face.getStepY(), nz2 + face.getStepZ())) continue;
                    addFace(level, mc, bestSpace, nx2, ny2, nz2, face, reach, mp, keys, out);
                }
        }
        l.faces = out;
        l.gx = l.x; l.gy = l.y; l.gz = l.z; l.gdx = l.dx; l.gdy = l.dy; l.gdz = l.dz; l.greach = reach;
        l.gatheredAt = now;
        return Math.max(1, steps[0]);
    }

    private static int floor(double v) { int i = (int) v; return v < i ? i - 1 : i; }

    private static boolean opaque(ClientLevel level, BlockPos.MutableBlockPos mp, int x, int y, int z) {
        BlockState st = level.getBlockState(mp.set(x, y, z));
        return !st.isAir() && st.canOcclude() && st.getRenderShape() == RenderShape.MODEL;
    }

    private static boolean march(ClientLevel level, BlockPos.MutableBlockPos mp, double ox, double oy, double oz, double dx, double dy, double dz, double reach,
                                 double[] hit, int[] steps) {
        int x = floor(ox), y = floor(oy), z = floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = dx != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE, tdy = dy != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE, tdz = dz != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tx = dx != 0 ? (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx : Double.MAX_VALUE;
        double ty = dy != 0 ? (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy : Double.MAX_VALUE;
        double tz = dz != 0 ? (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz : Double.MAX_VALUE;
        while (true) {
            Direction face;
            double t;
            if (tx <= ty && tx <= tz) { t = tx; if (t > reach) return false; x += sx; tx += tdx; face = sx > 0 ? Direction.WEST : Direction.EAST; }
            else if (ty <= tz) { t = ty; if (t > reach) return false; y += sy; ty += tdy; face = sy > 0 ? Direction.DOWN : Direction.UP; }
            else { t = tz; if (t > reach) return false; z += sz; tz += tdz; face = sz > 0 ? Direction.NORTH : Direction.SOUTH; }
            steps[0]++;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
            if (!opaque(level, mp, x, y, z)) continue;
            hit[0] = t; hit[1] = x; hit[2] = y; hit[3] = z; hit[4] = face.ordinal();
            return true;
        }
    }

    private static void addFace(ClientLevel level, Minecraft mc, Space sp, int x, int y, int z, Direction face, double reach, BlockPos.MutableBlockPos mp,
                                Set<Long> keys, List<Face> out) {
        java.util.UUID craft = sp.craft;
        double cx = x + 0.5 + face.getStepX() * 0.5 - sp.ox, cy = y + 0.5 + face.getStepY() * 0.5 - sp.oy, cz = z + 0.5 + face.getStepZ() * 0.5 - sp.oz;
        if (cx * cx + cy * cy + cz * cz > (reach + 0.9) * (reach + 0.9)) return;
        long key = BlockPos.asLong(x, y, z) * 8L + face.ordinal();
        if (!keys.add(key)) return;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads;
        try {
            quads = model.getQuads(state, face, RandomSource.create(state.getSeed(pos)), ModelData.EMPTY, null);
        } catch (RuntimeException e) {
            quads = List.of();
        }
        float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        if (quads.isEmpty()) {
            TextureAtlasSprite ic = model.getParticleIcon(ModelData.EMPTY);
            out.add(new Face(x, y, z, fullFace(face), new float[] { ic.getU0(), ic.getV0(), ic.getU0(), ic.getV1(), ic.getU1(), ic.getV1(), ic.getU1(), ic.getV0() }, nx, ny, nz, 1.0F, 1.0F, 1.0F, craft));
            return;
        }
        for (BakedQuad bq : quads) {
            int[] v = bq.getVertices();
            int stride = v.length / 4;
            float[] p = new float[12], uv = new float[8];
            for (int i = 0; i < 4; i++) {
                p[i * 3] = Float.intBitsToFloat(v[i * stride]);
                p[i * 3 + 1] = Float.intBitsToFloat(v[i * stride + 1]);
                p[i * 3 + 2] = Float.intBitsToFloat(v[i * stride + 2]);
                uv[i * 2] = Float.intBitsToFloat(v[i * stride + 4]);
                uv[i * 2 + 1] = Float.intBitsToFloat(v[i * stride + 5]);
            }
            float tr = 1.0F, tg = 1.0F, tb = 1.0F;
            if (bq.isTinted()) {
                int c = mc.getBlockColors().getColor(state, level, pos, bq.getTintIndex());
                tr = (c >> 16 & 255) / 255.0F; tg = (c >> 8 & 255) / 255.0F; tb = (c & 255) / 255.0F;
            }
            out.add(new Face(x, y, z, p, uv, nx, ny, nz, tr, tg, tb, craft));
        }
    }

    private static float[] fullFace(Direction d) {
        return switch (d) {
            case DOWN -> new float[] { 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1 };
            case UP -> new float[] { 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0 };
            case NORTH -> new float[] { 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0 };
            case SOUTH -> new float[] { 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1 };
            case WEST -> new float[] { 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1 };
            case EAST -> new float[] { 1, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0 };
        };
    }

    private static final int[] SUBDIV = { 0, 2, 4 };
    private static final double SUB_STEP = 0.04;

    private static int emit(BufferBuilder bb, Face f, org.joml.Matrix4d pose, Light l, Vec3 cam, float k, int q) {
        double[] w = new double[12];

        double fnx = f.nx, fny = f.ny, fnz = f.nz;
        if (pose == null) {
            for (int i = 0; i < 4; i++) { w[i * 3] = f.bx + f.p[i * 3]; w[i * 3 + 1] = f.by + f.p[i * 3 + 1]; w[i * 3 + 2] = f.bz + f.p[i * 3 + 2]; }
        } else {
            org.joml.Vector3d v = new org.joml.Vector3d();
            for (int i = 0; i < 4; i++) {
                pose.transformPosition(v.set(f.bx + (double) f.p[i * 3], f.by + (double) f.p[i * 3 + 1], f.bz + (double) f.p[i * 3 + 2]));
                w[i * 3] = v.x; w[i * 3 + 1] = v.y; w[i * 3 + 2] = v.z;
            }
            pose.transformDirection(v.set(f.nx, f.ny, f.nz)).normalize();
            fnx = v.x; fny = v.y; fnz = v.z;
        }
        float[] col = new float[12];
        boolean any = false;
        double spread = 0.0;
        for (int i = 0; i < 4; i++) {
            lightAt(l, w[i * 3], w[i * 3 + 1], w[i * 3 + 2], fnx, fny, fnz, f, k, col, i * 3);
            if (col[i * 3] + col[i * 3 + 1] + col[i * 3 + 2] > 0.004F) any = true;
        }
        if (!any) return 0;
        for (int c = 0; c < 3; c++) {
            float lo = Math.min(Math.min(col[c], col[3 + c]), Math.min(col[6 + c], col[9 + c])), hi = Math.max(Math.max(col[c], col[3 + c]), Math.max(col[6 + c], col[9 + c]));
            spread = Math.max(spread, hi - lo);
        }
        int n = Math.max(1, Math.min(SUBDIV[q], (int) Math.ceil(spread / SUB_STEP)));
        if (n == 1) {
            for (int i = 0; i < 4; i++)
                bb.addVertex((float) (w[i * 3] - cam.x), (float) (w[i * 3 + 1] - cam.y), (float) (w[i * 3 + 2] - cam.z))
                        .setUv(f.uv[i * 2], f.uv[i * 2 + 1]).setColor(col[i * 3], col[i * 3 + 1], col[i * 3 + 2], 1.0F);
            return 1;
        }

        int m = n + 1;
        double[] gp = new double[m * m * 3];
        float[] gu = new float[m * m * 2], gc = new float[m * m * 3];
        for (int bj = 0; bj <= n; bj++)
            for (int ai = 0; ai <= n; ai++) {
                double a = ai / (double) n, b = bj / (double) n;
                double k0 = (1 - a) * (1 - b), k1 = a * (1 - b), k2 = a * b, k3 = (1 - a) * b;
                int o = (bj * m + ai);
                for (int c = 0; c < 3; c++) gp[o * 3 + c] = k0 * w[c] + k1 * w[3 + c] + k2 * w[6 + c] + k3 * w[9 + c];
                for (int c = 0; c < 2; c++) gu[o * 2 + c] = (float) (k0 * f.uv[c] + k1 * f.uv[2 + c] + k2 * f.uv[4 + c] + k3 * f.uv[6 + c]);
                lightAt(l, gp[o * 3], gp[o * 3 + 1], gp[o * 3 + 2], fnx, fny, fnz, f, k, gc, o * 3);
            }
        int quads = 0;
        int[] corner = new int[4];
        for (int bj = 0; bj < n; bj++)
            for (int ai = 0; ai < n; ai++) {
                corner[0] = bj * m + ai; corner[1] = bj * m + ai + 1; corner[2] = (bj + 1) * m + ai + 1; corner[3] = (bj + 1) * m + ai;
                float sum = 0.0F;
                for (int o : corner) sum += gc[o * 3] + gc[o * 3 + 1] + gc[o * 3 + 2];
                if (sum < 0.004F) continue;
                for (int o : corner)
                    bb.addVertex((float) (gp[o * 3] - cam.x), (float) (gp[o * 3 + 1] - cam.y), (float) (gp[o * 3 + 2] - cam.z))
                            .setUv(gu[o * 2], gu[o * 2 + 1]).setColor(gc[o * 3], gc[o * 3 + 1], gc[o * 3 + 2], 1.0F);
                quads++;
            }
        return quads;
    }

    private static void lightAt(Light l, double wx, double wy, double wz, double fnx, double fny, double fnz, Face f, float k, float[] out, int o) {
        double reach = l.spot ? l.dist : l.radius;
        double ox = l.x - wx, oy = l.y - wy, oz = l.z - wz;
        double d = Math.sqrt(ox * ox + oy * oy + oz * oz);
        double s = d / Math.max(0.01, reach);
        double atten = s >= 1.0 ? 0.0 : (1.0 - s) * (1.0 - s) * (1.0 - s);
        double ndl = d > 1.0E-4 ? (fnx * ox + fny * oy + fnz * oz) / d : 1.0;
        double diffuse = l.spot ? ((ndl + 1.0) * 0.5 + 0.4) / 1.4 : (Math.max(0.0, ndl) + 0.4) / 1.4;
        diffuse *= atten;
        if (l.spot && d > 1.0E-4) {
            double cosMax = Math.cos(Math.min(Math.PI * 0.49, l.angle)), cosSoft = Math.cos(Math.min(Math.PI * 0.49, l.angle) * 0.7);
            double cosA = -(ox * l.dx + oy * l.dy + oz * l.dz) / d;
            double t = (cosA - cosMax) / Math.max(1.0E-4, cosSoft - cosMax);
            t = Math.max(0.0, Math.min(1.0, t));
            diffuse *= t * t * (3.0 - 2.0 * t);
        }
        double e = diffuse * l.bright * k;
        out[o] = (float) (1.0 - Math.exp(-e * l.r * f.tr));
        out[o + 1] = (float) (1.0 - Math.exp(-e * l.g * f.tg));
        out[o + 2] = (float) (1.0 - Math.exp(-e * l.b * f.tb));
    }

    private static int halo(BufferBuilder bb, Light l, Camera camera, Vec3 cam, float k) {
        float size = (float) Math.min(l.radius * 0.5, 12.0) * Math.min(1.0F, 0.4F + l.inscatter);
        float a = Math.min(1.0F, l.inscatter * l.bright * 0.35F * k);
        if (a < 0.01F || size < 0.05F) return 0;
        org.joml.Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float cx = (float) (l.x - cam.x), cy = (float) (l.y - cam.y), cz = (float) (l.z - cam.z);
        int seg = 32;
        float[] rs = { 0.0F, 0.33F, 0.66F, 1.0F };
        int tris = 0;
        for (int ring = 0; ring < rs.length - 1; ring++) {
            float r0 = rs[ring] * size, r1 = rs[ring + 1] * size;
            float i0 = a * (1.0F - rs[ring]) * (1.0F - rs[ring]), i1 = a * (1.0F - rs[ring + 1]) * (1.0F - rs[ring + 1]);
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2.0 * i / seg, a1 = Math.PI * 2.0 * (i + 1) / seg;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float[] p00 = disc(cx, cy, cz, left, up, c0 * r0, s0 * r0), p01 = disc(cx, cy, cz, left, up, c1 * r0, s1 * r0);
                float[] p10 = disc(cx, cy, cz, left, up, c0 * r1, s0 * r1), p11 = disc(cx, cy, cz, left, up, c1 * r1, s1 * r1);
                bb.addVertex(p00[0], p00[1], p00[2]).setColor(l.r * i0, l.g * i0, l.b * i0, 1.0F);
                bb.addVertex(p10[0], p10[1], p10[2]).setColor(l.r * i1, l.g * i1, l.b * i1, 1.0F);
                bb.addVertex(p11[0], p11[1], p11[2]).setColor(l.r * i1, l.g * i1, l.b * i1, 1.0F);
                tris++;
                if (ring > 0) {
                    bb.addVertex(p00[0], p00[1], p00[2]).setColor(l.r * i0, l.g * i0, l.b * i0, 1.0F);
                    bb.addVertex(p11[0], p11[1], p11[2]).setColor(l.r * i1, l.g * i1, l.b * i1, 1.0F);
                    bb.addVertex(p01[0], p01[1], p01[2]).setColor(l.r * i0, l.g * i0, l.b * i0, 1.0F);
                    tris++;
                }
            }
        }
        return tris;
    }

    private static float[] disc(float cx, float cy, float cz, org.joml.Vector3f left, org.joml.Vector3f up, float x, float y) {
        return new float[] { cx + left.x() * x + up.x() * y, cy + left.y() * x + up.y() * y, cz + left.z() * x + up.z() * y };
    }
}
