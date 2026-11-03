package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientSnapshot {

    private ClientSnapshot() {}

    private static final class ChunkData {
        final int[] top = new int[256];
        final BlockState[][] cols = new BlockState[256][];
        Biome biome;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
    }

    private static final class Snap {
        int id, tx, tz, r, farR = Balance.RG_SNAP_FAR_R;
        final Map<Long, ChunkData> chunks = new HashMap<>();
        final Map<Long, Map<RenderType, VertexBuffer>> sections = new HashMap<>();
        final LinkedHashSet<Long> dirty = new LinkedHashSet<>(), urgent = new LinkedHashSet<>();
        final Map<Long, Far> far = new HashMap<>();

        final Map<Long, Region> regions = new HashMap<>();

        double sr, sg, sb, sh, wh;
        int minH = Integer.MAX_VALUE;
        int cells, wet;
    }

    private static final int FAR_SKIRT = 48;

    private static final class Far {
        final int cx, cz; final short[] h; final int[] rgb;
        Far(int cx, int cz, short[] h, int[] rgb) { this.cx = cx; this.cz = cz; this.h = h; this.rgb = rgb; }
    }

    private static final class Region {
        VertexBuffer vb; boolean dirty = true; long builtAt;
    }

    private static final int FAR_REGION = 8, FAR_REBUILDS_PER_FRAME = 8;
    private static final long FAR_REBUILD_MS = 500L;

    private static net.minecraft.client.renderer.texture.DynamicTexture white;

    private static Snap snap;

    private static final java.util.LinkedHashMap<Integer, Snap> SNAPS = new java.util.LinkedHashMap<>();
    private static final int MAX_SNAPS = 3;

    static boolean show;

    static int chunks() { return snap == null ? 0 : snap.chunks.size(); }

    static void carve(double x, double y, double z, double radius, double depthFactor, boolean glass) { carve(x, y, z, radius, depthFactor, glass, false); }

    static void carve(double x, double y, double z, double radius, double depthFactor, boolean glass, boolean peak) {
        if (snap == null || radius < 2.0) return;
        int gx = (int) Math.floor(x), gz = (int) Math.floor(z), cy = (int) Math.floor(y);
        double depth = radius * depthFactor;
        int reach = (int) Math.ceil(radius * 1.2) + 1, c0x = (gx - reach) >> 4, c1x = (gx + reach) >> 4, c0z = (gz - reach) >> 4, c1z = (gz + reach) >> 4;
        BlockState[] floorMix = glass
                ? new BlockState[] { Blocks.TINTED_GLASS.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.GREEN_STAINED_GLASS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState() }
                : new BlockState[] { Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState() };
        BlockState rock = Blocks.STONE.defaultBlockState();
        java.util.List<long[]> order = new java.util.ArrayList<>();
        for (int ax = c0x; ax <= c1x; ax++) for (int az = c0z; az <= c1z; az++) order.add(new long[] { (long) (ax - (gx >> 4)) * (ax - (gx >> 4)) + (long) (az - (gz >> 4)) * (az - (gz >> 4)), ax, az });
        order.sort((p, q) -> Long.compare(p[0], q[0]));
        for (long[] o : order) {
            int ax = (int) o[1], az = (int) o[2];
            ChunkData d = snap.chunks.get(ChunkPos.asLong(ax, az));
            if (d == null) continue;
            int lo = d.minY, hi = d.maxY;
            boolean any = false;
            for (int c = 0; c < 256; c++) {
                int bx = (ax << 4) + (c & 15), bz = (az << 4) + (c >> 4);
                double dx = bx + 0.5 - (gx + 0.5), dz = bz + 0.5 - (gz + 0.5), rr = (dx * dx + dz * dz) / (radius * radius);
                double nxz = Crater.noise(bx, bz) * 0.14;
                if (rr >= 1.0 + nxz + 0.04) continue;
                int floor = (int) Math.floor(cy - Crater.peakDepth(depth, rr, peak) * Math.sqrt(Math.max(0.0, 1.0 + nxz - rr)));
                if (d.top[c] <= floor) continue;

                int low = floor;
                for (int[] off : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                    int nx = bx + off[0], nz = bz + off[1];
                    double ndx = nx + 0.5 - (gx + 0.5), ndz = nz + 0.5 - (gz + 0.5), nrr = (ndx * ndx + ndz * ndz) / (radius * radius), nn = Crater.noise(nx, nz) * 0.14;
                    if (nrr < 1.0 + nn + 0.04) low = Math.min(low, (int) Math.floor(cy - Crater.peakDepth(depth, nrr, peak) * Math.sqrt(Math.max(0.0, 1.0 + nn - nrr))));
                }
                BlockState[] col = new BlockState[Math.max(6, Math.min(64, floor - low + 3))];
                col[0] = floorMix[Math.floorMod(bx * 31 + bz * 17, floorMix.length)];
                col[1] = floorMix[Math.floorMod(bx * 7 + bz * 13 + 1, floorMix.length)];
                for (int i = 2; i < col.length; i++) col[i] = rock;
                d.top[c] = floor;
                d.cols[c] = col;
                d.minY = Math.min(d.minY, floor - col.length);
                any = true;
            }
            if (!any) continue;
            lo = Math.min(lo, d.minY);
            for (int bx = -1; bx <= 1; bx++) for (int bz = -1; bz <= 1; bz++) {
                if (snap.chunks.get(ChunkPos.asLong(ax + bx, az + bz)) == null) continue;
                for (int sy = Math.floorDiv(lo - 1, 16); sy <= Math.floorDiv(hi, 16); sy++) snap.urgent.add(SectionPos.asLong(ax + bx, sy, az + bz));
            }
        }
    }

    static double complete() {
        if (snap == null) return 0.0;
        int side = 2 * ((snap.r + 15) >> 4) + 1;
        return Math.min(1.0, snap.chunks.size() / (double) (side * side));
    }

    static boolean covers(double x, double z) {
        if (snap != null && Math.abs(x - snap.tx) < snap.r && Math.abs(z - snap.tz) < snap.r) return true;
        for (Snap c : SNAPS.values())
            if (Math.abs(x - c.tx) < c.r && Math.abs(z - c.tz) < c.r) { snap = c; return true; }
        return false;
    }

    static int realR() { return snap != null ? snap.r : Balance.RG_SNAP_R; }
    static int farR() { return snap != null ? snap.farR : Balance.RG_SNAP_FAR_R; }

    static void handle(SnapshotPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientReplay.record(p);
        switch (p.op()) {
            case SnapshotPayload.START -> {
                Snap old = SNAPS.remove(p.id());
                if (old != null) free(old);
                while (SNAPS.size() >= MAX_SNAPS) free(SNAPS.remove(SNAPS.keySet().iterator().next()));
                snap = new Snap();
                snap.id = p.id(); snap.tx = p.a(); snap.tz = p.b(); snap.r = p.c(); if (p.biome() > 0) snap.farR = p.biome();
                SNAPS.put(snap.id, snap);
                DEBRIS.clear();
            }
            case SnapshotPayload.END -> {
                Snap gone = SNAPS.remove(p.id());
                if (gone != null) free(gone);
                if (gone != null && gone == snap) { snap = null; for (Snap c : SNAPS.values()) snap = c; }
            }
            case SnapshotPayload.FAR -> {
                Snap was = snap;
                snap = SNAPS.get(p.id());
                try {
                if (snap == null) return;
                int n = 16 / Balance.RG_SNAP_FAR_CELL;
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(p.data()));
                short[] h = new short[n * n];
                int[] rgb = new int[n * n];
                for (int i = 0; i < n * n; i++) {
                    h[i] = buf.readShort(); rgb[i] = buf.readInt();
                    int col = rgb[i];
                    float cr = ((col >> 16) & 255) / 255.0F, cg = ((col >> 8) & 255) / 255.0F, cb = (col & 255) / 255.0F;
                    if ((col >> 24 & 1) != 0) { cr = cr * 0.6F + 0.05F; cg = cg * 0.6F + 0.15F; cb = cb * 0.6F + 0.4F; snap.wet++; snap.wh += h[i]; }
                    snap.sr += cr; snap.sg += cg; snap.sb += cb; snap.sh += h[i]; snap.cells++; snap.minH = Math.min(snap.minH, h[i]);
                }
                snap.far.put(ChunkPos.asLong(p.a(), p.b()), new Far(p.a(), p.b(), h, rgb));

                for (int[] o : new int[][] { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                    snap.regions.computeIfAbsent(ChunkPos.asLong(Math.floorDiv(p.a() + o[0], FAR_REGION), Math.floorDiv(p.b() + o[1], FAR_REGION)), k -> new Region()).dirty = true;
                } finally { snap = was; }
            }
            default -> {
                Snap was = snap;
                snap = SNAPS.get(p.id());
                try {
                if (snap == null) return;
                ChunkData d = decode(level, p);
                long k = ChunkPos.asLong(p.a(), p.b());
                ChunkData old = snap.chunks.put(k, d);
                boolean again = old != null;
                markChunk(p.a(), p.b(), old, d, again);
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    ChunkData n = snap.chunks.get(ChunkPos.asLong(p.a() + a, p.b() + b));
                    if (n != null) markChunk(p.a() + a, p.b() + b, null, n, again);
                }
                } finally { snap = was; }
            }
        }
    }

    private static ChunkData decode(ClientLevel level, SnapshotPayload p) {
        ChunkData d = new ChunkData();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(p.data()));
        int minY = level.getMinBuildHeight();
        for (int c = 0; c < 256; c++) {
            int top = buf.readVarInt() + minY, n = buf.readVarInt();
            BlockState[] a = new BlockState[n];
            int i = 0;
            while (i < n) {
                BlockState s = Block.stateById(buf.readVarInt());
                int run = buf.readVarInt();
                for (int k = 0; k < run && i < n; k++) a[i++] = s;
            }
            d.top[c] = top;
            d.cols[c] = a;
            d.minY = Math.min(d.minY, top - n);
            d.maxY = Math.max(d.maxY, top);
        }
        d.biome = level.registryAccess().registryOrThrow(Registries.BIOME).byId(p.biome());
        return d;
    }

    private static void markChunk(int cx, int cz, ChunkData old, ChunkData d, boolean urgent) {
        int lo = d.minY, hi = d.maxY;
        if (old != null) { lo = Math.min(lo, old.minY); hi = Math.max(hi, old.maxY); }
        for (int sy = Math.floorDiv(lo - 1, 16); sy <= Math.floorDiv(hi, 16); sy++) {
            long k = SectionPos.asLong(cx, sy, cz);
            (urgent ? snap.urgent : snap.dirty).add(k);
        }
    }

    private static void free(Snap x) {
        if (x == null) return;
        for (Map<RenderType, VertexBuffer> m : x.sections.values()) for (VertexBuffer vb : m.values()) vb.close();
        for (Region g : x.regions.values()) if (g.vb != null) g.vb.close();
    }

    private static void free() {
        for (Snap x : SNAPS.values()) free(x);
        if (snap != null && !SNAPS.containsValue(snap)) free(snap);
        SNAPS.clear();
        snap = null;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { free(); DEBRIS.clear(); show = false; }

    static BlockState at(int x, int y, int z) {
        if (snap == null) return Blocks.AIR.defaultBlockState();
        ChunkData d = snap.chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (d == null) return Blocks.AIR.defaultBlockState();
        int c = ((z & 15) << 4) | (x & 15), top = d.top[c];
        if (y >= top) return Blocks.AIR.defaultBlockState();
        BlockState[] a = d.cols[c];
        int i = top - 1 - y;
        return i < a.length ? a[i] : Blocks.STONE.defaultBlockState();
    }

    private static boolean sent(int x, int y, int z) {
        ChunkData d = snap.chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (d == null) return false;
        int c = ((z & 15) << 4) | (x & 15);
        return y < d.top[c] && d.top[c] - 1 - y < d.cols[c].length;
    }

    private static final BlockAndTintGetter GETTER = new BlockAndTintGetter() {
        @Override public float getShade(Direction d, boolean shade) { ClientLevel l = Minecraft.getInstance().level; return l == null ? 1.0F : l.getShade(d, shade); }
        @Override public LevelLightEngine getLightEngine() { return Minecraft.getInstance().level.getLightEngine(); }
        @Override public int getBrightness(LightLayer layer, BlockPos pos) { return layer == LightLayer.SKY ? 15 : 0; }
        @Override public int getRawBrightness(BlockPos pos, int amount) { return Math.max(0, 15 - amount); }
        @Override public boolean canSeeSky(BlockPos pos) { return true; }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            ChunkData d = snap == null ? null : snap.chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            Biome b = d != null && d.biome != null ? d.biome : null;
            if (b == null) { ClientLevel l = Minecraft.getInstance().level; b = l.getBiome(BlockPos.ZERO).value(); }
            return resolver.getColor(b, pos.getX(), pos.getZ());
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return at(pos.getX(), pos.getY(), pos.getZ()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return Minecraft.getInstance().level.getHeight(); }
        @Override public int getMinBuildHeight() { return Minecraft.getInstance().level.getMinBuildHeight(); }
    };

    private static void build(long budgetNanos) {
        if (snap == null || snap.dirty.isEmpty() && snap.urgent.isEmpty()) return;
        long end = System.nanoTime() + budgetNanos;
        while (System.nanoTime() < end) {
            Iterator<Long> it = !snap.urgent.isEmpty() ? snap.urgent.iterator() : snap.dirty.iterator();
            if (!it.hasNext()) return;
            long k = it.next();
            it.remove();
            snap.dirty.remove(k);
            buildSection(k);
        }
    }

    private static void buildSection(long key) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher br = mc.getBlockRenderer();
        int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
        int x0 = sx << 4, y0 = sy << 4, z0 = sz << 4;
        Map<RenderType, ByteBufferBuilder> bytes = new HashMap<>();
        Map<RenderType, BufferBuilder> builders = new HashMap<>();
        PoseStack ps = new PoseStack();
        RandomSource rnd = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y < y0 + 16; y++) for (int z = z0; z < z0 + 16; z++) for (int x = x0; x < x0 + 16; x++) {
            BlockState st = at(x, y, z);
            if (st.isAir() || !sent(x, y, z)) continue;
            pos.set(x, y, z);
            FluidState fs = st.getFluidState();
            if (!fs.isEmpty()) {
                RenderType rt = ItemBlockRenderTypes.getRenderLayer(fs);
                br.renderLiquid(pos, GETTER, layer(builders, bytes, rt), st, fs);
            }
            if (st.getRenderShape() != RenderShape.MODEL) continue;
            BakedModel model = br.getBlockModel(st);
            rnd.setSeed(st.getSeed(pos));
            for (RenderType rt : model.getRenderTypes(st, rnd, ModelData.EMPTY)) {
                ps.pushPose();
                ps.translate(x & 15, y & 15, z & 15);
                br.renderBatched(st, pos, GETTER, ps, layer(builders, bytes, rt), true, rnd, ModelData.EMPTY, rt);
                ps.popPose();
            }
        }
        Map<RenderType, VertexBuffer> old = snap.sections.remove(key);
        if (old != null) for (VertexBuffer vb : old.values()) vb.close();
        Map<RenderType, VertexBuffer> out = new HashMap<>();
        for (Map.Entry<RenderType, BufferBuilder> e : builders.entrySet()) {
            MeshData md = e.getValue().build();
            if (md != null) {
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(md);
                VertexBuffer.unbind();
                out.put(e.getKey(), vb);
            }
        }
        for (ByteBufferBuilder b : bytes.values()) b.close();
        if (!out.isEmpty()) snap.sections.put(key, out);
    }

    private static BufferBuilder layer(Map<RenderType, BufferBuilder> builders, Map<RenderType, ByteBufferBuilder> bytes, RenderType rt) {
        return builders.computeIfAbsent(rt, r -> {
            ByteBufferBuilder b = new ByteBufferBuilder(r.bufferSize());
            bytes.put(r, b);
            return new BufferBuilder(b, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        });
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage st = event.getStage();
        if (st == RenderLevelStageEvent.Stage.AFTER_SKY) {
            build(show ? 8_000_000L : 1_500_000L);
            if (show) DhBridge.clearStale();
            return;
        }
        if (st == RenderLevelStageEvent.Stage.AFTER_ENTITIES) { drawDebris(event); return; }
        if (snap == null || !show) return;
        if (st == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) drawFar(event);
        RenderType rt = st == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS ? RenderType.solid()
                : st == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS ? RenderType.cutoutMipped()
                : st == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS ? RenderType.cutout()
                : st == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS ? RenderType.translucent() : null;
        if (rt == null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix()), proj = new Matrix4f(event.getProjectionMatrix());
        rt.setupRenderState();
        ShaderInstance sh = RenderSystem.getShader();
        if (sh == null) { rt.clearRenderState(); return; }
        sh.setDefaultUniforms(VertexFormat.Mode.QUADS, mv, proj, mc.getWindow());
        sh.apply();
        Uniform off = sh.CHUNK_OFFSET;
        double far = Balance.RG_SNAP_R * 1.6;
        for (Map.Entry<Long, Map<RenderType, VertexBuffer>> e : snap.sections.entrySet()) {
            VertexBuffer vb = e.getValue().get(rt);
            if (vb == null) continue;
            long k = e.getKey();
            int sx = SectionPos.x(k), sy = SectionPos.y(k), sz = SectionPos.z(k);
            if (level.getChunkSource().hasChunk(sx, sz)) continue;
            double ox = (sx << 4) - cam.x, oy = (sy << 4) - cam.y, oz = (sz << 4) - cam.z;
            if (ox * ox + oz * oz > far * far) continue;
            if (off != null) { off.set((float) ox, (float) oy, (float) oz); off.upload(); }
            vb.bind();
            vb.draw();
        }
        if (off != null) off.set(0.0F, 0.0F, 0.0F);
        sh.clear();
        VertexBuffer.unbind();
        rt.clearRenderState();
    }

    private static void drawFar(RenderLevelStageEvent event) {
        if (snap.far.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        ShaderInstance sh = net.minecraft.client.renderer.GameRenderer.getRendertypeLeashShader();
        if (sh == null) return;
        if (white == null) {
            white = new net.minecraft.client.renderer.texture.DynamicTexture(16, 16, false);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) white.getPixels().setPixelRGBA(x, y, 0xFFFFFFFF);
            white.upload();
        }

        RenderSystem.setShaderTexture(2, white.getId());
        float sky = level.getSkyDarken(1.0F);
        RenderSystem.setShaderColor(sky, sky, sky, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix()).translate((float) (snap.tx - cam.x), (float) -cam.y, (float) (snap.tz - cam.z));
        drawPlane(sh, mv, event.getProjectionMatrix());
        long now = System.currentTimeMillis();
        int rebuilt = 0;
        for (Map.Entry<Long, Region> e : snap.regions.entrySet()) {
            Region g = e.getValue();
            if (g.dirty && rebuilt < FAR_REBUILDS_PER_FRAME && (g.vb == null || now - g.builtAt > FAR_REBUILD_MS)) {
                buildRegion(level, g, ChunkPos.getX(e.getKey()), ChunkPos.getZ(e.getKey()));
                g.builtAt = now;
                rebuilt++;
            }
            if (g.vb == null) continue;
            g.vb.bind();
            g.vb.drawWithShader(mv, event.getProjectionMatrix(), sh);
        }
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
    }

    private static void drawPlane(ShaderInstance sh, Matrix4f mv, Matrix4f proj) {
        if (snap.cells < 64) return;
        float r = (float) (snap.sr / snap.cells) * 0.9F, g = (float) (snap.sg / snap.cells) * 0.9F, b = (float) (snap.sb / snap.cells) * 0.9F;

        double y = snap.minH - 5.0;
        double half = snap.farR * 4.0;
        com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
        float x0 = (float) -half, x1 = (float) half, z0 = (float) -half, z1 = (float) half, yy = (float) y;

        int rc = (snap.r + 15) >> 4;
        float ix0 = (float) ((((snap.tx >> 4) - rc) << 4) - snap.tx), ix1 = (float) ((((snap.tx >> 4) + rc + 1) << 4) - snap.tx);
        float iz0 = (float) ((((snap.tz >> 4) - rc) << 4) - snap.tz), iz1 = (float) ((((snap.tz >> 4) + rc + 1) << 4) - snap.tz);
        planeQuad(bb, x0, z0, x1, iz0, yy, r, g, b);
        planeQuad(bb, x0, iz1, x1, z1, yy, r, g, b);
        planeQuad(bb, x0, iz0, ix0, iz1, yy, r, g, b);
        planeQuad(bb, ix1, iz0, x1, iz1, yy, r, g, b);
        MeshData md = bb.build();
        if (md == null) return;
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        vb.bind();
        vb.upload(md);
        vb.drawWithShader(mv, proj, sh);
        VertexBuffer.unbind();
        vb.close();
    }

    private static void planeQuad(com.mojang.blaze3d.vertex.BufferBuilder bb, float ax, float az, float bx, float bz, float y, float r, float g, float b) {
        fv(bb, ax, y, az, r, g, b); fv(bb, ax, y, bz, r, g, b); fv(bb, bx, y, bz, r, g, b); fv(bb, bx, y, az, r, g, b);
    }

    private static void buildRegion(ClientLevel level, Region g, int rx, int rz) {
        g.dirty = false;
        com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
        for (int a = 0; a < FAR_REGION; a++) for (int b = 0; b < FAR_REGION; b++) {
            Far f = snap.far.get(ChunkPos.asLong(rx * FAR_REGION + a, rz * FAR_REGION + b));
            if (f != null && !level.getChunkSource().hasChunk(f.cx, f.cz)) buildFar(bb, f, f.cx * 16 - snap.tx, f.cz * 16 - snap.tz);
        }
        MeshData md = bb.build();
        if (g.vb != null) { g.vb.close(); g.vb = null; }
        if (md == null) return;
        g.vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        g.vb.bind();
        g.vb.upload(md);
        VertexBuffer.unbind();
    }

    private static void buildFar(com.mojang.blaze3d.vertex.BufferBuilder bb, Far f, int ox, int oz) {
        int n = 16 / Balance.RG_SNAP_FAR_CELL, w = Balance.RG_SNAP_FAR_CELL;
        for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) {
            int idx = k * n + i, col = f.rgb[idx];
            float r = ((col >> 16) & 255) / 255.0F, g = ((col >> 8) & 255) / 255.0F, b = (col & 255) / 255.0F;
            if ((col >> 24 & 1) != 0) { r = r * 0.6F + 0.05F; g = g * 0.6F + 0.15F; b = b * 0.6F + 0.4F; }
            float top = f.h[idx], x0 = ox + i * w, z0 = oz + k * w;
            float nn = farH(f, i, k - 1), ss = farH(f, i, k + 1), ee = farH(f, i + 1, k), ww = farH(f, i - 1, k);
            float sh = top > nn ? 1.0F : top < nn ? 0.84F : 0.93F;
            fv(bb, x0, top, z0, r * sh, g * sh, b * sh); fv(bb, x0, top, z0 + w, r * sh, g * sh, b * sh);
            fv(bb, x0 + w, top, z0 + w, r * sh, g * sh, b * sh); fv(bb, x0 + w, top, z0, r * sh, g * sh, b * sh);

            farSide(bb, top, Float.isNaN(ee) ? top - FAR_SKIRT : ee, x0 + w, z0 + w, x0 + w, z0, r, g, b, 0.74F);
            farSide(bb, top, Float.isNaN(ww) ? top - FAR_SKIRT : ww, x0, z0, x0, z0 + w, r, g, b, 0.70F);
            farSide(bb, top, Float.isNaN(ss) ? top - FAR_SKIRT : ss, x0, z0 + w, x0 + w, z0 + w, r, g, b, 0.64F);
            farSide(bb, top, Float.isNaN(nn) ? top - FAR_SKIRT : nn, x0 + w, z0, x0, z0, r, g, b, 0.80F);
        }
    }

    private static float farH(Far f, int i, int k) {
        int n = 16 / Balance.RG_SNAP_FAR_CELL;
        if (i >= 0 && k >= 0 && i < n && k < n) return f.h[k * n + i];
        int cx = f.cx + Math.floorDiv(i, n), cz = f.cz + Math.floorDiv(k, n);
        Far o = snap.far.get(ChunkPos.asLong(cx, cz));
        int ii = Math.floorMod(i, n), kk = Math.floorMod(k, n);
        if (o != null) return o.h[kk * n + ii];
        int top = topAt((cx << 4) + ii * Balance.RG_SNAP_FAR_CELL, (cz << 4) + kk * Balance.RG_SNAP_FAR_CELL);
        return top == Integer.MIN_VALUE ? Float.NaN : top;
    }

    private static void farSide(com.mojang.blaze3d.vertex.BufferBuilder bb, float top, float low, float ax, float az, float bx, float bz, float r, float g, float b, float sh) {
        if (low >= top) return;
        fv(bb, ax, top, az, r * sh, g * sh, b * sh); fv(bb, bx, top, bz, r * sh, g * sh, b * sh);
        fv(bb, bx, low, bz, r * sh * 0.8F, g * sh * 0.8F, b * sh * 0.8F); fv(bb, ax, low, az, r * sh * 0.8F, g * sh * 0.8F, b * sh * 0.8F);
    }

    private static void fv(com.mojang.blaze3d.vertex.BufferBuilder bb, float x, float y, float z, float r, float g, float b) {
        bb.addVertex(x, y, z).setColor(r, g, b, 1.0F).setLight(LightTexture.pack(0, 15));
    }

    private record Piece(BlockState state, double x, double y, double z, double vx, double vy, double vz, float ax, float ay, float az, float spin, float size, long born, int life) {}

    private static final List<Piece> DEBRIS = new ArrayList<>();

    static void spawnDebris(double x, double y, double z, double r, long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource rnd = level.random;
        int n = Balance.RG_CUT_DEBRIS;
        for (int i = 0, tries = 0; i < n && tries < n * 4; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = r * (0.08 + 0.85 * Math.sqrt(rnd.nextDouble()));
            int bx = (int) Math.floor(x + Math.cos(a) * d), bz = (int) Math.floor(z + Math.sin(a) * d);
            int top = topAt(bx, bz);
            if (top == Integer.MIN_VALUE) continue;
            BlockState s = at(bx, top - 1, bz);
            if (s.isAir() || !s.getFluidState().isEmpty()) s = at(bx, top - 2, bz);
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            double near = 1.0 - d / r;
            double h = (6.0 + rnd.nextDouble() * 22.0) * (0.5 + near), v = 18.0 + rnd.nextDouble() * 42.0 * (0.4 + near);
            float size = rnd.nextFloat() < 0.6F ? 1.0F : rnd.nextFloat() < 0.75F ? 2.0F : 3.0F;
            DEBRIS.add(new Piece(s, bx + 0.5, top + 0.5, bz + 0.5, Math.cos(a) * h, v, Math.sin(a) * h,
                    rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.5F, 2.0F + rnd.nextFloat() * 10.0F, size, now + rnd.nextInt(6),
                    120 + rnd.nextInt(80)));
            i++;
        }
        for (int i = 0; i < 60; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = r * rnd.nextDouble();
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, x + Math.cos(a) * d, y + 2.0, z + Math.sin(a) * d, 0.0, 0.08 + rnd.nextDouble() * 0.1, 0.0);
        }
    }

    static int topAt(int x, int z) {
        if (snap == null) return Integer.MIN_VALUE;
        ChunkData d = snap.chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
        return d == null ? Integer.MIN_VALUE : d.top[((z & 15) << 4) | (x & 15)];
    }

    private static void drawDebris(RenderLevelStageEvent event) {
        if (DEBRIS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { DEBRIS.clear(); return; }
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher br = mc.getBlockRenderer();
        int light = LightTexture.pack(0, 15);
        boolean drew = false;
        for (Iterator<Piece> it = DEBRIS.iterator(); it.hasNext(); ) {
            Piece p = it.next();
            double age = now - p.born();
            if (age > p.life()) { it.remove(); continue; }
            if (age < 0) continue;
            double t = age / 20.0;
            double px = p.x() + p.vx() * t, py = p.y() + p.vy() * t - 0.5 * Balance.RG_CUT_GRAVITY * t * t, pz = p.z() + p.vz() * t;
            int ground = topAt((int) Math.floor(px), (int) Math.floor(pz));
            if (ground != Integer.MIN_VALUE && py < ground && p.vy() - Balance.RG_CUT_GRAVITY * t < 0) py = ground;
            float fade = (float) Math.min(1.0, (p.life() - age) / 20.0);
            float s = p.size() * fade;
            if (s < 0.05F) continue;
            ps.pushPose();
            ps.translate(px - cam.x, py - cam.y, pz - cam.z);
            float ang = (float) (p.spin() * Math.min(t, (p.vy() / Balance.RG_CUT_GRAVITY) * 2.0));
            ps.mulPose(Axis.of(new org.joml.Vector3f(p.ax(), p.ay(), p.az()).normalize()).rotation(ang));
            ps.scale(s, s, s);
            ps.translate(-0.5, -0.5, -0.5);
            br.renderSingleBlock(p.state(), ps, buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }
}
