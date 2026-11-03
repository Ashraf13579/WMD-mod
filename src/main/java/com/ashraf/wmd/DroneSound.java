package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

final class DroneSound {

    private DroneSound() {}

    private static final class Loop extends AbstractTickableSoundInstance {
        private final StrikeDroneEntity e;

        Loop(StrikeDroneEntity e, SoundEvent s, float volume, float pitch) {
            super(s, SoundSource.HOSTILE, RandomSource.create());
            this.e = e;
            this.looping = true;
            this.volume = volume;
            this.pitch = pitch;
            this.x = e.getX(); this.y = e.getY(); this.z = e.getZ();
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (e.isRemoved()) { stop(); return; }
            x = e.getX(); y = e.getY(); z = e.getZ();
        }
    }

    static void tick(StrikeDroneEntity e) {
        if (e.loop instanceof Loop l && !l.isStopped()) return;
        if (e.loop != null && e.tickCount % 20 != 0) return;
        if (!LaunchSounds.on()) return;
        Loop l = switch (e.type()) {
            case T1 -> new Loop(e, WmdSounds.DRONE_QUAD_LOOP.get(), 2.0F, 1.15F);
            case T2 -> new Loop(e, WmdSounds.DRONE_QUAD_LOOP.get(), 2.5F, 0.95F);
            case T3 -> new Loop(e, WmdSounds.DRONE_PROP_LOOP.get(), 4.0F, 1.0F);
            default -> new Loop(e, WmdSounds.DRONE_JET_LOOP.get(), 6.0F, 1.0F);
        };
        e.loop = l;
        Minecraft.getInstance().getSoundManager().play(l);
    }
}
