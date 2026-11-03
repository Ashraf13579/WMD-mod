package com.ashraf.wmd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LaunchTerminalBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, EmpVulnerable {
    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(net.minecraft.world.entity.player.Player player) { ownerData.claim(player); setChanged(); }

    public enum Stage { IDLE, LOADING, FUELLING, READY, COUNTDOWN, LAUNCHING, COOLDOWN }

    public static final int SLOT_CARD = 0, SLOT_MISSILE = 1, SLOT_FUEL = 2;

    public static final int SLOT_COUNTER = SLOT_FUEL;
    public static final int SLOT_CORE = 3, SLOT_UPPER = 4, SLOT_BOOST0 = 5, BOOSTERS = 2, SLOT_COUNT = 9;
    public static final int ENERGY_CAPACITY = 2_500_000, ENERGY_RECEIVE = 50_000;
    public static final int DATA_COUNT = 33;

    public static final int OK = 0, NOT_READY = 1, NO_TARGET = 2, NO_RANGE = 3, OUT_OF_RANGE = 4, OUT_OF_ARC = 5,
            TOO_CLOSE = 6, NEED_FUEL = 7, NO_POWER = 8, TOO_BIG = 9, NOT_FORMED = 10, NO_MISSILE = 11, DECOY_TOO_FAR = 12, NEED_STAGES = 13, NEED_BOOSTERS = 14,
            ORBIT_FULL = 15,
            NOT_LEVEL = 19,
            STATION_HAS_CORE = 20, STATION_NO_CORE = 21, STATION_PORT_TAKEN = 22,
            CARGO_NO_PORT = 23,
            STATION_LASER_TAKEN = 26,
            STATION_ROD_TAKEN = 28,
            STATION_MIRROR_TAKEN = 29,
            STATION_BAY_TAKEN = 30,
            STATION_NEED_HABITAT = 31;

    public static final Map<Long, double[]> CLIENT_LAUNCHES = Statics.client(new ConcurrentHashMap<>());

    private int targetX, targetY = 64, targetZ;
    private boolean targetSet;

    private int fakeX, fakeZ, launchFakeX, launchFakeZ;
    private boolean fakeSet, launchFakeSet;

    private Stage stage = Stage.IDLE;
    private int stageTicks;
    private int loadedTier;
    private MissileBuild loadedBuild = MissileBuild.preset(1);
    private ItemStack cargoPod = ItemStack.EMPTY;

    private final java.util.List<java.util.UUID> crewList = new java.util.ArrayList<>();
    private long boardT0 = -1, crewT0;
    private java.util.UUID boardWho;
    private boolean powered = true;
    private boolean fuelPaid;

    private boolean fuelAlky;

    private int refundMb;
    private boolean refundAlky;

    public boolean alkylateBoost() { return fuelPaid ? fuelAlky : Alkylate.is(jet.getFluid()); }
    private String launcherName = "";
    private java.util.UUID launcherId;
    private int launchX, launchY, launchZ;

    private int asatSat, launchAsat;

    private boolean airburst;

    private int mirvSpread;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_CARD -> stack.is(Wmd.NETWORK_CARD.get());
                case SLOT_MISSILE -> stack.getItem() instanceof LaunchMissileItem;
                case SLOT_COUNTER -> isCounter(stack);
                case SLOT_CORE -> stack.getItem() instanceof StageItem s && s.stageKind() == StageItem.CORE;
                case SLOT_UPPER -> (stack.getItem() instanceof StageItem s && s.stageKind() == StageItem.UPPER) || stack.getItem() instanceof PayloadUnitItem || stack.getItem() instanceof CrewCapsuleItem || stack.getItem() instanceof StationKitItem || stack.getItem() instanceof CargoPodItem;
                case SLOT_BOOST0, SLOT_BOOST0 + 1, SLOT_BOOST0 + 2, SLOT_BOOST0 + 3 -> stack.getItem() instanceof StageItem s && s.stageKind() == StageItem.BOOSTER;
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int slot) { return slot == SLOT_MISSILE || slot >= SLOT_CORE ? 1 : slot == SLOT_COUNTER ? 16 : 64; }

        @Override
        protected void onContentsChanged(int slot) { setChanged(); }
    };

    private final FluidTank jet = new FluidTank(Balance.TERMINAL_TANK_MB, fs -> fs.getFluid() == WmdFluids.JET_FUEL.source().get() || fs.getFluid() == WmdFluids.ALKYLATE.source().get());
    private final IFluidHandler jetInput = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return jet.getFluid(); }
        @Override public int getTankCapacity(int t) { return jet.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return jet.isFluidValid(s); }
        @Override public int fill(FluidStack s, FluidAction a) { int n = jet.fill(s, a); if (n > 0 && a.execute()) setChanged(); return n; }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int max, FluidAction a) { return FluidStack.EMPTY; }
    };

    private static class Store extends EnergyStorage {
        Store() { super(ENERGY_CAPACITY, ENERGY_RECEIVE, 0); }
        void drain(int n) { energy = Math.max(0, energy - n); }
        void set(int v) { energy = Mth.clamp(v, 0, capacity); }
    }

    private final Store energy = new Store();

    @Override
    public void empDrain() { energy.set(0); setChanged(); }
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public LaunchTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.LAUNCH_TERMINAL_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }

    private final net.neoforged.neoforge.items.IItemHandler automationPort = new InsertOnlyHandler(inventory);
    public net.neoforged.neoforge.items.IItemHandler automationPort() { return automationPort; }
    public IEnergyStorage getEnergy() { return energy; }
    public IFluidHandler getFuelInput() { return jetInput; }
    public int getJetMb() { return jet.getFluidAmount(); }
    public int getTargetX() { return targetX; }
    public int getTargetY() { return targetY; }
    public int getTargetZ() { return targetZ; }
    public Stage getStage() { return stage; }
    public int getStageTicks() { return stageTicks; }
    public int getLoadedTier() { return loadedTier; }

    public MissileBuild getLoadedBuild() { return loadedBuild; }

    public boolean hyperLaunch() { return loadedTier > 0 && loadedBuild.hypersonic() && getTier() < 4; }

    public int getTier() {
        return getBlockState().getBlock() instanceof LaunchTerminalBlock t ? t.getTier() : 1;
    }

    public static final double PAD4_MOUNT_Y = 3.75;

    public boolean isFormed() { return getBlockState().getValue(LaunchTerminalBlock.FORMED); }
    public Direction getFacing() { return getBlockState().getValue(LaunchTerminalBlock.FACING); }

    public Vec3 padCentre() {
        int n = PadStructure.size(getTier());
        Direction mount = Machines.mount(getBlockState());
        BlockPos c = PadStructure.cell(worldPosition, getFacing(), mount, getTier(), n / 2, n / 2);
        return Machines.mountPoint(c, mount, 0, getTier() >= 4 ? PAD4_MOUNT_Y : 1.0, 0);
    }

    public boolean padLevel() {
        if (level == null || !SableBridge.isOnShip(level, worldPosition)) return true;
        Direction m = Machines.mount(getBlockState());
        Vec3 c = padCentre(), w0 = SableBridge.toWorld(level, c), w1 = SableBridge.toWorld(level, c.add(m.getStepX(), m.getStepY(), m.getStepZ()));
        if (w0 == null || w1 == null) return true;
        Vec3 up = w1.subtract(w0);
        return up.lengthSqr() < 1.0E-6 || up.normalize().y >= Math.cos(Math.toRadians(Balance.PAD_TILT_DEG));
    }

    public Vec3 padCentreWorld() { return SableBridge.toWorld(level, padCentre()); }

    public void setTarget(int x, int y, int z) {
        targetX = Mth.clamp(x, -29_999_000, 29_999_000);
        targetY = Mth.clamp(y, -64, 320);
        targetZ = Mth.clamp(z, -29_999_000, 29_999_000);
        targetSet = true;
        setChanged();

        if (getTier() >= 3) {
            RadarBlockEntity radar = getLinkedRadar();
            if (radar != null && radar.isTargeting()) radar.aimAt(targetX, targetZ);
        }
    }

    public RadarBlockEntity getLinkedRadar() {
        RadarBlockEntity r = level == null ? null : NetworkCardItem.resolve(inventory.getStackInSlot(SLOT_CARD), level);
        return r != null && Access.trusts(r, getOwner()) ? r : null;
    }

    public int getRadarState() {
        RadarBlockEntity r = getLinkedRadar();
        return r == null ? 0 : (r.isTargeting() ? 2 : r.isOnline() ? 3 : 1);
    }

    public boolean isTargetInArc() {
        RadarBlockEntity r = getLinkedRadar();
        return r != null && r.isTargeting() && r.isInArc(targetX, targetZ);
    }

    public boolean navCovered() {
        return level instanceof ServerLevel sl && Uplink.active(sl.getServer(), getOwner());
    }

    public boolean uplinkCovered() {
        return level instanceof ServerLevel sl && Uplink.covers(sl.getServer(), shownBuild(), getOwner());
    }

    private int uplinkState() {
        MissileBuild b = shownBuild();
        int st = b != null && b.uplink() ? 1 : 0;
        if (level instanceof ServerLevel sl && Uplink.active(sl.getServer(), getOwner())) st |= 2;
        return st;
    }

    public boolean hasDivert() {
        if (loadedTier > 0) return loadedBuild.divert();
        ItemStack m = inventory.getStackInSlot(SLOT_MISSILE);
        return Missiles.tierOf(m) > 0 && MissileBuild.of(m).divert();
    }

    public int getFakeX() { return fakeX; }
    public int getFakeZ() { return fakeZ; }
    public boolean isFakeSet() { return fakeSet; }

    public void setFake(int x, int z) {
        fakeX = Mth.clamp(x, -29_999_000, 29_999_000);
        fakeZ = Mth.clamp(z, -29_999_000, 29_999_000);
        fakeSet = true;
        setChanged();
        sync();
    }

    public double decoyDistance() { return Math.hypot(fakeX - targetX, fakeZ - targetZ); }

    public int missileRange() {
        MissileBuild b = shownBuild();
        return b != null ? Alkylate.t4Range(b, MissileStats.range(b), alkylateBoost()) : PadStructure.maxRange(getTier());
    }

    public MissileBuild shownBuild() {
        if (loadedTier > 0) return loadedBuild;
        ItemStack m = inventory.getStackInSlot(SLOT_MISSILE);
        if (Missiles.tierOf(m) > 0) return MissileBuild.of(m);
        return getTier() >= 4 ? stackBuild() : null;
    }

    public int fuelNeed() {
        MissileBuild b = shownBuild();
        return MissileStats.fuel(b != null ? b : MissileBuild.preset(getTier()));
    }

    public int boostersIn() {
        int n = 0;
        for (int i = 0; i < BOOSTERS; i++) if (!inventory.getStackInSlot(SLOT_BOOST0 + i).isEmpty()) n++;
        return n;
    }

    public MissileBuild stackBuild() {
        ItemStack core = inventory.getStackInSlot(SLOT_CORE), upper = inventory.getStackInSlot(SLOT_UPPER);
        if (core.isEmpty() || upper.isEmpty()) return null;
        int n = 0;
        for (int i = 0; i < BOOSTERS; i++) if (!inventory.getStackInSlot(SLOT_BOOST0 + i).isEmpty()) n++;

        if (upper.getItem() instanceof PayloadUnitItem)
            return new MissileBuild(4, MissileBuild.of(core).motor(), 4, 4, Satellites.PREFIX + PayloadUnitItem.typeOf(upper), false, false, false, true, n);
        if (upper.getItem() instanceof CrewCapsuleItem)
            return new MissileBuild(4, MissileBuild.of(core).motor(), 4, 4, Crew.ID, false, false, false, true, n);
        if (upper.getItem() instanceof StationKitItem)
            return new MissileBuild(4, MissileBuild.of(core).motor(), 4, 4, StationKit.warhead(upper), false, false, false, true, n);
        if (upper.getItem() instanceof CargoPodItem)
            return new MissileBuild(4, MissileBuild.of(core).motor(), 4, 4, Cargo.ID, false, false, false, true, n);
        MissileBuild c = MissileBuild.of(core), u = MissileBuild.of(upper);
        return new MissileBuild(4, c.motor(), u.guidance(), u.warhead(), u.warheadType(), u.uplink(), false, u.refined(), true, n).withCamPod(u.camPod()).withSeeker(u.seeker()).withHypersonic(u.hypersonic());
    }

    private java.util.List<ItemStack> loadedStages() {
        java.util.List<ItemStack> l = new java.util.ArrayList<>();
        ItemStack core = new ItemStack(Wmd.STAGE_CORE.get());
        core.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(4, loadedBuild.motor(), 1, 1, "he", false, false, false));
        ItemStack upper = new ItemStack(Wmd.STAGE_UPPER.get());
        upper.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(4, loadedBuild.motor(), loadedBuild.guidance(), loadedBuild.warhead(), loadedBuild.warheadType(), loadedBuild.uplink(), false, loadedBuild.refined()).withCamPod(loadedBuild.camPod()).withSeeker(loadedBuild.seeker()).withHypersonic(loadedBuild.hypersonic()));
        if (Satellites.isSatellite(loadedBuild)) upper = PayloadUnitItem.of(Satellites.typeOf(loadedBuild));
        if (Crew.isCrew(loadedBuild)) upper = new ItemStack(Wmd.CREW_CAPSULE.get());
        if (StationKit.isKit(loadedBuild)) upper = StationKit.item(loadedBuild);
        if (Cargo.isCargo(loadedBuild)) { upper = cargoPod.isEmpty() ? new ItemStack(Wmd.CARGO_POD.get()) : cargoPod.copy(); cargoPod = ItemStack.EMPTY; }
        l.add(core); l.add(upper);
        for (int i = 0; i < loadedBuild.boosters(); i++) {
            ItemStack b = new ItemStack(Wmd.STAGE_BOOSTER.get());
            b.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(3, 3, 1, 1, "he", false, false, false));
            l.add(b);
        }
        return l;
    }

    public int getRange() {
        RadarBlockEntity r = getLinkedRadar();
        return (r != null && r.isTargeting()) || uplinkCovered() || navCovered() ? missileRange() : 0;
    }

    public int targetDistance() {
        Vec3 c = padCentreWorld();
        double dx = targetX + 0.5 - c.x, dz = targetZ + 0.5 - c.z;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    public int launchStatus() {
        if (stage != Stage.READY) return NOT_READY;
        if (!padLevel()) return NOT_LEVEL;

        if (Satellites.isSatellite(loadedBuild)) return orbitFull() ? ORBIT_FULL : OK;
        if (Crew.isCrew(loadedBuild)) return level instanceof ServerLevel bsl ? Boarding.status(bsl.getServer(), getOwner()) : OK;
        if (StationKit.isKit(loadedBuild)) return level == null ? OK : StationKit.status(level.getServer(), getOwner(), loadedBuild);
        if (Cargo.isCargo(loadedBuild)) return level == null ? OK : Cargo.status(level.getServer(), getOwner());
        if (Cubesat.isCubesat(loadedBuild)) return OK;

        if (Asat.isAsat(loadedBuild)) {
            if (!(level instanceof ServerLevel sl)) return OK;
            Vec3 pc = padCentreWorld();
            if (asatSat == 0 && Asat.stationPick(sl.getServer(), getOwner()) >= 0) return Asat.stationStatus(sl.getServer(), getOwner(), pc.x, pc.z);
            return Asat.status(sl.getServer(), getOwner(), asatSat, pc.x, pc.z);
        }
        if (!targetSet) return NO_TARGET;
        int range = getRange();
        if (range <= 0) return NO_RANGE;
        int dist = targetDistance();
        if (dist < Missiles.MIN_DISTANCE) return TOO_CLOSE;
        if (dist > range) return OUT_OF_RANGE;
        if (!isTargetInArc() && !uplinkCovered() && !navCovered()) return OUT_OF_ARC;
        if (hasDivert() && fakeSet && decoyDistance() > Flight.MAX_DECOY) return DECOY_TOO_FAR;
        return OK;
    }

    public int hint() {
        if (!isFormed()) return NOT_FORMED;
        return switch (stage) {
            case IDLE -> {
                int t = Missiles.tierOf(inventory.getStackInSlot(SLOT_MISSILE));
                if (t == 0 && getTier() >= 4) yield stackBuild() == null ? NEED_STAGES : boostersIn() < Balance.BOOSTER_MIN ? NEED_BOOSTERS : NO_POWER;
                yield t == 0 ? NO_MISSILE : (t > getTier() ? TOO_BIG : NO_POWER);
            }
            case LOADING -> powered ? OK : NO_POWER;
            case FUELLING -> !fuelPaid ? NEED_FUEL : (powered ? OK : NO_POWER);
            case READY -> launchStatus();
            default -> OK;
        };
    }

    public int asatSat() { return asatSat; }

    public boolean asatLoaded() { return Asat.isAsat(loadedTier > 0 ? loadedBuild : shownBuild()); }

    public Component pickAsat(java.util.UUID by, int satId) {
        if (by == null || !by.equals(getOwner())) return Component.translatable("message.wmd.asat.owner_only");
        if (!(level instanceof ServerLevel sl) || !Asat.pickable(sl.getServer(), by, satId)) return Component.translatable("message.wmd.asat.bad_target", satId);
        if (stage == Stage.COUNTDOWN || stage == Stage.LAUNCHING) return Component.translatable("message.wmd.asat.too_late");
        asatSat = satId;
        setChanged();
        sync();
        return Component.translatable("message.wmd.asat.picked", satId);
    }

    public int mirvSpread() { return mirvSpread > 0 ? mirvSpread : Balance.MIRV_SPREAD_DEFAULT; }

    public Component cycleMirv(java.util.UUID by) {
        if (by == null || !by.equals(getOwner())) return Component.translatable("message.wmd.asat.owner_only");
        if (stage == Stage.COUNTDOWN || stage == Stage.LAUNCHING) return Component.translatable("message.wmd.asat.too_late");
        int cur = mirvSpread(), next = Balance.MIRV_SPREADS[0];
        for (int i = 0; i < Balance.MIRV_SPREADS.length; i++) if (Balance.MIRV_SPREADS[i] == cur) next = Balance.MIRV_SPREADS[(i + 1) % Balance.MIRV_SPREADS.length];
        mirvSpread = next;
        setChanged();
        sync();
        return Component.translatable("message.wmd.mirv.spread", next);
    }

    public boolean airburst() { return airburst; }

    public Component toggleFuse(java.util.UUID by) {
        if (by == null || !by.equals(getOwner())) return Component.translatable("message.wmd.asat.owner_only");
        if (stage == Stage.COUNTDOWN || stage == Stage.LAUNCHING) return Component.translatable("message.wmd.asat.too_late");
        airburst = !airburst;
        setChanged();
        sync();
        return Component.translatable(airburst ? "message.wmd.fuse.air" : "message.wmd.fuse.ground");
    }

    private boolean orbitFull() {
        if (level instanceof ServerLevel sl) return OrbitData.full(sl.getServer());
        return OrbitView.count() >= Balance.SAT_MAX;
    }

    public Component tryLaunch(ServerPlayer player) {
        if (stage == Stage.READY && Crew.isCrew(loadedBuild) && getTier() >= 4) return Component.translatable("message.wmd.crew.lift.use_lift");
        return launchAs(player.getUUID(), player.getName().getString());
    }

    public long boardT0() { return boardT0; }
    public java.util.UUID boardWho() { return boardWho; }
    public long crewT0() { return crewT0; }
    public int crewCount() { return crewList.size(); }

    public double boardTime(float pt) { return boardT0 < 0 || level == null ? -1.0 : level.getGameTime() - boardT0 + pt; }

    public boolean crewRocket() {
        return loadedTier > 0 && Crew.isCrew(loadedBuild) && stage.ordinal() >= Stage.LOADING.ordinal() && stage.ordinal() <= Stage.LAUNCHING.ordinal();
    }

    public float crewArmAway(float pt) {
        if (!crewRocket()) return 1.0F;
        return switch (stage) {
            case LOADING -> 1.0F - smooth((progress(pt) - 0.88F) / 0.12F);
            case FUELLING, READY -> 0.0F;
            case COUNTDOWN -> smooth((stageTicks + pt - 10.0F) / 60.0F);
            default -> 1.0F;
        };
    }

    public int crewLaunchIn() {
        if (level == null) return -1;
        if (stage == Stage.COUNTDOWN) return (Missiles.COUNTDOWN_TICKS - stageTicks + 19) / 20;
        if (crewT0 <= 0) return -1;
        return (int) Math.max(0, (crewT0 + Balance.CREW_HOLD + Missiles.COUNTDOWN_TICKS - level.getGameTime() + 19) / 20);
    }

    public Component board(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        if (getTier() < 4 || !crewRocket()) return Component.translatable("message.wmd.crew.lift.no_capsule");
        if (!Access.mayUse(this, p)) return Component.translatable("message.wmd.crew.lift.not_yours", getOwnerName());
        if (stage != Stage.READY) return Component.translatable(stage == Stage.COUNTDOWN || stage == Stage.LAUNCHING ? "message.wmd.crew.lift.too_late" : "message.wmd.crew.lift.not_ready");
        if (crewList.contains(p.getUUID())) return Component.translatable("message.wmd.crew.lift.aboard");
        if (crewList.size() >= Balance.CREW_SEATS) return Component.translatable("message.wmd.crew.full");
        if (boardT0 >= 0 && now - boardT0 < PadCrew.BUSY) return Component.translatable("message.wmd.crew.lift.busy");
        if (crewT0 > 0 && now >= crewT0 + Balance.CREW_HOLD - PadCrew.ENTER) return Component.translatable("message.wmd.crew.lift.too_late");
        if (!EvaSuitItem.fullSuit(p)) return Component.translatable("message.wmd.crew.lift.suit");
        if (crewList.isEmpty()) {
            int st = launchStatus();
            if (st != OK) return Component.translatable("screen.wmd.terminal.hint." + st);
        }
        Vec3 o = PadCrew.origin(this);
        double r = PadCrew.rot(this);
        Vec3 in = PadCrew.arm(PadCrew.XW1 - 6.0, PadCrew.FLOOR, 0.0);
        Crew.embark(p, PadCrew.world(o, r, in.x, in.y, in.z), PadCrew.foot(this));
        crewList.add(p.getUUID());
        boardT0 = now;
        boardWho = p.getUUID();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new CutscenePayload(CutscenePayload.KIND_BOARD, (float) o.x, (float) o.y, (float) o.z,
                (float) r, 0.0F, 0.0F, 0, Balance.CREW_CUT_TICKS, ""));
        Wmd.LOGGER.info("[crew] {} boards the capsule at {} ({} aboard)", p.getName().getString(), worldPosition.toShortString(), crewList.size());
        setChanged();
        sync();
        return null;
    }

    private void crewTick(ServerLevel level) {
        if (crewList.isEmpty()) return;
        long now = level.getGameTime();
        if (crewT0 <= 0 && boardT0 >= 0 && now - boardT0 >= PadCrew.ENTER) {
            crewT0 = boardT0 + PadCrew.ENTER;
            for (java.util.UUID u : crewList) {
                ServerPlayer cp = level.getServer().getPlayerList().getPlayer(u);
                if (cp != null) cp.displayClientMessage(Component.translatable("message.wmd.crew.lift.clock", (Balance.CREW_HOLD + Missiles.COUNTDOWN_TICKS) / 20).withStyle(net.minecraft.ChatFormatting.AQUA), false);
            }
            setChanged();
            sync();
        }
        if (crewT0 > 0 && now >= crewT0 + Balance.CREW_HOLD && now - boardT0 >= PadCrew.ENTER) {
            java.util.UUID owner = getOwner() != null ? getOwner() : crewList.get(0);
            Component why = launchAs(owner, getOwnerName().isEmpty() ? "?" : getOwnerName());
            if (why != null) {
                for (java.util.UUID u : crewList) {
                    ServerPlayer cp = level.getServer().getPlayerList().getPlayer(u);
                    if (cp != null) cp.displayClientMessage(why.copy().withStyle(net.minecraft.ChatFormatting.YELLOW), false);
                }
                crewOff(level);
            }
        }
    }

    private void crewOff(Level level) {
        if (crewList.isEmpty() && boardT0 < 0) return;
        if (level instanceof ServerLevel sl) Crew.disembark(sl.getServer(), new java.util.ArrayList<>(crewList));
        crewList.clear();
        boardT0 = -1;
        boardWho = null;
        crewT0 = 0;
        setChanged();
        sync();
    }

    private Component launchAs(java.util.UUID id, String name) {
        int status = launchStatus();
        if (status == ORBIT_FULL && level instanceof ServerLevel sl) return Component.translatable("message.wmd.sat.orbit_full", OrbitData.get(sl.getServer()).count() + OrbitData.climbing(sl.getServer()), Balance.SAT_MAX);
        if (status != OK) return Component.translatable("screen.wmd.terminal.hint." + status);
        if (StationKit.isKit(loadedBuild) && level instanceof ServerLevel ksl) {
            int ks = StationKit.status(ksl.getServer(), id, loadedBuild);
            if (ks != OK) return Component.translatable("screen.wmd.terminal.hint." + ks);
        }
        if (Cargo.isCargo(loadedBuild) && level instanceof ServerLevel csl0) {
            int cs = Cargo.status(csl0.getServer(), id);
            if (cs != OK) return Component.translatable("screen.wmd.terminal.hint." + cs);
        }
        launcherName = name;
        launcherId = id;
        if (Satellites.toOrbit(loadedBuild)) {
            Vec3 c = padCentreWorld();
            launchX = (int) Math.floor(c.x) + Balance.SAT_DOWNRANGE;
            launchY = (int) Math.floor(c.y);
            launchZ = (int) Math.floor(c.z);
            launchFakeSet = false;
            launchAsat = 0;
            setStage(Stage.COUNTDOWN);
            return null;
        }
        if (Asat.isAsat(loadedBuild)) {
            Vec3 c = padCentreWorld();
            launchX = (int) Math.floor(c.x) + Balance.SAT_DOWNRANGE;
            launchY = (int) Math.floor(c.y);
            launchZ = (int) Math.floor(c.z);
            launchFakeSet = false;
            launchAsat = asatSat != 0 || !(level instanceof ServerLevel asl) ? asatSat : -(Asat.stationPick(asl.getServer(), getOwner()) + 1);
            setStage(Stage.COUNTDOWN);
            return null;
        }
        Component refused = Config.Server.refuse(loadedBuild);
        if (refused != null) return refused;
        if (level instanceof ServerLevel hs) {
            Component hyper = Hypersonic.refuse(hs, loadedBuild, id, targetX, targetZ);
            if (hyper != null) return hyper;
            Component off = OfflineGuard.refuse(hs, targetX + 0.5, targetZ + 0.5);
            if (off != null) return off;
        }
        launchX = targetX;
        launchY = targetY;
        launchZ = targetZ;
        launchFakeSet = fakeSet && hasDivert();
        launchFakeX = fakeX;
        launchFakeZ = fakeZ;
        setStage(Stage.COUNTDOWN);
        return null;
    }

    public boolean isTargetSet() { return targetSet; }

    private int remoteY(int x, int z) {
        if (level != null && level.hasChunkAt(new BlockPos(x, 0, z))) return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return targetSet ? targetY : level != null ? level.getSeaLevel() : 64;
    }

    public void remoteAim(int x, int z) {
        if (!(targetSet && targetX == x && targetZ == z)) {
            setTarget(x, remoteY(x, z), z);
            sync();
        }

        RadarBlockEntity radar = getLinkedRadar();
        if (radar != null && radar.isTargeting() && !radar.isInArc(x, z)) radar.aimAt(x, z);
    }

    public int radarTurnTicks(int x, int z) {
        RadarBlockEntity radar = getLinkedRadar();
        if (navCovered() || radar == null || !radar.isTargeting() || radar.isInArc(x, z)) return 0;
        float deg = Math.abs(RadarBlockEntity.delta(radar.bearingTo(x, z) - radar.getBearing())) - RadarBlockEntity.HALF_ARC;
        return (int) Math.ceil(Math.max(0.0F, deg) / RadarBlockEntity.DEGREES_PER_TICK);
    }

    public Component remoteLaunch(java.util.UUID id, String name, int x, int z) {
        if (stage == Stage.READY && Crew.isCrew(loadedBuild)) return Component.translatable("message.wmd.cc.launch.crew");
        if (stage == Stage.READY && Satellites.toOrbit(loadedBuild)) return Component.translatable("message.wmd.cc.launch.satellite");
        if (stage == Stage.READY && Asat.isAsat(loadedBuild)) return launchAs(id, name);
        remoteAim(x, z);
        if (launchStatus() == OUT_OF_ARC && radarTurnTicks(x, z) > 0)
            return Component.translatable("message.wmd.cc.launch.radar_turning", (radarTurnTicks(x, z) + 19) / 20);
        return launchAs(id, name);
    }

    public int predictTicks(int x, int z) {
        MissileBuild b = shownBuild();
        if (b == null || Satellites.toOrbit(b) || Asat.isAsat(b)) return -1;
        int tier = loadedTier > 0 ? loadedTier : Math.max(1, Math.min(getTier(), b.calibre()));
        Vec3 c = padCentreWorld();
        double d = Math.hypot(x + 0.5 - c.x, z + 0.5 - c.z);
        return Missiles.COUNTDOWN_TICKS + Missiles.launchTicks(b.hypersonic() && getTier() < 4) + Flight.totalTicks(tier, d, c.y + Flight.LAUNCH_HEIGHT, remoteY(x, z), MissileStats.speed(b), b.hypersonic() ? Balance.HYPER_APEX : 1.0);
    }

    public int stageLength(Stage s, int tier) {
        return switch (s) {
            case LOADING -> Missiles.loadTicks(tier);
            case FUELLING -> MissileStats.fuelTicks(loadedTier > 0 ? loadedBuild : MissileBuild.preset(Math.max(1, tier)), getTier() >= 4 && loadedTier > 0 && loadedTier < 4 ? loadedTier : getTier());
            case COUNTDOWN -> Missiles.COUNTDOWN_TICKS;
            case LAUNCHING -> Missiles.launchTicks(hyperLaunch());
            case COOLDOWN -> Missiles.COOLDOWN_TICKS;
            default -> 1;
        };
    }

    private void setStage(Stage s) {

        if (level instanceof ServerLevel sl && (s == Stage.COUNTDOWN || s == Stage.LAUNCHING || stage == Stage.COUNTDOWN || stage == Stage.LAUNCHING))
            ImpactChunks.launchHold(sl, worldPosition, s == Stage.COUNTDOWN || s == Stage.LAUNCHING);
        stage = s;
        stageTicks = 0;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaunchTerminalBlockEntity be) {
        CcLaunch.terminalSeen(be);
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        be.tickServer((ServerLevel) level);
    }

    private void tickServer(ServerLevel level) {
        if (refundMb > 0 && level.getGameTime() % 20 == 0) flushRefund();
        boolean formed = isFormed();
        switch (stage) {
            case IDLE -> {
                ItemStack m = inventory.getStackInSlot(SLOT_MISSILE);
                int t = Missiles.tierOf(m);
                if (formed && t > 0 && t <= getTier() && energy.getEnergyStored() >= Missiles.fePerTick(t)) {
                    loadedBuild = MissileBuild.of(m);
                    inventory.extractItem(SLOT_MISSILE, 1, false);
                    loadedTier = t;
                    fuelPaid = false;
                    powered = true;
                    setStage(Stage.LOADING);
                    sound(level, WmdSounds.LP_ERECT.get(), 1.5F, 1.0F);
                } else if (formed && t == 0 && getTier() >= 4 && stackBuild() != null && boostersIn() >= Balance.BOOSTER_MIN && energy.getEnergyStored() >= Missiles.fePerTick(4)) {
                    loadedBuild = stackBuild();
                    if (inventory.getStackInSlot(SLOT_UPPER).getItem() instanceof CargoPodItem) cargoPod = inventory.getStackInSlot(SLOT_UPPER).copy();
                    inventory.extractItem(SLOT_CORE, 1, false); inventory.extractItem(SLOT_UPPER, 1, false);
                    for (int i = 0; i < BOOSTERS; i++) inventory.extractItem(SLOT_BOOST0 + i, 1, false);
                    loadedTier = 4;
                    fuelPaid = false;
                    powered = true;
                    setStage(Stage.LOADING);
                    sound(level, WmdSounds.LP_ERECT.get(), 1.5F, 0.9F);
                }
            }
            case LOADING, FUELLING -> {
                if (!formed) { abort(level); return; }
                if (stage == Stage.FUELLING && !fuelPaid) {
                    int need = MissileStats.fuel(loadedBuild);
                    if (jet.getFluidAmount() >= need) {
                        fuelAlky = Alkylate.is(jet.getFluid());
                        jet.drain(need, IFluidHandler.FluidAction.EXECUTE);
                        fuelPaid = true;
                        sound(level, WmdSounds.LP_FUEL.get(), 1.5F, 1.0F);
                        sync();
                    } else {
                        if (powered) { powered = false; sync(); }
                        return;
                    }
                }
                int cost = Missiles.fePerTick(loadedTier);
                boolean ok = energy.getEnergyStored() >= cost;
                if (ok) { energy.drain(cost); stageTicks++; }
                if (ok != powered) { powered = ok; sync(); }
                if (stageTicks >= stageLength(stage, loadedTier)) {
                    if (stage == Stage.LOADING) {
                        setStage(Stage.FUELLING);
                        sound(level, WmdSounds.LP_LOCK.get(), 1.5F, 1.0F);
                    } else {
                        setStage(Stage.READY);
                        sound(level, WmdSounds.LP_VENT.get(), 1.2F, 1.0F);
                    }
                } else if (ok && stageTicks % 20 == 0) {
                    setChanged();
                    sync();
                }
            }
            case READY -> {
                if (!formed) { abort(level); return; }
                crewTick(level);
            }
            case COUNTDOWN -> {
                if (!formed) { abort(level); return; }
                if (stageTicks % 10 == 0 && !padLevel()) {
                    setStage(Stage.READY);
                    sound(level, WmdSounds.LP_HOLD.get(), 3.0F, 1.0F);
                    ServerPlayer who = launcherId == null ? null : level.getServer().getPlayerList().getPlayer(launcherId);
                    if (who != null) who.displayClientMessage(Component.translatable("message.wmd.pad_not_level_hold", (int) Balance.PAD_TILT_DEG), false);
                    setChanged();
                    sync();
                    return;
                }
                stageTicks++;
                int left = Missiles.COUNTDOWN_TICKS - stageTicks;
                if (getTier() >= 3 && stageTicks == 110) sound(level, WmdSounds.T4_ARMS.get(), 4.0F, 1.0F);
                if (crewRocket() && stageTicks == 10) sound(level, WmdSounds.T4_ARMS.get(), 4.0F, 0.9F);
                if (getTier() >= 4 && stageTicks == 150) sound(level, WmdSounds.T4_HOLDDOWN.get(), 5.0F, 1.0F);
                boolean crewCount = crewRocket() && getTier() >= 4;
                if (crewCount && left == 100) sound(level, WmdSounds.CREW_COUNT.get(), 3.0F, 1.0F);
                if (stageTicks % 20 == 0 && !(crewCount && left <= 100)) sound(level, WmdSounds.LP_COUNT.get(), 3.0F, left <= 60 ? 1.3F : 1.0F);
                if (stageTicks >= Missiles.COUNTDOWN_TICKS) {
                    setStage(Stage.LAUNCHING);
                    launchSound(level, LaunchSoundPayload.IGNITE);
                }
            }
            case LAUNCHING -> {
                stageTicks++;
                if (stageTicks == CLIMB_SOUND_AT) launchSound(level, LaunchSoundPayload.ASCENT);
                if (stageTicks <= Missiles.EXHAUST_TICKS && stageTicks % 5 == 0) exhaust(level);
                if (stageTicks >= Missiles.launchTicks(hyperLaunch())) {
                    if (getTier() >= 3) launchSound(level, LaunchSoundPayload.BOOM, Flight.LAUNCH_HEIGHT);
                    missileAway(level);
                    setStage(Stage.COOLDOWN);
                }
            }
            case COOLDOWN -> {
                stageTicks++;
                if (stageTicks >= Missiles.COOLDOWN_TICKS) {
                    loadedTier = 0;
                    setStage(Stage.IDLE);
                } else if (stageTicks % 100 == 0) {
                    setChanged();
                }
            }
        }
    }

    private void sound(ServerLevel level, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
        Vec3 c = padCentreWorld();
        level.playSound(null, c.x, c.y, c.z, s, SoundSource.BLOCKS, volume, pitch);
    }

    private static final int CLIMB_SOUND_AT = 50;

    private void launchSound(ServerLevel level, int base) { launchSound(level, base, 0.0); }

    private void launchSound(ServerLevel level, int base, double up) {
        Vec3 c = padCentreWorld();
        int kind = base + Math.max(1, Math.min(4, getTier()));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null, c.x, c.y + up, c.z, LaunchSoundPayload.range(kind), new LaunchSoundPayload(kind, c.x, c.y + up, c.z));
    }

    private void exhaust(ServerLevel level) {
        Vec3 c = padCentreWorld();
        double r = Missiles.EXHAUST_RADIUS;
        float fade = 1.0F - Math.max(0, stageTicks - 100) / 60.0F;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 8, r))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r) continue;
            double s = (1.0 - d / r) * fade;
            if (s <= 0) continue;
            e.hurt(level.damageSources().inFire(), (float) (2.0 + 8.0 * s));
            if (d < r * 0.7) e.igniteForSeconds(4.0F);
            double len = Math.max(0.3, d);
            e.push(dx / len * 0.9 * s, 0.25 * s, dz / len * 0.9 * s);
            e.hurtMarked = true;
        }
    }

    private void missileAway(ServerLevel level) {
        Vec3 c = padCentreWorld();
        String line = String.format("%s | %s | tier %d | pad %d %d %d | target %d %d | distance %d%n",
                Instant.now(), launcherName.isEmpty() ? "?" : launcherName, loadedTier,
                (int) Math.floor(c.x), (int) Math.floor(c.y), (int) Math.floor(c.z), launchX, launchZ,
                (int) Math.round(Math.hypot(launchX + 0.5 - c.x, launchZ + 0.5 - c.z)));
        try {
            Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("wmd_launch_log.txt");
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Wmd.LOGGER.warn("Could not write launch log", e);
        }
        Wmd.LOGGER.info("Missile launched: {}", line.trim());

        ItemStack counter = inventory.getStackInSlot(SLOT_COUNTER);
        if (!counter.isEmpty() && !Satellites.toOrbit(loadedBuild) && !Asat.isAsat(loadedBuild)) {
            FlightManager.get(level).nextCounter = counter.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flare_pack"))) ? 1 : 2;
            inventory.extractItem(SLOT_COUNTER, 1, false);
        }
        boolean asat = Asat.isAsat(loadedBuild);
        boolean cube = Cubesat.isCubesat(loadedBuild);
        boolean crew = Crew.isCrew(loadedBuild);
        boolean sat = Satellites.isSatellite(loadedBuild) || asat || cube || crew || StationKit.isKit(loadedBuild) || Cargo.isCargo(loadedBuild);
        boolean up = Uplink.covers(level.getServer(), loadedBuild, launcherId) && !CcJammer.jammedAt(level, launcherId, launchX + 0.5, launchZ + 0.5);
        double miss = Config.missRadius(loadedBuild.guidance());
        if (airburst && !sat && WarheadFx.airburstable(WarheadType.of(loadedBuild.warheadType()))) FlightManager.get(level).nextFlags = Flight.U_AIRBURST;
        if (loadedBuild.isMirv()) FlightManager.get(level).nextMirv = mirvSpread();
        long flightId = FlightManager.get(level).launch(level, loadedTier, new Vec3(c.x, c.y + 330.0, c.z), launchX, launchY, launchZ,
                launcherName.isEmpty() ? "?" : launcherName, launcherId, sat ? 0.0 : up ? Uplink.missRadius(miss) : miss, loadedBuild,
                launchFakeSet && !sat, launchFakeX, launchFakeZ);
        if (asat) { Asat.launched(level, flightId, launchAsat, launcherId, launcherName); return; }
        if (crew && level instanceof ServerLevel csl) {
            Crew.board(csl, flightId, c, launcherId, new java.util.ArrayList<>(crewList));
            crewList.clear(); boardT0 = -1; boardWho = null; crewT0 = 0;
            setChanged();
            return;
        }
        if (Cargo.isCargo(loadedBuild) && level instanceof ServerLevel cgl) {
            Cargo.launched(cgl, flightId, cargoPod, launcherId);
            cargoPod = ItemStack.EMPTY;
            setChanged();
            ServerPlayer cp = level.getServer().getPlayerList().getPlayerByName(launcherName);
            if (cp != null) cp.displayClientMessage(Component.translatable("screen.wmd.terminal.cargo_away"), false);
            return;
        }
        if (up && !sat) ShipDefence.assignLock(level, FlightManager.get(level), FlightManager.get(level).find(flightId), launcherId);
        ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(launcherName);
        if (p != null) {
            p.displayClientMessage(cube ? Component.translatable("screen.wmd.terminal.cubesat_away", Balance.CUBESAT_MINUTES)
                    : sat ? Component.translatable("screen.wmd.terminal.sat_away", OrbitData.typeName(Satellites.typeOf(loadedBuild)))
                    : Component.translatable("screen.wmd.terminal.away", launchX, launchZ), false);
        }
    }

    public static boolean isCounter(ItemStack s) { return s.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flare_pack"))) || s.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chaff_pod"))); }

    private ItemStack loadedStack() {
        ItemStack s = new ItemStack(Missiles.itemFor(loadedTier));
        s.set(WmdComponents.MISSILE_BUILD.get(), loadedBuild);
        return s;
    }

    private void refund(boolean alky, int mb) {
        if (mb <= 0) return;
        if (refundMb > 0 && refundAlky != alky) { flushRefund(); refundMb = 0; }
        refundAlky = alky;
        refundMb += mb;
        flushRefund();
    }

    private void flushRefund() {
        if (refundMb <= 0) return;
        FluidStack in = jet.getFluid();
        if (!in.isEmpty() && Alkylate.is(in) != refundAlky) return;
        int n = jet.fill(new FluidStack(refundAlky ? WmdFluids.ALKYLATE.source().get() : WmdFluids.JET_FUEL.source().get(), refundMb), IFluidHandler.FluidAction.EXECUTE);
        if (n > 0) { refundMb -= n; setChanged(); }
    }

    public int tankFluid() { return jet.isEmpty() ? 0 : Alkylate.is(jet.getFluid()) ? 2 : 1; }

    private void abort(Level level) {
        crewOff(level);
        if (loadedTier > 0) {
            if (loadedBuild.staged()) {
                for (ItemStack st : loadedStages()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), st);
            } else {
                ItemStack back = loadedStack();
                ItemStack rest = inventory.insertItem(SLOT_MISSILE, back, false);
                if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), rest);
            }
            if (fuelPaid) refund(fuelAlky, MissileStats.fuel(loadedBuild));
        }
        loadedTier = 0;
        fuelPaid = false;
        setStage(Stage.IDLE);
    }

    public void dropContents(Level level) {
        crewOff(level);
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), s.copy());
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        boolean onPad = loadedTier > 0 && stage.ordinal() >= Stage.LOADING.ordinal() && stage.ordinal() <= Stage.LAUNCHING.ordinal();
        if (onPad && loadedBuild.staged()) { for (ItemStack st : loadedStages()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), st); }
        else if (onPad) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), loadedStack());
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LaunchTerminalBlockEntity be) {
        be.tickClient(level);
    }

    private void tickClient(Level level) {
        boolean active = stage == Stage.LOADING || stage == Stage.FUELLING;
        if ((active && powered && (stage != Stage.FUELLING || fuelPaid)) || stage == Stage.COUNTDOWN
                || stage == Stage.LAUNCHING || stage == Stage.COOLDOWN) {
            stageTicks++;
        }
        if (stage == Stage.LAUNCHING && stageTicks < 220) {
            Vec3 c = padCentre();
            CLIENT_LAUNCHES.put(worldPosition.asLong(), new double[] {c.x, c.y, c.z, stageTicks});
        } else {
            CLIENT_LAUNCHES.remove(worldPosition.asLong());
        }
        if (isFormed()) particles(level);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_LAUNCHES.remove(worldPosition.asLong());
        if (level instanceof ServerLevel sl && (stage == Stage.COUNTDOWN || stage == Stage.LAUNCHING)) ImpactChunks.launchHold(sl, worldPosition, false);
    }

    public float progress(float partialTick) {
        return Mth.clamp((stageTicks + partialTick) / stageLength(stage, Math.max(1, loadedTier)), 0.0F, 1.0F);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    public float hatchOpen(float pt) {
        float p = progress(pt);
        return switch (stage) {
            case IDLE -> 0.0F;
            case LOADING -> smooth(p / 0.15F);
            case COOLDOWN -> 1.0F - smooth((p - 0.85F) / 0.15F);
            default -> 1.0F;
        };
    }

    public boolean showMissile() {
        return loadedTier > 0 && switch (stage) {
            case LOADING, FUELLING, READY, COUNTDOWN -> true;
            case LAUNCHING -> Missiles.ascent(stageTicks, hyperLaunch()) < 320.0F;
            default -> false;
        };
    }

    public float missileOffset(float pt) {
        int h = Missiles.height(Math.max(1, loadedTier));
        return switch (stage) {

            case LOADING -> getTier() >= 4 ? 0.0F : -Missiles.height(getTier()) * (1.0F - smooth((progress(pt) - 0.15F) / 0.7F));
            case LAUNCHING -> Missiles.ascent(stageTicks + pt, hyperLaunch());
            default -> 0.0F;
        };
    }

    public float liftOffset(float pt) {
        if (getTier() >= 4) return 0.0F;
        float bottom = -(Missiles.height(getTier()) + 0.5F);
        return switch (stage) {
            case LOADING, FUELLING, READY, COUNTDOWN -> Math.min(0.0F, missileOffset(pt));
            case LAUNCHING -> 0.0F;
            case COOLDOWN -> bottom * smooth(progress(pt) / 0.25F);
            default -> bottom;
        };
    }

    public float armsOpen(float pt) {
        float p = progress(pt);
        return switch (stage) {
            case LOADING -> 1.0F - smooth((p - 0.85F) / 0.15F);
            case FUELLING, READY -> 0.0F;
            case COUNTDOWN -> smooth((stageTicks + pt - 110.0F) / 70.0F);
            default -> 1.0F;
        };
    }

    public boolean hoseConnected() { return stage == Stage.FUELLING; }

    private static void puff(Level level, net.minecraft.core.particles.SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz) {
        PadFx.always(level, type, true, x, y, z, vx, vy, vz);
    }

    private void padLights(Level level, Vec3 c, double r, double h, double hatch, Direction in) {
        long t = level.getGameTime();
        long key = getBlockPos().asLong() * 31L;
        boolean lit = stage == Stage.FUELLING || stage == Stage.READY || stage == Stage.COUNTDOWN;
        if (lit) {

            PadFx.glow(key + 1, c.x + in.getStepX() * (r + 2.5), c.y + h * 0.45, c.z + in.getStepZ() * (r + 2.5),
                    0.85F, 0.92F, 1.0F, 11.0F, 1.1F, t);

            boolean cd = stage == Stage.COUNTDOWN;
            double a = t * (cd ? 0.55 : 0.3);
            double bx = c.x + in.getStepX() * (r + 1.6), bz = c.z + in.getStepZ() * (r + 1.6), by = c.y + h * 0.85;
            PadFx.glow(key + 2, bx + Math.cos(a) * 1.4, by, bz + Math.sin(a) * 1.4,
                    1.0F, cd ? 0.12F : 0.55F, cd ? 0.08F : 0.1F, 9.0F, cd ? 2.0F : 1.4F, 0.25F, t);
        }
        if (stage == Stage.LAUNCHING) {
            float up = Missiles.ascent(stageTicks, hyperLaunch());
            if (stageTicks == 1) PadFx.flash(c.x, c.y + 1.5, c.z, 1.0F, 0.9F, 0.7F, 34.0F, 5.0F, 0.5F, 24);
            if (up < 30) {
                float k = up < 8 ? 1.0F : 1.0F - (up - 8) / 22.0F;
                float flick = 0.85F + level.random.nextFloat() * 0.3F;
                PadFx.glow(key + 3, c.x, c.y + 0.8, c.z, 1.0F, 0.5F, 0.18F, 20.0F, 3.2F * k * flick, 0.6F, t);
            }
            if (up < 60) {
                PadFx.bloomNear(c.x, c.y + up, c.z, 300.0, 1.1F);
                PadFx.haze(c.x, c.y + up - 1.0, c.z, 6.0, 200.0, 1.0F);
            }
            if (stageTicks >= Missiles.IGNITION_TICKS - 6) {
                float flick = 0.85F + level.random.nextFloat() * 0.3F;
                PadFx.glow(key + 4, c.x, c.y + up - 1.2, c.z, 1.0F, 0.66F, 0.3F, 16.0F, 2.6F * flick, 0.4F, t);
            }
        }
    }

    private void particles(Level level) {
        if (loadedTier <= 0 && stage != Stage.COOLDOWN) return;
        Vec3 c = padCentre();
        PadFx.begin(this, c);
        try { particles(level, c); } finally { PadFx.end(); }
    }

    private void particles(Level level, Vec3 c) {
        RandomSource rnd = level.random;
        int tier = Math.max(1, loadedTier);
        double r = Missiles.radiusUnits(tier) / 16.0;
        double h = Missiles.height(tier);
        double hatch = PadGeometry.hatchHalf(getTier()) / 16.0;
        Direction in = getFacing().getOpposite();
        var BANK = WmdParticles.LAUNCH_SMOKE.get();
        var TRAIL = WmdParticles.TRAIL_SMOKE.get();
        var FLAME = WmdParticles.ROCKET_FLAME.get();
        var VAPOUR = WmdParticles.VAPOUR.get();
        if (tier == 1) padLights(level, c, r, h, hatch, in);
        if (getTier() >= 4 && loadedBuild.staged() && T4Launch.particles(level, this, c)) return;
        switch (stage) {
            case FUELLING -> {
                if (!fuelPaid) return;
                double hy = c.y + h * 0.42;
                for (int i = 0; i < ClientConfig.count(1.5F, rnd); i++) {
                    puff(level, VAPOUR, c.x + in.getStepX() * (r + 0.3) + (rnd.nextDouble() - 0.5) * 0.4, hy + rnd.nextDouble() * 0.4,
                            c.z + in.getStepZ() * (r + 0.3) + (rnd.nextDouble() - 0.5) * 0.4, (rnd.nextDouble() - 0.5) * 0.04, 0.01, (rnd.nextDouble() - 0.5) * 0.04);
                }
                if (tier == 3 && rnd.nextInt(2) == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    PadFx.add(level, ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * (r + 0.05), c.y + rnd.nextDouble() * h * 0.8,
                            c.z + Math.sin(a) * (r + 0.05), Math.cos(a) * 0.02, -0.01, Math.sin(a) * 0.02);
                }
            }
            case READY -> {
                for (int i = 0; i < ClientConfig.count(0.4F, rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    puff(level, VAPOUR, c.x + Math.cos(a) * r, c.y + h * (0.5 + rnd.nextDouble() * 0.25), c.z + Math.sin(a) * r,
                            Math.cos(a) * 0.03, 0.006, Math.sin(a) * 0.03);
                }
            }
            case COUNTDOWN -> {

                float build = Math.min(1.0F, stageTicks / (float) Missiles.COUNTDOWN_TICKS);
                for (int i = 0; i < ClientConfig.count(0.6F + 2.0F * build * build, rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double rr = r + rnd.nextDouble() * (hatch - r);
                    puff(level, VAPOUR, c.x + Math.cos(a) * rr, c.y + 0.1, c.z + Math.sin(a) * rr, Math.cos(a) * 0.06, 0.03, Math.sin(a) * 0.06);
                }
            }
            case LAUNCHING -> {
                float up = Missiles.ascent(stageTicks, hyperLaunch());
                float prevUp = Missiles.ascent(stageTicks - 1, hyperLaunch());
                double ny = c.y + up;
                if (stageTicks == 1) {
                    PadFx.always(level, ParticleTypes.FLASH, c.x, c.y + 1, c.z, 0, 0, 0);
                    for (int i = 0; i < 8; i++) PadFx.add(level, ParticleTypes.EXPLOSION, c.x + rnd.nextGaussian() * 1.5, c.y + 0.5, c.z + rnd.nextGaussian() * 1.5, 0, 0, 0);

                    for (int i = 0; i < ClientConfig.count(40, rnd); i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, s = 0.25 + rnd.nextDouble() * 0.6;
                        puff(level, BANK, c.x + Math.cos(a) * hatch * 0.6, c.y + 0.4, c.z + Math.sin(a) * hatch * 0.6, Math.cos(a) * s, 0.05 + rnd.nextDouble() * 0.15, Math.sin(a) * s);
                    }
                }

                for (int i = 0; i < ClientConfig.count(5, rnd) + 1; i++) {
                    puff(level, FLAME, c.x + rnd.nextGaussian() * r * 0.35, ny - 0.3 - rnd.nextDouble() * 0.8, c.z + rnd.nextGaussian() * r * 0.35,
                            rnd.nextGaussian() * 0.04, -0.55 - rnd.nextDouble() * 0.5, rnd.nextGaussian() * 0.04);
                }

                if (stageTicks >= Missiles.IGNITION_TICKS - 4 && up < 260) {
                    double from = Math.max(0.0, prevUp - 1.0), to = up;
                    double step = Math.max(0.9, (to - from) / Math.max(1, ClientConfig.count(26, rnd)));
                    for (double yy = from; yy <= to + 0.01; yy += step) {
                        puff(level, TRAIL, c.x + rnd.nextGaussian() * r * 0.6, c.y + yy - 1.0, c.z + rnd.nextGaussian() * r * 0.6,
                                rnd.nextGaussian() * 0.02, -0.02 - rnd.nextDouble() * 0.04, rnd.nextGaussian() * 0.02);
                    }
                }

                if (up < 30) {
                    float strength = up < 8 ? 1.0F : 1.0F - (up - 8) / 22.0F;
                    for (int i = 0; i < ClientConfig.count(9.0F * strength, rnd); i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, s = 0.3 + rnd.nextDouble() * 0.45;
                        puff(level, BANK, c.x + Math.cos(a) * hatch * 0.8, c.y + 0.3 + rnd.nextDouble() * 0.6, c.z + Math.sin(a) * hatch * 0.8,
                                Math.cos(a) * s, 0.01 + rnd.nextDouble() * 0.05, Math.sin(a) * s);
                    }
                    for (int i = 0; i < ClientConfig.count(2.0F * strength, rnd); i++) {
                        puff(level, BANK, c.x + rnd.nextGaussian() * hatch * 0.5, c.y + 0.5 + rnd.nextDouble() * Math.min(up, 6.0), c.z + rnd.nextGaussian() * hatch * 0.5,
                                rnd.nextGaussian() * 0.05, 0.08 + rnd.nextDouble() * 0.1, rnd.nextGaussian() * 0.05);
                    }
                }
            }
            case COOLDOWN -> {
                float left = 1.0F - progress(0);
                for (int i = 0; i < ClientConfig.count(left * 0.8F, rnd); i++) {
                    puff(level, TRAIL, c.x + (rnd.nextDouble() - 0.5) * hatch, c.y + 0.2, c.z + (rnd.nextDouble() - 0.5) * hatch,
                            (rnd.nextDouble() - 0.5) * 0.02, 0.05 + rnd.nextDouble() * 0.04, (rnd.nextDouble() - 0.5) * 0.02);
                }
                if (rnd.nextFloat() < left * 0.3F) {
                    PadFx.add(level, ParticleTypes.SMOKE, c.x + (rnd.nextDouble() - 0.5) * 3, c.y + 0.1, c.z + (rnd.nextDouble() - 0.5) * 3, 0, 0.02, 0);
                }
            }
            default -> {}
        }
    }

    private static int lo(int v) { return v & 0xFFFF; }
    private static int hi(int v) { return (v >>> 16) & 0xFFFF; }

    public ContainerData getContainerData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> isFormed() ? 1 : 0;
                    case 1 -> lo(targetX);
                    case 2 -> hi(targetX);
                    case 3 -> lo(targetY);
                    case 4 -> hi(targetY);
                    case 5 -> lo(targetZ);
                    case 6 -> hi(targetZ);
                    case 7 -> lo(getRange());
                    case 8 -> hi(getRange());
                    case 9 -> getRadarState();
                    case 10 -> { RadarBlockEntity r = getLinkedRadar(); yield r == null ? 0 : Math.round(r.getBearing() * 10.0F) % 3600; }
                    case 11 -> { RadarBlockEntity r = getLinkedRadar(); yield r == null ? 0 : Math.round(r.bearingTo(targetX, targetZ) * 10.0F) % 3600; }
                    case 12 -> isTargetInArc() ? 1 : 0;
                    case 13 -> stage.ordinal();
                    case 14 -> Math.round(progress(0) * 1000.0F);
                    case 15 -> loadedTier;
                    case 16 -> lo(energy.getEnergyStored());
                    case 17 -> hi(energy.getEnergyStored());
                    case 18 -> hint();
                    case 19 -> Math.max(0, (stageLength(stage, Math.max(1, loadedTier)) - stageTicks + 19) / 20);
                    case 20 -> jet.getFluidAmount();
                    case 21 -> hasDivert() ? 1 : 0;
                    case 22 -> lo(fakeX);
                    case 23 -> hi(fakeX);
                    case 24 -> lo(fakeZ);
                    case 25 -> hi(fakeZ);
                    case 26 -> fakeSet ? 1 : 0;
                    case 27 -> fuelNeed();
                    case 28 -> { MissileBuild b = shownBuild(); yield b == null ? 0 : (int) Math.round(MissileStats.weight(b) * 10.0); }
                    case 29 -> { MissileBuild b = shownBuild(); yield b == null ? 0 : (int) Math.round(MissileStats.speed(b) * 100.0); }
                    case 30 -> { MissileBuild b = shownBuild(); yield b == null ? 0 : MissileStats.health(b); }
                    case 31 -> uplinkState();
                    case 32 -> tankFluid();
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int value) {}

            @Override
            public int getCount() { return DATA_COUNT; }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        saveState(tag);
        tag.put("inv", inventory.serializeNBT(registries));
        tag.put("jet", jet.writeToNBT(registries, new CompoundTag()));
        tag.putInt("energy", energy.getEnergyStored());
        tag.putBoolean("targetSet", targetSet);
        tag.putString("launcher", launcherName);
        if (launcherId != null) tag.putUUID("launcherId", launcherId);
        tag.putInt("lx", launchX);
        tag.putInt("ly", launchY);
        tag.putInt("lz", launchZ);
    }

    private void saveState(CompoundTag tag) {
        tag.putInt("tx", targetX);
        tag.putInt("ty", targetY);
        tag.putInt("tz", targetZ);
        tag.putInt("stage", stage.ordinal());
        tag.putInt("stageTicks", stageTicks);
        tag.putInt("loadedTier", loadedTier);
        tag.put("loadedBuild", loadedBuild.save());
        if (!cargoPod.isEmpty() && level != null) tag.put("cargoPod", cargoPod.save(level.registryAccess()));
        tag.putBoolean("powered", powered);
        tag.putInt("fakeX", fakeX); tag.putInt("fakeZ", fakeZ); tag.putBoolean("fakeSet", fakeSet);
        tag.putInt("lfx", launchFakeX); tag.putInt("lfz", launchFakeZ); tag.putBoolean("lfs", launchFakeSet);
        tag.putBoolean("fuelPaid", fuelPaid);
        tag.putBoolean("fuelAlky", fuelAlky);
        tag.putInt("refundMb", refundMb);
        tag.putBoolean("refundAlky", refundAlky);
        tag.putInt("asat", asatSat);
        tag.putBoolean("fuse", airburst);
        tag.putInt("mirv", mirvSpread);
        tag.putInt("lasat", launchAsat);
        net.minecraft.nbt.ListTag cl = new net.minecraft.nbt.ListTag();
        for (java.util.UUID u : crewList) cl.add(net.minecraft.nbt.NbtUtils.createUUID(u));
        tag.put("crew", cl);
        tag.putLong("bt0", boardT0);
        tag.putLong("ct0", crewT0);
        if (boardWho != null) tag.putUUID("bwho", boardWho);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        targetX = tag.getInt("tx");
        targetY = tag.contains("ty") ? tag.getInt("ty") : 64;
        targetZ = tag.getInt("tz");
        int s = tag.getInt("stage");
        stage = s >= 0 && s < Stage.values().length ? Stage.values()[s] : Stage.IDLE;
        stageTicks = tag.getInt("stageTicks");
        loadedTier = tag.getInt("loadedTier");
        loadedBuild = MissileBuild.load(tag.getCompound("loadedBuild"), Math.max(1, loadedTier));
        cargoPod = tag.contains("cargoPod") ? ItemStack.parseOptional(registries, tag.getCompound("cargoPod")) : ItemStack.EMPTY;
        powered = !tag.contains("powered") || tag.getBoolean("powered");
        fakeX = tag.getInt("fakeX"); fakeZ = tag.getInt("fakeZ"); fakeSet = tag.getBoolean("fakeSet");
        launchFakeX = tag.getInt("lfx"); launchFakeZ = tag.getInt("lfz"); launchFakeSet = tag.getBoolean("lfs");
        fuelPaid = tag.getBoolean("fuelPaid");
        fuelAlky = tag.getBoolean("fuelAlky");
        refundMb = Math.max(0, tag.getInt("refundMb"));
        refundAlky = tag.getBoolean("refundAlky");
        asatSat = tag.getInt("asat");
        airburst = tag.getBoolean("fuse");
        mirvSpread = tag.getInt("mirv");
        launchAsat = tag.getInt("lasat");
        crewList.clear();
        for (net.minecraft.nbt.Tag t : tag.getList("crew", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) crewList.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        boardT0 = tag.contains("bt0") ? tag.getLong("bt0") : -1;
        crewT0 = tag.getLong("ct0");
        boardWho = tag.hasUUID("bwho") ? tag.getUUID("bwho") : null;
        if (tag.contains("inv")) {
            net.minecraft.nbt.CompoundTag invTag = tag.getCompound("inv").copy();
            invTag.putInt("Size", SLOT_COUNT);
            inventory.deserializeNBT(registries, invTag);
        } else if (tag.contains("card")) {
            ItemStackHandler old = new ItemStackHandler(1);
            old.deserializeNBT(registries, tag.getCompound("card"));
            inventory.setStackInSlot(SLOT_CARD, old.getStackInSlot(0));
        }
        if (tag.contains("jet")) jet.readFromNBT(registries, tag.getCompound("jet"));
        if (tag.contains("energy")) energy.set(tag.getInt("energy"));
        if (tag.contains("targetSet")) targetSet = tag.getBoolean("targetSet");
        if (tag.contains("launcher")) launcherName = tag.getString("launcher");
        launcherId = tag.hasUUID("launcherId") ? tag.getUUID("launcherId") : null;
        launchX = tag.getInt("lx");
        launchY = tag.contains("ly") ? tag.getInt("ly") : 64;
        launchZ = tag.getInt("lz");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveState(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
