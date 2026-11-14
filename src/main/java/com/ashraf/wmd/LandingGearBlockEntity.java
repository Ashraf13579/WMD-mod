package com.ashraf.wmd;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class LandingGearBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int NOSE = 0, MAIN = 1, TWIN = 2, CANTED = 3;

    public static final int SMALL = 0, MEDIUM = 1, LARGE = 2;

    public static final int BOGIE = 3;

    static final float LEG_SIGN = 1.0F, DOOR_SIGN = 1.0F;

    static final float BEAM_SIGN = 1.0F;

    static final int TANDEM_SIGN = 1;

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> GEARS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int role = MAIN;
    private int size = SMALL;

    private int leanX = -1;

    private boolean ordered = true, sig;

    private int ext = Balance.GEAR_CYCLE_TICKS;
    private long brakeUntil, chirpAt;
    private UUID ship;

    volatile boolean wow;
    volatile float comp, rollSpeed;

    volatile float steer;

    volatile double share = 1.0;
    volatile boolean parked = true;

    volatile net.minecraft.world.phys.Vec3 towHand;
    volatile boolean towOnShip;
    volatile double towLen = Balance.TOW_LEN_MIN;
    volatile long reverseUntil, driveUntil;

    volatile net.minecraft.world.phys.Vec3 reverseNose;

    private UUID towBy;
    private int drive, driveShown;
    private long workedAt = Long.MIN_VALUE;
    private boolean wowShown;
    private int airTicks;

    private double lastT = Double.NaN, wheelAngle;
    private float extShown, compShown, rollShown, steerShown, tiltShown;

    public LandingGearBlockEntity(BlockPos pos, BlockState state) {
        super(state.is(AirBlocks.LANDING_GEAR_LARGE.get()) ? AirBlocks.LANDING_GEAR_LARGE_BE.get() : state.is(AirBlocks.LANDING_GEAR_BOGIE.get()) ? AirBlocks.LANDING_GEAR_BOGIE_BE.get()
                : AirBlocks.LANDING_GEAR_BE.get(), pos, state, 0, 1, 0);
        if (state.is(AirBlocks.LANDING_GEAR_LARGE.get())) size = LARGE;
        if (state.is(AirBlocks.LANDING_GEAR_BOGIE.get())) size = BOGIE;
    }

    public int size() { return size; }
    boolean large() { return size == LARGE; }
    boolean bogie() { return size == BOGIE; }

    boolean fixedSize() { return size == LARGE || size == BOGIE; }
    boolean canted() { return role == CANTED && !fixedSize(); }

    boolean tandem() { return bogie() && role == MAIN; }

    void cycleSize() { if (!fixedSize()) { size = size == SMALL ? MEDIUM : SMALL; sync(); } }

    int tyres() {
        if (bogie()) return role == TWIN ? 4 : 2;
        if (role == TWIN && !large()) return size == SMALL ? 3 : 2;
        return 1;
    }

    double loadFactor() { return Balance.GEAR_LOAD_PER_TYRE[size] * tyres(); }

    String roleKey() { return bogie() ? "message.wmd.gear.bogie_role" + role : "message.wmd.gear.role" + role; }

    double reach() {
        float axle = Balance.GEAR_AXLE_PX[size], r = Balance.GEAR_R_PX[size];
        if (!canted()) return (axle + r) / 16.0;
        double a = Math.toRadians(Balance.GEAR_CANT_DEG), pv = Balance.GEAR_CANT_PIVOT_PX[size], below = axle + pv;
        return (below * Math.cos(a) - pv + r) / 16.0;
    }

    double sideOffset() {
        double x = Balance.GEAR_SIDE_PX[size];
        if (canted()) x += leanX * (Balance.GEAR_AXLE_PX[size] + Balance.GEAR_CANT_PIVOT_PX[size]) * Math.sin(Math.toRadians(Balance.GEAR_CANT_DEG));
        return x / 16.0;
    }

    double aftOffset() { return Balance.GEAR_AFT_PX[size] / 16.0; }

    Direction aftDir() {
        BlockState st = getBlockState();
        BlockPos o = Machines.toWorld(BlockPos.ZERO, facing(), Machines.mount(st), 0, 0, 1);
        return Direction.getNearest(o.getX(), o.getY(), o.getZ());
    }

    Direction sideDir() {
        BlockState st = getBlockState();
        BlockPos o = Machines.toWorld(BlockPos.ZERO, facing(), Machines.mount(st), 1, 0, 0);
        return Direction.getNearest(o.getX(), o.getY(), o.getZ());
    }

    double wheelR() { return Balance.GEAR_R_PX[size] / 16.0; }

    public int role() { return role; }

    public boolean locked() { return ext >= Balance.GEAR_CYCLE_TICKS; }
    public boolean up() { return ext <= 0; }
    public boolean braking() { return role != NOSE && level != null && level.getGameTime() < brakeUntil; }
    public UUID ship() { return ship; }

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

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void cycleRole() {
        role = bogie() ? (role == MAIN ? TWIN : MAIN) : (role + 1) % (large() ? 2 : 4);
        sync();
    }

    private static List<LandingGearBlockEntity> gearsOf(ServerLevel level, UUID ship) {
        List<LandingGearBlockEntity> out = new java.util.ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = GEARS.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof LandingGearBlockEntity g && !g.isRemoved()) out.add(g);
        }
        return out;
    }

    public static String setAll(ServerLevel level, UUID ship, boolean down) {
        List<LandingGearBlockEntity> l = gearsOf(level, ship);
        if (l.isEmpty()) return "message.wmd.gear.none";
        if (!down) for (LandingGearBlockEntity g : l) if (g.wow) return "message.wmd.gear.wow";
        for (LandingGearBlockEntity g : l) if (g.ordered != down) { g.ordered = down; g.setChanged(); }
        return down ? "message.wmd.gear.down" : "message.wmd.gear.up";
    }

    public static String toggleAll(ServerLevel level, UUID ship) {
        boolean anyUp = false;
        for (LandingGearBlockEntity g : gearsOf(level, ship)) if (!g.ordered) anyUp = true;
        return setAll(level, ship, anyUp);
    }

    public static void brakes(ServerLevel level, UUID ship, int holdTicks) {
        long until = level.getGameTime() + Math.max(1, Math.min(holdTicks, 20));
        for (LandingGearBlockEntity g : gearsOf(level, ship)) g.brakeUntil = Math.max(g.brakeUntil, until);
    }

    static void drive(ServerLevel level, UUID ship, int holdTicks) {
        long until = level.getGameTime() + Math.max(1, Math.min(holdTicks, 20));
        for (LandingGearBlockEntity g : gearsOf(level, ship)) g.driveUntil = Math.max(g.driveUntil, until);
        if (level.getGameTime() % 10 == 0) SableBridge.wake(level, ship);
    }

    static boolean reverse(ServerLevel level, UUID ship, int holdTicks, net.minecraft.world.phys.Vec3 nosePlot) {
        if (nosePlot == null) return false;
        long until = level.getGameTime() + Math.max(1, Math.min(holdTicks, 20));
        boolean any = false;
        for (LandingGearBlockEntity g : gearsOf(level, ship))
            if (g.role == NOSE && g.locked()) { g.reverseNose = nosePlot; g.reverseUntil = Math.max(g.reverseUntil, until); any = true; }
        if (any) drive(level, ship, holdTicks);
        return any;
    }

    boolean reversing() { return level != null && role == NOSE && level.getGameTime() < reverseUntil; }

    public boolean towed() { return towHand != null; }

    public UUID towBy() { return towBy; }
    public double towLen() { return towLen; }

    void setTowBy(UUID u) {
        towBy = u;
        if (u == null) towHand = null;
        sync();
    }

    private boolean hardLanding(ServerLevel sl, long now) {
        if (!Config.Server.hardLandingBreaksGear() || !WmdGameRules.crashDamage(sl) || ship == null) return false;
        double sink = AirCrash.sinkRate(ship, now);
        if (sink <= Balance.GEAR_SINK_BY_SIZE[size]) return false;
        double[] w = worldPoint(0.0, -0.6, 0.0);
        sl.playSound(null, w[0], w[1], w[2], WmdSounds.HE_DEBRIS.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 1.3F);
        Wmd.LOGGER.info("Landing gear at {}: hard landing at {} b/s sink, broke off", worldPosition.toShortString(), String.format("%.1f", sink));
        sl.destroyBlock(worldPosition, false);
        return true;
    }

    public static int stateAboard(ServerLevel level, UUID ship) {
        List<LandingGearBlockEntity> l = gearsOf(level, ship);
        if (l.isEmpty()) return -1;
        boolean allUp = true, allLocked = true, wow = false;
        for (LandingGearBlockEntity g : l) { allUp &= g.up(); allLocked &= g.locked(); wow |= g.wow; }
        return (allLocked ? 2 : allUp ? 0 : 1) + (wow ? 10 : 0);
    }

    static List<LandingGearBlockEntity> gearsNear(ServerLevel level, net.minecraft.world.phys.Vec3 p, double r) {
        List<LandingGearBlockEntity> out = new java.util.ArrayList<>();
        Map<UUID, Set<Long>> m = GEARS.get(level.dimension());
        if (m == null) return out;
        for (Set<Long> s : m.values())
            for (Long q : s) {
                BlockPos bp = BlockPos.of(q);
                if (!level.isLoaded(bp) || !(level.getBlockEntity(bp) instanceof LandingGearBlockEntity g) || g.isRemoved()) continue;
                net.minecraft.world.phys.Vec3 w = g.wheelWorld(level);
                if (w != null && w.distanceToSqr(p) <= r * r) out.add(g);
            }
        return out;
    }

    net.minecraft.world.phys.Vec3 wheelWorld(ServerLevel level) {
        if (!locked()) return null;
        Direction l = legDir();
        double d = 0.5 + reach() - comp * Balance.GEAR_STROKE, so = sideOffset(), ao = aftOffset();
        Direction sd = sideDir(), ad = aftDir();
        return SableBridge.toWorld(level, net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(l.getStepX() * d + sd.getStepX() * so + ad.getStepX() * ao,
                l.getStepY() * d + sd.getStepY() * so + ad.getStepY() * ao, l.getStepZ() * d + sd.getStepZ() * so + ad.getStepZ() * ao));
    }

    static boolean onWheels(ServerLevel level, UUID ship) {
        for (LandingGearBlockEntity g : gearsOf(level, ship)) if (g.wow) return true;
        return false;
    }

    static boolean orderedDown(ServerLevel level, UUID ship) {
        for (LandingGearBlockEntity g : gearsOf(level, ship)) if (g.ordered) return true;
        return false;
    }

    static int lockedAboard(ServerLevel level, UUID ship) {
        int n = 0;
        for (LandingGearBlockEntity g : gearsOf(level, ship)) if (g.locked()) n++;
        return n;
    }

    static double loadAboard(ServerLevel level, UUID ship) {
        double w = 0.0;
        for (LandingGearBlockEntity g : gearsOf(level, ship)) if (g.locked()) w += g.loadFactor();
        return w;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); return false; }
        long now = sl.getGameTime();
        if (now == workedAt) return drive != 0;
        workedAt = now;
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; }
            if (ship != null) GEARS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
            if ((canted() || tandem()) && ship != null) {
                SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
                net.minecraft.world.phys.Vec3 c = info == null ? null : SableBridge.toShip(sl, info, info.worldCenter());
                if (c != null) {
                    Direction sd = sideDir();
                    net.minecraft.world.phys.Vec3 d = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).subtract(c);
                    double dot = d.x * sd.getStepX() + d.y * sd.getStepY() + d.z * sd.getStepZ();
                    int lx = dot >= 0.0 ? 1 : -1;
                    if (lx != leanX) { leanX = lx; sync(); }
                }
            }
        }
        sig = sl.hasNeighborSignal(worldPosition);
        boolean want = ordered || sig;
        if (!want && wow) want = true;
        int was = ext;
        if (want && ext < Balance.GEAR_CYCLE_TICKS) ext++;
        else if (!want && ext > 0) ext--;
        if (was != ext) {
            if (was == 0 || was == Balance.GEAR_CYCLE_TICKS) playOnce(sl, WmdSounds.GEAR_CYCLE.get(), Balance.SND_MECH, 1.0F);
            if (ext == Balance.GEAR_CYCLE_TICKS) { playOnce(sl, WmdSounds.GEAR_LOCK.get(), Balance.SND_CLICK, 1.0F); sl.updateNeighborsAt(worldPosition, getBlockState().getBlock()); }
            if (was == Balance.GEAR_CYCLE_TICKS) sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            if (ext == 0 || ext == Balance.GEAR_CYCLE_TICKS || ext % 5 == 0) sync();
        }
        ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);

        if (towHand == null) steer = role == NOSE && o != null ? Math.max(-1.0F, Math.min(1.0F, Balance.GEAR_STEER_SENSE * o.rudder())) : 0.0F;
        parked = (o == null || o.throttle() < 0.05) && now >= driveUntil;
        if (ship != null && now % 20 == 0) { double sum = loadAboard(sl, ship); share = sum > 0.0 ? Math.min(1.0, loadFactor() / sum) : 1.0; }
        if (!locked() || ship == null) { wow = false; comp = 0.0F; rollSpeed = 0.0F; }
        int airBefore = airTicks;
        airTicks = wow ? 0 : Math.min(airTicks + 1, 10_000);
        if (wow != wowShown) {
            if (wow && airBefore >= Balance.GEAR_AIR_TICKS && hardLanding(sl, now)) return false;
            if (wow && airBefore >= Balance.GEAR_AIR_TICKS && now - chirpAt > 40) {
                playOnce(sl, WmdSounds.GEAR_TOUCHDOWN.get(), Balance.SND_MECH, 1.0F);
                chirpAt = now;
                if (Math.abs(rollSpeed) > Balance.TYRE_SMOKE_SPEED) {
                    double[] w = worldPoint(0.0, -0.6, 0.0);
                    int n = (int) Math.min(24, 6 + Math.abs(rollSpeed) * 0.3);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, w[0], w[1], w[2], n, 0.35, 0.05, 0.35, 0.03);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, w[0], w[1], w[2], n / 3, 0.25, 0.05, 0.25, 0.02);
                }
            }
            wowShown = wow;
            sync();
        } else if (wow && now % 10 == 0) sync();
        else if (towHand != null && now % 4 == 0) sync();

        int dr = towHand != null && wow && Math.abs(rollSpeed) > 0.3F ? 1 : reversing() && wow ? 2 : 0;
        if (dr != drive) { drive = dr; sync(); }
        return drive != 0;
    }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = GEARS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) leave(sl);
        if (level != null && level.isClientSide) TowBar.CLIENT_TOWED.remove(worldPosition.asLong());
        super.setRemoved();
    }

    Direction legDir() {
        BlockState st = getBlockState();
        return (st.hasProperty(MachineBlock.MOUNT) ? st.getValue(MachineBlock.MOUNT) : Direction.UP).getOpposite();
    }

    Direction rollDir() { return facing(); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT), k = Math.min(1.0, 0.35 * dt);
            extShown += (float) ((ext / (float) Balance.GEAR_CYCLE_TICKS - extShown) * Math.min(1.0, 0.5 * dt));
            compShown += (float) ((comp - compShown) * k);
            rollShown += (float) ((rollSpeed - rollShown) * k);
            steerShown += (float) ((steer - steerShown) * k);
            tiltShown += (float) (((locked() && !wow ? 1.0F : 0.0F) - tiltShown) * Math.min(1.0, 0.15 * dt));
            wheelAngle = (wheelAngle + rollShown / 20.0 * dt / wheelR()) % (Math.PI * 2.0);
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        float e = extShown;

        float doors = e < 0.3F ? e / 0.3F : 1.0F;
        float leg = e < 0.3F ? 0.0F : (e - 0.3F) / 0.7F;
        final float d = (float) Math.toRadians(85.0) * doors * DOOR_SIGN;
        final float w = (float) wheelAngle, s = (float) Math.toRadians(Balance.GEAR_STEER_DEG) * steerShown;
        final float comp16 = (float) (compShown * Balance.GEAR_STROKE * 16.0);
        model.getBone("wheel").ifPresent(b -> b.setRotX(w));
        model.getBone("fork").ifPresent(b -> b.setRotY(role == NOSE ? s : 0.0F));
        model.getBone("brake").ifPresent(b -> b.setHidden(role == NOSE));
        if (size == BOGIE) {
            model.getBone("door_l").ifPresent(b -> b.setRotZ(-d));
            model.getBone("door_r").ifPresent(b -> b.setRotZ(d));
            final float st = (1.0F - leg) * Balance.GEAR_BOGIE_STAGE * LEG_SIGN;
            model.getBone("leg").ifPresent(b -> b.setPosY(st));
            model.getBone("leg2").ifPresent(b -> b.setPosY(st));
            model.getBone("oleo").ifPresent(b -> b.setPosY(st + comp16 * LEG_SIGN));

            final float tilt = -(float) Math.toRadians(Balance.GEAR_BOGIE_TILT) * tiltShown * Math.max(0.0F, Math.min(1.0F, (leg - 0.9F) / 0.1F)) * BEAM_SIGN;
            model.getBone("beam").ifPresent(b -> b.setRotX(tilt));

            final boolean plusX = role == TWIN || leanX * TANDEM_SIGN > 0, minusX = role == TWIN || leanX * TANDEM_SIGN < 0;
            model.getBone("wheel").ifPresent(b -> { b.setRotX(w); b.setHidden(!plusX); });
            model.getBone("wheel2").ifPresent(b -> { b.setRotX(w); b.setHidden(!plusX); });
            model.getBone("wheel3").ifPresent(b -> { b.setRotX(w); b.setHidden(!minusX); });
            model.getBone("wheel4").ifPresent(b -> { b.setRotX(w); b.setHidden(!minusX); });
            return;
        }
        if (size == LARGE) {
            final float dl = d;
            model.getBone("door_n").ifPresent(b -> b.setRotX(dl));
            model.getBone("door_s").ifPresent(b -> b.setRotX(-dl));
            final float theta = 90.0F * (1.0F - leg);
            model.getBone("fold").ifPresent(b -> b.setRotZ((float) Math.toRadians(theta) * LEG_SIGN));
            model.getBone("oleo").ifPresent(b -> b.setPosY(comp16 * LEG_SIGN));
            final double[] aim = largeAim(theta), rest = largeAim(0.0F);
            model.getBone("act").ifPresent(b -> b.setRotZ((float) Math.toRadians(aim[0] - rest[0]) * LEG_SIGN));
            model.getBone("act_rod").ifPresent(b -> b.setPosY((float) (rest[1] - aim[1]) * LEG_SIGN));
            return;
        }
        model.getBone("door_l").ifPresent(b -> b.setRotZ(-d));
        model.getBone("door_r").ifPresent(b -> b.setRotZ(d));

        boolean cant = canted();
        final float lean = cant ? Math.max(0.0F, (leg - 0.7F) / 0.3F) : 0.0F;
        final float tele = cant ? Math.min(1.0F, leg / 0.7F) : leg;
        if (size == MEDIUM) {
            final float st = (1.0F - tele) * Balance.GEAR_M_STAGE * LEG_SIGN;
            model.getBone("leg").ifPresent(b -> b.setPosY(st));
            model.getBone("leg2").ifPresent(b -> b.setPosY(st));
            model.getBone("leg3").ifPresent(b -> b.setPosY(st));
            model.getBone("oleo").ifPresent(b -> b.setPosY(st + comp16 * LEG_SIGN));

            final boolean twin = role == TWIN;
            model.getBone("forkleg").ifPresent(b -> b.setHidden(twin));
            model.getBone("twin").ifPresent(b -> b.setHidden(!twin));
            model.getBone("wheel2").ifPresent(b -> { b.setRotX(w); b.setHidden(!twin); });
        } else {
            final float lu = (1.0F - tele) * Balance.GEAR_LEG_TRAVEL * LEG_SIGN, ou = ((1.0F - tele) * Balance.GEAR_OLEO_TRAVEL + comp16) * LEG_SIGN;
            model.getBone("leg").ifPresent(b -> b.setPosY(lu));
            model.getBone("oleo").ifPresent(b -> b.setPosY(ou));
            model.getBone("wheel2").ifPresent(b -> { b.setRotX(w); b.setHidden(role != TWIN); });
        }

        final float cz = (float) Math.toRadians(Balance.GEAR_CANT_DEG) * lean * leanX;
        model.getBone("cant").ifPresent(b -> b.setRotZ(cz));

        final boolean braceOn = cant && lean > 0.999F;
        model.getBone("brace").ifPresent(b -> {
            b.setHidden(!braceOn);
            b.setRotY(leanX > 0 ? (float) Math.PI : 0.0F);
            b.setPosX(leanX > 0 ? -8.8F : 0.0F);
        });
    }

    static double[] largeAim(float theta) {
        double a = Math.toRadians(theta), lx = -6.0 + 6.0 * Math.sin(a), ly = 11.0 - 6.0 * Math.cos(a);
        double vx = lx - 7.5, vy = ly - 13.6;
        return new double[] { Math.toDegrees(Math.atan2(vx, -vy)), Math.hypot(vx, vy) };
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return driveShown == 2 ? WmdSounds.NOSE_DRIVE.get() : WmdSounds.TOW_ROLL.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override
    protected String detail() {
        return new String[] { "small", "medium", "large", "bogie" }[size] + " " + (bogie() ? new String[] { "nose", "tandem", "quad", "canted" } : new String[] { "nose", "main", "twin", "canted" })[role] + (locked() ? ", DOWN" : up() ? ", up" : ", in transit") + (wow ? String.format(", on the wheels (%.0f %%, %.1f b/s)", comp * 100.0, rollSpeed) : "")
                + (ship == null ? ", not on a ship" : ", on " + ship);
    }

    @Override
    public Component status() {
        String k = locked() ? (wow ? "message.wmd.gear.status_wow" : "message.wmd.gear.status_down") : up() ? "message.wmd.gear.status_up" : "message.wmd.gear.status_transit";
        return Component.translatable(large() ? "block.wmd.landing_gear_large" : bogie() ? "block.wmd.landing_gear_bogie" : "block.wmd.landing_gear").append(" (").append(Component.translatable("message.wmd.gear.size" + size)).append(", ").append(Component.translatable(roleKey())).append("): ")
                .append(Component.translatable(k));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("lgRole", role);
        tag.putInt("lgSize", size);
        tag.putInt("lgLean", leanX);
        tag.putBoolean("lgOrder", ordered);
        tag.putInt("lgExt", ext);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        role = Math.max(0, Math.min(3, tag.getInt("lgRole")));
        if (!fixedSize()) size = tag.getInt("lgSize") == MEDIUM ? MEDIUM : SMALL;
        if (large() && role > MAIN) role = MAIN;
        if (bogie() && role != TWIN) role = MAIN;
        leanX = tag.getInt("lgLean") > 0 ? 1 : -1;
        ordered = !tag.contains("lgOrder") || tag.getBoolean("lgOrder");
        ext = tag.contains("lgExt") ? Math.max(0, Math.min(Balance.GEAR_CYCLE_TICKS, tag.getInt("lgExt"))) : Balance.GEAR_CYCLE_TICKS;
        if (tag.contains("lgWow")) { wow = tag.getBoolean("lgWow"); comp = tag.getFloat("lgComp"); rollSpeed = tag.getFloat("lgRoll"); steer = tag.getFloat("lgSteer"); }
        if (client) {
            towBy = tag.hasUUID("lgTowBy") ? tag.getUUID("lgTowBy") : null;
            towLen = tag.contains("lgTowLen") ? tag.getFloat("lgTowLen") : Balance.TOW_LEN_MIN;
            driveShown = tag.getByte("lgDrive");
            if (towBy != null) TowBar.CLIENT_TOWED.add(worldPosition.asLong());
            else TowBar.CLIENT_TOWED.remove(worldPosition.asLong());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("lgRole", role);
        tag.putInt("lgSize", size);
        tag.putInt("lgLean", leanX);
        tag.putBoolean("lgOrder", ordered);
        tag.putInt("lgExt", ext);
        tag.putBoolean("lgWow", wow);
        tag.putFloat("lgComp", comp);
        tag.putFloat("lgRoll", rollSpeed);
        tag.putFloat("lgSteer", steer);
        if (towBy != null) { tag.putUUID("lgTowBy", towBy); tag.putFloat("lgTowLen", (float) towLen); }
        tag.putByte("lgDrive", (byte) drive);
        return tag;
    }
}
