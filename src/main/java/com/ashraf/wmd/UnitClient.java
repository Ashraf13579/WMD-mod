package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

final class UnitClient {

    private UnitClient() {}

    static void ensureSound(ProcessUnitBlockEntity be) {
        ensureOverlay(be);

        if (be.soundHandle instanceof Loop l && !l.isStopped()) {
            if (be.clientAge() - l.born < LOOP_RETRY || Minecraft.getInstance().getSoundManager().isActive(l)) return;
            l.halt();
        }
        if (be.mufflerFactor() <= 0) return;
        if (be.soundVolume() <= 0.0F) return;
        Loop loop = new Loop(be, false);
        be.soundHandle = loop;
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    static final int LOOP_RETRY = 20;

    private static void ensureOverlay(ProcessUnitBlockEntity be) {
        if (be.overlayHandle instanceof Loop l && !l.isStopped()) {
            if (be.clientAge() - l.born < LOOP_RETRY || Minecraft.getInstance().getSoundManager().isActive(l)) return;
            l.halt();
        }
        if (be.mufflerFactor() <= 0 || be.overlaySound() == null || be.overlayVolume() <= 0.01F) return;
        Loop loop = new Loop(be, true);
        be.overlayHandle = loop;
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    private static class Loop extends AbstractTickableSoundInstance {
        private final ProcessUnitBlockEntity be;
        private final boolean over;
        final int born;

        void halt() { stop(); }

        Loop(ProcessUnitBlockEntity be, boolean over) {
            super(over ? be.overlaySound() : be.runSound(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.over = over;
            this.born = be.clientAge();
            this.looping = true;
            this.delay = 0;
            this.volume = (over ? be.overlayVolume() : be.soundVolume()) * be.mufflerFactor();
            this.pitch = over ? be.overlayPitch() : be.soundPitch();
            updatePos();
        }

        private void updatePos() {
            BlockPos c = be.soundPos();
            var p = ShipFrame.drawnWorld(be.getLevel(), be.getBlockPos(), new net.minecraft.world.phys.Vec3(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5), Float.NaN);

            var mc = Minecraft.getInstance();
            boolean aboard = mc.player != null && mc.player.isPassenger()
                    && (!(mc.player.getVehicle() instanceof CockpitSeatEntity seat) || seat.cockpit() == null || ShipFrame.sameShip(be.getLevel(), seat.cockpit(), be.getBlockPos()));
            if (be.cabinSound() && aboard) {
                var eye = mc.gameRenderer.getMainCamera().getPosition();
                var d = p.subtract(eye);
                double len = d.length();
                if (len < Balance.CABIN_RANGE && len > Balance.CABIN_DIST) p = eye.add(d.scale(Balance.CABIN_DIST / len));
            }
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }

        @Override
        public void tick() {
            if (be.isRemoved() || !be.soundAlive() || be.mufflerFactor() <= 0) { stop(); return; }
            net.minecraft.sounds.SoundEvent want = over ? be.overlaySound() : be.runSound();
            if (want == null || !want.getLocation().equals(this.location)) { stop(); return; }
            if (over && be.overlayVolume() <= 0.01F) { stop(); return; }
            this.volume = (over ? be.overlayVolume() : be.soundVolume()) * be.mufflerFactor();
            updatePos();
            this.pitch = over ? be.overlayPitch() : be.soundPitch();
        }
    }
}
