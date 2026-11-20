package com.ashraf.wmd;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class WardenFight {

    private WardenFight() {}

    static BlockPos uplink(WardenState d) {
        return new BlockPos(d.ox + 13, d.oy + WardenBunker.F + 2, d.oz - 36);
    }

    static boolean isUplink(ServerLevel level, BlockPos pos) {
        if (level != level.getServer().overworld()) return false;
        WardenState d = WardenState.get(level.getServer());
        return d.placed && (pos.equals(uplink(d)) || pos.equals(uplink(d).above()));
    }

    static void uplinkHint(ServerLevel level, net.minecraft.world.entity.player.Player p) {
        WardenState d = WardenState.get(level.getServer());
        if (d.phase == WardenState.DOWN) {
            long s = d.msToRespawn() / 1000;
            p.displayClientMessage(Component.translatable("message.wmd.warden.uplink_dead", s / 3600, s / 60 % 60), true);
        } else if (d.phase == WardenState.FIGHT) p.displayClientMessage(Component.translatable("message.wmd.warden.awake"), true);
        else if (d.phase == WardenState.ARMED) p.displayClientMessage(Component.translatable("message.wmd.warden.uplink_hint"), true);
        else p.displayClientMessage(Component.translatable("message.wmd.warden.not_ready"), true);
    }

    static void hacked(ServerLevel level, ServerPlayer p) {
        WardenState d = WardenState.get(level.getServer());
        if (d.phase == WardenState.DOWN) {
            long s = d.msToRespawn() / 1000;
            p.displayClientMessage(Component.translatable("message.wmd.warden.uplink_dead", s / 3600, s / 60 % 60), true);
            return;
        }
        if (d.phase != WardenState.ARMED) {
            p.displayClientMessage(Component.translatable(d.phase == WardenState.FIGHT ? "message.wmd.warden.awake" : "message.wmd.warden.not_ready"), true);
            return;
        }
        WardenEntity w = find(level, d);
        if (w == null) { p.displayClientMessage(Component.translatable("message.wmd.warden.not_ready"), true); return; }
        wake(level, d, w);
    }

    static void wake(ServerLevel level, WardenState d, WardenEntity w) {
        d.phase = WardenState.FIGHT;
        d.setDirty();
        w.setMode(WardenEntity.KLAXON);
        for (ServerPlayer adv : level.players()) if (adv.distanceToSqr(w) < 160.0 * 160.0) WmdTrigger.fire(adv, "warden_wake");
        WardenMusic.send(level, w.blockPosition(), null, "boss_intro");
        MusicTriggersHook.boss(level, "boss_intro", w.blockPosition(), null);
        MusicTriggersHook.flag(level, "wmd:boss_fight", true, 72_000L);
        for (ServerPlayer pl : level.players())
            if (pl.distanceToSqr(w) < Balance.WARDEN_SCALE_R * Balance.WARDEN_SCALE_R)
                pl.displayClientMessage(Component.translatable("message.wmd.warden.breach"), false);
    }

    static WardenEntity find(ServerLevel level, WardenState d) {
        AABB box = box(d);
        List<WardenEntity> l = level.getEntitiesOfClass(WardenEntity.class, box, e -> e.isAlive());
        if (l.isEmpty()) return null;
        for (int i = 1; i < l.size(); i++) l.get(i).discard();
        return l.get(0);
    }

    static boolean inside(WardenState d, double x, double y, double z, boolean hatchOpen) {
        double rx = x - (d.ox + 0.5), rz = z - (d.oz + 0.5), ry = y - d.oy;
        if (rx < WardenBunker.X0 - 1 || rx > WardenBunker.X1 + 1 || rz < WardenBunker.Z0 - 1 || rz > WardenBunker.Z1 + 1) return false;
        if (ry < -1.0) return true;
        return hatchOpen && Math.abs(rx) <= WardenBunker.SHAFT + 1 && Math.abs(rz) <= WardenBunker.SHAFT + 1 && ry < 96.0;
    }

    static AABB box(WardenState d) {
        return new AABB(d.ox + WardenBunker.X0, d.oy + WardenBunker.H0, d.oz + WardenBunker.Z0,
                d.ox + WardenBunker.X1 + 1, d.oy + WardenBunker.H1 + 1, d.oz + WardenBunker.Z1 + 1);
    }

    static void keep(ServerLevel level, WardenState d) {
        if (!d.placed || d.oy == Integer.MIN_VALUE) return;
        if (!level.isLoaded(new BlockPos(d.ox, d.oy, d.oz)) || !level.areEntitiesLoaded(ChunkPos.asLong(d.ox >> 4, d.oz >> 4))) return;
        boolean near = false;
        double r2 = Balance.WARDEN_WAKE_R * Balance.WARDEN_WAKE_R;
        for (ServerPlayer pl : level.players()) if (pl.distanceToSqr(d.ox, d.oy, d.oz) < r2) { near = true; break; }
        if (!near || find(level, d) != null) return;
        WardenEntity w = Wmd.WARDEN.get().create(level);
        if (w == null) return;
        boolean fighting = d.phase == WardenState.FIGHT;
        double y = d.oy + (fighting ? WardenBunker.F + 1 : WardenBunker.WARDEN_H);
        w.moveTo(d.ox + 0.5, y, d.oz + 0.5, 180.0F, 0.0F);
        w.setMode(fighting ? WardenEntity.ACTIVE : WardenEntity.DORMANT);
        level.addFreshEntity(w);
        Wmd.LOGGER.info("[warden] {} Warden placed at {} {} {}", fighting ? "a fighting" : "a dormant", d.ox, (int) y, d.oz);
    }

    static void write(ServerLevel level, BlockPos pos, BlockState s) {
        if (level.getBlockState(pos) != s) level.setBlock(pos, s, 2 | 16);
    }

    static void openRoof(ServerLevel level, WardenState d) {
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int x = -WardenBunker.SHAFT; x <= WardenBunker.SHAFT; x++)
            for (int z = -WardenBunker.SHAFT; z <= WardenBunker.SHAFT; z++) {
                for (int h = -2; h <= -1; h++) write(level, new BlockPos(d.ox + x, d.oy + h, d.oz + z), air);
                for (int h = 0; h <= 1; h++) {
                    BlockPos p = new BlockPos(d.ox + x, d.oy + h, d.oz + z);
                    if (level.isLoaded(p) && !level.getBlockState(p).isAir() && Config.Server.canBreak(level, p)) level.setBlock(p, air, 2 | 16);
                }
            }
        double cx = d.ox + 0.5, cy = d.oy, cz = d.oz + 0.5;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 2, 4.0, 0.5, 4.0, 0.0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, cx, cy, cz, 60, 6.0, 1.0, 6.0, 0.08);
        sound(level, cx, cy, cz, WmdSounds.BLAST_WHUMP.get(), Balance.SND_RELEASE, 0.5F);
    }

    static BlockPos placeCrate(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at).mutable();
        for (int i = 0; i < 6 && level.isLoaded(p) && !level.getBlockState(p).isAir(); i++) p.move(0, 1, 0);
        if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) return null;
        BlockPos c = p.immutable();
        write(level, c, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, level.random, c, SalvageBuilder.loot("warden"));
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 30, 0.4, 1.0, 0.4, 0.05);
        return c;
    }

    static net.minecraft.world.phys.Vec3 onWarden(ServerLevel level, net.minecraft.world.phys.Vec3 ground) {
        net.minecraft.world.phys.Vec3 best = ground;
        for (WardenEntity w : level.getEntitiesOfClass(WardenEntity.class, new AABB(ground, ground).inflate(16.0, 4.0, 16.0).expandTowards(0.0, 40.0, 0.0), e -> e.isAlive())) {
            for (WardenPart p : w.parts) {
                AABB b = p.getBoundingBox();
                if (ground.x < b.minX || ground.x > b.maxX || ground.z < b.minZ || ground.z > b.maxZ || b.maxY <= best.y) continue;
                best = new net.minecraft.world.phys.Vec3(ground.x, b.maxY, ground.z);
            }
        }
        return best;
    }

    static final java.util.UUID WARDEN_OWNER = java.util.UUID.nameUUIDFromBytes("wmd:warden".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    static HangarDoorBlockEntity door(ServerLevel level, WardenState d) {
        if (!AirGate.AVAILABLE) return null;
        BlockPos drum = new BlockPos(d.ox, d.oy + WardenBunker.F + WardenBunker.DOOR_H + 1, d.oz + WardenBunker.DOOR_Z);
        return level.isLoaded(drum) ? HangarDoorBlockEntity.at(level, drum) : null;
    }

    static void sound(ServerLevel level, double x, double y, double z, net.minecraft.sounds.SoundEvent ev, float classVolume, float pitch) {
        level.playSound(null, x, y, z, ev, SoundSource.HOSTILE, classVolume, pitch);
    }
}
