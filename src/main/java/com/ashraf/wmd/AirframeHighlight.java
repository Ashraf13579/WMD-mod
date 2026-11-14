package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class AirframeHighlight {

    private AirframeHighlight() {}

    private record Cell(BlockPos pos, int rgb, long until) {}

    private static final List<Cell> CELLS = Statics.client(new ArrayList<>());

    static void show(List<BlockPos> cells, List<Integer> colours) {
        CELLS.clear();
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) return;
        long until = l.getGameTime() + Balance.REC_SHOW_TICKS;
        for (int i = 0; i < cells.size() && i < Balance.REC_SHOW_MAX; i++) CELLS.add(new Cell(cells.get(i), colours.get(i), until));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || CELLS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !AirGate.AVAILABLE || !SableBridge.active()) return;
        if (level.getGameTime() > CELLS.get(0).until()) { CELLS.clear(); return; }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int lines = 0;
        float blink = 0.6F + 0.4F * (float) Math.sin(level.getGameTime() * 0.5);
        Vec3[] c = new Vec3[8];
        for (Cell cell : CELLS) {
            BlockPos p = cell.pos();
            boolean ok = true;
            for (int k = 0; k < 8 && ok; k++) {
                Vec3 in = new Vec3(p.getX() + ((k & 1) == 0 ? -0.02 : 1.02), p.getY() + ((k & 2) == 0 ? -0.02 : 1.02), p.getZ() + ((k & 4) == 0 ? -0.02 : 1.02));
                Vec3 w = ShipFrame.drawnWorld(level, p, in, pt);
                if (w == null || w == in) ok = false;
                else c[k] = w.subtract(cam);
            }
            if (!ok) continue;
            float r = (cell.rgb() >> 16 & 255) / 255.0F, g = (cell.rgb() >> 8 & 255) / 255.0F, b = (cell.rgb() & 255) / 255.0F;
            int[][] edges = { { 0, 1 }, { 2, 3 }, { 4, 5 }, { 6, 7 }, { 0, 2 }, { 1, 3 }, { 4, 6 }, { 5, 7 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
            for (int[] e : edges) {
                bb.addVertex((float) c[e[0]].x, (float) c[e[0]].y, (float) c[e[0]].z).setColor(r, g, b, blink);
                bb.addVertex((float) c[e[1]].x, (float) c[e[1]].y, (float) c[e[1]].z).setColor(r, g, b, blink);
                lines++;
            }
        }
        MeshData md = bb.build();
        if (md == null || lines == 0) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0F);
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        vb.bind();
        vb.upload(md);
        vb.drawWithShader(new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        vb.close();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
