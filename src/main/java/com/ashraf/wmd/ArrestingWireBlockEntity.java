package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ArrestingWireBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int IDLE = 0, CAUGHT = 1, RESET = 2;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private int span;
    private int state;
    private long stateAt;
    private UUID jet;
    private BlockPos hookPos;
    private Vec3 dir;
    private double v0, a;
    private Vec3 catchAt;

    private Vec3 tipLocal;

    public ArrestingWireBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.ARRESTING_WIRE_BE.get(), pos, state, 0, Balance.WIRE_FE * 4, Balance.WIRE_FE / 10);
    }

    public int span() { return span; }
    public boolean master() { return span > 0; }
    public int wireState() { return state; }
    public Vec3 tipLocal() { return tipLocal; }
    public long stateAt() { return stateAt; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }
    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return side == Direction.DOWN ? energy() : null; }

    private ArrestingWireBlockEntity partner() {
        if (level == null || span <= 0) return null;
        return level.getBlockEntity(worldPosition.relative(facing(), span)) instanceof ArrestingWireBlockEntity o ? o : null;
    }

    private int charge() { ArrestingWireBlockEntity p = partner(); return energy().getEnergyStored() + (p == null ? 0 : p.energy().getEnergyStored()); }

    private void spend(int fe) {
        int own = Math.min(fe, energy().getEnergyStored());
        if (own > 0 && useExtraEnergy(own)) fe -= own;
        ArrestingWireBlockEntity p = partner();
        if (fe > 0 && p != null) p.useExtraEnergy(Math.min(fe, p.energy().getEnergyStored()));
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    Vec3[] ends() {
        Direction f = facing();
        Vec3 aEnd = Vec3.atCenterOf(worldPosition).add(0.0, 0.5 + Balance.WIRE_UP, 0.0);
        return new Vec3[] { aEnd, aEnd.add(f.getStepX() * span, f.getStepY() * span, f.getStepZ() * span) };
    }

    private int findSpan() {
        if (level == null || level.isClientSide) return span;
        Direction f = facing();
        if (f.getAxis() != Direction.Axis.Y) {
            int i = reach(f);
            if (i > 0 && level.getBlockEntity(worldPosition.relative(f, i)) instanceof ArrestingWireBlockEntity o && o.facing() == f.getOpposite())
                return worldPosition.asLong() < o.getBlockPos().asLong() ? i : 0;
        }
        Direction best = null;
        int bestI = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int i = reach(d);
            if (i <= 0 || (bestI > 0 && i >= bestI)) continue;
            if (!(level.getBlockEntity(worldPosition.relative(d, i)) instanceof ArrestingWireBlockEntity o) || o.pairedAway(d.getOpposite())) continue;
            best = d;
            bestI = i;
        }
        if (best == null) return 0;
        ArrestingWireBlockEntity o = (ArrestingWireBlockEntity) level.getBlockEntity(worldPosition.relative(best, bestI));
        turn(this, best);
        turn(o, best.getOpposite());
        return worldPosition.asLong() < o.getBlockPos().asLong() ? bestI : 0;
    }

    private int reach(Direction d) {
        for (int i = 1; i <= Balance.WIRE_SPAN; i++) {
            BlockPos q = worldPosition.relative(d, i);
            if (level.getBlockEntity(q) instanceof ArrestingWireBlockEntity) return i >= 2 ? i : 0;
            BlockPos u = q.above();
            if (!level.getBlockState(u).getCollisionShape(level, u).isEmpty()) return 0;
        }
        return 0;
    }

    private boolean pairedAway(Direction toward) {
        Direction f = facing();
        if (f == toward || f.getAxis() == Direction.Axis.Y) return false;
        int i = reach(f);
        return i > 0 && level.getBlockEntity(worldPosition.relative(f, i)) instanceof ArrestingWireBlockEntity o && o.facing() == f.getOpposite();
    }

    private static void turn(ArrestingWireBlockEntity w, Direction d) {
        BlockState s = w.getBlockState();
        if (s.getValue(MachineBlock.FACING) != d) w.level.setBlock(w.worldPosition, s.setValue(MachineBlock.FACING, d), 3);
    }

    private boolean slaveOfPair() {
        if (level == null || span > 0) return false;
        Direction f = facing();
        if (f.getAxis() == Direction.Axis.Y) return false;
        int i = reach(f);
        return i > 0 && level.getBlockEntity(worldPosition.relative(f, i)) instanceof ArrestingWireBlockEntity o && o.span == i;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) return false;
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 31L)) % 40 == 0) { int s = findSpan(); if (s != span) { span = s; sync(); } }
        if (span <= 0) return false;
        UUID deck = SableBridge.shipIdAt(sl, worldPosition);
        switch (state) {
            case IDLE -> watch(sl, deck, now);
            case CAUGHT -> arrest(sl, deck, now);
            case RESET -> { if (now - stateAt >= Balance.WIRE_RESET_TICKS) { state = IDLE; stateAt = now; playOnce(sl, WmdSounds.WIRE_RESET.get(), Balance.SND_CLICK, 1.0F); sync(); } }
            default -> state = IDLE;
        }
        return false;
    }

    private Vec3 local(ServerLevel sl, UUID deck, Vec3 w) { return deck == null ? w : SableBridge.toShip(sl, deck, w); }

    private void watch(ServerLevel sl, UUID deck, long now) {
        Vec3[] e = ends();
        Vec3 mid = SableBridge.toWorld(sl, e[0].add(e[1]).scale(0.5));
        if (mid == null) return;
        for (TailhookBlockEntity h : TailhookBlockEntity.downHooks(sl)) {
            if (h.ship() == null || h.ship().equals(deck) || h.tip().distanceToSqr(mid) > 40.0 * 40.0) continue;
            Vec3 p0 = local(sl, deck, h.tipPrev()), p1 = local(sl, deck, h.tip());
            if (p0 == null || p1 == null) continue;
            Vec3 x = crossing(e[0], e[1], p0, p1);
            if (x == null) continue;
            if (charge() < Balance.WIRE_FE) { Wmd.LOGGER.info("Arresting wire at {}: a hook crossed, but no charge (busbar)", worldPosition); continue; }
            SableBridge.ShipInfo ji = SableBridge.shipInfo(sl, h.ship());
            if (ji == null) continue;
            Vec3 rel = relVel(sl, ji, deck);
            Vec3 horiz = new Vec3(rel.x, 0.0, rel.z);
            if (horiz.length() < Balance.WIRE_STOP_SPEED) continue;
            spend(Balance.WIRE_FE);
            jet = h.ship();
            hookPos = h.getBlockPos();
            dir = horiz.normalize();
            v0 = horiz.length();
            a = v0 * v0 / (2.0 * Balance.WIRE_RUNOUT);
            catchAt = x;
            tipLocal = x;
            state = CAUGHT;
            stateAt = now;
            h.caught(sl, 10);
            playOnce(sl, WmdSounds.WIRE_CATCH.get(), Balance.SND_MECH, 1.0F);
            Wmd.LOGGER.info("Arresting wire at {}: caught {} at {} b/s", worldPosition, jet, String.format("%.1f", v0));
            sync();
            return;
        }
    }

    private static Vec3 relVel(ServerLevel sl, SableBridge.ShipInfo jet, UUID deck) {
        Vec3 v = ShipDefence.velocity(sl, jet).scale(20.0);
        if (deck != null) { SableBridge.ShipInfo d = SableBridge.shipInfo(sl, deck); if (d != null) v = v.subtract(ShipDefence.velocity(sl, d).scale(20.0)); }
        return v;
    }

    private void arrest(ServerLevel sl, UUID deck, long now) {
        SableBridge.ShipInfo ji = jet == null ? null : SableBridge.shipInfo(sl, jet);
        TailhookBlockEntity h = hookPos != null && sl.isLoaded(hookPos) && sl.getBlockEntity(hookPos) instanceof TailhookBlockEntity t ? t : null;
        if (ji == null || h == null || !h.down() || now - stateAt > Balance.WIRE_MAX_TICKS) { release(sl, now, "lost"); return; }
        Vec3 rel = relVel(sl, ji, deck);
        double along = rel.dot(dir);
        Vec3 tip = h.tip() == null ? null : local(sl, deck, h.tip());
        if (tip != null) {
            tipLocal = tip;
            if (tip.distanceTo(catchAt) > Balance.WIRE_RUNOUT * 1.8) { release(sl, now, "parted"); return; }
            if ((now - stateAt) % 2 == 0) sync();
        }
        if (along <= Balance.WIRE_STOP_SPEED) { release(sl, now, "stopped"); return; }
        double dv = Math.min(along, a / 20.0);
        SableBridge.shipPush(sl, jet, dir.scale(-dv), Vec3.ZERO);
        h.caught(sl, 10);
    }

    private void release(ServerLevel sl, long now, String why) {
        Wmd.LOGGER.info("Arresting wire at {}: released {} ({})", worldPosition, jet, why);
        state = RESET;
        stateAt = now;
        jet = null;
        hookPos = null;
        tipLocal = null;
        sync();
    }

    static Vec3 crossing(Vec3 a, Vec3 b, Vec3 p0, Vec3 p1) {
        double rx = b.x - a.x, rz = b.z - a.z, sx = p1.x - p0.x, sz = p1.z - p0.z;
        double den = rx * sz - rz * sx;
        if (Math.abs(den) < 1.0E-9) return null;
        double qx = p0.x - a.x, qz = p0.z - a.z;
        double t = (qx * sz - qz * sx) / den, u = (qx * rz - qz * rx) / den;
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null;
        double y = p0.y + (p1.y - p0.y) * u, wy = a.y + (b.y - a.y) * t;
        if (y < wy - Balance.WIRE_CATCH_LOW || y > wy + Balance.WIRE_CATCH_HIGH) return null;
        return new Vec3(a.x + rx * t, wy, a.z + rz * t);
    }

    @Override public void ccAnimate(CcBones model, double tt, float pt) {}

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GPOD_SPIN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected String detail() { return (span > 0 ? "master, span " + span : "no partner / slave") + ", " + new String[] { "ready", "ARRESTING " + jet, "resetting" }[Math.max(0, Math.min(2, state))]; }

    @Override
    public Component status() {
        if (slaveOfPair()) return Component.translatable("block.wmd.arresting_wire").append(": ").append(Component.translatable("message.wmd.wire.paired"));
        String k = span <= 0 ? "message.wmd.wire.no_partner" : state == CAUGHT ? "message.wmd.wire.caught" : state == RESET ? "message.wmd.wire.reset"
                : charge() < Balance.WIRE_FE ? "message.wmd.wire.no_power" : "message.wmd.wire.ready";
        return Component.translatable("block.wmd.arresting_wire").append(": ").append(Component.translatable(k, span, Balance.WIRE_SPAN));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        if (tag.contains("awSpan")) {
            span = tag.getInt("awSpan");
            state = tag.getInt("awState");
            stateAt = tag.getLong("awAt");
            tipLocal = tag.contains("awTx") ? new Vec3(tag.getDouble("awTx"), tag.getDouble("awTy"), tag.getDouble("awTz")) : null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("awSpan", span);
        tag.putInt("awState", state);
        tag.putLong("awAt", stateAt);
        if (tipLocal != null) { tag.putDouble("awTx", tipLocal.x); tag.putDouble("awTy", tipLocal.y); tag.putDouble("awTz", tipLocal.z); }
        return tag;
    }
}
