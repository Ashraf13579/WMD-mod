package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DeckConsoleBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    static final float LID_OPEN = 105.0F, VISOR_OPEN = 90.0F;
    static final double PIN_Y = 20.5, PIN_Z = 3.5, STRUT_AY = 17.6, STRUT_AZ = -5.0, STRUT_LY = 21.0, STRUT_LZ = 0.5;

    static final float LID_SIGN = 1.0F;

    static final int K_LIFT = 0, K_CAT = 1, K_WIRE = 2, K_LIGHT = 3, K_RAMP = 4, K_DOOR = 5, K_CRAFT = 6, K_ARM = 7, K_DEPOT = 8, K_GANTRY = 9,
            K_TURN = 10;

    static final int A_REFRESH = 0, A_LIFT = 1, A_LAUNCH = 2, A_LIGHTS = 3, A_RAMP = 4, A_DOOR = 5, A_FUEL = 6, A_STOCK = 7, A_FIX = 8, A_TURN = 9, A_LAST = A_TURN;

    static final int W_FUEL = 1, W_FUEL_BUSY = 2, W_STOCK = 4, W_STOCK_ON = 8, W_FIX = 16, W_FIX_ON = 32;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private boolean unfolded, noPower;
    private ListTag marks = new ListTag();

    private long openUntil = Long.MIN_VALUE / 2, lastScan = Long.MIN_VALUE / 2;
    private final List<CompoundTag> wing = new ArrayList<>();
    private CompoundTag sum = new CompoundTag();
    private final List<Long> lights = new ArrayList<>(), arms = new ArrayList<>(), depots = new ArrayList<>(), gantries = new ArrayList<>();

    private float lidT;
    private double lastT = Double.NaN;

    public DeckConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(DeckConsoleBlocks.DECK_CONSOLE_BE.get(), pos, state, Balance.FDC_FE);
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

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    Direction front() { return getBlockState().getValue(MachineBlock.FACING); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return part.equals(worldPosition) && side == front().getOpposite() ? energy() : null; }

    public boolean unfolded() { return unfolded; }
    public boolean noPower() { return noPower; }

    public ListTag marks() { return marks; }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        lid(sl, true);
        return unfolded;
    }

    @Override
    protected void idle() {
        if (!(level instanceof ServerLevel sl)) return;
        boolean asked = sl.getGameTime() < openUntil && AirGate.on();
        UUID ship = asked ? SableBridge.shipIdAt(sl, worldPosition) : null;
        lid(sl, ship != null && ShipPower.draw(sl, ship, Balance.FDC_FE));
    }

    private void lid(ServerLevel sl, boolean power) {
        boolean asked = sl.getGameTime() < openUntil && AirGate.on();
        boolean want = asked && power, np = asked && !power;
        boolean changed = want != unfolded || np != noPower;
        if (want != unfolded) {
            unfolded = want;
            SoundEvent s = want ? WmdSounds.CATAPULT_LATCH.get() : WmdSounds.TPOD_CLICK.get();
            playOnce(sl, s, Balance.SND_CLICK, want ? 1.1F : 0.9F);
        }
        noPower = np;
        if (changed) sync();
    }

    void ask(ServerPlayer sp, boolean open) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return;
        long now = sl.getGameTime();
        openUntil = now + Balance.FDC_SCAN_TICKS + 20 + Balance.FDC_FOLD_TICKS;
        if (now - lastScan >= Balance.FDC_SCAN_TICKS || now < lastScan) scan(sl);
        send(sp, open, "");
    }

    private void send(ServerPlayer sp, boolean open, String msg) {
        CompoundTag t = new CompoundTag();
        ListTag rows = new ListTag();
        rows.addAll(wing);
        t.put("rows", rows);
        t.put("sum", sum.copy());
        t.putString("msg", msg);
        t.putBoolean("np", noPower);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new DeckConsolePayloads.Wing(worldPosition, open, t));
    }

    private UUID base(ServerLevel sl) { return SableBridge.shipIdAt(sl, worldPosition); }

    private Vec3 rel(ServerLevel sl, UUID base, Vec3 world) {
        Vec3 p = base == null ? world : SableBridge.toShip(sl, base, world);
        return p == null ? null : p.subtract(Vec3.atCenterOf(worldPosition));
    }

    private static boolean inRange(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= Balance.FDC_RANGE * Balance.FDC_RANGE && Math.abs(dy) <= Balance.FDC_RANGE_Y;
    }

    private boolean inRange(BlockPos p) { return inRange(p.getX() - worldPosition.getX(), p.getY() - worldPosition.getY(), p.getZ() - worldPosition.getZ()); }

    private List<BlockEntity> deckEntities(ServerLevel sl, UUID base) {
        List<BlockEntity> out = new ArrayList<>();
        if (base != null) {
            for (BlockPos p : SableBridge.blockEntitiesOn(sl, base))
                if (inRange(p) && sl.getBlockEntity(p) instanceof BlockEntity be) out.add(be);
            return out;
        }
        int r = (int) Math.ceil(Balance.FDC_RANGE);
        int cx0 = (worldPosition.getX() - r) >> 4, cx1 = (worldPosition.getX() + r) >> 4, cz0 = (worldPosition.getZ() - r) >> 4, cz1 = (worldPosition.getZ() + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk c = sl.getChunkSource().getChunkNow(cx, cz);
                if (c == null) continue;
                for (BlockEntity be : c.getBlockEntities().values()) if (!be.isRemoved() && inRange(be.getBlockPos())) out.add(be);
            }
        return out;
    }

    private static CompoundTag mark(int kind, BlockPos rel, long pos) {
        CompoundTag t = new CompoundTag();
        t.putByte("k", (byte) kind);
        t.putInt("x", rel.getX());
        t.putInt("y", rel.getY());
        t.putInt("z", rel.getZ());
        t.putLong("p", pos);
        return t;
    }

    void scan(ServerLevel sl) {
        lastScan = sl.getGameTime();
        UUID owner = getOwner(), base = base(sl);
        ListTag m = new ListTag();
        int[] n = new int[11];
        lights.clear(); arms.clear(); depots.clear(); gantries.clear();
        int armsBusy = 0, rounds = 0, gantryOn = 0;

        for (BlockEntity be : deckEntities(sl, base)) {
            BlockPos p = be.getBlockPos(), r = p.subtract(worldPosition);
            BlockState st = be.getBlockState();
            if (be instanceof DeckLiftBlockEntity l && st.hasProperty(DeckLiftBlock.HEAD) && st.getValue(DeckLiftBlock.HEAD) && n[K_LIFT]++ < Balance.FDC_MAX_EACH) {
                CompoundTag t = mark(K_LIFT, r, p.asLong());
                t.putInt("a", l.sx()); t.putInt("b", l.sz()); t.putInt("s", l.liftState());
                m.add(t);
            } else if (be instanceof CatapultTrackBlockEntity c && c.head() && n[K_CAT]++ < Balance.FDC_MAX_EACH) {
                CompoundTag t = mark(K_CAT, r, p.asLong());
                t.putInt("a", c.length()); t.putInt("f", st.getValue(MachineBlock.FACING).get2DDataValue()); t.putInt("s", c.catState());
                m.add(t);
            } else if (be instanceof ArrestingWireBlockEntity w && w.master() && n[K_WIRE]++ < Balance.FDC_MAX_EACH) {
                CompoundTag t = mark(K_WIRE, r, p.asLong());
                t.putInt("a", w.span()); t.putInt("f", st.getValue(MachineBlock.FACING).get2DDataValue()); t.putInt("s", w.wireState());
                m.add(t);
            } else if (be instanceof DeckLightBlockEntity d && n[K_LIGHT]++ < Balance.FDC_MAX_LIGHTS) {
                CompoundTag t = mark(K_LIGHT, r, p.asLong());
                t.putInt("m", d.mode()); t.putBoolean("on", d.lit()); t.putBoolean("o", d.consoleOff());
                m.add(t);
                lights.add(p.asLong());
            } else if (be instanceof CargoRampBlockEntity c && c.head() && n[K_RAMP]++ < Balance.FDC_MAX_EACH) {
                m.add(ramp(c, r, p, -1));
            } else if (be instanceof ServiceArmBlockEntity a && n[K_ARM]++ < Balance.FDC_MAX_EACH) {
                CompoundTag t = mark(K_ARM, r, p.asLong());
                t.putInt("s", a.armStatus());
                m.add(t);
                arms.add(p.asLong());
                if (a.phase() != ServiceArmBlockEntity.P_IDLE) armsBusy++;
            } else if (be instanceof AmmoDepotBlockEntity d && n[K_DEPOT]++ < Balance.FDC_MAX_EACH) {
                m.add(mark(K_DEPOT, r, p.asLong()));
                depots.add(p.asLong());
                rounds += d.rounds();
            } else if (be instanceof GantryCabinetBlockEntity g && n[K_GANTRY]++ < Balance.FDC_MAX_EACH) {
                CompoundTag t = mark(K_GANTRY, r, p.asLong());
                t.putBoolean("on", g.running());
                m.add(t);
                gantries.add(p.asLong());
                if (g.running()) gantryOn++;
            }
            else if (be instanceof TurntableBlockEntity tt && st.hasProperty(TurntableBlock.HEAD) && st.getValue(TurntableBlock.HEAD) && tt.size() > 0 && n[K_TURN]++ < Balance.FDC_MAX_EACH) {
                CompoundTag t = mark(K_TURN, r, p.asLong());
                t.putInt("a", tt.size()); t.putInt("h", (int) Math.round(((tt.target() % 360.0) + 360.0) % 360.0) % 360); t.putInt("s", tt.stat());
                m.add(t);
            }
            else if (be instanceof HangarDoorBlockEntity h && h.head() && h.columns() > 0 && n[K_DOOR]++ < Balance.FDC_MAX_EACH) {
                CompoundTag t = mark(K_DOOR, r, p.asLong());
                BlockPos one = Machines.toWorld(p, st.getValue(MachineBlock.FACING), 1, 0, 0);
                Direction run = Direction.fromDelta(one.getX() - p.getX(), 0, one.getZ() - p.getZ());
                t.putInt("a", h.columns()); t.putInt("f", run == null ? 0 : run.get2DDataValue()); t.putInt("s", h.doorState());
                m.add(t);
            }
        }

        wing.clear();
        if (owner != null) {
            Vec3 me = SableBridge.worldPos(sl, worldPosition);
            AABB box = new AABB(me, me).inflate(Balance.FDC_RANGE, Balance.FDC_RANGE_Y, Balance.FDC_RANGE);
            List<SableBridge.ShipInfo> ships = new ArrayList<>(SableBridge.shipsIntersecting(sl, box));
            ships.sort(Comparator.comparingDouble(s -> s.worldCenter().distanceToSqr(me)));
            int idx = 0;
            for (SableBridge.ShipInfo s : ships) {
                if (idx >= Balance.FDC_MAX_CRAFT) break;
                if (s.id().equals(base) || !Crafts.ownedBy(sl, s.id(), owner) || !Crafts.parked(sl, s.id(), base, Balance.GANTRY_PARKED)) continue;
                Vec3 r = rel(sl, base, s.worldCenter());
                if (r == null || !inRange(r.x, r.y, r.z)) continue;
                CompoundTag row = craftRow(sl, s, owner, r);
                wing.add(row);
                CompoundTag t = mark(K_CRAFT, BlockPos.containing(r), 0L);
                t.putInt("i", idx);
                m.add(t);
                for (BlockPos p : SableBridge.blockEntitiesOn(sl, s.id()))
                    if (sl.getBlockEntity(p) instanceof CargoRampBlockEntity c && c.head() && n[K_RAMP]++ < Balance.FDC_MAX_EACH) {
                        Vec3 rr = rel(sl, base, SableBridge.worldPos(sl, p));
                        if (rr != null) m.add(ramp(c, BlockPos.containing(rr), p, idx));
                    }
                idx++;
            }
        }
        sum = new CompoundTag();
        sum.putInt("arms", arms.size()); sum.putInt("armsBusy", armsBusy);
        sum.putInt("depots", depots.size()); sum.putInt("rounds", rounds);
        sum.putInt("gantries", gantries.size()); sum.putInt("gantryOn", gantryOn);
        if (!m.equals(marks)) { marks = m; sync(); }
    }

    private static CompoundTag ramp(CargoRampBlockEntity c, BlockPos rel, BlockPos p, int craft) {
        CompoundTag t = mark(K_RAMP, rel, p.asLong());
        t.putInt("a", c.width()); t.putInt("f", c.getBlockState().getValue(MachineBlock.FACING).get2DDataValue()); t.putInt("s", c.rampState());
        t.putInt("c", craft);
        return t;
    }

    private CompoundTag craftRow(ServerLevel sl, SableBridge.ShipInfo s, UUID owner, Vec3 r) {
        CompoundTag row = new CompoundTag();
        UUID id = s.id();
        row.putUUID("id", id);
        row.putString("n", s.name() == null || s.name().isBlank() ? "Craft" : s.name());
        row.putInt("x", (int) Math.round(r.x)); row.putInt("z", (int) Math.round(r.z)); row.putInt("y", (int) Math.round(r.y));
        int[] f = PowerCoreBlockEntity.fuelOf(sl, id);
        row.putInt("fuel", f[0]); row.putInt("fuelCap", f[1]);
        int total = 0, loaded = 0;
        for (StockTargets.Target t : StockTargets.of(sl, id, owner)) { total++; if (!t.current().isEmpty()) loaded++; }
        row.putInt("st", total); row.putInt("sl", loaded);
        int[] b = SableBridge.plotBox(sl, id);
        row.putInt("dmg", b == null || AirframeRecords.tooBig(b) ? -2 : AirframeRecords.faults(sl, id));
        int flags = 0;
        for (long a : arms)
            if (sl.getBlockEntity(BlockPos.of(a)) instanceof ServiceArmBlockEntity arm && arm.rowOn(sl, id) >= 0)
                flags |= arm.phase() == ServiceArmBlockEntity.P_IDLE ? W_FUEL : W_FUEL_BUSY;
        for (long d : depots)
            if (sl.getBlockEntity(BlockPos.of(d)) instanceof AmmoDepotBlockEntity dep && depotReaches(sl, dep, s))
                flags |= dep.modeFor(id) == AmmoDepotBlockEntity.MODE_STOCK ? W_STOCK_ON : W_STOCK;
        for (long g : gantries)
            if (sl.getBlockEntity(BlockPos.of(g)) instanceof GantryCabinetBlockEntity gan && id.equals(gan.targetCraft()))
                flags |= gan.running() ? W_FIX_ON : W_FIX;
        row.putInt("f", flags);
        return row;
    }

    private static boolean depotReaches(ServerLevel sl, AmmoDepotBlockEntity d, SableBridge.ShipInfo s) {
        Vec3 w = SableBridge.worldPos(sl, d.getBlockPos());
        double r = Balance.AD_RANGE + Balance.LOADER_TRACK_MAX;
        return Math.abs(w.x - s.worldCenter().x) <= r && Math.abs(w.z - s.worldCenter().z) <= r && Math.abs(w.y - s.worldCenter().y) <= Balance.AD_RANGE_Y;
    }

    private <T extends BlockEntity> T deckBlock(ServerLevel sl, UUID base, long pos, Class<T> type) {
        BlockPos p = BlockPos.of(pos);
        if (!sl.isLoaded(p) || !inRange(p) || !Objects.equals(SableBridge.shipIdAt(sl, p), base)) return null;
        BlockEntity be = sl.getBlockEntity(p);
        return type.isInstance(be) && !be.isRemoved() ? type.cast(be) : null;
    }

    private String craftOk(ServerLevel sl, UUID base, UUID ship) {
        SableBridge.ShipInfo si = ship == null ? null : SableBridge.shipInfo(sl, ship);
        if (si == null || ship.equals(base)) return "gone";
        if (!Crafts.ownedBy(sl, ship, getOwner())) return "not_owner";
        if (!Crafts.parked(sl, ship, base, Balance.GANTRY_PARKED)) return "moving";
        Vec3 r = rel(sl, base, si.worldCenter());
        if (r == null || !inRange(r.x, r.y, r.z)) return "far";
        return null;
    }

    static final int LS_OFF = 5, LS_ON = 6;

    String lightSet(ServerPlayer sp, long[] targets, int mode) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return "gated";
        if (!unfolded) { return noPower ? "no_power" : "folded"; }
        if (mode < 0 || mode > LS_ON || targets == null) return "gone";
        UUID base = base(sl);
        int k = 0;
        for (long p : targets) {
            DeckLightBlockEntity d = deckBlock(sl, base, p, DeckLightBlockEntity.class);
            if (d == null || !Access.mayUse(d, sp)) continue;
            if (mode == LS_OFF || mode == LS_ON) d.setConsoleOff(mode == LS_OFF); else { d.setMode(mode); d.setConsoleOff(false); }
            k++;
        }
        return k == 0 ? "gone" : "lights_set";
    }

    String command(ServerPlayer sp, int action, long target, UUID ship, int arg) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return "gated";
        if (!unfolded) return noPower ? "no_power" : "folded";
        UUID base = base(sl);
        switch (action) {
            case A_LIFT -> {
                DeckLiftBlockEntity l = deckBlock(sl, base, target, DeckLiftBlockEntity.class);
                if (l == null || !l.getBlockState().getValue(DeckLiftBlock.HEAD)) return "gone";
                if (!Access.mayUse(l, sp)) return "denied";
                sp.displayClientMessage(Component.translatable(l.toggle(sp)), true);
                return "sent";
            }
            case A_LAUNCH -> {
                CatapultTrackBlockEntity c = deckBlock(sl, base, target, CatapultTrackBlockEntity.class);
                if (c == null || !c.head()) return "gone";
                if (!Access.mayUse(c, sp)) return "denied";
                return c.crewLaunch() ? "launch" : "not_hooked";
            }
            case A_LIGHTS -> {
                int k = 0;
                for (long p : lights) {
                    DeckLightBlockEntity d = deckBlock(sl, base, p, DeckLightBlockEntity.class);
                    if (d == null || !Access.mayUse(d, sp)) continue;
                    d.setConsoleOff(arg != 0);
                    k++;
                }
                return k == 0 ? "gone" : arg != 0 ? "lights_off" : "lights_on";
            }
            case A_RAMP -> {
                BlockPos p = BlockPos.of(target);
                if (!sl.isLoaded(p) || !(sl.getBlockEntity(p) instanceof CargoRampBlockEntity c) || c.isRemoved() || !c.head()) return "gone";
                UUID body = SableBridge.shipIdAt(sl, p);
                if (Objects.equals(body, base)) { if (!inRange(p)) return "far"; }
                else { String why = craftOk(sl, base, body); if (why != null) return why; }
                if (!Access.mayUse(c, sp)) return "denied";
                sp.displayClientMessage(Component.translatable(c.toggle(sl)), true);
                return "sent";
            }
            case A_FUEL, A_STOCK, A_FIX -> {
                String why = craftOk(sl, base, ship);
                if (why != null) return why;
                SableBridge.ShipInfo si = SableBridge.shipInfo(sl, ship);
                boolean busy = false;
                if (action == A_FUEL) {
                    for (long a : arms) {
                        ServiceArmBlockEntity arm = deckBlock(sl, base, a, ServiceArmBlockEntity.class);
                        if (arm == null || !Access.mayUse(arm, sp)) continue;
                        int row = arm.rowOn(sl, ship);
                        if (row < 0) continue;
                        if (arm.phase() != ServiceArmBlockEntity.P_IDLE) { busy = true; continue; }
                        if (arm.pinnedRow() != row) arm.act(ServiceArmBlockEntity.A_PIN_AT, new int[] { row }, sp);
                        arm.act(ServiceArmBlockEntity.A_GO, new int[0], sp);
                        return "fuel";
                    }
                    return busy ? "arm_busy" : "no_arm";
                }
                if (action == A_STOCK) {
                    for (long d : depots) {
                        AmmoDepotBlockEntity dep = deckBlock(sl, base, d, AmmoDepotBlockEntity.class);
                        if (dep == null || !Access.mayUse(dep, sp) || !depotReaches(sl, dep, si)) continue;
                        dep.orderCraft(ship, AmmoDepotBlockEntity.MODE_STOCK);
                        return "stock";
                    }
                    return "no_depot";
                }
                for (long g : gantries) {
                    GantryCabinetBlockEntity gan = deckBlock(sl, base, g, GantryCabinetBlockEntity.class);
                    if (gan == null || !Access.mayUse(gan, sp)) continue;
                    if (gan.running() && ship.equals(gan.targetCraft())) { busy = true; continue; }
                    if (gan.startFor(sl, ship)) return "fix";
                }
                return busy ? "fixing" : "no_gantry";
            }
            case A_TURN -> {
                TurntableBlockEntity t = deckBlock(sl, base, target, TurntableBlockEntity.class);
                if (t == null || !t.getBlockState().getValue(TurntableBlock.HEAD)) return "gone";
                if (!Access.mayUse(t, sp)) return "denied";
                if (arg < TurntableBlockEntity.A_FIRST || arg > TurntableBlockEntity.A_LAST) return "gone";
                sp.displayClientMessage(t.answer(t.order(arg)), true);
                return "sent";
            }
            case A_DOOR -> {
                HangarDoorBlockEntity d = deckBlock(sl, base, target, HangarDoorBlockEntity.class);
                if (d == null || !d.head()) return "gone";
                if (!Access.mayUse(d, sp)) return "denied";
                sp.displayClientMessage(Component.translatable(d.command(arg != 0)), true);
                return "sent";
            }
            default -> { return "gone"; }
        }
    }

    void answer(ServerPlayer sp, String key) {
        if (!(level instanceof ServerLevel sl)) return;
        scan(sl);
        send(sp, false, key);
    }

    static float[] pose(float t) {
        float lid = LID_OPEN * smooth(t / 0.8F), visor = VISOR_OPEN * smooth((t - 0.8F) / 0.2F);
        double[] s = strutAngles(lid);
        return new float[] { lid, visor, (float) s[0], (float) s[1] };
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0.0F, 1.0F); return x * x * (3.0F - 2.0F * x); }

    static double[] strutAngles(double lidRx) {
        double[] p0 = lidPoint(0.0), p1 = lidPoint(lidRx);
        double a0 = Math.toDegrees(Math.atan2(p0[0] - STRUT_AY, p0[1] - STRUT_AZ)), a1 = Math.toDegrees(Math.atan2(p1[0] - STRUT_AY, p1[1] - STRUT_AZ));
        return new double[] { -(a1 - a0), -(a1 - a0) - lidRx };
    }

    static double[] lidPoint(double lidRx) {
        double a = Math.toRadians(-lidRx), vz = STRUT_LZ - PIN_Z, vy = STRUT_LY - PIN_Y;
        double z = vz * Math.cos(a) - vy * Math.sin(a), y = vz * Math.sin(a) + vy * Math.cos(a);
        return new double[] { PIN_Y + y, PIN_Z + z };
    }

    float lidDeg() { return pose(lidT)[0]; }

    boolean screenOn() { return unfolded && lidT >= 0.99F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float want = unfolded ? 1.0F : 0.0F;
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT);
            lidT += (float) Mth.clamp(want - lidT, -dt / Balance.FDC_LID_TICKS, dt / Balance.FDC_LID_TICKS);
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        float[] a = pose(lidT);
        final float lid = (float) Math.toRadians(a[0]) * LID_SIGN, vis = (float) Math.toRadians(a[1]) * LID_SIGN;
        final float cyl = (float) Math.toRadians(a[2]) * LID_SIGN, rod = (float) Math.toRadians(a[3]) * LID_SIGN;
        model.getBone("lid").ifPresent(b -> b.setRotX(lid));
        model.getBone("visor").ifPresent(b -> b.setRotX(vis));
        for (String s : new String[] { "l", "r" }) {
            model.getBone("strut_" + s + "_c").ifPresent(b -> b.setRotX(cyl));
            model.getBone("strut_" + s + "_r").ifPresent(b -> b.setRotX(rod));
        }
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected boolean soundAlive() { return false; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }

    @Override
    protected String detail() { return (unfolded ? "unfolded" : "folded") + ", " + marks.size() + " marks, " + wing.size() + " craft"; }

    @Override
    public Component status() {
        String k = unfolded ? "message.wmd.deck_console.status_open" : noPower ? "message.wmd.deck_console.status_no_power" : "message.wmd.deck_console.status_folded";
        return Component.translatable("block.wmd.deck_console").append(": ").append(Component.translatable(k));
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
        if (tag.contains("dcOpen")) unfolded = tag.getBoolean("dcOpen");
        if (tag.contains("dcNoPow")) noPower = tag.getBoolean("dcNoPow");
        if (tag.contains("dcMarks")) marks = tag.getList("dcMarks", Tag.TAG_COMPOUND);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("dcOpen", unfolded);
        tag.putBoolean("dcNoPow", noPower);
        tag.put("dcMarks", marks.copy());
        return tag;
    }
}
