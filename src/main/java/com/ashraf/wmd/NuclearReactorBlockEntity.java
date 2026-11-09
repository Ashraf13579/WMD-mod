package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class NuclearReactorBlockEntity extends ItemProcessBlockEntity implements ModeMachine, Owned {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final OwnerData ownerData = new OwnerData();
    private boolean ownerLogged;
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private static final double CX = (ReactorStructure.X0 + ReactorStructure.X1) / 2.0 - ReactorStructure.CTRL_X;
    private static final double CZ = (ReactorStructure.Z0 + ReactorStructure.Z1) / 2.0 - ReactorStructure.CTRL_Z;
    private static final double TOP = ReactorStructure.Y1 + 1 - ReactorStructure.CTRL_Y;
    private static final double LX0 = ReactorStructure.X0 - ReactorStructure.CTRL_X, LX1 = ReactorStructure.X1 - ReactorStructure.CTRL_X;
    private static final double LZ0 = ReactorStructure.Z0 - ReactorStructure.CTRL_Z, LZ1 = ReactorStructure.Z1 - ReactorStructure.CTRL_Z;

    static final double[] CORE = { 0.0, 5.0 - ReactorStructure.CTRL_Y, 4.0 - ReactorStructure.CTRL_Z };

    private static final double[][] BEACONS = { { LX0, TOP + 0.3, LZ0 }, { LX1, TOP + 0.3, LZ0 }, { LX0, TOP + 0.3, LZ1 }, { LX1, TOP + 0.3, LZ1 } };

    private static final double[] LAMP = { 0.0, 0.7, -0.55 };

    private static final double[][] VENTS = { { CX, TOP, LZ0 }, { CX, TOP, LZ1 }, { LX0, TOP, CZ }, { LX1, TOP, CZ } };
    private static final PortMap PORT_MAP = portMap();

    private static PortMap portMap() {
        PortMap m = new PortMap((int) LX0, (int) LX1, (int) (LZ1 - LZ0) + 1);
        for (ReactorStructure.Port p : ReactorStructure.ports()) {
            switch (p.kind()) {
                case 'I' -> m.item(p.x(), p.y(), p.z(), p.side(), false);
                case 'W' -> m.fluid(p.x(), p.y(), p.z(), p.side(), "water", false);
                case 'O' -> m.fluid(p.x(), p.y(), p.z(), p.side(), "steam", true);
                default -> m.power(p.x(), p.y(), p.z(), p.side(), false);
            }
        }
        return m;
    }

    public static final float ROD_TRAVEL_PX = 32.0F;
    public static final int CRANE_TICKS = 60;

    public static final int SCRAM_MODE = 5;

    public static final int NORMAL = 0, ALARM = 1, STEAM = 2, IMMINENT = 3;

    private final FluidTank water = new FluidTank(Balance.REACTOR_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final FluidTank steam = new FluidTank(Balance.REACTOR_STEAM_TANK_MB, fs -> fs.getFluid() == WmdFluids.STEAM.source().get());

    private final IFluidHandler waterIn = counting(fillOnly(water), true);

    private final IFluidHandler steamOut = counting(drainOnly(steam), false);
    private final Slot fuel = new Slot(Balance.REACTOR_FUEL_SLOTS, false, s -> s.is(wmd("fuel_rod")));
    private final Slot spent = new Slot(Balance.REACTOR_SPENT_SLOTS, true, s -> false);
    private final Slot breeder = new Slot(Balance.BREEDER_SLOTS, false, s -> s.is(wmd("breeder_rod")));
    private final Slot bred = new Slot(Balance.BREEDER_SLOTS, true, s -> false);
    private final Slot[] slots = { fuel, spent, breeder, bred };
    private final List<FluidTank> tanks = List.of(water, steam, fuel.gauge, spent.gauge, breeder.gauge, bred.gauge);

    private final IItemHandler fuelHatch = fuelHatch();

    private final PumpBuffer pumps = new PumpBuffer();

    @Override
    public void empDrain() { pumps.set(0); setChanged(); }

    private final class PumpBuffer extends net.neoforged.neoforge.energy.EnergyStorage {
        PumpBuffer() { super(Balance.REACTOR_PUMP_BUFFER_FE, Balance.REACTOR_PUMP_FE_PER_TICK * 10, 0); }
        @Override public int receiveEnergy(int n, boolean sim) { int r = super.receiveEnergy(n, sim); if (r > 0 && !sim) setChanged(); return r; }
        boolean use(int n) { if (energy < n) return false; energy -= n; return true; }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private int rods;
    private boolean scram;
    private boolean damaged;
    private double heat;
    private int fuelLeft;
    private int breedLeft;
    private boolean spentPending;
    private boolean testPowered;
    private int warnedStage;
    private long lastWarn = -100000;

    private int stage;
    private int coolPct;
    private int steamNow;
    private int steamOutRate;
    private int waterInRate;
    private boolean pumpsOn = true;
    private boolean spentFull;
    private int safeQ;

    private int steamOutAcc, waterInAcc;
    private boolean registered, checkedPorts, meltdownPending, melted;
    private long lastSync;
    private int syncedKey = Integer.MIN_VALUE;

    public NuclearReactorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.NUCLEAR_REACTOR_BE.get(), pos, state, 0);
    }

    private static final Map<Level, Set<BlockPos>> REACTORS = new WeakHashMap<>();
    static void clearStatics() { REACTORS.clear(); }

    private void register(Level level) {
        if (registered) return;
        REACTORS.computeIfAbsent(level, k -> new HashSet<>()).add(worldPosition.immutable());
        registered = true;
    }

    private void checkPortsOnce(Level level) {
        if (checkedPorts) return;
        checkedPorts = true;
        if (!isFormed()) return;
        Direction f = facing();
        for (ReactorStructure.Port p : ReactorStructure.ports()) {
            if (p.kind() != 'O') continue;
            BlockPos at = Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), p.x(), p.y(), p.z());
            if (level.getBlockState(at).is(Wmd.REACTOR_STEAM_PORT.get())) continue;
            if (getBlockState().getBlock() instanceof NuclearReactorBlock rb) rb.unform(level, worldPosition);
            if (level instanceof ServerLevel sl)
                tell(sl, Balance.REACTOR_WARN_RADIUS, Component.translatable("message.wmd.nuclear.needs_steam_ports", at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.YELLOW));
            return;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            Set<BlockPos> s = REACTORS.get(level);
            if (s != null) s.remove(worldPosition);
        }
        registered = false;
        if (level != null && level.isClientSide) ClientReactorFx.remove(worldPosition);
    }

    void setTestPowered() { testPowered = true; setChanged(); }

    void testFill() {
        int n = Balance.REACTOR_FUEL_SLOTS - fuel.count();
        if (n > 0 && (fuel.count() == 0 || fuel.stack.is(wmd("fuel_rod")))) fuel.add(wmd("fuel_rod"), n);
        water.fill(new FluidStack(Fluids.WATER, Balance.REACTOR_TANK_MB), IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        sync(-1);
    }

    void testHeat(double h) {
        heat = Mth.clamp(h, 0.0, Balance.REACTOR_HEAT_MAX - 1.0);
        setChanged();
        sync(-1);
    }

    static List<NuclearReactorBlockEntity> near(Level level, BlockPos from, int range) {
        List<NuclearReactorBlockEntity> l = new ArrayList<>();
        Set<BlockPos> s = REACTORS.get(level);
        if (s == null) return l;
        for (Iterator<BlockPos> it = s.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (SableBridge.distSqWithShips(level, p, from) > (double) range * range) continue;
            if (!level.isLoaded(p)) continue;
            if (level.getBlockEntity(p) instanceof NuclearReactorBlockEntity r && !r.isRemoved()) { if (r.isFormed()) l.add(r); }
            else it.remove();
        }
        l.sort((a, b) -> Double.compare(SableBridge.distSqWithShips(level, a.worldPosition, from), SableBridge.distSqWithShips(level, b.worldPosition, from)));
        return l;
    }

    public static void serverTickReactor(Level level, BlockPos pos, BlockState state, NuclearReactorBlockEntity be) {
        be.register(level);
        if (!be.ownerLogged) {
            be.ownerLogged = true;
            if (be.getOwner() == null) Wmd.LOGGER.info("WMD: nuclear reactor at {} in {} has no owner (an older world): it stays public until its controller is placed again",
                    pos.toShortString(), level.dimension().location());
        }
        be.checkPortsOnce(level);
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
        if (be.meltdownPending && !be.melted && level instanceof ServerLevel sl) be.meltdown(sl);
    }

    public static void clientTickReactor(Level level, BlockPos pos, BlockState state, NuclearReactorBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (!be.isFormed()) { ClientReactorFx.remove(pos); return; }

        ClientReactorFx.tick(level, pos, be.facing(), be.rodLevel() / 4.0, be.stage(), be.isRunning(), be.heat() / (double) Balance.REACTOR_HEAT_MAX, level.getGameTime(), be.ringAngle(0.0F));
        be.animate();
    }

    public boolean isFormed() {
        BlockState s = getBlockState();
        return s.hasProperty(NuclearReactorBlock.FORMED) && s.getValue(NuclearReactorBlock.FORMED);
    }

    void formedChanged(boolean formed) {
        if (!formed) scram();
        else sync(-1);
    }

    @Override
    protected boolean process() {
        long now = level.getGameTime();

        if (spentPending && spent.fits(wmd("spent_fuel_rod"), 1)) { spent.add(wmd("spent_fuel_rod"), 1); spentPending = false; setChanged(); }

        int q = damaged || !isFormed() ? 0 : rods;
        boolean spentRoom = !spentPending && spent.fits(wmd("spent_fuel_rod"), 1);
        if (q > 0 && fuelLeft <= 0 && fuel.count() > 0 && spentRoom) {
            fuel.take(1);
            fuelLeft = Balance.REACTOR_FUEL_TICKS * 4;
            craneStart = now;
            setChanged();
            sync(-1);
        }
        int run = fuelLeft > 0 ? q : 0;
        double lf = run / 4.0;
        spentFull = q > 0 && run == 0 && fuel.count() > 0 && !spentRoom;

        boolean pumpsNeeded = run > 0 || heat > 0.5;
        pumpsOn = !pumpsNeeded || testPowered || pumps.use(Balance.REACTOR_PUMP_FE_PER_TICK);

        int want = Balance.REACTOR_STEAM_MB * run / 4;
        int made = 0;
        if (want > 0 && pumpsOn) {
            int space = steam.getCapacity() - steam.getFluidAmount();
            made = Math.max(0, Math.min(want, Math.min(water.getFluidAmount(), space)));
            if (made > 0) {
                water.drain(made, IFluidHandler.FluidAction.EXECUTE);
                steam.fill(new FluidStack(WmdFluids.STEAM.source().get(), made), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        steamNow = made;
        flowAcc += made;

        double cf = want > 0 ? made / (double) want : (pumpsOn ? 1.0 : 0.0);
        coolPct = (int) Math.round(cf * 100.0);
        double cooled = lf * cf;

        if (run > 0 && lf > cooled + 1.0E-9) {
            heat += Balance.REACTOR_HEAT_RISE * (lf - cooled);
        } else if (pumpsOn) {
            double target = Balance.REACTOR_HEAT_OPERATING * lf;
            heat = heat > target ? Math.max(target, heat - Balance.REACTOR_HEAT_FALL) : Math.min(target, heat + Balance.REACTOR_HEAT_FALL);
        }
        heat = Mth.clamp(heat, 0.0, Balance.REACTOR_HEAT_MAX);

        if (run > 0) {
            fuelLeft = Math.max(0, fuelLeft - run);
            if (fuelLeft == 0) {
                if (spent.fits(wmd("spent_fuel_rod"), 1)) spent.add(wmd("spent_fuel_rod"), 1);
                else spentPending = true;
                setChanged();
                sync(-1);
            }
            if (now % 100 == 0) setChanged();
        }

        if (breedLeft <= 0 && breeder.count() > 0 && bred.fits(wmd("irradiated_breeder_rod"), 1)) {
            breeder.take(1);
            breedLeft = Balance.BREEDER_TICKS * 4;
            setChanged();
        }
        if (run > 0 && breedLeft > 0) {
            breedLeft = Math.max(0, breedLeft - run);
            if (breedLeft == 0) { bred.add(wmd("irradiated_breeder_rod"), 1); setChanged(); }
        }

        if (now % 20 == 0) loopRates();

        ladder((ServerLevel) level, now);
        if (heat >= Balance.REACTOR_HEAT_MAX && !damaged && !meltdownPending) overheat((ServerLevel) level);
        sync(now);
        if (spentFull) return fail(OUT_FULL);
        if (q > 0 && run == 0) return fail(NO_INPUT);
        return run > 0;
    }

    private void loopRates() {
        waterInRate = waterInAcc / 20;
        steamOutRate = steamOutAcc / 20;
        waterInAcc = 0;
        steamOutAcc = 0;
        int waterCap = water.getFluidAmount() * 2 >= water.getCapacity() ? Integer.MAX_VALUE : waterInRate;
        int steamCap = steam.getFluidAmount() * 2 >= steam.getCapacity() ? steamOutRate : Balance.PIPE_RATE_MB * attachedSteamFaces();
        boolean power = testPowered || pumps.getEnergyStored() >= Balance.REACTOR_PUMP_FE_PER_TICK;
        int carry = power ? Math.min(waterCap, steamCap) : 0;
        safeQ = (int) Mth.clamp(carry * 4L / Balance.REACTOR_STEAM_MB, 0L, 4L);
    }

    private int attachedSteamFaces() {
        if (!isFormed()) return 0;
        Direction f = facing();
        int n = 0;
        for (ReactorStructure.Port p : ReactorStructure.ports()) {
            if (p.kind() != 'O') continue;
            Direction wd = Machines.dirToWorld(f, Machines.mount(getBlockState()), p.side());
            BlockPos next = Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), p.x(), p.y(), p.z()).relative(wd);
            if (level.isLoaded(next) && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, next, wd.getOpposite()) != null) n++;
        }
        return n;
    }

    public static int stageOf(double heat) {
        if (heat >= Balance.REACTOR_HEAT_IMMINENT) return IMMINENT;
        if (heat >= Balance.REACTOR_HEAT_STEAM) return STEAM;
        if (heat >= Balance.REACTOR_HEAT_ALARM) return ALARM;
        return NORMAL;
    }

    private void ladder(ServerLevel sl, long now) {
        int st = stageOf(heat);
        if (st != stage) {
            if (st > stage && st > NORMAL) {
                MusicTriggersHook.fire(sl, "wmd:reactor_" + (st == ALARM ? "alarm" : st == STEAM ? "steam" : "imminent"), getBlockPos());
            }
            stage = st;
            setChanged();
        }
        if (st < warnedStage) warnedStage = st;
        if (st >= STEAM && st > warnedStage && now - lastWarn >= Balance.REACTOR_WARN_REPEAT_TICKS) {
            warnedStage = st;
            lastWarn = now;
            BlockPos c = corePos();
            tell(sl, Balance.REACTOR_WARN_RADIUS, Component.translatable(st >= IMMINENT ? "message.wmd.nuclear.imminent" : "message.wmd.nuclear.steam",
                    c.getX(), c.getY(), c.getZ()).withStyle(st >= IMMINENT ? ChatFormatting.DARK_RED : ChatFormatting.RED));
        }

        double[] b = worldPoint(CX, TOP, CZ);
        if (st >= ALARM && st < IMMINENT && now % 30 == 0)
            sl.playSound(null, b[0], b[1], b[2], WmdSounds.NR_ALARM.get(), SoundSource.BLOCKS, Balance.SND_ALARM, st >= STEAM ? 1.15F : 1.0F);
        if (st >= STEAM && now % 40 == 0)
            sl.playSound(null, b[0], b[1], b[2], WmdSounds.NR_STEAM.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 0.9F + sl.random.nextFloat() * 0.2F);
        if (st >= IMMINENT && now % 60 == 0)
            sl.playSound(null, b[0], b[1], b[2], WmdSounds.SIREN.get(), SoundSource.BLOCKS, Balance.SND_ALARM, 1.0F);
    }

    private void overheat(ServerLevel sl) {
        if (Config.reactorMeltdown()) {
            meltdownPending = true;
            return;
        }
        damaged = true;
        rods = 0;
        scram = true;
        setChanged();
        BlockPos c = corePos();
        sl.playSound(null, c, WmdSounds.NR_DAMAGE.get(), SoundSource.BLOCKS, Balance.SND_ALARM, 1.0F);
        tell(sl, Balance.REACTOR_WARN_RADIUS, Component.translatable("message.wmd.nuclear.damaged", c.getX(), c.getY(), c.getZ()).withStyle(ChatFormatting.RED));
        sync(-1);
    }

    private void meltdown(ServerLevel sl) {
        if (melted || isRemoved()) return;
        melted = true;
        meltdownPending = false;
        MusicTriggersHook.fire(sl, "wmd:reactor_meltdown", getBlockPos());
        BlockPos core = corePos();
        net.minecraft.world.phys.Vec3 cw = SableBridge.worldPos(sl, core);
        int gx = (int) Math.floor(cw.x), gy = (int) Math.floor(cw.y), gz = (int) Math.floor(cw.z);
        tell(sl, Balance.REACTOR_WARN_RADIUS * 4, Component.translatable("message.wmd.nuclear.meltdown", gx, gy, gz).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        takeAll();
        water.setFluid(FluidStack.EMPTY);
        steam.setFluid(FluidStack.EMPTY);
        ReactorStructure.destroy(sl, worldPosition, facing(), Machines.mount(getBlockState()));
        sl.removeBlock(worldPosition, false);
        FlightManager mgr = FlightManager.get(sl);
        int tier = Balance.REACTOR_MELTDOWN_TIER;
        mgr.holdForTest(sl, gx, gz, WarheadFx.holdRadius(tier, WarheadType.NUKE));
        Forcefield.inside(() -> WarheadFx.detonate(sl, mgr, gx, gy, gz, tier, WarheadType.NUKE, 0.0, 1.0));
    }

    private void tell(ServerLevel sl, int r, Component msg) {
        net.minecraft.world.phys.Vec3 c = SableBridge.worldPos(sl, corePos());
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(c.x, c.y, c.z) <= (double) r * r) p.sendSystemMessage(msg);
    }

    public BlockPos corePos() {
        double[] c = worldPoint(CORE[0], CORE[1], CORE[2]);
        return BlockPos.containing(c[0], c[1], c[2]);
    }

    private void sync(long now) {
        if (level == null || level.isClientSide) return;
        int key = (int) heat / 2 * 31 + stage * 7 + rods * 131 + (damaged ? 1 : 0) + (scram ? 3 : 0) + coolPct * 1009 + steamOutRate * 7919 + waterInRate * 13
                + steamNow * 17 + coreFuelPct() * 101 + safeQ * 37 + (pumpsOn ? 5 : 0) + (spentFull ? 11 : 0) + spent.count() * 53 + (int) craneStart * 3;
        if (key == syncedKey) return;
        boolean urgent = now < 0 || stage != lastSyncedStage;
        long t = level.getGameTime();
        if (!urgent && t - lastSync < 10) return;
        syncedKey = key;
        lastSyncedStage = stage;
        lastSync = t;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private int lastSyncedStage = -1;

    @Override public int mode() { return rods; }
    @Override public int modeCount() { return SCRAM_MODE + 1; }

    @Override
    public void setMode(int m) {
        if (m == SCRAM_MODE) { scram(); return; }
        if (damaged || !isFormed()) return;
        int nq = Mth.clamp(m, 0, 4);
        if (nq == rods && !scram) return;
        rods = nq;
        scram = false;
        setChanged();
        sync(-1);
    }

    public void scram() {
        boolean was = rods > 0;
        rods = 0;
        scram = true;
        setChanged();
        if (level instanceof ServerLevel sl && was) sl.playSound(null, corePos(), WmdSounds.NR_RODS.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.9F);
        sync(-1);
    }

    @Override
    public void setModeClient(int m) {
        if (m == SCRAM_MODE) { rods = 0; scram = true; }
        else if (!damaged) { rods = Mth.clamp(m, 0, 4); scram = false; }
    }

    public boolean repair(Player player) {
        if (!damaged) { player.displayClientMessage(Component.translatable("message.wmd.nuclear.no_damage"), true); return false; }
        damaged = false;
        scram = true;
        rods = 0;
        level.playSound(null, worldPosition, WmdSounds.NR_RODS.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.1F);
        player.displayClientMessage(Component.translatable("message.wmd.nuclear.repaired"), true);
        setChanged();
        sync(-1);
        return true;
    }

    public int rodLevel() { return rods; }
    public int levelPct() { return rods * 25; }
    public boolean isDamaged() { return damaged; }
    public boolean isScrammed() { return scram; }
    public int heat() { return (int) heat; }
    public int stage() { return stage; }
    public int coolPct() { return coolPct; }

    public int steamNow() { return steamNow; }

    public int steamOutRate() { return steamOutRate; }
    public int waterInRate() { return waterInRate; }

    public boolean pumpsOn() { return pumpsOn; }

    public boolean spentFull() { return spentFull; }

    public int safeMaxPct() { return safeQ * 25; }

    public int breedPct() { return breedLeft <= 0 ? 0 : 100 - (int) Math.round(breedLeft * 100.0 / (Balance.BREEDER_TICKS * 4.0)); }
    public int coreFuelPct() { return (int) Math.round(fuelLeft * 100.0 / (Balance.REACTOR_FUEL_TICKS * 4.0)); }

    private long craneStart = -100000;
    private float ringAngle, ringSpeed;
    private float rodPx, rodPxOld;
    private float vent, ventOld;

    private void animate() {
        float target = isRunning() ? 0.02F + 0.06F * rods : 0.0F;
        ringSpeed += (target - ringSpeed) * 0.03F;
        ringAngle = (ringAngle + ringSpeed) % (Mth.TWO_PI * 64);
        rodPxOld = rodPx;
        float rt = damaged || scram ? 0.0F : ROD_TRAVEL_PX * rods / 4.0F;
        float step = rt < rodPx && scram ? 8.0F : 0.5F;
        rodPx = rt > rodPx ? Math.min(rt, rodPx + step) : Math.max(rt, rodPx - step);
        ventOld = vent;
        vent = Mth.clamp(vent + (stage >= STEAM ? 0.08F : -0.03F), 0.0F, 1.0F);
    }

    public float ringAngle(float pt) { return ringAngle + ringSpeed * pt; }
    public float rodPx(float pt) { return Mth.lerp(pt, rodPxOld, rodPx); }
    public float ventOpen(float pt) { return Mth.lerp(pt, ventOld, vent); }

    public float craneProgress(float pt) {
        if (level == null) return -1.0F;
        float p = (level.getGameTime() - craneStart + pt) / CRANE_TICKS;
        return p >= 0.0F && p <= 1.0F ? p : -1.0F;
    }

    @Override protected String[] iconIds() { return new String[] { "fuel_rod", "spent_fuel_rod", "breeder_rod", "irradiated_breeder_rod" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, WmdFluids.STEAM.source().get(), Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("water", "steam", "fuel_rods", "spent_fuel", "breeder_rods", "irradiated_rods"); }
    @Override protected int rateMbPerTick() { return Balance.REACTOR_STEAM_MB; }
    @Override public boolean guiTankOutput(int t) { return t == 1 || t == 3 || t == 5; }
    @Override protected float progress01() { return fuelLeft <= 0 ? -1.0F : coreFuelPct() / 100.0F; }
    @Override public String guiTankUnit(int t) { return t < 2 ? "mB" : "items"; }
    @Override public IEnergyStorage guiEnergy() { return pumps; }
    @Override public int guiFeUse() { return Balance.REACTOR_PUMP_FE_PER_TICK; }

    @Override
    public int guiStatus() {
        if (running) return pumpsOn ? 1 : 2;
        return reason;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.reactor_steam", Balance.REACTOR_STEAM_MB, Balance.REACTOR_PUMP_FE_PER_TICK * 20, Balance.REACTOR_FUEL_TICKS / 1200),
                Component.translatable("gui.wmd.info.reactor_loop"));
    }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override protected SoundEvent runSound() { return WmdSounds.NUC_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.containing(CORE[0], CORE[1], CORE[2]); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }
    @Override protected float levelFraction() { return water.getFluidAmount() / (float) Balance.REACTOR_TANK_MB; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x4E55_0000_0000_0000L;
        double[] core = worldPoint(CORE[0], CORE[1], CORE[2]), lamp = worldPoint(LAMP[0], LAMP[1], LAMP[2]);
        float lvl = rods / 4.0F;

        VeilBridge.machineGlow(key + 1, lamp[0], lamp[1], lamp[2], 0.35F, 0.6F, 1.0F, 0.8F, 0.5F, t);
        if (t % 25 != 0) return;
        var r = level.random;
        double[] v = VENTS[r.nextInt(VENTS.length)];
        double[] w = worldPoint(v[0], v[1], v[2]);
        level.addParticle(ParticleTypes.CLOUD, w[0], w[1] + 0.1, w[2], 0, 0.05, 0);
    }

    private void warningEffects(Level level) {
        if (stage < ALARM) return;
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x4E56_0000_0000_0000L;
        int period = stage >= IMMINENT ? 10 : 20;
        if (t % period < period / 2) {
            for (int i = 0; i < BEACONS.length; i++) {
                double[] b = worldPoint(BEACONS[i][0], BEACONS[i][1], BEACONS[i][2]);
                if (stage >= STEAM) VeilBridge.machineGlow(key + i, b[0], b[1], b[2], 1.0F, 0.15F, 0.1F, 5.0F, 1.3F, t);
                else VeilBridge.machineGlow(key + i, b[0], b[1], b[2], 1.0F, 0.6F, 0.1F, 4.0F, 1.1F, t);
            }
        }
        if (stage < STEAM) return;
        var r = level.random;
        int n = stage >= IMMINENT ? 3 : 1;
        for (int i = 0; i < n; i++) {
            double[] v = VENTS[r.nextInt(VENTS.length)];
            double[] w = worldPoint(v[0], v[1], v[2]);
            level.addParticle(ParticleTypes.CLOUD, w[0] + (r.nextDouble() - 0.5) * 0.3, w[1] + 0.1, w[2] + (r.nextDouble() - 0.5) * 0.3,
                    (r.nextDouble() - 0.5) * 0.06, 0.35 + r.nextDouble() * 0.25, (r.nextDouble() - 0.5) * 0.06);
        }
        if (stage >= IMMINENT && t % 4 == 0) {
            double[] w = worldPoint(CX + (r.nextDouble() - 0.5) * (LX1 - LX0), TOP, CZ + (r.nextDouble() - 0.5) * (LZ1 - LZ0));
            level.addParticle(ParticleTypes.LARGE_SMOKE, w[0], w[1], w[2], 0, 0.12, 0);
        }
    }

    @Override
    protected String detail() {
        return "rods " + levelPct() + "%, heat " + (int) heat + ", cooling " + coolPct + "%, water " + mb(water) + ", steam " + mb(steam) + ", fuel rods " + fuel.count()
                + " (+" + coreFuelPct() + "% in core), spent " + spent.count() + ", breeder " + breeder.count() + " (+" + breedPct() + "% bred), irradiated " + bred.count() + (pumpsOn ? "" : ", PUMPS UNPOWERED") + (damaged ? ", DAMAGED" : "") + (scram ? ", SCRAM" : "");
    }

    private boolean isPort(BlockPos part, Direction side, char kind) {
        if (!isFormed()) return false;
        for (ReactorStructure.Port p : ReactorStructure.ports()) if (p.kind() == kind && at(part, side, p.x(), p.y(), p.z(), p.side())) return true;
        return false;
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (isPort(part, side, 'W')) return waterIn;
        if (isPort(part, side, 'O')) return steamOut;
        return null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return isPort(part, side, 'I') ? fuelHatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return isPort(part, side, 'E') ? pumps : null; }

    private IItemHandler fuelHatch() {
        IItemHandler in = inHandler(fuel), out = outHandler(spent), bIn = inHandler(breeder), bOut = outHandler(bred);
        return new IItemHandler() {
            @Override public int getSlots() { return 4; }
            @Override public net.minecraft.world.item.ItemStack getStackInSlot(int i) { return i == 0 ? in.getStackInSlot(0) : i == 1 ? out.getStackInSlot(0) : i == 2 ? bIn.getStackInSlot(0) : bOut.getStackInSlot(0); }
            @Override public net.minecraft.world.item.ItemStack insertItem(int i, net.minecraft.world.item.ItemStack s, boolean sim) { return i == 0 ? in.insertItem(0, s, sim) : i == 2 ? bIn.insertItem(0, s, sim) : s; }
            @Override public net.minecraft.world.item.ItemStack extractItem(int i, int n, boolean sim) { return i == 1 ? out.extractItem(0, n, sim) : i == 3 ? bOut.extractItem(0, n, sim) : net.minecraft.world.item.ItemStack.EMPTY; }
            @Override public int getSlotLimit(int i) { return i == 0 ? in.getSlotLimit(0) : i == 1 ? out.getSlotLimit(0) : i == 2 ? bIn.getSlotLimit(0) : bOut.getSlotLimit(0); }
            @Override public boolean isItemValid(int i, net.minecraft.world.item.ItemStack s) { return i == 0 && in.isItemValid(0, s) || i == 2 && bIn.isItemValid(0, s); }
        };
    }

    private IFluidHandler counting(IFluidHandler h, boolean countFill) {
        return new IFluidHandler() {
            @Override public int getTanks() { return h.getTanks(); }
            @Override public FluidStack getFluidInTank(int i) { return h.getFluidInTank(i); }
            @Override public int getTankCapacity(int i) { return h.getTankCapacity(i); }
            @Override public boolean isFluidValid(int i, FluidStack s) { return h.isFluidValid(i, s); }
            @Override
            public int fill(FluidStack s, FluidAction a) {
                int n = h.fill(s, a);
                if (countFill && a.execute() && n > 0) waterInAcc += n;
                return n;
            }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return counted(h.drain(s, a), a); }
            @Override public FluidStack drain(int max, FluidAction a) { return counted(h.drain(max, a), a); }
            private FluidStack counted(FluidStack got, FluidAction a) {
                if (!countFill && a.execute() && !got.isEmpty()) steamOutAcc += got.getAmount();
                return got;
            }
        };
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        ownerData.write(tag);
        tag.putInt("pumpFe", pumps.getEnergyStored());
        if (testPowered) tag.putBoolean("testPowered", true);
        if (spentPending) tag.putBoolean("spentPending", true);
        tag.putInt("rods", rods);
        tag.putBoolean("scram", scram);
        tag.putBoolean("damaged", damaged);
        tag.putDouble("heat", heat);
        tag.putInt("fuelLeft", fuelLeft);
        tag.putInt("breedLeft", breedLeft);
        tag.putInt("warned", warnedStage);
        tag.putInt("stage", stage);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        ownerData.read(tag);

        pumps.set(tag.getInt("pumpFe"));
        testPowered = tag.getBoolean("testPowered");
        spentPending = tag.getBoolean("spentPending");

        if (!steam.isEmpty() && steam.getFluid().getFluid() != WmdFluids.STEAM.source().get()) steam.setFluid(FluidStack.EMPTY);
        rods = Mth.clamp(tag.getInt("rods"), 0, 4);
        scram = tag.getBoolean("scram");
        damaged = tag.getBoolean("damaged");
        heat = Mth.clamp(tag.getDouble("heat"), 0.0, Balance.REACTOR_HEAT_MAX);
        fuelLeft = Math.max(0, tag.getInt("fuelLeft"));
        breedLeft = Math.max(0, tag.getInt("breedLeft"));
        warnedStage = tag.getInt("warned");
        stage = tag.getInt("stage");

        if (tag.contains("coolPct")) {
            coolPct = tag.getInt("coolPct");
            steamNow = tag.getInt("steamNow");
            steamOutRate = tag.getInt("steamOut");
            waterInRate = tag.getInt("waterIn");
            pumpsOn = tag.getBoolean("pumpsOn");
            spentFull = tag.getBoolean("spentFull");
            safeQ = tag.getInt("safeQ");
            craneStart = tag.getLong("crane");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("rods", rods);
        tag.putBoolean("scram", scram);
        tag.putBoolean("damaged", damaged);
        tag.putDouble("heat", heat);
        tag.putInt("fuelLeft", fuelLeft);
        tag.putInt("stage", stage);
        tag.putInt("coolPct", coolPct);
        tag.putInt("steamNow", steamNow);
        tag.putInt("steamOut", steamOutRate);
        tag.putInt("waterIn", waterInRate);
        tag.putBoolean("pumpsOn", pumpsOn);
        tag.putBoolean("spentFull", spentFull);
        tag.putInt("safeQ", safeQ);
        tag.putLong("crane", craneStart);
        return tag;
    }
}
