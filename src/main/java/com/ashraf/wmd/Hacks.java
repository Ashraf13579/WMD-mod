package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class Hacks {

    private Hacks() {}

    private record Hack(UUID hacker, long until) {}

    private static final Map<String, Map<Long, Hack>> HACKS = new HashMap<>();

    private static String dim(Level l) { return l.dimension().location().toString(); }

    static synchronized void grant(Level level, BlockPos pos, UUID hacker, long until) {
        HACKS.computeIfAbsent(dim(level), k -> new HashMap<>()).put(pos.asLong(), new Hack(hacker, until));
    }

    static synchronized UUID hacker(Level level, BlockPos pos) {
        Map<Long, Hack> m = HACKS.get(dim(level));
        Hack h = m == null ? null : m.get(pos.asLong());
        if (h == null) return null;
        if (level.getGameTime() >= h.until()) { m.remove(pos.asLong()); return null; }
        return h.hacker();
    }

    static boolean allows(Owned o, Player p) {
        return !weapon(o) && hackedBy(o, p);
    }

    private static boolean hackedBy(Owned o, Player p) {
        return o instanceof BlockEntity be && be.getLevel() != null && !be.getLevel().isClientSide && p.getUUID().equals(hacker(be.getLevel(), be.getBlockPos()));
    }

    static boolean weapon(Owned o) {
        return o instanceof LaunchTerminalBlockEntity || o instanceof InterceptorLauncherBlockEntity || o instanceof InterceptorBatteryBlockEntity
                || o instanceof InterceptorSiloBlockEntity || o instanceof InterceptorSiloT4BlockEntity || o instanceof CiwsBlockEntity
                || o instanceof ArtilleryGunBlockEntity || o instanceof RailgunBlockEntity || o instanceof CruiseLauncherBlockEntity
                || o instanceof SwarmLauncherBlockEntity || o instanceof DroneHiveBlockEntity || o instanceof DroneCatapultBlockEntity
                || o instanceof BombRackBlockEntity || o instanceof BombSightBlockEntity || o instanceof LaserDefenceBlockEntity
                || o instanceof LaserTripodBlockEntity || o instanceof SentryTurretBlockEntity || o instanceof DeadHandBlockEntity
                || o instanceof CcLaunchConsoleBlockEntity || o instanceof CcStrikePlannerBlockEntity || o instanceof CcRetargetConsoleBlockEntity
                || o instanceof CcArtilleryConsoleBlockEntity || o instanceof CcIronDomeBlockEntity || o instanceof CcInterceptorConsoleBlockEntity
                || o instanceof CcAirDefenceBlockEntity

                || o instanceof MineBlockEntity || o instanceof C4ChargeBlockEntity || o instanceof PortableJammerBlockEntity;
    }

    static boolean intel(Owned o, Player p) {
        if (!weapon(o) || !hackedBy(o, p)) return false;
        BlockEntity be = (BlockEntity) o;
        p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.wmd.hack.intel", be.getBlockState().getBlock().getName()).withStyle(net.minecraft.ChatFormatting.GOLD));
        if (be instanceof GuiMachine gm) for (net.minecraft.network.chat.Component c : gm.guiInfo()) p.sendSystemMessage(c.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
        if (be instanceof ProcessUnitBlockEntity pu) p.sendSystemMessage(pu.status().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
        return true;
    }

    static synchronized boolean clear(Level level, BlockPos pos) {
        Map<Long, Hack> m = HACKS.get(dim(level));
        return m != null && m.remove(pos.asLong()) != null;
    }

    public static synchronized void clearStatics() { HACKS.clear(); }
}
