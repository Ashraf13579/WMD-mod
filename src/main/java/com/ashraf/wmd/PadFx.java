package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PadFx {

    private PadFx() {}

    private static Direction mount = Direction.UP;
    private static double cx, cy, cz;
    private static Level frameLevel;
    private static BlockPos framePos;

    public static void begin(LaunchTerminalBlockEntity be, Vec3 c) {
        mount = Machines.mount(be.getBlockState());
        cx = c.x; cy = c.y; cz = c.z;
        frameLevel = be.getLevel();
        framePos = be.getBlockPos();
    }

    public static void end() {
        mount = Direction.UP;
        frameLevel = null;
        framePos = null;
    }

    private static Vec3 point(double x, double y, double z) {
        if (mount == Direction.UP) return new Vec3(x, y, z);
        double[] r = Machines.mountTurn(mount, x - cx, y - cy, z - cz);
        return new Vec3(cx + r[0], cy + r[1], cz + r[2]);
    }

    private static Vec3 drawn(double x, double y, double z) {
        return ShipFrame.drawnWorld(frameLevel, framePos, point(x, y, z), Float.NaN);
    }

    private static double[] vel(Vec3 p, double vx, double vy, double vz) {
        double[] v = mount == Direction.UP ? new double[] { vx, vy, vz } : Machines.mountTurn(mount, vx, vy, vz);
        Vec3 a = ShipFrame.drawnWorld(frameLevel, framePos, p, Float.NaN);
        if (a == p) return v;
        Vec3 b = ShipFrame.drawnWorld(frameLevel, framePos, p.add(v[0], v[1], v[2]), Float.NaN);
        return new double[] { b.x - a.x, b.y - a.y, b.z - a.z };
    }

    public static void always(Level level, ParticleOptions o, boolean force, double x, double y, double z, double vx, double vy, double vz) {
        Vec3 q = point(x, y, z), p = ShipFrame.drawnWorld(frameLevel, framePos, q, Float.NaN);
        double[] v = vel(q, vx, vy, vz);
        level.addAlwaysVisibleParticle(o, force, p.x, p.y, p.z, v[0], v[1], v[2]);
    }

    public static void always(Level level, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        Vec3 q = point(x, y, z), p = ShipFrame.drawnWorld(frameLevel, framePos, q, Float.NaN);
        double[] v = vel(q, vx, vy, vz);
        level.addAlwaysVisibleParticle(o, p.x, p.y, p.z, v[0], v[1], v[2]);
    }

    public static void add(Level level, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        Vec3 q = point(x, y, z), p = ShipFrame.drawnWorld(frameLevel, framePos, q, Float.NaN);
        double[] v = vel(q, vx, vy, vz);
        level.addParticle(o, p.x, p.y, p.z, v[0], v[1], v[2]);
    }

    public static void sound(Level level, double x, double y, double z, SoundEvent s, SoundSource src, float volume, float pitch, boolean delay) {
        if (level == null) return;
        Vec3 p = drawn(x, y, z);
        level.playLocalSound(p.x, p.y, p.z, s, src, volume, pitch, delay);
    }

    public static void glow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, long gameTime) {
        Vec3 p = drawn(x, y, z);
        VeilBridge.glow(key, p.x, p.y, p.z, r, g, b, radius, brightness, gameTime);
    }

    public static void glow(long key, double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, long gameTime) {
        Vec3 p = drawn(x, y, z);
        VeilBridge.glow(key, p.x, p.y, p.z, r, g, b, radius, brightness, inscatter, gameTime);
    }

    public static void flash(double x, double y, double z, float r, float g, float b, float radius, float brightness, int duration) {
        Vec3 p = drawn(x, y, z);
        VeilBridge.flash(p.x, p.y, p.z, r, g, b, radius, brightness, duration);
    }

    public static void flash(double x, double y, double z, float r, float g, float b, float radius, float brightness, float inscatter, int duration) {
        Vec3 p = drawn(x, y, z);
        VeilBridge.flash(p.x, p.y, p.z, r, g, b, radius, brightness, inscatter, duration);
    }

    public static void bloomNear(double x, double y, double z, double range, float strength) {
        Vec3 p = drawn(x, y, z);
        VeilBridge.bloomNear(p.x, p.y, p.z, range, strength);
    }

    public static void haze(double x, double y, double z, double size, double range, float strength) {
        Vec3 p = drawn(x, y, z);
        VeilBridge.haze(p.x, p.y, p.z, size, range, strength);
    }

    public static void shock(double x, double y, double z) {
        Vec3 p = drawn(x, y, z);
        VeilBridge.shock(p.x, p.y, p.z);
    }
}
