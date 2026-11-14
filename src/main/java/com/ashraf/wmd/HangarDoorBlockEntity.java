package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class HangarDoorBlockEntity extends ProcessUnitBlockEntity implements Owned, CopycatState.Host {

    public static final int S_NO_RUN = 0, S_OPEN = 1, S_CLOSED = 2, S_HELD = 3, S_CLOSING = 4, S_OPENING = 5, S_BLOCKED = 6, S_NO_POWER = 7;
    public static final int K_NONE = 0, K_PLATE = 1, K_CAP = 2;
    static final int W_NONE = 0, W_BLOCKED = 1, W_POWER = 2;

    static final double BAR_H = 2.6 / 16.0;
    static final double SPEED = 1.0 / Balance.HDOOR_TICKS_PER_ROW;
    private static final double EPS = 1e-6;

    private static final Map<UUID, int[]> CLICKS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState hood = new CopycatState(), slats = new CopycatState();

    final CopycatState.Host slatsHost = new CopycatState.Host() {
        @Override public CopycatState copycat() { return slats; }
        @Override public void copycatChanged() { slatsChanged(); }
    };

    private BlockPos headPos;
    private int col, westKind = K_PLATE, eastKind = K_PLATE, drop;
    private boolean isHead;

    private int n;
    private int[] dy = new int[0], drops = new int[0];
    private double travel;
    private int dir, wait;
    private long syncAt;
    private boolean fresh = true;

    private boolean sig;
    private long nextScan, nextCheck;

    public HangarDoorBlockEntity(BlockPos pos, BlockState state) {
        super(HangarDoorBlocks.HANGAR_DOOR_BE.get(), pos, state, 0, Balance.HDOOR_BUFFER, Balance.HDOOR_RECEIVE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    @Override public CopycatState copycat() { return hood; }
    @Override public void copycatChanged() { sync(); }
    CopycatState slats() { return slats; }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        for (CopycatState cs : new CopycatState[] { hood, slats }) {
            ItemStack k = cs.takeItem();
            if (!k.isEmpty()) l.add(k);
        }
        return l;
    }

    boolean head() { return isHead; }
    BlockPos headPosClient() { return headPos; }
    int column() { return col; }
    int columns() { return n; }
    int westKind() { return westKind; }
    int eastKind() { return eastKind; }
    int columnDrop() { return drop; }
    boolean inRun() { return headPos != null; }

    double travelAt(double time) {
        if (dir == 0 || wait != W_NONE || level == null) return travel;
        return Math.max(0.0, Math.min(tMax(), travel + dir * SPEED * (time - syncAt)));
    }

    static double extension(double t, int columnDrop) { return Math.min(t + BAR_H, columnDrop); }

    double tMax() {
        int m = 0;
        for (int d : drops) m = Math.max(m, d);
        return Math.max(0.0, m - BAR_H);
    }

    boolean curtainDrawn() { return dir != 0 || wait != W_NONE || slatsSkinned() || (travel > EPS && travel < tMax() - EPS); }

    private boolean slatsSkinned() { return level != null && level.isClientSide ? slats.shown() != null : slats.skinned(); }

    public int doorState() {
        if (n == 0) return S_NO_RUN;
        if (wait == W_BLOCKED) return S_BLOCKED;
        if (wait == W_POWER) return S_NO_POWER;
        if (dir > 0) return S_CLOSING;
        if (dir < 0) return S_OPENING;
        if (travel <= EPS) return S_OPEN;
        if (travel >= tMax() - EPS) return S_CLOSED;
        return S_HELD;
    }

    public static HangarDoorBlockEntity at(Level level, BlockPos pos) { return HangarDoorBlock.headOf(level, pos); }

    public boolean isOpen() { return doorState() == S_OPEN; }

    public boolean isClosed() { return doorState() == S_CLOSED; }

    public float progress() { double m = tMax(); return m <= 0.0 ? 0.0F : (float) Math.max(0.0, Math.min(1.0, travel / m)); }

    int comparatorOutput() { return n == 0 ? 0 : Math.round(15.0F * (1.0F - progress())); }

    public String command(boolean open) {
        if (!(level instanceof ServerLevel sl) || !isHead) return "message.wmd.hdoor.no_head";
        if (!AirGate.on()) return "message.wmd.hdoor.off";
        if (dir == 0 && wait == W_NONE) measure(sl);
        if (n == 0) return "message.wmd.hdoor.not_run";
        if (open) {
            if (dir == 0 && travel <= EPS) return "message.wmd.hdoor.already_open";
            startStroke(sl, -1);
            return "message.wmd.hdoor.opening";
        }
        if (n < Balance.HDOOR_W_MIN) return "message.wmd.hdoor.not_run";
        if (dir == 0 && travel >= tMax() - EPS) { heal(sl); return "message.wmd.hdoor.already_closed"; }
        startStroke(sl, 1);
        return "message.wmd.hdoor.closing";
    }

    String toggle() {
        boolean down = dir > 0 || (dir == 0 && travel > tMax() * 0.5);
        return command(down);
    }

    static void click(Level level, BlockPos at, Player player, BlockHitResult hit, boolean curtain) {
        HangarDoorBlockEntity head = HangarDoorBlock.headOf(level, at);
        if (head == null) { player.displayClientMessage(Component.translatable("message.wmd.hdoor.no_head"), true); return; }
        if (!MachineBlock.gate(level, head.getBlockPos(), player, head)) return;
        if (CopycatState.interact(level, player, hit.getBlockPos(), curtain ? head.slatsHost : head)) return;
        if (player.isShiftKeyDown()) { player.displayClientMessage(head.status(), true); return; }
        if (!player.getMainHandItem().isEmpty()) return;
        if (player instanceof ServerPlayer sp && AirframeRecords.tooSoon(CLICKS, sp, 1, Balance.HDOOR_CLICK_GAP)) return;
        player.displayClientMessage(Component.translatable(head.toggle()), true);
    }

    private void slatsChanged() {
        if (level instanceof ServerLevel sl && isClosed()) setDrawn(sl, slats.skinned(), true);
        sync();
    }

    private BlockPos drumAt(int c) { return Machines.toWorld(worldPosition, facing(), c, dy[c], 0); }

    private BlockPos cellAt(int c, int k) { return Machines.toWorld(worldPosition, facing(), c, dy[c] - k, 0); }

    private boolean ownCurtain(BlockState s) { return s.getBlock() instanceof HangarCurtainBlock && s.getValue(HangarCurtainBlock.FACING) == facing(); }

    private boolean freeCell(BlockState s) { return s.isAir() || ownCurtain(s) || (s.canBeReplaced() && s.getFluidState().isEmpty()); }

    private void measure(ServerLevel sl) {
        Direction f = facing();
        List<Integer> ys = new ArrayList<>();
        ys.add(0);
        BlockPos p = worldPosition;
        while (ys.size() < Balance.HDOOR_W_MAX) {
            BlockPos q = HangarDoorBlock.neighbour(sl, p, f, 1);
            if (q == null) break;
            ys.add(Machines.toLocal(worldPosition, f, q).getY());
            p = q;
        }
        int nn = ys.size();
        int[] ndy = new int[nn], ndr = new int[nn];
        for (int c = 0; c < nn; c++) {
            ndy[c] = ys.get(c);
            int k = 0;
            while (k < Balance.HDOOR_DROP_MAX && freeCell(sl.getBlockState(Machines.toWorld(worldPosition, f, c, ndy[c] - k - 1, 0)))) k++;
            ndr[c] = k;
        }
        boolean changed = nn != n || !java.util.Arrays.equals(ndy, dy) || !java.util.Arrays.equals(ndr, drops);
        n = nn; dy = ndy; drops = ndr;
        if (fresh) { fresh = false; derive(sl); changed = true; }
        for (int c = 0; c < n; c++) {
            if (sl.getBlockEntity(drumAt(c)) instanceof HangarDoorBlockEntity t) {
                int wk = c == 0 ? K_PLATE : dy[c - 1] != dy[c] ? K_CAP : K_NONE;
                int ek = c == n - 1 ? K_PLATE : dy[c + 1] != dy[c] ? K_CAP : K_NONE;
                t.setColumn(worldPosition, c, wk, ek, drops[c]);
            }
        }
        if (changed) sync();
    }

    private void derive(ServerLevel sl) {
        int most = 0;
        boolean all = true, any = false;
        for (int c = 0; c < n; c++) {
            int k = 0;
            while (k < drops[c] && ownCurtain(sl.getBlockState(cellAt(c, k + 1)))) k++;
            most = Math.max(most, k);
            any |= k > 0;
            all &= k == drops[c];
        }
        dir = 0; wait = W_NONE;
        travel = !any ? 0.0 : all ? tMax() : Math.max(0.0, Math.min(tMax(), most - BAR_H));
        if (any) setDrawn(sl, !all || slats.skinned(), all);
    }

    private boolean whole(ServerLevel sl) {
        for (int c = 0; c < n; c++) {
            BlockState s = sl.getBlockState(drumAt(c));
            if (!(s.getBlock() instanceof HangarDoorBlock) || s.getValue(MachineBlock.FACING) != facing()) return false;
        }
        return true;
    }

    void setColumn(BlockPos head, int c, int wk, int ek, int d) {
        boolean h = head.equals(worldPosition);
        if (head.equals(headPos) && c == col && wk == westKind && ek == eastKind && d == drop && h == isHead) return;
        headPos = head.immutable(); col = c; westKind = wk; eastKind = ek; drop = d; isHead = h;
        sync();
    }

    private void clearColumn() {
        if (headPos == null) return;
        headPos = null; col = 0; westKind = K_PLATE; eastKind = K_PLATE; drop = 0;
        sync();
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return false;
        long now = sl.getGameTime();
        if (now >= nextScan) {
            nextScan = now + Balance.HDOOR_SCAN_TICKS;
            boolean h = HangarDoorBlock.neighbour(sl, worldPosition, facing(), -1) == null;
            if (h != isHead) { isHead = h; fresh = true; sync(); }
            if (!isHead) {
                HangarDoorBlockEntity hd = headPos == null ? null : sl.getBlockEntity(headPos) instanceof HangarDoorBlockEntity x ? x : null;
                if (hd == null || !hd.isHead || col >= hd.n || !hd.drumAt(col).equals(worldPosition)) clearColumn();
                return false;
            }
            if (dir == 0 && wait == W_NONE) measure(sl);
            else if (!whole(sl)) { measure(sl); if (n == 0) { dir = 0; wait = W_NONE; snapshot(sl); } }
        }
        if (!isHead) return false;
        if (fresh) measure(sl);
        if ((now & 1) == 0 && n > 0) {
            boolean s = false;
            for (int c = 0; c < n && !s; c++) s = sl.hasNeighborSignal(drumAt(c));
            if (s != sig) { sig = s; command(s); }
        }
        if (dir == 0) return false;
        return step(sl, now);
    }

    private boolean step(ServerLevel sl, long now) {
        if (wait == W_BLOCKED && now < nextCheck) return false;
        double t1 = Math.max(0.0, Math.min(tMax(), travel + dir * SPEED));
        List<BlockPos> cells = new ArrayList<>();
        List<Integer> colOf = new ArrayList<>();
        if (dir > 0) {

            for (int c = 0; c < n; c++) {
                int done = (int) Math.floor(extension(travel, drops[c]) + EPS);
                int reach = (int) Math.ceil(extension(t1, drops[c]) - EPS);
                for (int k = done + 1; k <= reach; k++) {
                    if (cellBlocked(sl, cellAt(c, k))) {
                        if (wait != W_BLOCKED) {
                            playOnce(sl, WmdSounds.HD_SAFETY.get(), Balance.SND_CLICK, 1.0F);
                            wait = W_BLOCKED;
                            snapshot(sl);
                        }
                        nextCheck = now + Balance.HDOOR_BLOCKED_RECHECK;
                        return false;
                    }
                }
                int full = (int) Math.floor(extension(t1, drops[c]) + EPS);
                for (int k = done + 1; k <= full; k++) { cells.add(cellAt(c, k)); colOf.add(c); }
            }
        } else {
            for (int c = 0; c < n; c++) {
                int was = (int) Math.floor(extension(travel, drops[c]) + EPS), now1 = (int) Math.floor(extension(t1, drops[c]) + EPS);
                for (int k = was; k > now1; k--) { cells.add(cellAt(c, k)); colOf.add(c); }
            }
        }
        if (!cells.isEmpty() && !pay(sl, cells.size())) {
            if (wait != W_POWER) { wait = W_POWER; snapshot(sl); }
            return false;
        }
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            BlockState s = sl.getBlockState(p);
            if (dir > 0) { if (!ownCurtain(s) && freeCell(s)) sl.setBlock(p, curtain(colOf.get(i), true, false), 3); }
            else if (ownCurtain(s)) sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        if (dir < 0 && !cells.isEmpty()) Autopilot.ownDrop(sl, SableBridge.shipIdAt(sl, worldPosition));
        boolean resumed = wait != W_NONE;
        wait = W_NONE;
        travel = t1;
        if ((dir > 0 && travel >= tMax() - EPS) || (dir < 0 && travel <= EPS)) {
            boolean closed = dir > 0;
            dir = 0;
            playOnce(sl, closed ? WmdSounds.HD_STOP_FLOOR.get() : WmdSounds.HD_STOP_TOP.get(), Balance.SND_MECH, 1.0F);
            if (closed) {
                heal(sl);
                setDrawn(sl, slats.skinned(), true);
                playOnce(sl, WmdSounds.HD_LATCH.get(), Balance.SND_CLICK, 1.0F);
            }
            Wmd.LOGGER.info("Hangar door at {}: {} ({} columns)", worldPosition, closed ? "closed" : "open", n);
            snapshot(sl);
            return true;
        }
        if (resumed || now - syncAt >= 40) snapshot(sl);
        return true;
    }

    private void startStroke(ServerLevel sl, int d) {
        if (dir == 0) {
            setDrawn(sl, true, false);
            playOnce(sl, WmdSounds.HD_START.get(), Balance.SND_MECH, 1.0F);
        }
        dir = d;
        wait = W_NONE;
        snapshot(sl);
    }

    private BlockState curtain(int c, boolean drawn, boolean bottom) {
        int edge = c == 0 ? 1 : c == n - 1 ? 2 : 0;
        return HangarDoorBlocks.HANGAR_CURTAIN.get().defaultBlockState().setValue(HangarCurtainBlock.FACING, facing())
                .setValue(HangarCurtainBlock.EDGE, edge).setValue(HangarCurtainBlock.DRAWN, drawn).setValue(HangarCurtainBlock.BOTTOM, bottom);
    }

    private void setDrawn(ServerLevel sl, boolean drawn, boolean markBottom) {
        for (int c = 0; c < n; c++) for (int k = 1; k <= drops[c]; k++) {
            BlockPos p = cellAt(c, k);
            BlockState s = sl.getBlockState(p);
            if (!ownCurtain(s)) continue;
            BlockState t = curtain(c, drawn, markBottom && k == drops[c]);
            if (!s.equals(t)) sl.setBlock(p, t, 2);
        }
    }

    private void heal(ServerLevel sl) {
        boolean placed = false;
        for (int c = 0; c < n; c++) for (int k = 1; k <= drops[c]; k++) {
            BlockPos p = cellAt(c, k);
            BlockState s = sl.getBlockState(p);
            if (ownCurtain(s)) continue;
            if (!freeCell(s) || cellBlocked(sl, p) || !pay(sl, 1)) break;
            sl.setBlock(p, curtain(c, slats.skinned(), k == drops[c]), 3);
            placed = true;
        }
        if (placed) setDrawn(sl, slats.skinned(), true);
    }

    private boolean pay(ServerLevel sl, int cells) {
        int cost = cells * Balance.HDOOR_FE_PER_CELL;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship != null && ShipPower.draw(sl, ship, cost)) return true;
        return useExtraEnergy(cost);
    }

    boolean cellBlocked(ServerLevel sl, BlockPos p) {
        if (!freeCell(sl.getBlockState(p))) return true;
        Direction f = facing();
        double half = 3.0 / 16.0;
        AABB slab = f.getAxis() == Direction.Axis.Z
                ? new AABB(p.getX(), p.getY(), p.getZ() + 0.5 - half, p.getX() + 1, p.getY() + 1, p.getZ() + 0.5 + half)
                : new AABB(p.getX() + 0.5 - half, p.getY(), p.getZ(), p.getX() + 0.5 + half, p.getY() + 1, p.getZ() + 1);
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        AABB world = slab;
        if (own != null) {
            Vec3 a = SableBridge.toWorld(sl, new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
            if (a != null) world = new AABB(a, a).inflate(1.0);
        }
        for (Entity e : sl.getEntities((Entity) null, world.inflate(0.5), en -> !en.isSpectator() && en.isAlive()
                && (en instanceof LivingEntity || en instanceof VehicleEntity || en instanceof PalletEntity))) {
            AABB b = e.getBoundingBox();
            if (own != null) {
                Vec3 c = SableBridge.toShip(sl, own, b.getCenter());
                if (c == null) continue;
                b = new AABB(c, c).inflate(b.getXsize() / 2.0, b.getYsize() / 2.0, b.getZsize() / 2.0);
            }
            if (b.intersects(slab)) return true;
        }
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, world)) if (own == null || !own.equals(s.id())) return true;
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        nextScan = 0;
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }

    @Override protected SoundEvent runSound() { return n >= Balance.HDOOR_HEAVY_FROM ? WmdSounds.HD_LOOP_HEAVY.get() : WmdSounds.HD_LOOP.get(); }
    @Override protected float soundVolume() { return n >= Balance.HDOOR_HEAVY_FROM ? Balance.SND_HEAVY : Balance.SND_MACHINE; }

    @Override protected BlockPos soundLocal() { int m = n / 2; return n == 0 || m >= dy.length ? BlockPos.ZERO : new BlockPos(m, dy[m], 0); }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return isHead && side == Machines.dirToWorld(facing(), Direction.SOUTH) ? energy() : null; }

    @Override protected String detail() { return isHead ? n + " columns, " + new String[] { "NO RUN", "OPEN", "CLOSED", "HELD", "CLOSING", "OPENING", "BLOCKED", "NO POWER" }[doorState()] + String.format(", travel %.2f / %.2f", travel, tMax()) : "tile " + col; }

    @Override
    public Component status() {
        int m = 0;
        for (int d : drops) m = Math.max(m, d);
        return Component.translatable("block.wmd.hangar_door").append(": ").append(Component.translatable("message.wmd.hdoor.status_" + doorState(), n, m,
                energy().getEnergyStored(), Balance.HDOOR_FE_PER_CELL, Balance.HDOOR_W_MIN, Balance.HDOOR_W_MAX));
    }

    private void snapshot(ServerLevel sl) {
        syncAt = sl.getGameTime();
        sync();
        for (int c = 0; c < n; c++) sl.updateNeighbourForOutputSignal(drumAt(c), getBlockState().getBlock());
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void put(CompoundTag tag) {
        if (headPos != null) tag.putLong("hdHead", headPos.asLong());
        tag.putInt("hdCol", col); tag.putInt("hdWk", westKind); tag.putInt("hdEk", eastKind); tag.putInt("hdDrop", drop); tag.putBoolean("hdIsHead", isHead);
        tag.putInt("hdN", n); tag.putIntArray("hdDy", dy); tag.putIntArray("hdDrops", drops);
        tag.putDouble("hdT", travel); tag.putInt("hdDir", dir); tag.putInt("hdWait", wait);
    }

    private void get(CompoundTag tag) {
        headPos = tag.contains("hdHead") ? BlockPos.of(tag.getLong("hdHead")) : null;
        col = Math.max(0, tag.getInt("hdCol"));
        westKind = clampI(tag.contains("hdWk") ? tag.getInt("hdWk") : K_PLATE, 0, 2); eastKind = clampI(tag.contains("hdEk") ? tag.getInt("hdEk") : K_PLATE, 0, 2);
        drop = clampI(tag.getInt("hdDrop"), 0, Balance.HDOOR_DROP_MAX);
        isHead = tag.getBoolean("hdIsHead");
        int[] a = tag.getIntArray("hdDy"), b = tag.getIntArray("hdDrops");
        int nn = clampI(tag.getInt("hdN"), 0, Balance.HDOOR_W_MAX);
        if (a.length < nn || b.length < nn) nn = 0;
        n = nn;
        dy = java.util.Arrays.copyOf(a, nn);
        drops = java.util.Arrays.copyOf(b, nn);
        for (int i = 0; i < nn; i++) { dy[i] = clampI(dy[i], -Balance.HDOOR_STEP_MAX * Balance.HDOOR_W_MAX, Balance.HDOOR_STEP_MAX * Balance.HDOOR_W_MAX); drops[i] = clampI(drops[i], 0, Balance.HDOOR_DROP_MAX); }
        double t = tag.getDouble("hdT");
        travel = Double.isFinite(t) ? Math.max(0.0, Math.min(tMax(), t)) : 0.0;
        dir = Integer.signum(tag.getInt("hdDir"));
        wait = clampI(tag.getInt("hdWait"), 0, 2);
    }

    private static int clampI(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        hood.save(tag, registries);
        CompoundTag s = new CompoundTag();
        slats.save(s, registries);
        tag.put("hdSlats", s);
        put(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        hood.load(tag, registries, client);
        slats.load(tag.getCompound("hdSlats"), registries, client);
        get(tag);
        if (tag.contains("hdAt")) syncAt = tag.getLong("hdAt");
        if (!client) fresh = n == 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        hood.putSync(tag);
        CompoundTag s = new CompoundTag();
        slats.putSync(s);
        tag.put("hdSlats", s);
        put(tag);
        tag.putLong("hdAt", syncAt);
        return tag;
    }
}
