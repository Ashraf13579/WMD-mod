package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class IncomingSound extends AbstractTickableSoundInstance {

    private final long flightId;

    public IncomingSound(long flightId) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.flightId = flightId;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 0.5F;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = false;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Flight f = ClientFlights.FLIGHTS.get(flightId);
        if (f == null || mc.level == null || mc.player == null) { stop(); return; }
        double t = mc.level.getGameTime();
        if (t >= f.endTime()) { stop(); return; }
        Vec3 p = f.position(t);
        x = p.x; y = p.y; z = p.z;
        double left = (f.endTime() - t) / 20.0;
        pitch = (float) Mth.clamp(0.55 + (20.0 - left) / 20.0 * 1.35, 0.55, 1.9);
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(p);
        volume = (float) Mth.clamp(1.7 - d / 500.0, 0.0, 1.7);
    }
}
