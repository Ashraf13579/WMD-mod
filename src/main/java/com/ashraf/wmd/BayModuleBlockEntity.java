package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BayModuleBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, MachinePorts, EmpVulnerable {
    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(net.minecraft.world.entity.player.Player player) { ownerData.claim(player); setChanged(); }

    public enum Stage {
        IDLE(0, -1), FETCH(Balance.BAY_FETCH_TICKS, -1), ROLL_WELD(Balance.BAY_ROLL_WELD_TICKS, -1), WELD(Balance.BAY_WELD_TICKS, -1), ROLL_CRANE(Balance.BAY_ROLL_CRANE_TICKS, -1),
        CRANE(Balance.BAY_CRANE_TICKS, -1), ROLL_WARHEAD(Balance.BAY_ROLL_WARHEAD_TICKS, -1), WARHEAD(Balance.BAY_WARHEAD_TICKS, -1),
        ROLL_CLEAN(Balance.BAY_ROLL_CLEAN_TICKS, 0), CLEAN(Balance.BAY_CLEAN_TICKS, 0), ROLL_PAINT(Balance.BAY_ROLL_PAINT_TICKS, 1), PAINT(Balance.BAY_PAINT_TICKS, 1),
        ROLL_CRYO(Balance.BAY_ROLL_CRYO_TICKS, 2), CRYO(Balance.BAY_CRYO_TICKS, 2), RETURN(Balance.BAY_RETURN_TICKS, -1),

        ROLL_STORE(Balance.BAY_ROLL_STORE_TICKS, -1, true), STORE(Balance.BAY_STORE_TICKS, -1, true);
        public final int ticks;

        public final boolean store;

        public final int station;
        Stage(int ticks, int station) { this(ticks, station, false); }
        Stage(int ticks, int station, boolean store) { this.ticks = ticks; this.station = station; this.store = store; }

        int len(int stations) { return len(stations, false); }

        int len(int stations, boolean storing) { return this == RETURN ? ticks + stations * Balance.BAY_RETURN_PER_STATION + (storing ? Balance.BAY_RETURN_STORE_EXTRA : 0) : ticks; }

        int before(int stations, boolean storing) {
            int n = 0;
            for (Stage s : timeline(stations, storing)) { if (s == this) break; n += s.len(stations, storing); }
            return n;
        }
        private static java.util.List<Stage> timeline(int stations, boolean storing) {
            java.util.List<Stage> l = new java.util.ArrayList<>();
            for (Stage s : values()) { if (s.store || s == RETURN || s.station >= stations) continue; l.add(s); }
            if (storing) { l.add(ROLL_STORE); l.add(STORE); }
            l.add(RETURN);
            return l;
        }
    }

    public static final int FLAG_READY = 1, FLAG_LINKED = 2, FLAG_OUT = 4, FLAG_NEEDS = 8, FLAG_GRADE = 16, FLAG_SIZE = 32, FLAG_RUNWAY = 64, FLAG_MODE = 128;

    private static class Buffer extends EnergyStorage {
        Buffer(int capacity, int maxReceive) { super(capacity, maxReceive, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    public final BayKind kind;

    public final ItemStackHandler parts = new ItemStackHandler(4) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof MissilePartItem p && p.slot() == slot; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public final ItemStackHandler out = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return false; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final Buffer store = new Buffer(Balance.BAY_BUFFER_FE, Balance.BAY_RECEIVE_FE);

    public final ItemStackHandler uplinkSlot = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return Uplink.isCell(s); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public static final int UPLINK_BIT = 4;

    public final ItemStackHandler camSlot = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return false; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public static final int CAM_BIT = 5;

    public final ItemStackHandler seekerSlot = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof SeekerModuleItem || isCamPod(s); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public static final int SEEKER_BIT = 6;

    public static boolean isCamPod(ItemStack s) { return s.is(Wmd.CAM_POD.get()); }

    @Override
    public void empDrain() { store.set(0); setChanged(); }
    private Stage stage = Stage.IDLE;
    private int stageLen, pending, flags, stockMask;
    private int stations, outTier = 1;

    private boolean storing, stored;
    private int storeSlot = -1, storeFrom, rackSt;

    private int storeRack;

    public static final int RACK_SLOTS = 6;
    private final ItemStackHandler racked = new ItemStackHandler(RACK_SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); sync(); }
    };
    private int top;

    private int mode, runMode;
    private int sizeLevel = 1;
    private MissileBuild build = MissileBuild.preset(1);
    private long stageStart;
    private int syncedStock = -1, syncedFlags = -1;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private Stage seenStage = Stage.IDLE;
    private double seenZ = -1;
    private int seenHeld = -2;
    private long seatedAt = -1000;

    public BayModuleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, BayKind kind) {
        super(type, pos, state);
        this.kind = kind;
    }

    public Stage stage() { return stage; }

    public MissileBuild build() { return build; }

    public boolean showPainted(float partialTick) { return paintProgress(partialTick) >= 1.0F; }

    public float paintProgress(float partialTick) {
        boolean heavy = build.calibre() >= 3;
        float f = level != null && stageLen > 0 ? net.minecraft.util.Mth.clamp((level.getGameTime() + partialTick - stageStart) / stageLen, 0.0F, 1.0F) : 1.0F;
        return switch (stage) {
            case IDLE, STORE -> 1.0F;
            case ROLL_STORE -> heavy ? 1.0F : f;
            case ROLL_CRYO, CRYO -> heavy ? 1.0F : 0.0F;
            case RETURN -> heavy ? 1.0F : f;
            case PAINT -> heavy ? net.minecraft.util.Mth.clamp((f - 0.04F) / 0.42F, 0.0F, 1.0F) : 0.0F;
            default -> 0.0F;
        };
    }
    public int flags() { return flags; }
    public int stockMask() { return stockMask; }
    public int energy() { return store.getEnergyStored(); }
    public int stageLen() { return stageLen; }
    public long stageStart() { return stageStart; }
    public int sizeLevel() { return sizeLevel; }
    public int mode() { return mode; }
    public int runMode() { return runMode; }

    public void cycleMode() { if (kind != BayKind.CORE || stage != Stage.IDLE) return; mode = (mode + 1) % MODES; if (mode == MODE_AIR && !AirGate.on()) mode = 0; setChanged(); sync(); }

    public boolean setMode(int m) {
        if (kind != BayKind.CORE || stage != Stage.IDLE || m < 0 || m >= MODES || (m == MODE_AIR && !AirGate.on()) || m == mode) return false;
        mode = m; setChanged(); sync(); return true;
    }

    public static final int MODES = 7, MODE_CRUISE = MissileBuild.KIND_CRUISE;

    public static final int MODE_AIR = 6;

    public static final int[] MODE_MIN_SIZE = { 1, 4, 4, 2, 2, 3, 2 };

    public static final int MODE_SLBM = 5;

    public static int kindOf(int mode) { return mode == MODE_SLBM ? MissileBuild.KIND_SLBM : mode == MODE_AIR ? 0 : mode; }

    public static int airKindOf(String seeker) {
        return MissileBuild.SEEK_HEAT.equals(seeker) ? AirMissiles.HEAT : MissileBuild.SEEK_RADAR.equals(seeker) ? AirMissiles.RADAR
                : MissileBuild.SEEK_ARM.equals(seeker) ? AirMissiles.ARM : MissileBuild.SEEK_LASER.equals(seeker) ? AirMissiles.AGM : -1;
    }

    public static final int AIR_ASHM = 4;

    public static int airKindOf(String seeker, boolean jet) { int k = airKindOf(seeker); return k == AirMissiles.RADAR && jet ? AIR_ASHM : k; }

    public static int airArt(int k) { return k == AIR_ASHM ? AirMissiles.RADAR : k; }

    public static final int[] AIR_TIER = { 2, 3, 3, 2, 3 };

    public int airKind() { return airKindOf(SeekerModuleItem.kindOf(seekerSlot.getStackInSlot(0)), jetIn()); }

    private int runAir = -1;
    public int runAir() { return runAir; }

    public int airKindShown() { return airArt(runAir >= 0 && stage != Stage.IDLE ? runAir : airKind()); }

    public boolean airRun() { return runMode == MODE_AIR && (stage != Stage.IDLE || (flags & FLAG_OUT) != 0); }

    static boolean groundWarhead(String w) {
        return !w.equals("interceptor") && !w.startsWith(Satellites.PREFIX) && !w.equals(Asat.ID) && !w.equals(Cubesat.ID) && !w.equals(Crew.ID) && !w.startsWith(StationKit.PREFIX) && !w.equals(Cargo.ID);
    }

    public boolean jetIn() { return parts.getStackInSlot(1).getItem() instanceof MissilePartItem p && p.isJet(); }

    public static int requiredMask(int mode) { return mode == 1 || mode == 3 ? 3 : 15; }

    public boolean modeOk() {
        int a = partTier(0);
        if (mode == MODE_AIR) {
            int k = airKind();
            if (k < 0 || a != AIR_TIER[k] || (k != AIR_ASHM && partTier(1) != a) || partTier(2) != a || jetIn() != (k == AIR_ASHM)) return false;
            String w = parts.getStackInSlot(3).getItem() instanceof MissilePartItem p ? (p.variant().isEmpty() ? "he" : p.variant()) : "";
            String mv = parts.getStackInSlot(1).getItem() instanceof MissilePartItem m ? m.variant() : "";
            return !mv.equals("divert") && !mv.equals("hypersonic") && w.equals(k == AirMissiles.AGM || k == AIR_ASHM ? "he" : "frag");
        }
        if (MissileBuild.SEEK_RADAR.equals(SeekerModuleItem.kindOf(seekerSlot.getStackInSlot(0)))) return false;
        if (mode == MODE_CRUISE) {
            String w = parts.getStackInSlot(3).getItem() instanceof MissilePartItem p ? p.variant() : "he";
            return a >= 2 && a <= 3 && jetIn() && !w.equals("interceptor") && !w.startsWith(Satellites.PREFIX) && !w.equals(Asat.ID) && !w.equals(Cubesat.ID);
        }
        if (mode == MODE_SLBM) {
            String w = parts.getStackInSlot(3).getItem() instanceof MissilePartItem p ? p.variant() : "he";
            String mv = parts.getStackInSlot(1).getItem() instanceof MissilePartItem m ? m.variant() : "";
            return a == 3 && partTier(1) == 3 && !jetIn() && !mv.equals("divert") && !mv.equals("hypersonic") && !(parts.getStackInSlot(3).getItem() instanceof MirvBusItem) && groundWarhead(w);
        }
        if (jetIn()) return false;
        return switch (mode) { case 1, 2 -> a >= 4 && partTier(1) >= 4; case 3 -> a >= 2 && a <= 3; default -> a <= 3; };
    }
    public boolean storing() { return storing; }
    public int storeSlot() { return storeSlot; }
    public int storeRack() { return storeRack; }

    public int rackSide() { return kind == BayKind.RACK_R ? 1 : 0; }

    public int rackIndex() { return BayLine.rackIndex(this); }

    public net.neoforged.neoforge.items.IItemHandler rackHandler() { return racked; }
    public net.minecraft.world.item.ItemStack rackStack(int i) { return racked.getStackInSlot(i); }
    public int rackTop() { return top; }
    public int rackCount() { int n = 0; for (int i = 0; i < RACK_SLOTS; i++) if (!racked.getStackInSlot(i).isEmpty()) n++; return n; }

    public int rackFree() {
        int best = -1, bestD = 99;
        for (int i = 0; i < RACK_SLOTS; i++) {
            if (!racked.getStackInSlot(i).isEmpty()) continue;
            int d = Math.min(Math.floorMod(i - top, RACK_SLOTS), Math.floorMod(top - i, RACK_SLOTS));
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    boolean rackPut(int slot, ItemStack s) {
        if (!kind.isRack() || slot < 0 || slot >= RACK_SLOTS || !racked.getStackInSlot(slot).isEmpty()) return false;
        racked.setStackInSlot(slot, s);
        top = slot;
        setChanged(); sync();
        return true;
    }

    public ItemStack takeMissile() {
        for (int i = 0; i < RACK_SLOTS; i++) {
            ItemStack s = racked.getStackInSlot(i);
            if (!s.isEmpty()) { racked.setStackInSlot(i, ItemStack.EMPTY); return s; }
        }
        return ItemStack.EMPTY;
    }

    public int calibre() {
        if (airRun() || (mode == MODE_AIR && stage == Stage.IDLE)) return 1;
        if (stage != Stage.IDLE || (flags & FLAG_OUT) != 0) return build.calibre();
        return BayCalibre.clamp(partTier(0));
    }

    public boolean tooSmall() { return BayCalibre.clamp(partTier(0)) > sizeLevel; }

    public int runwayShort() {
        int cal = calibre();
        if (cal <= 1) return 0;
        double nose = BayAnim.maxNose(neededStations(), cal);
        double have = BayLine.lengthBlocks(this) * 16.0;
        double miss = nose - BayAnim.RUNWAY_GRACE - have;
        return miss <= 0 ? 0 : (int) Math.ceil(miss / (BayKind.EXTEND.length * 16.0));
    }

    public boolean upgradeSize() {
        if (kind != BayKind.CORE || sizeLevel >= BayCalibre.MAX) return false;
        sizeLevel++;
        setChanged();
        sync();
        return true;
    }

    public float progress(Level level) {
        if (stage == Stage.IDLE) return 0.0F;
        float in = Mth.clamp((level.getGameTime() - stageStart) / (float) Math.max(1, stageLen), 0.0F, 1.0F) * stageLen;
        int sub = (runMode == 1 || runMode == 3) ? Balance.BAY_WARHEAD_TICKS : 0;
        int before = stage.before(stations, storing) - (stage.ordinal() > Stage.WARHEAD.ordinal() ? sub : 0);
        if (runMode == MODE_AIR) in /= (float) Balance.BAY_AIR_TIME;
        return Mth.clamp((before + in) / (float) (Balance.bayRunTicks(stations, storing) - sub), 0.0F, 1.0F);
    }

    public BayAnim.Pose pose(float partialTick) {
        BayModuleBlockEntity core = kind == BayKind.CORE ? this : BayLine.coreOf(this);
        if (core == null || level == null) return new BayAnim.Pose();
        double t = level.getGameTime() + partialTick - core.stageStart;
        BayAnim.Pose p = BayAnim.compute(core.stage, t, core.stageLen, core.stations, core.calibre(), core.storing, core.storeSlot, core.storeFrom, core.rackSt);
        if (core.airRun() && core.runAir >= 0) BayAnim.airPose(p, core.stage, core.stageLen <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, t / core.stageLen)), airArt(core.runAir), core.stations);
        if (core.stage == Stage.IDLE && (core.flags & FLAG_OUT) != 0) { p.attached = requiredMask(core.runMode); p.cradleLoaded = true; }
        else if (core.stage != Stage.IDLE) p.attached &= requiredMask(core.runMode);
        return p;
    }

    public BayAnim.Pose rackPose(float partialTick) {
        BayAnim.Pose p = pose(partialTick);
        BayModuleBlockEntity c = core();
        if (c == null || !(c.stage == Stage.STORE && c.storing && c.storeRack == rackIndex())) {
            p.wpos = top;
            p.rbz = BayAnim.REST_BZ; p.rtx = BayAnim.REST_TX; p.rdy = BayAnim.REST_DY; p.rjaw = 0.0;
            p.rcarry = false; p.rplaced = false; p.wmoving = false;
        }
        return p;
    }

    public BayModuleBlockEntity core() { return kind == BayKind.CORE ? this : BayLine.coreOf(this); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BayModuleBlockEntity be) {
        if (be.kind == BayKind.CORE) Logistics.noteBay(be);
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        if (be.kind == BayKind.CORE) be.tickCore(level);
    }

    private boolean partsReady() {
        int need = requiredMask(mode);
        for (int i = 0; i < 4; i++) if ((need >> i & 1) != 0 && parts.getStackInSlot(i).isEmpty()) return false;
        return mode != MODE_AIR || !seekerSlot.getStackInSlot(0).isEmpty();
    }

    public int maxTier() {
        int m = 1;
        int need = requiredMask(mode);
        for (int i = 0; i < 4; i++) if ((need >> i & 1) != 0 && parts.getStackInSlot(i).getItem() instanceof MissilePartItem p) m = Math.max(m, p.tier());
        return m;
    }

    public int airframeGrade() { return partTier(0); }

    private int partTier(int slot) { return parts.getStackInSlot(slot).getItem() instanceof MissilePartItem p ? p.tier() : 1; }

    private int maxPartTier() {
        int m = 0, need = requiredMask(mode);
        for (int i = 0; i < 4; i++) if ((need >> i & 1) != 0 && !parts.getStackInSlot(i).isEmpty()) m = Math.max(m, partTier(i));
        return m;
    }

    public boolean gradesOk() {
        int a = partTier(0);
        int need = requiredMask(mode);
        for (int i = 1; i < 4; i++) if ((need >> i & 1) != 0 && partTier(i) > a) return false;
        return true;
    }

    public MissileBuild buildFromParts() {
        String type = parts.getStackInSlot(3).getItem() instanceof MirvBusItem ? MirvBusItem.type(parts.getStackInSlot(3))
                : parts.getStackInSlot(3).getItem() instanceof MissilePartItem w && !w.variant().isEmpty() ? w.variant() : "he";
        boolean divert = parts.getStackInSlot(1).getItem() instanceof MissilePartItem m && m.variant().equals("divert");
        boolean hyper = parts.getStackInSlot(1).getItem() instanceof MissilePartItem mh && mh.variant().equals("hypersonic");
        boolean refined = parts.getStackInSlot(3).getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0;
        boolean air = mode == MODE_AIR;
        boolean uplink = !air && mode != MODE_CRUISE && !uplinkSlot.getStackInSlot(0).isEmpty() && (requiredMask(mode) & 4) != 0 && !type.equals("interceptor") && !type.startsWith(Satellites.PREFIX);
        boolean cam = !air && (!camSlot.getStackInSlot(0).isEmpty() || isCamPod(seekerSlot.getStackInSlot(0))) && (requiredMask(mode) & 4) != 0 && !type.equals("interceptor") && !type.startsWith(Satellites.PREFIX);
        String seeker = (requiredMask(mode) & 4) != 0 && !type.equals("interceptor") && !type.startsWith(Satellites.PREFIX) ? SeekerModuleItem.kindOf(seekerSlot.getStackInSlot(0)) : "";
        return new MissileBuild(partTier(0), partTier(1), partTier(2), partTier(3), type, uplink, divert, refined, false, 0, kindOf(mode), cam, seeker, hyper);
    }

    public int neededStations() { return maxTier() - 1; }

    public int needKfe() { return Balance.bayRunFe(neededStations()) / 1000; }

    private int computeStock() {
        int m = pending;
        for (int i = 0; i < 4; i++) if (!parts.getStackInSlot(i).isEmpty()) m |= 1 << i;
        return m;
    }

    private void tickCore(Level level) {
        long now = level.getGameTime();
        if (stage != Stage.IDLE && stageStart > now) { stageStart = now; setChanged(); sync(); }
        if (stage == Stage.IDLE) {
            if (now % 10 == 0) {
                boolean linked = BayLine.complete(this);
                int need = neededStations();
                boolean missing = linked && partsReady() && BayLine.stations(this) < need;
                boolean grade = linked && partsReady() && !gradesOk();
                boolean modeBad = linked && partsReady() && !grade && !modeOk();
                grade = grade || modeBad;
                boolean size = linked && partsReady() && !grade && tooSmall();
                boolean runway = linked && partsReady() && !grade && !size && !missing && runwayShort() > 0;
                boolean ready = linked && partsReady() && !grade && !size && !missing && !runway && out.getStackInSlot(0).isEmpty() && store.getEnergyStored() >= Balance.bayRunFe(need);
                flags = (linked ? FLAG_LINKED : 0) | (ready ? FLAG_READY : 0) | (missing ? FLAG_NEEDS : 0) | (grade ? FLAG_GRADE : 0) | (size ? FLAG_SIZE : 0) | (runway ? FLAG_RUNWAY : 0) | (modeBad ? FLAG_MODE : 0) | (flags & FLAG_OUT);
                if (ready) start(level, now);
            }
            flags = out.getStackInSlot(0).isEmpty() ? flags & ~FLAG_OUT : flags | FLAG_OUT;
        } else {

            switch (stage) {
                case FETCH -> take(0, 0.31, now);
                case CRANE -> take(1, 0.31, now);
                case WARHEAD -> { take(2, 0.2156, now); take(UPLINK_BIT, 0.2156, now); take(CAM_BIT, 0.2156, now); take(SEEKER_BIT, 0.2156, now); take(3, 0.6456, now); }
                default -> {}
            }
            if (now >= stageStart + stageLen) advance(level, now);
        }
        int stock = computeStock() | (partTier(0) << 8);
        if (stock != syncedStock || flags != syncedFlags) {
            syncedStock = stock; syncedFlags = flags; stockMask = stock;
            sync();
        }
    }

    private void take(int slot, double fraction, long now) {
        if ((pending >> slot & 1) != 0 && now >= stageStart + (long) (stageLen * fraction)) {
            if (slot == CAM_BIT) {
                ItemStackHandler from = isCamPod(seekerSlot.getStackInSlot(0)) ? seekerSlot : camSlot;
                if (from.extractItem(0, 1, false).isEmpty()) { build = build.withCamPod(false); sync(); }
            }
            else if (slot == SEEKER_BIT) {
                if (seekerSlot.extractItem(0, 1, false).isEmpty()) { build = build.withSeeker(""); sync(); }
            }
            else if (slot != UPLINK_BIT) parts.extractItem(slot, 1, false);
            else if (uplinkSlot.extractItem(0, 1, false).isEmpty()) {
                build = build.withUplink(false);
                sync();
            }
            pending &= ~(1 << slot);
        }
    }

    private void start(Level level, long now) {
        stations = neededStations();
        runMode = mode;
        runAir = mode == MODE_AIR ? airKind() : -1;
        build = buildFromParts();
        outTier = build.calibre();
        store.use(Balance.bayRunFe(stations));

        BayModuleBlockEntity bestRack = null;
        int bestSlot = -1, bestSide = 0, bestD = Integer.MAX_VALUE;
        for (int side = 0; side < BayLine.RACKS; side++) {
            BayModuleBlockEntity r = BayLine.rackOf(this, side);
            if (r == null) continue;
            int slot = r.rackFree();
            if (slot < 0) continue;
            int d = Math.min(Math.floorMod(slot - r.rackTop(), RACK_SLOTS), Math.floorMod(r.rackTop() - slot, RACK_SLOTS));
            if (d < bestD) { bestD = d; bestRack = r; bestSlot = slot; bestSide = side; }
        }
        storeSlot = bestSlot;
        storing = storeSlot >= 0;
        if (build.warheadType().equals("interceptor") || runMode == MODE_AIR) { storing = false; storeSlot = -1; }
        stored = false;
        storeRack = bestSide;
        storeFrom = storing ? bestRack.rackTop() : 0;
        rackSt = BayLine.stations(this) + 3 * (storeRack / 2);
        pending = requiredMask(runMode) | (build.uplink() ? 1 << UPLINK_BIT : 0) | (build.camPod() ? 1 << CAM_BIT : 0) | (build.seeker().isEmpty() ? 0 : 1 << SEEKER_BIT);
        enter(Stage.FETCH, Stage.FETCH.len(stations), now);
    }

    private int timed(int len) { return runMode == MODE_AIR && len > 0 ? Math.max(1, (int) Math.round(len * Balance.BAY_AIR_TIME)) : len; }

    private void enter(Stage s, int len, long now) {
        if (s != Stage.IDLE) len = timed(len);
        stage = s; stageLen = len; stageStart = now;
        setChanged();
        sync();
    }

    private void advance(Level level, long now) {
        if (stage == Stage.RETURN) {
            boolean keep = stored;
            if (!keep) out.setStackInSlot(0, makeMissile());
            storing = false; stored = false; storeSlot = -1;
            enter(Stage.IDLE, 0, now);
            flags = keep ? 0 : FLAG_OUT; syncedFlags = -1;
        } else if (stage == Stage.ROLL_STORE) {
            enter(Stage.STORE, Stage.STORE.len(stations, true), now);
        } else if (stage == Stage.STORE) {
            BayModuleBlockEntity rack = BayLine.rackOf(this, storeRack);
            stored = rack != null && rack.rackPut(storeSlot, makeMissile());
            if (!stored) storing = false;
            enter(Stage.RETURN, storing ? rollTicks(BayAnim.handoverZ(rackSt) - BayAnim.dockZ(outTier)) : Stage.RETURN.len(stations, false), now);
        } else {
            Stage next = Stage.values()[stage.ordinal() + 1];
            if (next == Stage.WARHEAD && (runMode == 1 || runMode == 3)) next = Stage.values()[next.ordinal() + 1];
            while (next.station >= stations) next = Stage.values()[next.ordinal() + 1];
            if (next == Stage.RETURN && storing) { enter(Stage.ROLL_STORE, rollTicks(BayAnim.handoverZ(rackSt) - BayAnim.endZ(stations, outTier)), now); return; }
            enter(next, next.len(stations, storing), now);
        }
    }

    private static int rollTicks(double px) { return Math.max(60, (int) Math.ceil(px / 1.25)); }

    private ItemStack makeMissile() {
        if (runMode == MODE_AIR && runAir == AIR_ASHM) {
            if (!AirGate.AVAILABLE) return ItemStack.EMPTY;
            ItemStack am = new ItemStack(AntiShipItems.ASHM.get(), Balance.ASHM_BAY_YIELD);
            if (build.refined()) am.set(WmdComponents.WARHEAD_GRADE.get(), 1);
            return am;
        }
        if (runMode == MODE_AIR) {
            net.minecraft.world.item.Item it = AamItem.of(runAir);
            if (it == null) return ItemStack.EMPTY;
            ItemStack aam = new ItemStack(it, Balance.BAY_AIR_YIELD);
            if (build.refined()) aam.set(WmdComponents.WARHEAD_GRADE.get(), 1);
            return aam;
        }
        if (build.warheadType().equals("interceptor"))
        {
            ItemStack made = new ItemStack(outTier <= 1 ? Wmd.INTERCEPTOR_T1.get() : outTier == 2 ? Wmd.INTERCEPTOR_T2.get() : Wmd.INTERCEPTOR_T3.get());
            if (build.refined()) made.set(WmdComponents.WARHEAD_GRADE.get(), 1);
            return made;
        }
        if (runMode == MODE_CRUISE) {
            ItemStack cm = new ItemStack(Wmd.CRUISE_MISSILE.get());
            cm.set(WmdComponents.MISSILE_BUILD.get(), build);
            return cm;
        }
        if (runMode == MODE_SLBM) {
            ItemStack sb = new ItemStack(Wmd.SLBM.get());
            sb.set(WmdComponents.MISSILE_BUILD.get(), build);
            return sb;
        }
        if (runMode != 0) {
            ItemStack st = new ItemStack(runMode == 1 ? Wmd.STAGE_CORE.get() : runMode == 2 ? Wmd.STAGE_UPPER.get() : Wmd.STAGE_BOOSTER.get());
            st.set(WmdComponents.MISSILE_BUILD.get(), build);
            return st;
        }
        ItemStack made = new ItemStack(outTier <= 1 ? Wmd.MISSILE_T1.get() : outTier == 2 ? Wmd.MISSILE_T2.get() : Wmd.MISSILE_T3.get());
        made.set(WmdComponents.MISSILE_BUILD.get(), build);
        return made;
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void dropContents(Level level, BlockPos pos) {
        if (kind.isRack()) {
            for (int i = 0; i < RACK_SLOTS; i++) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, racked.getStackInSlot(i));
            return;
        }
        if (kind != BayKind.CORE) return;
        for (int i = 0; i < 4; i++) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, parts.getStackInSlot(i));
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, uplinkSlot.getStackInSlot(0));
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, camSlot.getStackInSlot(0));
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, seekerSlot.getStackInSlot(0));
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out.getStackInSlot(0));
    }

    protected Direction facing() { return getBlockState().getValue(MachineBlock.FACING); }

    private boolean at(BlockPos part, Direction side, int x, int y, int z, Direction localSide) {
        Direction f = facing();
        BlockPos l = Machines.toLocal(worldPosition, f, Machines.mount(getBlockState()), part);
        return l.getX() == x && l.getY() == y && l.getZ() == z && side == Machines.dirToWorld(f, Machines.mount(getBlockState()), localSide);
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {

        return kind == BayKind.CORE && at(part, side, 4, 0, 7, Direction.EAST) ? store : null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        Direction f = facing();
        BlockPos l = Machines.toLocal(worldPosition, f, Machines.mount(getBlockState()), part);

        if (l.getX() == -4 && l.getY() == 0 && side == Machines.dirToWorld(f, Machines.mount(getBlockState()), Direction.WEST)) {
            BayModuleBlockEntity core = core();
            if (core == null) return null;
            return switch (kind) {
                case CORE -> new InsertOnlyHandler(new RangedWrapper(core.parts, 0, 1));
                case CRANE -> new InsertOnlyHandler(new RangedWrapper(core.parts, 1, 2));
                case WARHEAD -> new InsertOnlyHandler(new net.neoforged.neoforge.items.wrapper.CombinedInvWrapper(new RangedWrapper(core.parts, 2, 4), core.uplinkSlot, core.camSlot, core.seekerSlot));
                default -> null;
            };
        }
        return null;
    }

    public Component statusText() {
        if (stage != Stage.IDLE) return Component.translatable("gui.wmd.bay.stage." + stage.name().toLowerCase());
        if ((flags & FLAG_LINKED) == 0) return Component.translatable("gui.wmd.bay.not_linked");
        if (!partsReady()) return Component.translatable("gui.wmd.bay.need_parts");
        if (mode == MODE_AIR && airKind() < 0) return Component.translatable("gui.wmd.bay.air_seeker");
        if (mode != MODE_AIR && MissileBuild.SEEK_RADAR.equals(SeekerModuleItem.kindOf(seekerSlot.getStackInSlot(0)))) return Component.translatable("gui.wmd.bay.radar_seeker_air");
        if (mode != MODE_CRUISE && jetIn() && !(mode == MODE_AIR && airKind() == AIR_ASHM)) return Component.translatable("gui.wmd.bay.jet_needs_cruise");
        if (mode == 0 && maxPartTier() >= 4) return Component.translatable("gui.wmd.bay.t4_is_stages");
        if (!gradesOk()) return Component.translatable("gui.wmd.bay.grade_too_high");
        if (!modeOk()) return Component.translatable("gui.wmd.bay.mode_airframe." + mode);
        if (tooSmall()) return Component.translatable("gui.wmd.bay.size_too_small", BayCalibre.clamp(partTier(0)), sizeLevel);
        if (!out.getStackInSlot(0).isEmpty()) return Component.translatable("gui.wmd.bay.output_full");
        if (BayLine.stations(this) < neededStations()) return Component.translatable("gui.wmd.bay.need_station", Component.translatable("block.wmd.bay_" + BayKind.values()[BayKind.WARHEAD.ordinal() + neededStations()].name().toLowerCase()));
        if (runwayShort() > 0) return Component.translatable("gui.wmd.bay.need_runway", runwayShort());
        if (store.getEnergyStored() < Balance.bayRunFe(neededStations())) return Component.translatable("gui.wmd.bay.need_power");
        return Component.translatable("gui.wmd.bay.stage.idle");
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BayModuleBlockEntity be) {
        BayModuleBlockEntity core = be.kind == BayKind.CORE ? be : BayLine.coreOf(be);
        if (core == null) return;
        BayAnim.Pose p = be.kind.isRack() ? be.rackPose(0.0F) : be.pose(0.0F);
        long now = level.getGameTime();
        var rnd = level.random;
        double[] c = be.worldPoint(0, 1.0, 0);
        if (be.kind == BayKind.CORE) {

            if (core.stage != be.seenStage) {
                if (be.seenStage == Stage.IDLE && core.stage == Stage.FETCH) level.playLocalSound(c[0], c[1], c[2], WmdSounds.BAY_START.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F, false);
                be.seenStage = core.stage;
            }
            if (p.held != be.seenHeld) {
                if (p.held >= 0) level.playLocalSound(c[0], c[1], c[2], WmdSounds.BAY_GRAB.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F, false);
                else if (be.seenHeld >= 0) { if (!BayAudio.seat(level, c[0], c[1], c[2])) level.playLocalSound(c[0], c[1], c[2], SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4F, 1.7F, false); be.seatedAt = now; }
                be.seenHeld = p.held;
            }

            double moved = Math.abs(p.cradleZ - be.seenZ);
            be.seenZ = p.cradleZ;
            if (moved > 0.05 && moved < 40.0 && now % 8 == 0) {
                double[] r = be.worldPoint(0, 0.6, p.cradleZ / 16.0);
                if (!BayAudio.rail(be, level, now, r[0], r[1], r[2])) level.playLocalSound(r[0], r[1], r[2], SoundEvents.MINECART_INSIDE, SoundSource.BLOCKS, 0.16F, 0.55F + rnd.nextFloat() * 0.1F, false);
            }
        }
        if (be.kind == BayKind.WELD && p.weld > 0.05) {
            double[] w = be.worldPoint(0, BayArt.AXY / 16.0, BayArt.ZO / 16.0);

            final double lift = BayCalibre.lift(p.cal), dx = lift;
            double a = p.ring, r = (BayArt.RING_R + dx - 24.0) / 16.0;
            double ox = -Math.sin(a) * r, oy = Math.cos(a) * r;
            double[][] tips = { { ox, (47.38 + lift) / 16.0 + oy, BayArt.ZO / 16.0 }, { 0.0, (47.38 + lift + 9.3 * BayCalibre.rad(p.cal)) / 16.0, BayArt.ZH / 16.0 }, { -0.94 - dx / 16.0, 2.2, BayArt.ZF / 16.0 }, { 0.94 + dx / 16.0, 2.2, BayArt.ZF / 16.0 } };
            for (int i = 0; i < tips.length; i++) {
                double[] t = be.worldPoint(tips[i][0], tips[i][1], tips[i][2]);
                if (rnd.nextFloat() < 0.8F * p.weld) level.addParticle(ParticleTypes.ELECTRIC_SPARK, t[0], t[1], t[2], (rnd.nextDouble() - 0.5) * 0.3, rnd.nextDouble() * 0.2, (rnd.nextDouble() - 0.5) * 0.3);
                if (rnd.nextFloat() < 0.15F * p.weld) level.addParticle(ParticleTypes.LAVA, t[0], t[1], t[2], 0, 0, 0);
                float flick = 0.7F + rnd.nextFloat() * 0.6F;
                VeilBridge.machineGlow(0x7E1D_0000_0000_0000L ^ (be.worldPosition.asLong() * 8 + i), t[0], t[1], t[2], 0.62F, 0.8F, 1.0F, 9.0F, 3.4F * (float) p.weld * flick, 0.5F, now);
                if (rnd.nextFloat() < 0.04F) level.addParticle(ParticleTypes.SMOKE, t[0], t[1], t[2], 0, 0.04, 0);
            }
            if (!BayAudio.weld(be, level, now, w[0], w[1], w[2]) && now % 3 == 0) level.playLocalSound(w[0], w[1], w[2], SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.25F, 1.7F + rnd.nextFloat() * 0.4F, false);
        }
        if (be.kind == BayKind.CRANE && p.tqSpin > 0) {

            double[] t = be.worldPoint((10.5 + BayAnim.toolOut(p.cal)) / 16.0, 47.38 / 16.0, BayArtCrane.TQ_Z / 16.0);
            if (rnd.nextFloat() < 0.5F) level.addParticle(ParticleTypes.ELECTRIC_SPARK, t[0], t[1], t[2], (rnd.nextDouble() - 0.5) * 0.2, rnd.nextDouble() * 0.15, (rnd.nextDouble() - 0.5) * 0.2);
            if (!BayAudio.bolt(be, level, now, t[0], t[1], t[2]) && now % 3 == 0) level.playLocalSound(t[0], t[1], t[2], SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.2F, 1.9F + rnd.nextFloat() * 0.2F, false);
        }
        if (be.kind == BayKind.CRANE && p.held == 1 && now % 9 == 0) {
            double[] h = be.worldPoint(p.trolX / 16.0, 5.0, p.craneZ / 16.0);
            if (!BayAudio.rail(be, level, now, h[0], h[1], h[2])) level.playLocalSound(h[0], h[1], h[2], SoundEvents.MINECART_INSIDE, SoundSource.BLOCKS, 0.25F, 0.5F, false);
        }
        if (be.kind == BayKind.CLEAN && core.stage == Stage.CLEAN) {
            if (p.hood > 0.02 && p.hood < 0.98 && now % 40 == 0) level.playLocalSound(c[0], c[1] + 3, c[2] + 7, WmdSounds.BAY_HOOD.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F, false);
            if (p.hatch > 0.02 && p.hatch < 0.98 && now % 30 == 0) level.playLocalSound(c[0] + 3, c[1] + 2, c[2] + 6, WmdSounds.BAY_HATCH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F, false);
            if (p.scan >= 0) {

                double zz = -40 / 16.0 + 80 / 16.0 * p.scan + 60 / 16.0 ;
                for (int i = 0; i < 2; i++) {
                    double[] t = be.worldPoint((rnd.nextDouble() - 0.5) * 3.2, 1.0 + rnd.nextDouble() * 3.4, zz);
                    level.addParticle(ParticleTypes.END_ROD, t[0], t[1], t[2], 0, 0.002, 0);
                }
                double[] g = be.worldPoint(0, 3.4, zz);
                VeilBridge.machineGlow(0x7E1D_0000_0000_1000L ^ be.worldPosition.asLong(), g[0], g[1], g[2], 0.4F, 0.7F, 1.0F, 9.0F, 1.6F, 0.5F, now);
                if (!BayAudio.scan(be, level, now, g[0], g[1], g[2]) && now % 8 == 0) level.playLocalSound(g[0], g[1], g[2], SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.2F, 1.8F, false);
            }
        }
        if (be.kind == BayKind.PAINT) {
            if (p.spray > 0.05) {

                float[][] cols = { { 0.36F, 0.41F, 0.22F }, { 0.46F, 0.49F, 0.52F }, { 0.12F, 0.13F, 0.14F } };
                for (int i = 0; i < 3; i++) {
                    double ang = Math.PI / 4 * rnd.nextInt(8) + p.ringP * (i == 0 ? 1.0 : i == 1 ? -1.2 : 0.8);
                    double[] n = be.worldPoint(Math.sin(ang) * 1.25, (54.0 + Math.cos(ang) * 20.0) / 16.0, (30.0 + 22.0 * i) / 16.0);
                    double[] m = be.worldPoint(Math.sin(ang) * 0.7, (54.0 + Math.cos(ang) * 11.0) / 16.0, (30.0 + 22.0 * i) / 16.0);
                    if (rnd.nextFloat() < 0.9F * p.spray)
                        level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(cols[i][0], cols[i][1], cols[i][2]), 1.2F), n[0], n[1], n[2], (m[0] - n[0]) * 0.15, (m[1] - n[1]) * 0.15, (m[2] - n[2]) * 0.15);
                }
                if (!BayAudio.paint(be, level, now, c[0], c[1] + 3, c[2] + 4) && now % 4 == 0) level.playLocalSound(c[0], c[1] + 3, c[2] + 4, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.12F, 1.9F, false);
            }
            if (p.ir) {
                double[] g = be.worldPoint(0, 4.3, 5.2);
                VeilBridge.machineGlow(0x7E1D_0000_0000_2000L ^ be.worldPosition.asLong(), g[0], g[1], g[2], 1.0F, 0.35F, 0.12F, 9.0F, 1.4F, 0.5F, now);
                if (!BayAudio.oven(be, () -> !be.isRemoved() && be.pose(0.0F).ir) && now % 10 == 0) level.playLocalSound(g[0], g[1], g[2], SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.3F, 0.8F, false);
            }
            if (p.arch > 0.02 && p.arch < 0.98 && now % 40 == 0) level.playLocalSound(c[0], c[1] + 3, c[2] + 5, WmdSounds.BAY_ARCH.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F, false);
        }
        if (be.kind == BayKind.CRYO && p.cryo > 0.02) {

            for (int i = 0; i < 4; i++) {
                double ang = Math.PI / 2 * i + Math.PI / 4 + p.lring;
                double[] t = be.worldPoint(Math.sin(ang) * 0.62, (54.0 + Math.cos(ang) * 10.0) / 16.0, 4.0);
                if (p.beams && rnd.nextFloat() < 0.7F) level.addParticle(ParticleTypes.ELECTRIC_SPARK, t[0], t[1], t[2], (rnd.nextDouble() - 0.5) * 0.2, rnd.nextDouble() * 0.15, (rnd.nextDouble() - 0.5) * 0.2);
                if (p.beams) VeilBridge.machineGlow(0x7E1D_0000_0000_3000L ^ (be.worldPosition.asLong() * 8 + i), t[0], t[1], t[2], 1.0F, 0.25F, 0.35F, 8.0F, 1.6F * (float) p.cryo, 0.5F, now);
            }
            if (now % 2 == 0) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    double[] v = be.worldPoint(sx * 1.9, 1.2, 4.0 + (rnd.nextDouble() - 0.5) * 2.4);
                    level.addParticle(ParticleTypes.CLOUD, v[0], v[1], v[2], sx * 0.02, 0.03 + rnd.nextDouble() * 0.02, 0);
                    if (rnd.nextFloat() < 0.3F) level.addParticle(ParticleTypes.SNOWFLAKE, v[0], v[1] + 0.3, v[2], 0, 0.02, 0);
                }
            }
            if (!BayAudio.cryo(be, level, now, c[0], c[1] + 2, c[2] + 4)) {
                if (now % 40 == 0) level.playLocalSound(c[0], c[1] + 3, c[2] + 4, WmdSounds.BAY_FROST.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F, false);

            }

        }
        if (be.kind == BayKind.WARHEAD) {
            double[] d = be.worldPoint(2.8, 2.0, 3.9);
            if (p.clamp > 0.02 && p.clamp < 0.98 && now % 16 == 0) level.playLocalSound(d[0], d[1], d[2], WmdSounds.BAY_CLAMP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F, false);
        }
        if (be.kind.isRack()) {

            final double sx = be.rackSide() == 1 ? -1.0 : 1.0;
            double[] g = be.worldPoint(sx * p.rtx / 16.0, (BayAnim.HOLD_Y + p.rdy) / 16.0, p.rbz / 16.0);
            int held = p.rcarry ? 5 : -1;
            if (held != be.seenHeld) {
                if (held >= 0) level.playLocalSound(g[0], g[1], g[2], WmdSounds.BAY_GRAB.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.9F, false);
                else if (be.seenHeld >= 0) { if (!BayAudio.seat(level, g[0], g[1], g[2])) level.playLocalSound(g[0], g[1], g[2], SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F, false); be.seatedAt = now; }
                be.seenHeld = held;
            }
            if (p.wmoving && now % 7 == 0) { double[] h = be.worldPoint(sx * 3.5, 4.9, 12.0); if (!BayAudio.rail(be, level, now, h[0], h[1], h[2])) level.playLocalSound(h[0], h[1], h[2], SoundEvents.MINECART_INSIDE, SoundSource.BLOCKS, 0.2F, 0.5F, false); }
            if (p.rjaw > 0.03 && p.rjaw < 0.97) {
                VeilBridge.machineGlow(0x7E1D_0000_0000_7000L ^ be.worldPosition.asLong(), g[0], g[1], g[2], 0.45F, 0.75F, 1.0F, 6.0F, 1.2F, 0.5F, now);
                if (now % 15 == 0) level.playLocalSound(g[0], g[1], g[2], WmdSounds.BAY_CLAMP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.15F, false);
            }
            long age = now - be.seatedAt;
            if (age >= 0 && age < 14) {
                float k = 1.0F - age / 14.0F;
                VeilBridge.machineGlow(0x7E1D_0000_0000_7100L ^ be.worldPosition.asLong(), g[0], g[1], g[2], 0.7F, 0.9F, 1.0F, 8.0F, 4.0F * k, 0.5F, now);
            }
        }

        if (be.kind == BayKind.CORE || be.kind == BayKind.CRANE || be.kind == BayKind.WARHEAD) {
            double[] g = gripPoint(be, p);
            if (be.kind != BayKind.CORE && p.held != be.seenHeld) {
                if (p.held >= 0) level.playLocalSound(g[0], g[1], g[2], WmdSounds.BAY_GRAB.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.05F, false);
                else if (be.seenHeld >= 0) { if (!BayAudio.seat(level, g[0], g[1], g[2])) level.playLocalSound(g[0], g[1], g[2], SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.45F, 1.6F, false); be.seatedAt = now; }
                be.seenHeld = p.held;
            }
            if (p.jaw > 0.03 && p.jaw < 0.97) {
                VeilBridge.machineGlow(0x7E1D_0000_0000_4000L ^ be.worldPosition.asLong(), g[0], g[1], g[2], 0.45F, 0.75F, 1.0F, 6.0F, 1.2F, 0.5F, now);
                if (rnd.nextFloat() < 0.5F) level.addParticle(ParticleTypes.ELECTRIC_SPARK, g[0] + (rnd.nextDouble() - 0.5) * 0.8, g[1] + (rnd.nextDouble() - 0.5) * 0.8, g[2], 0, 0.03, 0);
                if (now % 15 == 0) level.playLocalSound(g[0], g[1], g[2], WmdSounds.BAY_CLAMP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.15F, false);
            }
            long age = now - be.seatedAt;
            if (age >= 0 && age < 14) {
                float k = 1.0F - age / 14.0F;
                VeilBridge.machineGlow(0x7E1D_0000_0000_5000L ^ be.worldPosition.asLong(), g[0], g[1], g[2], 0.7F, 0.9F, 1.0F, 8.0F, 4.0F * k, 0.5F, now);
                if (age == 0) for (int i = 0; i < 18; i++) {
                    double a = Math.PI * 2 * i / 18;
                    level.addParticle(ParticleTypes.END_ROD, g[0], g[1], g[2], Math.cos(a) * 0.08, Math.sin(a) * 0.08, (rnd.nextDouble() - 0.5) * 0.04);
                }
            }

            if ((be.kind == BayKind.CRANE && core.stage == Stage.CRANE) || (be.kind == BayKind.WARHEAD && core.stage == Stage.WARHEAD)) {
                double[] bc = be.kind == BayKind.CRANE ? be.worldPoint(-60 / 16.0, 176 / 16.0, p.craneZ / 16.0) : be.worldPoint(0, 118 / 16.0, 98 / 16.0);
                VeilBridge.machineGlow(0x7E1D_0000_0000_6000L ^ be.worldPosition.asLong(), bc[0], bc[1], bc[2], 1.0F, 0.7F, 0.2F, 7.0F, 1.2F + 0.5F * (float) Math.sin(now * 0.3), 0.5F, now);
            }
        }
    }

    private static double[] gripPoint(BayModuleBlockEntity be, BayAnim.Pose p) {
        return switch (be.kind) {
            case CORE -> be.worldPoint((-28.0 + p.armX) / 16.0, (47.38 + BayAnim.LIFT - p.armDown) / 16.0, p.loaderZ / 16.0);
            case CRANE -> be.worldPoint(p.trolX / 16.0, (96.0 + p.hookDy) / 16.0, p.craneZ / 16.0);
            default -> be.worldPoint(p.wlx / 16.0, (64.0 - p.ext) / 16.0, p.wlz / 16.0);
        };
    }

    double[] worldPoint(double lx, double ly, double lz) {
        double dx, dz;
        switch (facing()) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        net.minecraft.world.phys.Vec3 p = new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5 + dx, worldPosition.getY() + ly, worldPosition.getZ() + 0.5 + dz);
        p = level != null && level.isClientSide ? ShipFrame.drawnWorld(level, worldPosition, p, Float.NaN) : SableBridge.toWorld(level, p);
        return new double[] { p.x, p.y, p.z };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        if (kind.isRack()) { tag.put("racked", racked.serializeNBT(registries)); tag.putInt("top", top); return; }
        if (kind != BayKind.CORE) return;
        tag.put("parts", parts.serializeNBT(registries));
        tag.put("out", out.serializeNBT(registries));
        tag.put("uplink", uplinkSlot.serializeNBT(registries));
        tag.put("campod", camSlot.serializeNBT(registries));
        tag.put("seeker", seekerSlot.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        tag.putInt("stage", stage.ordinal());
        tag.putInt("len", stageLen);
        tag.putLong("start", stageStart);
        tag.putInt("pending", pending);
        tag.putInt("flags", flags);
        tag.putInt("stations", stations);
        tag.putInt("outTier", outTier);
        tag.putInt("sizeLevel", sizeLevel);
        tag.putInt("mode", mode); tag.putInt("runMode", runMode); tag.putInt("runAir", runAir);
        tag.put("build", build.save());
        tag.putInt("stock", stockMask);
        tag.putBoolean("storing", storing);
        tag.putBoolean("stored", stored);
        tag.putInt("storeSlot", storeSlot);
        tag.putInt("storeFrom", storeFrom);
        tag.putInt("storeRack", storeRack);
        tag.putInt("rackSt", rackSt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (kind.isRack()) { if (tag.contains("racked")) racked.deserializeNBT(registries, tag.getCompound("racked")); top = Mth.clamp(tag.getInt("top"), 0, RACK_SLOTS - 1); return; }
        if (kind != BayKind.CORE) return;
        if (tag.contains("parts")) parts.deserializeNBT(registries, tag.getCompound("parts"));
        if (tag.contains("out")) out.deserializeNBT(registries, tag.getCompound("out"));
        if (tag.contains("uplink")) { CompoundTag ut = tag.getCompound("uplink").copy(); ut.putInt("Size", 1); uplinkSlot.deserializeNBT(registries, ut); }
        if (tag.contains("campod")) { CompoundTag ct = tag.getCompound("campod").copy(); ct.putInt("Size", 1); camSlot.deserializeNBT(registries, ct); }
        if (tag.contains("seeker")) { CompoundTag st = tag.getCompound("seeker").copy(); st.putInt("Size", 1); seekerSlot.deserializeNBT(registries, st); }
        store.set(tag.getInt("energy"));
        int s = tag.getInt("stage");
        stage = s >= 0 && s < Stage.values().length ? Stage.values()[s] : Stage.IDLE;
        stageLen = tag.getInt("len");
        stageStart = tag.getLong("start");
        pending = tag.getInt("pending");
        flags = tag.getInt("flags");
        stations = tag.getInt("stations");
        outTier = Math.max(1, tag.getInt("outTier"));
        sizeLevel = tag.contains("sizeLevel") ? Math.max(1, Math.min(BayCalibre.MAX, tag.getInt("sizeLevel"))) : 1;
        mode = Mth.clamp(tag.getInt("mode"), 0, MODES - 1); runMode = Mth.clamp(tag.getInt("runMode"), 0, MODES - 1); runAir = tag.contains("runAir") ? tag.getInt("runAir") : -1;
        build = MissileBuild.load(tag.getCompound("build"), outTier);
        stockMask = tag.getInt("stock");
        storing = tag.getBoolean("storing");
        stored = tag.getBoolean("stored");
        storeSlot = tag.contains("storeSlot") ? tag.getInt("storeSlot") : -1;
        storeFrom = tag.getInt("storeFrom");
        storeRack = Mth.clamp(tag.getInt("storeRack"), 0, BayLine.RACKS - 1);
        rackSt = tag.getInt("rackSt");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
