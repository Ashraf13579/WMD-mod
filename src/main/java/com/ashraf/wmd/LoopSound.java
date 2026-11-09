package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class LoopSound extends AbstractTickableSoundInstance {

    private final Supplier<Vec3> where;
    private final BooleanSupplier alive;
    private boolean ended;

    private LoopSound(SoundEvent s, float volume, float pitch, Supplier<Vec3> where, BooleanSupplier alive) {
        super(s, SoundSource.BLOCKS, RandomSource.create());
        this.where = where;
        this.alive = alive;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.pitch = pitch;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        Vec3 p = where.get();
        this.x = p.x; this.y = p.y; this.z = p.z;
    }

    @Override
    public void tick() {
        if (!alive.getAsBoolean()) { ended = true; stop(); return; }
        Vec3 p = where.get();
        x = p.x; y = p.y; z = p.z;
    }

    static Object keep(Object handle, long now, SoundEvent s, float volume, float pitch, Supplier<Vec3> where, BooleanSupplier alive) {
        if (handle instanceof LoopSound l && !l.isStopped()) return l;
        if (handle != null && !(handle instanceof LoopSound m && m.ended) && now % 20 != 0) return handle;
        if (!LaunchSounds.on() || !alive.getAsBoolean()) return handle;
        LoopSound n = new LoopSound(s, volume, pitch, where, alive);
        Minecraft.getInstance().getSoundManager().play(n);
        return n;
    }
}
