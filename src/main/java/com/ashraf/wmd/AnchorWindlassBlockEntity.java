package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class AnchorWindlassBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int S_STOWED = 0, S_DROPPING = 1, S_HOLDING = 2, S_DRAGGING = 3, S_WEIGHING = 4, S_NO_BOTTOM = 5;

    public static final int P_BLOCKED = 1, P_NO_WATER = 2, P_NO_POWER = 4, P_NOT_SHIP = 8;

    static final float GYPSY_R = 4.4F, PITCH = 3.8F, RUN_DZ = 0.8735F, RUN_DY = 0.4868F, HAWSE_Y = 4.4F, SHACKLE_Y = 3.6F, ANCHOR_DROP_PX = 19.1F;
    static final float BRAKE_OFF = -10.0F, STOPPER_UP = 70.0F, LEVER = 25.0F, HANDWHEEL_TURNS = 200.0F;

    static final float GYPSY_SIGN = 1.0F, ROT_SIGN = 1.0F, CHAIN_SIGN = 1.0F;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private int state = S_STOWED, problems;
    private float chainOut;
    private int pipes;
    private boolean onBottom;
    private Vec3 anchor;
    private float waterDepth = -1.0F, seaDist = -1.0F, tension;

    private UUID ship;
    private AnchorHold.Joint joint;
    private long lastStep = Long.MIN_VALUE, lastJoin = Long.MIN_VALUE / 2, lastSync;
    private int digTicks;
    private boolean lastRs, splashed;
    private int airAbove = -1;
    private Vec3 sentAnchor;
    private float sentChain = -9.0F;

    private float shownChain, travel, brakeT, stopT, leverT;
    private double lastT = Double.NaN;

    public AnchorWindlassBlockEntity(BlockPos pos, BlockState state) {
        super(AnchorBlocks.ANCHOR_WINDLASS_BE.get(), pos, state, Balance.ANCHOR_FE_WEIGH, Balance.ANCHOR_BUFFER, Balance.ANCHOR_FE_WEIGH * 4);
    }

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

    private static final PortMap PORT_MAP = new PortMap(0, 1, 2).power(1, 0, 1, Direction.SOUTH, false);

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int state() { return state; }
    public int problems() { return problems; }
    public float chainOut() { return chainOut; }
    public int pipes() { return pipes; }
    public boolean onBottom() { return onBottom; }
    public Vec3 anchorPoint() { return anchor; }
    public float waterDepth() { return waterDepth; }
    public float seaDist() { return seaDist; }
    public float tension() { return tension; }
    public float storedFraction() { return energy().getEnergyStored() / (float) Math.max(1, energy().getMaxEnergyStored()); }

    public boolean running() { return state == S_DROPPING || state == S_WEIGHING; }

    String command(boolean drop, ServerPlayer by) {
        if (!(level instanceof ServerLevel sl)) return "gone";
        if (!AirGate.on()) return "gated";
        if (drop) {
            if (state == S_DROPPING || state == S_HOLDING || state == S_DRAGGING || state == S_NO_BOTTOM) return "already_down";
            Hawse h = hawse(sl);
            if (h.blocked) { problems |= P_BLOCKED; sync(); return "blocked"; }
            Sea s = sea(sl, h.exitWorld);
            if (s.kind == Sea.NO_WATER) { problems |= P_NO_WATER; sync(); return "no_water"; }
            setState(sl, S_DROPPING);
            UUID sh = ship != null ? ship : SableBridge.shipIdAt(sl, worldPosition);
            if (sh != null) SeaPilot.anchorDropped(sl, sh);
            mech(sl, WmdSounds.ANCHOR_BRAKE.get(), 1.15F);
            return "drop";
        }
        if (state == S_STOWED) return "already_up";
        if (state == S_WEIGHING) return "weighing";
        setState(sl, S_WEIGHING);
        mech(sl, WmdSounds.ANCHOR_BRAKE.get(), 0.9F);
        return "weigh";
    }

    private void toggle(ServerLevel sl) {
        boolean down = state == S_DROPPING || state == S_HOLDING || state == S_DRAGGING || state == S_NO_BOTTOM;
        command(!down, null);
    }

    private void setState(ServerLevel sl, int s) {
        if (s == state) return;
        state = s;
        digTicks = 0;
        if (s == S_STOWED) { chainOut = 0.0F; onBottom = false; anchor = null; tension = 0.0F; letGo(sl); }
        if (s == S_DROPPING) splashed = false;
        sync();
        if (ship != null) Anchors.report(sl, ship, worldPosition, state);
    }

    private void mech(ServerLevel sl, SoundEvent e, float pitch) {
        Vec3 w = SableBridge.toWorld(sl, Vec3.atCenterOf(worldPosition));
        sl.playSound(null, w.x, w.y, w.z, e, SoundSource.BLOCKS, Balance.SND_MECH, pitch);
    }

    record Hawse(int pipes, Vec3 exitPlot, Vec3 exitWorld, boolean blocked) {}

    Hawse hawse(Level l) {
        int n = 0;
        BlockPos p = worldPosition.below();
        while (n < Balance.ANCHOR_PIPES_MAX && l.getBlockState(p).getBlock() instanceof HawsePipeBlock) { n++; p = p.below(); }
        BlockState st = l.getBlockState(p);
        boolean blocked = !st.getCollisionShape(l, p).isEmpty();
        Vec3 plot = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() - n, worldPosition.getZ() + 0.5);
        Vec3 world = l instanceof ServerLevel ? SableBridge.toWorld(l, plot) : ShipFrame.drawnWorld(l, worldPosition, plot, Float.NaN);
        return new Hawse(n, plot, world == null ? plot : world, blocked);
    }

    record Sea(int kind, int air, int water, double seabedY) {
        static final int UNKNOWN = 0, BOTTOM = 1, NO_WATER = 2, TOO_DEEP = 3;
        double dist(Vec3 exit) { return exit.y - seabedY; }
    }

    static Sea sea(Level l, Vec3 exit) {
        int x = Mth.floor(exit.x), z = Mth.floor(exit.z), y0 = Mth.floor(exit.y - 1.0E-3);
        int air = 0, water = 0;
        if (!l.hasChunk(x >> 4, z >> 4)) return new Sea(Sea.UNKNOWN, 0, 0, Double.NaN);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= Balance.ANCHOR_AIR_MAX + Balance.ANCHOR_WATER_MAX + 1; i++) {
            int y = y0 - i;
            if (y < l.getMinBuildHeight()) return new Sea(water > 0 ? Sea.TOO_DEEP : Sea.NO_WATER, air, water, Double.NaN);
            p.set(x, y, z);
            BlockState st = l.getBlockState(p);
            boolean wet = l.getFluidState(p).is(FluidTags.WATER);
            VoxelShape sh = st.getCollisionShape(l, p);
            if (!sh.isEmpty()) {
                if (water == 0) return new Sea(Sea.NO_WATER, air, 0, Double.NaN);
                return new Sea(Sea.BOTTOM, air, water, y + sh.max(Direction.Axis.Y));
            }
            if (wet) { if (++water > Balance.ANCHOR_WATER_MAX) return new Sea(Sea.TOO_DEEP, air, water, Double.NaN); }
            else if (water == 0 && ++air > Balance.ANCHOR_AIR_MAX) return new Sea(Sea.NO_WATER, air, 0, Double.NaN);
        }
        return new Sea(Sea.TOO_DEEP, air, water, Double.NaN);
    }

    private static double seabedAt(ServerLevel sl, double wx, double wz, double fromY) {
        int x = Mth.floor(wx), z = Mth.floor(wz);
        if (!sl.hasChunk(x >> 4, z >> 4)) return Double.NaN;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(fromY) + 3; y >= Mth.floor(fromY) - 6 && y >= sl.getMinBuildHeight(); y--) {
            p.set(x, y, z);
            VoxelShape sh = sl.getBlockState(p).getCollisionShape(sl, p);
            if (!sh.isEmpty()) return y + sh.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    @Override
    protected boolean work() {
        return level instanceof ServerLevel sl && step(sl, true);
    }

    @Override
    protected void idle() {
        if (level instanceof ServerLevel sl) step(sl, false);
    }

    private boolean step(ServerLevel sl, boolean busbar) {
        long now = sl.getGameTime();
        if (now == lastStep) return false;
        lastStep = now;
        if (now % 10 == (worldPosition.asLong() & 7) || (ship == null && state != S_STOWED && now % 2 == 0)) ship = SableBridge.shipIdAt(sl, worldPosition);
        boolean on = AirGate.on();
        if (now % 2 == 0) {
            boolean rs = redstone(sl);
            if (on && rs && !lastRs) toggle(sl);
            lastRs = rs;
        }
        if (!on) { if (joint != null) letGo(sl); return false; }
        int before = problems;
        problems &= ~(P_BLOCKED | P_NO_WATER | P_NO_POWER | P_NOT_SHIP);
        if (state == S_STOWED) {
            if (now % 40 == 0) { Hawse h = hawse(sl); pipes = h.pipes(); if (h.blocked()) problems |= P_BLOCKED; }
            if (problems != before) sync();
            return false;
        }
        Hawse h = hawse(sl);
        pipes = h.pipes();
        if (ship == null) problems |= P_NOT_SHIP;
        boolean used = false;
        switch (state) {
            case S_DROPPING, S_NO_BOTTOM -> drop(sl, h, now);
            case S_HOLDING, S_DRAGGING -> hold(sl, h, now, false);
            case S_WEIGHING -> used = weigh(sl, h, now, busbar);
            default -> { }
        }
        if (ship != null && state != S_STOWED && now % Balance.ANCHOR_BEAT == 0) Anchors.report(sl, ship, worldPosition, state);
        if (problems != before || Math.abs(chainOut - sentChain) > 0.4F || anchorMoved() || now - lastSync > 40) sendState(now);
        return used;
    }

    private boolean anchorMoved() {
        if (anchor == null) return sentAnchor != null;
        return sentAnchor == null || anchor.distanceToSqr(sentAnchor) > 0.04;
    }

    private void sendState(long now) {
        sentChain = chainOut;
        sentAnchor = anchor;
        lastSync = now;
        sync();
    }

    private boolean redstone(ServerLevel sl) {
        Direction f = facing();
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++)
            if (sl.hasNeighborSignal(Machines.toWorld(worldPosition, f, Direction.UP, x, 0, z))) return true;
        return false;
    }

    private void drop(ServerLevel sl, Hawse h, long now) {
        if (h.blocked()) { problems |= P_BLOCKED; return; }
        Sea s = sea(sl, h.exitWorld());
        if (s.kind() == Sea.BOTTOM) { waterDepth = s.water(); seaDist = (float) s.dist(h.exitWorld()); airAbove = s.air(); }
        else if (s.kind() == Sea.TOO_DEEP) { waterDepth = s.water(); seaDist = -1.0F; airAbove = s.air(); }
        else if (s.kind() == Sea.NO_WATER) { problems |= P_NO_WATER; }
        if (state == S_DROPPING) chainOut = Math.min(Balance.ANCHOR_CHAIN_MAX, chainOut + (float) (Balance.ANCHOR_DROP_SPEED / 20.0));
        if (!splashed && airAbove >= 0 && chainOut >= airAbove && s.kind() != Sea.NO_WATER) {
            splashed = true;
            Vec3 w = h.exitWorld().add(0.0, -airAbove, 0.0);
            sl.playSound(null, w.x, w.y, w.z, WmdSounds.ANCHOR_SPLASH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
            sl.sendParticles(ParticleTypes.SPLASH, w.x, w.y + 0.2, w.z, 24, 0.4, 0.2, 0.4, 0.2);
        }
        if (s.kind() == Sea.BOTTOM && chainOut >= s.dist(h.exitWorld())) {
            chainOut = (float) Math.max(0.0, s.dist(h.exitWorld()));
            onBottom = true;
            anchor = new Vec3(h.exitWorld().x, s.seabedY(), h.exitWorld().z);
            if (joint != null) AnchorHold.moveAnchor(joint, anchor);
            mud(sl, 18);
            double sp = ship == null ? 0.0 : Crafts.speed(sl, ship, null);
            setState(sl, sp > Balance.ANCHOR_DIG_SPEED ? S_DRAGGING : S_HOLDING);
            if (state == S_HOLDING) mech(sl, WmdSounds.ANCHOR_BRAKE.get(), 1.0F);
            return;
        }
        if (state == S_DROPPING && chainOut >= Balance.ANCHOR_CHAIN_MAX) setState(sl, S_NO_BOTTOM);
    }

    private void mud(ServerLevel sl, int n) {
        if (anchor == null) return;
        BlockPos b = BlockPos.containing(anchor.x, anchor.y - 0.5, anchor.z);
        BlockState bed = sl.getBlockState(b);
        if (bed.isAir()) return;
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bed), anchor.x, anchor.y + 0.3, anchor.z, n, 0.6, 0.3, 0.6, 0.05);
    }

    private void hold(ServerLevel sl, Hawse h, long now, boolean weighing) {
        if (anchor == null) { onBottom = false; setState(sl, S_NO_BOTTOM); return; }
        Vec3 ex = h.exitWorld();
        double dx = ex.x - anchor.x, dz = ex.z - anchor.z, dist = Math.sqrt(dx * dx + dz * dz), vert = Math.max(0.0, ex.y - anchor.y);
        double need = Math.sqrt(dist * dist + vert * vert);
        if (!weighing) chainOut = (float) Mth.clamp(Math.max(chainOut, need), 0.0, Balance.ANCHOR_CHAIN_MAX);
        waterDepth = Math.max(waterDepth, 0.0F);
        seaDist = (float) vert;
        double speed = ship == null ? 0.0 : Crafts.speed(sl, ship, null);
        ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);
        boolean pushing = o != null && Math.abs(o.throttle()) > Balance.ANCHOR_DRAG_THROTTLE + 1.0E-6;
        double r = Balance.ANCHOR_SWING_R;
        if (weighing) r = Math.min(r, Math.sqrt(Math.max(0.0, (double) chainOut * chainOut - vert * vert)));
        boolean slip = !weighing && (dist > r + Balance.ANCHOR_SLIP || need > Balance.ANCHOR_CHAIN_MAX + 1.0);
        if (state == S_HOLDING && !weighing && (pushing || slip)) { setState(sl, S_DRAGGING); mech(sl, WmdSounds.ANCHOR_BRAKE.get(), 0.7F); }
        boolean drag = state == S_DRAGGING;
        if (drag) {
            if (!pushing && speed < Balance.ANCHOR_DIG_SPEED) {
                if (++digTicks >= Balance.ANCHOR_DIG_TICKS) { setState(sl, S_HOLDING); mech(sl, WmdSounds.ANCHOR_BRAKE.get(), 1.0F); drag = false; }
            } else digTicks = 0;

            double lim = r + (pushing ? 0.0 : Balance.ANCHOR_SLIP * 0.5);
            if (drag && dist > lim) {
                double k = (dist - lim) / dist;
                double nx = anchor.x + dx * k, nz = anchor.z + dz * k, ny = seabedAt(sl, nx, nz, anchor.y);
                anchor = new Vec3(nx, Double.isNaN(ny) ? anchor.y : ny, nz);
                if (joint != null) AnchorHold.moveAnchor(joint, anchor);
                if (now % 4 == 0) mud(sl, 6);
                dx = ex.x - anchor.x; dz = ex.z - anchor.z; dist = Math.sqrt(dx * dx + dz * dz);
            }
        }
        if (ship == null) { tension = 0.0F; return; }
        double m = AnchorHold.mass(sl, ship);
        if (!(m > 0.0) || !Double.isFinite(m)) return;
        double w = 2.0 * Math.PI / Balance.ANCHOR_SETTLE_S;
        double kSpring = m * w * w, damp = 2.0 * Balance.ANCHOR_ZETA * m * w;
        double cap = m * Balance.GEAR_G * (drag ? Balance.ANCHOR_DRAG_G : Balance.ANCHOR_HOLD_G);
        boolean outside = dist > r;
        double tx = outside ? dx * r / dist : dx, tz = outside ? dz * r / dist : dz, stiff = outside ? kSpring : 0.0;
        Vec3 vel = velocity(sl);
        double pullF = (outside ? kSpring * (dist - r) : 0.0) + damp * Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        tension = (float) Mth.clamp(pullF / cap, 0.0, 1.0);
        if (AnchorHold.jointOk()) {
            if (!AnchorHold.valid(joint)) {
                joint = null;
                if (now - lastJoin >= Balance.ANCHOR_REJOIN_TICKS) { lastJoin = now; joint = AnchorHold.attach(sl, ship, anchor, h.exitPlot()); }
            }
            if (joint != null) { AnchorHold.motors(joint, tx, tz, stiff, damp, cap, m * Balance.ANCHOR_YAW_DAMP); return; }
        }
        push(sl, ex, dx - tx, dz - tz, stiff, damp, cap, m, vel);
    }

    private Vec3 velocity(ServerLevel sl) {
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        return info == null || info.linearVelocity() == null ? Vec3.ZERO : ShipDefence.velocity(sl, info).scale(20.0);
    }

    private void push(ServerLevel sl, Vec3 hawse, double ox, double oz, double stiff, double damp, double cap, double m, Vec3 vel) {
        SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
        if (info == null) return;
        double fx = -stiff * ox - damp * vel.x, fz = -stiff * oz - damp * vel.z, f = Math.sqrt(fx * fx + fz * fz);
        if (!(f > 1.0E-9)) return;
        if (f > cap) { fx *= cap / f; fz *= cap / f; }
        SableBridge.shipPush(sl, ship, new Vec3(fx / m / 20.0, 0.0, fz / m / 20.0), hawse.subtract(info.worldCenter()));
    }

    private boolean weigh(ServerLevel sl, Hawse h, long now, boolean busbar) {
        boolean paid = busbar || (ship != null && ShipPower.draw(sl, ship, Balance.ANCHOR_FE_WEIGH));
        if (!paid) { problems |= P_NO_POWER; if (onBottom) hold(sl, h, now, true); return false; }
        chainOut = Math.max(0.0F, chainOut - (float) (Balance.ANCHOR_WEIGH_SPEED / 20.0));
        if (onBottom && anchor != null) {
            double vert = Math.max(0.0, h.exitWorld().y - anchor.y);
            if (chainOut <= vert + 0.5) {
                onBottom = false;
                mud(sl, 14);
                letGo(sl);
                anchor = null;
                tension = 0.0F;
                chainOut = (float) vert;
                mech(sl, WmdSounds.ANCHOR_BRAKE.get(), 0.8F);
            } else hold(sl, h, now, true);
        }
        if (chainOut <= 0.0F) { setState(sl, S_STOWED); mech(sl, WmdSounds.ANCHOR_HOME.get(), 1.0F); if (ship != null) Anchors.withdraw(sl, ship, worldPosition); }
        return busbar;
    }

    private void letGo(ServerLevel sl) {
        if (joint != null) { if (SableBridge.active()) AnchorHold.release(joint); joint = null; }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            letGo(sl);
            if (ship != null) Anchors.withdraw(sl, ship, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel sl) letGo(sl);
        super.onChunkUnloaded();
    }

    static void clientTick(Level level, BlockPos pos, BlockState st, AnchorWindlassBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, st, be);
        if (!AirGate.on()) return;
        int s = be.state;
        if (s == S_DROPPING) MachineLoop.ensure(be, WmdSounds.ANCHOR_CHAIN_OUT.get(), () -> be.state == S_DROPPING && !be.isRemoved(), 0.5, Balance.SND_MACHINE, false);
        else if (s == S_WEIGHING && (be.problems & P_NO_POWER) == 0) MachineLoop.ensure(be, WmdSounds.ANCHOR_WEIGH.get(), () -> be.state == S_WEIGHING && (be.problems & P_NO_POWER) == 0 && !be.isRemoved(), 0.5, Balance.SND_MACHINE, true);
        else if (s == S_DRAGGING) MachineLoop.ensure(be, WmdSounds.ANCHOR_DRAG.get(), () -> be.state == S_DRAGGING && !be.isRemoved(), 0.5, Balance.SND_MACHINE, false);
    }

    float shownChain() { return shownChain; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT);
            float want = chainOut;
            float maxStep = (float) (Math.max(Balance.ANCHOR_DROP_SPEED, Balance.ANCHOR_WEIGH_SPEED) / 20.0 * 1.6 * dt);
            float d = Mth.clamp(want - shownChain, -maxStep, maxStep);
            if (Math.abs(want - shownChain) > 6.0F) d = want - shownChain;
            shownChain += d;
            travel += d;
            boolean released = state == S_DROPPING || state == S_WEIGHING;
            float k = (float) Math.min(1.0, dt / 8.0);
            brakeT += ((released ? 1.0F : 0.0F) - brakeT) * k;
            stopT += ((released ? 1.0F : 0.0F) - stopT) * k;
            leverT += ((state == S_DROPPING ? -1.0F : state == S_WEIGHING ? 1.0F : 0.0F) - leverT) * k;
        }
        if (Double.isNaN(lastT) || tt > lastT) { if (Double.isNaN(lastT)) shownChain = chainOut; lastT = tt; }
        float px = travel * 16.0F;
        final float gyp = (float) (px / GYPSY_R) * GYPSY_SIGN;
        float sh = px % PITCH;
        if (sh < 0) sh += PITCH;
        final float slide = sh * CHAIN_SIGN;
        final float brake = (float) Math.toRadians(BRAKE_OFF * brakeT) * ROT_SIGN, wheel = (float) Math.toRadians(HANDWHEEL_TURNS * brakeT) * ROT_SIGN;
        final float stop = (float) Math.toRadians(STOPPER_UP * stopT) * ROT_SIGN, lever = (float) Math.toRadians(LEVER * leverT) * ROT_SIGN;
        model.getBone("gypsy").ifPresent(b -> b.setRotX(gyp));
        model.getBone("chainf").ifPresent(b -> { b.setPosY(-slide * RUN_DY); b.setPosZ(-slide * RUN_DZ); });
        model.getBone("chainb").ifPresent(b -> b.setPosY(slide));
        model.getBone("brake").ifPresent(b -> b.setRotX(brake));
        model.getBone("handwheel").ifPresent(b -> b.setRotY(wheel));
        model.getBone("stopper").ifPresent(b -> b.setRotX(stop));
        model.getBone("lever").ifPresent(b -> b.setRotX(lever));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.ANCHOR_WEIGH.get(); }
    @Override protected boolean soundAlive() { return false; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    static String stateKey(int s) {
        return switch (s) {
            case S_DROPPING -> "dropping";
            case S_HOLDING -> "holding";
            case S_DRAGGING -> "dragging";
            case S_WEIGHING -> "weighing";
            case S_NO_BOTTOM -> "no_bottom";
            default -> "stowed";
        };
    }

    @Override
    protected String detail() { return stateKey(state) + ", chain " + Math.round(chainOut * 10) / 10.0 + ", pipes " + pipes + ", joint " + (joint != null) + ", ship " + ship; }

    @Override
    public Component status() {
        Component c = Component.translatable("block.wmd.anchor_windlass").append(": ").append(Component.translatable("gui.wmd.anchor.st_" + stateKey(state)));
        if ((problems & P_BLOCKED) != 0) c = c.copy().append(" (").append(Component.translatable("gui.wmd.anchor.p_blocked")).append(")");
        return c;
    }

    private void writeState(CompoundTag tag) {
        tag.putInt("awS", state);
        tag.putInt("awP", problems);
        tag.putFloat("awC", chainOut);
        tag.putInt("awN", pipes);
        tag.putBoolean("awB", onBottom);
        if (anchor != null) { tag.putDouble("awAx", anchor.x); tag.putDouble("awAy", anchor.y); tag.putDouble("awAz", anchor.z); }
        tag.putFloat("awW", waterDepth);
        tag.putFloat("awD", seaDist);
        tag.putFloat("awT", tension);
    }

    private void readState(CompoundTag tag) {
        if (!tag.contains("awS")) return;
        state = Mth.clamp(tag.getInt("awS"), S_STOWED, S_NO_BOTTOM);
        problems = tag.getInt("awP");
        chainOut = Mth.clamp(tag.getFloat("awC"), 0.0F, Balance.ANCHOR_CHAIN_MAX);
        pipes = Mth.clamp(tag.getInt("awN"), 0, Balance.ANCHOR_PIPES_MAX);
        onBottom = tag.getBoolean("awB");
        anchor = tag.contains("awAx") ? new Vec3(tag.getDouble("awAx"), tag.getDouble("awAy"), tag.getDouble("awAz")) : null;
        if (onBottom && anchor == null) onBottom = false;
        waterDepth = tag.getFloat("awW");
        seaDist = tag.getFloat("awD");
        tension = tag.getFloat("awT");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        writeState(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        readState(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        writeState(tag);
        return tag;
    }
}
