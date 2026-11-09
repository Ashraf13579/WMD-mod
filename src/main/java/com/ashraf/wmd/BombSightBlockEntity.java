package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class BombSightBlockEntity extends ProcessUnitBlockEntity implements Owned, GuiAction, CcAnimated {

    public static final int OFF = 0, CCIP = 1, CCRP = 2, A_OFF = 0, A_CCIP = 1, A_CCRP = 2, A_SRC = 3;
    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.EAST, false);

    static final Map<BlockPos, BombSightBlockEntity> CLIENT = Statics.client(new ConcurrentHashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private int mode = OFF;
    private boolean laserTarget, targetSet;
    private int targetX, targetZ;

    private boolean solved, aimed, locked;

    public static final int K_NEXT = 0, K_DROP = 1, K_SALVO = 2;
    private int station;
    private long[] stPos = new long[0];
    private int[] stSlot = new int[0], stKind = new int[0], stCount = new int[0];
    private boolean[] stBay = new boolean[0];
    private double ix, iy, iz, hx, hy, hz, tx, tz, along = Double.NaN, cross, height, speed, track;
    private int pulse;

    private List<BlockPos> racks = List.of();
    private long racksAt = -10_000L;
    private Vec3 lastWorld, vel = Vec3.ZERO;

    public BombSightBlockEntity(BlockPos pos, BlockState state) { super(Wmd.BOMB_SIGHT_BE.get(), pos, state, Balance.SIGHT_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public int mode() { return mode; }
    public boolean laserTarget() { return laserTarget; }
    public boolean targetSet() { return targetSet; }
    public int targetX() { return targetX; }
    public int targetZ() { return targetZ; }
    public boolean solved() { return solved; }
    public boolean aimed() { return aimed; }
    public Vec3 impact() { return new Vec3(ix, iy, iz); }
    public Vec3 dropPoint() { return new Vec3(hx, hy, hz); }
    public Vec3 target() { return new Vec3(tx, iy, tz); }
    public double along() { return along; }
    public double cross() { return cross; }
    public double height() { return height; }

    public double speed() { return speed; }

    public double track() { return track; }

    public double releaseSeconds() { return !aimed || Double.isNaN(along) || speed < 0.5 ? Double.NaN : along / speed; }
    public boolean pulsing() { return pulse > 0; }

    public Direction backFace() { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.SOUTH); }

    public Vec3 worldCentre() {
        double[] p = worldPoint(0.0, 0.5, 0.0);
        return new Vec3(p[0], p[1], p[2]);
    }

    void setTarget(boolean set, int x, int z) { targetSet = set; targetX = x; targetZ = z; setChanged(); sync(); }

    @Override
    public boolean guiAction(Player player, int id) {
        if (!(level instanceof ServerLevel)) return false;
        if (!Access.mayUse(this, player)) { Access.deny(this, player); return false; }
        switch (id) {
            case A_OFF -> mode = OFF;
            case A_CCIP -> mode = CCIP;
            case A_CCRP -> mode = CCRP;
            case A_SRC -> laserTarget = !laserTarget;
            default -> { return false; }
        }
        if (mode == OFF) { solved = false; aimed = false; }
        setChanged();
        sync();
        return true;
    }

    public static void serverTickSight(Level level, BlockPos pos, BlockState state, BombSightBlockEntity be) {
        if (level instanceof ServerLevel sl) be.sightTick(sl);
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
    }

    private void sightTick(ServerLevel sl) {
        long now = sl.getGameTime();
        if (pulse > 0 && --pulse == 0) emit(sl);
        Vec3 w = worldCentre();
        vel = lastWorld == null ? Vec3.ZERO : w.subtract(lastWorld);
        if (vel.lengthSqr() > 4.0) vel = Vec3.ZERO;
        lastWorld = w;
        if (mode == OFF || !hasPower()) { if (solved) { solved = false; aimed = false; sync(); } return; }
        if (now % Balance.SIGHT_STEP != 0) return;
        if (now - racksAt >= 100) { racks = findRacks(sl); racksAt = now; }

        Vec3 drop = null;
        for (BlockPos rp : racks) {
            if (sl.getBlockEntity(rp) instanceof BombRackBlockEntity r && r.total() > 0 && Machines.mount(r.getBlockState()) != Direction.UP) {
                drop = r.hangWorld();
                if (drop != null) break;
            }
        }
        stations(sl);
        if (station >= 0 && station < stPos.length && stCount[station] > 0 && sl.getBlockEntity(BlockPos.of(stPos[station])) instanceof BombRackBlockEntity sr
                && Machines.mount(sr.getBlockState()) != Direction.UP) {
            Vec3 sd = sr.hangWorld(stSlot[station]);
            if (sd != null) drop = sd;
        }
        if (drop == null) drop = w.add(0.0, -1.0, 0.0);
        hx = drop.x; hy = drop.y; hz = drop.z;

        Vec3 p = drop, v = vel.add(0.0, -0.15, 0.0);
        solved = false;
        for (int i = 0; i < Balance.SIGHT_SIM_TICKS; i++) {
            v = new Vec3(v.x * Balance.BOMB_DRAG, v.y * Balance.BOMB_DRAG - Balance.BOMB_G, v.z * Balance.BOMB_DRAG);
            p = p.add(v);
            BlockPos col = BlockPos.containing(p.x, sl.getMinBuildHeight() + 1, p.z);
            if (!sl.hasChunkAt(col)) break;
            double ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            if (p.y <= ground) { ix = p.x; iy = ground; iz = p.z; solved = true; break; }
        }
        BlockPos under = BlockPos.containing(drop.x, sl.getMinBuildHeight() + 1, drop.z);
        height = sl.hasChunkAt(under) ? drop.y - sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, under.getX(), under.getZ()) : 0.0;
        double hs = Math.hypot(vel.x, vel.z);
        speed = hs * 20.0;
        track = hs < 1.0E-3 ? 0.0 : (Math.toDegrees(Math.atan2(vel.x, -vel.z)) + 360.0) % 360.0;

        aimed = false;
        boolean wasLocked = locked;
        locked = false;
        double prev = along;
        along = Double.NaN;
        if (mode == CCRP && solved) {
            Vec3 t = null;
            if (laserTarget) {
                LaserSpots.Spot s = getOwner() == null ? null : LaserSpots.nearest(sl, getOwner(), ix, iz, Balance.SIGHT_LASER);
                if (s != null) t = s.pos();
            } else if (targetSet) t = new Vec3(targetX + 0.5, iy, targetZ + 0.5);
            if (t != null) {
                tx = t.x; tz = t.z; aimed = true;
                if (hs > 0.02) {
                    double ux = vel.x / hs, uz = vel.z / hs, dx = t.x - ix, dz = t.z - iz;
                    along = dx * ux + dz * uz;
                    cross = Math.abs(dx * uz - dz * ux);
                    locked = along > 0.0 && cross <= Balance.SIGHT_CROSS;
                    if (locked && !wasLocked) sl.playSound(null, w.x, w.y, w.z, WmdSounds.SIGHT_LOCK.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
                    if (!Double.isNaN(prev) && prev > 0.0 && along <= 0.0 && cross <= Balance.SIGHT_CROSS && pulse == 0) release(sl);
                }
            }
        }
        if (now % (Balance.SIGHT_STEP * 2) == 0) sync();
    }

    private void release(ServerLevel sl) {
        pulse = Balance.SIGHT_PULSE;
        emit(sl);
        Vec3 c = worldCentre();
        sl.playSound(null, c.x, c.y, c.z, WmdSounds.SIGHT_RELEASE.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        Wmd.LOGGER.info("Bomb sight: release pulse at {} (cross {} blocks)", worldPosition.toShortString(), String.format("%.1f", cross));
        if (getOwner() != null && sl.getServer().getPlayerList().getPlayer(getOwner()) instanceof net.minecraft.server.level.ServerPlayer p && p.distanceToSqr(c) < 64.0 * 64.0)
            p.displayClientMessage(Component.translatable("message.wmd.sight.release"), true);
    }

    private void emit(ServerLevel sl) {
        sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        sl.updateNeighborsAt(worldPosition.relative(backFace()), getBlockState().getBlock());
        sync();
    }

    private void stations(ServerLevel sl) {
        List<long[]> l = new ArrayList<>();
        for (BlockPos rp : racks)
            if (sl.getBlockEntity(rp) instanceof BombRackBlockEntity r)
                for (int i = 0; i < r.slots(); i++) {
                    BombType t = r.kind(i);
                    l.add(new long[] { rp.asLong(), i, t == null ? -1 : t.ordinal(), r.count(i), r.heavy ? 1 : 0 });
                }
        int n = l.size();
        stPos = new long[n]; stSlot = new int[n]; stKind = new int[n]; stCount = new int[n]; stBay = new boolean[n];
        for (int i = 0; i < n; i++) { long[] e = l.get(i); stPos[i] = e[0]; stSlot[i] = (int) e[1]; stKind[i] = (int) e[2]; stCount[i] = (int) e[3]; stBay[i] = e[4] != 0; }
        if (station >= n) station = 0;
    }

    void key(net.minecraft.server.level.ServerPlayer p, int k) {
        if (!(level instanceof ServerLevel sl) || mode == OFF || !hasPower()) return;
        double reach = Balance.SIGHT_HUD + 2.0;
        if (SableBridge.worldPos(sl, worldPosition).distanceToSqr(p.position()) > reach * reach) return;
        if (getOwner() != null && !Access.mayUse(this, p)) { Access.deny(this, p); return; }
        racks = findRacks(sl); racksAt = sl.getGameTime();
        stations(sl);
        int n = stPos.length;
        if (n == 0) { p.displayClientMessage(Component.translatable("message.wmd.sight.no_racks", Balance.SIGHT_LINK), true); return; }
        if (k == K_NEXT) {
            int to = (station + 1) % n;
            for (int i = 1; i <= n; i++) { int s = (station + i) % n; if (stCount[s] > 0) { to = s; break; } }
            station = to;
            setChanged();
            sync();
            p.displayClientMessage(Component.translatable("message.wmd.sight.station", station + 1, stationName(station)), true);
            return;
        }
        if (!(sl.getBlockEntity(BlockPos.of(stPos[station])) instanceof BombRackBlockEntity r)) return;
        if (r.getOwner() != null && !Access.mayUse(r, p)) { Access.deny(r, p); return; }
        String why = r.stationDrop(sl, stSlot[station], k == K_SALVO);
        if (why != null) p.displayClientMessage(Component.translatable(why), true);
        else p.displayClientMessage(Component.translatable(k == K_SALVO ? "message.wmd.sight.salvo" : "message.wmd.sight.drop", station + 1, stationName(station)), true);
    }

    public Component stationName(int i) {
        if (i < 0 || i >= stKind.length || stKind[i] < 0 || stCount[i] <= 0) return Component.translatable("hud.wmd.sight.st_empty");
        return Component.translatable("hud.wmd.sight.st_load", Component.translatable("item.wmd." + BombType.ofOrdinal(stKind[i]).id), stCount[i]);
    }

    public int station() { return station; }
    public int stationCount() { return stPos.length; }
    public boolean stationBay(int i) { return stBay[i]; }
    public int stationSlot(int i) { return stSlot[i]; }

    public int stationRack(int i) {
        int r = 0;
        for (int k = 0; k <= i; k++) if (k == 0 || stPos[k] != stPos[k - 1]) r++;
        return r;
    }

    private List<BlockPos> findRacks(ServerLevel sl) {
        List<BlockPos> out = new ArrayList<>();
        int r = Balance.SIGHT_LINK, cx0 = (worldPosition.getX() - r) >> 4, cx1 = (worldPosition.getX() + r) >> 4, cz0 = (worldPosition.getZ() - r) >> 4, cz1 = (worldPosition.getZ() + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            if (!sl.hasChunk(cx, cz)) continue;
            for (BlockEntity be : sl.getChunk(cx, cz).getBlockEntities().values())
                if (be instanceof BombRackBlockEntity && be.getBlockPos().distSqr(worldPosition) <= (double) r * r) out.add(be.getBlockPos());
        }
        out.sort((a, b) -> Double.compare(a.distSqr(worldPosition), b.distSqr(worldPosition)));
        return out;
    }

    public static void clientTickSight(Level level, BlockPos pos, BlockState state, BombSightBlockEntity be) {
        if (be.mode != OFF) CLIENT.put(pos.immutable(), be); else CLIENT.remove(pos);
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) CLIENT.remove(worldPosition);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        boolean on = mode != OFF;
        model.getBone("readout").ifPresent(b -> b.setHidden(!on));
    }

    @Override protected boolean work() { reason = 0; return mode != OFF; }
    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.sight.info_use", Balance.SIGHT_FE * 20, Balance.SIGHT_LINK),
                Component.translatable("gui.wmd.sight.info_out"));
    }

    @Override
    protected String detail() { return mode == OFF ? "off" : mode == CCIP ? "CCIP" : "CCRP" + (aimed ? "" : " (no target)"); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.EAST) ? energy() : null; }

    @Override protected SoundEvent runSound() { return SoundEvents.BEACON_AMBIENT; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }
    @Override protected float soundPitch() { return 1.8F; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("mode", mode); tag.putBoolean("laser", laserTarget); tag.putBoolean("tset", targetSet); tag.putInt("tx", targetX); tag.putInt("tz", targetZ);
        tag.putInt("station", station);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        mode = tag.getInt("mode"); laserTarget = tag.getBoolean("laser"); targetSet = tag.getBoolean("tset"); targetX = tag.getInt("tx"); targetZ = tag.getInt("tz");
        station = tag.getInt("station");
        if (tag.contains("stp")) {
            stPos = tag.getLongArray("stp"); stSlot = tag.getIntArray("sts"); stKind = tag.getIntArray("stk"); stCount = tag.getIntArray("stc");
            int[] b = tag.getIntArray("stb");
            stBay = new boolean[b.length];
            for (int i = 0; i < b.length; i++) stBay[i] = b[i] != 0;
        }
        if (tag.contains("sol")) {
            solved = tag.getBoolean("sol"); aimed = tag.getBoolean("aim");
            ix = tag.getDouble("ix"); iy = tag.getDouble("iy"); iz = tag.getDouble("iz");
            hx = tag.getDouble("hx"); hy = tag.getDouble("hy"); hz = tag.getDouble("hz");
            tx = tag.getDouble("ax"); tz = tag.getDouble("az");
            along = tag.contains("along") ? tag.getDouble("along") : Double.NaN; cross = tag.getDouble("cross");
            height = tag.getDouble("alt"); speed = tag.getDouble("spd"); track = tag.getDouble("trk"); pulse = tag.getInt("pulse");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putInt("mode", mode); tag.putBoolean("laser", laserTarget); tag.putBoolean("tset", targetSet); tag.putInt("tx", targetX); tag.putInt("tz", targetZ);
        tag.putBoolean("sol", solved); tag.putBoolean("aim", aimed);
        tag.putDouble("ix", ix); tag.putDouble("iy", iy); tag.putDouble("iz", iz);
        tag.putDouble("hx", hx); tag.putDouble("hy", hy); tag.putDouble("hz", hz);
        tag.putDouble("ax", tx); tag.putDouble("az", tz);
        if (!Double.isNaN(along)) tag.putDouble("along", along);
        tag.putDouble("cross", cross); tag.putDouble("alt", height); tag.putDouble("spd", speed); tag.putDouble("trk", track); tag.putInt("pulse", pulse);
        tag.putInt("station", station);
        tag.putLongArray("stp", stPos); tag.putIntArray("sts", stSlot); tag.putIntArray("stk", stKind); tag.putIntArray("stc", stCount);
        int[] b = new int[stBay.length];
        for (int i = 0; i < b.length; i++) b[i] = stBay[i] ? 1 : 0;
        tag.putIntArray("stb", b);
        return tag;
    }
}
