package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Boarding {

    private Boarding() {}

    public static final int TOP = 4;
    public static final int NO_FREE_PORT = 27;

    private static final Map<Long, long[]> CUTTING = new HashMap<>();

    static void clearStatics() { CUTTING.clear(); }

    static int pick(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return -1;
        Integer p = Station.Data.get(server).boardPick.get(Orbit.slotOf(server, owner));
        return p == null ? -1 : p;
    }

    static Component setPick(ServerPlayer p, int slot, int target) {
        MinecraftServer server = p.server;
        Station.Data d = Station.Data.get(server);
        if (target < 0) { d.boardPick.remove(slot); d.setDirty(); return Component.translatable("message.wmd.board.cleared"); }
        if (target == slot || Station.core(server, target) == null || !Rendezvous.sees(server, slot, target)) return Component.translatable("message.wmd.laser.bad_target");
        d.boardPick.put(slot, target);
        d.setDirty();
        return Component.translatable("message.wmd.board.picked", ownerName(server, target), target);
    }

    static boolean sees(MinecraftServer server, UUID owner) {
        return SensorArray.working(server, Orbit.slotOf(server, owner)) || Recon.covered(server, owner);
    }

    static String ownerName(MinecraftServer server, int slot) {
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(slot);
        return o == null ? "?" : CcIntel.name(server, o);
    }

    static int status(MinecraftServer server, UUID owner) {
        int target = pick(server, owner);
        if (target < 0) return 0;
        if (!sees(server, owner) && !Rendezvous.close(server, Orbit.slotOf(server, owner), target)) return Asat.STATION_UNSEEN;
        return port(server.getLevel(Orbit.ORBIT), target) < 0 ? NO_FREE_PORT : 0;
    }

    static int launched(ServerLevel level, long flightId, UUID owner, String ownerName) {
        MinecraftServer server = level.getServer();
        int target = pick(server, owner);
        if (target < 0) return -1;
        Station.Data d = Station.Data.get(server);
        d.boardPick.remove(Orbit.slotOf(server, owner));
        d.setDirty();
        Flight f = FlightManager.get(level).find(flightId);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (f != null && orbit != null)
            SensorArray.incoming(server, target, "boarding", ownerName, orbit.getGameTime() + Math.max(0L, Satellites.insertTick(f) - level.getGameTime()));
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) p.sendSystemMessage(Component.translatable("message.wmd.board.away", ownerName(server, target)).withStyle(ChatFormatting.GOLD));
        Wmd.LOGGER.info("[board] crew capsule flight {} of {} going to board slot {}", flightId, ownerName, target);
        return target;
    }

    static BlockPos spot(MinecraftServer server, int slot, int k) {
        Station.Module core = Station.core(server, slot);
        if (core == null) return null;
        return k == TOP ? Station.world(core, 0, 9, 0) : Cargo.podController(server, slot, k);
    }

    static boolean side(int k) { return (k >= 0 && k < 4) || k == Station.DOCK; }

    private static boolean free(ServerLevel orbit, int slot, Station.Module core, int k) {
        BlockPos c = spot(orbit.getServer(), slot, k);
        if (c == null) return false;
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) if (!orbit.getBlockState(c.offset(x, y, z)).isAir()) return false;
        if (k == TOP) return true;
        if (k == Station.DOCK) return Station.spotState(orbit.getServer(), slot, Station.DOCK) == 0;
        for (Station.Module o : Station.modules(orbit.getServer(), slot)) if (o.host() == core.id() && o.port() == k) return false;
        return true;
    }

    static int port(ServerLevel orbit, int slot) {
        if (orbit == null) return -1;
        Station.Module core = Station.core(orbit.getServer(), slot);
        if (core == null || !core.built()) return -1;
        if (free(orbit, slot, core, TOP)) return TOP;
        if (free(orbit, slot, core, Station.DOCK)) return Station.DOCK;
        for (int k = 0; k < 4; k++) if (free(orbit, slot, core, k)) return k;
        return -1;
    }

    static BlockPos dock(ServerLevel orbit, int slot, UUID owner, ResourceKey<Level> padDim, BlockPos pad) {
        int k = port(orbit, slot);
        if (k < 0) return null;
        Station.Module core = Station.core(orbit.getServer(), slot);
        BlockPos at = spot(orbit.getServer(), slot, k);
        BlockState st = Wmd.CREW_CAPSULE_BLOCK.get().defaultBlockState();
        if (k != TOP) st = st.setValue(MachineBlock.FACING, Station.spotDir(k));
        orbit.setBlockAndUpdate(at, st);
        st.getBlock().setPlacedBy(orbit, at, st, null, ItemStack.EMPTY);
        long now = orbit.getGameTime();
        if (orbit.getBlockEntity(at) instanceof CrewCapsuleBlockEntity be) { be.setUp(owner, padDim, pad); be.setBoarding(slot, k, now); }
        if (k == TOP && orbit.getBlockEntity(Station.controller(core)) instanceof StationModuleBlockEntity sb) sb.setDock(now);
        orbit.playSound(null, at, WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, 3.0F, 0.8F);
        CUTTING.put(at.asLong(), new long[] { slot, k, now });
        String who = owner == null ? "?" : CcIntel.name(orbit.getServer(), owner);
        tell(orbit, slot, Component.translatable("message.wmd.board.alarm", portName(k), who).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        UUID tOwner = Orbit.OrbitSlots.get(orbit.getServer()).ownerOf(slot);
        if (tOwner != null) CcBattleLog.add(orbit.getServer(), tOwner, CcBattleLog.ALARM, "log.wmd.board.alarm", who, Integer.toString(k));
        Wmd.LOGGER.info("[board] capsule of {} docked on port {} of slot {}: cutting in", who, k, slot);
        return at;
    }

    static Component portName(int k) { return k == TOP ? Component.translatable("gui.wmd.board.top") : k == Station.DOCK ? Component.translatable("gui.wmd.board.dock") : Component.literal(Integer.toString(k)); }

    static void resume(BlockPos at, int slot, int port, long start) { CUTTING.put(at.asLong(), new long[] { slot, port, start }); }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (CUTTING.isEmpty() || !(event.getLevel() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit)) return;
        long now = orbit.getGameTime();
        for (Iterator<Map.Entry<Long, long[]>> it = CUTTING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, long[]> e = it.next();
            BlockPos at = BlockPos.of(e.getKey());
            if (!orbit.isLoaded(at)) continue;
            if (!(orbit.getBlockEntity(at) instanceof CrewCapsuleBlockEntity be) || be.boardSlot() < 0) { it.remove(); continue; }
            int slot = (int) e.getValue()[0], k = (int) e.getValue()[1];
            long start = e.getValue()[2], left = start + Balance.BOARD_CUT_TICKS - now;
            BlockPos port = portCell(orbit, slot, k);
            if (left <= 0) {
                it.remove();
                tell(orbit, slot, Component.translatable("message.wmd.board.through", portName(k)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                for (ServerPlayer p : Crew.seated(orbit, at)) p.sendSystemMessage(Component.translatable("message.wmd.board.go").withStyle(ChatFormatting.GOLD));
                orbit.playSound(null, at, WmdSounds.ST_AIRLOCK.get(), SoundSource.BLOCKS, 3.0F, 0.6F);
                continue;
            }
            if ((now - start) % 60 == 0) orbit.playSound(null, at, WmdSounds.BD_CUTTER_LOOP.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
            if (now % 4 == 0 && port != null)
                orbit.sendParticles(ParticleTypes.ELECTRIC_SPARK, port.getX() + 0.5, port.getY() + 0.5, port.getZ() + 0.5, 10, 0.8, 0.8, 0.8, 0.25);
            if (now % 40 == 0) {
                orbit.playSound(null, at, WmdSounds.BD_ALARM.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                Component bar = Component.translatable("message.wmd.board.cutting", portName(k), left / 20).withStyle(ChatFormatting.RED);
                for (ServerPlayer p : orbit.players()) if (Orbit.slotAt(p.getX(), p.getZ()) == slot) p.displayClientMessage(bar, true);
            }
        }
    }

    private static BlockPos portCell(ServerLevel orbit, int slot, int k) {
        Station.Module core = Station.core(orbit.getServer(), slot);
        if (core == null) return null;
        if (k == TOP) return Station.world(core, 0, 8, 0);
        if (k == Station.DOCK) { Station.Module hab = Station.habitatOn(orbit.getServer(), slot, 0); return hab == null ? null : Station.world(hab, Station.SIDE_X[3], 4, -4); }
        Direction d = Station.PORT_DIR[k];
        return Station.world(core, 0, 2, 0).relative(d, 4);
    }

    static boolean cutThrough(ServerLevel orbit, int slot, int k) {
        BlockPos at = spot(orbit.getServer(), slot, k);
        return at != null && orbit.getBlockEntity(at) instanceof CrewCapsuleBlockEntity be && be.boardSlot() >= 0 && be.cutDone(orbit.getGameTime());
    }

    static BlockPos entry(ServerLevel orbit, BlockPos capsule) {
        if (!(orbit.getBlockEntity(capsule) instanceof CrewCapsuleBlockEntity be) || be.boardSlot() < 0) return null;
        if (!be.cutDone(orbit.getGameTime())) return null;
        Station.Module core = Station.core(orbit.getServer(), be.boardSlot());
        if (core == null) return null;
        int k = be.boardPort();
        if (k == TOP) return Station.world(core, 0, 1, 0);
        if (k == Station.DOCK) { Station.Module hab = Station.habitatOn(orbit.getServer(), be.boardSlot(), 0); return hab == null ? null : Station.world(hab, Station.SIDE_X[3], 1, -2); }
        Direction d = Station.PORT_DIR[k];
        return Station.world(core, 0, 1, 0).relative(d, 3);
    }

    private static void tell(ServerLevel orbit, int slot, Component msg) {
        UUID o = Orbit.OrbitSlots.get(orbit.getServer()).ownerOf(slot);
        ServerPlayer op = o == null ? null : orbit.getServer().getPlayerList().getPlayer(o);
        if (op != null) op.sendSystemMessage(msg);
        for (ServerPlayer p : orbit.players()) if (p != op && Orbit.slotAt(p.getX(), p.getZ()) == slot) p.sendSystemMessage(msg);
    }
}
