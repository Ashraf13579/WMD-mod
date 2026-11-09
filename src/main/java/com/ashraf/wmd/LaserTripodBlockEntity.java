package com.ashraf.wmd;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class LaserTripodBlockEntity extends BlockEntity implements Owned, MachinePorts {

    private static class Buffer extends EnergyStorage {
        Buffer(int cap, int receive) { super(cap, receive, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private static final Set<LaserTripodBlockEntity> ALL = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));

    private final OwnerData ownerData = new OwnerData();
    private final Buffer store = new Buffer(Balance.TRIPOD_BUFFER, Balance.TRIPOD_RECEIVE);
    private float yaw, pitch;
    private boolean lasing;
    private int spotId = -1;
    private double beamLen;
    private UUID tracked;
    private long aimedAt;

    private UUID trackShip;
    private Vec3 trackPlot;

    public LaserTripodBlockEntity(BlockPos pos, BlockState state) { super(Wmd.LASER_TRIPOD_BE.get(), pos, state); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean lasing() { return lasing; }
    public double beamLen() { return beamLen; }
    public int energy() { return store.getEnergyStored(); }
    public int spotId() { return lasing ? spotId : -1; }

    static List<LaserTripodBlockEntity> tripodsOf(Level level, UUID owner) {
        List<LaserTripodBlockEntity> l = new java.util.ArrayList<>();
        synchronized (ALL) { for (LaserTripodBlockEntity t : ALL) if (!t.isRemoved() && t.level == level && owner != null && owner.equals(t.getOwner())) l.add(t); }
        return l;
    }

    Vec3 sight() { return sightOf(level, worldPosition, getBlockState()); }

    static Vec3 sightOf(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        Vec3 p = Machines.mountPoint(pos, Machines.mount(state), 0.0, 25.4 / 16.0, 0.0);
        if (level == null) return p;
        return level.isClientSide ? ShipFrame.drawnWorld(level, pos, p, Float.NaN) : SableBridge.toWorld(level, p);
    }

    static final double CLEAR = 0.9;

    static Vec3 dir(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        Direction f = getBlockState().getValue(MachineBlock.FACING), m = Machines.mount(getBlockState());
        BlockPos l = Machines.toLocal(worldPosition, f, m, part);
        return l.equals(BlockPos.ZERO) && side == Machines.dirToWorld(f, m, Direction.SOUTH) ? store : null;
    }

    void aim(ServerPlayer player, float y, float p, boolean on) {
        if (!(level instanceof ServerLevel sl) || SableBridge.distSqWithShips(sl, player.position(), Vec3.atCenterOf(worldPosition)) > Balance.TRIPOD_REACH * Balance.TRIPOD_REACH) return;
        if (getOwner() != null && !Access.mayUse(this, player)) { Access.deny(this, player); return; }
        yaw = Mth.wrapDegrees(y);
        pitch = Mth.clamp(p, -89.0F, 89.0F);
        aimedAt = sl.getGameTime();
        if (on && !lasing) {
            if (store.getEnergyStored() < Balance.TRIPOD_FE_PER_TICK * 20) { player.displayClientMessage(Component.translatable("message.wmd.tripod.flat"), true); on = false; }
            else sl.playSound(null, worldPosition, WmdSounds.LASER_ON.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        }
        if (!on && lasing) sl.playSound(null, worldPosition, WmdSounds.LASER_OFF.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        boolean was = lasing;
        lasing = on;

        if (lasing) { long gt = sl.getGameTime(); if (!was || gt - aimBeamAt >= 4) { aimBeamAt = gt; beam(sl, player); sync(); } }
        else { off(); sync(); }
    }

    private long aimBeamAt = -100L;

    private void beam(ServerLevel sl, Player operator) {
        Vec3 from = sight(), d = dir(yaw, pitch), start = from.add(d.scale(CLEAR));
        net.minecraft.world.phys.BlockHitResult bh = BeamTrace.hit(sl, start, d, Balance.BEAM_RANGE);
        Vec3 hit = bh == null ? null : bh.getLocation();
        UUID plainShip = bh == null ? null : SableBridge.shipIdAt(sl, bh.getBlockPos());
        if (plainShip != null) {
            Vec3 w = shipWorld(sl, bh.getBlockPos(), bh.getLocation(), from);
            if (w == null) hit = null;
            else { hit = w; trackShip = plainShip; trackPlot = bh.getLocation(); tracked = null; }
        }
        if (hit != null && hit.distanceTo(from) > Balance.BEAM_RANGE + 8.0) hit = null;
        Vec3 dh = Debris.hitAlong(sl, start, hit != null ? hit : from.add(d.scale(Balance.BEAM_RANGE)));
        if (dh != null) { hit = dh; plainShip = null; }
        Vec3 end = hit != null ? hit : from.add(d.scale(Balance.BEAM_RANGE));
        EntityHitResult e = ProjectileUtil.getEntityHitResult(sl, operator, start, end, new AABB(start, end).inflate(1.0),
                x -> x instanceof Player pl && !pl.isSpectator() && pl != operator && x.isAlive());
        if (e != null) { hit = e.getLocation(); tracked = e.getEntity().getUUID(); }
        else if (operator != null) tracked = null;
        ShipHit sh = shipHit(sl, start, end);
        if (sh != null && (hit == null || sh.world().distanceToSqr(from) < hit.distanceToSqr(from))) {
            hit = sh.world();
            trackShip = sh.ship();
            trackPlot = sh.plot();
            tracked = null;
        } else if (operator != null && plainShip == null) { trackShip = null; trackPlot = null; }
        if (hit == null) { double cut = SmokeScreens.entry(sl, from, end); beamLen = cut >= 0.0 ? cut : Balance.BEAM_RANGE; if (spotId >= 0) { LaserSpots.remove(spotId); spotId = -1; } return; }
        spot(sl, from, hit);
    }

    private void spot(ServerLevel sl, Vec3 from, Vec3 hit) {
        double cut = SmokeScreens.entry(sl, from, hit);
        if (cut >= 0.0) { beamLen = Math.max(0.0, cut); if (spotId >= 0) { LaserSpots.remove(spotId); spotId = -1; } return; }
        beamLen = hit.distanceTo(from);
        UUID owner = getOwner();
        if (owner == null) return;
        if (spotId < 0 || !LaserSpots.refresh(sl, spotId, hit, from)) spotId = LaserSpots.light(sl, owner, hit, LaserSpots.SRC_TRIPOD, from);
    }

    private record ShipHit(UUID ship, Vec3 plot, Vec3 world) {}

    private static ShipHit shipHit(ServerLevel sl, Vec3 from, Vec3 to) {
        ShipHit best = null;
        double bd = Double.MAX_VALUE;
        for (SableBridge.ShipInfo ship : SableBridge.shipsAlongSegment(sl, from, to)) {
            Vec3 a = SableBridge.toShip(sl, ship.id(), from), b = SableBridge.toShip(sl, ship.id(), to);
            if (a == null || b == null) continue;
            net.minecraft.world.phys.BlockHitResult r = sl.clip(new net.minecraft.world.level.ClipContext(a, b, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (r.getType() == net.minecraft.world.phys.HitResult.Type.MISS || !ship.id().equals(SableBridge.shipIdAt(sl, r.getBlockPos()))) continue;
            Vec3 w = shipWorld(sl, r.getBlockPos(), r.getLocation(), from);
            if (w == null) continue;
            double d = w.distanceToSqr(from);
            if (d < bd) { bd = d; best = new ShipHit(ship.id(), r.getLocation(), w); }
        }
        return best;
    }

    private static Vec3 shipWorld(ServerLevel sl, net.minecraft.core.BlockPos block, Vec3 plot, Vec3 from) {
        Vec3 w = SableBridge.toWorld(sl, plot);
        if (w == null || w.distanceToSqr(plot) < 1.0E-6) w = SableBridge.worldPos(sl, block);
        return w != null && w.distanceTo(from) <= Balance.BEAM_RANGE + 8.0 ? w : null;
    }

    private boolean followShip(ServerLevel sl) {
        if (SableBridge.shipInfo(sl, trackShip) == null) return false;
        Vec3 from = sight(), w = shipWorld(sl, net.minecraft.core.BlockPos.containing(trackPlot), trackPlot, from);
        if (w == null) return false;
        Vec3 d = w.subtract(from);
        double len = d.length();
        if (len < 0.5 || len > Balance.BEAM_RANGE) return false;
        Vec3 start = from.add(d.scale(CLEAR / len));
        Vec3 ground = BeamTrace.trace(sl, start, d, len - CLEAR);
        if (ground != null && ground.distanceTo(from) < len - 0.75) return false;
        ShipHit first = shipHit(sl, start, w.add(d.scale(0.5 / len)));
        if (first != null && (!first.ship().equals(trackShip) || first.world().distanceTo(w) > 1.5)) return false;
        yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(d.y / len, -1.0, 1.0)));
        spot(sl, from, w);
        return true;
    }

    private void off() {
        lasing = false;
        tracked = null;
        trackShip = null;
        trackPlot = null;
        if (spotId >= 0) { LaserSpots.remove(spotId); spotId = -1; }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserTripodBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 20 == 0) synchronized (ALL) { ALL.add(be); }
        if (!be.lasing) return;
        if (EmpField.jammed(sl, pos) || be.store.getEnergyStored() < Balance.TRIPOD_FE_PER_TICK) { be.off(); be.sync(); return; }
        be.store.use(Balance.TRIPOD_FE_PER_TICK);
        long now = sl.getGameTime();
        if ((now + pos.asLong()) % 10 != 0) return;
        if (be.trackShip != null && now - be.aimedAt > Balance.TRIPOD_IDLE) {
            float wy = be.yaw, wp = be.pitch;
            if (!be.followShip(sl)) { be.off(); be.sync(); return; }
            if (wy != be.yaw || wp != be.pitch || now % 40 == 0) be.sync();
            return;
        }
        if (be.tracked != null && now - be.aimedAt > Balance.TRIPOD_IDLE) {
            Entity t = sl.getEntity(be.tracked);
            if (t == null || !t.isAlive()) { be.off(); be.sync(); return; }
            Vec3 from = be.sight(), to = t.position().add(0, t.getBbHeight() * 0.6, 0), d = to.subtract(from);
            Vec3 block = BeamTrace.trace(sl, from.add(d.normalize().scale(CLEAR)), d, d.length() - CLEAR);
            if (block != null && block.distanceTo(from) < d.length() - 0.5) { be.off(); be.sync(); return; }
            be.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            be.pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(d.normalize().y, -1.0, 1.0)));
        }
        double was = be.beamLen;
        float wy = be.yaw, wp = be.pitch;
        be.beam(sl, null);
        if (Math.abs(was - be.beamLen) > 0.3 || wy != be.yaw || wp != be.pitch || now % 40 == 0) be.sync();
        if (now % 100 == 0) be.setChanged();
    }

    public Component info() {
        return Component.translatable("message.wmd.tripod.info", String.format("%,d", store.getEnergyStored()), String.format("%,d", store.getMaxEnergyStored()),
                Component.translatable(lasing ? "message.wmd.tripod.on" : "message.wmd.tripod.off"));
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && spotId >= 0) { LaserSpots.remove(spotId); spotId = -1; }
        super.setRemoved();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag t = new CompoundTag();
        t.putInt("energy", store.getEnergyStored());
        components.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        CustomData cd = input.get(DataComponents.CUSTOM_DATA);
        if (cd != null) store.set(cd.copyTag().getInt("energy"));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("energy");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("energy", store.getEnergyStored());
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("energy")) store.set(tag.getInt("energy"));
        yaw = tag.getFloat("yaw");
        pitch = tag.getFloat("pitch");
        if (tag.contains("lasing")) { lasing = tag.getBoolean("lasing"); beamLen = tag.getDouble("beam"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putBoolean("lasing", lasing);
        tag.putDouble("beam", beamLen);
        tag.putInt("energy", store.getEnergyStored());
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
