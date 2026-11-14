package com.ashraf.wmd;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class WorldOverlayGeo {

    private WorldOverlayGeo() {}

    static BufferBuilder quads() { return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR); }

    static BufferBuilder lines() { return Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR); }

    static void draw(RenderLevelStageEvent event, BufferBuilder bb, float lineWidth) { draw(event, bb, lineWidth, null); }

    static void draw(RenderLevelStageEvent event, BufferBuilder bb, float lineWidth, Matrix4f pre) {
        MeshData md = bb.build();
        if (md == null) return;
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        if (pre != null) mv.mul(pre);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        if (lineWidth > 0) RenderSystem.lineWidth(lineWidth);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(md);
        if (lineWidth > 0) RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.restoreProjectionMatrix();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    static void line(BufferBuilder lb, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float a = (argb >>> 24) / 255F, r = (argb >> 16 & 255) / 255F, g = (argb >> 8 & 255) / 255F, b = (argb & 255) / 255F;
        lb.addVertex(x0, y0, z0).setColor(r, g, b, a);
        lb.addVertex(x1, y1, z1).setColor(r, g, b, a);
    }

    static void boxLines(BufferBuilder lb, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        line(lb, x0, y0, z0, x1, y0, z0, argb); line(lb, x0, y0, z1, x1, y0, z1, argb);
        line(lb, x0, y1, z0, x1, y1, z0, argb); line(lb, x0, y1, z1, x1, y1, z1, argb);
        line(lb, x0, y0, z0, x0, y0, z1, argb); line(lb, x1, y0, z0, x1, y0, z1, argb);
        line(lb, x0, y1, z0, x0, y1, z1, argb); line(lb, x1, y1, z0, x1, y1, z1, argb);
        line(lb, x0, y0, z0, x0, y1, z0, argb); line(lb, x1, y0, z0, x1, y1, z0, argb);
        line(lb, x0, y0, z1, x0, y1, z1, argb); line(lb, x1, y0, z1, x1, y1, z1, argb);
    }

    static void quad(BufferBuilder bb, float[] p0, float[] p1, float[] p2, float[] p3, int argb) {
        float a = (argb >>> 24) / 255F, r = (argb >> 16 & 255) / 255F, g = (argb >> 8 & 255) / 255F, b = (argb & 255) / 255F;
        bb.addVertex(p0[0], p0[1], p0[2]).setColor(r, g, b, a);
        bb.addVertex(p1[0], p1[1], p1[2]).setColor(r, g, b, a);
        bb.addVertex(p2[0], p2[1], p2[2]).setColor(r, g, b, a);
        bb.addVertex(p3[0], p3[1], p3[2]).setColor(r, g, b, a);
    }

    static void boxFaces(BufferBuilder bb, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        quad(bb, v(x0, y0, z0), v(x1, y0, z0), v(x1, y0, z1), v(x0, y0, z1), argb);
        quad(bb, v(x0, y1, z0), v(x0, y1, z1), v(x1, y1, z1), v(x1, y1, z0), argb);
        quad(bb, v(x0, y0, z0), v(x0, y1, z0), v(x1, y1, z0), v(x1, y0, z0), argb);
        quad(bb, v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1), argb);
        quad(bb, v(x0, y0, z0), v(x0, y0, z1), v(x0, y1, z1), v(x0, y1, z0), argb);
        quad(bb, v(x1, y0, z0), v(x1, y1, z0), v(x1, y1, z1), v(x1, y0, z1), argb);
    }

    static float[] v(float x, float y, float z) { return new float[] { x, y, z }; }

    static float[][] tangents(Direction n) {
        return switch (n.getAxis()) {
            case X -> new float[][] { { 0, 0, 1 }, { 0, 1, 0 } };
            case Z -> new float[][] { { 1, 0, 0 }, { 0, 1, 0 } };
            default -> new float[][] { { 1, 0, 0 }, { 0, 0, 1 } };
        };
    }

    static void ring(BufferBuilder bb, float[] c, float[] u, float[] w, float r0, float r1, int argb) {
        final int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(bb, at(c, u, w, c0 * r0, s0 * r0), at(c, u, w, c0 * r1, s0 * r1), at(c, u, w, c1 * r1, s1 * r1), at(c, u, w, c1 * r0, s1 * r0), argb);
        }
    }

    static float[] at(float[] c, float[] u, float[] w, float a, float b) {
        return new float[] { c[0] + u[0] * a + w[0] * b, c[1] + u[1] * a + w[1] * b, c[2] + u[2] * a + w[2] * b };
    }

    static int argb(int rgb, float alpha) { return ((int) (Math.max(0F, Math.min(1F, alpha)) * 255) << 24) | (rgb & 0xFFFFFF); }
}
