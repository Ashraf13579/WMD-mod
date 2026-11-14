package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

final class AirframeView {

    final int n, ox, oy, oz, sx, sy, sz;
    final int[] x, y, z;
    final BlockState[] st;
    private final int[] col;
    private final Map<Long, Integer> at = new HashMap<>();
    private byte[] status;
    private VertexBuffer vb;
    private boolean dirty = true;
    private int slice;
    private final Matrix4f last = new Matrix4f();
    private boolean drawn;

    AirframeView(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        var blocks = mc.level.holderLookup(Registries.BLOCK);
        ListTag pl = t.getList("pal", Tag.TAG_COMPOUND);
        BlockState[] pal = new BlockState[pl.size()];
        for (int i = 0; i < pal.length; i++) pal[i] = NbtUtils.readBlockState(blocks, pl.getCompound(i));
        long[] c = t.getLongArray("c");
        int[] o = t.getIntArray("o");
        ox = o.length == 3 ? o[0] : 0; oy = o.length == 3 ? o[1] : 0; oz = o.length == 3 ? o[2] : 0;
        n = c.length;
        x = new int[n]; y = new int[n]; z = new int[n]; st = new BlockState[n]; col = new int[n];
        int mx = 0, my = 0, mz = 0;
        BlockPos here = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
        Map<BlockState, Integer> colours = new HashMap<>();
        for (int i = 0; i < n; i++) {
            x[i] = (int) (c[i] >>> 44 & 0xFFF); y[i] = (int) (c[i] >>> 32 & 0xFFF); z[i] = (int) (c[i] >>> 20 & 0xFFF);
            int p = (int) (c[i] & 0xFFFFF);
            st[i] = p < pal.length ? pal[p] : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            col[i] = colours.computeIfAbsent(st[i], s -> machine(s) ? MACHINE : CcHolo.colourAt(mc.level, s, here));
            mx = Math.max(mx, x[i]); my = Math.max(my, y[i]); mz = Math.max(mz, z[i]);
            at.put(key(x[i], y[i], z[i]), i);
        }
        sx = mx + 1; sy = my + 1; sz = mz + 1;
        slice = sy - 1;
    }

    static final int MACHINE = 0x4A5A70;

    static boolean machine(BlockState s) { return s.getRenderShape() != RenderShape.MODEL; }

    private static long key(int x, int y, int z) { return BlockPos.asLong(x, y, z); }

    void setStatus(byte[] s) { status = s != null && s.length == n ? s : null; dirty = true; }

    byte status(int i) { return status == null ? AirframeRecords.OK : status[i]; }

    int slice() { return slice; }

    void setSlice(int s) { s = Math.max(0, Math.min(sy - 1, s)); if (s != slice) { slice = s; dirty = true; } }

    Block block(int i) { return st[i].getBlock(); }

    void close() { if (vb != null) vb.close(); vb = null; }

    private boolean solidShown(int i) { return y[i] <= slice && status(i) != AirframeRecords.MISSING; }

    private boolean solidAt(int x, int y, int z) { Integer i = at.get(key(x, y, z)); return i != null && solidShown(i); }

    private void build() {
        dirty = false;
        close();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int faces = 0;
        for (int i = 0; i < n; i++) {
            if (!solidShown(i)) continue;
            faces += cube(bb, null, x[i], y[i], z[i], col[i], 1.0F, true);
        }
        if (faces == 0) { bb.build(); return; }
        MeshData md = bb.build();
        if (md == null) return;
        vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(md);
        VertexBuffer.unbind();
    }

    private int cube(BufferBuilder bb, Matrix4f m, int cx, int cy, int cz, int rgb, float a, boolean cull) {
        float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        float x0 = cx, y0 = cy, z0 = cz, x1 = cx + 1, y1 = cy + 1, z1 = cz + 1;
        if (!cull) { float e = 0.03F; x0 -= e; y0 -= e; z0 -= e; x1 += e; y1 += e; z1 += e; }
        int f = 0;
        if (!cull || !solidAt(cx, cy + 1, cz)) { quad(bb, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a, 1.0F); f++; }
        if (!cull || !solidAt(cx, cy - 1, cz)) { quad(bb, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a, 0.55F); f++; }
        if (!cull || !solidAt(cx, cy, cz - 1)) { quad(bb, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a, 0.8F); f++; }
        if (!cull || !solidAt(cx, cy, cz + 1)) { quad(bb, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a, 0.8F); f++; }
        if (!cull || !solidAt(cx - 1, cy, cz)) { quad(bb, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a, 0.68F); f++; }
        if (!cull || !solidAt(cx + 1, cy, cz)) { quad(bb, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a, 0.68F); f++; }
        return f;
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float r, float g, float b, float a, float k) {
        r *= k; g *= k; b *= k;
        if (m == null) {
            bb.addVertex(ax, ay, az).setColor(r, g, b, a); bb.addVertex(bx, by, bz).setColor(r, g, b, a);
            bb.addVertex(cx, cy, cz).setColor(r, g, b, a); bb.addVertex(dx, dy, dz).setColor(r, g, b, a);
        } else {
            bb.addVertex(m, ax, ay, az).setColor(r, g, b, a); bb.addVertex(m, bx, by, bz).setColor(r, g, b, a);
            bb.addVertex(m, cx, cy, cz).setColor(r, g, b, a); bb.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
        }
    }

    static final int RED = 0xFF5A48, AMBER = 0xF2C08A, FLASH = 0xFFFFFF;

    void draw(GuiGraphics gg, int x0, int y0, int w, int h, float yaw, float pitch, float zoom, int filter, IntPredicate flash, float time) {
        if (dirty) build();
        gg.enableScissor(x0, y0, x0 + w, y0 + h);
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        PoseStack ps = gg.pose();
        ps.pushPose();
        float s = Math.min(w, h) / (Math.max(Math.max(sx, sz), sy) * 1.3F) * zoom;
        ps.translate(x0 + w / 2.0F, y0 + h / 2.0F, 600.0F);
        ps.scale(s, -s, s);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-sx / 2.0F, -sy / 2.0F, -sz / 2.0F);
        last.set(ps.last().pose());
        drawn = true;
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();
        boolean dim = status != null && filter != 0;
        if (vb != null) {
            if (dim) { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.18F); RenderSystem.depthMask(false); }
            vb.bind();
            vb.drawWithShader(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ps.last().pose()), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
        }

        if (status != null || flash != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f m = ps.last().pose();
            float pulse = 0.55F + 0.45F * (float) Math.sin(time * 5.0);
            boolean blink = Math.sin(time * 12.0) > 0.0;
            int faces = 0;
            for (int i = 0; i < n; i++) {
                if (y[i] > slice) continue;
                byte b = status(i);
                if (flash != null && blink && b != AirframeRecords.OK && flash.test(i)) { faces += cube(bb, m, x[i], y[i], z[i], FLASH, 0.85F, false); continue; }
                if (b == AirframeRecords.MISSING && filter != 2) faces += cube(bb, m, x[i], y[i], z[i], RED, 0.38F, false);
                else if (b != AirframeRecords.OK && b != AirframeRecords.MISSING && filter != 1) faces += cube(bb, m, x[i], y[i], z[i], AMBER, 0.35F + 0.4F * pulse, false);
            }
            MeshData md = bb.build();
            if (md != null && faces > 0) BufferUploader.drawWithShader(md);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
        RenderSystem.enableCull();
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        ps.popPose();
        gg.disableScissor();
    }

    int pick(double mx, double my) {
        if (!drawn) return -1;
        Vector4f v = new Vector4f();
        int best = -1;
        float bz = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (y[i] > slice) continue;
            v.set(x[i] + 0.5F, y[i] + 0.5F, z[i] + 0.5F, 1.0F);
            last.transform(v);
            float dx = (float) (v.x - mx), dy = (float) (v.y - my);
            if (dx * dx + dy * dy > 16.0F || v.z < bz) continue;
            bz = v.z;
            best = i;
        }
        return best;
    }
}
