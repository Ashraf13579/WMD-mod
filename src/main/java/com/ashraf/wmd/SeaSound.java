package com.ashraf.wmd;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

final class SeaSound {

    private SeaSound() {}

    static void nf(ServerLevel sl, SoundEvent near, SoundEvent far, double farFrom, Vec3 at, float volume, float pitch) {
        double reach = Math.max(1.0F, volume) * 16.0;
        long seed = sl.random.nextLong();
        for (ServerPlayer p : sl.players()) {
            double d = p.position().distanceTo(at);
            if (d > reach) continue;
            SoundEvent s = d < farFrom ? near : far;
            p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(s), SoundSource.BLOCKS, at.x, at.y, at.z, volume, pitch, seed));
        }
    }
}
