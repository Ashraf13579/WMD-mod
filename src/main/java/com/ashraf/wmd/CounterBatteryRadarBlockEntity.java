package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CounterBatteryRadarBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 6, Z0 = 0, Z1 = 4;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 4, Direction.SOUTH, false);
    private static final int SCAN_TICKS = 10, TRACK_TICKS = 200;

    private final OwnerData ownerData = new OwnerData();

    private ListTag traces = new ListTag();

    private float trackAz;
    private long trackUntil;

    private int defcon = CcDefcon.PEACE;

    private final Set<Long> traced = new HashSet<>();

    private float yaw, prevYaw, tilt = Float.NaN, prevTilt;

    private static final Map<UUID, Map<Long, Long>> LAST = new HashMap<>();

    static void clearStatic() { LAST.clear(); }

    public CounterBatteryRadarBlockEntity(BlockPos pos, BlockState state) { super(Wmd.COUNTER_BATTERY_RADAR_BE.get(), pos, state, Balance.CBR_FE_PER_TICK); }

    @Override public UUID getOwner() { return ownerData.id(); }

    @Override
    public void onLoad() {
        super.onLoad();
        RadarEmit.note(this);
    }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public List<CompoundTag> traces() {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : traces) if (t instanceof CompoundTag c) l.add(c);
        return l;
    }

    public int defcon() { return defcon; }

    public boolean tracking(long now) { return now < trackUntil; }

    @Override
    protected boolean work() {
        if (level instanceof ServerLevel sl) {
            long now = sl.getGameTime();
            if ((now + (worldPosition.asLong() & 7L)) % SCAN_TICKS == 0) scan(sl, now);
            if ((now + (worldPosition.asLong() & 15L)) % 20 == 5) {
                int d = getOwner() == null ? CcDefcon.PEACE : CcDefcon.level(getOwner());
                if (d != defcon) { defcon = d; setChanged(); sync(); }
            }
        }
        return true;
    }

    private boolean hostile(ArtShell s) {
        UUID o = getOwner();
        if (o == null || o.equals(s.owner())) return false;
        return true;
    }

    private void scan(ServerLevel sl, long now) {
        List<ArtShell> all = ArtilleryManager.get(sl).shells();
        if (all.isEmpty()) { traced.clear(); return; }
        Set<Long> live = new HashSet<>();
        Vec3 me = SableBridge.worldPos(sl, worldPosition);
        double cx = me.x, cz = me.z, r2 = (double) Balance.CBR_RANGE * Balance.CBR_RANGE;
        for (ArtShell s : all) {
            live.add(s.id());
            if (traced.contains(s.id()) || now - s.start() < Balance.CBR_TRACE_TICKS || !hostile(s)) continue;
            Vec3 p = s.position(now);
            double dx = p.x - cx, dz = p.z - cz;
            if (dx * dx + dz * dz > r2) continue;
            traced.add(s.id());
            trace(sl, s, now);
        }
        traced.retainAll(live);
    }

    private void trace(ServerLevel sl, ArtShell s, long now) {
        UUID owner = getOwner();
        BlockPos gun = s.gunPos() != 0L ? BlockPos.of(s.gunPos()) : BlockPos.containing(s.ox(), s.oy(), s.oz());
        double a = sl.random.nextDouble() * Math.PI * 2.0, d = Balance.CBR_ERROR * Math.sqrt(sl.random.nextDouble());
        Vec3 gw = SableBridge.worldPos(sl, gun);
        int ex = (int) Math.round(gw.x + Math.cos(a) * d), ez = (int) Math.round(gw.z + Math.sin(a) * d);

        Vec3 me = SableBridge.worldPos(sl, worldPosition);
        double bx = ex + 0.5 - me.x, bz = ez + 0.5 - me.z;
        trackAz = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(bx, -bz)));
        trackUntil = now + TRACK_TICKS;

        Map<Long, Long> mine = LAST.computeIfAbsent(owner, k -> new HashMap<>());
        Long last = mine.get(gun.asLong());
        boolean fresh = last == null || now - last >= Balance.CBR_RETRACE_TICKS || now < last;
        if (!fresh) {
            for (int i = traces.size() - 1; i >= 0; i--) {
                CompoundTag c = traces.getCompound(i);
                if (c.getLong("gp") == gun.asLong()) { c.putInt("n", c.getInt("n") + 1); break; }
            }
            setChanged();
            sync();
            return;
        }
        mine.put(gun.asLong(), now);
        String gunKey = "#" + Artillery.gunNameKey(s.gun());
        String by = s.ownerName().isEmpty() ? "?" : s.ownerName();
        CompoundTag c = new CompoundTag();
        c.putInt("x", ex);
        c.putInt("z", ez);
        c.putInt("err", Balance.CBR_ERROR);
        c.putInt("g", s.gun());
        c.putString("by", by);
        c.putLong("ms", System.currentTimeMillis());
        c.putInt("n", 1);
        c.putLong("gp", gun.asLong());
        traces.add(c);
        while (traces.size() > Balance.CBR_KEEP) traces.remove(0);
        setChanged();
        sync();
        MinecraftServer server = sl.getServer();
        if (owner == null || server == null) return;
        Wmd.LOGGER.info("Counter-battery radar of {} at {}: traced a {} of {} to {} {} (real {})", getOwnerName(), worldPosition.toShortString(),
                gunKey.substring(1), by, ex, ez, gun.toShortString());

        long wid = Long.MIN_VALUE / 2 + (gun.asLong() & 0xFFFFFFFFFFL);
        CommandCenter.addWarning(server, owner, new CommandCenter.Warning(wid, ex, ez, (int) Math.round(s.tx()), (int) Math.round(s.tz()), s.start(), s.endTime(), false));

        CcTarget t = new CcTarget(ex, ez, "gui.wmd.cc.des.enemy_gun", CcTarget.SRC_COUNTER, "counter-battery radar", System.currentTimeMillis());
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) b.designate(t);
        CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_IN, "log.wmd.cbr_trace", gunKey, by, Integer.toString(ex), Integer.toString(ez), Integer.toString(Balance.CBR_ERROR));
        if (CcDefcon.level(owner) <= 1) autoFire(sl, owner, ex, ez);
    }

    private void autoFire(ServerLevel sl, UUID owner, int ex, int ez) {
        int n = 0;
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(owner)) {
            if (g.getLevel() != sl || g.mission() != 0) continue;
            BlockPos gp = g.getBlockPos();
            if (!CcReach.reach(sl, owner, gp)) continue;
            if (g.checkFor(ex, ez, Artillery.HE) != ArtilleryGunBlockEntity.S_READY) continue;
            int st = g.order(ex, ez, Artillery.HE, Balance.CBR_AUTO_ROUNDS, "Counter-battery");
            if (st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING || st == ArtilleryGunBlockEntity.S_FIRING) n++;
        }
        MinecraftServer server = sl.getServer();
        if (n == 0 || server == null) return;
        Wmd.LOGGER.info("Counter-battery radar of {}: AUTO counter-fire with {} gun(s) at {} {}", getOwnerName(), n, ex, ez);
        CcBattleLog.add(server, owner, CcBattleLog.COUNTER, "log.wmd.cbr_auto", Integer.toString(n), Integer.toString(ex), Integer.toString(ez));
        CcDefcon.banner(server, owner, CcDefcon.B_COUNTER, true, "hud.wmd.cc.alert.cbr_auto", Integer.toString(n), Integer.toString(ex), Integer.toString(ez));
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? energy() : null; }

    public static void clientTickRadar(Level level, BlockPos pos, BlockState state, CounterBatteryRadarBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.tickHead(level.getGameTime());
    }

    private void tickHead(long now) {
        prevYaw = yaw;
        prevTilt = tilt;
        float facingYaw = (getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F;
        float tYaw, tTilt;
        if (!isRunning()) { tYaw = yaw; tTilt = 0.0F; }
        else if (now < trackUntil) {
            float ta = trackAz;
            double[] s = ShipFrame.compassAim(level, worldPosition, trackAz, 0.0, Float.NaN);
            if (s != null) ta = (float) s[0];
            tYaw = Mth.wrapDegrees(ta - facingYaw); tTilt = CcSpecS52.CBR_TRACK_TILT_DEG;
        }
        else {
            tYaw = yaw + 360.0F / CcSpecS52.CBR_SCAN_TICKS;
            double ph = (now % 200L) / 200.0 * Math.PI * 2.0;
            tTilt = CcSpecS52.CBR_SCAN_TILT_MIN + (CcSpecS52.CBR_SCAN_TILT_MAX - CcSpecS52.CBR_SCAN_TILT_MIN) * (float) (0.5 + 0.5 * Math.sin(ph));
        }
        if (Float.isNaN(tilt)) { tilt = tTilt; prevTilt = tilt; }
        yaw += Mth.clamp(Mth.wrapDegrees(tYaw - yaw), -CcSpecS52.CBR_SLEW_DEG, CcSpecS52.CBR_SLEW_DEG);
        tilt += Mth.clamp(tTilt - tilt, -0.6F, 0.6F);
        tilt = Mth.clamp(tilt, 0.0F, CcSpecS52.CBR_TILT_MAX_DEG);
        if (yaw >= 360.0F) { yaw -= 360.0F; prevYaw -= 360.0F; }
        if (yaw < 0.0F) { yaw += 360.0F; prevYaw += 360.0F; }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float y = prevYaw + Mth.wrapDegrees(yaw - prevYaw) * pt;
        float t = Float.isNaN(tilt) ? 0.0F : prevTilt + (tilt - prevTilt) * pt;
        final float ry = CcSpecS52.CBR_YAW_SIGN * (float) Math.toRadians(y);
        final float rx = CcSpecS52.CBR_TILT_SIGN * (float) Math.toRadians(t);
        model.getBone("yaw").ifPresent(b -> b.setRotY(ry));
        model.getBone("array").ifPresent(b -> b.setRotX(rx));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.CBR_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.cbr", String.format("%,d", Balance.CBR_RANGE)));
    }

    @Override
    protected String detail() {
        return (isRunning() ? "scanning" : "no power") + ", " + traces.size() + " trace(s), range " + Balance.CBR_RANGE;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CBR_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }
    @Override protected float soundVolume() { return 0.2F; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.put("cbrTraces", traces);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("cbrTraces")) traces = tag.getList("cbrTraces", Tag.TAG_COMPOUND);
        trackAz = tag.getFloat("cbrAz");
        trackUntil = tag.getLong("cbrUntil");
        defcon = tag.contains("cbrDefcon") ? CcDefcon.clamp(tag.getInt("cbrDefcon")) : CcDefcon.PEACE;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        ListTag clean = new ListTag();
        for (int i = 0; i < traces.size(); i++) {
            CompoundTag c = traces.getCompound(i).copy();
            c.remove("gp");
            clean.add(c);
        }
        tag.put("cbrTraces", clean);
        tag.putFloat("cbrAz", trackAz);
        tag.putLong("cbrUntil", trackUntil);
        tag.putInt("cbrDefcon", defcon);
        return tag;
    }
}
