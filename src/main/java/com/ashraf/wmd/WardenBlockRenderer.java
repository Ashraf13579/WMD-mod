package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class WardenBlockRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    private final String mesh;

    public WardenBlockRenderer(String mesh) { this.mesh = mesh; }

    @Override
    public void render(T be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof WardenTallBlock)) return;
        MeshModel m = MeshModel.named(mesh);
        boolean terminal = be instanceof WardenTerminalBlockEntity;
        if (!m.has("base")) {
            BlockState look = (terminal ? Blocks.LODESTONE : Blocks.BEACON).defaultBlockState();
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(look, ps, buffers, light, OverlayTexture.NO_OVERLAY);
            ps.pushPose(); ps.translate(0.0F, 1.0F, 0.0F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(look, ps, buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
            return;
        }
        float t = (be.getLevel() == null ? 0L : be.getLevel().getGameTime()) + pt;
        Direction f = st.getValue(WardenTallBlock.FACING);
        float rot = f == Direction.SOUTH ? 180.0F : f == Direction.EAST ? -90.0F : f == Direction.WEST ? 90.0F : 0.0F;
        int full = LightTexture.FULL_BRIGHT, ov = OverlayTexture.NO_OVERLAY;
        ps.pushPose();
        ps.translate(0.5F, 0.0F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(rot));
        m.draw("base", ps.last(), buffers, light, ov);
        if (terminal) {
            WardenTerminalBlockEntity te = (WardenTerminalBlockEntity) be;
            boolean awake = te.phase() == WardenState.FIGHT;
            m.draw("screen", ps.last(), buffers, full, ov);
            m.draw("lamps", ps.last(), buffers, full, ov);
            ps.pushPose(); ps.translate(0.0F, 0.0F, awake ? -3.0F / 16.0F : 0.0F); m.draw("tray", ps.last(), buffers, light, ov); ps.popPose();
            ps.pushPose();
            ps.translate(3.0F / 16.0F, 30.0F / 16.0F, 4.0F / 16.0F);
            ps.mulPose(Axis.YP.rotationDegrees(t * (awake ? 9.0F : 1.5F)));
            ps.translate(-3.0F / 16.0F, -30.0F / 16.0F, -4.0F / 16.0F);
            m.draw("dish", ps.last(), buffers, light, ov);
            ps.popPose();
            text(te, ps, buffers);
        } else {
            m.draw("core", ps.last(), buffers, full, ov);
            m.draw("emitter", ps.last(), buffers, full, ov);
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(t * (3.0F + 2.0F * i) * (i % 2 == 0 ? 1.0F : -1.0F)));
                m.draw("coil" + (i + 1), ps.last(), buffers, light, ov);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static void text(WardenTerminalBlockEntity te, PoseStack ps, MultiBufferSource buffers) {
        String a, b;
        int col;
        switch (te.phase()) {
            case WardenState.ARMED -> { a = "UPLINK READY"; b = "WARDEN: DORMANT"; col = 0xFF7FE0FF; }
            case WardenState.FIGHT -> { a = "!! BREACH !!"; b = "WARDEN: AWAKE"; col = 0xFFFF5040; }
            case WardenState.DOWN -> {
                long s = te.secs();
                a = "WARDEN: OFFLINE";
                b = String.format("REBOOT %02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
                col = 0xFFFFC060;
            }
            case WardenState.LAY -> { a = "SYSTEMS REPAIR"; b = "STAND BY"; col = 0xFF9FE0A0; }
            default -> { a = "NO SIGNAL"; b = ""; col = 0xFF808890; }
        }
        Font font = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(0.0F, 25.5F / 16.0F, -2.8F / 16.0F);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        float s = 0.0055F;
        ps.scale(s, -s, s);
        font.drawInBatch(a, -font.width(a) / 2.0F, -10.0F, col, false, ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(b, -font.width(b) / 2.0F, 2.0F, col, false, ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(T be) { return new AABB(be.getBlockPos()).expandTowards(0.0, 1.0, 0.0).inflate(0.5); }
}
