package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CargoRampBlockEntity extends ProcessUnitBlockEntity implements Owned, CopycatState.Host, ComparatorMachine {

    public static final int A_OPEN = 1, A_LEVEL = 2, A_GROUND = 3, A_CLOSE = 4, A_NEXT = 5, A_ALL = 6, A_STATIC = 7, A_FIRST = A_OPEN, A_LAST = A_STATIC;
    public static final int S_CLOSED = 0, S_MOVING = 1, S_LEVEL = 2, S_GROUND = 3, S_NO_RECT = 4;

    static final double PIV_H = 2.0 / 16.0, PIV_IN = 2.0 / 16.0, FACE = 2.0 / 16.0;
    static final double LEVEL = 90.0, GROUND_MAX = 90.0 + Balance.RAMP_GROUND_MAX;

    private static final Map<UUID, int[]> CLICKS = Statics.server(new HashMap<>());

    private static final Map<net.minecraft.resources.ResourceKey<Level>, java.util.Set<Long>> ACTIVE = Statics.server(new HashMap<>());

    private static final Map<net.minecraft.resources.ResourceKey<Level>, CargoRampBlockEntity[]> FLOORS = Statics.server(new java.util.concurrent.ConcurrentHashMap<>());
    private volatile UUID floorShip;

    private static void refloor(ServerLevel sl, CargoRampBlockEntity me) {
        java.util.Set<Long> a = ACTIVE.get(sl.dimension());
        List<CargoRampBlockEntity> l = new ArrayList<>();
        if (a != null) for (Long p : a) {
            BlockPos bp = BlockPos.of(p);
            CargoRampBlockEntity r = me != null && me.worldPosition.equals(bp) ? me : sl.isLoaded(bp) && sl.getBlockEntity(bp) instanceof CargoRampBlockEntity x ? x : null;
            if (r == null) continue;
            r.floorShip = SableBridge.shipIdAt(sl, bp);
            l.add(r);
        }
        if (l.isEmpty()) FLOORS.remove(sl.dimension());
        else FLOORS.put(sl.dimension(), l.toArray(new CargoRampBlockEntity[0]));
    }

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int w, h, rows = Balance.RAMP_ROWS_DEF;

    float theta, phi, toe, lock;
    private float thetaT = (float) LEVEL;
    private boolean open, staticLine = true;
    private long syncAt, rollUntil;

    private int laneLight, laneHeavy, dropLeft;
    private long nextScan, nextDrop;
    private boolean sig, wasMoving;
    private final Map<UUID, Long> seen = new LinkedHashMap<>();

    public CargoRampBlockEntity(BlockPos pos, BlockState state) {
        super(CargoBlocks.CARGO_RAMP_BE.get(), pos, state, 0, Balance.RAMP_BUFFER, Balance.RAMP_RECEIVE);
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

    public int width() { return w; }
    public int height() { return h; }

    public int leafRows() { return Math.max(0, Math.min(rows, h)); }
    public int doorRows() { return Math.max(0, h - leafRows()); }
    public boolean opened() { return open; }
    public boolean staticLineOn() { return staticLine; }
    public int laneLight() { return laneLight; }
    public int laneHeavy() { return laneHeavy; }
    long syncAtClient() { return syncAt; }
    long rollUntilClient() { return rollUntil; }

    @Override public int comparatorOutput() { return Math.min(15, laneLight + laneHeavy); }
    float thetaTarget() { return thetaT; }
    boolean head() { return getBlockState().getValue(CargoRampBlock.HEAD); }

    double leafLength() { return (16.0 * leafRows() - 2.0) / 16.0; }

    public int rampState() {
        if (w == 0) return S_NO_RECT;
        if (!open && lock <= 0.0F) return S_CLOSED;
        if (settledOpen()) return thetaT > LEVEL + 0.5 ? S_GROUND : S_LEVEL;
        return S_MOVING;
    }

    boolean settledOpen() { return open && lock >= 1.0F && (doorRows() == 0 || phi >= 90.0F) && Math.abs(theta - thetaT) < 0.01F && toe == toeTarget(); }

    private float toeTarget() { return thetaT > LEVEL + 0.5 ? 1.0F : 0.0F; }

    static boolean step(float[] p, boolean open, float thetaT, boolean door, float toeT) {
        if (open) {
            if (p[3] < 1.0F) { p[3] = Math.min(1.0F, p[3] + 1.0F / Balance.RAMP_LOCK_TICKS); return true; }
            if (door && p[1] < 90.0F) { p[1] = (float) Math.min(90.0, p[1] + Balance.RAMP_DOOR_RATE); return true; }
            if (p[2] > 0.0F && (Math.abs(p[0] - thetaT) > 0.01F || toeT <= 0.0F)) { p[2] = Math.max(0.0F, p[2] - 1.0F / Balance.RAMP_TOE_TICKS); return true; }
            if (Math.abs(p[0] - thetaT) > 0.01F) { p[0] = approach(p[0], thetaT, (float) Balance.RAMP_LEAF_RATE); return true; }
            if (p[2] < toeT) { p[2] = Math.min(toeT, p[2] + 1.0F / Balance.RAMP_TOE_TICKS); return true; }
            return false;
        }
        if (p[2] > 0.0F) { p[2] = Math.max(0.0F, p[2] - 1.0F / Balance.RAMP_TOE_TICKS); return true; }
        if (p[0] > 0.0F) { p[0] = approach(p[0], 0.0F, (float) Balance.RAMP_LEAF_RATE); return true; }
        if (p[1] > 0.0F) { p[1] = (float) Math.max(0.0, p[1] - Balance.RAMP_DOOR_RATE); return true; }
        if (p[3] > 0.0F) { p[3] = Math.max(0.0F, p[3] - 1.0F / Balance.RAMP_LOCK_TICKS); return true; }
        return false;
    }

    private static float approach(float v, float t, float rate) { return v < t ? Math.min(t, v + rate) : Math.max(t, v - rate); }

    float[] poseAfter(double ticks) {
        float[] p = { theta, phi, toe, lock };
        int n = (int) Math.min(400, Math.max(0, Math.floor(ticks)));
        boolean door = doorRows() > 0;
        float tt = thetaT, toeT = toeTarget();
        for (int i = 0; i < n; i++) if (!step(p, open, tt, door, toeT)) break;
        float[] q = p.clone();
        double f = ticks - Math.floor(ticks);
        if (f > 0 && n < 400 && step(q, open, tt, door, toeT)) for (int i = 0; i < 4; i++) p[i] += (q[i] - p[i]) * (float) f;
        return p;
    }

    Vec3 out() { Direction f = facing(); return new Vec3(f.getStepX(), 0, f.getStepZ()); }
    Vec3 right() { Direction r = Machines.dirToWorld(facing(), Direction.EAST); return new Vec3(r.getStepX(), 0, r.getStepZ()); }

    Vec3 gridPoint(double a, double u, double y) {
        Vec3 c = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
        return c.add(out().scale(a + 0.5)).add(right().scale(u - 0.5)).add(0.0, y, 0.0);
    }

    double[] local(Vec3 g) {
        Vec3 d = g.subtract(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
        return new double[] { d.dot(out()) - 0.5, d.dot(right()) + 0.5, d.y };
    }

    static double[] leafAt(double s, double th) {
        double t = Math.toRadians(th);
        double ua = Math.sin(t), uy = Math.cos(t), na = -Math.cos(t), ny = Math.sin(t);
        return new double[] { -PIV_IN + FACE * na + s * ua, PIV_H + FACE * ny + s * uy };
    }

    double[] tip(double th) { return leafAt(leafLength(), th); }

    double leafHeight(double a, double th) {
        double t = Math.toRadians(th), ua = Math.sin(t);
        if (ua < 0.2) return Double.NaN;
        double[] h0 = leafAt(0.0, th);
        double s = (a - h0[0]) / ua;
        if (s < 0.0 || s > leafLength()) return Double.NaN;
        return h0[1] + s * Math.cos(t);
    }

    Vec3 toWorld(Vec3 grid) {
        if (!(level instanceof ServerLevel sl)) return grid;
        Vec3 w2 = SableBridge.toWorld(sl, grid);
        return w2 == null ? grid : w2;
    }

    Vec3 toGrid(Vec3 world) {
        if (!(level instanceof ServerLevel sl)) return world;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship == null) return world;
        Vec3 g = SableBridge.toShip(sl, ship, world);
        return g == null ? world : g;
    }

    private boolean tile(int c, int r) { return level != null && CargoRampBlock.same(level, Machines.toWorld(worldPosition, facing(), c, r, 0), facing()); }

    boolean measure() {
        int nw = 0, nh = 0;
        while (nw < Balance.RAMP_W_MAX && tile(nw, 0)) nw++;
        while (nh < Balance.RAMP_H_MAX && tile(0, nh)) nh++;
        boolean whole = nw >= Balance.RAMP_W_MIN && nh >= Balance.RAMP_H_MIN && !tile(nw, 0) && !tile(0, nh);
        for (int c = 0; whole && c < nw; c++) for (int r = 0; whole && r < nh; r++) if (!tile(c, r)) whole = false;
        if (!whole) { nw = 0; nh = 0; }
        if (nw != w || nh != h) { w = nw; h = nh; sync(); }
        return whole;
    }

    private boolean whole() {
        for (int c = 0; c < w; c++) for (int rr = 0; rr < h; rr++) if (!tile(c, rr)) return false;
        return true;
    }

    private void setOpenState(ServerLevel sl, boolean o) {
        for (int c = 0; c < w; c++) for (int r = 0; r < h; r++) {
            BlockPos p = Machines.toWorld(worldPosition, facing(), c, r, 0);
            BlockState s = sl.getBlockState(p);
            if (s.getBlock() instanceof CargoRampBlock && s.getValue(CargoRampBlock.OPEN) != o) sl.setBlock(p, s.setValue(CargoRampBlock.OPEN, o), 2);
        }
    }

    Component cycleRows() {
        if (open || lock > 0.0F) return Component.translatable("message.wmd.ramp.close_first");
        int max = Math.max(Balance.RAMP_ROWS_MIN, Math.min(Balance.RAMP_ROWS_MAX, h == 0 ? Balance.RAMP_ROWS_MAX : h));
        rows = rows >= max ? Balance.RAMP_ROWS_MIN : rows + 1;
        sync();
        return Component.translatable("message.wmd.ramp.rows", leafRows(), doorRows());
    }

    String openRamp(ServerLevel sl, int mode) {
        if (!AirGate.on()) return "message.wmd.ramp.off";
        if (!head() || !measure()) return "message.wmd.ramp.not_rect";
        boolean wasOpen = open || lock > 0.0F;
        double g = groundAngle(sl);
        float t = (float) (mode == A_LEVEL ? LEVEL : mode == A_GROUND ? (Double.isNaN(g) ? GROUND_MAX : g) : (Double.isNaN(g) ? LEVEL : g));
        if (!wasOpen) {
            int cost = w * h * Balance.RAMP_FE_PER_TILE;
            UUID ship = SableBridge.shipIdAt(sl, worldPosition);
            boolean paid = ship != null && ShipPower.draw(sl, ship, cost);
            if (!paid) {
                if (energy().getEnergyStored() < cost) return "message.wmd.ramp.no_power";
                useExtraEnergy(cost);
            }
            setOpenState(sl, true);
            ACTIVE.computeIfAbsent(sl.dimension(), k -> new java.util.LinkedHashSet<>()).add(worldPosition.asLong());
            refloor(sl, this);
            playOnce(sl, WmdSounds.RAMP_LOCK.get(), Balance.SND_CLICK, 1.0F);
        }
        open = true;
        thetaT = t;
        snapshot(sl);
        Wmd.LOGGER.info("Cargo ramp at {}: open {} ({} x {}, leaf {} rows, target {} deg)", worldPosition, mode, w, h, leafRows(), t);
        return t > LEVEL + 0.5 ? "message.wmd.ramp.ground" : "message.wmd.ramp.level";
    }

    String closeRamp(ServerLevel sl) {
        if (!open) return "message.wmd.ramp.closed";
        open = false;
        dropLeft = 0;
        snapshot(sl);
        return "message.wmd.ramp.closing";
    }

    String toggle(ServerLevel sl) { return open ? closeRamp(sl) : openRamp(sl, A_OPEN); }

    String drop(ServerLevel sl, int n) {
        if (!settledOpen()) return "message.wmd.ramp.not_open";
        scanLane(sl);
        if (laneLight + laneHeavy == 0) return "message.wmd.ramp.lane_empty";
        dropLeft = n < 0 ? Integer.MAX_VALUE : dropLeft + n;
        nextDrop = Math.min(nextDrop, sl.getGameTime());
        return n < 0 ? "message.wmd.ramp.stick" : "message.wmd.ramp.next";
    }

    void act(int action, int[] data, Player player) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !head()) return;
        if (AirframeRecords.tooSoon(CLICKS, sp, 1, Balance.RAMP_ACT_GAP)) return;
        String k = switch (action) {
            case A_OPEN, A_LEVEL, A_GROUND -> openRamp(sl, action);
            case A_CLOSE -> closeRamp(sl);
            case A_NEXT -> drop(sl, 1);
            case A_ALL -> drop(sl, -1);
            case A_STATIC -> { staticLine = !staticLine; sync(); yield staticLine ? "message.wmd.ramp.static_on" : "message.wmd.ramp.static_off"; }
            default -> null;
        };
        if (k != null) player.displayClientMessage(Component.translatable(k), true);
    }

    int hudCode() {
        if (dropLeft > 0 && settledOpen()) return 4;
        return switch (rampState()) { case S_MOVING -> 1; case S_LEVEL -> 2; case S_GROUND -> 3; default -> 0; };
    }

    static int hudAboard(ServerLevel sl, UUID ship) {
        int best = 0;
        if (ship == null) return 0;
        for (BlockPos q : SableBridge.blockEntitiesOn(sl, ship))
            if (sl.isLoaded(q) && sl.getBlockEntity(q) instanceof CargoRampBlockEntity r && r.head()) best = Math.max(best, r.hudCode());
        return best;
    }

    static void pilot(ServerLevel sl, UUID ship, ServerPlayer p, boolean stick) {
        if (!AirGate.on() || ship == null) return;
        String k = null;
        for (BlockPos q : SableBridge.blockEntitiesOn(sl, ship)) {
            if (!sl.isLoaded(q) || !(sl.getBlockEntity(q) instanceof CargoRampBlockEntity r) || !r.head()) continue;
            k = stick ? r.drop(sl, -1) : r.toggle(sl);
        }
        p.displayClientMessage(Component.translatable(k == null ? "message.wmd.ramp.none_aboard" : k), true);
    }

    double groundAngle(ServerLevel sl) {
        for (double th = LEVEL; th <= GROUND_MAX + 1e-6; th += 2.0) {
            double[] t = tip(th);
            Vec3 wp = toWorld(gridPoint(t[0], w / 2.0, t[1]));
            if (solidAt(sl, wp) || solidAt(sl, wp.add(0, -0.15, 0))) return Math.max(LEVEL, th - 1.0);
        }
        return Double.NaN;
    }

    static boolean solidAt(ServerLevel sl, Vec3 q) {
        BlockPos b = BlockPos.containing(q);
        if (b.getY() < sl.getMinBuildHeight() || b.getY() >= sl.getMaxBuildHeight() || !sl.isLoaded(b)) return false;
        BlockState s = sl.getBlockState(b);
        if (!s.getFluidState().isEmpty()) return true;
        if (s.isAir()) return false;
        double lx = q.x - b.getX(), ly = q.y - b.getY(), lz = q.z - b.getZ();
        for (AABB box : s.getCollisionShape(sl, b).toAabbs())
            if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) return true;
        return false;
    }

    static double clearBelow(ServerLevel sl, Vec3 q, double max) {
        for (double d = 0.0; d < max; d += 0.5) if (solidAt(sl, q.add(0, -d, 0))) return d;
        return max;
    }

    record LanePallet(BlockPos ctrl, boolean heavy, int depth, double u) {}

    List<LanePallet> lane(ServerLevel sl) {
        List<LanePallet> out = new ArrayList<>();
        if (w == 0) return out;
        boolean[] blocked = new boolean[w];
        Map<BlockPos, int[]> seenP = new LinkedHashMap<>();
        Direction f = facing();
        for (int d = 1; d <= Balance.RAMP_LANE; d++) {
            for (int c = 0; c < w; c++) {
                BlockPos p = Machines.toWorld(worldPosition, f, c, 0, d);
                if (!sl.isLoaded(p)) { blocked[c] = true; continue; }
                BlockPos ctrl = CargoPalletBlock.controllerAt(sl, p);
                if (ctrl == null) {
                    if (!sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()) blocked[c] = true;
                    continue;
                }
                int[] e = seenP.computeIfAbsent(ctrl, k -> new int[] { 99, 99, -1, 0 });
                e[0] = Math.min(e[0], d); e[1] = Math.min(e[1], c); e[2] = Math.max(e[2], c);
                if (blocked[c]) e[3] = 1;
            }
        }
        for (Map.Entry<BlockPos, int[]> en : seenP.entrySet()) {
            int[] e = en.getValue();
            if (e[3] != 0) continue;
            BlockState s = sl.getBlockState(en.getKey());
            if (!(s.getBlock() instanceof CargoPalletBlock pb)) continue;
            int size = pb.heavy() ? 2 : 1;
            if (e[2] - e[1] + 1 != size) continue;
            out.add(new LanePallet(en.getKey(), pb.heavy(), e[0], e[1] + size / 2.0));
        }
        out.sort((a, b) -> Integer.compare(a.depth(), b.depth()));
        return out;
    }

    private void scanLane(ServerLevel sl) {
        int l = 0, hv = 0;
        for (LanePallet p : lane(sl)) { if (p.heavy()) hv++; else l++; }
        if (l != laneLight || hv != laneHeavy) {
            laneLight = l; laneHeavy = hv; sync();
            for (int c = 0; c < w; c++) for (int r = 0; r < h; r++) {
                BlockPos p = Machines.toWorld(worldPosition, facing(), c, r, 0);
                sl.updateNeighbourForOutputSignal(p, sl.getBlockState(p).getBlock());
            }
        }
    }

    private boolean releaseNext(ServerLevel sl, long now) {
        List<LanePallet> l = lane(sl);
        if (l.isEmpty()) return false;
        LanePallet lp = l.get(0);
        if (!(sl.getBlockEntity(lp.ctrl()) instanceof CargoPalletBlockEntity pe) || !(sl.getBlockState(lp.ctrl()).getBlock() instanceof CargoPalletBlock)) return false;

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack st : pe.items()) items.add(st.copy());
        PalletEntity e = new PalletEntity(CargoBlocks.PALLET.get(), sl);
        double a0 = -(lp.depth() + (lp.heavy() ? 1.0 : 0.5));
        e.startRoll(this, lp.heavy(), items, a0, lp.u(), now);
        if (!sl.addFreshEntity(e)) return false;
        pe.takeAllForRelease();
        sl.removeBlock(lp.ctrl(), false);
        Autopilot.ownDrop(sl, SableBridge.shipIdAt(sl, worldPosition));
        playOnce(sl, WmdSounds.RAMP_LOCK.get(), Balance.SND_CLICK, 1.25F);
        Wmd.LOGGER.info("Cargo ramp at {}: pallet {} away ({} stacks)", worldPosition, lp.heavy() ? "heavy" : "light", items.stream().filter(s -> !s.isEmpty()).count());
        nextDrop = now + Balance.PALLET_GAP_TICKS * (lp.heavy() ? 2 : 1);
        rollUntil = now + Balance.RAMP_ROLLER_TICKS;
        sync();
        return true;
    }

    private void riders(ServerLevel sl, long now) {
        if (w == 0 || theta < 30.0F) return;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        double speed = ship == null ? 0.0 : Crafts.speed(sl, ship, null);
        double[] tp = tip(theta);
        AABB box = boxOf(-3.0, tp[0] + 1.5, -0.5, w + 0.5, Math.min(tp[1], 0.0) - 1.5, h + 1.0);
        for (Entity e : sl.getEntities((Entity) null, box, en -> !en.isSpectator() && !en.isPassenger() && !(en instanceof PalletEntity))) {
            double[] l = local(toGrid(e.position()));
            boolean onRamp = l[1] >= -0.3 && l[1] <= w + 0.3 && l[0] >= -3.0 && l[0] <= tp[0] + 0.5 && l[2] > Math.min(tp[1], 0.0) - 1.2 && l[2] < h;
            if (onRamp && e instanceof Player pl) seen.put(pl.getUUID(), now);

            if (e instanceof Player) continue;
            if (!onRamp || speed >= Balance.RAMP_CARRY_SPEED || l[0] < 0.0) continue;
            double y = leafHeight(l[0], theta);
            if (Double.isNaN(y) || l[2] < y - 0.8 || l[2] > y + 0.6) continue;
            Vec3 nw = toWorld(gridPoint(l[0], l[1], y));
            e.teleportTo(nw.x, nw.y, nw.z);
            e.setDeltaMovement(e.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            e.fallDistance = 0.0F;
            e.setOnGround(true);
        }
        if (!staticLine || thetaT > LEVEL + 0.5) { seen.clear(); return; }
        for (Iterator<Map.Entry<UUID, Long>> it = seen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            if (now - en.getValue() > Balance.STATIC_LINE_TICKS) { it.remove(); continue; }
            Player p = sl.getPlayerByUUID(en.getKey());
            if (p == null || p.isPassenger() || p.onGround() || p.isSpectator()) continue;
            double[] l = local(toGrid(p.position()));
            boolean away = l[0] > tp[0] + 0.3 || l[2] < Math.min(tp[1], 0.0) - 1.5;
            if (!away || p.getDeltaMovement().y > -0.05) continue;
            it.remove();
            if (clearBelow(sl, p.position(), Balance.STATIC_LINE_MIN) < Balance.STATIC_LINE_MIN) continue;
            ParachuteEntity c = new ParachuteEntity(CargoBlocks.PARACHUTE.get(), sl);
            c.setup(p.position(), p.getDeltaMovement(), p.getYRot());
            sl.addFreshEntity(c);
            p.startRiding(c, true);
            p.displayClientMessage(Component.translatable("message.wmd.ramp.static_line"), true);
        }
    }

    private AABB boxOf(double a0, double a1, double u0, double u1, double y0, double y1) {
        AABB b = null;
        for (double a : new double[] { a0, a1 }) for (double u : new double[] { u0, u1 }) for (double y : new double[] { y0, y1 }) {
            Vec3 p = toWorld(gridPoint(a, u, y));
            b = b == null ? new AABB(p, p) : b.minmax(new AABB(p, p));
        }
        return b.inflate(0.5);
    }

    private long nextCharge;

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl) || !head() || !AirGate.on()) return false;
        long now = sl.getGameTime();

        if (now >= nextCharge) {
            nextCharge = now + Balance.RAMP_CORE_CHARGE_TICKS;
            int missing = Balance.RAMP_BUFFER - energy().getEnergyStored();
            UUID cs = missing > 0 ? SableBridge.shipIdAt(sl, worldPosition) : null;
            if (cs != null) {
                int n = Math.min(missing, Balance.RAMP_CORE_CHARGE);
                if (ShipPower.draw(sl, cs, n)) chargeDirect(n);
            }
        }
        boolean s = sl.hasNeighborSignal(worldPosition);
        if (s && !sig && settledOpen()) drop(sl, 1);
        sig = s;
        if (now >= nextScan) {
            nextScan = now + Balance.RAMP_SCAN_TICKS;

            if (!open && lock <= 0.0F) { measure(); if (w > 0) setOpenState(sl, false); }
            else if (w > 0 && !whole()) {
                open = false; dropLeft = 0;
                theta = 0.0F; phi = 0.0F; toe = 0.0F; lock = 0.0F; wasMoving = false;
                setOpenState(sl, false);
                inactive(sl);
                measure();
                if (w > 0) setOpenState(sl, false);
                snapshot(sl);
            }
            if (w > 0) scanLane(sl);
        }
        if (w == 0) return false;
        float[] p = { theta, phi, toe, lock };
        float[] before = p.clone();
        boolean moving = step(p, open, thetaT, doorRows() > 0, toeTarget());
        theta = p[0]; phi = p[1]; toe = p[2]; lock = p[3];
        if (moving) {
            for (int i = 0; i < 4; i++) {
                boolean done = before[i] != p[i] && stageDone(i, p);
                if (done) switch (i) {
                    case 0 -> playOnce(sl, WmdSounds.RAMP_CLUNK.get(), Balance.SND_MECH, 1.0F);
                    case 1 -> playOnce(sl, WmdSounds.RAMP_CLUNK.get(), Balance.SND_MECH, 1.2F);
                    case 2 -> playOnce(sl, WmdSounds.RAMP_TOES.get(), Balance.SND_CLICK, 1.0F);
                    default -> playOnce(sl, WmdSounds.RAMP_LOCK.get(), Balance.SND_CLICK, 1.0F);
                }
            }
            if (now % 20 == 0) snapshot(sl);
        } else if (wasMoving) {
            snapshot(sl);
            if (!open) { setOpenState(sl, false); inactive(sl); }
        }
        wasMoving = moving;
        if (open) riders(sl, now);
        if (settledOpen() && dropLeft > 0 && now >= nextDrop) {
            if (releaseNext(sl, now)) { if (dropLeft != Integer.MAX_VALUE) dropLeft--; }
            else dropLeft = 0;
            scanLane(sl);
        }
        return moving;
    }

    private void inactive(ServerLevel sl) {
        java.util.Set<Long> a = ACTIVE.get(sl.dimension());
        if (a != null) { a.remove(worldPosition.asLong()); if (a.isEmpty()) ACTIVE.remove(sl.dimension()); }
        refloor(sl, null);
    }

    public static boolean floorAt(ServerLevel level, Vec3 q, UUID own) {
        CargoRampBlockEntity[] a = FLOORS.get(level.dimension());
        if (a == null) return false;
        for (CargoRampBlockEntity r : a) {
            if (r.isRemoved() || r.w == 0 || r.theta < 30.0F) continue;
            UUID ship = r.floorShip;
            if (own != null && own.equals(ship)) continue;
            Vec3 g = ship == null ? q : SableBridge.toShip(level, ship, q);
            double[] l = r.local(g == null ? q : g);
            if (l[1] < 0.0 || l[1] > r.w || l[0] < 0.0) continue;
            double y = r.leafHeight(l[0], r.theta);
            if (!Double.isNaN(y) && l[2] <= y && l[2] >= y - 0.6) return true;
        }
        return false;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) inactive(sl);
        super.setRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && (open || lock > 0.0F)) {
            ACTIVE.computeIfAbsent(sl.dimension(), k -> new java.util.LinkedHashSet<>()).add(worldPosition.asLong());
            refloor(sl, this);
        }
    }

    private Object windLoop, rollLoop;

    void clientLoops(Level lv) {
        if (w == 0) return;
        long now = lv.getGameTime();
        windLoop = LoopSound.keep(windLoop, now, WmdSounds.RAMP_WIND.get(), Balance.SND_SMALL, 1.0F, () -> clientWorld(lv, -1.5, w / 2.0, 1.5),
                () -> !isRemoved() && open && theta >= 80.0F && thetaT <= LEVEL + 0.5);
        rollLoop = LoopSound.keep(rollLoop, now, WmdSounds.PALLET_ROLL.get(), Balance.SND_SMALL, 1.0F, () -> clientWorld(lv, 0.0, w / 2.0, 0.5),
                () -> !isRemoved() && lv.getGameTime() < rollUntil);
    }

    private Vec3 clientWorld(Level lv, double a, double u, double y) {
        Vec3 g = gridPoint(a, u, y), wp = SableBridge.toWorld(lv, g);
        return wp == null ? g : wp;
    }

    private boolean stageDone(int i, float[] p) {
        return switch (i) {
            case 0 -> Math.abs(p[0] - thetaT) < 0.01F || p[0] <= 0.0F;
            case 1 -> p[1] >= 90.0F || p[1] <= 0.0F;
            case 2 -> p[2] >= 1.0F || p[2] <= 0.0F;
            default -> p[3] >= 1.0F || p[3] <= 0.0F;
        };
    }

    private void snapshot(ServerLevel sl) {
        syncAt = sl.getGameTime();
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RAMP_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return head() && side == Machines.dirToWorld(facing(), Direction.WEST) ? energy() : null; }

    @Override protected String detail() { return head() ? w + " x " + h + ", leaf " + leafRows() + ", " + new String[] { "CLOSED", "MOVING", "LEVEL", "GROUND", "NO RECT" }[rampState()] : "tile"; }

    @Override
    public Component status() {
        int st = rampState();
        return Component.translatable("block.wmd.cargo_ramp").append(": ").append(Component.translatable("message.wmd.ramp.status_" + st, w, h, leafRows(), doorRows(),
                laneLight, laneHeavy, energy().getEnergyStored(), w * h * Balance.RAMP_FE_PER_TILE));
    }

    private void put(CompoundTag tag) {
        tag.putInt("crW", w); tag.putInt("crH", h); tag.putInt("crRows", rows);
        tag.putFloat("crTh", theta); tag.putFloat("crPhi", phi); tag.putFloat("crToe", toe); tag.putFloat("crLock", lock);
        tag.putFloat("crThT", thetaT); tag.putBoolean("crOpen", open); tag.putBoolean("crStatic", staticLine);
        tag.putInt("crLaneL", laneLight); tag.putInt("crLaneH", laneHeavy);
    }

    private void get(CompoundTag tag) {
        w = tag.getInt("crW"); h = tag.getInt("crH");
        rows = tag.contains("crRows") ? Math.max(Balance.RAMP_ROWS_MIN, Math.min(Balance.RAMP_ROWS_MAX, tag.getInt("crRows"))) : Balance.RAMP_ROWS_DEF;
        theta = clamp(tag.getFloat("crTh"), 0F, (float) GROUND_MAX); phi = clamp(tag.getFloat("crPhi"), 0F, 90F);
        toe = clamp(tag.getFloat("crToe"), 0F, 1F); lock = clamp(tag.getFloat("crLock"), 0F, 1F);
        thetaT = tag.contains("crThT") ? clamp(tag.getFloat("crThT"), (float) LEVEL, (float) GROUND_MAX) : (float) LEVEL;
        open = tag.getBoolean("crOpen"); staticLine = !tag.contains("crStatic") || tag.getBoolean("crStatic");
        laneLight = tag.getInt("crLaneL"); laneHeavy = tag.getInt("crLaneH");
    }

    private static float clamp(float v, float lo, float hi) { return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        put(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        get(tag);
        if (tag.contains("crAt")) syncAt = tag.getLong("crAt");
        if (tag.contains("crRoll")) rollUntil = tag.getLong("crRoll");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        put(tag);
        tag.putLong("crAt", syncAt);
        tag.putLong("crRoll", rollUntil);
        return tag;
    }
}
