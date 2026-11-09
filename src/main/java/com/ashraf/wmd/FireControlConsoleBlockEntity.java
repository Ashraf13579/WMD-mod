package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FireControlConsoleBlockEntity extends ProcessUnitBlockEntity implements Owned {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 0;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 0, Direction.SOUTH, false);

    public static final int A_FREQ = 0, A_SELECT = 1, A_DEPTH = 2, A_GYRO = 3, A_TARGET = 4, A_SOLVE = 5, A_FIRE = 6, A_SALVO = 7, A_TRAIN = 8;

    public static final int A_STRIKE = 9, A_STRIKE_CLR = 10, A_SSEL = 11, A_SFIRE = 12, A_SSALVO = 13, A_OWN_X = 14, A_OWN_Z = 15, A_OWN_CLR = 16;

    public static final int RC_NONE = 0, RC_OK = 1, RC_CLOSE = 2, RC_FAR = 3;

    private final OwnerData ownerData = new OwnerData();
    private int frequency = 1, selected, target = -1;
    private long lastShot = -1000L;

    private ListTag rows = new ListTag();

    private ListTag targets = new ListTag();

    private ListTag torps = new ListTag();
    private int cx, cz;

    private boolean strikeSet;
    private int strikeX, strikeZ, sSel, pendX;
    private long strikeAt;

    private ListTag strikes = new ListTag();

    public FireControlConsoleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.FIRE_CONTROL_CONSOLE_BE.get(), pos, state, Balance.FCC_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    public int frequency() { return frequency; }
    public int selected() { return selected; }
    public int target() { return target; }
    public long lastShot() { return lastShot; }
    public int mapX() { return cx; }
    public int mapZ() { return cz; }
    public List<CompoundTag> launcherRows() { return rows(rows); }
    public List<CompoundTag> targetRows() { return rows(targets); }
    public List<CompoundTag> torpedoRows() { return rows(torps); }
    public List<CompoundTag> strikeRows() { return rows(strikes); }
    public boolean strikeSet() { return strikeSet; }
    public int strikeX() { return strikeX; }
    public int strikeZ() { return strikeZ; }
    public int strikeSelected() { return sSel; }

    public CompoundTag strikeRow() { List<CompoundTag> l = strikeRows(); return sSel >= 0 && sSel < l.size() ? l.get(sSel) : null; }

    private static List<CompoundTag> rows(ListTag t) { List<CompoundTag> l = new ArrayList<>(); for (Tag x : t) if (x instanceof CompoundTag c) l.add(c); return l; }

    public CompoundTag selectedRow() { List<CompoundTag> l = launcherRows(); return selected >= 0 && selected < l.size() ? l.get(selected) : null; }

    public CompoundTag targetRow() { List<CompoundTag> l = targetRows(); return target >= 0 && target < l.size() ? l.get(target) : null; }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return true;
        if ((sl.getGameTime() + (worldPosition.asLong() & 7L)) % Balance.FCC_TICKS == 0) compile(sl);
        return true;
    }

    private List<TorpedoLauncherBlockEntity> net(ServerLevel sl) {
        List<TorpedoLauncherBlockEntity> l = CommsLink.keep(sl, worldPosition, FireControlNet.launchers(sl, getOwner(), frequency));
        return l.size() > Balance.FCC_ROWS ? l.subList(0, Balance.FCC_ROWS) : l;
    }

    private static double yaw(Vec3 d) { return Math.toDegrees(Math.atan2(d.x, -d.z)); }

    private List<LaunchCellBlockEntity> cells(ServerLevel sl, int used) {
        List<LaunchCellBlockEntity> l = CommsLink.keep(sl, worldPosition, FireControlNet.cells(sl, getOwner(), frequency));
        int room = Math.max(0, Balance.FCC_ROWS - used);
        return l.size() > room ? l.subList(0, room) : l;
    }

    private List<CoastalBatteryBlockEntity> batteries(ServerLevel sl, int used) {
        List<CoastalBatteryBlockEntity> l = CommsLink.keep(sl, worldPosition, FireControlNet.batteries(sl, getOwner(), frequency));
        int room = Math.max(0, Balance.FCC_ROWS - used);
        return l.size() > room ? l.subList(0, room) : l;
    }

    private CoastalBatteryBlockEntity batteryAt(ServerLevel sl, int i) {
        int nl = net(sl).size(), nc = cells(sl, nl).size();
        List<CoastalBatteryBlockEntity> l = batteries(sl, nl + nc);
        return i >= nl + nc && i - nl - nc < l.size() ? l.get(i - nl - nc) : null;
    }

    private List<BlockEntity> strikeMembers(ServerLevel sl) {
        List<BlockEntity> l = new ArrayList<>(cells(sl, 0));
        l.addAll(batteries(sl, l.size()));
        return l;
    }

    private BlockEntity strikeAt(ServerLevel sl, int i) {
        List<BlockEntity> l = strikeMembers(sl);
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    private CompoundTag cellRow(ServerLevel sl, LaunchCellBlockEntity lc) {
        CompoundTag r = new CompoundTag();
        Vec3 at = SableBridge.worldPos(sl, lc.getBlockPos());
        r.putInt("x", Mth.floor(at.x)); r.putInt("z", Mth.floor(at.z)); r.putLong("p", lc.getBlockPos().asLong());
        r.putBoolean("cell", true);
        int cs = lc.cellStatus();
        r.putInt("cst", cs);
        r.putInt("st", cs == LaunchCellBlockEntity.S_READY ? TorpedoLauncherBlockEntity.S_READY : cs == LaunchCellBlockEntity.S_EMPTY ? TorpedoLauncherBlockEntity.S_EMPTY
                : cs == LaunchCellBlockEntity.S_NO_POWER ? TorpedoLauncherBlockEntity.S_NO_POWER : TorpedoLauncherBlockEntity.S_CHARGING);
        r.putInt("n", lc.loadedCount());
        int rk = lc.roundKind();
        r.putInt("rk", rk);
        r.putInt("yaw", 0);
        boolean strikeRound = rk == LaunchCellBlockEntity.R_SLBM || rk == LaunchCellBlockEntity.R_CRUISE;
        int src = LaunchCellBlockEntity.SRC_NONE;
        Vec3 t;
        if (strikeRound && lc.strikeSet()) { src = LaunchCellBlockEntity.SRC_OWN; t = new Vec3(lc.strikeX() + 0.5, 0, lc.strikeZ() + 0.5); }
        else {
            FireControlNet.Strike st = strikeRound ? FireControlNet.strikeOf(sl, getOwner(), frequency, lc.getBlockPos()) : null;
            if (st != null) { src = LaunchCellBlockEntity.SRC_CONSOLE; t = new Vec3(st.x() + 0.5, 0, st.z() + 0.5); }
            else { t = FireControlNet.aimOf(sl, getOwner(), frequency); if (t != null) src = LaunchCellBlockEntity.SRC_NET; }
        }
        r.putInt("src", src);
        r.putBoolean("own", lc.strikeSet());
        r.putInt("ox", lc.strikeX()); r.putInt("oz", lc.strikeZ());
        int min = lc.minRangeNow(), max = lc.rangeNow();
        r.putInt("min", min); r.putInt("rng", max);
        int d = t == null ? 0 : (int) Math.round(Math.hypot(t.x - at.x, t.z - at.z));
        r.putInt("dist", d);
        r.putInt("rc", t == null ? RC_NONE : lc.loadedCount() <= 0 ? RC_OK : d < min ? RC_CLOSE : d > max ? RC_FAR : RC_OK);
        return r;
    }

    private CompoundTag batteryRow(ServerLevel sl, CoastalBatteryBlockEntity b) {
        CompoundTag r = new CompoundTag();
        Vec3 at = SableBridge.worldPos(sl, b.getBlockPos());
        r.putInt("x", Mth.floor(at.x)); r.putInt("z", Mth.floor(at.z)); r.putLong("p", b.getBlockPos().asLong());
        r.putBoolean("bat", true);
        int bs = b.batteryStatus();
        r.putInt("cbs", bs);
        r.putInt("st", bs == CoastalBatteryBlockEntity.S_READY ? TorpedoLauncherBlockEntity.S_READY
                : bs == CoastalBatteryBlockEntity.S_NO_MISSILE ? TorpedoLauncherBlockEntity.S_EMPTY
                : bs == CoastalBatteryBlockEntity.S_NO_POWER ? TorpedoLauncherBlockEntity.S_NO_POWER : TorpedoLauncherBlockEntity.S_CHARGING);
        r.putInt("n", b.loaded());
        boolean typed = b.mode() == CoastalBatteryBlockEntity.M_TYPED;
        r.putInt("src", typed ? LaunchCellBlockEntity.SRC_OWN : b.hasAim() ? LaunchCellBlockEntity.SRC_NET : LaunchCellBlockEntity.SRC_NONE);
        r.putBoolean("own", typed && b.typed());
        r.putInt("ox", b.typedX()); r.putInt("oz", b.typedZ());
        r.putString("lk", b.lockLabel());
        int d = b.hasAim() ? b.routeLength() : 0;
        r.putInt("dist", d); r.putInt("rng", b.nextRange()); r.putInt("min", Balance.CRUISE_MIN_RANGE);
        r.putInt("rc", !b.hasAim() ? RC_NONE : b.loaded() <= 0 ? RC_OK : bs == CoastalBatteryBlockEntity.S_TOO_CLOSE ? RC_CLOSE : bs == CoastalBatteryBlockEntity.S_TOO_FAR ? RC_FAR : RC_OK);
        r.putInt("yaw", 0);
        return r;
    }

    private LaunchCellBlockEntity cellAt(ServerLevel sl, int i) {
        int nl = net(sl).size();
        List<LaunchCellBlockEntity> l = cells(sl, nl);
        return i >= nl && i - nl < l.size() ? l.get(i - nl) : null;
    }

    private void compile(ServerLevel sl) {
        Vec3 me = SableBridge.worldPos(sl, worldPosition);
        cx = Mth.floor(me.x);
        cz = Mth.floor(me.z);

        ListTag tg = new ListTag();
        for (SonarNet.Fix f : SonarNet.pingFixes(sl, getOwner(), frequency)) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", Mth.floor(f.x())); t.putInt("y", Mth.floor(f.y())); t.putInt("z", Mth.floor(f.z())); t.putInt("d", (int) Math.round(f.depth()));
            t.putBoolean("s", f.submerged()); t.putBoolean("w", f.label().startsWith("wreck:")); t.putString("n", f.label());
            tg.add(t);
        }
        for (SonarNet.Contact c : SonarNet.contacts(sl, getOwner(), frequency)) {
            if (c.fix() == null || !CommsLink.hears(sl, worldPosition, c.ear())) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("x", Mth.floor(c.fix().x())); t.putInt("y", Mth.floor(c.fix().y())); t.putInt("z", Mth.floor(c.fix().z())); t.putBoolean("f", true);
            t.putString("n", "fix:" + c.kind().name().toLowerCase(java.util.Locale.ROOT));
            tg.add(t);
        }
        while (tg.size() > 12) tg.remove(tg.size() - 1);
        targets = tg;
        if (target >= targets.size()) target = -1;
        CompoundTag tgt = target >= 0 ? targets.getCompound(target) : null;
        FireControlNet.aim(sl, getOwner(), frequency, worldPosition.asLong(), tgt == null ? null : new Vec3(tgt.getInt("x") + 0.5, tgt.getInt("y") + 0.5, tgt.getInt("z") + 0.5));

        ListTag ls = new ListTag();
        for (TorpedoLauncherBlockEntity t : net(sl)) {
            Vec3[] m = t.worldMuzzle();
            CompoundTag r = new CompoundTag();
            r.putInt("x", Mth.floor(m[0].x)); r.putInt("z", Mth.floor(m[0].z)); r.putLong("p", t.getBlockPos().asLong());
            r.putBoolean("deck", t instanceof TorpedoLauncherDeckBlockEntity);
            r.putInt("st", t.statusNow());
            TorpedoKind k = t.nextKind();
            r.putInt("k", k == null ? -1 : k.ordinal());
            r.putInt("n", t.loadedCount());
            r.putInt("d", t.depth()); r.putInt("g", t.gyro());
            if (t instanceof TorpedoLauncherDeckBlockEntity dk) r.putInt("t", dk.train());
            double base = yaw(t instanceof TorpedoLauncherDeckBlockEntity ? t.worldFront() : m[1]);
            r.putInt("yaw", (int) Math.round(Mth.wrapDegrees(base + t.gyro()) * 10));
            if (tgt != null) {
                double dx = tgt.getInt("x") + 0.5 - m[0].x, dz = tgt.getInt("z") + 0.5 - m[0].z, dist = Math.hypot(dx, dz);
                double want = Math.toDegrees(Math.atan2(dx, -dz));
                double rel = Mth.wrapDegrees(want - base);
                boolean arc = t instanceof TorpedoLauncherDeckBlockEntity ? Math.abs(rel) > Balance.DECK_TRAIN_MAX + Balance.GYRO_MAX : Math.abs(rel) > Balance.GYRO_MAX;
                r.putBoolean("arc", arc);
                r.putBoolean("far", k != null && dist > k.range());
                r.putInt("dist", (int) Math.round(dist));
                r.putInt("want", (int) Math.round(Mth.wrapDegrees(want) * 10));
            }
            ls.add(r);
        }

        FireControlNet.strike(sl, getOwner(), frequency, worldPosition, strikeSet, strikeX, strikeZ, strikeAt);
        for (LaunchCellBlockEntity lc : cells(sl, ls.size())) ls.add(cellRow(sl, lc));

        for (CoastalBatteryBlockEntity b : batteries(sl, ls.size())) ls.add(batteryRow(sl, b));
        rows = ls;
        if (selected >= rows.size()) selected = Math.max(0, rows.size() - 1);

        ListTag sr = new ListTag();
        for (BlockEntity m : strikeMembers(sl)) sr.add(m instanceof LaunchCellBlockEntity lc ? cellRow(sl, lc) : batteryRow(sl, (CoastalBatteryBlockEntity) m));
        strikes = sr;
        if (sSel >= strikes.size()) sSel = Math.max(0, strikes.size() - 1);

        ListTag tp = new ListTag();
        UUID o = getOwner();
        if (o != null) {
            for (TorpedoEntity e : sl.getEntitiesOfClass(TorpedoEntity.class, new AABB(me, me).inflate(Balance.FCC_TORP_RANGE, 128, Balance.FCC_TORP_RANGE))) {
                if (!o.equals(e.owner())) continue;
                CompoundTag c = new CompoundTag();
                c.putInt("x", Mth.floor(e.getX())); c.putInt("z", Mth.floor(e.getZ()));
                tp.add(c);
                if (tp.size() >= 16) break;
            }
        }
        torps = tp;
        setChanged();
        sync();
    }

    private TorpedoLauncherBlockEntity launcherAt(ServerLevel sl, int i) {
        List<TorpedoLauncherBlockEntity> l = net(sl);
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    public void act(Player by, int action, int a, int b) {
        if (!(level instanceof ServerLevel sl)) return;
        switch (action) {
            case A_FREQ -> { FireControlNet.strike(sl, getOwner(), frequency, worldPosition, false, 0, 0, 0L); frequency = Math.max(1, Math.min(9999, a)); target = -1; }
            case A_SELECT -> selected = Math.max(0, a);
            case A_DEPTH -> { TorpedoLauncherBlockEntity t = launcherAt(sl, a); if (t != null) t.setDepth(b); }
            case A_GYRO -> { TorpedoLauncherBlockEntity t = launcherAt(sl, a); if (t != null) t.setGyro(b); }
            case A_TRAIN -> { if (launcherAt(sl, a) instanceof TorpedoLauncherDeckBlockEntity d) d.setTrain(b); }
            case A_TARGET -> target = a < 0 ? -1 : a;
            case A_SOLVE -> solve(sl, by);
            case A_FIRE -> {
                TorpedoLauncherBlockEntity t = launcherAt(sl, a);
                if (t == null) {
                    LaunchCellBlockEntity lc = cellAt(sl, a);
                    if (lc != null) fireMember(sl, lc, by);
                    else { CoastalBatteryBlockEntity cb = batteryAt(sl, a); if (cb != null) fireMember(sl, cb, by); }
                    compile(sl);
                    return;
                }
                int r = t.fire(by);
                if (r == TorpedoLauncherBlockEntity.S_FIRED) { lastShot = sl.getGameTime(); fireKey(sl); }
                else if (by != null) by.displayClientMessage(Component.translatable("gui.wmd.torp.st." + r), true);
            }
            case A_SALVO -> {
                List<TorpedoLauncherBlockEntity> ready = new ArrayList<>();
                for (TorpedoLauncherBlockEntity t : net(sl)) if (t.statusNow() == TorpedoLauncherBlockEntity.S_READY) ready.add(t);
                int n = ready.size(), fired = 0;
                for (int i = 0; i < n; i++) if (ready.get(i).fire(by, Math.round((i - (n - 1) / 2.0F) * Balance.SALVO_FAN)) == TorpedoLauncherBlockEntity.S_FIRED) fired++;
                for (LaunchCellBlockEntity lc : cells(sl, net(sl).size()))
                    if (lc.cellStatus() == LaunchCellBlockEntity.S_READY && lc.launchFromConsole(by)) fired++;
                fired += DepthChargeThrowerBlockEntity.salvo(sl, getOwner(), frequency, by);
                int used = net(sl).size();
                fired += batterySalvo(batteries(sl, used + cells(sl, used).size()), by);
                if (fired > 0) { lastShot = sl.getGameTime(); fireKey(sl); }
                if (by != null) by.displayClientMessage(Component.translatable("gui.wmd.fcc.salvo_fired", fired), true);
            }

            case A_STRIKE -> { strikeSet = true; strikeX = clampXZ(a); strikeZ = clampXZ(b); strikeAt = sl.getGameTime(); strikeReport(sl, by); }
            case A_STRIKE_CLR -> { strikeSet = false; strikeReport(sl, by); }
            case A_SSEL -> sSel = Math.max(0, a);
            case A_SFIRE -> { BlockEntity m = strikeAt(sl, a); if (m != null) fireMember(sl, m, by); }
            case A_SSALVO -> {
                int fired = 0;
                List<CoastalBatteryBlockEntity> bats = new ArrayList<>();
                for (BlockEntity m : strikeMembers(sl)) {
                    if (m instanceof LaunchCellBlockEntity lc) { if (lc.cellStatus() == LaunchCellBlockEntity.S_READY && lc.launchFromConsole(by)) fired++; }
                    else if (m instanceof CoastalBatteryBlockEntity cb) bats.add(cb);
                }
                fired += batterySalvo(bats, by);
                if (fired > 0) { lastShot = sl.getGameTime(); fireKey(sl); }
                if (by != null) by.displayClientMessage(Component.translatable("gui.wmd.fcc.strike_salvo", fired), true);
            }
            case A_OWN_X -> pendX = clampXZ(b);
            case A_OWN_Z -> {
                BlockEntity m = strikeAt(sl, a);
                int z = clampXZ(b);
                if (m instanceof LaunchCellBlockEntity lc) { lc.act(by, LaunchCellBlockEntity.A_TX, pendX); lc.act(by, LaunchCellBlockEntity.A_TZ, z); lc.recheck(); }
                else if (m instanceof CoastalBatteryBlockEntity cb) cb.consoleTarget(true, pendX, z, by);
                if (m != null && by != null) by.displayClientMessage(Component.translatable("gui.wmd.fcc.own_set", a + 1, pendX, z), true);
            }
            case A_OWN_CLR -> {
                BlockEntity m = strikeAt(sl, a);
                if (m instanceof LaunchCellBlockEntity lc) { lc.act(by, LaunchCellBlockEntity.A_CLEAR, 0); lc.recheck(); }
                else if (m instanceof CoastalBatteryBlockEntity cb) cb.consoleTarget(false, 0, 0, by);
                if (m != null && by != null) by.displayClientMessage(Component.translatable("gui.wmd.fcc.own_clr", a + 1), true);
            }
            default -> {}
        }
        compile(sl);
    }

    private static int clampXZ(int v) { return Mth.clamp(v, -29_999_000, 29_999_000); }

    private void fireMember(ServerLevel sl, BlockEntity m, Player by) {
        boolean ok = false;
        if (m instanceof LaunchCellBlockEntity lc) ok = lc.launchFromConsole(by);
        else if (m instanceof CoastalBatteryBlockEntity cb) {
            ok = cb.fireFromConsole(by);
            if (!ok && by != null) by.displayClientMessage(Component.translatable("gui.wmd.fcc.bat_no", Component.translatable("gui.wmd.coastal.st." + cb.batteryStatus())), true);
        }
        if (ok) { lastShot = sl.getGameTime(); fireKey(sl); }
    }

    private static int batterySalvo(List<CoastalBatteryBlockEntity> bats, Player by) {
        int n = 0;
        for (CoastalBatteryBlockEntity cb : bats) if (cb.salvoReady() && cb.fireFromConsole(by)) n++;
        return n;
    }

    private void strikeReport(ServerLevel sl, Player by) {
        FireControlNet.strike(sl, getOwner(), frequency, worldPosition, strikeSet, strikeX, strikeZ, strikeAt);
        int ok = 0, far = 0, close = 0;
        for (LaunchCellBlockEntity lc : cells(sl, 0)) {
            lc.recheck();
            CompoundTag r = cellRow(sl, lc);
            if (r.getInt("src") != LaunchCellBlockEntity.SRC_CONSOLE || r.getInt("n") <= 0) continue;
            int rc = r.getInt("rc");
            if (rc == RC_FAR) far++; else if (rc == RC_CLOSE) close++; else ok++;
        }
        if (by == null) return;
        if (!strikeSet) by.displayClientMessage(Component.translatable("gui.wmd.fcc.strike_cleared"), true);
        else if (ok + far + close == 0) by.displayClientMessage(Component.translatable("gui.wmd.fcc.strike_none", strikeX, strikeZ), true);
        else by.displayClientMessage(Component.translatable("gui.wmd.fcc.strike_result", strikeX, strikeZ, ok, far, close), true);
    }

    private void solve(ServerLevel sl, Player by) {
        CompoundTag tgt = target >= 0 && target < targets.size() ? targets.getCompound(target) : null;
        if (tgt == null) { if (by != null) by.displayClientMessage(Component.translatable("gui.wmd.fcc.no_target"), true); return; }
        int ok = 0;
        for (TorpedoLauncherBlockEntity t : net(sl)) {
            Vec3[] m = t.worldMuzzle();
            double dx = tgt.getInt("x") + 0.5 - m[0].x, dz = tgt.getInt("z") + 0.5 - m[0].z;
            double want = Math.toDegrees(Math.atan2(dx, -dz));
            if (t instanceof TorpedoLauncherDeckBlockEntity d) {
                double rel = Mth.wrapDegrees(want - yaw(d.worldFront()));
                int train = (int) Math.round(Mth.clamp(rel, -Balance.DECK_TRAIN_MAX, Balance.DECK_TRAIN_MAX));
                d.setTrain(train);
                d.setGyro((int) Math.round(Mth.clamp(rel - train, -Balance.GYRO_MAX, Balance.GYRO_MAX)));
                ok++;
            } else {
                double rel = Mth.wrapDegrees(want - yaw(m[1]));
                if (Math.abs(rel) > Balance.GYRO_MAX) continue;
                t.setGyro((int) Math.round(rel));
                ok++;
            }
            if (!tgt.getBoolean("f")) t.setDepth((int) Math.round(tgt.getBoolean("s") ? Math.max(Balance.TORP_MIN_DEPTH, tgt.getInt("d") + Balance.TORP_SUB_EXTRA) : Balance.TORP_KEEL_RUN));
        }
        if (by != null) by.displayClientMessage(Component.translatable("gui.wmd.fcc.solved", ok), true);
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    private void fireKey(ServerLevel sl) { sl.playSound(null, worldPosition, WmdSounds.FCC_FIRE.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 1.0F); }

    @Override protected SoundEvent runSound() { return WmdSounds.FCC_ROOM_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.FCC_FE * 20), Component.translatable("gui.wmd.info.fire_control_console"));
    }

    @Override
    protected String detail() {
        return (isRunning() ? "on" : "no power") + ", " + rows.size() + " launcher(s), " + targets.size() + " target(s), frequency " + frequency + ", " + strikes.size() + " strike row(s)"
                + (strikeSet ? ", strike target " + strikeX + " " + strikeZ : "");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("fcFreq", frequency);
        tag.putInt("fcSel", selected);
        tag.putBoolean("fcStrike", strikeSet); tag.putInt("fcSx", strikeX); tag.putInt("fcSz", strikeZ); tag.putLong("fcSat", strikeAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("fcFreq")) frequency = Math.max(1, tag.getInt("fcFreq"));
        selected = tag.getInt("fcSel");
        strikeSet = tag.getBoolean("fcStrike"); strikeX = tag.getInt("fcSx"); strikeZ = tag.getInt("fcSz"); strikeAt = tag.getLong("fcSat");
        sSel = tag.getInt("fcSsel");
        if (tag.contains("fcStrikes")) strikes = tag.getList("fcStrikes", Tag.TAG_COMPOUND);
        target = tag.contains("fcTgt") ? tag.getInt("fcTgt") : -1;
        if (tag.contains("fcRows")) rows = tag.getList("fcRows", Tag.TAG_COMPOUND);
        if (tag.contains("fcTargets")) targets = tag.getList("fcTargets", Tag.TAG_COMPOUND);
        if (tag.contains("fcTorps")) torps = tag.getList("fcTorps", Tag.TAG_COMPOUND);
        if (tag.contains("fcLast")) lastShot = tag.getLong("fcLast");
        cx = tag.contains("fcCx") ? tag.getInt("fcCx") : worldPosition.getX();
        cz = tag.contains("fcCz") ? tag.getInt("fcCz") : worldPosition.getZ();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putInt("fcFreq", frequency);
        tag.putInt("fcSel", selected);
        tag.putInt("fcTgt", target);
        tag.put("fcRows", rows);
        tag.put("fcTargets", targets);
        tag.put("fcTorps", torps);
        tag.putLong("fcLast", lastShot);
        tag.putInt("fcCx", cx);
        tag.putInt("fcCz", cz);
        tag.putBoolean("fcStrike", strikeSet); tag.putInt("fcSx", strikeX); tag.putInt("fcSz", strikeZ); tag.putLong("fcSat", strikeAt);
        tag.putInt("fcSsel", sSel);
        tag.put("fcStrikes", strikes);
        return tag;
    }
}
