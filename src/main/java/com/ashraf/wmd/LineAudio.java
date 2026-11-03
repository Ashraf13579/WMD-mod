package com.ashraf.wmd;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

final class LineAudio {

    private LineAudio() {}

    private static final Map<Object, double[]> LAST = Statics.client(new WeakHashMap<>());
    private static final double[] PRESS_HITS = { 0.34, 0.50, 0.66 };

    private static boolean working(double f) { return f >= 0.24 && f < 0.76; }

    private static double[] along(BlockPos origin, Direction f, Direction m, double blocks) {
        BlockPos c = Machines.toWorld(origin, f, m, -(int) Math.round(blocks), 0, 0);
        return new double[] { c.getX() + 0.5, c.getY() + 1.2, c.getZ() + 0.5 };
    }

    private static double[] here(BlockPos p) { return new double[] { p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5 }; }

    static void drone(Level level, DroneModuleBlockEntity be) {
        double[] last = LAST.computeIfAbsent(be, k -> new double[] { 1.0, 0.0 });
        if (be.kind() == DroneKind.HANGAR) {
            double door = be.doorOpen(0.0F), prevDoor = last[1];
            last[1] = door;
            if (prevDoor <= 0.0 && door > 0.0 || prevDoor >= 1.0 && door < 1.0) play(level, WmdSounds.HANGAR_DOOR.get(), here(be.getBlockPos()), 3.0F);
        }
        DroneModuleBlockEntity core = be.kind() == DroneKind.CORE ? be : DroneLine.coreOf(be);
        if (core == null) return;
        DroneAnim.Pose p = core.pose(0.0F);
        long now = level.getGameTime();
        double[] w = here(be.getBlockPos());
        boolean mine = p.active == be.kind();
        double prev = last[0];
        last[0] = mine ? p.f : 1.0;
        switch (be.kind()) {
            case CORE -> {
                if (core.stage().name().startsWith("TO_")) {
                    Direction f = core.getBlockState().getValue(MachineBlock.FACING);
                    double[] c = along(core.getBlockPos(), f, Machines.mount(core.getBlockState()), p.car * DroneKind.STEP);
                    BayAudio.rail(be, level, now, c[0], c[1], c[2]);
                }
            }
            case FRAME -> { if (mine && working(p.f)) BayAudio.weld(be, level, now, w[0], w[1], w[2]); }
            case AVIONICS, PROPULSION -> { if (mine && working(p.f)) BayAudio.bolt(be, level, now, w[0], w[1], w[2]); }
            case TEST -> { if (mine && prev < 0.25 && p.f >= 0.25) play(level, WmdSounds.DRONE_TEST_SPINUP.get(), w, 1.5F); }
            case STORAGE -> { if (mine && p.onStacker) BayAudio.rail(be, level, now, w[0], w[1], w[2]); }
            default -> { }
        }
    }

    private static void play(Level level, net.minecraft.sounds.SoundEvent s, double[] w, float volume) {
        if (!LaunchSounds.on()) return;
        var p = SableBridge.worldPos(level, w[0], w[1], w[2]);
        level.playLocalSound(p.x, p.y, p.z, s, SoundSource.BLOCKS, volume, 1.0F, false);
    }

    static void bomb(Level level, BombLineBlockEntity be) {
        BombLineBlockEntity core = be.kind() == BombLineKind.CORE ? be : BombLine.coreOf(be);
        if (core == null) return;
        BombLineAnim.Pose p = core.pose(0.0F);
        long now = level.getGameTime();
        double[] w = here(be.getBlockPos());
        boolean mine = p.active == be.kind();
        double[] last = LAST.computeIfAbsent(be, k -> new double[] { 1.0 });
        double prev = last[0];
        last[0] = mine ? p.f : 1.0;
        switch (be.kind()) {
            case CORE -> {
                if (core.stage().name().startsWith("TO_")) {
                    Direction f = core.getBlockState().getValue(MachineBlock.FACING);
                    double[] c = along(core.getBlockPos(), f, Machines.mount(core.getBlockState()), p.car * BombLineKind.STEP);
                    BayAudio.rail(be, level, now, c[0], c[1], c[2]);
                }
            }
            case PRESS -> {
                if (!mine || !LaunchSounds.on()) return;
                for (double h : PRESS_HITS) if (prev < h && p.f >= h) {
                    var s = SableBridge.worldPos(level, w[0], w[1], w[2]);
                    level.playLocalSound(s.x, s.y, s.z, WmdSounds.BAY_PRESS.get(), SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.1F, false);
                }
            }
            case FILLING -> { if (mine && p.f >= 0.3 && p.f < 0.55) BayAudio.pour(be, level, now, w[0], w[1], w[2]); }
            case FUZE -> { if (mine && working(p.f)) BayAudio.bolt(be, level, now, w[0], w[1], w[2]); }
            case STORAGE -> { if (mine && p.onStacker) BayAudio.rail(be, level, now, w[0], w[1], w[2]); }
            case CRADLE -> { if (mine && p.onCradle && !p.done) BayAudio.rail(be, level, now, w[0], w[1], w[2]); }
            default -> { }
        }
    }
}
