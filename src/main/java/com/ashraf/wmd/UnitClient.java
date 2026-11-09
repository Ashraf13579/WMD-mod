package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

final class UnitClient {

    private UnitClient() {}

    static void ensureSound(ProcessUnitBlockEntity be) {
        if (be.soundHandle instanceof Loop l && !l.isStopped()) return;
        if (be.mufflerFactor() <= 0) return;
        if (be.soundVolume() <= 0.0F) return;
        Loop loop = new Loop(be);
        be.soundHandle = loop;
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    private static class Loop extends AbstractTickableSoundInstance {
        private final ProcessUnitBlockEntity be;

        Loop(ProcessUnitBlockEntity be) {
            super(be.runSound(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.looping = true;
            this.delay = 0;
            this.volume = be.soundVolume() * be.mufflerFactor();
            this.pitch = be.soundPitch();
            updatePos();
        }

        private void updatePos() {
            BlockPos c = be.soundPos();
            var p = ShipFrame.drawnWorld(be.getLevel(), be.getBlockPos(), new net.minecraft.world.phys.Vec3(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5), Float.NaN);
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }

        @Override
        public void tick() {
            if (be.isRemoved() || !be.soundAlive() || be.mufflerFactor() <= 0) { stop(); return; }
            this.volume = be.soundVolume() * be.mufflerFactor();
            updatePos();
            this.pitch = be.soundPitch();
        }
    }
}
