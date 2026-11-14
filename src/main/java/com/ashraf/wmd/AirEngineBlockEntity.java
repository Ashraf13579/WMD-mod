package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public abstract class AirEngineBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, ShipEngine {

    static final int STOP = Balance.PROP_STEP_STOP;

    static final float RX = 1.0F, RY = 1.0F, RZ = 1.0F;

    protected boolean nacelle;
    private BlockState copy;
    private ItemStack copyItem = ItemStack.EMPTY;

    protected int step = STOP, source = -1, redstone;
    protected double throttle;
    protected boolean afterburner;
    protected float rudderOrd, planesOrd, rollOrd;
    protected UUID ship;
    protected double pressure = 1.0, vFwd, vShip;

    protected boolean belly;
    protected boolean dry = true;
    protected int rollSide;
    protected volatile double[] force;

    protected int hp = Balance.ENGINE_HP;

    private static final Set<AirEngineBlockEntity> LIVE = Statics.server(new HashSet<>());

    protected boolean cNacelle;
    protected int cHp = Balance.ENGINE_HP;
    private Vec3 cLastExhaust;
    protected BlockState cCopy;

    protected AirEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fePerTick, int bufferFe, int receivePerTick) {
        super(type, pos, state, fePerTick, bufferFe, receivePerTick);
    }

    protected Direction front() { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.NORTH); }
    protected Direction localEast() { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.EAST); }
    protected Direction localUp() { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.UP); }

    static Vec3 unit(Direction d) { return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }

    protected abstract double[] intakeLocal();

    protected abstract double[] thrustLocal();

    protected abstract double airflow();

    protected boolean rocket() { return false; }

    protected void orders(ServerLevel sl) {

        if (EmpField.jammed(sl, worldPosition)) return;
        if (sl.getGameTime() % 10 == (worldPosition.asLong() & 7)) redstone = redstoneIn(sl);
        ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);
        rudderOrd = o == null ? 0.0F : o.rudder();
        planesOrd = o == null ? 0.0F : o.planes();
        rollOrd = o == null ? 0.0F : o.roll();
        if (redstone > 0) { throttle = redstone / 15.0; step = ShipControl.stepOf(throttle); source = 0; }
        else if (o != null) { throttle = o.throttle(); step = o.step(); source = o.source(); }
        else { throttle = 0.0; step = STOP; source = -1; }
        boolean ab = redstone == 0 && o != null && o.afterburner();
        if (ab != afterburner) { afterburner = ab; syncNow(); }
    }

    protected int redstoneIn(ServerLevel sl) {
        int best = 0;
        Direction f = facing(), m = Machines.mount(getBlockState());
        if (getBlockState().getBlock() instanceof MachineBlock mb)
            for (BlockPos l : mb.footprint()) best = Math.max(best, sl.getBestNeighborSignal(Machines.toWorld(worldPosition, f, m, l.getX(), l.getY(), l.getZ())));
        return best;
    }

    protected void airBeat(ServerLevel sl) {
        if (damageable() && !isRemoved()) LIVE.add(this);
        ship = SableBridge.shipIdAt(sl, worldPosition);
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        double[] in = intakeLocal();
        double[] w = worldPoint(in[0], in[1], in[2]);
        dry = !sl.getFluidState(BlockPos.containing(w[0], w[1], w[2])).is(FluidTags.WATER);

        pressure = rocket() ? 1.0 : SableAir.pressure(sl, w[0], w[1], w[2]);
        vFwd = 0.0;
        vShip = 0.0;
        rollSide = 0;
        belly = ship != null && !rocket() && AirBelly.sliding(sl, ship);
        if (info != null) {
            if (info.linearVelocity() != null) vShip = ShipDefence.velocity(sl, info).length() * 20.0;
            Vec3 c = Vec3.atCenterOf(worldPosition), f = unit(front());
            Vec3 a = SableBridge.toWorld(sl, c), b = SableBridge.toWorld(sl, c.add(f));
            if (a != null && b != null && info.linearVelocity() != null) vFwd = info.linearVelocity().dot(b.subtract(a).normalize());
            Vec3 centre = SableBridge.toShip(sl, info, info.worldCenter());
            Vec3 starboard = unit(Direction.UP).cross(f).scale(-1.0);
            if (centre != null && starboard.lengthSqr() > 0.5) {
                double s = c.subtract(centre).dot(starboard);
                rollSide = s > 0.75 ? 1 : s < -0.75 ? -1 : 0;
            }
        }
    }

    static double airPressure(Level level, double y) {
        return SableAir.eval(SableAir.pointsFor(level), y);
    }

    protected double airShare(double thrustSign) {
        if (ship == null || !dry) return 0.0;
        double a = airflow();
        double flow = a <= 0.0 ? 1.0 : Mth.clamp(1.0 - Math.signum(thrustSign) * vFwd / a, 0.0, 1.0);
        return pressure * flow * speedCapShare(Math.signum(thrustSign) * vFwd, vShip) * bellyShare(Math.signum(thrustSign) * vFwd);
    }

    protected double bellyShare(double along) {
        if (!belly || !(along > 0.0)) return 1.0;
        return Mth.clamp((Balance.BELLY_MAX - along) / Balance.BELLY_FADE, 0.0, 1.0);
    }

    static double speedCapShare(double along, double shipSpeed) {
        if (!(along > 0.0) || !Double.isFinite(shipSpeed)) return 1.0;
        double cap = Balance.AIRCRAFT_MAX_SPEED, fade = Balance.AIRCRAFT_CAP_FADE;
        return Mth.clamp((cap - shipSpeed) / fade, 0.0, 1.0);
    }

    protected double[] steerLocal(boolean withRoll) {
        Vec3 f = unit(front()), up = unit(Direction.UP);
        Vec3 port = up.cross(f);
        Vec3 upPerp = up.subtract(f.scale(up.dot(f)));
        if (port.lengthSqr() < 0.25) return new double[] { 0.0, 0.0 };
        Vec3 l = port.scale(rudderOrd).add(upPerp.scale(planesOrd));
        if (withRoll && rollSide != 0) l = l.add(upPerp.scale(-rollSide * rollOrd));
        Vec3 ex = unit(localEast()), ey = unit(localUp());
        return new double[] { Mth.clamp(l.dot(ex), -1.0, 1.0), Mth.clamp(l.dot(ey), -1.0, 1.0) };
    }

    static double devThrustScale = 1.0;

    protected void setForce(double along, double sideX, double sideY) {
        double dmg = damageShare();
        if (contrails() && ship != null && AirGate.on() && LiftFanBlockEntity.lifting(ship)) dmg *= Balance.LIFT_ENGINE_SHARE;
        along *= devThrustScale * dmg; sideX *= devThrustScale * dmg; sideY *= devThrustScale * dmg;
        if (ship == null || (Math.abs(along) < 1.0E-4 && Math.abs(sideX) < 1.0E-4 && Math.abs(sideY) < 1.0E-4)) { force = null; return; }
        Vec3 f = unit(front()).scale(along).add(unit(localEast()).scale(sideX)).add(unit(localUp()).scale(sideY));
        double[] t = thrustLocal();
        Vec3 p = plotPoint(t[0], t[1], t[2]);
        force = new double[] { p.x, p.y, p.z, f.x, f.y, f.z };
    }

    @Override public double[] engineForce() { return force; }

    protected void report(ServerLevel sl, double share, boolean running, double rudderDeg, double planesDeg) {
        if (ship != null) ShipControl.answer(sl, ship, worldPosition, ShipControl.stepOf(share), running, share, rudderDeg, planesDeg);
        double h = running ? heatSignature() : 0.0;
        if (ship != null && h > 0.0) AirHeat.report(sl, ship, h, sl.getGameTime(), exhaustWorld(sl));
        seaNoise(sl, running && share > 0.05);
    }

    protected Vec3 exhaustWorld(ServerLevel sl) {
        Vec3 c = Vec3.atCenterOf(worldPosition), f = unit(front());
        Vec3 a = SableBridge.toWorld(sl, c), b = SableBridge.toWorld(sl, c.add(f));
        return a == null || b == null ? null : a.subtract(b);
    }

    protected double heatSignature() { return 0.0; }

    protected double noiseDb() { return Balance.AIR_NOISE_DB_FAN; }

    private void seaNoise(ServerLevel sl, boolean running) {
        if (!running) { NoiseNet.silence(sl, this); return; }
        double[] w = worldPoint(0.0, 0.5, 0.0);
        int x = Mth.floor(w[0]), z = Mth.floor(w[2]);
        int top = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos surface = new BlockPos(x, top - 1, z);
        if (w[1] - top <= Balance.AIR_NOISE_AGL && sl.getFluidState(surface).is(FluidTags.WATER))
            NoiseNet.emit(sl, this, Vec3.atCenterOf(surface), noiseDb(), NoiseNet.Kind.ENGINE, null, ship);
        else NoiseNet.silence(sl, this);
    }

    protected static float approach(float cur, float tgt, float max) { return ScrewPropellerBlockEntity.approach(cur, tgt, max); }

    protected boolean damageable() { return true; }

    protected double hitSize() { return 1.0; }

    public int damageStage() {
        int h = level != null && level.isClientSide ? cHp : hp;
        double f = h / (double) Balance.ENGINE_HP;
        return h <= 0 ? 3 : f < Balance.ENGINE_FIRE ? 2 : f < Balance.ENGINE_SMOKE ? 1 : 0;
    }

    public double damageShare() {
        return switch (damageStage()) { case 1 -> Balance.ENGINE_SMOKE_THRUST; case 2 -> Balance.ENGINE_FIRE_THRUST; case 3 -> 0.0; default -> 1.0; };
    }

    public int hp() { return level != null && level.isClientSide ? cHp : hp; }

    void damage(ServerLevel sl, double amount) {
        if (!damageable() || amount <= 0.0 || hp <= 0) return;
        int before = damageStage();
        boolean first = hp >= Balance.ENGINE_HP;
        hp = Math.max(0, hp - (int) Math.ceil(amount));
        int now = damageStage();
        if (now == before) { setChanged(); if (first) syncNow(); return; }
        double[] t = thrustLocal();
        double[] w = worldPoint(t[0], t[1], t[2]);
        if (now == 2) sl.playSound(null, w[0], w[1], w[2], net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 0.8F);
        if (now == 3) sl.playSound(null, w[0], w[1], w[2], net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 0.6F);
        Wmd.LOGGER.info("Air engine at {}: hit, {} HP, stage {}", worldPosition.toShortString(), hp, now);
        syncNow();
    }

    boolean repair() {
        if (hp >= Balance.ENGINE_HP) return false;
        hp = Balance.ENGINE_HP;
        syncNow();
        return true;
    }

    boolean atRest() { return ship == null || vShip < Balance.ENGINE_REPAIR_SPEED; }

    static List<AirEngineBlockEntity> live(ServerLevel sl) {
        List<AirEngineBlockEntity> out = new ArrayList<>();
        LIVE.removeIf(e -> e.isRemoved() || e.level == null);
        for (AirEngineBlockEntity e : LIVE) if (e.level == sl) out.add(e);
        return out;
    }

    private Vec3 hitPoint() { double[] t = thrustLocal(); double[] w = worldPoint(t[0], t[1], t[2]); return new Vec3(w[0], w[1], w[2]); }

    static int[] systemsAboard(ServerLevel sl, UUID ship) {
        int[] out = { 0, 0, 0, 0, 0 };
        if (sl == null || ship == null) return out;
        for (AirEngineBlockEntity e : live(sl)) {
            if (!ship.equals(e.ship) || !e.damageable()) continue;
            int st = e.damageStage();
            if (st > 0) { out[1]++; out[0] = Math.max(out[0], st); }
            if (e instanceof HeliRotorBlockEntity r && out[2] == 0) { out[2] = r.bladeCount(); out[3] = r.bladeLength(); out[4] = Math.round(r.spoolNow() * 100.0F); }
        }
        return out;
    }

    static boolean jetRunning(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null) return false;
        for (AirEngineBlockEntity e : live(sl)) if (ship.equals(e.ship) && e.contrails() && e.damageStage() < 3 && Math.abs(e.throttle) > 0.02) return true;
        return false;
    }

    static void blastAt(ServerLevel sl, Vec3 pos, double radius, float power) {
        if (sl == null || pos == null || power <= 0.0F || LIVE.isEmpty()) return;
        for (AirEngineBlockEntity e : live(sl)) {
            double reach = radius + Balance.ENGINE_BLAST_PAD + e.hitSize();
            double d = e.hitPoint().distanceTo(pos);
            if (d < reach) e.damage(sl, Balance.ENGINE_BLAST_DMG * power * (1.0 - d / reach));
        }
    }

    static void rayAt(ServerLevel sl, Vec3 from, Vec3 to, int blocks) {
        if (sl == null || from == null || to == null || blocks <= 0 || LIVE.isEmpty()) return;
        Vec3 d = to.subtract(from);
        double len2 = d.lengthSqr();
        if (len2 < 1.0E-9) return;
        for (AirEngineBlockEntity e : live(sl)) {
            Vec3 c = e.hitPoint();
            double k = Mth.clamp(c.subtract(from).dot(d) / len2, 0.0, 1.0);
            if (c.distanceTo(from.add(d.scale(k))) <= Balance.ENGINE_RAY_REACH + e.hitSize()) e.damage(sl, Balance.ENGINE_RAY_DMG * blocks);
        }
    }

    @Override
    public void setRemoved() {
        LIVE.remove(this);
        super.setRemoved();
    }

    private static void wisp(Level lv, double x, double y, double z, float size) {
        WmdSmokeParticle.nextSize = size;
        try { lv.addParticle(WmdParticles.TRAIL_SMOKE.get(), x, y, z, 0.0, 0.02, 0.0); }
        finally { WmdSmokeParticle.nextSize = 1.0F; }
    }

    protected void leakFx(Level lv, double x, double y, double z) {
        var r = lv.random;
        double[] w = { x, y, z };
        wisp(lv, w[0] + (r.nextDouble() - 0.5) * 0.4, w[1] + 0.2, w[2] + (r.nextDouble() - 0.5) * 0.4, (float) Balance.ENGINE_SMOKE_SIZE);
    }

    protected boolean contrails() { return false; }

    @Override protected boolean cabinSound() { return true; }

    protected boolean runningClient() { return cStep != STOP; }

    protected boolean afterburnerClient() { return cAb && runningClient(); }

    protected boolean hotExhaust() { return false; }

    protected double[] exhaustLocal() { return thrustLocal(); }

    private void damageFx(Level lv) {
        double[] t = thrustLocal();
        double[] w = worldPoint(t[0], t[1], t[2]);
        Vec3 here = new Vec3(w[0], w[1], w[2]);
        Vec3 last = cLastExhaust;
        cLastExhaust = here;
        int stage = damageStage();
        var r = lv.random;

        if (stage == 1 && lv.getGameTime() % 3 == 0) leakFx(lv, w[0], w[1], w[2]);
        if (stage == 2) {
            lv.addParticle(WmdParticles.ROCKET_FLAME.get(), w[0] + (r.nextDouble() - 0.5) * 0.6, w[1] + 0.2 + r.nextDouble() * 0.3, w[2] + (r.nextDouble() - 0.5) * 0.6, 0.0, 0.03, 0.0);
            if (lv.getGameTime() % 2 == 0) wisp(lv, w[0], w[1] + 0.4, w[2], (float) Balance.ENGINE_FIRE_SMOKE_SIZE);
        }
        if (stage == 3 && lv.getGameTime() % 5 == 0) wisp(lv, w[0], w[1] + 0.3, w[2], (float) Balance.ENGINE_SMOKE_SIZE);
        if (hotExhaust() && stage < 3 && runningClient() && lv.getGameTime() % 2 == 0) { double[] e = exhaustLocal(); AirFx.haze(lv, worldPoint(e[0], e[1], e[2])); }

        if (!contrails() || stage == 3 || last == null || w[1] < Balance.CONTRAIL_Y || !runningClient() || lv.getGameTime() % Balance.CONTRAIL_EVERY != 0) return;
        double speed = here.distanceTo(last) * 20.0;
        if (speed < Balance.CONTRAIL_SPEED || speed > 400.0) return;
        WmdSmokeParticle.nextSize = afterburnerClient() ? (float) Balance.CONTRAIL_AB_SIZE : 1.0F;

        Vec3 back = speed > 1.0E-3 ? here.subtract(last).normalize().scale(-Balance.CONTRAIL_BACK) : Vec3.ZERO;
        try { lv.addAlwaysVisibleParticle(WmdParticles.CONTRAIL.get(), true, w[0] + back.x, w[1] + back.y, w[2] + back.z, 0.0, 0.0, 0.0); }
        finally { WmdSmokeParticle.nextSize = 1.0F; }
    }

    public boolean nacelle() { return level != null && level.isClientSide ? cNacelle : nacelle; }

    void toggleVariant() { nacelle = !nacelle; syncNow(); }

    public BlockState copyShown() { return cCopy; }

    ItemStack setCopy(BlockState st, ItemStack used) {
        if (st == null ? copy == null : st.equals(copy)) return null;
        ItemStack back = copyItem;
        copy = st;
        copyItem = st == null || used.isEmpty() ? ItemStack.EMPTY : used.copyWithCount(1);
        syncNow();
        return back;
    }

    boolean interactExtra(Player player, ItemStack held) { return false; }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        if (!copyItem.isEmpty()) { l.add(copyItem); copyItem = ItemStack.EMPTY; }
        return l;
    }

    protected void syncNow() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    static void clientTickAir(Level lv, BlockPos pos, BlockState state, AirEngineBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
        be.easeTick(lv);
        be.damageFx(lv);
    }

    protected abstract void easeTick(Level lv);

    static Component sourceName(int src) { return ScrewPropellerBlockEntity.sourceName(src); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putShared(tag);
        if (!copyItem.isEmpty()) tag.put("aeCopyItem", copyItem.save(registries));
    }

    private void putShared(CompoundTag tag) {
        tag.putBoolean("aeNacelle", nacelle);
        if (copy != null) tag.put("aeCopy", net.minecraft.nbt.NbtUtils.writeBlockState(copy));
        tag.putInt("aeStep", step);
        tag.putInt("aeSrc", source);
        tag.putInt("aeHp", hp);
        tag.putBoolean("aeAb", afterburner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockState cp = tag.contains("aeCopy") ? net.minecraft.nbt.NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("aeCopy")) : null;
        if (cp != null && cp.isAir()) cp = null;
        cCopy = cp;
        cNacelle = tag.getBoolean("aeNacelle");
        cStep = ShipControl.clampStep(tag.contains("aeStep") ? tag.getInt("aeStep") : STOP);
        cSource = tag.getInt("aeSrc");
        cHp = tag.contains("aeHp") ? tag.getInt("aeHp") : Balance.ENGINE_HP;
        cAb = tag.getBoolean("aeAb");
        if (level != null && level.isClientSide) return;
        hp = cHp;
        nacelle = cNacelle;
        copy = cp;
        copyItem = tag.contains("aeCopyItem") ? ItemStack.parseOptional(registries, tag.getCompound("aeCopyItem")) : ItemStack.EMPTY;
    }

    protected int cStep = STOP, cSource = -1;
    protected boolean cAb;

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putShared(tag);
        return tag;
    }
}
