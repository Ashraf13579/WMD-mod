package com.ashraf.wmd;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class ClientNavalGun {

    private ClientNavalGun() {}

    static void shot(ClientLevel level, GunnerPayloads.Fire p) {
        boolean heavy = p.kind() >= NavalGunBlockEntity.HEAVY_KIND;
        int k = p.kind() - (heavy ? NavalGunBlockEntity.HEAVY_KIND : NavalGunBlockEntity.SHOT_KIND);
        Vec3 m = new Vec3(p.fx(), p.fy(), p.fz()), to = new Vec3(p.tx(), p.ty(), p.tz());
        Vec3 d = to.subtract(m);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 dir = d.normalize();
        boolean xl = level.getBlockEntity(p.pos()) instanceof NavalGunBlockEntity g && g.spec().xl();
        if (level.getBlockEntity(p.pos()) instanceof NavalGunBlockEntity g) g.clientKick(k);
        var rnd = level.random;
        float jit = 0.95F + rnd.nextFloat() * 0.1F;
        if (!xl) WmdSound.playNF(WmdSounds.DG_SHOT.get(), WmdSounds.DG_SHOT_FAR.get(), LaunchSounds.FAR_FROM, m.x, m.y, m.z, 16.0F, jit);
        else WmdSound.playNF(WmdSounds.XL_SHOT.get(), WmdSounds.XL_SHOT_FAR.get(), LaunchSounds.FAR_FROM, m.x, m.y, m.z, heavy ? 32.0F : 24.0F,
                (heavy ? 0.85F : 1.0F) * jit);
        float size = xl ? (heavy ? 2.4F : 1.8F) : 1.0F;
        long t = level.getGameTime();
        VeilBridge.glow(0x4E61_0000_0000_0001L ^ p.pos().asLong() ^ k, m.x + dir.x * 1.5 * size, m.y + dir.y * 1.5 * size, m.z + dir.z * 1.5 * size, 1.0F, 0.72F, 0.3F,
                12.0F * size, 1.0F, t);
        VeilBridge.flash(m.x + dir.x * 2.0 * size, m.y + dir.y * 2.0 * size, m.z + dir.z * 2.0 * size, 1.0F, 0.7F, 0.28F, 26.0F * size, 2.0F * size, 3);
        int flames = (int) (6 * size);
        for (int i = 0; i < flames; i++) {
            double sp = (0.15 + rnd.nextDouble() * 0.25) * size;
            level.addParticle(ParticleTypes.FLAME, m.x + dir.x, m.y + dir.y, m.z + dir.z, dir.x * sp + (rnd.nextDouble() - 0.5) * 0.1, dir.y * sp + (rnd.nextDouble() - 0.5) * 0.1,
                    dir.z * sp + (rnd.nextDouble() - 0.5) * 0.1);
        }
        int puffs = (int) (5 * size);
        for (int i = 0; i < puffs; i++) {
            double s = 1.5 + i * 0.7;
            level.addParticle(ParticleTypes.LARGE_SMOKE, m.x + dir.x * s, m.y + dir.y * s, m.z + dir.z * s, dir.x * 0.06 + (rnd.nextDouble() - 0.5) * 0.04, 0.02,
                    dir.z * 0.06 + (rnd.nextDouble() - 0.5) * 0.04);
        }
        for (int i = 0; i < (xl ? 3 : 1); i++)
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, m.x + dir.x * (2.5 + i), m.y + dir.y * (2.5 + i), m.z + dir.z * (2.5 + i), dir.x * 0.04, 0.04, dir.z * 0.04);
        if (xl) return;

        double e = Math.asin(Math.max(-1.0, Math.min(1.0, dir.y)));
        double back = Math.cos(e) * 59.0 / 16.0 + 19.5 / 16.0;
        Vec3 h = new Vec3(dir.x, 0.0, dir.z);
        if (h.lengthSqr() < 1.0E-6) h = new Vec3(0.0, 0.0, -1.0);
        h = h.normalize();
        Vec3 port = new Vec3(m.x - h.x * back, m.y - Math.sin(e) * 59.0 / 16.0 - 0.1, m.z - h.z * back);
        level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_INGOT)), port.x, port.y, port.z, -h.x * 0.14, 0.06, -h.z * 0.14);
        level.addParticle(ParticleTypes.SMOKE, port.x, port.y, port.z, -h.x * 0.03, 0.03, -h.z * 0.03);
        level.playLocalSound(port.x, port.y - 1.0, port.z, WmdSounds.DG_CASE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.95F + level.random.nextFloat() * 0.1F, false);
    }
}
