package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class RailgunStrikes {

    private RailgunStrikes() {}

    private record Strike(ServerLevel level, int x, int z, long due, String owner, double mx, double my, double mz, java.util.UUID ownerId) {}

    private static final List<Strike> PENDING = new ArrayList<>();

    static void schedule(ServerLevel level, int x, int z, long due, String owner, java.util.UUID ownerId, double[] muzzle) { PENDING.add(new Strike(level, x, z, due, owner, muzzle[0], muzzle[1], muzzle[2], ownerId)); }

    private record Replay(net.minecraft.server.level.ServerPlayer player, long key, int x, int z, double mx, double my, double mz, long since) {}

    private static final List<Replay> REPLAYS = new ArrayList<>();

    static void clearStatics() { PENDING.clear(); REPLAYS.clear(); }

    static void replay(net.minecraft.server.level.ServerPlayer p, int x, int z) {
        ServerLevel sl = p.serverLevel();
        long key = p.getUUID().getMostSignificantBits() ^ 0x5245504C4159L;
        RailgunBlockEntity gun = RailgunBlockEntity.nearest(sl, p.position(), 160.0);
        double[] m = gun != null ? gun.muzzle() : new double[] { p.getX(), p.getEyeY() + 12.0, p.getZ() };
        REPLAYS.removeIf(r -> r.key() == key);
        TargetSnapshot.start(sl, key, x, z, p);
        REPLAYS.add(new Replay(p, key, x, z, m[0], m[1], m[2], sl.getGameTime()));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        for (Iterator<Replay> it = REPLAYS.iterator(); it.hasNext(); ) {
            Replay r = it.next();
            if (r.player().serverLevel() != sl) continue;
            if (r.player().hasDisconnected()) { it.remove(); continue; }
            if (!TargetSnapshot.ready(r.key()) && sl.getGameTime() - r.since() < 600) continue;
            it.remove();
            var ch = AsyncChunks.now(sl, r.x() >> 4, r.z() >> 4);
            int ty = ch != null ? ch.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, r.x() & 15, r.z() & 15) + 1 : 64;
            int lead = Balance.RG_CUTSCENE_LEAD;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(r.player(), new CutscenePayload(CutscenePayload.KIND_RAILGUN_REPLAY, (float) r.mx(), (float) r.my(), (float) r.mz(),
                    r.x() + 0.5F, ty, r.z() + 0.5F, lead, Balance.RG_CUTSCENE_TICKS, r.player().getGameProfile().getName()));
            TargetSnapshot.stopIn(r.key(), sl, Balance.RG_CUTSCENE_TICKS + 200);
        }
        if (PENDING.isEmpty()) return;
        for (Iterator<Strike> it = PENDING.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            if (s.level() != sl || sl.getGameTime() < s.due()) continue;
            FlightManager mgr0 = FlightManager.get(sl);
            if (!WarheadFx.impactReady(sl, mgr0, s.x(), s.z()) && sl.getGameTime() < s.due() + WarheadFx.IMPACT_WAIT_TICKS) continue;
            it.remove();
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x(), s.z());
            Wmd.LOGGER.info("[railgun] {}'s slug lands at {} {} {}", s.owner(), s.x(), y, s.z());
            FlightManager mgr = FlightManager.get(sl);
            mgr.holdForTest(sl, s.x(), s.z(), WarheadFx.holdRadius(4, WarheadType.RAILGUN));
            WarheadFx.detonate(sl, mgr, s.x(), y, s.z(), 4, WarheadType.RAILGUN, 0.0, 1.0);
            double rr = Missiles.craterRadius(4) * WarheadType.RAILGUN.crater;
            StrikeLog.report(sl, s.ownerId(), "log.wmd.strike.railgun", s.x(), s.z(), rr, rr * WarheadType.RAILGUN.depth + Balance.RG_SHAFT_DEPTH);
            MusicTriggersHook.fire(sl, "wmd:railgun_impact", new net.minecraft.core.BlockPos(s.x(), y, s.z()));
            RailgunWreck.schedule(sl, s.x(), s.z(), RailgunArc.dir(new net.minecraft.world.phys.Vec3(s.mx(), s.my(), s.mz()), new net.minecraft.world.phys.Vec3(s.x() + 0.5, y, s.z() + 0.5), 1.0));
        }
    }
}
