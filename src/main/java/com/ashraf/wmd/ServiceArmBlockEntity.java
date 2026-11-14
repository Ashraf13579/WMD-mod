package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ServiceArmBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host, ComparatorMachine {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 2, Z0 = -5, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0)
            .fluid(-1, 0, 1, Direction.SOUTH, "jet_fuel", false).power(0, 0, 1, Direction.SOUTH, false).fluid(1, 0, 1, Direction.SOUTH, "diesel", false);

    static final float P0F = 6.0F, P0Y = 23.0F, LIFT = 30.0F, L1 = 74.0F, KH = 13.0F, W0 = 72.0F, TELE = 44.0F, LH = 21.0F;
    static final float J0F = 16.0F, J0Y = 9.5F, LUGS = 44.0F, LUGH = -6.0F, GEAR_R = 7.4F, PIN_R = 2.5F;
    static final float A1_MIN = 8.0F, A1_MAX = 75.0F, BETA_MIN = -70.0F;
    static final float LE = (float) Math.hypot(L1, KH), PHI = (float) Math.atan2(KH, L1);

    static final float[] HOSE_A = { -18.5F, 36.0F, -9.4F }, HOSE_B = { -7.2F, 36.0F, 2.5F };

    static final float SX = 1.0F, SY = 1.0F, SZ = 1.0F;

    static final int H = 0, L = 1, S = 2, SL = 3, A1 = 4, B = 5, E = 6, HD = 7, K = 8, N = 9;
    static final float[] STOWED = { 0, 0, 0, 0, 0, 180, 0, 180, 0 };

    public static final int P_IDLE = 0, P_OPEN = 1, P_UNFOLD = 2, P_SLEW = 3, P_REACH = 4, P_ATTACH = 5, P_PUMP = 6, P_RELEASE = 7, P_UNREACH = 8,
            P_SLEW_BACK = 9, P_FOLD = 10, P_CLOSE = 11, P_BREAK = 12;
    public static final int S_IDLE = 0, S_WAIT_PARK = 1, S_WORKING = 2, S_PUMPING = 3, S_DONE = 4, S_NO_FUEL = 5, S_BREAK = 6, S_NO_POWER = 7, S_STANDBY = 8;
    public static final int M_AUTO = 0, M_REDSTONE = 1, M_MANUAL = 2;
    public static final int A_MODE = 0, A_GO = 1, A_STOP = 2, A_PIN = 3, A_PIN_AT = 4, A_LAST = A_PIN_AT;

    public static final int LIST = 4;

    private final FluidTank jet = new FluidTank(Balance.SA_JET_MB, fs -> fs.getFluid() == WmdFluids.JET_FUEL.source().get());
    private final FluidTank diesel = new FluidTank(Balance.SA_DIESEL_MB, fs -> fs.getFluid() == WmdFluids.DIESEL.source().get());
    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int mode = M_AUTO;
    private boolean go, redstoneWas;

    private int phase = P_IDLE;
    private long phaseStart, pausedAt = -1L;
    private int phaseDur = 1;
    private float[] from = STOWED.clone(), to = STOWED.clone();

    private float[] pFrom = STOWED.clone(), pTo = STOWED.clone();
    private long pStart;
    private int pDur;
    private int status = S_STANDBY, found;
    private String targetText = "";
    private int jobMb, jobFe;

    private UUID tShip;
    private BlockPos tPos;
    private int tKind = -1;
    private Vec3 tLast;
    private Vec3 seenAt;
    private long seenTime;
    private UUID seenShip;
    private BlockPos seenPos;
    private long pinPos = Long.MIN_VALUE;
    private boolean cPinned;
    private final List<Long> foundPos = new ArrayList<>();
    private List<String> foundText = new ArrayList<>();
    private int pinIdx = -1;
    private int idleTicks;
    private int comparator;
    private long coolUntil;

    Object moveLoop, pumpLoop;
    private float scanAng, pScanAng;

    private float[] shown, shownV;
    private long shownAt;

    private int recFrames, recBack, recJump, recSyncs, recMoves;
    private long recStart, recLast, recWorstFrame, recMoveAt = Long.MIN_VALUE;
    private double recLastTt = Double.NaN, recWorstBack, recWorstJump, recWorstIn, recWorstOut;
    private float[] recIn, recOut;

    public ServiceArmBlockEntity(BlockPos pos, BlockState state) {
        super(ServiceBlocks.SERVICE_ARM_BE.get(), pos, state, Balance.SA_FE_MOVE, Balance.SA_BUFFER_FE, Balance.SA_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { setChanged(); sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    public void act(int action, int[] data, Player by) {
        switch (action) {
            case A_MODE -> { mode = (mode + 1) % 3; go = false; status = phase == P_IDLE ? (mode == M_AUTO ? S_IDLE : S_STANDBY) : status; }
            case A_GO -> go = true;
            case A_STOP -> { go = false; if (phase != P_IDLE && phase < P_UNREACH) finishJob(); }
            case A_PIN -> pinNext();
            case A_PIN_AT -> {
                int i = data.length > 0 ? data[0] : -1;
                if (i < 0 || i >= foundPos.size()) return;
                long p = foundPos.get(i);
                pinPos = pinPos == p ? Long.MIN_VALUE : p;
                targetText = pinPos == Long.MIN_VALUE ? "" : foundText.get(i);
                updatePinIdx();
            }
            default -> { return; }
        }
        setChanged();
        sync();
    }

    public int mode() { return mode; }
    public int phase() { return phase; }
    public int armStatus() { return status; }
    public int pointsFound() { return found; }
    public String targetText() { return targetText; }
    public int jobMb() { return jobMb; }
    public int jobFe() { return jobFe; }

    public List<String> foundList() { return foundText; }
    public int pinnedRow() { return pinIdx; }

    int rowOn(ServerLevel sl, UUID ship) {
        if (ship == null) return -1;
        for (int i = 0; i < foundPos.size(); i++) if (ship.equals(SableBridge.shipIdAt(sl, BlockPos.of(foundPos.get(i))))) return i;
        return -1;
    }

    public boolean pinned() { return level != null && level.isClientSide ? cPinned : pinPos != Long.MIN_VALUE; }
    public FluidTank jetTank() { return jet; }
    public FluidTank dieselTank() { return diesel; }

    public float[] pose(double tt) {
        double t = pausedAt >= 0 ? pausedAt : tt;
        float s = ease((t - phaseStart) / Math.max(1, phaseDur));
        float[] p = new float[N];
        for (int i = 0; i < N; i++) p[i] = i == SL || i == HD ? from[i] + Mth.wrapDegrees(to[i] - from[i]) * s : from[i] + (to[i] - from[i]) * s;
        if (pDur > 0) {
            float sp = ease((t - pStart) / Math.max(1, pDur));
            if (sp < 1.0F) for (int i = 0; i < N; i++) p[i] += (i == SL || i == HD ? Mth.wrapDegrees(pTo[i] - pFrom[i]) : pTo[i] - pFrom[i]) * (sp - 1.0F);
        }
        return p;
    }

    private static float ease(double k) {
        k = Mth.clamp(k, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }

    private static boolean blends(int p) {
        return p == P_SLEW || p == P_REACH || p == P_RELEASE || p == P_BREAK || p == P_UNREACH || p == P_OPEN || p == P_UNFOLD || p == P_SLEW_BACK
                || p == P_FOLD;
    }

    private static double blendAt(int p) {
        return p == P_OPEN || p == P_UNFOLD || p == P_SLEW_BACK || p == P_FOLD ? Balance.SA_BLEND_PIT : Balance.SA_BLEND;
    }

    private boolean handOver(long now) {
        boolean prevDone = pDur <= 0 || now - pStart >= pDur;
        if (now - phaseStart >= phaseDur) return prevDone || blends(phase);
        return blends(phase) && prevDone && now - phaseStart >= phaseDur * blendAt(phase);
    }

    public boolean moving(double tt) { return phase != P_IDLE && phase != P_PUMP && pausedAt < 0 && tt - phaseStart < phaseDur; }

    static float[] ik(double f, double y, double head) {
        double th = Math.toRadians(head);
        double wf = f - LH * Math.cos(th), wy = y - LH * Math.sin(th);
        double py0 = P0Y + LIFT, dx = wf - P0F, dy = wy - py0, d = Math.max(1.0, Math.hypot(dx, dy));
        double l2 = Math.max(W0, Math.min(W0 + 2 * TELE, d - 0.80 * (LE + W0) + W0));
        l2 = Math.min(l2, LE + d - 0.5);
        double ca = Mth.clamp((LE * LE + d * d - l2 * l2) / (2 * LE * d), -1.0, 1.0);
        double a1 = Math.atan2(dy, dx) + Math.acos(ca) - PHI;
        a1 = Mth.clamp(a1, Math.toRadians(A1_MIN), Math.toRadians(A1_MAX));
        double kf = P0F + LE * Math.cos(a1 + PHI), ky = py0 + LE * Math.sin(a1 + PHI);
        double a2 = Math.atan2(wy - ky, wf - kf);
        l2 = Math.hypot(wf - kf, wy - ky);
        double beta = Mth.wrapDegrees(Math.toDegrees(a2 - a1));
        if (beta < -100.0) beta += 360.0;
        if (beta > 200.0) beta -= 360.0;
        beta = Mth.clamp(beta, BETA_MIN, 180.0);
        return new float[] { (float) Math.toDegrees(a1), (float) beta, (float) Mth.clamp(l2 - W0, 0.0, 2 * TELE) };
    }

    static double[] tip(float a1, float beta, float ext, float head) {
        double r1 = Math.toRadians(a1), r2 = Math.toRadians(a1 + beta), rh = Math.toRadians(head);
        double kf = P0F + LE * Math.cos(r1 + PHI), ky = P0Y + LIFT + LE * Math.sin(r1 + PHI);
        double wf = kf + (W0 + ext) * Math.cos(r2), wy = ky + (W0 + ext) * Math.sin(r2);
        return new double[] { wf + LH * Math.cos(rh), wy + LH * Math.sin(rh) };
    }

    static Vec3 planeToLocal(double f, double y, double slewDeg) {
        double s = Math.toRadians(slewDeg);
        return new Vec3(-Math.sin(s) * f / 16.0, y / 16.0, -Math.cos(s) * f / 16.0);
    }

    static float[] solve(Vec3 p, Vec3 n, double off) {
        Vec3 t = p.add(n.scale(off));
        double slew = Math.toDegrees(Math.atan2(-t.x, -t.z));
        double hx = -Math.sin(Math.toRadians(slew)), hz = -Math.cos(Math.toRadians(slew));
        double f = (t.x * hx + t.z * hz) * 16.0, y = t.y * 16.0;
        if (Math.hypot(t.x, t.z) > Balance.SA_REACH + 1.0) return null;
        Vec3 d = n.scale(-1.0);
        double df = d.x * hx + d.z * hz, dy = d.y;
        double head = Math.hypot(df, dy) > 0.35 ? Math.toDegrees(Math.atan2(dy, df)) : 0.0;
        float[] q = ik(f, y, head);
        double[] tp = tip(q[0], q[1], q[2], (float) head);
        if (Math.hypot(tp[0] - f, tp[1] - y) > 3.5) return null;
        return new float[] { 1, 1, 1, (float) slew, q[0], q[1], q[2], (float) head, 0 };
    }

    private Vec3 wp(double lx, double ly, double lz) { double[] q = worldPoint(lx, ly, lz); return new Vec3(q[0], q[1], q[2]); }

    private Vec3 toLocal(Vec3 w) {
        Vec3 o = wp(0, 0, 0), r = wp(1, 0, 0).subtract(o), u = wp(0, 1, 0).subtract(o), f = wp(0, 0, -1).subtract(o);
        Vec3 d = w.subtract(o);
        return new Vec3(d.dot(r), d.dot(u), -d.dot(f));
    }

    private Vec3 dirToLocal(Vec3 wn) {
        Vec3 o = wp(0, 0, 0), r = wp(1, 0, 0).subtract(o), u = wp(0, 1, 0).subtract(o), f = wp(0, 0, -1).subtract(o);
        return new Vec3(wn.dot(r), wn.dot(u), -wn.dot(f)).normalize();
    }

    static UUID craftOwner(ServerLevel sl, UUID ship) { return Crafts.owner(sl, ship); }

    static int kindOf(BlockEntity be) {
        if (be instanceof ServiceReceptacleBlockEntity) return 0;
        if (be instanceof FuelTankBlockEntity) return be.getBlockState().is(AirBlocks.DROP_TANK.get()) ? 2 : 1;
        if (be instanceof PowerCoreBlockEntity) return 3;
        if (be instanceof ShipTurbineBlockEntity) return 4;
        if (be instanceof SubDieselBlockEntity) return 5;
        return -1;
    }

    private static int[] portCell(int kind) {
        return switch (kind) {
            case 1 -> new int[] { 0, 0, 0, Direction.NORTH.get3DDataValue() };
            case 2 -> new int[] { 0, 0, 0, Direction.UP.get3DDataValue() };
            case 3 -> new int[] { 0, 0, 0, Direction.WEST.get3DDataValue() };
            case 4 -> new int[] { 1, 1, 1, Direction.UP.get3DDataValue() };
            case 5 -> new int[] { 1, 0, 0, Direction.NORTH.get3DDataValue() };
            default -> null;
        };
    }

    private static Vec3[] pointOf(ServerLevel sl, BlockEntity be, int kind) {
        if (be == null || be.isRemoved()) return null;
        if (kind == 0) return ((ServiceReceptacleBlockEntity) be).facePoint();
        int[] c = portCell(kind);
        if (c == null || !(be instanceof ProcessUnitBlockEntity pu)) return null;
        Direction ls = Direction.from3DDataValue(c[3]);
        double fx = c[0] + 0.5 * ls.getStepX(), fy = c[1] + 0.5 + 0.5 * ls.getStepY(), fz = c[2] + 0.5 * ls.getStepZ();
        Vec3 p = SableBridge.toWorld(sl, pu.plotPoint(fx, fy, fz)), q = SableBridge.toWorld(sl, pu.plotPoint(fx + ls.getStepX(), fy + ls.getStepY(), fz + ls.getStepZ()));
        if (p == null || q == null) return null;
        return new Vec3[] { p, q.subtract(p).normalize() };
    }

    private static Object[] partOf(BlockEntity be, int kind) {
        int[] c = portCell(kind);
        if (c == null) return null;
        BlockState st = be.getBlockState();
        Direction f = Machines.facing(st), m = Machines.mount(st);
        return new Object[] { Machines.toWorld(be.getBlockPos(), f, m, c[0], c[1], c[2]), Machines.dirToWorld(f, m, Direction.from3DDataValue(c[3])) };
    }

    private static boolean exposed(ServerLevel sl, BlockEntity be, int kind) {
        if (kind == 0) return ((ServiceReceptacleBlockEntity) be).exposed();
        Object[] ps = partOf(be, kind);
        if (ps == null) return false;
        BlockPos n = ((BlockPos) ps[0]).relative((Direction) ps[1]);
        BlockState st = sl.getBlockState(n);
        return st.isAir() || st.getCollisionShape(sl, n).isEmpty();
    }

    private static IFluidHandler handlerOf(BlockEntity be, int kind) {
        Object[] ps = partOf(be, kind);
        if (ps == null) return null;
        BlockPos part = (BlockPos) ps[0];
        Direction side = (Direction) ps[1];
        if (be instanceof FuelTankBlockEntity t) return t.fluidPort(part, side);
        if (be instanceof PowerCoreBlockEntity c) return c.fluidPort(part, side);
        return ServiceReceptacleBlockEntity.drivePort(be);
    }

    private static int give(ServerLevel sl, BlockEntity be, int kind, Fluid fluid, int mb, boolean simulate) {
        if (mb <= 0) return 0;
        if (kind == 0) return ((ServiceReceptacleBlockEntity) be).acceptFuel(sl, fluid, mb, simulate);
        IFluidHandler h = handlerOf(be, kind);
        return h == null ? 0 : h.fill(new FluidStack(fluid, mb), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    private boolean wants(ServerLevel sl, BlockEntity be, int kind) {
        if (jet.getFluidAmount() > 0 && give(sl, be, kind, jet.getFluid().getFluid(), Balance.SA_FUEL_RATE, true) > 0) return true;
        if (diesel.getFluidAmount() > 0 && give(sl, be, kind, diesel.getFluid().getFluid(), Balance.SA_FUEL_RATE, true) > 0) return true;
        if (energy().getEnergyStored() < Balance.SA_GROUND_FE * 4) return false;
        IEnergyStorage core = coreOf(be, kind);
        if (core != null) return core.receiveEnergy(Balance.SA_GROUND_FE, true) > 0;
        if (kind == 0 && be instanceof ServiceReceptacleBlockEntity r && r.craft() != null) {
            int[] c = PowerCoreBlockEntity.chargeOf(sl, r.craft());
            return c[1] > 0 && c[0] < c[1] - Balance.SA_GROUND_FE;
        }
        return false;
    }

    private static IEnergyStorage coreOf(BlockEntity be, int kind) { return kind == 3 && be instanceof PowerCoreBlockEntity c ? c.energy() : null; }

    private record Cand(UUID ship, BlockEntity be, int kind, Vec3 local, double dist) {}

    private List<Cand> candidates(ServerLevel sl) {
        List<Cand> out = new ArrayList<>();
        if (!SableBridge.active() || !AirGate.on()) return out;
        Vec3 c = wp(0, 1, 0);
        double r = Balance.SA_REACH + 3.0;
        AABB box = new AABB(c.x - r, c.y - 6.0, c.z - r, c.x + r, c.y + 14.0, c.z + r);
        UUID mine = SableBridge.shipIdAt(sl, worldPosition);
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, box)) {
            if (s.id().equals(mine)) continue;
            UUID own = craftOwner(sl, s.id());
            if (own != null && getOwner() != null && !ShipStrike.sameSide(getOwner(), own)) continue;
            for (BlockPos p : SableBridge.blockEntitiesOn(sl, s.id())) {
                BlockEntity be = sl.getBlockEntity(p);
                int kind = kindOf(be);
                if (kind < 0 || !exposed(sl, be, kind) || !wants(sl, be, kind)) continue;
                Vec3[] pt = pointOf(sl, be, kind);
                if (pt == null) continue;
                Vec3 lp = toLocal(pt[0]), ln = dirToLocal(pt[1]);
                if (solve(lp, ln, 1.25) == null || solve(lp, ln, 0.05) == null) continue;
                out.add(new Cand(s.id(), be, kind, lp, Math.hypot(lp.x, lp.z) + (kind == 0 ? 0.0 : 100.0)));
            }
        }
        out.sort((a, b) -> Double.compare(a.dist(), b.dist()));
        return out;
    }

    private void setFound(List<Cand> l) {
        List<String> t = new ArrayList<>();
        foundPos.clear();
        for (int i = 0; i < Math.min(LIST, l.size()); i++) { foundPos.add(l.get(i).be().getBlockPos().asLong()); t.add(label(l.get(i))); }
        boolean changed = !t.equals(foundText);
        foundText = t;
        if (updatePinIdx() || changed) { setChanged(); sync(); }
    }

    private boolean updatePinIdx() {
        int i = pinPos == Long.MIN_VALUE ? -1 : foundPos.indexOf(pinPos);
        if (i == pinIdx) return false;
        pinIdx = i;
        return true;
    }

    private void pinNext() {
        if (!(level instanceof ServerLevel sl)) return;
        List<Cand> l = candidates(sl);
        found = l.size();
        setFound(l);
        if (l.isEmpty()) { pinPos = Long.MIN_VALUE; targetText = ""; return; }
        int i = 0;
        for (int k = 0; k < l.size(); k++) if (l.get(k).be().getBlockPos().asLong() == pinPos) { i = k + 1; break; }
        if (i >= l.size()) { pinPos = Long.MIN_VALUE; targetText = ""; return; }
        pinPos = l.get(i).be().getBlockPos().asLong();
        targetText = label(l.get(i));
        updatePinIdx();
    }

    private static String label(Cand c) {
        return c.be().getBlockState().getBlock().getName().getString() + String.format(" %.1f", Math.hypot(c.local().x, c.local().z));
    }

    private void go(int p, float[] target, int minDur) {
        long t0 = level.getGameTime();
        boolean overlap = blends(phase) && pausedAt < 0 && t0 - phaseStart < phaseDur && (pDur <= 0 || t0 - pStart >= pDur);
        if (overlap) {
            pFrom = from.clone(); pTo = to.clone(); pStart = phaseStart; pDur = phaseDur;
            from = to.clone();
        } else {
            from = pose(t0);
            pDur = 0;
        }
        to = target.clone();
        float dur = minDur;
        float[] sp = { Balance.SA_LID, Balance.SA_LID, Balance.SA_LID, Balance.SA_SLEW, Balance.SA_LUFF, Balance.SA_KNUCKLE, Balance.SA_TELE, Balance.SA_WRIST, 0.1F };
        for (int i = 0; i < N; i++) {
            float d = i == SL || i == HD ? Math.abs(Mth.wrapDegrees(to[i] - from[i])) : Math.abs(to[i] - from[i]);
            dur = Math.max(dur, d / sp[i]);
        }
        phase = p;
        phaseStart = level.getGameTime();
        phaseDur = Math.max(1, (int) Math.ceil(dur));

        Wmd.LOGGER.info("[arm] {} t{}: {} for {} ticks{}", worldPosition.toShortString(), phaseStart % 100_000L, PHASE_NAMES[p], phaseDur, overlap ? " (overlaps the previous move)" : "");
        setChanged();
        sync();
    }

    private static final String[] PHASE_NAMES = { "IDLE", "OPEN", "UNFOLD", "SLEW", "REACH", "ATTACH", "PUMP", "RELEASE", "UNREACH", "SLEW_BACK", "FOLD", "CLOSE", "BREAK" };

    private float[] ready(float slew) { return new float[] { 1, 1, 1, slew, A1_MAX, 95, 0, -60, 0 }; }

    private boolean startWanted(ServerLevel sl) {
        if (mode == M_AUTO) return true;
        if (mode == M_MANUAL) return go;
        boolean sig = false;
        Direction f = facing(), m = Machines.mount(getBlockState());
        for (int x = -1; x <= 1 && !sig; x++) sig = sl.hasNeighborSignal(Machines.toWorld(worldPosition, f, m, x, 0, 1));
        boolean edge = sig && !redstoneWas;
        redstoneWas = sig;
        if (edge) go = true;
        return go;
    }

    @Override
    protected int feCost() { return phase == P_PUMP ? Balance.SA_FE_PUMP : Balance.SA_FE_MOVE; }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (pausedAt >= 0) {

            if (energy().getEnergyStored() < Balance.SA_FE_MOVE * 20) return false;
            phaseStart += now - pausedAt; pStart += now - pausedAt; pausedAt = -1L; sync();
        }
        if (phase != P_PUMP && comparator != 0) setComparator(sl, 0);
        if (phase == P_IDLE) return idleWork(sl, now);

        BlockEntity tbe = targetBe(sl);
        if (tKind == 0 && tbe instanceof ServiceReceptacleBlockEntity r && phase >= P_REACH && phase <= P_RELEASE && now % 10 == 0)
            r.armSignal(sl, phase == P_PUMP);
        if (phase == P_ATTACH || phase == P_PUMP) {
            if (breakCheck(sl, tbe)) return true;
            if (phase == P_PUMP) { pump(sl, tbe); return true; }
        }
        if (!handOver(now)) return true;
        switch (phase) {
            case P_OPEN -> go(P_UNFOLD, ready(0), 10);
            case P_UNFOLD -> { float[] s = target(sl, 1.25); if (s == null) { lost(sl); finishJob(); } else go(P_SLEW, ready(s[SL]), 10); }
            case P_SLEW -> { float[] s = target(sl, 1.25); if (s == null) { lost(sl); finishJob(); } else go(P_REACH, s, 20); }
            case P_REACH -> {
                float[] t = target(sl, 0.05);
                if (t == null) { lost(sl); finishJob(); break; }
                t[K] = 1;
                go(P_ATTACH, t, 20);
            }
            case P_ATTACH -> {
                sl.playSound(null, tipWorld().x, tipWorld().y, tipWorld().z, WmdSounds.SA_COUPLE.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
                status = S_PUMPING; idleTicks = 0; jobMb = 0; jobFe = 0;
                phase = P_PUMP; phaseStart = now; phaseDur = 1; from = to.clone(); pDur = 0;
                Wmd.LOGGER.info("[arm] {} t{}: PUMP (coupled)", worldPosition.toShortString(), now % 100_000L);
                setChanged(); sync();
            }
            case P_RELEASE, P_BREAK -> go(P_UNREACH, ready(to[SL]), 10);
            case P_UNREACH -> go(P_SLEW_BACK, ready(0), 10);
            case P_SLEW_BACK -> go(P_FOLD, new float[] { 1, 1, 1, 0, 0, 180, 0, 180, 0 }, 20);
            case P_FOLD -> {
                Vec3 lid = wp(0, 2.5, -2);
                sl.playSound(null, lid.x, lid.y, lid.z, WmdSounds.SA_LID.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.9F);
                go(P_CLOSE, STOWED, 20);
            }
            default -> {
                phase = P_IDLE; phaseStart = now; phaseDur = 1; from = STOWED.clone(); to = STOWED.clone(); pDur = 0;
                Wmd.LOGGER.info("[arm] {} t{}: IDLE (stowed), status {}, {} mB / {} FE given", worldPosition.toShortString(), now % 100_000L, status, jobMb, jobFe);
                tShip = null; tPos = null; tKind = -1; tLast = null;
                if (status == S_PUMPING || status == S_WORKING) status = S_DONE;
                setChanged(); sync();
            }
        }
        return true;
    }

    private boolean idleWork(ServerLevel sl, long now) {
        if (now < coolUntil || (now + (worldPosition.asLong() & 15L)) % Balance.SA_SCAN_TICKS != 0) return false;
        if (!startWanted(sl)) { if (status != S_STANDBY && status != S_BREAK && status != S_DONE) { status = S_STANDBY; sync(); } return false; }
        if (jet.isEmpty() && diesel.isEmpty() && energy().getEnergyStored() < Balance.SA_GROUND_FE * 4) { setStatus(S_NO_FUEL); return false; }
        List<Cand> l = candidates(sl);
        found = l.size();
        setFound(l);
        Cand pick = null;
        for (Cand c : l) if (c.be().getBlockPos().asLong() == pinPos) { pick = c; break; }
        if (pick == null && !l.isEmpty()) pick = l.get(0);
        if (pick == null) { seenShip = null; setStatus(mode == M_AUTO ? S_IDLE : S_STANDBY); if (mode != M_AUTO && now - seenTime > 200) go = false; return false; }
        targetText = label(pick);
        if (!pick.ship().equals(seenShip) || !pick.be().getBlockPos().equals(seenPos) || seenAt == null || seenAt.distanceTo(pick.local()) > Balance.SA_PARK_MOVE) {
            seenShip = pick.ship(); seenPos = pick.be().getBlockPos(); seenAt = pick.local(); seenTime = now;
            setStatus(S_WAIT_PARK);
            return false;
        }
        if (now - seenTime < Balance.SA_PARK_TICKS) { setStatus(S_WAIT_PARK); return false; }

        tShip = pick.ship(); tPos = pick.be().getBlockPos(); tKind = pick.kind(); tLast = pick.local();
        go = false;
        status = S_WORKING;
        sl.playSound(null, wp(0, 2.5, 0).x, wp(0, 2.5, 0).y, wp(0, 2.5, 0).z, WmdSounds.SA_LID.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        go(P_OPEN, new float[] { 1, 1, 1, 0, 0, 180, 0, 180, 0 }, 40);
        Wmd.LOGGER.info("Service arm {}: serving {} on craft {}", worldPosition.toShortString(), targetText, tShip);
        return true;
    }

    private void setStatus(int s) { if (status != s) { status = s; setChanged(); sync(); } }

    private BlockEntity targetBe(ServerLevel sl) {
        if (tPos == null || tShip == null || SableBridge.shipInfo(sl, tShip) == null || !tShip.equals(SableBridge.shipIdAt(sl, tPos))) return null;
        return sl.getBlockEntity(tPos);
    }

    private void lost(ServerLevel sl) {
        BlockEntity be = targetBe(sl);
        Wmd.LOGGER.info("[arm] {}: point lost in {} ({})", worldPosition.toShortString(), PHASE_NAMES[phase],
                be == null ? "its craft / block is gone" : kindOf(be) != tKind ? "the block changed" : pointOf(sl, be, tKind) == null ? "no face point" : "out of reach now");
    }

    private float[] target(ServerLevel sl, double off) {
        BlockEntity be = targetBe(sl);
        if (be == null || kindOf(be) != tKind) return null;
        Vec3[] pt = pointOf(sl, be, tKind);
        if (pt == null) return null;
        return solve(toLocal(pt[0]), dirToLocal(pt[1]), off);
    }

    private Vec3 tipWorld() {
        float[] p = pose(level.getGameTime());
        double[] t = tip(p[A1], p[B], p[E], p[HD]);
        Vec3 l = planeToLocal(t[0], t[1], p[SL]);
        return wp(l.x, l.y, l.z);
    }

    private boolean breakCheck(ServerLevel sl, BlockEntity be) {
        Vec3[] pt = be == null || kindOf(be) != tKind ? null : pointOf(sl, be, tKind);
        Vec3 lp = pt == null ? null : toLocal(pt[0]);
        boolean snap = lp == null;
        if (!snap && phase == P_PUMP) {
            double[] t = tip(to[A1], to[B], to[E], to[HD]);
            Vec3 tl = planeToLocal(t[0], t[1], to[SL]);
            snap = tl.distanceTo(lp) > Balance.SA_BREAK_DIST || (tLast != null && tLast.distanceTo(lp) > Balance.SA_BREAK_STEP);
        }
        tLast = lp;
        if (!snap) return false;
        Vec3 w = tipWorld();
        sl.playSound(null, w.x, w.y, w.z, WmdSounds.SA_BREAKAWAY.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        sl.sendParticles(ParticleTypes.CLOUD, w.x, w.y, w.z, 14, 0.25, 0.2, 0.25, 0.03);
        status = S_BREAK;
        coolUntil = sl.getGameTime() + Balance.SA_BREAK_COOLDOWN;
        float[] back = pose(sl.getGameTime());
        back[K] = 0;
        back[A1] = Math.min(A1_MAX, back[A1] + 12.0F);
        back[E] = Math.max(0.0F, back[E] - 30.0F);
        go(P_BREAK, back, 8);
        Wmd.LOGGER.info("Service arm {}: break-away from craft {}", worldPosition.toShortString(), tShip);
        return true;
    }

    @Override public int comparatorOutput() { return comparator; }

    private void setComparator(ServerLevel sl, int v) {
        if (v == comparator) return;
        comparator = v;
        Direction f = facing(), m = Machines.mount(getBlockState());
        for (int x = X0; x <= X1; x++)
            for (int y = Y0; y <= Y1; y++)
                for (int z = Z0; z <= Z1; z++) {
                    BlockPos p = Machines.toWorld(worldPosition, f, m, x, y, z);
                    sl.updateNeighbourForOutputSignal(p, sl.getBlockState(p).getBlock());
                }
    }

    private void pump(ServerLevel sl, BlockEntity be) {
        if (sl.getGameTime() % 20 == 0 || comparator == 0) {
            int[] fill = tShip == null ? new int[] { 0, 0 } : PowerCoreBlockEntity.fuelOf(sl, tShip);
            setComparator(sl, fill[1] > 0 ? 1 + Math.round(14.0F * fill[0] / fill[1]) : 1);
        }
        boolean moved = false;
        FluidTank[] order = { jet, diesel };
        for (FluidTank t : order) {
            if (t.isEmpty()) continue;
            int n = give(sl, be, tKind, t.getFluid().getFluid(), Math.min(Balance.SA_FUEL_RATE, t.getFluidAmount()), false);
            if (n > 0) { t.drain(n, IFluidHandler.FluidAction.EXECUTE); jobMb += n; moved = true; break; }
        }
        int avail = energy().getEnergyStored() - Balance.SA_FE_PUMP * 2;
        if (avail > 0) {
            int ask = Math.min(Balance.SA_GROUND_FE, avail), took = 0;
            IEnergyStorage core = coreOf(be, tKind);
            if (core != null) took = core.receiveEnergy(ask, false);
            else if (tKind == 0 && be instanceof ServiceReceptacleBlockEntity r) took = r.acceptPower(sl, ask);
            if (took > 0 && useExtraEnergy(took)) { jobFe += took; moved = true; }
        }
        if (moved) { idleTicks = 0; setChanged(); if (sl.getGameTime() % 20 == 0) sync(); return; }
        if (++idleTicks < Balance.SA_FULL_TICKS) return;
        sl.playSound(null, tipWorld().x, tipWorld().y, tipWorld().z, WmdSounds.SA_DONE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        status = jet.isEmpty() && diesel.isEmpty() ? S_NO_FUEL : S_DONE;
        finishJob();
    }

    private void finishJob() {
        float[] back = pose(level.getGameTime());
        if (phase == P_ATTACH || phase == P_PUMP) {
            float[] s = level instanceof ServerLevel sl ? target(sl, 1.25) : null;
            if (s == null) { s = back.clone(); s[E] = Math.max(0.0F, s[E] - 20.0F); }
            s[K] = 0;
            go(P_RELEASE, s, 20);
        } else go(P_UNREACH, ready(back[SL]), 10);
    }

    @Override
    protected void idle() {
        super.idle();
        if (level == null) return;
        if (phase != P_IDLE && phase != P_PUMP && pausedAt < 0) { pausedAt = level.getGameTime(); setChanged(); sync(); }
        if (level.getGameTime() % 20 == 0 && status != S_NO_POWER) { status = S_NO_POWER; setChanged(); sync(); }
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 1, Direction.SOUTH)) return fillOnly(jet);
        if (at(part, side, 1, 0, 1, Direction.SOUTH)) return fillOnly(diesel);
        return null;
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    public static void clientTickArm(Level level, BlockPos pos, BlockState state, ServiceArmBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        long gt = level.getGameTime();
        float[] p = be.pose(gt);
        be.pScanAng = be.scanAng;
        if (p[S] > 0.5F && be.phase != P_PUMP) be.scanAng += 0.35F;
        final Vec3 at = Vec3.atCenterOf(pos).add(0.0, 2.0, 0.0);
        be.moveLoop = LoopSound.keep(be.moveLoop, gt, WmdSounds.SA_HYDRAULIC_LOOP.get(), Balance.SND_HEAVY, 1.0F, () -> at,
                () -> !be.isRemoved() && be.moving(level.getGameTime()));
        be.pumpLoop = LoopSound.keep(be.pumpLoop, gt, WmdSounds.FUEL_FLOW_LOOP.get(), Balance.SND_MACHINE, 1.0F, () -> at,
                () -> !be.isRemoved() && be.phase == P_PUMP);
    }

    float[] follow(float[] target) {
        long now = System.nanoTime();
        if (shown == null || now - shownAt > 500_000_000L || now < shownAt) {
            shown = target.clone(); shownV = new float[N]; shownAt = now;
            return shown.clone();
        }
        float dt = (now - shownAt) / 1.0e9F;
        shownAt = now;
        if (dt <= 0.0F) return shown.clone();
        float w = (float) Balance.SA_FOLLOW, e = (float) Math.exp(-w * dt);
        for (int i = 0; i < N; i++) {
            float d = i == SL || i == HD ? Mth.wrapDegrees(shown[i] - target[i]) : shown[i] - target[i];
            float tmp = (shownV[i] + w * d) * dt;
            shownV[i] = (shownV[i] - w * tmp) * e;
            shown[i] = target[i] + (d + tmp) * e;
        }
        return shown.clone();
    }

    private void record(double tt, float[] in, float[] out) {
        long now = System.nanoTime();
        if (phase == P_IDLE) {
            if (recFrames > 20) {
                double secs = Math.max(0.001, (recLast - recStart) / 1.0e9);
                Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[arm-smooth] %s: %d frames in %.1f s (%.0f fps, worst frame %d ms) | clock: %d back-steps (worst %.2f ticks), "
                        + "%d jumps (worst %.2f ticks) | biggest step a frame: timeline %.2f, drawn %.2f | %d syncs, %d moves", worldPosition.toShortString(), recFrames, secs,
                        recFrames / secs, recWorstFrame / 1_000_000L, recBack, recWorstBack, recJump, recWorstJump, recWorstIn, recWorstOut, recSyncs, recMoves));
            }
            recFrames = 0; recBack = 0; recJump = 0; recSyncs = 0; recMoves = 0; recWorstFrame = 0L; recWorstBack = 0; recWorstJump = 0; recWorstIn = 0; recWorstOut = 0;
            recLastTt = Double.NaN; recIn = null; recOut = null;
            return;
        }
        if (recFrames == 0) recStart = now;
        if (recFrames > 0 && recIn != null && !Double.isNaN(recLastTt) && now - recLast <= 500_000_000L) {
            long gap = now - recLast;
            recWorstFrame = Math.max(recWorstFrame, gap);
            double real = gap / 5.0e7, clock = tt - recLastTt;
            if (clock < -0.25) { recBack++; recWorstBack = Math.max(recWorstBack, -clock); }
            else if (clock - real > 1.5) { recJump++; recWorstJump = Math.max(recWorstJump, clock - real); }
            for (int i = SL; i <= HD; i++) {
                recWorstIn = Math.max(recWorstIn, Math.abs(i == SL || i == HD ? Mth.wrapDegrees(in[i] - recIn[i]) : in[i] - recIn[i]));
                recWorstOut = Math.max(recWorstOut, Math.abs(i == SL || i == HD ? Mth.wrapDegrees(out[i] - recOut[i]) : out[i] - recOut[i]));
            }
        }
        recFrames++;
        recLast = now; recLastTt = tt; recIn = in.clone(); recOut = out.clone();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float[] raw = pose(tt);
        final float[] p = follow(raw);
        record(tt, raw, p);
        final float d2r = Mth.DEG_TO_RAD;
        model.getBone("base").ifPresent(b -> { b.setPosX(0.0F); b.setPosZ(0.0F); b.setRotX(0.0F); b.setRotZ(0.0F); });
        model.getBone("hatch_l").ifPresent(b -> b.setRotZ(SZ * 180.0F * p[H] * d2r));
        model.getBone("hatch_r").ifPresent(b -> b.setRotZ(-SZ * 180.0F * p[H] * d2r));
        model.getBone("lift").ifPresent(b -> b.setPosY(LIFT * p[L]));
        model.getBone("turret").ifPresent(b -> b.setRotY(SY * p[SL] * d2r));
        model.getBone("scan_mast").ifPresent(b -> b.setPosY(26.0F * p[S]));
        final float sa = Mth.lerp(pt, pScanAng, scanAng);
        model.getBone("scanner").ifPresent(b -> b.setRotY(SY * sa));
        model.getBone("boom1").ifPresent(b -> b.setRotX(SX * p[A1] * d2r));

        double a = Math.toRadians(p[A1]);
        double lugF = P0F + LUGS * Math.cos(a) - LUGH * Math.sin(a), lugY = P0Y + LUGS * Math.sin(a) + LUGH * Math.cos(a);
        double d0f = P0F + LUGS - J0F, d0y = P0Y + LUGH - J0Y, df = lugF - J0F, dy = lugY - J0Y;
        final float jr = (float) (Math.atan2(dy, df) - Math.atan2(d0y, d0f));
        double n0 = Math.hypot(d0f, d0y), dl = Math.hypot(df, dy) - n0;
        final float sy = (float) (dl * d0y / n0), sz = (float) (-dl * d0f / n0);
        model.getBone("j1c").ifPresent(b -> b.setRotX(SX * jr));
        model.getBone("j1r").ifPresent(b -> { b.setPosY(sy); b.setPosZ(sz); });
        model.getBone("pinion").ifPresent(b -> b.setRotX(-SX * (p[B] - 180.0F) * GEAR_R / PIN_R * d2r));
        model.getBone("boom2").ifPresent(b -> b.setRotX(SX * (p[B] - 180.0F) * d2r));
        final float e1 = Math.min(TELE, p[E] / 2.0F), e2 = Math.min(TELE, p[E] - e1);
        model.getBone("tele1").ifPresent(b -> b.setPosZ(e1));
        model.getBone("tele2").ifPresent(b -> b.setPosZ(e2));
        model.getBone("wrist_p").ifPresent(b -> b.setRotX(SX * Mth.wrapDegrees(p[HD] - (p[A1] + p[B])) * d2r));
        model.getBone("collar").ifPresent(b -> b.setRotZ(-SZ * 35.0F * p[K] * d2r));
        final boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final String lamp = dark || status == S_NO_POWER ? "" : status == S_BREAK ? "lamp_r" : phase == P_PUMP ? "lamp_g" : "lamp_b";
        for (String l : new String[] { "lamp_b", "lamp_g", "lamp_r" }) model.getBone(l).ifPresent(b -> b.setHidden(!l.equals(lamp)));
    }

    @Override protected List<FluidTank> tanks() { return List.of(jet, diesel); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.JET_FUEL.source().get(), WmdFluids.DIESEL.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("jet_fuel", "diesel"); }
    @Override protected int rateMbPerTick() { return Balance.SA_FUEL_RATE; }
    @Override protected SoundEvent runSound() { return WmdSounds.SA_HYDRAULIC_LOOP.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 2, 0); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.service_arm", (int) Balance.SA_REACH, Balance.SA_FUEL_RATE * 20, Balance.SA_GROUND_FE * 20),
                Component.translatable("gui.wmd.info.use", Balance.SA_FE_MOVE * 20));
    }

    @Override
    protected String detail() {
        return "mode " + mode + ", phase " + phase + ", status " + status + ", points " + found + (targetText.isEmpty() ? "" : ", target " + targetText)
                + ", jet " + jet.getFluidAmount() + ", diesel " + diesel.getFluidAmount() + ", job " + jobMb + " mB / " + jobFe + " FE";
    }

    private void writeState(CompoundTag tag) {
        tag.putInt("saMode", mode);
        tag.putInt("saPhase", phase);
        tag.putLong("saT0", phaseStart);
        tag.putInt("saDur", phaseDur);
        tag.putLong("saPause", pausedAt);
        for (int i = 0; i < N; i++) { tag.putFloat("saF" + i, from[i]); tag.putFloat("saT" + i, to[i]); tag.putFloat("saPF" + i, pFrom[i]); tag.putFloat("saPT" + i, pTo[i]); }
        tag.putLong("saPS", pStart);
        tag.putInt("saPD", pDur);
        tag.putInt("saStatus", status);
        tag.putInt("saFound", found);
        tag.putString("saTarget", targetText);
        tag.putInt("saMb", jobMb);
        tag.putInt("saFe", jobFe);
        tag.putBoolean("saPinned", pinPos != Long.MIN_VALUE);
        tag.putString("saList", String.join("|", foundText));
        tag.putInt("saPinIdx", pinIdx);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        writeState(tag);
        tag.putBoolean("saGo", go);
        tag.putLong("saPin", pinPos);
        tag.putLong("saCool", coolUntil);
        if (tShip != null) tag.putUUID("saShip", tShip);
        if (tPos != null) tag.putLong("saPos", tPos.asLong());
        tag.putInt("saKind", tKind);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean client = level != null && level.isClientSide;
        ownerData.read(tag);
        copycat.load(tag, registries, client);
        mode = Mth.clamp(tag.getInt("saMode"), M_AUTO, M_MANUAL);
        phase = Mth.clamp(tag.getInt("saPhase"), P_IDLE, P_BREAK);
        phaseStart = tag.getLong("saT0");
        phaseDur = Math.max(1, tag.getInt("saDur"));
        pausedAt = tag.contains("saPause") ? tag.getLong("saPause") : -1L;
        for (int i = 0; i < N; i++) {
            from[i] = tag.contains("saF" + i) ? tag.getFloat("saF" + i) : STOWED[i];
            to[i] = tag.contains("saT" + i) ? tag.getFloat("saT" + i) : STOWED[i];
            pFrom[i] = tag.contains("saPF" + i) ? tag.getFloat("saPF" + i) : from[i];
            pTo[i] = tag.contains("saPT" + i) ? tag.getFloat("saPT" + i) : from[i];
        }
        pStart = tag.getLong("saPS");
        pDur = Math.max(0, tag.getInt("saPD"));
        status = tag.getInt("saStatus");
        found = tag.getInt("saFound");
        targetText = tag.getString("saTarget");
        jobMb = tag.getInt("saMb");
        jobFe = tag.getInt("saFe");
        String ls = tag.getString("saList");
        foundText = ls.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(ls.split("\\|")));
        pinIdx = tag.contains("saPinIdx") ? tag.getInt("saPinIdx") : -1;
        if (client) {
            cPinned = tag.getBoolean("saPinned");
            recSyncs++;
            if (phaseStart != recMoveAt) { recMoveAt = phaseStart; recMoves++; }
            return;
        }
        go = tag.getBoolean("saGo");
        pinPos = tag.contains("saPin") ? tag.getLong("saPin") : Long.MIN_VALUE;
        coolUntil = tag.getLong("saCool");
        tShip = tag.hasUUID("saShip") ? tag.getUUID("saShip") : null;
        tPos = tag.contains("saPos") ? BlockPos.of(tag.getLong("saPos")) : null;
        tKind = tag.contains("saKind") ? tag.getInt("saKind") : -1;
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
