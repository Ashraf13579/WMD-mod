package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

public final class LaunchSounds {

    private LaunchSounds() {}

    static final double FAR_FROM = 180.0;

    private static final float[] PAD_VOLUME = { 12.0F, 16.0F, 24.0F, 40.0F };

    static boolean on() { return ClientConfig.newSounds(); }

    private static float pitch(RandomSource rnd) { return 0.97F + rnd.nextFloat() * 0.06F; }

    private static RandomSource rnd() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.random : RandomSource.create();
    }

    public static void play(LaunchSoundPayload p) {
        if (Minecraft.getInstance().level == null) return;
        int tier = Math.max(1, Math.min(4, p.kind() % 10));
        if (p.kind() == LaunchSoundPayload.CRUISE) { cruise(p.x(), p.y(), p.z()); return; }
        if (p.kind() == LaunchSoundPayload.DRONE) { droneDown(p.x(), p.y(), p.z()); return; }
        if (p.kind() >= LaunchSoundPayload.BOOM) { boom(p.x(), p.y(), p.z(), tier == 4 ? 60.0F : 40.0F, 1.0F); return; }
        boolean ascent = p.kind() >= LaunchSoundPayload.ASCENT;
        float vol = PAD_VOLUME[tier - 1];
        if (!on()) {
            if (!ascent) WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), p.x(), p.y(), p.z(), 6.0F, 0.5F);
            else {
                WmdSound.play(SoundEvents.ELYTRA_FLYING, p.x(), p.y(), p.z(), 8.0F, 0.55F);
                WmdSound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, p.x(), p.y(), p.z(), 6.0F, 0.5F);
            }
            return;
        }
        SoundEvent near = ascent ? ascentNear(tier) : igniteNear(tier), far = ascent ? ascentFar(tier) : igniteFar(tier);
        WmdSound.playNF(near, far, FAR_FROM, p.x(), p.y(), p.z(), vol, pitch(rnd()));
    }

    private static SoundEvent igniteNear(int tier) {
        return switch (tier) { case 1 -> WmdSounds.LAUNCH_T1_IGNITE.get(); case 2 -> WmdSounds.LAUNCH_T2_IGNITE.get(); case 3 -> WmdSounds.LAUNCH_T3_IGNITE.get(); default -> WmdSounds.LAUNCH_T4_IGNITE.get(); };
    }

    private static SoundEvent igniteFar(int tier) {
        return switch (tier) { case 1 -> null; case 2 -> WmdSounds.LAUNCH_T2_IGNITE_FAR.get(); case 3 -> WmdSounds.LAUNCH_T3_IGNITE_FAR.get(); default -> WmdSounds.LAUNCH_T4_IGNITE_FAR.get(); };
    }

    private static SoundEvent ascentNear(int tier) {
        return switch (tier) { case 1 -> WmdSounds.LAUNCH_T1_ASCENT.get(); case 2 -> WmdSounds.LAUNCH_T2_ASCENT.get(); case 3 -> WmdSounds.LAUNCH_T3_ASCENT.get(); default -> WmdSounds.LAUNCH_T4_ASCENT.get(); };
    }

    private static SoundEvent ascentFar(int tier) {
        return switch (tier) { case 1 -> null; case 2 -> WmdSounds.LAUNCH_T2_ASCENT_FAR.get(); case 3 -> WmdSounds.LAUNCH_T3_ASCENT_FAR.get(); default -> WmdSounds.LAUNCH_T4_ASCENT_FAR.get(); };
    }

    static void cruise(double x, double y, double z) {
        if (on()) WmdSound.play(WmdSounds.CRUISE_LAUNCH.get(), x, y, z, 10.0F, pitch(rnd()));
        else {
            WmdSound.play(SoundEvents.FIREWORK_ROCKET_LAUNCH, x, y, z, 6.0F, 0.5F);
            WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 2.0F, 1.6F);
        }
    }

    static void interceptor(int kind, double x, double y, double z) {
        RandomSource r = rnd();
        if (!on()) {
            if (kind == 1 || kind == 2) {
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 14.0F, 0.45F);
                WmdSound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, x, y, z, 14.0F, 0.45F);
                WmdSound.play(SoundEvents.ELYTRA_FLYING, x, y, z, 12.0F, 0.5F);
            } else {
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 12.0F, 0.5F);
                WmdSound.play(SoundEvents.ELYTRA_FLYING, x, y, z, 12.0F, 0.55F);
                WmdSound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, x, y, z, 10.0F, 0.5F);
            }
            return;
        }
        switch (kind) {
            case 0 -> WmdSound.play(WmdSounds.INT_T1_EJECT.get(), x, y, z, 8.0F, pitch(r));
            case 3 -> WmdSound.play(WmdSounds.INT_T2_LAUNCH.get(), x, y, z, 12.0F, pitch(r));
            default -> WmdSound.playNF(WmdSounds.INT_T34_LAUNCH.get(), WmdSounds.INT_T34_LAUNCH_FAR.get(), FAR_FROM, x, y, z, 16.0F, pitch(r));
        }
    }

    static void gun(boolean howitzer, double x, double y, double z) {
        RandomSource r = rnd();
        if (!on()) {
            if (howitzer) {
                WmdSound.play(WmdSounds.HE_NEAR.get(), x, y, z, 7.0F, 1.35F);
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 6.0F, 0.7F);
            } else {
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 3.0F, 1.7F);
                WmdSound.play(WmdSounds.BLAST_WHUMP.get(), x, y, z, 2.0F, 1.6F);
            }
            return;
        }
        if (howitzer) WmdSound.playNF(WmdSounds.HOWITZER_FIRE.get(), WmdSounds.HOWITZER_FIRE_FAR.get(), FAR_FROM, x, y, z, 14.0F, pitch(r));
        else WmdSound.play(WmdSounds.MORTAR_FIRE.get(), x, y, z, 8.0F, pitch(r));
    }

    static void m270(double x, double y, double z, boolean firstOfSalvo) {
        RandomSource r = rnd();
        if (!on()) {
            WmdSound.play(SoundEvents.FIREWORK_ROCKET_LAUNCH, x, y, z, 8.0F, 0.45F + r.nextFloat() * 0.1F);
            WmdSound.play(WmdSounds.BLAST_WHUMP.get(), x, y, z, 4.0F, 0.8F);
            WmdSound.play(WmdSounds.INCENDIARY_WHOOSH.get(), x, y, z, 3.0F, 1.3F);
            return;
        }
        if (firstOfSalvo) WmdSound.playNF(WmdSounds.M270_SALVO.get(), WmdSounds.M270_SALVO_FAR.get(), FAR_FROM, x, y, z, 14.0F, pitch(r));
    }

    static void superGun(double x, double y, double z, double d, float reachVolume) {
        RandomSource r = rnd();
        if (!on()) {
            if (d < 400.0) {
                WmdSound.play(WmdSounds.HE_NEAR.get(), x, y, z, 25.0F, 0.6F);
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 25.0F, 0.45F);
                WmdSound.play(WmdSounds.BLAST_WHUMP.get(), x, y, z, 20.0F, 0.5F);
            }
            WmdSound.play(WmdSounds.HE_FAR.get(), x, y, z, reachVolume, 0.55F + r.nextFloat() * 0.05F);
            return;
        }
        WmdSound.playNF(WmdSounds.SUPERGUN_FIRE.get(), WmdSounds.SUPERGUN_FIRE_FAR.get(), 300.0, x, y, z, Math.max(reachVolume, 30.0F), pitch(r));
    }

    static void boom(double x, double y, double z, float volume, float pitch) {
        if (!on()) return;
        WmdSound.playNF(WmdSounds.SONIC_BOOM.get(), WmdSounds.SONIC_BOOM_FAR.get(), 250.0, x, y, z, volume, pitch);
    }

    static void reentry(double x, double y, double z, float volume, float pitch) {
        if (!on()) return;
        WmdSound.playNF(WmdSounds.REENTRY.get(), WmdSounds.REENTRY_FAR.get(), 250.0, x, y, z, volume, pitch);
    }

    static void terminalDive(double x, double y, double z) {
        if (on()) WmdSound.play(WmdSounds.TERMINAL_DIVE.get(), x, y, z, 14.0F, pitch(rnd()));
    }

    static void cruiseApproach(double x, double y, double z) {
        if (on()) WmdSound.play(WmdSounds.CRUISE_APPROACH.get(), x, y, z, 14.0F, pitch(rnd()));
    }

    private static final java.util.Map<Long, Long> FLYBY = new java.util.HashMap<>();

    static boolean flyby(long id, net.minecraft.world.phys.Vec3 p, double dist, long now) {
        if (!on()) return false;
        if (dist < 50.0 && now - FLYBY.getOrDefault(id, -1000L) > 160L) {
            if (FLYBY.size() > 96) FLYBY.clear();
            FLYBY.put(id, now);
            WmdSound.play(WmdSounds.FLYBY_CRACK.get(), p.x, p.y, p.z, 8.0F, pitch(rnd()));
        }
        return true;
    }

    static boolean hemp(double x, double y, double z) {
        if (!on()) return false;
        WmdSound.playNF(WmdSounds.HEMP_PULSE.get(), WmdSounds.HEMP_PULSE_FAR.get(), 250.0, x, y, z, 200.0F, 1.0F);
        return true;
    }

    static boolean interceptMiss(double x, double y, double z) {
        if (!on()) return false;
        WmdSound.play(WmdSounds.INT_MISS.get(), x, y, z, 25.0F, pitch(rnd()));
        return true;
    }

    static boolean interceptKill(double x, double y, double z) {
        if (!on()) return false;
        WmdSound.playNF(WmdSounds.INT_KILL.get(), WmdSounds.INT_KILL_FAR.get(), 250.0, x, y, z, 60.0F, pitch(rnd()));
        return true;
    }

    static void asat() {
        if (!on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        WmdSound.play(WmdSounds.ASAT_HIT.get(), cam.x, cam.y + 150.0, cam.z, 40.0F, 1.0F);
    }

    static void droneDown(double x, double y, double z) {
        if (on()) WmdSound.play(WmdSounds.DRONE_DOWN.get(), x, y, z, 14.0F, pitch(rnd()));
    }

    static void fairing(double x, double y, double z) {
        if (on()) WmdSound.play(WmdSounds.FAIRING_POP.get(), x, y, z, 16.0F, pitch(rnd()));
        else WmdSound.play(SoundEvents.FIREWORK_ROCKET_BLAST, x, y, z, 16.0F, 0.8F);
    }

    static boolean banner(Minecraft mc, int kind, java.util.List<String> args) {
        if (!on()) return false;
        SoundEvent e = null;
        if (kind == CcDefcon.B_LOCKDOWN) e = WmdSounds.DEFCON_1.get();
        else if (kind == CcDefcon.B_INCOMING) e = WmdSounds.DEFCON_2.get();
        else if (kind == CcDefcon.B_DEFCON && !args.isEmpty()) {
            int lv;
            try { lv = Integer.parseInt(args.get(0).trim()); } catch (NumberFormatException ex) { return false; }
            e = lv == 1 ? WmdSounds.DEFCON_1.get() : lv == 2 ? WmdSounds.DEFCON_2.get() : lv == 3 ? WmdSounds.DEFCON_3.get() : WmdSounds.CC_PING.get();
        }
        if (e == null && kind == CcDefcon.B_COUNTER) e = WmdSounds.CC_ALERT.get();
        else if (e == null && kind == CcDefcon.B_BDA) e = WmdSounds.CC_PING.get();
        if (e == null) return false;
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(e, 1.0F, 0.9F));
        return true;
    }

    static void interceptorBoost(double x, double y, double z) {
        if (!on()) {
            WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 12.0F, 0.6F);
            WmdSound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, x, y, z, 12.0F, 0.5F);
            WmdSound.play(SoundEvents.ELYTRA_FLYING, x, y, z, 12.0F, 0.6F);
            return;
        }
        WmdSound.play(WmdSounds.INT_T1_BOOST.get(), x, y, z, 12.0F, pitch(rnd()));
    }
}
