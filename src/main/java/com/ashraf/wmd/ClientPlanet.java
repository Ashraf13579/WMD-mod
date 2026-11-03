package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.InflaterInputStream;
import org.joml.Matrix4f;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class ClientPlanet {

    private ClientPlanet() {}

    private static final ResourceLocation NEAR = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/planet_near");
    private static final ResourceLocation FAR = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/planet_far");
    private static final ResourceLocation CLOUDS = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/planet_clouds");
    private static final float H = 40.0F, CURVE = 520.0F;
    private static DynamicTexture near, far, clouds;
    private static PlanetPayloads.Info info;
    private static boolean nearDirty, farDirty, anyFar;
    private static final List<float[]> FLASHES = new ArrayList<>();
    private static final List<Long> STREAKS = new ArrayList<>();
    static final int STREAK_TICKS = 160;

    static void streak(long tick) { STREAKS.add(tick); }

    static ResourceLocation nearTexture() { return near != null && info != null ? NEAR : null; }

    static boolean drawMap(net.minecraft.client.gui.GuiGraphics g, int x0, int y0, int size) {
        drain();
        if (info == null || near == null) return false;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (anyFar && far != null) {
            int span = info.nearPx() * info.nearBlocks(), fpx = Math.max(1, span / info.farBlocks()), u0 = info.farPx() / 2 - fpx / 2;
            g.blit(FAR, x0, y0, size, size, (float) u0, (float) u0, fpx, fpx, info.farPx(), info.farPx());
        }
        int px = info.nearPx();
        g.blit(NEAR, x0, y0, size, size, 0.0F, 0.0F, px, px, px, px);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        return true;
    }
    static int[] nearFrame() { return info == null ? null : new int[] { info.gx(), info.gz(), info.nearPx(), info.nearBlocks() }; }

    private static void drain() {
        CustomPacketPayload p;
        while ((p = PlanetPayloads.PENDING.poll()) != null) {
            if (p instanceof PlanetPayloads.Info i) {
                if (info == null || info.key() != i.key() || near == null) reset(i);
            } else if (p instanceof PlanetPayloads.Tile t) {
                if (info == null || t.key() != info.key()) continue;
                byte[] rgba;
                try (InflaterInputStream in = new InflaterInputStream(new java.io.ByteArrayInputStream(t.data()))) { rgba = in.readAllBytes(); }
                catch (java.io.IOException e) { continue; }
                NativeImage img = (t.near() ? near : far).getPixels();
                if (img == null || rgba.length < Planet.TILE * Planet.TILE * 4) continue;
                for (int y = 0; y < Planet.TILE; y++) for (int x = 0; x < Planet.TILE; x++) {
                    int o = (y * Planet.TILE + x) * 4;
                    int abgr = ((rgba[o + 3] & 0xFF) << 24) | ((rgba[o + 2] & 0xFF) << 16) | ((rgba[o + 1] & 0xFF) << 8) | (rgba[o] & 0xFF);
                    img.setPixelRGBA(t.tx() * Planet.TILE + x, t.ty() * Planet.TILE + y, abgr);
                }
                if (t.near()) nearDirty = true; else { farDirty = true; anyFar = true; }
            } else if (p instanceof PlanetPayloads.Flash f) {
                var lv = Minecraft.getInstance().level;
                FLASHES.add(new float[] { f.dx(), f.dz(), f.tier(), lv == null ? 0 : lv.getGameTime() });
            }
        }
        if (nearDirty) { near.upload(); nearDirty = false; }
        if (farDirty) { far.upload(); farDirty = false; }
    }

    private static void reset(PlanetPayloads.Info i) {
        info = i;
        anyFar = false;
        var tm = Minecraft.getInstance().getTextureManager();
        if (near != null) near.close();
        if (far != null) far.close();
        near = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, i.nearPx(), i.nearPx(), true));
        far = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, i.farPx(), i.farPx(), true));
        tm.register(NEAR, near);
        tm.register(FAR, far);
        if (clouds == null) { clouds = makeClouds(); tm.register(CLOUDS, clouds); }
    }

    private static DynamicTexture makeClouds() {
        int n = 256;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, n, n, true);
        RandomSource r = RandomSource.create(4_011L);
        float[][] g = new float[16][16];
        for (int a = 0; a < 16; a++) for (int b = 0; b < 16; b++) g[a][b] = r.nextFloat();
        float[][] g2 = new float[64][64];
        for (int a = 0; a < 64; a++) for (int b = 0; b < 64; b++) g2[a][b] = r.nextFloat();
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            float v = 0.65F * lerp2(g, x / 16.0F, y / 16.0F, 16) + 0.35F * lerp2(g2, x / 4.0F, y / 4.0F, 64);
            float a = Mth.clamp((v - 0.52F) * 3.0F, 0.0F, 1.0F);
            int al = (int) (a * 200);
            img.setPixelRGBA(x, y, (al << 24) | 0x00FFFFFF);
        }
        return new DynamicTexture(img);
    }

    private static float lerp2(float[][] g, float x, float y, int m) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        float a = g[x0 % m][y0 % m], b = g[(x0 + 1) % m][y0 % m], c = g[x0 % m][(y0 + 1) % m], d = g[(x0 + 1) % m][(y0 + 1) % m];
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float y(float r) { return -H - r * r / (2.0F * CURVE); }

    static boolean draw(Matrix4f m) {
        drain();
        if (info == null || !anyFar) return false;
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        int n = Math.max(0, (int) Math.round(cam.x / Balance.ORBIT_SLOT_SPACING));
        float s = (float) (H / Balance.PLANET_ALT);
        float ox = (float) -((cam.x - (double) n * Balance.ORBIT_SLOT_SPACING) * s), oz = (float) -(cam.z * s);
        float farHalf = info.farPx() * info.farBlocks() / 2.0F * s, nearHalf = info.nearPx() * info.nearBlocks() / 2.0F * s;
        long tick = mc.level != null ? mc.level.getGameTime() : 0;
        StationPayloads.Drift dr = StationPayloads.drift;
        if (dr != null && (tick > dr.end() + 100 || System.currentTimeMillis() - StationPayloads.driftAt > 4_000L)) dr = null;
        if (dr != null) {
            float p = Mth.clamp((tick - dr.start()) / (float) Math.max(1L, dr.end() - dr.start()), 0.0F, 1.0F);
            float dx = (dr.toX() - dr.fromX()) * p, dz = (dr.toZ() - dr.fromZ()) * p, cap = info.farPx() * info.farBlocks() / 2.0F * 0.35F, len = (float) Math.hypot(dx, dz);
            if (len > cap) { dx *= cap / len; dz *= cap / len; }
            ox -= dx * s; oz -= dz * s;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FAR);
        grid(m, ox, oz, farHalf, farHalf, 0.0F, 0.0F, 1.0F, 40, 0.0F, true);
        RenderSystem.setShaderTexture(0, NEAR);
        grid(m, ox, oz, nearHalf, farHalf, 0.0F, 0.0F, 1.0F, 24, 0.02F, false);
        RenderSystem.setShaderTexture(0, CLOUDS);
        float drift = (tick % 24_000L) / 24_000.0F;
        grid(m, ox, oz, farHalf, farHalf, drift, drift * 0.4F, 5.0F, 40, 0.6F, true);

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (Iterator<float[]> it = FLASHES.iterator(); it.hasNext(); ) {
            float[] f = it.next();
            float age = tick - f[3];
            if (age > 80) { it.remove(); continue; }
            float k = 1.0F - age / 80.0F, rad = (f[2] >= 4 ? 520.0F : 240.0F) * s * (0.4F + 0.6F * Math.min(1.0F, age / 10.0F));
            float fx = ox + f[0] * s, fz = oz + f[1] * s;
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            float cy = y((float) Math.hypot(fx, fz)) + 0.05F;
            b.addVertex(m, fx, cy, fz).setColor(1.0F, 0.95F, 0.8F, k);
            for (int i = 0; i <= 24; i++) {
                double a = Math.PI * 2 * i / 24;
                b.addVertex(m, fx + (float) Math.cos(a) * rad, cy, fz + (float) Math.sin(a) * rad).setColor(1.0F, 0.6F, 0.3F, 0.0F);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        for (Iterator<Long> it = STREAKS.iterator(); it.hasNext(); ) {
            float age = tick - it.next();
            if (age > STREAK_TICKS) { it.remove(); continue; }
            float u = age / STREAK_TICKS, k = Math.min(1.0F, age / 10.0F) * (1.0F - u * u);
            float hx = farHalf * (-0.05F + 0.4F * u), hz = farHalf * (0.02F + 0.14F * u), lx = hx - farHalf * 0.09F, lz = hz - farHalf * 0.032F;
            float w = farHalf * 0.004F, nx = -(hz - lz), nz = hx - lx, nl = (float) Math.hypot(nx, nz);
            nx = nx / nl * w; nz = nz / nl * w;
            float hy = y((float) Math.hypot(hx, hz)) + 0.1F, ly = y((float) Math.hypot(lx, lz)) + 0.1F;
            BufferBuilder sb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            sb.addVertex(m, lx - nx, ly, lz - nz).setColor(1.0F, 0.5F, 0.2F, 0.0F);
            sb.addVertex(m, hx - nx, hy, hz - nz).setColor(1.0F, 0.9F, 0.7F, k);
            sb.addVertex(m, hx + nx, hy, hz + nz).setColor(1.0F, 0.9F, 0.7F, k);
            sb.addVertex(m, lx + nx, ly, lz + nz).setColor(1.0F, 0.5F, 0.2F, 0.0F);
            BufferUploader.drawWithShader(sb.buildOrThrow());
        }
        float rimY = y(farHalf);
        BufferBuilder limb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 64; i++) {
            double a0 = Math.PI * 2.0 * i / 64, a1 = Math.PI * 2.0 * (i + 1) / 64;
            float r0 = farHalf * 0.97F, r1 = farHalf * 1.06F;
            limb.addVertex(m, (float) Math.cos(a0) * r0, rimY, (float) Math.sin(a0) * r0).setColor(0.35F, 0.65F, 1.0F, 0.9F);
            limb.addVertex(m, (float) Math.cos(a1) * r0, rimY, (float) Math.sin(a1) * r0).setColor(0.35F, 0.65F, 1.0F, 0.9F);
            limb.addVertex(m, (float) Math.cos(a1) * r1, rimY + 16.0F, (float) Math.sin(a1) * r1).setColor(0.2F, 0.4F, 1.0F, 0.0F);
            limb.addVertex(m, (float) Math.cos(a0) * r1, rimY + 16.0F, (float) Math.sin(a0) * r1).setColor(0.2F, 0.4F, 1.0F, 0.0F);
        }
        BufferUploader.drawWithShader(limb.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        return true;
    }

    private static void grid(Matrix4f m, float ox, float oz, float half, float rim, float du, float dv, float uvScale, int cells, float lift, boolean fade) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < cells; i++) for (int j = 0; j < cells; j++) {
            for (int[] c : new int[][] { { i, j }, { i, j + 1 }, { i + 1, j + 1 }, { i + 1, j } }) {
                float u = c[0] / (float) cells, v = c[1] / (float) cells;
                float x = ox - half + 2 * half * u, z = oz - half + 2 * half * v;
                float r = (float) Math.sqrt(x * x + z * z);
                float a = fade ? 1.0F - Mth.clamp((r - rim * 0.82F) / (rim * 0.18F), 0.0F, 1.0F) : 1.0F;
                b.addVertex(m, x, y(r) + lift, z).setUv(u * uvScale + du, v * uvScale + dv).setColor(1.0F, 1.0F, 1.0F, a);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
