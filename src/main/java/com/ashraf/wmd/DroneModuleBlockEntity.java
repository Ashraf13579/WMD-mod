package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DroneModuleBlockEntity extends BlockEntity implements BigDroneSite, GeoBlockEntity, MachinePorts, EmpVulnerable {
    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public enum Stage { IDLE, LOAD, TO_FRAME, FRAME, TO_AVIONICS, AVIONICS, TO_PROPULSION, PROPULSION, TO_TEST, TEST, TO_STORE, STORE, TO_HOME }

    public static final int SHELVES = Balance.DRONE_SHELVES, BAYS = 2;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.DRONE_BUFFER_FE, Balance.DRONE_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final DroneKind kind;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public final ItemStackHandler inputs = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final Buffer store = new Buffer();
    private Stage stage = Stage.IDLE;
    private int stageLen, type, storeSlot;
    private long stageStart;
    private boolean auto;

    public final ItemStackHandler racked = new ItemStackHandler(SHELVES) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { DroneType t = DroneType.of(s); return t != null && !t.big; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    public final ItemStackHandler parked = new ItemStackHandler(BAYS) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { DroneType t = DroneType.of(s); return t != null && t.big; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    public static final int PYLONS = 4, DOOR_TICKS = 40;
    public final ItemStackHandler pylons = new ItemStackHandler(PYLONS) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { BombType t = BombType.of(s); return t != null && t != BombType.TSAR && (!t.heavy || slot == 1 || slot == 2); }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    private DroneMission mission = DroneMission.NONE;
    private int out;
    private long doorAt = -1000, doorUntil = -1000;
    private boolean runway;

    public DroneModuleBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.DRONE_BE.get(), pos, state);
        this.kind = ((DroneModuleBlock) state.getBlock()).kind;
    }

    @Override public void onLoad() { super.onLoad(); if (kind == DroneKind.HANGAR && level != null && !level.isClientSide) BigDrones.SITES.add(this); }
    @Override public void setRemoved() { BigDrones.SITES.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { BigDrones.SITES.remove(this); super.onChunkUnloaded(); }

    private void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public DroneKind kind() { return kind; }
    public Stage stage() { return stage; }
    public int typeIndex() { return type; }
    public DroneType type() { return DroneType.of(type); }
    public int storeSlot() { return storeSlot; }
    public int energy() { return store.getEnergyStored(); }
    public IEnergyStorage energyStore() { return store; }
    public boolean auto() { return auto; }

    @Override public void empDrain() { store.set(0); setChanged(); }

    public float progress(Level level) {
        if (stage == Stage.IDLE || stageLen <= 0) return 0.0F;
        return Mth.clamp((level.getGameTime() - stageStart) / (float) stageLen, 0.0F, 1.0F);
    }

    public float runProgress(Level level) {
        if (stage == Stage.IDLE) return 0.0F;
        int done = 0, all = 0;
        for (Stage s : Stage.values()) {
            if (s == Stage.IDLE) continue;
            int l = lenOf(s);
            all += l;
            if (s.ordinal() < stage.ordinal()) done += l;
        }
        return all <= 0 ? 0.0F : Mth.clamp((done + progress(level) * stageLen) / all, 0.0F, 1.0F);
    }

    public DroneAnim.Pose pose(float partialTick) {
        double t = level == null ? 0 : level.getGameTime() + partialTick - stageStart;
        return DroneAnim.compute(stage, Math.max(0.0, t), stageLen, type(), storeSlot);
    }

    public ItemStack shelf(int i) { return racked.getStackInSlot(Mth.clamp(i, 0, SHELVES - 1)); }
    public ItemStack bay(int i) { return parked.getStackInSlot(Mth.clamp(i, 0, BAYS - 1)); }

    @Override public BlockPos sitePos() { return worldPosition; }
    @Override public boolean isHangar() { return true; }
    @Override public ItemStackHandler droneSlots() { return parked; }
    @Override public ItemStackHandler bombSlots() { return pylons; }
    @Override public DroneMission mission() { return mission; }
    @Override public void setMission(DroneMission m) { mission = m == null ? DroneMission.NONE : m; changedAndSync(); }
    @Override public int outId() { return out; }
    public boolean runway() { return runway; }

    @Override
    public int siteStatus() {
        if (out != 0) return S_OUT;
        if (DroneType.of(bay(0)) != DroneType.T4) return DroneType.of(bay(0)) == DroneType.T3 || DroneType.of(bay(1)) == DroneType.T3 ? S_FLIGHT_T3 : S_EMPTY;
        if (!mission.hasTarget()) return S_NO_TARGET;
        if (!runway) return S_NO_RUNWAY;
        return S_READY;
    }

    @Override
    public void act(int action, int[] data, net.minecraft.server.level.ServerPlayer by) {
        if (kind != DroneKind.HANGAR) return;
        switch (action) {
            case A_LAUNCH -> {
                int st = siteStatus();
                if (st != S_READY) { by.displayClientMessage(Component.translatable("gui.wmd.big_drone.st." + st), true); return; }
                if (level instanceof net.minecraft.server.level.ServerLevel sl && EmpField.jammed(sl, worldPosition)) { by.displayClientMessage(Component.translatable("message.wmd.hive.jammed"), true); return; }
                int[] id = new int[1];
                Component why = BigDrones.launchRunway((net.minecraft.server.level.ServerLevel) level, this, by, id);
                if (why != null) { by.displayClientMessage(why, true); return; }
                out = id[0];
                parked.setStackInSlot(0, ItemStack.EMPTY);
                for (int i = 0; i < PYLONS; i++) pylons.setStackInSlot(i, ItemStack.EMPTY);
                changedAndSync();
                by.displayClientMessage(Component.translatable("message.wmd.big_drone.launched", out), true);
            }
            case A_RECALL -> { if (out != 0) { BigDrones.recall(by.server, out); by.displayClientMessage(Component.translatable("message.wmd.hive.recalled", out), true); } }
            case A_WATCH -> {
                if (out == 0) { by.displayClientMessage(Component.translatable("message.wmd.big_drone.not_flying"), true); return; }
                Component why = BigDrones.watch(by, out);
                if (why != null) by.displayClientMessage(why, true); else by.closeContainer();
            }
            case A_DESIGNATED -> {
                CcTarget t = CcTarget.of(getOwner());
                if (t == null) { by.displayClientMessage(Component.translatable("message.wmd.cruise.no_designated"), true); return; }
                setMission(mission.withTarget(t.x(), t.z()));
            }
            case A_ROUTE -> {
                DroneMission m = DroneMission.of(data);
                if (m != null) setMission(m);
            }
            default -> {}
        }
    }

    void openDoor(int ticks) {
        long now = level.getGameTime();
        if (now > doorUntil - DOOR_TICKS) doorAt = now - (long) (doorOpen(0.0F) * DOOR_TICKS);
        doorUntil = Math.max(doorUntil, now + ticks);
        changedAndSync();
    }

    public float doorOpen(float pt) {
        if (level == null) return 0.0F;
        double t = level.getGameTime() + pt;
        if (t < doorAt || t > doorUntil) return 0.0F;
        double f = Math.min(Math.min((t - doorAt) / DOOR_TICKS, (doorUntil - t) / DOOR_TICKS), 1.0);
        f = Mth.clamp(f, 0.0, 1.0);
        return (float) (f * f * (3.0 - 2.0 * f));
    }

    boolean dockT4(int droneId, int[] left) {
        if (freeBay(this, DroneType.T4) != 0) return false;
        if (out == droneId) out = 0;
        parked.setStackInSlot(0, DroneType.T4.stack());
        returnBombs(left);
        openDoor(DOOR_TICKS * 2 + 20);
        changedAndSync();
        return true;
    }

    private DroneModuleBlockEntity storage() { return DroneLine.module(this, DroneKind.STORAGE); }
    private DroneModuleBlockEntity hangar() { return DroneLine.module(this, DroneKind.HANGAR); }

    public int lenOf(Stage s) {
        DroneType t = type();
        int storeT = t.big ? Balance.DRONE_HANGAR_TICKS : Balance.DRONE_STORE_TICKS, toStore = t.big ? Balance.DRONE_TRAVEL_TICKS * 3 / 2 : Balance.DRONE_TRAVEL_TICKS;
        int home = t.big ? Balance.DRONE_HOME_TICKS * 6 / 5 : Balance.DRONE_HOME_TICKS;
        int fixed = Balance.DRONE_LOAD_TICKS + 4 * Balance.DRONE_TRAVEL_TICKS + toStore + storeT + home;
        int stations = Math.max(80, t.runTicks() - fixed);
        return switch (s) {
            case IDLE -> 0;
            case LOAD -> Balance.DRONE_LOAD_TICKS;
            case TO_FRAME, TO_AVIONICS, TO_PROPULSION, TO_TEST -> Balance.DRONE_TRAVEL_TICKS;
            case FRAME -> (int) (stations * Balance.DRONE_STATION_SHARE[0]);
            case AVIONICS -> (int) (stations * Balance.DRONE_STATION_SHARE[1]);
            case PROPULSION -> (int) (stations * Balance.DRONE_STATION_SHARE[2]);
            case TEST -> (int) (stations * Balance.DRONE_STATION_SHARE[3]);
            case TO_STORE -> toStore;
            case STORE -> storeT;
            case TO_HOME -> home;
        };
    }

    public void cycleType(boolean back) {
        if (stage != Stage.IDLE) return;
        type = Math.floorMod(type + (back ? -1 : 1), DroneType.values().length);
        sync();
    }

    public void toggleAuto() { auto = !auto; setChanged(); sync(); }

    public int blocker() {
        if (stage != Stage.IDLE) return 1;
        DroneType t = type();
        if (!DroneLine.complete(this, t)) return 2;
        if (t.big ? freeBay(hangar(), t) < 0 : freeShelf(storage()) < 0) return 3;
        if (store.getEnergyStored() < t.runFe()) return 4;
        if (!DroneRecipes.satisfied(inputs, DroneRecipes.needs(t))) return 5;
        return 0;
    }

    private static int freeShelf(DroneModuleBlockEntity st) {
        if (st == null) return -1;
        for (int i = 0; i < SHELVES; i++) if (st.racked.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    static int freeBay(DroneModuleBlockEntity h, DroneType t) {
        if (h == null) return -1;
        ItemStack a = h.parked.getStackInSlot(0), b = h.parked.getStackInSlot(1);
        if (t == DroneType.T4) return a.isEmpty() && b.isEmpty() ? 0 : -1;
        if (DroneType.of(a) == DroneType.T4) return -1;
        return a.isEmpty() ? 0 : b.isEmpty() ? 1 : -1;
    }

    public void startRun(Player player) {
        int b = blocker();
        if (b != 0) { if (player != null) player.displayClientMessage(Component.translatable("message.wmd.drone_line.cannot." + b), true); return; }
        DroneType t = type();
        DroneRecipes.consume(inputs, DroneRecipes.needs(t));
        store.use(t.runFe());
        level.playSound(null, worldPosition, WmdSounds.CC_PING.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        enter(Stage.LOAD);
        if (player != null) player.displayClientMessage(Component.translatable("message.wmd.drone_line.started", Component.translatable("item.wmd.drone_" + t.id())), true);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneModuleBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        if (be.kind == DroneKind.HANGAR) { hangarTick(level, pos, be); return; }
        if (be.kind != DroneKind.CORE) return;
        if (be.stage == Stage.IDLE) {
            if (be.auto && level.getGameTime() % 20 == 0 && be.blocker() == 0) be.startRun(null);
            return;
        }
        long now = level.getGameTime();
        if (be.stageStart > now) { be.stageStart = now; be.sync(); }
        if (now % 20 == 0 && !DroneLine.complete(be, be.type())) { be.abort(); return; }
        if (now >= be.stageStart + be.stageLen) be.advance();
    }

    private static void hangarTick(Level level, BlockPos pos, DroneModuleBlockEntity h) {
        long now = level.getGameTime();
        if (now % 40 == 11 && level instanceof net.minecraft.server.level.ServerLevel sl) {
            boolean r = BigDrones.runwayOk(sl, pos, h.getBlockState().getValue(MachineBlock.FACING));
            if (r != h.runway) { h.runway = r; h.changedAndSync(); }
        }
        if (h.out != 0 && now % 20 == 9 && !Drones.alive(level.getServer(), h.out)) { h.out = 0; h.changedAndSync(); }
    }

    private void enter(Stage s) {
        stage = s; stageLen = lenOf(s); stageStart = level.getGameTime();
        sync();
    }

    private void advance() {
        DroneType t = type();
        switch (stage) {
            case TO_STORE -> {
                if (!t.big) storeSlot = Math.max(0, freeShelf(storage()));
                else storeSlot = Math.max(0, freeBay(hangar(), t));
                enter(Stage.STORE);
            }
            case STORE -> {
                DroneModuleBlockEntity dest = t.big ? hangar() : storage();
                if (dest != null) {
                    ItemStackHandler h = t.big ? dest.parked : dest.racked;
                    int s = storeSlot;
                    if (!h.getStackInSlot(s).isEmpty() || (t.big && freeBay(dest, t) != s)) s = t.big ? freeBay(dest, t) : freeShelf(dest);
                    if (s < 0) return;
                    h.setStackInSlot(s, t.stack());
                }
                level.playSound(null, worldPosition, WmdSounds.LAB_REACTOR_DONE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                tell(Component.translatable("message.wmd.drone_line.done", Component.translatable("item.wmd.drone_" + t.id())));
                enter(Stage.TO_HOME);
            }
            case TO_HOME -> enter(Stage.IDLE);
            default -> enter(Stage.values()[stage.ordinal() + 1]);
        }
    }

    private void abort() {
        tell(Component.translatable("message.wmd.drone_line.aborted"));
        enter(Stage.IDLE);
    }

    private void tell(Component c) {
        if (level == null) return;
        Vec3 m = SableBridge.worldPos(level, worldPosition);
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(16))) p.displayClientMessage(c, true);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void dropContents() {
        if (level == null) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        if (kind == DroneKind.CORE) for (int i = 0; i < inputs.getSlots(); i++) Containers.dropItemStack(level, x, y, z, inputs.getStackInSlot(i));
        if (kind == DroneKind.STORAGE) for (int i = 0; i < SHELVES; i++) Containers.dropItemStack(level, x, y, z, racked.getStackInSlot(i));
        if (kind == DroneKind.HANGAR) {
            for (int i = 0; i < BAYS; i++) Containers.dropItemStack(level, x, y, z, parked.getStackInSlot(i));
            for (int i = 0; i < PYLONS; i++) Containers.dropItemStack(level, x, y, z, pylons.getStackInSlot(i));
        }
    }

    private final IItemHandler hopperIn = new IItemHandler() {
        @Override public int getSlots() { return 9; }
        @Override public ItemStack getStackInSlot(int s) { return inputs.getStackInSlot(s); }
        @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return isItemValid(s, stack) ? inputs.insertItem(s, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int s) { return 64; }
        @Override public boolean isItemValid(int s, ItemStack stack) { return DroneRecipes.accepts(stack); }
    };

    private IItemHandler takeOnly(ItemStackHandler h) {
        return new IItemHandler() {
            @Override public int getSlots() { return h.getSlots(); }
            @Override public ItemStack getStackInSlot(int s) { return h.getStackInSlot(s); }
            @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return stack; }
            @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return h.extractItem(s, amount, simulate); }
            @Override public int getSlotLimit(int s) { return 1; }
            @Override public boolean isItemValid(int s, ItemStack stack) { return false; }
        };
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return kind == DroneKind.CORE && Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, 0, 0, 3, Direction.SOUTH) ? store : null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        return switch (kind) {
            case CORE -> hopperIn;
            case STORAGE -> takeOnly(racked);
            case HANGAR -> takeOnly(parked);
            default -> null;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        if (kind == DroneKind.STORAGE) tag.put("racked", racked.serializeNBT(registries));
        if (kind == DroneKind.HANGAR) { tag.put("parked", parked.serializeNBT(registries)); writeHangar(tag, registries); }
        if (kind != DroneKind.CORE) return;
        tag.put("inputs", inputs.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        writeState(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (kind == DroneKind.STORAGE && tag.contains("racked")) racked.deserializeNBT(registries, tag.getCompound("racked"));
        if (kind == DroneKind.HANGAR && tag.contains("parked")) parked.deserializeNBT(registries, tag.getCompound("parked"));
        if (kind == DroneKind.HANGAR) readHangar(tag, registries);
        if (kind != DroneKind.CORE) return;
        if (tag.contains("inputs")) inputs.deserializeNBT(registries, tag.getCompound("inputs"));
        store.set(tag.getInt("energy"));
        readState(tag);
    }

    private void writeHangar(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("pylons", pylons.serializeNBT(registries));
        tag.put("mission", mission.save());
        tag.putInt("out", out);
        tag.putLong("doorAt", doorAt); tag.putLong("doorUntil", doorUntil);
        tag.putBoolean("runway", runway);
    }

    private void readHangar(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("pylons")) pylons.deserializeNBT(registries, tag.getCompound("pylons"));
        mission = DroneMission.load(tag.getCompound("mission"));
        out = tag.getInt("out");
        doorAt = tag.contains("doorAt") ? tag.getLong("doorAt") : -1000; doorUntil = tag.contains("doorUntil") ? tag.getLong("doorUntil") : -1000;
        runway = tag.getBoolean("runway");
    }

    private void writeState(CompoundTag tag) {
        tag.putInt("stage", stage.ordinal()); tag.putInt("len", stageLen); tag.putLong("start", stageStart); tag.putInt("type", type);
        tag.putInt("storeSlot", storeSlot); tag.putBoolean("auto", auto);
    }

    private void readState(CompoundTag tag) {
        int s = tag.getInt("stage");
        stage = s >= 0 && s < Stage.values().length ? Stage.values()[s] : Stage.IDLE;
        stageLen = tag.getInt("len"); stageStart = tag.getLong("start"); type = Math.floorMod(tag.getInt("type"), DroneType.values().length);
        storeSlot = Mth.clamp(tag.getInt("storeSlot"), 0, SHELVES - 1); auto = tag.getBoolean("auto");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (kind == DroneKind.CORE) writeState(tag);
        if (kind == DroneKind.STORAGE) tag.put("racked", racked.serializeNBT(registries));
        if (kind == DroneKind.HANGAR) { tag.put("parked", parked.serializeNBT(registries)); writeHangar(tag, registries); }
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (kind == DroneKind.CORE) readState(tag);
        if (kind == DroneKind.STORAGE && tag.contains("racked")) racked.deserializeNBT(registries, tag.getCompound("racked"));
        if (kind == DroneKind.HANGAR && tag.contains("parked")) parked.deserializeNBT(registries, tag.getCompound("parked"));
        if (kind == DroneKind.HANGAR) readHangar(tag, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
