package com.ashraf.wmd;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;

public abstract class LiveScreenRenderer<T extends ProcessUnitBlockEntity> extends StagedUnitRenderer<T> {

    public record Rect(float x0, float x1, float y0, float y1, float z) {
        float w() { return x1 - x0; }
        float h() { return y1 - y0; }
    }

    public interface Canvas {
        float width();
        float height();
        void fill(float x0, float y0, float x1, float y1, int argb);

        void text(String s, float x, float y, float size, float maxW, boolean centre, int argb);
        default void text(String s, float cx, float y, float size, int argb) { text(s, cx, y, size, 1.0E9F, true, argb); }
        default void dot(float x, float y, float r, int argb) { fill(x - r, y - r, x + r, y + r, argb); }
        default void line(float x0, float y0, float x1, float y1, float step, float r, int argb) {
            float d = (float) Math.hypot(x1 - x0, y1 - y0);
            int n = Math.max(1, Math.min(400, (int) (d / step)));
            for (int i = 0; i <= n; i++) dot(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, r, argb);
        }
    }

    public static final class GuiCanvas implements Canvas {
        private final GuiGraphics g;
        private final Font font;
        private float ox, oy, w, h;
        public GuiCanvas(GuiGraphics g, Font font) { this.g = g; this.font = font; }
        public GuiCanvas box(float x, float y, float w, float h) { ox = x; oy = y; this.w = w; this.h = h; return this; }
        @Override public float width() { return w; }
        @Override public float height() { return h; }
        @Override public void fill(float x0, float y0, float x1, float y1, int argb) {
            g.fill(Math.round(ox + Math.min(x0, x1)), Math.round(oy + Math.min(y0, y1)), Math.max(Math.round(ox + Math.min(x0, x1)) + 1, Math.round(ox + Math.max(x0, x1))),
                    Math.max(Math.round(oy + Math.min(y0, y1)) + 1, Math.round(oy + Math.max(y0, y1))), argb);
        }
        @Override public void text(String s, float x, float y, float size, float maxW, boolean centre, int argb) {
            float k = size / 9.0F, w = font.width(s) * k;
            if (w > maxW && w > 0) { k *= maxW / w; w = maxW; }
            g.pose().pushPose();
            g.pose().translate(ox + (centre ? x - w / 2.0F : x), oy + y + (size - 9.0F * k) / 2.0F, 0.0F);
            g.pose().scale(k, k, 1.0F);
            g.drawString(font, s, 0, 0, argb, false);
            g.pose().popPose();
        }
    }

    static final class WorldCanvas implements Canvas {
        static final float BOX = 100.0F;
        private final PoseStack ps;
        private final MultiBufferSource buf;
        private final Rect r;
        private final float z;
        WorldCanvas(PoseStack ps, MultiBufferSource buf, Rect r, float z) { this.ps = ps; this.buf = buf; this.r = r; this.z = z; }
        @Override public float width() { return BOX; }
        @Override public float height() { return BOX * r.h() / r.w(); }
        private float mx(float x) { return r.x1 - x / BOX * r.w(); }
        private float my(float y) { return r.y1 - y / height() * r.h(); }
        @Override public void fill(float x0, float y0, float x1, float y1, int argb) {

            float a0 = Math.max(0.0F, Math.min(x0, x1)), a1 = Math.min(BOX, Math.max(x0, x1)), b0 = Math.max(0.0F, Math.min(y0, y1)), b1 = Math.min(height(), Math.max(y0, y1));
            if (a1 <= a0 || b1 <= b0) return;
            x0 = a0; x1 = a1; y0 = b0; y1 = b1;
            Matrix4f m = ps.last().pose();
            CcRenderers.rect(buf.getBuffer(RenderType.debugQuads()), m, Math.min(mx(x0), mx(x1)), Math.min(my(y0), my(y1)), Math.max(mx(x0), mx(x1)), Math.max(my(y0), my(y1)), z, argb);
        }
        @Override public void text(String s, float x, float y, float size, float maxW, boolean centre, int argb) {
            float k = size / 9.0F, w = net.minecraft.client.Minecraft.getInstance().font.width(s) * k;
            if (w > maxW && w > 0) { k *= maxW / w; w = maxW; }
            float cx = centre ? x : x + w / 2.0F;
            if (cx - w / 2.0F < -0.5F || cx + w / 2.0F > BOX + 0.5F || y < -0.5F || y + size > height() + 0.5F) return;
            CcRenderers.text(ps, buf, s, mx(cx), my(y + size / 2.0F), z - 0.01F, 9.0F * k / height() * r.h(), argb);
        }
    }

    private final Rect[] rects;

    protected LiveScreenRenderer(String name, int up, int reach, Rect... rects) {
        super(name, up, reach);
        this.rects = rects;
    }

    protected abstract void paint(T be, int screen, Canvas c, double tt);

    protected int background(T be, int screen) { return 0xF0081420; }

    @Override
    public void render(T be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        super.render(be, pt, ps, buf, light, overlay);
        if (!be.isRunning() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        CcRenderers.modelFrame(ps, f);
        double tt = be.getLevel().getGameTime() + pt;
        for (int i = 0; i < rects.length; i++) {
            Rect r = rects[i];
            float z = r.z() - 0.05F;
            WorldCanvas c = new WorldCanvas(ps, buf, r, z - 0.04F);
            new WorldCanvas(ps, buf, r, z).fill(0, 0, WorldCanvas.BOX, c.height(), background(be, i));
            paint(be, i, c, tt);
        }
        ps.popPose();
    }
}
