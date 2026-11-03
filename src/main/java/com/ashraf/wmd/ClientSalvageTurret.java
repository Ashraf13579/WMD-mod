package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

final class ClientSalvageTurret {

    private ClientSalvageTurret() {}

    private static final class Loop extends AbstractTickableSoundInstance {
        private final BlockPos pos;
        private int quiet;

        Loop(BlockPos pos) {
            super(WmdSounds.CIWS_LOOP.get(), SoundSource.HOSTILE, RandomSource.create());
            this.pos = pos;
            this.looping = true;
            this.volume = 5.0F;
            this.pitch = 1.15F;
            this.x = pos.getX() + 0.5; this.y = pos.getY() + 2.0; this.z = pos.getZ() + 0.5;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || !(level.getBlockEntity(pos) instanceof SalvageTurretBlockEntity be)) { stop(); return; }
            quiet = be.state() == SalvageTurretBlockEntity.FIRE && be.powered() ? 0 : quiet + 1;
            if (quiet > 4) stop();
        }
    }

    static void start(SalvageTurretBlockEntity be) {
        if (be.loop instanceof Loop l && !l.isStopped()) return;
        Loop l = new Loop(be.getBlockPos());
        be.loop = l;
        Minecraft.getInstance().getSoundManager().play(l);
    }

    static void stop(SalvageTurretBlockEntity be) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos p = be.getBlockPos();
        level.playLocalSound(p.getX() + 0.5, p.getY() + 2.0, p.getZ() + 0.5, WmdSounds.CIWS_END.get(), SoundSource.HOSTILE, 3.0F, 1.15F, false);
    }
}
