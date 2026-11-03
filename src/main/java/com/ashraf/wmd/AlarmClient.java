package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

final class AlarmClient {

    private AlarmClient() {}

    static void ensureSound(AlarmBlockEntity be) {
        if (be.soundHandle instanceof Loop l && !l.isStopped()) return;
        Loop loop = new Loop(be);
        be.soundHandle = loop;
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    private static class Loop extends AbstractTickableSoundInstance {
        private final AlarmBlockEntity be;

        Loop(AlarmBlockEntity be) {
            super(WmdSounds.SIREN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.looping = true;
            this.delay = 0;
            this.volume = 6.0F;
            this.x = be.getBlockPos().getX() + 0.5;
            this.y = be.getBlockPos().getY() + 0.5;
            this.z = be.getBlockPos().getZ() + 0.5;
        }

        @Override
        public void tick() {
            if (be.isRemoved() || !be.isSounding()) stop();
        }
    }
}
