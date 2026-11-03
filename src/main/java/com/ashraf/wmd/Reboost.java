package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Reboost {

    private Reboost() {}

    static int cells(double dist) { return Math.max(1, (int) Math.ceil(dist / Balance.REBOOST_BLOCKS_PER_CELL)); }

    static int ticks(double dist) { return Math.max(100, (int) Math.round(dist / 1000.0 * Balance.REBOOST_TICKS_PER_1000)); }

    static Component start(ServerPlayer p, int slot, int x, int z) {
        MinecraftServer server = p.server;
        if (!p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) return Component.translatable("message.wmd.reboost.not_owner");
        Station.Module core = Station.core(server, slot);
        if (core == null || !core.built()) return Component.translatable("message.wmd.reboost.no_core");
        Station.Data d = Station.Data.get(server);
        if (d.reboost.containsKey(slot)) return Component.translatable("message.wmd.reboost.busy");
        Orbit.Ground g = Orbit.ground(server, slot);
        double dist = Math.hypot(x - g.x(), z - g.z());
        if (dist < 16.0) return Component.translatable("message.wmd.reboost.here");
        if (dist > Balance.REBOOST_MAX) return Component.translatable("message.wmd.reboost.too_far", String.format("%,d", Balance.REBOOST_MAX));
        ServerLevel ground = server.getLevel(g.dim());
        if (ground == null || !ground.getWorldBorder().isWithinBounds(new BlockPos(x, 64, z))) return Component.translatable("message.wmd.reboost.border");
        int need = cells(dist), have = Cargo.playerCells(p) + Cargo.podCells(server, slot);
        if (have < need) return Component.translatable("message.wmd.reboost.cells", need, have);
        Cargo.takeCells(p, slot, need);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        long now = orbit != null ? orbit.getGameTime() : p.level().getGameTime();
        d.reboost.put(slot, new long[] { g.x(), g.z(), x, z, now + Balance.STATION_BURN_WARN, now + Balance.STATION_BURN_WARN + ticks(dist) });
        d.setDirty();
        if (orbit != null) orbit.playSound(null, Station.controller(core), WmdSounds.RB_BURN_START.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        p.sendSystemMessage(Component.translatable("message.wmd.reboost.started", x, z, need, String.format("%d:%02d", ticks(dist) / 1200, ticks(dist) / 20 % 60)).withStyle(ChatFormatting.AQUA));
        Wmd.LOGGER.info("[station] slot {} re-boosting {} blocks to {} {} ({} cells)", slot, Math.round(dist), x, z, need);
        return null;
    }

    static void tick(ServerLevel orbit, Station.Data d, int slot, Station.Module core, long now) {
        long[] r = d.reboost.get(slot);
        if (r == null) return;
        if (now >= r[5]) {
            Orbit.Ground g = Orbit.ground(orbit.getServer(), slot);
            Orbit.setGround(orbit.getServer(), slot, g.dim(), (int) r[2], (int) r[3]);
            d.reboost.remove(slot);
            d.setDirty();
            orbit.playSound(null, Station.controller(core), WmdSounds.RB_BURN_STOP.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            for (ServerPlayer o : orbit.players())
                if (Orbit.slotAt(o.getX(), o.getZ()) == slot) o.sendSystemMessage(Component.translatable("message.wmd.reboost.done", r[2], r[3]).withStyle(ChatFormatting.AQUA));
            return;
        }
        if (now < r[4]) {
            int left = (int) ((r[4] - now + 19) / 20);
            for (ServerPlayer o : orbit.players()) {
                if (Orbit.slotAt(o.getX(), o.getZ()) != slot) continue;
                boolean out = StationLife.inside(o) == null && !o.isPassenger();
                o.displayClientMessage(Component.translatable(out ? "message.wmd.reboost.warn_out" : "message.wmd.reboost.warn", left).withStyle(out ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
            }
            return;
        }
        StationPayloads.Drift dr = new StationPayloads.Drift((int) r[0], (int) r[1], (int) r[2], (int) r[3], r[4], r[5]);
        for (ServerPlayer o : orbit.players())
            if (Orbit.slotAt(o.getX(), o.getZ()) == slot && !StationWreck.leftBehind(slot, o.getUUID())) PacketDistributor.sendToPlayer(o, dr);

        BlockPos c = core.origin();
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45.0 + 90.0 * k), rr = 88.0 / 16.0 / Math.cos(Math.toRadians(22.5));
            orbit.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5 + Math.cos(a) * rr, c.getY() + 2.0, c.getZ() + 0.5 + Math.sin(a) * rr, 4, 0.2, 0.2, 0.2, 0.06);
        }
        if ((now / 20) % 4 == 0) orbit.playSound(null, Station.controller(core), WmdSounds.ST_REBOOST_LOOP.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    static long[] state(MinecraftServer server, int slot, long now) {
        long[] r = Station.Data.get(server).reboost.get(slot);
        if (r == null) return null;
        int pct = (int) Math.max(0, Math.min(100, 100 * (now - r[4]) / Math.max(1L, r[5] - r[4])));
        return new long[] { r[2], r[3], pct };
    }
}
