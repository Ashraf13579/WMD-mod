package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

final class CrudeTankClient {

    private CrudeTankClient() {}

    static void ensureSound(CrudeTankBlockEntity be) {
        if (be.soundHandle instanceof Loop l && !l.isStopped()) return;
        Loop loop = new Loop(be);
        be.soundHandle = loop;
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    private static class Loop extends AbstractTickableSoundInstance {
        private final CrudeTankBlockEntity be;

        Loop(CrudeTankBlockEntity be) {
            super(WmdSounds.TANK_FILL.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.8F;
            BlockPos c = be.centre();
            this.x = c.getX() + 0.5;
            this.y = c.getY() + 0.5;
            this.z = c.getZ() + 0.5;
        }

        @Override
        public void tick() {
            if (be.isRemoved() || !be.isFilling()) stop();
        }
    }
}
