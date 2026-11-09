package com.ashraf.wmd;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class BayAudio {

    private BayAudio() {}

    private static final int WELD = 0, PAINT = 1, RAIL = 2, SCAN = 3, CRYO = 4, BOLT = 5, POUR = 6;
    private static final Map<BlockEntity, long[]> UNTIL = Statics.client(new WeakHashMap<>());

    static boolean seat(Level level, double x, double y, double z) {
        if (!LaunchSounds.on()) return false;
        var w = SableBridge.worldPos(level, x, y, z);
        level.playLocalSound(w.x, w.y, w.z, WmdSounds.BAY_PRESS.get(), SoundSource.BLOCKS, 1.0F, 0.95F + level.random.nextFloat() * 0.1F, false);
        return true;
    }

    static boolean weld(BlockEntity be, Level level, long now, double x, double y, double z) { return clip(be, WELD, level, now, 56, WmdSounds.BAY_WELD.get(), x, y, z, 1.0F); }

    static boolean paint(BlockEntity be, Level level, long now, double x, double y, double z) { return clip(be, PAINT, level, now, 56, WmdSounds.BAY_PAINT.get(), x, y, z, 1.0F); }

    static boolean rail(BlockEntity be, Level level, long now, double x, double y, double z) { return clip(be, RAIL, level, now, 52, WmdSounds.BAY_RAIL.get(), x, y, z, 0.9F); }

    static boolean scan(BlockEntity be, Level level, long now, double x, double y, double z) { return clip(be, SCAN, level, now, 24, WmdSounds.BAY_SCAN.get(), x, y, z, 0.75F); }

    static boolean cryo(BlockEntity be, Level level, long now, double x, double y, double z) { return clip(be, CRYO, level, now, 40, WmdSounds.BAY_CRYO.get(), x, y, z, 0.9F); }

    static boolean bolt(BlockEntity be, Level level, long now, double x, double y, double z) { return clip(be, BOLT, level, now, 16, WmdSounds.BAY_BOLT.get(), x, y, z, 0.85F); }

    static boolean pour(BlockEntity be, Level level, long now, double x, double y, double z) { return clip(be, POUR, level, now, 40, WmdSounds.TANK_FILL.get(), x, y, z, 0.8F); }

    private static boolean clip(BlockEntity be, int slot, Level level, long now, int length, SoundEvent e, double x, double y, double z, float volume) {
        if (!LaunchSounds.on()) return false;
        long[] u = UNTIL.computeIfAbsent(be, k -> new long[7]);
        if (now >= u[slot] || u[slot] - now > length + 40) {
            var w = SableBridge.worldPos(level, x, y, z);
            level.playLocalSound(w.x, w.y, w.z, e, SoundSource.BLOCKS, volume, 0.97F + level.random.nextFloat() * 0.06F, false);
            u[slot] = now + length;
        }
        return true;
    }

    static boolean oven(BlockEntity be, java.util.function.BooleanSupplier drying) {
        if (!LaunchSounds.on()) return false;
        MachineLoop.ensure(be, WmdSounds.CURING_OVEN_RUN.get(), drying, 3.0, Balance.SND_MACHINE, false);
        return true;
    }
}
