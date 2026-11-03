package com.ashraf.wmd;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;

final class MachineLoop {

    private MachineLoop() {}

    private static final Map<BlockEntity, Loop> LOOPS = new WeakHashMap<>();

    static void ensure(BlockEntity be, SoundEvent event, BooleanSupplier running, double yOffset, float volume, boolean revUp) {
        Loop l = LOOPS.get(be);
        if (l != null && !l.isStopped()) return;
        l = new Loop(be, event, running, yOffset, volume, revUp);
        LOOPS.put(be, l);
        Minecraft.getInstance().getSoundManager().play(l);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final BlockEntity be;
        private final BooleanSupplier running;
        private final double yOffset;
        private final boolean revUp;
        private int age;

        Loop(BlockEntity be, SoundEvent event, BooleanSupplier running, double yOffset, float volume, boolean revUp) {
            super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.running = running;
            this.yOffset = yOffset;
            this.revUp = revUp;
            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            this.pitch = revUp ? 0.85F : 1.0F;
            updatePos();
        }

        private void updatePos() {
            var pos = be.getBlockPos();
            var p = ShipFrame.drawnWorld(be.getLevel(), pos, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5 + yOffset, pos.getZ() + 0.5), Float.NaN);
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }

        @Override
        public void tick() {
            if (be.isRemoved() || !running.getAsBoolean()) { stop(); return; }
            updatePos();
            if (revUp && age < 40) { age++; pitch = 0.85F + 0.15F * age / 40.0F; }
        }
    }
}
