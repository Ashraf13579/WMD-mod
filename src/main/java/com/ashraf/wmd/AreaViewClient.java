package com.ashraf.wmd;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AreaViewClient {

    private AreaViewClient() {}

    public static final Model MAIN = new Model(0), SCOPE = new Model(1);

    public static final Model[] CCTV = { new Model(2), new Model(3), new Model(4), new Model(5) };
    private static final Model[] ALL = { MAIN, SCOPE, CCTV[0], CCTV[1], CCTV[2], CCTV[3] };

    public static final class Model {
        final int slot;
        int token = -1, cx, cz, n, baseY, step = 1;
        short[] h = new short[0];
        int[] rgb = new int[0];
        String refused = "";
        List<AreaMarkersPayload.Marker> markers = List.of();
        VertexBuffer vb;
        boolean dirty;
        float mid;
        long receivedMs;

        Model(int slot) { this.slot = slot; }

        public boolean ready() { return n > 0 && refused.isEmpty(); }
        public String refused() { return refused; }
        public int cx() { return cx; }
        public int cz() { return cz; }

        public int size() { return n * step; }

        public void request(int mode, int x, int z, int size, boolean markersOnly) {
            if (!markersOnly) token = ((token < 0 ? 0 : token >> 3) + 1) << 3 | slot;
            PacketDistributor.sendToServer(new AreaViewRequestPayload(token, mode, x, z, size, markersOnly));
        }

        public boolean answered() { return answered == token; }
        int answered = -1;

        void accept(AreaViewPayload p) {
            if (p.token() != token) return;
            answered = p.token();
            refused = p.refused();
            if (!refused.isEmpty()) return;
            cx = p.cx(); cz = p.cz(); n = p.n(); step = Math.max(1, p.step()); baseY = p.baseY(); h = p.h(); rgb = p.rgb();
            long sum = 0;
            for (short v : h) sum += v;
            mid = h.length == 0 ? 0.0F : sum / (float) h.length;
            dirty = true;
            receivedMs = System.currentTimeMillis();
        }

        void accept(AreaMarkersPayload p) { if (p.token() == token) markers = p.markers(); }

        public void setMarkers(List<AreaMarkersPayload.Marker> l) { markers = l; }

        public void close() {
            if (vb != null) { vb.close(); vb = null; }
            n = 0; token = -1; markers = List.of(); refused = "";
        }

        float mx(double x) { return (float) (x - (cx - size() / 2)) - size() / 2.0F; }
        float mz(double z) { return (float) (z - (cz - size() / 2)) - size() / 2.0F; }
        float my(double y) { return (float) (y - baseY) - mid; }

        private void build() {
            dirty = false;
            if (vb != null) { vb.close(); vb = null; }
            if (n == 0) return;
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float off = size() / 2.0F, w = step;
            for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) {
                int idx = k * n + i, c = rgb[idx];
                float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
                boolean water = (c >> 24 & 1) != 0;
                if (water) { r = r * 0.6F + 0.05F; g = g * 0.6F + 0.15F; b = b * 0.6F + 0.4F; }
                float top = h[idx] + 1 - mid, x0 = i * w - off, z0 = k * w - off;
                float north = k > 0 ? h[idx - n] : h[idx];
                float sh = h[idx] > north ? 1.0F : h[idx] < north ? 0.8F : 0.9F;
                bb.addVertex(x0, top, z0).setColor(r * sh, g * sh, b * sh, 1.0F);
                bb.addVertex(x0, top, z0 + w).setColor(r * sh, g * sh, b * sh, 1.0F);
                bb.addVertex(x0 + w, top, z0 + w).setColor(r * sh, g * sh, b * sh, 1.0F);
                bb.addVertex(x0 + w, top, z0).setColor(r * sh, g * sh, b * sh, 1.0F);

                side(bb, i, k, idx, i + 1 < n ? h[idx + 1] : -1, top, x0 + w, z0, x0 + w, z0 + w, r, g, b, 0.72F);
                side(bb, i, k, idx, i > 0 ? h[idx - 1] : -1, top, x0, z0 + w, x0, z0, r, g, b, 0.68F);
                side(bb, i, k, idx, k + 1 < n ? h[idx + n] : -1, top, x0 + w, z0 + w, x0, z0 + w, r, g, b, 0.62F);
                side(bb, i, k, idx, k > 0 ? h[idx - n] : -1, top, x0, z0, x0 + w, z0, r, g, b, 0.78F);
            }
            MeshData md = bb.build();
            if (md == null) return;
            vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(md);
            VertexBuffer.unbind();
        }

        private void side(BufferBuilder bb, int i, int k, int idx, int nb, float top, float ax, float az, float bx, float bz, float r, float g, float b, float sh) {
            float low = nb < 0 ? -mid - 2.0F : nb + 1 - mid;
            if (low >= top) return;
            bb.addVertex(ax, top, az).setColor(r * sh, g * sh, b * sh, 1.0F);
            bb.addVertex(bx, top, bz).setColor(r * sh, g * sh, b * sh, 1.0F);
            bb.addVertex(bx, low, bz).setColor(r * sh * 0.8F, g * sh * 0.8F, b * sh * 0.8F, 1.0F);
            bb.addVertex(ax, low, az).setColor(r * sh * 0.8F, g * sh * 0.8F, b * sh * 0.8F, 1.0F);
        }

        private final Matrix4f last = new Matrix4f();
        private boolean drawn;

        public void draw(GuiGraphics gg, int x, int y, int w, int hh, float yaw, float pitch, float zoom, int desX, int desZ, int hoverX, int hoverZ) {
            if (dirty) build();
            if (vb == null) return;
            gg.enableScissor(x, y, x + w, y + hh);
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            PoseStack ps = gg.pose();
            ps.pushPose();
            float s = Math.min(w, hh) / (size() * 1.15F) * zoom;
            ps.translate(x + w / 2.0F, y + hh / 2.0F, 600.0F);
            ps.scale(s, -s, s);
            ps.mulPose(Axis.XP.rotationDegrees(pitch));
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            last.set(ps.last().pose());
            drawn = true;
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.disableCull();
            vb.bind();

            vb.drawWithShader(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ps.last().pose()), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f m = ps.last().pose();
            for (AreaMarkersPayload.Marker mk : markers) {
                float mx = mx(mk.x()), my = my(mk.y()), mz = mz(mk.z());
                switch (mk.kind()) {
                    case AreaMarkersPayload.PLAYER -> cube(bb, m, mx, my + 1.0F, mz, 0.7F, 1.0F, 1.0F, 1.0F, 1.0F);
                    case AreaMarkersPayload.AIRSHIP -> cube(bb, m, mx, my, mz, Math.max(1.5F, mk.r()), 0.35F, 0.6F, 1.0F, 0.55F);
                    case AreaMarkersPayload.MISSILE -> cube(bb, m, mx, my, mz, 1.0F, 1.0F, 0.6F, 0.15F, 1.0F);
                    case AreaMarkersPayload.LASER -> cube(bb, m, mx, my + 0.5F, mz, 0.6F, 1.0F, 0.15F, 0.1F, 1.0F);
                    case AreaMarkersPayload.SMOKE -> disc(bb, m, mx, my + 3.0F, mz, mk.r(), 0.75F, 0.75F, 0.78F, 0.35F);
                    case AreaMarkersPayload.MOB -> cube(bb, m, mx, my + 0.9F, mz, 0.6F, 1.0F, 0.2F, 0.15F, 1.0F);
                    case AreaMarkersPayload.CREATURE -> cube(bb, m, mx, my + 0.6F, mz, 0.45F, 0.35F, 0.9F, 0.4F, 1.0F);
                    default -> {}
                }
            }
            if (desX != Integer.MIN_VALUE) column(bb, m, desX, desZ, 1.0F, 0.15F, 0.1F, 0.85F);
            if (hoverX != Integer.MIN_VALUE) column(bb, m, hoverX, hoverZ, 1.0F, 1.0F, 1.0F, 0.5F);
            MeshData md = bb.build();
            if (md != null) BufferUploader.drawWithShader(md);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            ps.popPose();
            gg.disableScissor();
        }

        private void column(BufferBuilder bb, Matrix4f m, int wx, int wz, float r, float g, float b, float a) {
            int i = Math.floorDiv(wx - (cx - size() / 2), step), k = Math.floorDiv(wz - (cz - size() / 2), step);
            if (i < 0 || k < 0 || i >= n || k >= n) return;
            float x = mx(wx + 0.5), z = mz(wz + 0.5), y0 = h[k * n + i] + 1 - mid;
            for (int q = 0; q < 2; q++) {
                float ox = q == 0 ? 0.3F : 0.0F, oz = q == 0 ? 0.0F : 0.3F;
                bb.addVertex(m, x - ox, y0, z - oz).setColor(r, g, b, a);
                bb.addVertex(m, x + ox, y0, z + oz).setColor(r, g, b, a);
                bb.addVertex(m, x + ox, y0 + 12.0F, z + oz).setColor(r, g, b, 0.0F);
                bb.addVertex(m, x - ox, y0 + 12.0F, z - oz).setColor(r, g, b, 0.0F);
            }
        }

        private static void cube(BufferBuilder bb, Matrix4f m, float x, float y, float z, float s, float r, float g, float b, float a) {
            float[][] f = { { 0, 1, 0 }, { 0, -1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
            for (float[] d : f) {
                float sh = d[1] > 0 ? 1.0F : d[1] < 0 ? 0.5F : 0.75F;
                float[][] v = faceVerts(d, s);
                for (float[] p : v) bb.addVertex(m, x + p[0], y + p[1], z + p[2]).setColor(r * sh, g * sh, b * sh, a);
            }
        }

        private static float[][] faceVerts(float[] d, float s) {
            if (d[1] != 0) { float y = d[1] * s; return new float[][] { { -s, y, -s }, { -s, y, s }, { s, y, s }, { s, y, -s } }; }
            if (d[0] != 0) { float x = d[0] * s; return new float[][] { { x, -s, -s }, { x, s, -s }, { x, s, s }, { x, -s, s } }; }
            float z = d[2] * s;
            return new float[][] { { -s, -s, z }, { s, -s, z }, { s, s, z }, { -s, s, z } };
        }

        private static void disc(BufferBuilder bb, Matrix4f m, float x, float y, float z, float r, float cr, float cg, float cb, float a) {
            int seg = 24;
            for (int q = 0; q < seg; q++) {
                double a0 = Math.PI * 2 * q / seg, a1 = Math.PI * 2 * (q + 1) / seg;
                bb.addVertex(m, x, y, z).setColor(cr, cg, cb, a);
                bb.addVertex(m, x + (float) Math.cos(a0) * r, y, z + (float) Math.sin(a0) * r).setColor(cr, cg, cb, a * 0.6F);
                bb.addVertex(m, x + (float) Math.cos(a1) * r, y, z + (float) Math.sin(a1) * r).setColor(cr, cg, cb, a * 0.6F);
                bb.addVertex(m, x, y, z).setColor(cr, cg, cb, a);
            }
        }

        public int[] pick(double mouseX, double mouseY) {
            if (!drawn || n == 0) return null;
            Vector4f v = new Vector4f();
            int bi = -1, bk = -1;
            double best = 36.0, bestZ = -Double.MAX_VALUE;
            float off = size() / 2.0F;
            for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) {
                v.set(i * step - off + step * 0.5F, h[k * n + i] + 1 - mid, k * step - off + step * 0.5F, 1.0F);
                last.transform(v);
                double dx = v.x - mouseX, dy = v.y - mouseY, d = dx * dx + dy * dy;
                if (d > 36.0) continue;
                if (d < best - 4.0 || (d < best + 4.0 && v.z > bestZ)) { best = d; bestZ = v.z; bi = i; bk = k; }
            }
            return bi < 0 ? null : new int[] { cx - size() / 2 + bi * step + step / 2, cz - size() / 2 + bk * step + step / 2 };
        }
    }

    public static void onView(AreaViewPayload p) { ALL[Math.min(ALL.length - 1, p.token() & 7)].accept(p); }

    public static void onMarkers(AreaMarkersPayload p) { ALL[Math.min(ALL.length - 1, p.token() & 7)].accept(p); }
}
