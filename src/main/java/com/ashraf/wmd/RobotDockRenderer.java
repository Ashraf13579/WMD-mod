package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class RobotDockRenderer implements BlockEntityRenderer<RobotDockBlockEntity> {

    private static final String NAME = "robot_dock";
    private static final float[] ARM = { -8.0F, 27.0F, 121.0F };

    static final float ARM_UP = 25.0F, SHOE_FREE = -0.3F;
    static final int SEGS = 10, GUIDES = 8;

    @Override
    public void render(RobotDockBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(NAME);
        if (!mesh.has("base")) return;
        float t = (be.getLevel() == null ? 0L : be.getLevel().getGameTime() % 24000L) + partialTick;
        int phase = be.phase();
        boolean powered = be.powered(), connected = phase == RobotDockBlockEntity.CONNECTED;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        mesh.draw("base", ps.last(), buffers, light, overlay);

        if (mesh.has("arm")) {
            float k = be.armDown(partialTick);
            float[] pv = SatelliteMesh.pivot(NAME, "arm", ARM);
            ps.pushPose();
            ps.translate(pv[0] / 16.0F, pv[1] / 16.0F, pv[2] / 16.0F);
            ps.mulPose(Axis.XP.rotationDegrees(ARM_UP * (1.0F - k)));
            ps.translate(-pv[0] / 16.0F, -pv[1] / 16.0F, -pv[2] / 16.0F);
            mesh.draw("arm", ps.last(), buffers, light, overlay);
            if (mesh.has("shoe")) {
                ps.pushPose();
                if (!be.contactClosed()) ps.translate(0.0F, SHOE_FREE / 16.0F, 0.0F);
                mesh.draw("shoe", ps.last(), buffers, light, overlay);
                int age = be.beatAge();
                boolean arc = age >= 0 && age < 8 && age != 3 && age != 6;
                if (mesh.has("spark") && arc) mesh.draw("spark", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
                ps.popPose();
            }
            ps.popPose();
        }

        if (connected) {
            int lit = be.level10();
            boolean blink = be.charging() && ((int) (t / 8.0F) & 1) == 0;
            for (int i = 0; i < SEGS; i++) {
                if (i < lit || i == lit && blink) {
                    String b = "seg" + i;
                    if (mesh.has(b)) mesh.draw(b, ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
                }
            }
        }

        if (powered) {
            float head = phase == RobotDockBlockEntity.INBOUND ? (t % 20.0F) / 20.0F * (GUIDES + 2) - 1.0F : -100.0F;
            for (int i = 0; i < GUIDES; i++) {
                boolean on = head > -50.0F ? Math.abs(i - head) < 1.3F : true;
                String b = "guide" + i;
                if (on && mesh.has(b)) mesh.draw(b, ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
            }
        }

        boolean fast = ((int) (t / 5.0F) & 1) == 0, slow = (t % 20.0F) < 15.0F;
        boolean red = !powered && ((int) (t / 10.0F) & 1) == 0;
        boolean amber = powered && (phase == RobotDockBlockEntity.INBOUND && fast || connected && be.level10() < 10 && (!be.charging() || slow));
        boolean green = powered && (phase == RobotDockBlockEntity.READY || connected && be.level10() >= 10);
        if (green && mesh.has("lamp_g")) mesh.draw("lamp_g", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        if (amber && mesh.has("lamp_a")) mesh.draw("lamp_a", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        if (red && mesh.has("lamp_r")) mesh.draw("lamp_r", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        if (mesh.has("glow")) mesh.draw("glow", ps.last(), buffers, powered ? LightTexture.FULL_BRIGHT : light, overlay);
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RobotDockBlockEntity be) { return new AABB(be.getBlockPos()).inflate(10.0, 3.0, 10.0); }

    @Override
    public int getViewDistance() { return 128; }
}
