package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class CcHolo {

    private CcHolo() {}

    public static final int KIND_HOME = 0, KIND_RECON = 1;
    public static final int HOME_R = 64, HOME_N = 2 * HOME_R, MAX_N = 128;
    static final long HOME_REBUILD_MS = 10_000L, CHUNK_CHECK_MS = 1_000L, RECON_MIN_MS = 3_000L, IDLE_CLOSE_MS = 5_000L;
    static final float EXAG_HOME = 1.5F, EXAG_RECON = 3.0F;
    static final int FOG = 0x141C26, BASE_COL = 0xFF0A1A2E;

    public static final class Model {
        final long key;
        Level level;
        public int kind = -1, gn;
        public float[] top;

        public double cx, cz, halfB;
        public float maxTop;
        public boolean ok;
        VertexBuffer terrain, water;
        long builtMs, checkMs, lastDrawMs;
        int chunkSig, reconVersion = Integer.MIN_VALUE;

        int detail = 1;

        Model(long key) { this.key = key; }

        void closeBuffers() {
            if (terrain != null) { terrain.close(); terrain = null; }
            if (water != null) { water.close(); water = null; }
        }
    }

    private static final Map<Long, Model> MODELS = new HashMap<>();

    private record Req(Model m, Matrix4f pose, float alpha) {}
    private static final List<Req> QUEUE = new ArrayList<>();
    private static Level cacheLevel;

    public static Model home(Level level, BlockPos pos, double cx, double cz) {
        Model m = model(level, pos);
        long now = System.currentTimeMillis();
        int ccx = (int) Math.floor(cx), ccz = (int) Math.floor(cz);
        boolean rebuild = m.kind != KIND_HOME || now - m.builtMs >= HOME_REBUILD_MS || m.detail != ClientConfig.holoStep();
        if (!rebuild && now - m.checkMs >= CHUNK_CHECK_MS) {
            m.checkMs = now;
            rebuild = chunkSig(level, ccx, ccz) != m.chunkSig;
        }
        if (rebuild) {
            m.builtMs = m.checkMs = now;
            m.chunkSig = chunkSig(level, ccx, ccz);
            buildHome(m, level, ccx, ccz);
            m.kind = KIND_HOME;
        }
        return m;
    }

    public static Model recon(Level level, BlockPos pos) {
        ReconMapPayload mp = ReconView.map;
        if (mp == null || mp.n() <= 0 || mp.col().length != mp.n() * mp.n() || mp.step() <= 0) return null;
        Model m = model(level, pos);
        long now = System.currentTimeMillis();
        if (m.kind != KIND_RECON || (ReconView.version != m.reconVersion && now - m.builtMs >= RECON_MIN_MS) || m.detail != ClientConfig.holoStep()) {
            m.reconVersion = ReconView.version;
            m.builtMs = now;
            buildRecon(m, mp);
            m.kind = KIND_RECON;
        }
        return m.ok ? m : null;
    }

    private static Model model(Level level, BlockPos pos) {
        if (cacheLevel != level) { clearAll(); cacheLevel = level; }
        Model m = MODELS.computeIfAbsent(pos.asLong(), Model::new);
        m.level = level;
        m.lastDrawMs = System.currentTimeMillis();
        return m;
    }

    private static int chunkSig(Level level, int ccx, int ccz) {
        int h = 1;
        for (int cz = (ccz - HOME_R) >> 4; cz <= (ccz + HOME_R - 1) >> 4; cz++)
            for (int cx = (ccx - HOME_R) >> 4; cx <= (ccx + HOME_R - 1) >> 4; cx++)
                h = h * 31 + (level.getChunkSource().hasChunk(cx, cz) ? 1 : 0);
        return h;
    }

    private static final Map<BlockState, int[]> STATE_COL = new IdentityHashMap<>();
    private static final Map<ResourceLocation, Integer> SPRITE_COL = new HashMap<>();

    private static int[] stateColour(BlockState st) {
        int[] c = STATE_COL.get(st);
        if (c != null) return c;
        int rgb = -1, tint = -1;
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(st);
            RandomSource r = RandomSource.create(42L);
            List<BakedQuad> qs = model.getQuads(st, Direction.UP, r);
            BakedQuad q = qs.isEmpty() ? null : qs.get(0);
            if (q == null) for (BakedQuad x : model.getQuads(st, null, r)) if (x.getDirection() == Direction.UP) { q = x; break; }
            TextureAtlasSprite sp = q != null ? q.getSprite() : model.getParticleIcon();
            if (q != null && q.isTinted()) tint = q.getTintIndex();
            rgb = spriteColour(sp);
        } catch (Throwable t) {
            rgb = -1;
        }
        c = new int[] { rgb, tint };
        STATE_COL.put(st, c);
        return c;
    }

    private static int spriteColour(TextureAtlasSprite sp) {
        if (sp == null) return -1;
        ResourceLocation name = sp.contents().name();
        if (name.equals(MissingTextureAtlasSprite.getLocation())) return -1;
        Integer c = SPRITE_COL.get(name);
        if (c != null) return c;
        int out = -1;
        try {
            NativeImage img = sp.contents().getOriginalImage();
            int w = Math.min(sp.contents().width(), img.getWidth()), h = Math.min(sp.contents().height(), img.getHeight());
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);
                if ((p >>> 24) < 128) continue;
                r += p & 255; g += (p >> 8) & 255; b += (p >> 16) & 255; n++;
            }
            if (n > 0) out = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        } catch (Throwable t) {
            out = -1;
        }
        SPRITE_COL.put(name, out);
        return out;
    }

    static int colourAt(Level level, BlockState st, BlockPos pos) {
        int[] c = stateColour(st);
        int rgb = c[0];
        if (rgb == -1) {
            MapColor mc = st.getMapColor(level, pos);
            return mc == MapColor.NONE ? 0x808080 : mc.col;
        }
        if (c[1] >= 0) {
            int t = Minecraft.getInstance().getBlockColors().getColor(st, level, pos, c[1]);
            if (t != -1) rgb = mul(rgb, t);
        }
        return rgb;
    }

    private static int mul(int a, int b) {
        return (((a >> 16) & 255) * ((b >> 16) & 255) / 255) << 16 | (((a >> 8) & 255) * ((b >> 8) & 255) / 255) << 8 | ((a & 255) * (b & 255) / 255);
    }

    static int shade(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    private static void buildHome(Model m, Level level, int ccx, int ccz) {
        int cs = ClientConfig.holoStep();
        m.detail = cs;
        int n = HOME_N / cs, x0 = ccx - HOME_R, z0 = ccz - HOME_R, minB = level.getMinBuildHeight();
        int[] ys = new int[n * n], col = new int[n * n], wy = new int[n * n];
        boolean[] known = new boolean[n * n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int k = j * n + i, x = x0 + i * cs + cs / 2, z = z0 + j * cs + cs / 2;
            wy[k] = Integer.MIN_VALUE;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (h <= minB) continue;
            int y = h - 1;
            pos.set(x, y, z);
            BlockState st = level.getBlockState(pos);
            if (st.getFluidState().is(FluidTags.WATER)) {
                wy[k] = y;
                int bed = y, lim = Math.max(minB, y - 32);
                BlockState b = st;
                while (bed > lim) {
                    bed--;
                    pos.setY(bed);
                    b = level.getBlockState(pos);
                    if (!b.getFluidState().is(FluidTags.WATER)) break;
                }
                y = bed;
                col[k] = b.getFluidState().is(FluidTags.WATER) ? 0x10204A : shade(colourAt(level, b, pos), 0.7F);
                pos.setY(wy[k]);
                int wc = BiomeColors.getAverageWaterColor(level, pos);
                col[k] = mix(col[k], wc, 0.35F);
            } else col[k] = colourAt(level, st, pos);
            ys[k] = y;
            known[k] = true;
            min = Math.min(min, y);
            max = Math.max(max, Math.max(y, wy[k]));
        }
        if (min == Integer.MAX_VALUE) { min = 60; max = 61; }
        else {
            int[] surf = new int[n * n];
            int c = 0;
            for (int k = 0; k < n * n; k++) if (known[k]) surf[c++] = wy[k] != Integer.MIN_VALUE ? wy[k] : ys[k];
            min = lowPercentile(surf, c, 0.10) - 3;
        }
        float hs = 2.0F * CcModelSpec.HOLO_MODEL_HALF / HOME_N;
        float vs = Math.min(EXAG_HOME * hs, CcModelSpec.HOLO_MODEL_RELIEF / (max - min + 2.0F));
        float[] top = new float[n * n], wTop = new float[n * n];
        int[] wCol = new int[n * n];
        for (int k = 0; k < n * n; k++) {
            wTop[k] = Float.NaN;
            if (!known[k]) { top[k] = 0.4F; col[k] = FOG; continue; }
            top[k] = Math.max(0.4F, (ys[k] - min + 2.0F) * vs);
            if (wy[k] != Integer.MIN_VALUE) {
                wTop[k] = Math.max(0.5F, (wy[k] - min + 1.9F) * vs);
                pos.set(x0 + (k % n) * cs + cs / 2, wy[k], z0 + (k / n) * cs + cs / 2);
                wCol[k] = BiomeColors.getAverageWaterColor(level, pos);
            }
        }
        m.cx = ccx;
        m.cz = ccz;
        m.halfB = HOME_R;
        finish(m, n, top, col, wTop, wCol);
    }

    private static int lowPercentile(int[] a, int c, double p) {
        if (c <= 0) return 60;
        java.util.Arrays.sort(a, 0, c);
        return a[Math.max(0, Math.min(c - 1, (int) Math.floor(p * (c - 1))))];
    }

    private static void buildRecon(Model m, ReconMapPayload mp) {
        int maxN = MAX_N / ClientConfig.holoStep();
        m.detail = ClientConfig.holoStep();
        int sn = mp.n(), stride = Math.max(1, (sn + maxN - 1) / maxN), n = sn / stride;
        byte[] cb = mp.col();
        short[] hts = mp.hts() != null && mp.hts().length == sn * sn ? mp.hts() : null;
        int waterId = MapColor.WATER.id;
        int[] ys = new int[n * n], col = new int[n * n];
        boolean[] known = new boolean[n * n], hasY = new boolean[n * n], wet = new boolean[n * n];
        long sum = 0;
        int cnt = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int k = j * n + i, si = (j * stride) * sn + i * stride, v = cb[si] & 0xFF;
            if (v < 4) continue;
            known[k] = true;
            int id = v >> 2;
            MapColor mc = MapColor.byId(id);
            col[k] = mc == MapColor.NONE ? 0x808080 : mc.col;
            wet[k] = id == waterId;
            if (hts != null && hts[si] != ReconData.NO_H) {
                ys[k] = hts[si];
                hasY[k] = true;
                sum += ys[k];
                cnt++;
                min = Math.min(min, ys[k]);
                max = Math.max(max, ys[k]);
            }
        }
        int mean = cnt > 0 ? (int) (sum / cnt) : 64;
        if (cnt == 0) { min = mean; max = mean; }
        if (cnt > 0) {
            int[] hv = new int[cnt];
            int c = 0;
            for (int k = 0; k < n * n; k++) if (hasY[k]) hv[c++] = ys[k];
            min = lowPercentile(hv, c, 0.10);
        }
        min -= 3;
        int span = sn * mp.step();
        float hs = 2.0F * CcModelSpec.HOLO_MODEL_HALF / span;
        float vs = Math.min(EXAG_RECON * hs, CcModelSpec.HOLO_MODEL_RELIEF / (max - min + 2.0F));
        float[] top = new float[n * n], wTop = new float[n * n];
        int[] wCol = new int[n * n];
        for (int k = 0; k < n * n; k++) {
            wTop[k] = Float.NaN;
            if (!known[k]) { top[k] = 0.4F; col[k] = FOG; continue; }
            int y = hasY[k] ? ys[k] : mean;
            if (wet[k]) {
                wTop[k] = Math.max(0.5F, (y - min + 1.9F) * vs);
                wCol[k] = col[k];
                top[k] = Math.max(0.4F, (y - 3 - min + 2.0F) * vs);
                col[k] = shade(col[k], 0.45F);
            } else top[k] = Math.max(0.4F, (y - min + 2.0F) * vs);
        }
        m.cx = mp.x0() + span / 2.0;
        m.cz = mp.z0() + span / 2.0;
        m.halfB = span / 2.0;
        finish(m, n, top, col, wTop, wCol);
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k), g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k),
                bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | g << 8 | bl;
    }

    private static void finish(Model m, int n, float[] top, int[] col, float[] wTop, int[] wCol) {
        m.closeBuffers();
        m.gn = n;
        m.top = top;
        float mx = 0.0F;
        for (float t : top) mx = Math.max(mx, t);
        for (float t : wTop) if (!Float.isNaN(t)) mx = Math.max(mx, t);
        m.maxTop = mx;
        float half = CcModelSpec.HOLO_MODEL_HALF, cell = 2.0F * half / n, F = CcModelSpec.HOLO_MODEL_BASE;

        ByteBufferBuilder mem = new ByteBufferBuilder(1 << 20);
        try {
            BufferBuilder b = new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                int k = j * n + i;
                float t = top[k], x0 = -half + i * cell, z0 = -half + j * cell, x1 = x0 + cell, z1 = z0 + cell, y = F + t;
                int c = col[k];
                float tn = j > 0 ? top[k - n] : t;
                int ct = shade(c, t > tn + 0.01F ? 1.1F : t < tn - 0.01F ? 0.82F : 1.0F);
                q(b, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, ct);
                float w = i > 0 ? top[k - 1] : 0.0F, e = i + 1 < n ? top[k + 1] : 0.0F, no = j > 0 ? top[k - n] : 0.0F, so = j + 1 < n ? top[k + n] : 0.0F;
                if (w < t) q(b, x0, y, z0, x0, y, z1, x0, F + w, z1, x0, F + w, z0, shade(c, 0.62F));
                if (e < t) q(b, x1, y, z1, x1, y, z0, x1, F + e, z0, x1, F + e, z1, shade(c, 0.62F));
                if (no < t) q(b, x1, y, z0, x0, y, z0, x0, F + no, z0, x1, F + no, z0, shade(c, 0.76F));
                if (so < t) q(b, x0, y, z1, x1, y, z1, x1, F + so, z1, x0, F + so, z1, shade(c, 0.76F));
            }
            q(b, -half, F - 0.02F, -half, half, F - 0.02F, -half, half, F - 0.02F, half, -half, F - 0.02F, half, BASE_COL);
            m.terrain = upload(b.build());

            BufferBuilder wb = new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int j = 0; j < n; j++) {
                int i = 0;
                while (i < n) {
                    float wt = wTop[j * n + i];
                    if (Float.isNaN(wt)) { i++; continue; }
                    int c = wCol[j * n + i], s0 = i;
                    while (i < n && wTop[j * n + i] == wt && wCol[j * n + i] == c) i++;
                    float x0 = -half + s0 * cell, x1 = -half + i * cell, z0 = -half + j * cell, z1 = z0 + cell, y = F + wt;
                    q(wb, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, (0x90 << 24) | (shade(c, 1.15F) & 0xFFFFFF));
                }
            }
            m.water = upload(wb.build());
        } finally {
            mem.close();
        }
        m.ok = m.terrain != null;
    }

    private static void q(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int rgb) {
        int argb = (rgb >>> 24) == 0 ? 0xFF000000 | rgb : rgb;
        b.addVertex(x0, y0, z0).setColor(argb);
        b.addVertex(x1, y1, z1).setColor(argb);
        b.addVertex(x2, y2, z2).setColor(argb);
        b.addVertex(x3, y3, z3).setColor(argb);
    }

    private static VertexBuffer upload(MeshData md) {
        if (md == null) return null;
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(md);
        VertexBuffer.unbind();
        return vb;
    }

    public static void queue(Model m, Matrix4f pose, float alpha) {
        if (m == null || !m.ok || QUEUE.size() >= 16) return;
        m.lastDrawMs = System.currentTimeMillis();
        QUEUE.add(new Req(m, new Matrix4f(pose), alpha));
    }

    @SubscribeEvent
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || QUEUE.isEmpty()) return;

        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        try {
            ShaderInstance sh = GameRenderer.getPositionColorShader();
            if (sh == null) return;
            Matrix4f proj = RenderSystem.getProjectionMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (Req r : QUEUE) {
                Model m = r.m();
                if (m.terrain == null || m.terrain.isInvalid()) continue;
                Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(r.pose());

                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.depthMask(true);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                m.terrain.bind();
                m.terrain.drawWithShader(mv, proj, sh);

                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.setShaderColor(0.9F, 0.97F, 1.05F, r.alpha());
                m.terrain.drawWithShader(mv, proj, sh);

                if (m.water != null && !m.water.isInvalid()) {
                    RenderSystem.depthMask(false);
                    m.water.bind();
                    m.water.drawWithShader(mv, proj, sh);
                    RenderSystem.depthMask(true);
                }
                VertexBuffer.unbind();
            }
        } finally {
            QUEUE.clear();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != cacheLevel) {
            if (!MODELS.isEmpty() || cacheLevel != null) { clearAll(); cacheLevel = mc.level; }
            QUEUE.clear();
            return;
        }
        if (mc.level.getGameTime() % 20 != 7 || MODELS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Model> it = MODELS.values().iterator(); it.hasNext(); ) {
            Model m = it.next();
            if (m.level != mc.level || now - m.lastDrawMs > IDLE_CLOSE_MS) { m.closeBuffers(); it.remove(); }
        }
    }

    private static void clearAll() {
        for (Model m : MODELS.values()) m.closeBuffers();
        MODELS.clear();
        QUEUE.clear();
        STATE_COL.clear();
        SPRITE_COL.clear();
    }
}
