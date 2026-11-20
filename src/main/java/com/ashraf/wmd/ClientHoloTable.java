package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
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
public final class ClientHoloTable {

    private ClientHoloTable() {}

    static final int NX = Balance.HOLOT_GRID, NZ = Balance.HOLOT_GRID / 2;
    private static final long IDLE_CLOSE_MS = 5_000L, REBUILD_MS = 1_000L, SCAN_MS = 250L;
    private static final int FOG = 0x101A28, WATER_ID = 12;
    private static final int[] BRIGHT = { 180, 220, 255, 135 };
    static final float FLOOR = HoloTableBlockEntity.BED_Y + 0.6F;

    record View(double cx, double cz, float s, Direction f, boolean recon) {
        float[] toModel(double x, double z) {
            double[] l = HoloTableBlockEntity.toLocal(f, (x - cx) * s, (z - cz) * s);
            return new float[] { (float) l[0], HoloTableBlockEntity.BED_CZ + (float) l[1] };
        }
        double[] toWorld(float mx, float mz) {
            double[] w = HoloTableBlockEntity.toWorldVec(f, mx, mz - HoloTableBlockEntity.BED_CZ);
            return new double[] { cx + w[0] / s, cz + w[1] / s };
        }
        static boolean inside(float[] mp) {
            return Math.abs(mp[0]) <= HoloTableBlockEntity.BED_X && mp[1] >= HoloTableBlockEntity.BED_Z0 && mp[1] <= HoloTableBlockEntity.BED_Z1;
        }
    }

    static final class Model {
        VertexBuffer terrain, water;
        Level level;
        View view;
        int sig = Integer.MIN_VALUE, version = Integer.MIN_VALUE;
        float front = -1.0F, maxTop;
        final float[] top = new float[NX * NZ];

        final int[] col = new int[NX * NZ];
        final float[] ys = new float[NX * NZ];
        final boolean[] wet = new boolean[NX * NZ];
        long sampledMs, builtMs, lastDrawMs;
        boolean dirty = true;

        void close() {
            if (terrain != null) { terrain.close(); terrain = null; }
            if (water != null) { water.close(); water = null; }
        }
    }

    private record Req(Model m, Matrix4f pose, float alpha) {}

    private static final Map<Long, Model> MODELS = Statics.client(new HashMap<>());
    private static final List<Req> QUEUE = Statics.client(new ArrayList<>());
    private static Level cacheLevel;

    static boolean reconShown(HoloTableBlockEntity be) {
        ReconMapPayload mp = ReconView.map;
        UUID me = CcRenderers.me();
        return be.view() == CcMapTableBlockEntity.VIEW_RECON && me != null && me.equals(be.getOwner())
                && mp != null && mp.n() > 0 && mp.step() > 0 && mp.col() != null && mp.col().length == mp.n() * mp.n();
    }

    static View view(HoloTableBlockEntity be) {
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        if (reconShown(be)) {
            ReconMapPayload mp = ReconView.map;
            double span = mp.n() * (double) mp.step();
            return new View(mp.x0() + span / 2.0, mp.z0() + span / 2.0, (float) ((HoloTableBlockEntity.BED_Z1 - HoloTableBlockEntity.BED_Z0) / span), f, true);
        }
        return new View(be.viewX(), be.viewZ(), be.scale(), f, false);
    }

    static Model model(HoloTableBlockEntity be, View v, float scanFront) {
        Level level = be.getLevel();
        Model m = MODELS.computeIfAbsent(be.getBlockPos().asLong(), k -> new Model());
        m.level = level;
        int lvl = be.level();
        if (!v.recon()) want(v, level.dimension().location().toString(), level.getGameTime(), lvl);
        int sig = v.recon() ? 0x5EC0 + ReconView.version * 31 : be.zoom() * 31 + Mth.floor(v.cx()) * 7 + Mth.floor(v.cz()) * 13 + v.f().ordinal();
        int ver = v.recon() ? ReconView.version : HoloTerrainView.version;
        long now = System.currentTimeMillis();
        boolean local = !v.recon() && be.span() <= Balance.HOLOT_LOCAL_SPAN;
        if (sig != m.sig || (ver != m.version && now - m.sampledMs >= REBUILD_MS) || (local && now - m.sampledMs >= Balance.HOLOT_LOCAL_MS)) {
            m.sig = sig;
            m.version = ver;
            m.view = v;
            m.sampledMs = now;
            sample(m, be, v, lvl, local);
            m.dirty = true;
        }
        boolean scanning = scanFront < 1.0F;
        float front = scanning ? scanFront : 1.0F;
        if (m.dirty || (scanning && now - m.builtMs >= SCAN_MS && front != m.front) || (!scanning && m.front < 1.0F)) {
            m.front = front;
            m.builtMs = now;
            m.dirty = false;
            build(m, be);
        }
        return m;
    }

    private static void want(View v, String dim, long tick, int lvl) {
        if (HoloTerrainView.disabled()) return;
        int tb = WorldMap.TILE * (WorldMap.BASE << lvl);
        double[] a = v.toWorld(-HoloTableBlockEntity.BED_X, HoloTableBlockEntity.BED_Z0), b = v.toWorld(HoloTableBlockEntity.BED_X, HoloTableBlockEntity.BED_Z1);
        int tx0 = Math.floorDiv(Mth.floor(Math.min(a[0], b[0])), tb), tx1 = Math.floorDiv(Mth.floor(Math.max(a[0], b[0])), tb);
        int tz0 = Math.floorDiv(Mth.floor(Math.min(a[1], b[1])), tb), tz1 = Math.floorDiv(Mth.floor(Math.max(a[1], b[1])), tb);
        List<int[]> l = new ArrayList<>();
        for (int tz = tz0; tz <= tz1; tz++) for (int tx = tx0; tx <= tx1; tx++) l.add(new int[] { tx, tz });
        int cx = Math.floorDiv(Mth.floor(v.cx()), tb), cz = Math.floorDiv(Mth.floor(v.cz()), tb);
        l.sort((p, q) -> Integer.compare(Math.abs(p[0] - cx) + Math.abs(p[1] - cz), Math.abs(q[0] - cx) + Math.abs(q[1] - cz)));
        HoloTerrainView.want(dim, tick, lvl, l);
    }

    private static int mapColour(int packed) {
        int id = packed >> 2;
        if (id <= 0) return -1;
        int base = MapColor.byId(id).col, k = BRIGHT[packed & 3];
        return (((base >> 16) & 255) * k / 255) << 16 | (((base >> 8) & 255) * k / 255) << 8 | ((base & 255) * k / 255);
    }

    private static void sample(Model m, HoloTableBlockEntity be, View v, int lvl, boolean local) {
        Level level = be.getLevel();
        float cw = 2.0F * HoloTableBlockEntity.BED_X / NX, cd = (HoloTableBlockEntity.BED_Z1 - HoloTableBlockEntity.BED_Z0) / NZ;
        ReconMapPayload mp = v.recon() ? ReconView.map : null;
        short[] rh = mp != null && mp.hts() != null && mp.hts().length == mp.n() * mp.n() ? mp.hts() : null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int t = WorldMap.BASE << lvl, tb = WorldMap.TILE * t;
        for (int j = 0; j < NZ; j++) for (int i = 0; i < NX; i++) {
            int k = j * NX + i;
            double[] w = v.toWorld(-HoloTableBlockEntity.BED_X + (i + 0.5F) * cw, HoloTableBlockEntity.BED_Z0 + (j + 0.5F) * cd);
            m.col[k] = FOG; m.ys[k] = Float.NaN; m.wet[k] = false;
            int bx = Mth.floor(w[0]), bz = Mth.floor(w[1]);
            if (mp != null) {
                int si = Math.floorDiv(bx - mp.x0(), mp.step()), sj = Math.floorDiv(bz - mp.z0(), mp.step());
                if (si < 0 || sj < 0 || si >= mp.n() || sj >= mp.n()) continue;
                int o = sj * mp.n() + si, pv = mp.col()[o] & 0xFF;
                if (pv < 4) continue;
                m.col[k] = mapColour(pv);
                m.wet[k] = (pv >> 2) == WATER_ID;
                m.ys[k] = rh != null && rh[o] != ReconData.NO_H ? rh[o] : Float.NaN;
                if (Float.isNaN(m.ys[k])) m.ys[k] = 64.0F;
                continue;
            }
            boolean hasTile = false;
            if (!local && !HoloTerrainView.disabled()) {
                int tx = Math.floorDiv(bx, tb), tz = Math.floorDiv(bz, tb);
                HoloTerrainView.Entry e = HoloTerrainView.tile(lvl, tx, tz);
                if (e != null) {
                    int ii = Mth.clamp(Math.floorDiv(bx - tx * tb, t), 0, WorldMap.TILE - 1), jj = Mth.clamp(Math.floorDiv(bz - tz * tb, t), 0, WorldMap.TILE - 1);
                    int o = jj * WorldMap.TILE + ii, pv = e.ids[o] & 0xFF, h = e.heights[o] & 0xFF;
                    if (pv >= 4 && h != 0) {
                        m.col[k] = mapColour(pv);
                        m.ys[k] = h * 2 - 64;
                        m.wet[k] = (pv >> 2) == WATER_ID;
                        hasTile = true;
                    }
                }
            }
            if (!hasTile && level.hasChunk(bx >> 4, bz >> 4)) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
                pos.set(bx, y, bz);
                BlockState st = level.getBlockState(pos);
                boolean water = st.getFluidState().is(FluidTags.WATER);
                m.col[k] = CcHolo.colourAt(level, st, pos);
                m.ys[k] = y;
                m.wet[k] = water;
            }
        }

        float[] known = new float[NX * NZ];
        int c = 0;
        for (int k = 0; k < NX * NZ; k++) if (!Float.isNaN(m.ys[k])) known[c++] = m.ys[k];
        float lo, hi = -Float.MAX_VALUE;
        if (c == 0) { lo = 63.0F; hi = 64.0F; }
        else {
            java.util.Arrays.sort(known, 0, c);
            lo = known[(int) Math.floor(0.10 * (c - 1))] - 3.0F;
            hi = known[c - 1];
        }
        float vs = Math.min(Balance.HOLOT_EXAG[Mth.clamp(be.zoom(), 0, Balance.HOLOT_EXAG.length - 1)] * v.s(), Balance.HOLOT_RELIEF / Math.max(2.0F, hi - lo + 2.0F));
        float mx = 0.0F;
        for (int k = 0; k < NX * NZ; k++) {
            m.top[k] = Float.isNaN(m.ys[k]) ? 0.3F : Math.max(0.4F, (m.ys[k] - lo + 1.0F) * vs);
            mx = Math.max(mx, m.top[k]);
        }
        m.maxTop = mx;
    }

    private static int cast(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r * 3 + 0x30) / 4) << 16 | ((g * 3 + 0xC8) / 4) << 8 | ((b * 3 + 0xFF) / 4);
    }

    private static void build(Model m, HoloTableBlockEntity be) {
        float cw = 2.0F * HoloTableBlockEntity.BED_X / NX, cd = (HoloTableBlockEntity.BED_Z1 - HoloTableBlockEntity.BED_Z0) / NZ;
        int frontCol = m.front >= 1.0F ? NX : Mth.clamp((int) (m.front * NX), 0, NX);
        m.close();
        ByteBufferBuilder mem = new ByteBufferBuilder(1 << 20);
        try {
            BufferBuilder b = new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            List<float[]> water = new ArrayList<>();
            int n = 0;
            for (int j = 0; j < NZ; j++) for (int i = 0; i < frontCol; i++) {
                int k = j * NX + i;
                float t = m.top[k], x0 = -HoloTableBlockEntity.BED_X + i * cw, z0 = HoloTableBlockEntity.BED_Z0 + j * cd, x1 = x0 + cw, z1 = z0 + cd, y = FLOOR + t;
                boolean known = !Float.isNaN(m.ys[k]);
                int c = known ? cast(m.col[k]) : FOG;
                if (m.wet[k] && known) {
                    water.add(new float[] { x0, z0, x1, z1, y + 0.05F, c });
                    y = FLOOR + Math.max(0.3F, t - 0.6F);
                    c = CcHolo.shade(c, 0.5F);
                }
                q(b, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, c); n++;
                float yt = y - FLOOR;
                float w = i > 0 ? m.top[k - 1] : 0.0F, e = i + 1 < frontCol ? m.top[k + 1] : 0.0F, no = j > 0 ? m.top[k - NX] : 0.0F, so = j + 1 < NZ ? m.top[k + NX] : 0.0F;
                if (w < yt) { q(b, x0, y, z0, x0, y, z1, x0, FLOOR + w, z1, x0, FLOOR + w, z0, CcHolo.shade(c, 0.62F)); n++; }
                if (e < yt) { q(b, x1, y, z1, x1, y, z0, x1, FLOOR + e, z0, x1, FLOOR + e, z1, CcHolo.shade(c, 0.62F)); n++; }
                if (no < yt) { q(b, x1, y, z0, x0, y, z0, x0, FLOOR + no, z0, x1, FLOOR + no, z0, CcHolo.shade(c, 0.76F)); n++; }
                if (so < yt) { q(b, x0, y, z1, x1, y, z1, x1, FLOOR + so, z1, x0, FLOOR + so, z1, CcHolo.shade(c, 0.76F)); n++; }
            }
            float bx1 = -HoloTableBlockEntity.BED_X + frontCol * cw;
            if (frontCol > 0) { q(b, -HoloTableBlockEntity.BED_X, FLOOR - 0.02F, HoloTableBlockEntity.BED_Z0, bx1, FLOOR - 0.02F, HoloTableBlockEntity.BED_Z0, bx1, FLOOR - 0.02F, HoloTableBlockEntity.BED_Z1,
                    -HoloTableBlockEntity.BED_X, FLOOR - 0.02F, HoloTableBlockEntity.BED_Z1, 0xFF0A1A2E); n++; }
            m.terrain = n > 0 ? upload(b.build()) : null;
            if (!water.isEmpty()) {
                BufferBuilder wb = new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (float[] w : water) q(wb, w[0], w[4], w[1], w[0], w[4], w[3], w[2], w[4], w[3], w[2], w[4], w[1], (0x90 << 24) | (CcHolo.shade((int) w[5], 1.15F) & 0xFFFFFF));
                m.water = upload(wb.build());
            }
        } finally {
            mem.close();
        }
    }

    static float topAt(Model m, float x, float z) {
        float cw = 2.0F * HoloTableBlockEntity.BED_X / NX, cd = (HoloTableBlockEntity.BED_Z1 - HoloTableBlockEntity.BED_Z0) / NZ;
        int i = (int) Math.floor((x + HoloTableBlockEntity.BED_X) / cw), j = (int) Math.floor((z - HoloTableBlockEntity.BED_Z0) / cd);
        if (i < 0 || j < 0 || i >= NX || j >= NZ) return 0.0F;
        return m.top[j * NX + i];
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

    static void queue(Model m, Matrix4f pose, float alpha) {
        if (m == null || m.terrain == null || QUEUE.size() >= 8) return;
        m.lastDrawMs = System.currentTimeMillis();
        QUEUE.add(new Req(m, new Matrix4f(pose), alpha));
    }

    @SubscribeEvent
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || QUEUE.isEmpty()) return;
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
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
                RenderSystem.setShaderColor(0.95F, 1.0F, 1.06F, r.alpha());
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

    static void clearStatics() {
        for (Model m : MODELS.values()) m.close();
        MODELS.clear();
        QUEUE.clear();
        cacheLevel = null;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != cacheLevel) {
            for (Model m : MODELS.values()) m.close();
            MODELS.clear();
            QUEUE.clear();
            cacheLevel = mc.level;
            return;
        }
        if (mc.level.getGameTime() % 20 != 9 || MODELS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Model> it = MODELS.values().iterator(); it.hasNext(); ) {
            Model m = it.next();
            if (m.level != mc.level || now - m.lastDrawMs > IDLE_CLOSE_MS) { m.close(); it.remove(); }
        }
    }
}
