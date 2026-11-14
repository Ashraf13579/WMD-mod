package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class AnchorClient {

    private AnchorClient() {}

    static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof AnchorWindlassBlockEntity)) return;
        if (!(mc.screen instanceof AnchorScreen s && s.pos().equals(pos))) mc.setScreen(new AnchorScreen(pos));
    }

    static Vec3 toPlot(Level level, BlockPos pos, Vec3 world, float pt) {
        if (!SableBridge.active() || level == null || world == null) return world;
        try {
            var pose = SableClient.pose(level, pos, pt);
            return pose == null ? world : pose.transformPositionInverse(world);
        } catch (Throwable t) { SableBridge.fail(t); return world; }
    }

    static float[] model(BlockPos pos, Direction f, Vec3 plot) {
        double[] l = AirLights.local(plot.x - (pos.getX() + 0.5), plot.z - (pos.getZ() + 0.5), f);
        return new float[] { (float) l[0], (float) (plot.y - pos.getY()), (float) l[1] };
    }

    static float[] down(Level level, BlockPos pos, Direction f, float pt) {
        Vec3 d = ShipFrame.dirInFrame(level, pos, new Vec3(0.0, -1.0, 0.0), pt);
        double[] l = AirLights.local(d.x, d.z, f);
        return new float[] { (float) l[0], (float) d.y, (float) l[1] };
    }
}
