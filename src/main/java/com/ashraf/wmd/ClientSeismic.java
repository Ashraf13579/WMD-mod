package com.ashraf.wmd;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientSeismic {

    private ClientSeismic() {}

    private static List<SeismicResultPayload.Hit> hits = List.of();
    private static long until;

    public static void onResult(SeismicResultPayload p) {
        Minecraft mc = Minecraft.getInstance();
        hits = p.hits();
        until = mc.level == null ? 0 : mc.level.getGameTime() + Balance.SEIS_SHOW;
    }

    public static void clearStatics() { hits = List.of(); until = 0; }

    private static final int[] COLOURS = { 0xFFFF9A30, 0xFFFF3030, 0xFFFFE040, 0xFFC070FF, 0xFF40E0FF };

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || hits.isEmpty() || mc.level == null) return;
        long now = mc.level.getGameTime();
        if (now >= until) { hits = List.of(); return; }
        Vec3 cam = event.getCamera().getPosition();
        float pulse = 0.55F + 0.45F * (float) Math.sin(now * 0.25);
        float fade = Math.min(1.0F, (until - now) / 60.0F);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (SeismicResultPayload.Hit h : hits) {
            BlockPos p = BlockPos.of(h.pos());
            int c = COLOURS[Math.max(0, Math.min(COLOURS.length - 1, h.kind()))];
            float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F, a = pulse * fade;
            float x0 = (float) (p.getX() - cam.x) - 0.02F, y0 = (float) (p.getY() - cam.y) - 0.02F, z0 = (float) (p.getZ() - cam.z) - 0.02F;
            float x1 = x0 + 1.04F, y1 = y0 + 1.04F, z1 = z0 + 1.04F;
            float[][] e = { { x0, y0, z0, x1, y0, z0 }, { x0, y1, z0, x1, y1, z0 }, { x0, y0, z1, x1, y0, z1 }, { x0, y1, z1, x1, y1, z1 },
                    { x0, y0, z0, x0, y1, z0 }, { x1, y0, z0, x1, y1, z0 }, { x0, y0, z1, x0, y1, z1 }, { x1, y0, z1, x1, y1, z1 },
                    { x0, y0, z0, x0, y0, z1 }, { x1, y0, z0, x1, y0, z1 }, { x0, y1, z0, x0, y1, z1 }, { x1, y1, z0, x1, y1, z1 } };
            for (float[] s : e) {
                bb.addVertex(s[0], s[1], s[2]).setColor(r, g, b, a);
                bb.addVertex(s[3], s[4], s[5]).setColor(r, g, b, a);
            }
        }
        MeshData md = bb.build();
        if (md == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        com.mojang.blaze3d.vertex.VertexBuffer vb = new com.mojang.blaze3d.vertex.VertexBuffer(com.mojang.blaze3d.vertex.VertexBuffer.Usage.DYNAMIC);
        vb.bind();
        vb.upload(md);
        vb.drawWithShader(new Matrix4f(event.getModelViewMatrix()), event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        vb.close();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
