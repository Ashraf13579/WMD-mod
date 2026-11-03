package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcMissileCamBlockEntity extends CcConsoleBlockEntity {

    public static final int MAX_ROWS = 6;

    public static final long DRONE_KEY = Long.MIN_VALUE;

    public static boolean isDroneKey(long k) { return k >= DRONE_KEY && k < DRONE_KEY + Integer.MAX_VALUE; }

    public CcMissileCamBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_MISSILE_CAM_BE.get(), pos, state, Balance.CC_CAM_FE); }

    @Override public CcModule kind() { return CcModule.MISSILE_CAM; }

    static List<Flight> camFlights(ServerLevel level, UUID owner, String ownerName) {
        List<Flight> out = new ArrayList<>();
        long now = level.getGameTime();
        for (Flight f : FlightManager.get(level).flights()) {
            if (!f.build().hasCamera() || f.build().isInterceptor() || now < f.start() || now >= f.endTime()) continue;
            if (f.isOwnedBy(owner, ownerName)) out.add(f);
        }
        out.sort((a, b) -> Long.compare(a.id(), b.id()));
        return out;
    }

    private static final java.util.Map<UUID, long[]> ARMED = Statics.server(new java.util.HashMap<>());
    private static final int ARM_TICKS = 1200;

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = new ListTag();
        armedCheck(level, owner);

        for (LaunchTerminalBlockEntity t : CcLaunch.terminalsOf(owner)) {
            if (out.size() >= MAX_ROWS || t.getLevel() != level || t.isRemoved()) continue;
            MissileBuild b = t.getLoadedBuild();
            LaunchTerminalBlockEntity.Stage st = t.getStage();
            if (b == null || !b.hasCamera() || b.isInterceptor() || !(st == LaunchTerminalBlockEntity.Stage.READY || st == LaunchTerminalBlockEntity.Stage.COUNTDOWN
                    || st == LaunchTerminalBlockEntity.Stage.LAUNCHING)) continue;
            CompoundTag c = new CompoundTag();
            c.putLong("p", t.getBlockPos().asLong());
            c.putInt("t", b.calibre());
            c.putString("w", b.warheadType());
            c.putInt("tx", t.getTargetX());
            c.putInt("tz", t.getTargetZ());
            c.putString("st", st.name().toLowerCase());
            out.add(c);
        }
        List<Flight> l = camFlights(level, owner, getOwnerName());
        for (int i = 0; i < l.size() && out.size() < MAX_ROWS; i++) {
            Flight f = l.get(i);
            CompoundTag c = new CompoundTag();
            c.putLong("id", f.id());
            c.putInt("t", f.tier());
            c.putString("w", f.build().warheadType());
            c.putInt("tx", f.tx());
            c.putInt("tz", f.tz());
            c.putLong("end", f.endTime());
            out.add(c);
        }
        if (level == level.getServer().overworld())
            for (Drones.Drone d : Drones.get(level.getServer()).drones()) {
                if (out.size() >= MAX_ROWS) break;
                if (d.kind() < 3 || owner == null || !owner.equals(d.owner()) || !d.airborne()) continue;
                int full = d.kind() == 3 ? Balance.DRONE_T3_BATTERY : Balance.DRONE_T4_BATTERY;
                CompoundTag c = new CompoundTag();
                c.putLong("id", DRONE_KEY + d.id());
                c.putInt("dr", d.id());
                c.putInt("t", d.kind());
                c.putInt("tx", d.tx);
                c.putInt("tz", d.tz);
                c.putInt("bat", Math.max(0, Math.min(100, d.battery * 100 / full)));
                out.add(c);
            }
        return out;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        if (p.action() != CcBPayload.CAM) return null;
        ServerLevel sl = (ServerLevel) level;
        if (isDroneKey(p.arg())) {
            Drones.Drone d = Drones.get(sl.getServer()).byId((int) (p.arg() - DRONE_KEY));
            if (d == null || getOwner() == null || !getOwner().equals(d.owner())) return Component.translatable("message.wmd.big_drone.not_flying");
            Component why = BigDrones.watch(player, d.id());
            if (why != null) return why;
            player.closeContainer();
            return null;
        }
        if (FlightManager.get(sl).find(p.arg()) == null) {
            LaunchTerminalBlockEntity t = CcLaunch.terminalAt(sl, BlockPos.of(p.arg()), getOwner());
            if (t != null && t.getLoadedBuild() != null && t.getLoadedBuild().hasCamera()) {
                ARMED.put(player.getUUID(), new long[] { p.arg(), sl.getGameTime() });

                net.minecraft.world.phys.Vec3 base = t.padCentreWorld();
                double nose = base.y + Missiles.lengthOf(t.getLoadedBuild()) + 0.6;
                float yaw = (float) Math.toDegrees(Math.atan2(-(t.getTargetX() + 0.5 - base.x), t.getTargetZ() + 0.5 - base.z));
                PacketDistributor.sendToPlayer(player, new CcCamPadPayload(base.x, nose, base.z, yaw, t.getTargetX(), t.getTargetZ(), t.getBlockPos().asLong()));
                return Component.translatable("message.wmd.cc.cam.armed");
            }
        }
        return watch(player, sl, getOwner(), getOwnerName(), p.arg());
    }

    private void armedCheck(ServerLevel level, UUID owner) {
        if (ARMED.isEmpty()) return;
        long now = level.getGameTime();
        ARMED.entrySet().removeIf(e -> now - e.getValue()[1] > ARM_TICKS);
        for (java.util.Iterator<java.util.Map.Entry<UUID, long[]>> it = ARMED.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<UUID, long[]> e = it.next();
            ServerPlayer pl = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (pl == null) { it.remove(); continue; }
            BlockPos pad = BlockPos.of(e.getValue()[0]);
            for (Flight f : camFlights(level, owner, getOwnerName())) {
                if (f.start() < e.getValue()[1]) continue;
                net.minecraft.world.phys.Vec3 pw = SableBridge.worldPos(level, pad);
                double dx = f.ox() - pw.x, dz = f.oz() - pw.z;
                if (dx * dx + dz * dz > 24 * 24) continue;
                PacketDistributor.sendToPlayer(pl, new CcCamPayload(f.id()));
                camStarted(level, owner, f);
                pl.displayClientMessage(Component.translatable("message.wmd.cc.cam.watching", f.id()), true);
                it.remove();
                break;
            }
        }
    }

    static Component watch(ServerPlayer player, ServerLevel level, UUID owner, String ownerName, long id) {
        Flight f = FlightManager.get(level).find(id);
        long now = level.getGameTime();
        if (f == null || !f.isOwnedBy(owner, ownerName) || now >= f.endTime()) return Component.translatable("message.wmd.cc.cam.no_flight", id);
        if (!f.build().hasCamera()) return Component.translatable("message.wmd.cc.cam.no_pod", id);
        PacketDistributor.sendToPlayer(player, new CcCamPayload(f.id()));
        camStarted(level, owner, f);
        return Component.translatable("message.wmd.cc.cam.watching", f.id());
    }

    public static final int LIVE_LINGER_TICKS = 60;

    static void camStarted(ServerLevel level, UUID owner, Flight f) {
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner))
            if (b.getLevel() == level) b.setLiveCam(f.id(), f.endTime() + LIVE_LINGER_TICKS);
    }
}
