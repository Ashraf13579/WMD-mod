package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FusionReactorBlockEntity extends ProcessUnitBlockEntity implements ModeMachine, Owned, CcAnimated {

    public static final int X0 = -8, X1 = 8, Y0 = 0, Y1 = 12, Z0 = 0, Z1 = 16;

    public static final int COLD = 0, COOLDOWN = 1, ENERGISING = 2, IGNITION = 3, RAMP = 4, RUNNING = 5, SHUTDOWN = 6, QUENCH = 7;

    public static final int R_NONE = 0, R_WATER = 1, R_DEUTERIUM = 2, R_TRITIUM = 3, R_EMP = 4, R_STOPPED = 5;

    public static final int MODE_START = 10, MODE_STOP = 11;

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private final FluidTank deuterium = new FluidTank(Balance.FUSION_FUEL_TANK_MB, fs -> fs.getFluid() == WmdFluids.DEUTERIUM.source().get());
    private final FluidTank tritium = new FluidTank(Balance.FUSION_FUEL_TANK_MB, fs -> fs.getFluid() == WmdFluids.TRITIUM.source().get());
    private final FluidTank water = new FluidTank(Balance.FUSION_WATER_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final List<FluidTank> tanks = List.of(deuterium, tritium, water);
    private final IFluidHandler dIn = fillOnly(deuterium), tIn = fillOnly(tritium), wIn = fillOnly(water);

    private int state = COLD, stateTicks, reason;
    private int throttle = 100;
    private int charge;
    private int stored;
    private float plasma;
    private double dAcc, tAcc, wAcc;
    private int feNow;
    private int chargedThisTick;
    private long chargeTick = -1;
    private int lastSentHash;

    private final IEnergyStorage socket = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int n, boolean sim) {
            if (state != ENERGISING || level == null) return 0;
            long t = level.getGameTime();
            if (t != chargeTick) { chargeTick = t; chargedThisTick = 0; }
            int take = Math.max(0, Math.min(n, Math.min(Balance.FUSION_IGNITION_FE - charge, Balance.FUSION_CHARGE_IN - chargedThisTick)));
            if (!sim && take > 0) { charge += take; chargedThisTick += take; setChanged(); }
            return take;
        }
        @Override
        public int extractEnergy(int n, boolean sim) {
            int take = Math.max(0, Math.min(n, Math.min(stored, Balance.FUSION_SOCKET_OUT)));
            if (!sim && take > 0) { stored -= take; setChanged(); }
            return take;
        }
        @Override public int getEnergyStored() { return state == ENERGISING ? charge / 25 : stored; }
        @Override public int getMaxEnergyStored() { return state == ENERGISING ? Balance.FUSION_IGNITION_FE / 25 : Balance.FUSION_BUFFER_FE; }
        @Override public boolean canExtract() { return state != ENERGISING && stored > 0; }
        @Override public boolean canReceive() { return state == ENERGISING && charge < Balance.FUSION_IGNITION_FE; }
    };

    private final IEnergyStorage gauge = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) { return 0; }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.FUSION_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return false; }
    };

    public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FUSION_REACTOR_BE.get(), pos, state, 0);
    }

    public static void serverTickFusion(Level level, BlockPos pos, BlockState bs, FusionReactorBlockEntity be) {
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, pos)) {
            if (be.hot()) be.quench(R_EMP);
            be.tickQuench();
        }
        ProcessUnitBlockEntity.serverTick(level, pos, bs, be);
        be.sync(false);
        if (level instanceof ServerLevel sl) be.serverArcs(sl);
    }

    private int arcCountdown = 60, burstPending;
    private long lastBig;

    private double[] ring(double r, double deg, double y) {
        double a = Math.toRadians(deg);
        return worldPoint(r * Math.cos(a), y, 8.0 + r * Math.sin(a));
    }

    private void serverArcs(ServerLevel sl) {
        var rnd = sl.random;
        java.util.List<FusionArcPayload.Arc> out = new java.util.ArrayList<>();
        float p = Math.max(0.1F, plasma);
        switch (state) {
            case ENERGISING -> {
                if (rnd.nextFloat() < 0.02F + 0.12F * chargePermille() / 1000.0F) out.add(crownArc(rnd));
            }
            case RAMP, RUNNING -> {
                if (burstPending > 0) {
                    for (int i = 0; i < burstPending; i++) out.add(airArc(rnd, i % 4 == 0));
                    burstPending = 0;
                }
                if (--arcCountdown <= 0) {
                    out.add(airArc(rnd, false));
                    arcCountdown = (int) ((160 - 120 * p) * (0.6 + 0.8 * rnd.nextFloat()));
                }
                if (sl.getGameTime() - lastBig > 1_200 && rnd.nextFloat() < 0.004F) {
                    lastBig = sl.getGameTime();
                    int n = 3 + rnd.nextInt(2);
                    for (int i = 0; i < n; i++) out.add(airArc(rnd, true));
                }
            }
            case QUENCH -> {
                if (stateTicks < 60 && sl.getGameTime() % 3 == 0) {
                    out.add(airArc(rnd, rnd.nextFloat() < 0.15F));
                    if (rnd.nextBoolean()) out.add(crownArc(rnd));
                }
            }
            default -> {}
        }
        if (out.isEmpty()) return;
        for (FusionArcPayload.Arc a : out) if (a.kind() > 0) hurtAlong(sl, a);
        double[] c = worldPoint(0.0, 6.0, 8.0);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(sl, null, c[0], c[1], c[2], 96.0, new FusionArcPayload(out));
    }

    private FusionArcPayload.Arc airArc(net.minecraft.util.RandomSource rnd, boolean big) {
        int k = rnd.nextInt(16);
        double a = 11.25 + 22.5 * k, y = 3.5 + rnd.nextDouble() * 5.0;
        double[] s = ring(6.8, a, y);
        double a2 = a + (rnd.nextDouble() - 0.5) * 24.0;
        double[] e = big ? ring(10.0 + rnd.nextDouble() * 4.0, a2, 0.05) : ring(9.5 + rnd.nextDouble() * 3.5, a2, Math.max(0.4, y + (rnd.nextDouble() - 0.6) * 4.0));
        return new FusionArcPayload.Arc((float) s[0], (float) s[1], (float) s[2], (float) e[0], (float) e[1], (float) e[2], rnd.nextInt(), big ? 2 : 1);
    }

    private FusionArcPayload.Arc crownArc(net.minecraft.util.RandomSource rnd) {
        double a = 11.25 + 22.5 * rnd.nextInt(16);
        double[] s = ring(3.8 + rnd.nextDouble() * 1.5, a, 9.8);
        double[] e = ring(7.9, a + (rnd.nextDouble() - 0.5) * 20.0, 12.1);
        return new FusionArcPayload.Arc((float) s[0], (float) s[1], (float) s[2], (float) e[0], (float) e[1], (float) e[2], rnd.nextInt(), 0);
    }

    private void hurtAlong(ServerLevel sl, FusionArcPayload.Arc a) {
        net.minecraft.world.phys.Vec3 s = new net.minecraft.world.phys.Vec3(a.x0(), a.y0(), a.z0()), e = new net.minecraft.world.phys.Vec3(a.x1(), a.y1(), a.z1());
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(s, e).inflate(1.6);
        net.minecraft.world.phys.Vec3 d = e.subtract(s);
        double len2 = Math.max(1.0E-6, d.lengthSqr());
        for (net.minecraft.world.entity.LivingEntity le : sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, x -> x.isAlive() && !x.isSpectator())) {
            if (le instanceof Player pl && pl.isCreative()) continue;
            net.minecraft.world.phys.Vec3 m = le.getBoundingBox().getCenter();
            double k = Mth.clamp(m.subtract(s).dot(d) / len2, 0.0, 1.0);
            if (m.distanceTo(s.add(d.scale(k))) > 1.4) continue;
            le.hurt(sl.damageSources().lightningBolt(), Balance.FUSION_ARC_DAMAGE);
            net.minecraft.world.phys.Vec3 away = new net.minecraft.world.phys.Vec3(d.x, 0.0, d.z);
            if (away.lengthSqr() > 1.0E-6) {
                away = away.normalize().scale(Balance.FUSION_ARC_KNOCKBACK);
                le.push(away.x, 0.25, away.z);
                le.hurtMarked = true;
            }
        }
    }

    private boolean hot() { return state == IGNITION || state == RAMP || state == RUNNING || state == SHUTDOWN; }

    private void go(int s) {
        state = s;
        stateTicks = 0;
        setChanged();
        sync(true);
    }

    private void quench(int why) {
        reason = why;
        charge = 0;
        plasma = 0.0F;
        go(QUENCH);
    }

    private void tickQuench() {
        if (state == QUENCH && ++stateTicks >= Balance.FUSION_QUENCH_TICKS) go(COLD);
    }

    @Override
    protected boolean work() {
        feNow = 0;
        switch (state) {
            case COOLDOWN -> {
                if (water.getFluidAmount() < Balance.FUSION_START_WATER_MB / 4) { reason = R_WATER; go(COLD); break; }
                if (++stateTicks >= Balance.FUSION_COOLDOWN_TICKS) go(ENERGISING);
            }
            case ENERGISING -> {
                stateTicks++;
                if (charge >= Balance.FUSION_IGNITION_FE) { charge = 0; go(IGNITION); }
            }
            case IGNITION -> {
                if (!burn(Balance.FUSION_MIN_PCT / 100.0F)) break;
                if (++stateTicks >= Balance.FUSION_IGNITION_TICKS) { burstPending = 10; go(RAMP); }
            }
            case RAMP -> {
                float target = throttle / 100.0F * Math.min(1.0F, (stateTicks + 1) / (float) Balance.FUSION_RAMP_TICKS);
                plasma = Math.max(Balance.FUSION_MIN_PCT / 100.0F, target);
                if (!make()) break;
                if (++stateTicks >= Balance.FUSION_RAMP_TICKS) go(RUNNING);
            }
            case RUNNING -> {
                stateTicks++;
                plasma += Mth.clamp(throttle / 100.0F - plasma, -0.002F, 0.002F);
                make();
            }
            case SHUTDOWN -> {
                plasma = Math.max(0.0F, plasma - 1.0F / Balance.FUSION_SHUTDOWN_TICKS);
                if (plasma > 0.0F) make();
                if (++stateTicks >= Balance.FUSION_SHUTDOWN_TICKS || plasma <= 0.0F) { plasma = 0.0F; reason = R_STOPPED; go(COLD); }
            }
            case QUENCH -> tickQuench();
            default -> {}
        }
        return state == RAMP || state == RUNNING || state == SHUTDOWN;
    }

    private boolean make() {
        int want = Math.round(plasma * Balance.FUSION_MAX_FE);
        int made = Math.max(0, Math.min(want, Balance.FUSION_BUFFER_FE - stored));
        float lvl = Math.max(Balance.FUSION_MIN_PCT / 100.0F, made / (float) Balance.FUSION_MAX_FE);
        if (!burn(lvl)) return false;
        stored += made;
        feNow = made;
        flowAcc += Math.round(lvl * rateMbPerTick());
        if (level.getGameTime() % 40 == 0) setChanged();
        return true;
    }

    private boolean burn(float lvl) {
        dAcc += Balance.FUSION_D_MB * lvl;
        tAcc += Balance.FUSION_T_MB * lvl;
        wAcc += Balance.FUSION_WATER_MB * lvl;
        int d = (int) dAcc, t = (int) tAcc, w = (int) wAcc;
        if (water.getFluidAmount() < w) { quench(R_WATER); return false; }
        if (deuterium.getFluidAmount() < d) { quench(R_DEUTERIUM); return false; }
        if (tritium.getFluidAmount() < t) { quench(R_TRITIUM); return false; }
        dAcc -= d; tAcc -= t; wAcc -= w;
        if (d > 0) deuterium.drain(d, IFluidHandler.FluidAction.EXECUTE);
        if (t > 0) tritium.drain(t, IFluidHandler.FluidAction.EXECUTE);
        if (w > 0) water.drain(w, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private void start() {
        if (state != COLD) return;
        if (water.getFluidAmount() < Balance.FUSION_START_WATER_MB) { reason = R_WATER; sync(true); return; }
        if (deuterium.getFluidAmount() < Balance.FUSION_START_FUEL_MB) { reason = R_DEUTERIUM; sync(true); return; }
        if (tritium.getFluidAmount() < Balance.FUSION_START_FUEL_MB) { reason = R_TRITIUM; sync(true); return; }
        reason = R_NONE;
        go(COOLDOWN);
    }

    private void stop() {
        if (state == COOLDOWN || state == ENERGISING) { reason = R_STOPPED; go(COLD); }
        else if (state == IGNITION || state == RAMP || state == RUNNING) { reason = R_NONE; go(SHUTDOWN); }
    }

    void plasmaRelease(ServerLevel sl) {
        if (!hot() || released) { Wmd.LOGGER.info("[fusion] reactor at {} destroyed: no plasma release (state {}, released {})", getBlockPos().toShortString(), state, released); return; }
        released = true;
        double[] w = worldPoint(0.0, 0.0, 8.0);
        int gx = (int) Math.floor(w[0]), gy = (int) Math.floor(w[1]), gz = (int) Math.floor(w[2]);
        FlightManager mgr = FlightManager.get(sl);
        mgr.holdForTest(sl, gx, gz, WarheadFx.holdRadius(2, WarheadType.PLASMA));
        Forcefield.inside(() -> WarheadFx.detonate(sl, mgr, gx, gy, gz, 2, WarheadType.PLASMA, 0.0, 1.0));
    }

    private boolean released;

    static final java.util.Set<FusionReactorBlockEntity> LOADED = Statics.server(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) LOADED.remove(this);
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = Wmd.MODID)
    public static final class VanillaBlasts {
        private VanillaBlasts() {}

        @net.neoforged.bus.api.SubscribeEvent
        static void onDetonate(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
            if (event.getLevel() instanceof ServerLevel sl && !ClaimGuard.probing()) {
                blastAt(sl, event.getExplosion().center(), Math.max(2.0, event.getExplosion().radius() * 1.5));
                AntimatterStoreBlockEntity.blastAt(sl, event.getExplosion().center(), Math.max(2.0, event.getExplosion().radius() * 1.5));
                RailgunBlockEntity.blastAt(sl, event.getExplosion().center(), Math.max(2.0, event.getExplosion().radius() * 1.5));
            }
        }
    }

    public static void blastAt(ServerLevel sl, net.minecraft.world.phys.Vec3 pos, double r) {
        NavalReactorBlockEntity.blastAt(sl, pos, r);
        for (FusionReactorBlockEntity be : new java.util.ArrayList<>(LOADED)) {
            if (be.level != sl || be.isRemoved() || !be.hot()) continue;
            double[] c = be.worldPoint(0.0, 6.0, 8.0);
            double d = Math.sqrt((c[0] - pos.x) * (c[0] - pos.x) + (c[1] - pos.y) * (c[1] - pos.y) + (c[2] - pos.z) * (c[2] - pos.z));
            if (d > r + 9.0) continue;
            Wmd.LOGGER.info("[fusion] reactor at {} caught in a blast r {} at {} blocks: PLASMA RELEASE", be.getBlockPos().toShortString(), Math.round(r), Math.round(d));
            be.plasmaRelease(sl);
        }
    }
    private long minedTick = Long.MIN_VALUE;

    void markMined(Level lv) { minedTick = lv.getGameTime(); }
    boolean minedNow(Level lv) { return lv.getGameTime() - minedTick <= 1; }

    @Override
    public void empDrain() {
        stored = 0;
        if (hot()) quench(R_EMP);
        setChanged();
    }

    private void sync(boolean now) {
        if (level == null || level.isClientSide) return;
        int h = (((state * 31 + reason) * 31 + throttle) * 31 + chargePermille()) * 31 + Math.round(plasma * 1000) * 7 + stored / 40_000;
        if (h == lastSentHash || (!now && level.getGameTime() % 10 != 0)) return;
        lastSentHash = h;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public int mode() { return throttle / 10 - 1; }
    @Override public int modeCount() { return 12; }

    @Override
    public void setMode(int m) {
        if (m >= 0 && m <= 9) { throttle = (m + 1) * 10; setChanged(); sync(true); }
        else if (m == MODE_START) start();
        else if (m == MODE_STOP) stop();
    }

    @Override
    public void setModeClient(int m) { if (m >= 0 && m <= 9) throttle = (m + 1) * 10; }

    public int state() { return state; }
    public int reason() { return reason; }
    public int throttle() { return throttle; }
    public int chargePermille() { return (int) Math.min(1000L, charge * 1000L / Balance.FUSION_IGNITION_FE); }
    public int plasmaPct() { return Math.round(plasma * 100.0F); }
    public int outputFe() { return Math.round(plasma * Balance.FUSION_MAX_FE); }
    public int stateTicks() { return stateTicks; }

    public float stateProgress() {
        int len = switch (state) {
            case COOLDOWN -> Balance.FUSION_COOLDOWN_TICKS; case IGNITION -> Balance.FUSION_IGNITION_TICKS; case RAMP -> Balance.FUSION_RAMP_TICKS;
            case SHUTDOWN -> Balance.FUSION_SHUTDOWN_TICKS; case QUENCH -> Balance.FUSION_QUENCH_TICKS; default -> 0;
        };
        return len > 0 ? Math.min(1.0F, stateTicks / (float) len) : -1.0F;
    }

    private long clientStateTick = -1;
    private int clientState = -1;

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        if (clientState != state) { clientState = state; clientStateTick = (long) tt - stateTicks; }
        double t = tt - clientStateTick;
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);

        int lit = switch (state) {
            case ENERGISING -> Math.min(16, chargePermille() * 16 / 1000);
            case IGNITION, RAMP, RUNNING, SHUTDOWN -> 16;
            case QUENCH -> ((long) tt) % 6 < 3 ? 16 : 0;
            default -> 0;
        };

        int gap = state == RUNNING || state == RAMP ? (int) ((tt * (0.25 + 0.5 * Math.max(0.1, plasmaPct() / 100.0))) % 16) : -1;
        for (int k = 0; k < 16; k++) {
            final boolean on = k < lit && !dark && k != gap && (gap < 0 || k != (gap + 8) % 16);
            model.getBone("coil" + k).ifPresent(b -> b.setHidden(!on));
        }
        boolean sol = !dark && (state == ENERGISING && chargePermille() >= 500 || hot() || state == QUENCH && ((long) tt) % 6 < 3);
        model.getBone("solenoid").ifPresent(b -> b.setHidden(!sol));

        boolean nbi = !dark && (state == IGNITION && t >= Balance.FUSION_IGNITION_TICKS - 20 || state == RAMP || state == RUNNING);
        model.getBone("nbi").ifPresent(b -> b.setHidden(!nbi));

        boolean pl = switch (state) {
            case IGNITION -> t >= Balance.FUSION_IGNITION_TICKS - 8 && ((long) tt) % 2 == 0;
            case RAMP -> t > 20 || ((long) tt) % 3 != 0;
            case RUNNING -> ((long) tt + worldPosition.getX()) % 97 != 0;
            case SHUTDOWN -> plasmaPct() > 2;
            case QUENCH -> t < 30 && ((long) tt) % 4 < 2;
            default -> false;
        };
        final boolean plOn = pl && !dark;
        model.getBone("plasma").ifPresent(b -> b.setHidden(!plOn));
        final boolean desk = state != COLD && !dark;
        model.getBone("desk").ifPresent(b -> b.setHidden(!desk));
    }

    int fxState = -1, fxLit;
    double fxOrbit;

    public static void clientTickFusion(Level level, BlockPos pos, BlockState bs, FusionReactorBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, bs, be);
        ClientFusionFx.tickReactor(be, level);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.FUSION_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 6, 8); }
    @Override protected float soundVolume() { return Balance.SND_GIANT; }
    @Override protected float soundPitch() { return 0.8F + 0.3F * plasmaPct() / 100.0F; }

    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.DEUTERIUM.source().get(), WmdFluids.TRITIUM.source().get(), Fluids.WATER); }
    @Override protected List<String> labelKeys() { return List.of("deuterium", "tritium", "water"); }
    @Override protected int inputTanks() { return 3; }
    @Override protected int rateMbPerTick() { return 100; }
    @Override public IEnergyStorage guiEnergy() { return gauge; }
    @Override protected float levelFraction() { return plasma; }

    @Override
    public int guiStatus() {
        if (hot()) return 1;
        if (state == COLD && reason == R_WATER) return 6;
        if (state == COLD && (reason == R_DEUTERIUM || reason == R_TRITIUM)) return 4;
        return 0;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.fusion_out", String.format("%,d", Balance.FUSION_MAX_FE)),
                Component.translatable("gui.wmd.info.fusion_fuel", fmt(Balance.FUSION_D_MB * 20), fmt(Balance.FUSION_T_MB * 20), fmt(Balance.FUSION_WATER_MB * 20)),
                Component.translatable("gui.wmd.info.fusion_start", String.format("%,d", Balance.FUSION_IGNITION_FE)));
    }

    private static String fmt(double v) { return v == Math.rint(v) ? String.format("%,d", (long) v) : String.format("%.1f", v); }

    @Override
    protected String detail() {
        return "state " + state + ", throttle " + throttle + " %, plasma " + plasmaPct() + " %, making " + feNow + " FE/t, stored " + stored + " FE, charge " + charge
                + " FE, deuterium " + mb(deuterium) + ", tritium " + mb(tritium) + ", water " + mb(water);
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -8, 1, 4, Direction.WEST)) return dIn;
        if (at(part, side, -8, 1, 12, Direction.WEST)) return tIn;
        if (at(part, side, 8, 1, 8, Direction.EAST)) return wIn;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, -3, 1, 16, Direction.SOUTH) || at(part, side, 0, 1, 16, Direction.SOUTH) || at(part, side, 3, 1, 16, Direction.SOUTH) ? socket : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-8, 8, 17)
            .fluid(-8, 1, 4, Direction.WEST, "deuterium", false).fluid(-8, 1, 12, Direction.WEST, "tritium", false)
            .fluid(8, 1, 8, Direction.EAST, "water", false)
            .power(-3, 1, 16, Direction.SOUTH, true).power(0, 1, 16, Direction.SOUTH, true).power(3, 1, 16, Direction.SOUTH, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putState(tag);
        tag.putInt("stored", stored);
        tag.putDouble("dAcc", dAcc);
        tag.putDouble("tAcc", tAcc);
        tag.putDouble("wAcc", wAcc);
        ownerData.write(tag);
    }

    private void putState(CompoundTag tag) {
        tag.putInt("fstate", state);
        tag.putInt("fticks", stateTicks);
        tag.putInt("freason", reason);
        tag.putInt("throttle", throttle);
        tag.putInt("charge", charge);
        tag.putFloat("plasma", plasma);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fstate")) {
            state = Mth.clamp(tag.getInt("fstate"), COLD, QUENCH);
            stateTicks = Math.max(0, tag.getInt("fticks"));
            reason = tag.getInt("freason");
            throttle = Mth.clamp(tag.getInt("throttle") / 10 * 10, 10, 100);
            charge = Mth.clamp(tag.getInt("charge"), 0, Balance.FUSION_IGNITION_FE);
            plasma = Mth.clamp(tag.getFloat("plasma"), 0.0F, 1.0F);
        }
        if (tag.contains("stored")) {
            stored = Mth.clamp(tag.getInt("stored"), 0, Balance.FUSION_BUFFER_FE);
            dAcc = tag.getDouble("dAcc");
            tAcc = tag.getDouble("tAcc");
            wAcc = tag.getDouble("wAcc");
            ownerData.read(tag);
        } else {
            gaugeStored = tag.getInt("fstored");
        }
    }

    private int gaugeStored;

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putState(tag);
        tag.putInt("fstored", stored);
        return tag;
    }

    public int storedShown() { return level != null && level.isClientSide ? gaugeStored : stored; }
}
