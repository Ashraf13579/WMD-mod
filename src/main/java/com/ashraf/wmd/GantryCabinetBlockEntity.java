package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class GantryCabinetBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, CopycatState.Host, Owned {

    public static final int PARTS = 54;

    static final int IDLE = 0, MOVE = 1, LOWER = 2, WELD = 3, RAISE = 4, R_MOVE = 5, R_LOWER = 6, RELOAD = 7, R_RAISE = 8;

    static final int ST_NO_RAIL = 0, ST_NO_CRAFT = 1, ST_READY = 2, ST_WORKING = 3, ST_DONE = 4, ST_NO_POWER = 5, ST_MOVED = 6, ST_PARTS = 7, ST_HATCH = 8;

    static final float UPPER = 23.4F, UNDER_ELBOW = 25.0F, SHOULDER = 3.6F, FOOT0 = 32.0F, MAST_X = 12.0F, MAST_V_MIN = 10.0F / 16.0F;

    private final CopycatState copycat = new CopycatState();
    private final OwnerData ownerData = new OwnerData();
    private final ItemStackHandler parts = new ItemStackHandler(PARTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };

    private boolean linked;
    private BlockPos railA = BlockPos.ZERO;
    private Direction vDir = Direction.NORTH;
    private int len, span;
    private float hatchU, hatchV, hatchY;

    private boolean running;
    private UUID target;
    private final List<Integer> queue = new ArrayList<>();
    private int job = -1, tote, phase = IDLE, phaseT, status = ST_NO_RAIL, toFix, missing, viewers;
    private float u, vMast, mast, arm, gU, gV, gMast, gArm;
    private BlockState held;
    private long lastRefresh = -1000;

    private long recTime;

    private float cU, cV, cMast, cArm, pU, pV, pMast, pArm;
    private int cPhase, cViewers;
    private BlockState cHeld;
    private long lastEase = -1;
    private float hatchOpen, pHatch, doorOpen, pDoor;

    public GantryCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.GANTRY_CABINET_BE.get(), pos, state, Balance.GANTRY_FE);
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }
    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    IItemHandler parts() { return parts; }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        for (int i = 0; i < PARTS; i++) {
            ItemStack s = parts.getStackInSlot(i);
            if (!s.isEmpty()) { l.add(s.copy()); parts.setStackInSlot(i, ItemStack.EMPTY); }
        }
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private Direction front() { return Machines.dirToWorld(facing(), Direction.UP, Direction.NORTH); }
    private Direction east() { return Machines.dirToWorld(facing(), Direction.UP, Direction.EAST); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return part.equals(worldPosition) && side == front().getOpposite() ? energy() : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        return part.equals(Machines.toWorld(worldPosition, facing(), Direction.UP, 1, 0, 0)) && side == east() ? parts : null;
    }

    void viewer(boolean open) { viewers = Math.max(0, viewers + (open ? 1 : -1)); sync(); }

    Direction vDir() { return level != null && level.isClientSide ? cvDir : vDir; }
    Direction uDir() { return vDir().getClockWise(); }
    boolean linked() { return level != null && level.isClientSide ? cLinked : linked; }
    BlockPos railA() { return level != null && level.isClientSide ? cRailA : railA; }
    int span() { return level != null && level.isClientSide ? cSpan : span; }
    int len() { return level != null && level.isClientSide ? cLen : len; }

    private boolean cLinked;
    private BlockPos cRailA = BlockPos.ZERO;
    private Direction cvDir = Direction.NORTH;
    private int cSpan, cLen;

    Vec3 origin() { BlockPos a = railA(); return new Vec3(a.getX() + 0.5, a.getY() + 15.6 / 16.0, a.getZ() + 0.5); }

    private static Vec3 dir(Direction d) { return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }

    double[] frame(Vec3 w) {
        Vec3 r = w.subtract(origin());
        return new double[] { r.dot(dir(uDir())), r.dot(dir(vDir())), -r.y };
    }

    private static boolean rail(Level l, BlockPos p, Direction railFront) {
        BlockState s = l.getBlockState(p);
        return s.getBlock() instanceof GantryRailBlock && Machines.dirToWorld(s.getValue(MachineBlock.FACING), Direction.UP, Direction.NORTH) == railFront;
    }

    private boolean link(ServerLevel sl) {
        Direction f = front(), ud = f.getClockWise();
        BlockPos hit = null;
        search:
        for (int dy = 2; dy <= Balance.GANTRY_RAIL_UP; dy++) for (int lx = 0; lx <= 1; lx++) {
            BlockPos p = Machines.toWorld(worldPosition, facing(), Direction.UP, lx, dy, 0);
            if (rail(sl, p, f)) { hit = p; break search; }
            if (rail(sl, p.relative(f), f)) { hit = p.relative(f); break search; }
        }
        if (hit == null) return false;
        BlockPos a0 = hit, a1 = hit;
        while (rail(sl, a0.relative(ud.getOpposite()), f) && a0.distManhattan(hit) < Balance.GANTRY_LEN_MAX) a0 = a0.relative(ud.getOpposite());
        while (rail(sl, a1.relative(ud), f) && a1.distManhattan(a0) < Balance.GANTRY_LEN_MAX - 1) a1 = a1.relative(ud);
        int n = a0.distManhattan(a1) + 1;
        if (n < Balance.GANTRY_LEN_MIN) return false;
        for (int d = Balance.GANTRY_SPAN_MIN; d <= Balance.GANTRY_SPAN_MAX; d++) {
            if (!rail(sl, a0.relative(f, d), f.getOpposite())) continue;
            boolean all = true;
            for (int k = 0; k < n && all; k++) all = rail(sl, a0.relative(ud, k).relative(f, d), f.getOpposite());
            if (!all) continue;
            linked = true; railA = a0; vDir = f; len = n; span = d;

            Vec3 hatch = Vec3.atCenterOf(worldPosition).add(dir(east()).scale(0.5)).add(dir(f).scale(0.5 + Balance.GANTRY_HANDOVER_OUT)).add(0.0, 1.5, 0.0);
            double[] h = frame(hatch);
            hatchU = (float) h[0]; hatchV = (float) h[1]; hatchY = (float) (worldPosition.getY() + 2.0);
            return true;
        }
        return false;
    }

    private UUID findCraft(ServerLevel sl) {
        UUID owner = getOwner();
        if (owner == null || !SableBridge.active()) return null;
        Vec3 o = origin();
        Vec3 far = o.add(dir(uDir()).scale(len - 1)).add(dir(vDir).scale(span));
        AABB box = new AABB(o, far).inflate(0.5, 0.0, 0.5).expandTowards(0.0, -Balance.GANTRY_MAST_MAX / 16.0 - 6.0, 0.0);
        AirframeRecords d = AirframeRecords.get(sl.getServer());
        UUID best = null;
        double bd = Double.MAX_VALUE;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, box)) {
            AirframeRecords.Rec r = d.now(s.id());
            if (r == null || !ShipStrike.sameSide(owner, r.owner)) continue;
            if (!Crafts.parked(sl, s.id(), null, Balance.GANTRY_PARKED)) continue;
            if (!inside(s.worldBounds())) continue;
            double dd = s.worldCenter().distanceToSqr(o);
            if (dd < bd) { bd = dd; best = s.id(); }
        }
        return best;
    }

    private boolean inside(AABB b) {
        for (int i = 0; i < 4; i++) {
            double[] f = frame(new Vec3((i & 1) == 0 ? b.minX : b.maxX, b.minY, (i & 2) == 0 ? b.minZ : b.maxZ));
            if (f[0] < -0.5 || f[0] > len - 0.5 || f[1] < 0.5 || f[1] > span - 0.5) return false;
        }
        return true;
    }

    private void refresh(ServerLevel sl) {
        lastRefresh = sl.getGameTime();
        boolean was = linked;
        linked = false;
        if (!link(sl)) { status = ST_NO_RAIL; target = null; queue.clear(); if (was) sync(); return; }
        if (!was && phase == IDLE) {
            u = Math.max(0.0F, Math.min(len - 1, hatchU)); vMast = MAST_V_MIN; mast = 0.0F; arm = 90.0F;
        }
        UUID t = target != null && SableBridge.shipInfo(sl, target) != null ? target : findCraft(sl);
        if (t != null) {
            SableBridge.ShipInfo s = SableBridge.shipInfo(sl, t);
            if (s == null || !inside(s.worldBounds()) || !Crafts.parked(sl, t, null, Balance.GANTRY_PARKED)) t = null;
        }
        if (t == null) { target = null; queue.clear(); toFix = 0; status = running ? ST_MOVED : ST_NO_CRAFT; if (running) stop(); sync(); return; }
        target = t;
        buildQueue(sl);
        if (!running) status = toFix == 0 ? ST_DONE : ST_READY;
        if (!was) sync();
    }

    private void buildQueue(ServerLevel sl) {
        queue.clear();
        AirframeRecords.Rec r = AirframeRecords.get(sl.getServer()).now(target);
        if (r == null) { toFix = 0; return; }
        recTime = r.time;
        byte[] st = AirframeRecords.compare(sl, r, new CompoundTag());
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < st.length; i++) {
            if (st[i] == AirframeRecords.OK) continue;
            Vec3 w = SableBridge.toWorld(sl, Vec3.atCenterOf(new BlockPos(r.x(i), r.y(i), r.z(i))));
            double[] f = frame(w);
            cells.add(new int[] { i, (int) Math.round(f[0]), (int) Math.round(f[1] * 4) });
        }
        cells.sort((a, b) -> a[1] != b[1] ? a[1] - b[1] : (a[1] % 2 == 0 ? a[2] - b[2] : b[2] - a[2]));
        for (int[] c : cells) queue.add(c[0]);
        toFix = queue.size();
    }

    void toggle(ServerLevel sl) {
        if (running) { stop(); return; }
        refresh(sl);
        if (!linked || target == null || queue.isEmpty()) return;
        running = true;
        status = ST_WORKING;
        missing = 0;
        sync();
    }

    private void stop() {
        running = false;
        job = -1;
        held = null;
        phase = RAISE;
        gMast = 0.0F; gArm = 90.0F;
        status = linked ? (target == null ? ST_NO_CRAFT : toFix == 0 ? ST_DONE : ST_READY) : ST_NO_RAIL;
        sync();
    }

    private float[] goalFor(Vec3 w) {
        double[] f = frame(w);
        double down = f[2] * 16.0;
        if (f[0] < -0.01 || f[0] > len - 0.99 || f[1] < 0.5 || f[1] > span - 0.5) return null;
        return goal((float) f[0], (float) f[1], (float) down);
    }

    private float[] goal(float gu, float v, float down) {
        float minDown = FOOT0 + SHOULDER + UNDER_ELBOW;
        if (down < minDown || down > FOOT0 + SHOULDER + UPPER + UNDER_ELBOW + Balance.GANTRY_MAST_MAX) return null;
        float straight = FOOT0 + SHOULDER + UPPER + UNDER_ELBOW;
        float m = Math.max(0.0F, down - straight);
        float c = Math.min(1.0F, (down - m - minDown) / UPPER);
        float a = (float) Math.toDegrees(Math.acos(c));
        float off = UPPER * (float) Math.sin(Math.toRadians(a)) / 16.0F;
        float vm = v - off;
        if (vm < MAST_V_MIN) { a = -a; vm = v + off; }
        if (vm >= MAST_V_MIN && vm <= span - MAST_V_MIN) return new float[] { gu, vm, m, a };

        boolean nearA = v < MAST_V_MIN;
        float vEnd = nearA ? MAST_V_MIN : span - MAST_V_MIN, need = Math.abs(v - vEnd);
        if (need * 16.0F > UPPER) return null;
        float b = (float) Math.toDegrees(Math.asin(need * 16.0F / UPPER));
        float mb = down - minDown - UPPER * (float) Math.cos(Math.toRadians(b));
        if (mb < 0.0F || mb > Balance.GANTRY_MAST_MAX) return null;
        return new float[] { gu, vEnd, mb, nearA ? -b : b };
    }

    private static float step(float x, float goal, float speed) { return x < goal ? Math.min(goal, x + speed) : Math.max(goal, x - speed); }

    private boolean at(float x, float g) { return Math.abs(x - g) < 1.0E-3; }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return false;
        long now = sl.getGameTime();
        if (now - lastRefresh >= Balance.GANTRY_REFRESH) refresh(sl);
        if (!running && phase == IDLE) return false;
        phaseT++;
        boolean moving = true;
        switch (phase) {
            case MOVE, R_MOVE -> {
                if (!at(mast, 0.0F) || !at(arm, gArm == 0 ? 90.0F : Math.signum(gArm) * 90.0F)) {
                    mast = step(mast, 0.0F, Balance.GANTRY_MAST_SPEED * 16.0F);
                    arm = step(arm, gArm < 0 ? -90.0F : 90.0F, Balance.GANTRY_ARM_SPEED);
                } else {
                    u = step(u, gU, (float) Balance.GANTRY_BRIDGE_SPEED);
                    vMast = step(vMast, gV, (float) Balance.GANTRY_TROLLEY_SPEED);
                    if (at(u, gU) && at(vMast, gV)) setPhase(phase == MOVE ? LOWER : R_LOWER);
                }
            }
            case LOWER, R_LOWER -> {
                mast = step(mast, gMast, Balance.GANTRY_MAST_SPEED * 16.0F);
                arm = step(arm, gArm, Balance.GANTRY_ARM_SPEED);
                if (at(mast, gMast) && at(arm, gArm)) setPhase(phase == LOWER ? WELD : RELOAD);
            }
            case WELD -> {
                moving = false;
                if (phaseT == 4) placeJob(sl);
                if (phaseT >= weldTicks()) { job = -1; setPhase(RAISE); }
            }
            case RELOAD -> {
                moving = false;
                if (phaseT >= Balance.GANTRY_RELOAD_TICKS) { tote = Balance.GANTRY_TOTE; setPhase(R_RAISE); }
            }
            case RAISE, R_RAISE -> {
                mast = step(mast, 0.0F, Balance.GANTRY_MAST_SPEED * 16.0F);
                arm = step(arm, arm < 0 ? -90.0F : 90.0F, Balance.GANTRY_ARM_SPEED);
                if (at(mast, 0.0F) && Math.abs(Math.abs(arm) - 90.0F) < 1.0E-3) { setPhase(IDLE); if (running) next(sl); }
            }
            default -> { moving = false; if (running) next(sl); }
        }
        if (moving && now % 4 == 0) sync();
        return running || phase != IDLE;
    }

    private int weldTicks() {
        return job >= 0 && jobStatus == AirframeRecords.DAMAGED ? Balance.GANTRY_ENGINE_TICKS : Balance.GANTRY_WELD_TICKS;
    }

    private byte jobStatus;

    private void setPhase(int p) { phase = p; phaseT = 0; sync(); }

    private void next(ServerLevel sl) {
        AirframeRecords.Rec r = target == null ? null : AirframeRecords.get(sl.getServer()).now(target);
        SableBridge.ShipInfo s = target == null ? null : SableBridge.shipInfo(sl, target);
        if (r == null || s == null || !Crafts.parked(sl, target, null, Balance.GANTRY_PARKED)) { stop(); status = ST_MOVED; sync(); return; }
        if (r.time != recTime) buildQueue(sl);
        byte[] st = AirframeRecords.compare(sl, r, new CompoundTag());
        while (!queue.isEmpty()) {
            int i = queue.remove(0);
            if (i >= st.length || st[i] == AirframeRecords.OK) continue;
            Item need = needs(r, i, st[i]);
            if (need == Items.BARRIER) continue;
            if (need != null && count(need) == 0) { missing++; continue; }
            BlockPos plot = new BlockPos(r.x(i), r.y(i), r.z(i));
            if (st[i] == AirframeRecords.WRONG && sl.getBlockState(plot).hasBlockEntity()) continue;
            float[] g = goalFor(SableBridge.toWorld(sl, Vec3.atCenterOf(plot)));
            if (g == null) continue;
            if (need != null && tote <= 0) {
                queue.add(0, i);
                float[] h = goal(hatchU, hatchV, (float) ((origin().y - hatchY) * 16.0));

                if (h == null) { stop(); status = ST_HATCH; sync(); return; }
                gU = h[0]; gV = h[1]; gMast = h[2]; gArm = h[3];
                setPhase(R_MOVE);
                return;
            }
            job = i; jobStatus = st[i];
            held = st[i] == AirframeRecords.MISSING || st[i] == AirframeRecords.WRONG ? r.state(i) : null;
            gU = g[0]; gV = g[1]; gMast = g[2]; gArm = g[3];
            setPhase(MOVE);
            return;
        }
        running = false;
        held = null;
        buildQueue(sl);
        status = missing > 0 ? ST_PARTS : ST_DONE;
        playOnce(sl, WmdSounds.SCANNER_PING.get(), Balance.SND_CLICK, missing > 0 ? 0.8F : 1.2F);
        setPhase(IDLE);
    }

    private Item needs(AirframeRecords.Rec r, int i, byte st) {
        BlockState want = r.state(i);
        Item it = want.getBlock().asItem();
        return switch (st) {
            case AirframeRecords.MISSING, AirframeRecords.WRONG -> it == Items.AIR ? Items.BARRIER : it;
            case AirframeRecords.DAMAGED -> Wmd.ENGINE_REPAIR_KIT.get();
            case AirframeRecords.SKIN -> skinItem(r, i);
            default -> null;
        };
    }

    private Item skinItem(AirframeRecords.Rec r, int i) {
        CompoundTag sp = r.spec.get(i);
        if (sp == null || level == null) return null;
        for (String k : AirframeRecords.SKIN_KEYS) {
            if (!sp.contains(k)) continue;
            BlockState s = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), sp.getCompound(k));
            return s.isAir() ? null : s.getBlock().asItem();
        }
        return null;
    }

    private int count(Item it) {
        int n = 0;
        for (int k = 0; k < PARTS; k++) if (parts.getStackInSlot(k).is(it)) n += parts.getStackInSlot(k).getCount();
        return n;
    }

    private ItemStack take(Item it) {
        if (it == null) return ItemStack.EMPTY;
        for (int k = 0; k < PARTS; k++) if (parts.getStackInSlot(k).is(it)) return parts.extractItem(k, 1, false);
        return ItemStack.EMPTY;
    }

    private boolean store(ItemStack s) {
        ItemStack left = s.copy();
        for (int k = 0; k < PARTS && !left.isEmpty(); k++) left = parts.insertItem(k, left, false);
        return left.isEmpty();
    }

    private void placeJob(ServerLevel sl) {
        AirframeRecords.Rec r = target == null ? null : AirframeRecords.get(sl.getServer()).now(target);
        if (r == null || job < 0 || job >= r.size()) return;
        BlockPos p = new BlockPos(r.x(job), r.y(job), r.z(job));
        if (r.time != recTime || !target.equals(SableBridge.shipIdAt(sl, p)) || !Crafts.parked(sl, target, null, Balance.GANTRY_PARKED)) {
            stop(); status = ST_MOVED; sync();
            return;
        }
        BlockState want = r.state(job), cur = sl.getBlockState(p);
        boolean same = cur.getBlock() == want.getBlock();
        switch (jobStatus) {
            case AirframeRecords.WRONG, AirframeRecords.MISSING -> {
                if (same) break;
                boolean there = AirframeRecords.solid(cur);
                if (there && cur.hasBlockEntity()) break;
                if (want.getBlock() instanceof MachineBlock mb && !footprintFree(sl, mb, p, want)) { missing++; break; }
                Item part = want.getBlock().asItem();
                if (count(part) == 0) { missing++; break; }
                if (there) {
                    ItemStack old = new ItemStack(cur.getBlock().asItem());
                    if (!old.isEmpty() && !store(old)) { missing++; break; }
                }
                ItemStack used = take(part);
                tote--;
                sl.setBlock(p, want, 3);
                if (want.getBlock() instanceof MachineBlock mb) {
                    mb.setPlacedBy(sl, p, want, null, used);
                    giveOwner(sl, p, r.owner);
                }
                applySpec(sl, p, r.spec.get(job));
            }
            case AirframeRecords.SHAPE -> { if (same) sl.setBlock(p, want, 3); }
            case AirframeRecords.DAMAGED -> {
                if (sl.getBlockEntity(p) instanceof AirEngineBlockEntity ae && ae.damageable() && ae.hp() < Balance.ENGINE_HP
                        && count(Wmd.ENGINE_REPAIR_KIT.get()) > 0 && ae.repair()) { take(Wmd.ENGINE_REPAIR_KIT.get()); tote--; }
            }
            case AirframeRecords.SKIN, AirframeRecords.SETTING -> { if (same) applySpec(sl, p, r.spec.get(job)); }
            default -> {}
        }
        held = null;
        playOnce(sl, WmdSounds.ARC_STRIKE.get(), Balance.SND_MECH, 1.3F);
        sync();
    }

    private static boolean footprintFree(ServerLevel sl, MachineBlock mb, BlockPos p, BlockState want) {
        Direction facing = want.getValue(MachineBlock.FACING), mount = Machines.mount(want);
        for (BlockPos l : mb.footprint()) {
            BlockPos q = Machines.toWorld(p, facing, mount, l.getX(), l.getY(), l.getZ());
            if (!q.equals(p) && !sl.getBlockState(q).canBeReplaced()) return false;
        }
        return true;
    }

    private static void giveOwner(ServerLevel sl, BlockPos p, UUID owner) {
        BlockEntity be = sl.getBlockEntity(p);
        if (owner == null || !(be instanceof Owned o) || o.getOwner() != null) return;
        HolderLookup.Provider reg = sl.registryAccess();
        CompoundTag t = be.saveWithoutMetadata(reg);
        t.putUUID("owner", owner);
        t.putString("ownerName", sl.getServer().getProfileCache() == null ? "" : sl.getServer().getProfileCache().get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(""));
        be.loadWithComponents(t, reg);
        be.setChanged();
    }

    private void applySpec(ServerLevel sl, BlockPos p, CompoundTag spec) {
        BlockEntity be = sl.getBlockEntity(p);
        if (be == null) return;
        HolderLookup.Provider reg = sl.registryAccess();
        CompoundTag cur = be.saveWithoutMetadata(reg);
        CompoundTag want = spec == null ? new CompoundTag() : spec;
        for (String k : AirframeRecords.SKIN_KEYS) {
            String ik = "ccSkin".equals(k) ? "ccItem" : k + "Item";
            boolean had = cur.contains(k), wants = want.contains(k);
            if (had && wants && java.util.Objects.equals(cur.get(k), want.get(k))) continue;
            if (!had && !wants) continue;
            if (cur.contains(ik)) {
                ItemStack old = ItemStack.parseOptional(reg, cur.getCompound(ik));
                if (!old.isEmpty() && !store(old)) return;
                cur.remove(ik);
            }
            cur.remove(k);
            if (wants) {
                BlockState s = NbtUtils.readBlockState(sl.holderLookup(Registries.BLOCK), want.getCompound(k));
                ItemStack used = s.isAir() ? ItemStack.EMPTY : take(s.getBlock().asItem());
                if (!s.isAir() && used.isEmpty()) { missing++; continue; }
                cur.put(k, want.get(k).copy());
                if (!used.isEmpty()) cur.put(ik, used.save(reg));
            }
        }
        for (String k : AirframeRecords.SETTING_KEYS) if (want.contains(k)) cur.put(k, want.get(k).copy());
        be.loadWithComponents(cur, reg);
        be.setChanged();
        BlockState st = sl.getBlockState(p);
        sl.sendBlockUpdated(p, st, st, 3);
    }

    int statusCode() { return status; }
    int toFix() { return toFix; }
    int missing() { return missing; }
    int tote() { return tote; }
    boolean running() { return running; }

    UUID targetCraft() { return target; }

    boolean startFor(ServerLevel sl, UUID ship) {
        if (running || ship == null) return false;
        refresh(sl);
        if (!ship.equals(target)) return false;
        toggle(sl);
        return running;
    }
    int energyFe() { return energy().getEnergyStored(); }

    Component craftName(ServerLevel sl) {
        SableBridge.ShipInfo s = target == null ? null : SableBridge.shipInfo(sl, target);
        return s == null || s.name() == null || s.name().isBlank() ? Component.literal("-") : Component.literal(s.name());
    }

    void openAirframe(net.minecraft.server.level.ServerPlayer sp) { if (target != null) AirframeRecords.openFor(sp, target); }

    @Override
    public Component status() {
        return Component.translatable("block.wmd.gantry_cabinet").append(": ").append(Component.translatable("gui.wmd.gantry.st." + status));
    }

    @Override protected void clientEffects(Level lv) {}

    void ease() {
        if (level == null || level.getGameTime() == lastEase) return;
        if (lastEase < 0) { cU0 = cU; cV0 = cV; cMast0 = cMast; cArm0 = cArm; }
        lastEase = level.getGameTime();
        pU = cU0; pV = cV0; pMast = cMast0; pArm = cArm0;
        cU0 = step(cU0, cU, (float) Balance.GANTRY_BRIDGE_SPEED * 1.5F);
        cV0 = step(cV0, cV, (float) Balance.GANTRY_TROLLEY_SPEED * 1.5F);
        cMast0 = step(cMast0, cMast, Balance.GANTRY_MAST_SPEED * 24.0F);
        cArm0 = step(cArm0, cArm, Balance.GANTRY_ARM_SPEED * 1.5F);
        pHatch = hatchOpen; pDoor = doorOpen;
        boolean reload = cPhase == R_LOWER || cPhase == RELOAD || cPhase == R_RAISE;
        hatchOpen = step(hatchOpen, reload ? 1.0F : 0.0F, 0.1F);
        doorOpen = step(doorOpen, cViewers > 0 ? 1.0F : 0.0F, 0.15F);
    }

    private float cU0, cV0, cMast0, cArm0;

    float[] crane(float pt) {
        return new float[] { pU + (cU0 - pU) * pt, pV + (cV0 - pV) * pt, pMast + (cMast0 - pMast) * pt, pArm + (cArm0 - pArm) * pt };
    }

    int clientPhase() { return cPhase; }
    BlockState clientHeld() { return cHeld; }
    float clientHatch(float pt) { return pHatch + (hatchOpen - pHatch) * pt; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        ease();
        float d = (float) Math.toRadians(Balance.GANTRY_DOOR_OPEN) * (pDoor + (doorOpen - pDoor) * pt);
        float h = (float) Math.toRadians(Balance.GANTRY_HATCH_OPEN) * clientHatch(pt);
        model.getBone("housing_door_l").ifPresent(b -> b.setRotY(d));
        model.getBone("housing_door_r").ifPresent(b -> b.setRotY(-d));
        model.getBone("housing_hatch_f").ifPresent(b -> b.setRotX(-h));
        model.getBone("housing_hatch_b").ifPresent(b -> b.setRotX(h));
        model.getBone("lift").ifPresent(b -> b.setPosY(6.0F * clientHatch(pt)));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.T4_CRANE_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return toFix + " / " + tote; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.put("gtParts", parts.serializeNBT(registries));
        tag.putInt("gtTote", tote);
        putSync(tag);
    }

    private void putSync(CompoundTag tag) {
        tag.putBoolean("gtL", linked);
        tag.putLong("gtA", railA.asLong());
        tag.putInt("gtV", vDir.get3DDataValue());
        tag.putInt("gtLen", len);
        tag.putInt("gtSpan", span);
        tag.putFloat("gtU", u);
        tag.putFloat("gtVm", vMast);
        tag.putFloat("gtM", mast);
        tag.putFloat("gtArm", arm);
        tag.putInt("gtPh", phase);
        tag.putInt("gtView", viewers);
        if (held != null) tag.put("gtHeld", NbtUtils.writeBlockState(held));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        boolean client = level != null && level.isClientSide;
        cLinked = tag.getBoolean("gtL");
        cRailA = BlockPos.of(tag.getLong("gtA"));
        cvDir = Direction.from3DDataValue(tag.getInt("gtV"));
        if (cvDir.getAxis() == Direction.Axis.Y) cvDir = Direction.NORTH;
        cLen = tag.getInt("gtLen");
        cSpan = tag.getInt("gtSpan");
        cU = tag.getFloat("gtU"); cV = tag.getFloat("gtVm"); cMast = tag.getFloat("gtM"); cArm = tag.getFloat("gtArm");
        cPhase = tag.getInt("gtPh");
        cViewers = tag.getInt("gtView");
        cHeld = tag.contains("gtHeld") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("gtHeld")) : null;
        if (client) return;
        if (tag.contains("gtParts")) parts.deserializeNBT(registries, tag.getCompound("gtParts"));
        tote = tag.getInt("gtTote");
        linked = cLinked; railA = cRailA; vDir = cvDir; len = cLen; span = cSpan;
        u = cU; vMast = cV; mast = cMast; arm = cArm;
        phase = IDLE;
        viewers = 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        putSync(tag);
        return tag;
    }
}
