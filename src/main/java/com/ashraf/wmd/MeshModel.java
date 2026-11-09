package com.ashraf.wmd;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class MeshModel {

    private record Part(int kind, int quads, float[] v, float[] bb, boolean[] flat, float[] area, int atlas) {
        boolean glow() { return kind == 1; }
    }

    private static Object[] sortByArea(float[] v, int quads) {
        float[] a = new float[quads];
        Integer[] order = new Integer[quads];
        for (int q = 0; q < quads; q++) {
            int i = q * 32;
            float ax = v[i + 8] - v[i], ay = v[i + 9] - v[i + 1], az = v[i + 10] - v[i + 2];
            float bx = v[i + 16] - v[i], by = v[i + 17] - v[i + 1], bz = v[i + 18] - v[i + 2];
            float cx = v[i + 24] - v[i], cy = v[i + 25] - v[i + 1], cz = v[i + 26] - v[i + 2];
            float t1 = (float) (0.5 * Math.sqrt((ay * bz - az * by) * (ay * bz - az * by) + (az * bx - ax * bz) * (az * bx - ax * bz) + (ax * by - ay * bx) * (ax * by - ay * bx)));
            float t2 = (float) (0.5 * Math.sqrt((by * cz - bz * cy) * (by * cz - bz * cy) + (bz * cx - bx * cz) * (bz * cx - bx * cz) + (bx * cy - by * cx) * (bx * cy - by * cx)));
            a[q] = t1 + t2;
            order[q] = q;
        }
        java.util.Arrays.sort(order, (x, y) -> Float.compare(a[y], a[x]));
        float[] nv = new float[v.length], na = new float[quads];
        for (int q = 0; q < quads; q++) {
            System.arraycopy(v, order[q] * 32, nv, q * 32, 32);
            na[q] = a[order[q]];
        }
        return new Object[] { nv, na };
    }

    private static boolean[] flatQuads(float[] v, int quads) {
        boolean[] f = new boolean[quads];
        for (int q = 0; q < quads; q++) {
            int i = q * 32;
            f[q] = true;
            for (int k = 1; k < 4 && f[q]; k++) {
                int j = i + k * 8;
                f[q] = v[j + 5] == v[i + 5] && v[j + 6] == v[i + 6] && v[j + 7] == v[i + 7];
            }
        }
        return f;
    }

    public static volatile boolean fast = true;

    public static long statQuads, statNanos;
    private static final Vector3f TMP = new Vector3f();

    public static volatile boolean gpu = true;
    public static long statGpu;

    private static final int MIN_GPU_QUADS = 700;

    private static final int MAX_VARIANTS = 6;

    private static final class Cached { VertexBuffer[] vb; int[] atlas; int quads; float[] bb;
        void close() { if (vb != null) for (VertexBuffer b : vb) if (b != null) b.close(); } }
    private final Map<String, Map<Long, Cached>> cache = new HashMap<>();
    private final Map<String, Integer> boneQuads = new HashMap<>();

    private void closeCache() {
        for (Map<Long, Cached> m : cache.values()) for (Cached c : m.values()) c.close();
        cache.clear();
        boneQuads.clear();
    }

    public boolean drawCached(String bone, PoseStack.Pose pose, int light, int overlay) {
        if (!gpu || !inLevel) return false;
        Map<String, List<Part>> p = load();
        if (p == null) return false;
        List<Part> list = p.get(bone);
        if (list == null) return false;
        Integer bq = boneQuads.get(bone);
        if (bq == null) { int t = 0; for (Part part : list) t += part.quads(); boneQuads.put(bone, bq = t); }
        if (bq < MIN_GPU_QUADS) return false;
        long t0 = System.nanoTime();
        boolean dark = darkFor(pose);
        try {
            Map<Long, Cached> variants = cache.computeIfAbsent(bone, k -> new java.util.LinkedHashMap<>());
            long key = ((long) light << 32) ^ (overlay & 0xFFFFFFFFL) ^ (dark ? Long.MIN_VALUE : 0L);
            Cached c = variants.get(key);
            if (c == null) {
                if (variants.size() >= MAX_VARIANTS) {
                    java.util.Iterator<Cached> it = variants.values().iterator();
                    it.next().close();
                    it.remove();
                }
                c = new Cached();
                c.quads = bq;
                float[] box = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
                boolean allBox = true;
                int[] used = list.stream().mapToInt(Part::atlas).distinct().sorted().toArray();
                c.atlas = used;
                c.vb = new VertexBuffer[used.length];
                for (int ai = 0; ai < used.length; ai++) {
                    int nq = 0;
                    for (Part part : list) if (part.atlas() == used[ai]) nq += part.quads();
                    try (ByteBufferBuilder bytes = new ByteBufferBuilder(nq * 4 * DefaultVertexFormat.NEW_ENTITY.getVertexSize() + 256)) {
                        BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                        for (Part part : list) {
                            if (part.atlas() != used[ai]) continue;
                            if (part.bb() == null) allBox = false;
                            else for (int k = 0; k < 3; k++) { box[k] = Math.min(box[k], part.bb()[k]); box[k + 3] = Math.max(box[k + 3], part.bb()[k + 3]); }
                            int l = part.glow() && !dark ? LightTexture.FULL_BRIGHT : light;
                            float[] v = part.v();
                            for (int i = 0; i < part.quads() * 32; i += 8)
                                bb.addVertex(v[i], v[i + 1], v[i + 2]).setColor(255, 255, 255, 255).setUv(v[i + 3], v[i + 4]).setOverlay(overlay).setLight(l).setNormal(v[i + 5], v[i + 6], v[i + 7]);
                        }
                        MeshData md = bb.buildOrThrow();
                        c.vb[ai] = new VertexBuffer(VertexBuffer.Usage.STATIC);
                        c.vb[ai].bind();
                        c.vb[ai].upload(md);
                        VertexBuffer.unbind();
                    }
                }
                c.bb = allBox ? box : null;
                variants.put(key, c);
            }
            if (cull && c.bb != null && !inView(pose, c.bb)) { statCulled += c.quads; return true; }
            Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
            for (int ai = 0; ai < c.vb.length; ai++) {
                RenderType rt = RenderType.entityCutoutNoCull(atlas(c.atlas[ai]));
                rt.setupRenderState();
                c.vb[ai].bind();
                c.vb[ai].drawWithShader(mv, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                VertexBuffer.unbind();
                rt.clearRenderState();
            }
            statGpu += c.quads;
            long dt = System.nanoTime() - t0;
            statNanos += dt;
            myNanos += dt;
            return true;
        } catch (Throwable t) {
            gpu = false;
            Wmd.LOGGER.error("GPU-cached mesh drawing failed for {} / {}, switched off", file, bone, t);
            return false;
        }
    }

    private void emit(VertexConsumer vc, PoseStack.Pose pose, Part part, int count, int cr, int cg, int cb, int ca, int overlay, int light) {
        long t0 = System.nanoTime();
        float[] v = part.v();
        if (!fast) {
            for (int i = 0; i < count * 32; i += 8) {
                vc.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(cr, cg, cb, ca).setUv(v[i + 3], v[i + 4]).setOverlay(overlay).setLight(light).setNormal(pose, v[i + 5], v[i + 6], v[i + 7]);
            }
        } else {
            Matrix4f m = pose.pose();
            Vector3f t = TMP;
            boolean[] flat = part.flat();
            int i = 0;
            for (int q = 0; q < count; q++) {
                float nx = 0, ny = 0, nz = 0;
                boolean f = flat[q];
                if (f) { pose.transformNormal(v[i + 5], v[i + 6], v[i + 7], t); nx = t.x(); ny = t.y(); nz = t.z(); }
                for (int k = 0; k < 4; k++, i += 8) {
                    m.transformPosition(v[i], v[i + 1], v[i + 2], t);
                    vc.addVertex(t.x(), t.y(), t.z()).setColor(cr, cg, cb, ca).setUv(v[i + 3], v[i + 4]).setOverlay(overlay).setLight(light);
                    if (f) vc.setNormal(nx, ny, nz);
                    else { pose.transformNormal(v[i + 5], v[i + 6], v[i + 7], t); vc.setNormal(t.x(), t.y(), t.z()); }
                }
            }
        }
        long dt = System.nanoTime() - t0;
        statQuads += count;
        statLod += part.quads() - count;
        statNanos += dt;
        myQuads += count;
        myNanos += dt;
    }

    private static final List<MeshModel> ALL = new ArrayList<>();
    public static final MeshModel LAUNCHER = new MeshModel("interceptor_launcher");
    private static final Map<Integer, MeshModel> PADS = new HashMap<>();

    public static MeshModel pad(int tier) { return PADS.computeIfAbsent(tier, t -> new MeshModel("pad_t" + t)); }

    private static final Map<String, MeshModel> NAMED = new HashMap<>();

    public static MeshModel named(String name) { synchronized (NAMED) { return NAMED.computeIfAbsent(name, MeshModel::new); } }

    private final String name;

    private final boolean noLod;
    private long myQuads, myNanos;
    private final ResourceLocation file, atlas;

    private ResourceLocation[] atlases;
    private Map<String, List<Part>> parts;
    private boolean tried;

    private ResourceLocation atlas(int k) {
        ResourceLocation[] a = atlases;
        if (k == 0 || a == null) return atlas;
        return k < a.length ? a[k] : atlas;
    }

    private MeshModel(String name) {
        this.noLod = "asteroid_rock".equals(name);
        this.name = name;
        this.file = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mesh/" + name + ".mesh");
        this.atlas = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/mesh/" + name + "_mesh.png");
        synchronized (ALL) { ALL.add(this); }
    }

    public static String topStats(long frames) {
        List<MeshModel> list;
        synchronized (ALL) { list = new ArrayList<>(ALL); }
        list.sort((a, b) -> Long.compare(b.myQuads, a.myQuads));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, list.size()); i++) {
            MeshModel m = list.get(i);
            if (m.myQuads == 0) break;
            sb.append(String.format("%s %dk %.2fms  ", m.name, m.myQuads / Math.max(1, frames) / 1000, m.myNanos / 1e6 / Math.max(1, frames)));
        }
        for (MeshModel m : list) { m.myQuads = 0; m.myNanos = 0; }
        return sb.toString();
    }

    @SubscribeEvent
    static void onReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
            synchronized (ALL) { for (MeshModel m : ALL) { m.parts = null; m.tried = false; m.closeCache(); } }
        });
    }

    private Map<String, List<Part>> load() {
        if (tried) return parts;
        tried = true;
        try {
            Resource res = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
            if (res == null) return parts = null;
            Map<String, List<Part>> out = new HashMap<>();
            try (InputStream in = res.open(); DataInputStream d = new DataInputStream(new GZIPInputStream(in))) {
                byte[] magic = new byte[4];
                d.readFully(magic);
                boolean wmd = magic[0] == 'W' && magic[1] == 'M' && magic[2] == 'D';
                boolean split = wmd && magic[3] == '3';
                boolean boxes = wmd && (magic[3] == '2' || split);
                if (!boxes && !(wmd && magic[3] == 'M')) throw new IllegalStateException("bad mesh file");
                int n = d.readInt();
                int maxAtlas = 0;
                for (int i = 0; i < n; i++) {
                    byte[] nb = new byte[d.readUnsignedShort()];
                    d.readFully(nb);
                    String pname = new String(nb, java.nio.charset.StandardCharsets.UTF_8);
                    int kind = d.readByte();
                    int atlasIdx = split ? d.readUnsignedByte() : 0;
                    maxAtlas = Math.max(maxAtlas, atlasIdx);
                    int quads = d.readInt();
                    float[] bb = null;
                    if (boxes) { bb = new float[6]; for (int k = 0; k < 6; k++) bb[k] = d.readFloat(); }
                    byte[] raw = new byte[quads * 32 * 4];
                    d.readFully(raw);
                    float[] v = new float[quads * 32];
                    ByteBuffer.wrap(raw).asFloatBuffer().get(v);
                    String bone = kind == 1 ? pname.substring(0, pname.length() - 5) : kind == 2 ? pname.substring(0, pname.length() - 6) : pname;
                    Object[] sorted = sortByArea(v, quads);
                    v = (float[]) sorted[0];
                    out.computeIfAbsent(bone, k -> new ArrayList<>()).add(new Part(kind, quads, v, bb, flatQuads(v, quads), (float[]) sorted[1], atlasIdx));
                }
                ResourceLocation[] at = new ResourceLocation[maxAtlas + 1];
                at[0] = atlas;
                for (int k = 1; k <= maxAtlas; k++) at[k] = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/mesh/" + name + "_mesh" + k + ".png");
                atlases = at;
                if (maxAtlas > 0) for (List<Part> l : out.values()) l.sort((a, b) -> Integer.compare(a.atlas(), b.atlas()));
            }
            Wmd.LOGGER.info("Mesh {} loaded: {} bones", file, out.size());
            return parts = out;
        } catch (Exception e) {
            Wmd.LOGGER.error("Mesh {} could not be loaded, drawing cubes instead", file, e);
            return parts = null;
        }
    }

    public boolean has(String bone) {
        Map<String, List<Part>> p = load();
        return p != null && p.containsKey(bone);
    }

    public static volatile boolean cull = true;

    private static boolean inLevel;
    public static long statCulled, statLod;

    public static volatile float lodMinPx2 = 0.5F;
    private static float pxK;

    private static int lodCount(PoseStack.Pose pose, Part part) {
        if (lodMinPx2 <= 0 || !inLevel || part.bb() == null || pxK <= 0) return part.quads();
        float[] bb = part.bb();
        Vector3f t = new Vector3f();
        pose.pose().transformPosition((bb[0] + bb[3]) * 0.5F, (bb[1] + bb[4]) * 0.5F, (bb[2] + bb[5]) * 0.5F, t);
        float dist2 = t.lengthSquared();
        Matrix4f m = pose.pose();
        float sc = (float) Math.cbrt(Math.abs(m.determinant3x3()));
        float thr = lodMinPx2 * dist2 / (sc * sc * pxK * pxK);
        float[] area = part.area();
        if (area[area.length - 1] >= thr) return part.quads();
        int lo = 0, hi = area.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (area[mid] >= thr) lo = mid + 1; else hi = mid; }
        return lo;
    }
    private static net.minecraft.client.renderer.culling.Frustum frustum;
    private static net.minecraft.world.phys.Vec3 camPos;

    private static boolean darkFor(PoseStack.Pose pose) {
        if (!inLevel || camPos == null || !ClientHemp.any()) return false;
        org.joml.Matrix4f m = pose.pose();
        return ClientHemp.darkAt(camPos.x + m.m30(), camPos.z + m.m32());
    }

    @SubscribeEvent
    static void onStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) {
            inLevel = true;
            frustum = event.getFrustum();
            camPos = event.getCamera().getPosition();
            Minecraft mc = Minecraft.getInstance();
            double fov = Math.toRadians(mc.options.fov().get());
            pxK = (float) (mc.getWindow().getHeight() / (2.0 * Math.tan(fov / 2.0)));
        } else if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            inLevel = false;
            frustum = null;
        }
    }

    private static boolean inView(PoseStack.Pose pose, float[] bb) {
        net.minecraft.client.renderer.culling.Frustum fr = frustum;
        if (fr == null || camPos == null) return true;
        Matrix4f m = pose.pose();
        Vector3f t = new Vector3f();
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            m.transformPosition((i & 1) == 0 ? bb[0] : bb[3], (i & 2) == 0 ? bb[1] : bb[4], (i & 4) == 0 ? bb[2] : bb[5], t);
            x0 = Math.min(x0, t.x); x1 = Math.max(x1, t.x);
            y0 = Math.min(y0, t.y); y1 = Math.max(y1, t.y);
            z0 = Math.min(z0, t.z); z1 = Math.max(z1, t.z);
        }
        double cx = camPos.x, cy = camPos.y, cz = camPos.z, pad = 0.5;
        return fr.isVisible(new net.minecraft.world.phys.AABB(cx + x0 - pad, cy + y0 - pad, cz + z0 - pad, cx + x1 + pad, cy + y1 + pad, cz + z1 + pad));
    }

    private static boolean hidden(PoseStack.Pose pose, Part part) {
        if (!cull || !inLevel || part.bb() == null) return false;
        if (inView(pose, part.bb())) return false;
        statCulled += part.quads();
        return true;
    }

    public void drawTinted(String bone, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay, int tr, int tg, int tb, int alpha, boolean paintOnly) {
        boolean dark = darkFor(pose);
        Map<String, List<Part>> p = load();
        if (p == null) return;
        List<Part> list = p.get(bone);
        if (list == null) return;
        VertexConsumer vc = null;
        int cur = -1;
        for (Part part : list) {
            boolean paint = part.kind() == 2;
            if (paintOnly && !paint) continue;
            if (hidden(pose, part)) continue;
            if (vc == null || part.atlas() != cur) { cur = part.atlas(); vc = buffers.getBuffer(alpha < 255 ? RenderType.entityTranslucent(atlas(cur)) : RenderType.entityCutoutNoCull(atlas(cur))); }
            int l = part.glow() && !dark ? LightTexture.FULL_BRIGHT : light;
            int cr = paint ? tr : 255, cg = paint ? tg : 255, cb = paint ? tb : 255;
            emit(vc, pose, part, (noLod ? part.quads() : lodCount(pose, part)), cr, cg, cb, alpha, overlay, l);
        }
    }

    public void drawTintedZ(String bone, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay, int tr, int tg, int tb, float z0, float z1) {
        boolean dark = darkFor(pose);
        Map<String, List<Part>> p = load();
        if (p == null) return;
        List<Part> list = p.get(bone);
        if (list == null) return;
        VertexConsumer vc = null;
        int cur = -1;
        for (Part part : list) {
            if (vc == null || part.atlas() != cur) { cur = part.atlas(); vc = buffers.getBuffer(RenderType.entityCutoutNoCull(atlas(cur))); }
            boolean paint = part.kind() == 2;
            int l = part.glow() && !dark ? LightTexture.FULL_BRIGHT : light;
            int cr = paint ? tr : 255, cg = paint ? tg : 255, cb = paint ? tb : 255;
            float[] v = part.v();
            for (int q = 0, i = 0; q < part.quads(); q++, i += 32) {
                float zc = (v[i + 2] + v[i + 10] + v[i + 18] + v[i + 26]) * 0.25F;
                if (zc < z0 || zc >= z1) continue;
                for (int k = 0; k < 32; k += 8)
                    vc.addVertex(pose, v[i + k], v[i + k + 1], v[i + k + 2]).setColor(cr, cg, cb, 255).setUv(v[i + k + 3], v[i + k + 4]).setOverlay(overlay).setLight(l)
                            .setNormal(pose, v[i + k + 5], v[i + k + 6], v[i + k + 7]);
            }
        }
    }

    public void drawColoured(String bone, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay, int r, int g, int b) {
        boolean dark = darkFor(pose);
        Map<String, List<Part>> p = load();
        if (p == null) return;
        List<Part> list = p.get(bone);
        if (list == null) return;
        VertexConsumer vc = null;
        int cur = -1;
        for (Part part : list) {
            if (hidden(pose, part)) continue;
            if (vc == null || part.atlas() != cur) { cur = part.atlas(); vc = buffers.getBuffer(RenderType.entityCutoutNoCull(atlas(cur))); }
            int l = part.glow() && !dark ? LightTexture.FULL_BRIGHT : light;
            emit(vc, pose, part, (noLod ? part.quads() : lodCount(pose, part)), r, g, b, 255, overlay, l);
        }
    }

    public void draw(String bone, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) { draw(bone, pose, buffers, light, overlay, null); }

    public void draw(String bone, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay, java.util.function.Predicate<float[]> visible) {
        if (visible == null && drawCached(bone, pose, light, overlay)) return;
        boolean dark = darkFor(pose);
        Map<String, List<Part>> p = load();
        if (p == null) return;
        List<Part> list = p.get(bone);
        if (list == null) return;
        VertexConsumer vc = null;
        int cur = -1;
        for (Part part : list) {
            if (visible != null && part.bb() != null && !visible.test(part.bb())) continue;
            if (hidden(pose, part)) continue;
            if (vc == null || part.atlas() != cur) { cur = part.atlas(); vc = buffers.getBuffer(RenderType.entityCutoutNoCull(atlas(cur))); }
            int l = part.glow() && !dark ? LightTexture.FULL_BRIGHT : light;
            emit(vc, pose, part, (noLod ? part.quads() : lodCount(pose, part)), 255, 255, 255, 255, overlay, l);
        }
    }
}
