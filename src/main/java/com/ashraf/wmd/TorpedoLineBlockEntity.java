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

public class TorpedoLineBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, MachinePorts, EmpVulnerable {

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public enum Stage { IDLE, LOAD, BEND, TO_FIT, FIT, TO_TEST, MATE, TANK, STORE, TO_HOME }

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.TLINE_BUFFER_FE, Balance.TLINE_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final TorpedoLineKind kind;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public final ItemStackHandler inputs = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final Buffer store = new Buffer();
    private Stage stage = Stage.IDLE;
    private int stageLen, type;
    private long stageStart;
    private boolean auto;

    public final ItemStackHandler racked = new ItemStackHandler(Balance.TLINE_RACK) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { return TorpedoKind.of(s) != null; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    public TorpedoLineBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.TORPEDO_LINE_BE.get(), pos, state);
        this.kind = ((TorpedoLineBlock) state.getBlock()).kind;
    }

    private void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public TorpedoLineKind kind() { return kind; }

    Object loop;
    public Stage stage() { return stage; }
    public int typeIndex() { return type; }
    public TorpedoKind type() { return TorpedoKind.of(type); }
    public int energy() { return store.getEnergyStored(); }
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

    public TorpedoLineAnim.Pose pose(float partialTick) {
        double t = level == null ? 0 : level.getGameTime() + partialTick - stageStart;
        return TorpedoLineAnim.compute(stage, Math.max(0.0, t), stageLen, type(), freeRack(test()));
    }

    public ItemStack rackSlot(int i) { return racked.getStackInSlot(Mth.clamp(i, 0, Balance.TLINE_RACK - 1)); }

    TorpedoLineBlockEntity test() { return TorpedoLine.module(this, TorpedoLineKind.TEST); }

    public int lenOf(Stage s) {
        int fixed = Balance.TLINE_LOAD_TICKS + 2 * Balance.TLINE_TRAVEL_TICKS + Balance.TLINE_MATE_TICKS + Balance.TLINE_HOME_TICKS;
        int stations = Math.max(400, TorpedoLineRecipes.runTicks(type()) - fixed);
        return switch (s) {
            case IDLE -> 0;
            case LOAD -> Balance.TLINE_LOAD_TICKS;
            case TO_FIT, TO_TEST -> Balance.TLINE_TRAVEL_TICKS;
            case MATE -> Balance.TLINE_MATE_TICKS;
            case BEND -> (int) (stations * Balance.TLINE_STATION_SHARE[0]);
            case FIT -> (int) (stations * Balance.TLINE_STATION_SHARE[1]);
            case TANK -> (int) (stations * Balance.TLINE_STATION_SHARE[2]);
            case STORE -> (int) (stations * Balance.TLINE_STATION_SHARE[3]);
            case TO_HOME -> Balance.TLINE_HOME_TICKS;
        };
    }

    public void cycleType(boolean back) {
        if (stage != Stage.IDLE) return;
        type = Math.floorMod(type + (back ? -1 : 1), TorpedoKind.values().length);
        sync();
    }

    public void toggleAuto() { auto = !auto; setChanged(); sync(); }

    public int blocker() {
        if (stage != Stage.IDLE) return 1;
        if (!TorpedoLine.complete(this)) return 2;
        if (freeRack(test()) < 0) return 3;
        if (store.getEnergyStored() < TorpedoLineRecipes.runFe(type())) return 4;
        if (!TorpedoLineRecipes.satisfied(inputs, TorpedoLineRecipes.needs(type()))) return 5;
        return 0;
    }

    static int freeRack(TorpedoLineBlockEntity t) {
        if (t == null) return -1;
        for (int i : TorpedoLineAnim.RACK_ORDER) if (i < Balance.TLINE_RACK && t.racked.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    public void startRun(Player player) {
        int b = blocker();
        if (b != 0) { if (player != null) player.displayClientMessage(Component.translatable("message.wmd.torpedo_line.cannot." + b), true); return; }
        TorpedoKind t = type();
        TorpedoLineRecipes.consume(inputs, TorpedoLineRecipes.needs(t));
        store.use(TorpedoLineRecipes.runFe(t));
        level.playSound(null, worldPosition, WmdSounds.CC_PING.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        enter(Stage.LOAD);
        if (player != null) player.displayClientMessage(Component.translatable("message.wmd.torpedo_line.started", Component.translatable("item.wmd." + t.id)), true);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TorpedoLineBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        if (be.kind != TorpedoLineKind.SHOP) return;
        if (be.stage == Stage.IDLE) {
            if (be.auto && level.getGameTime() % 20 == 0 && be.blocker() == 0) be.startRun(null);
            return;
        }
        long now = level.getGameTime();
        if (be.stageStart > now) { be.stageStart = now; be.sync(); }
        if (now % 20 == 0 && !TorpedoLine.complete(be)) { be.abort(); return; }
        if (now >= be.stageStart + be.stageLen) be.advance();
    }

    private void enter(Stage s) {
        stage = s; stageLen = lenOf(s); stageStart = level.getGameTime();
        sync();
    }

    private void advance() {
        TorpedoKind t = type();
        switch (stage) {
            case STORE -> {
                TorpedoLineBlockEntity dest = test();
                int slot = freeRack(dest);
                if (dest == null || slot < 0) return;
                dest.racked.setStackInSlot(slot, TorpedoLineRecipes.stack(t));
                level.playSound(null, worldPosition, WmdSounds.LAB_REACTOR_DONE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
                tell(Component.translatable("message.wmd.torpedo_line.done", Component.translatable("item.wmd." + t.id)));
                enter(Stage.TO_HOME);
            }
            case TO_HOME -> enter(Stage.IDLE);
            default -> {
                enter(Stage.values()[stage.ordinal() + 1]);
            }
        }
    }

    private void abort() {
        tell(Component.translatable("message.wmd.torpedo_line.aborted"));
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

    private boolean wasSplashing;

    Vec3 linePoint(double ax, double ay, double az) {
        double lx = (ax - 8.0) / 16.0, ly = ay / 16.0, lz = (az - 8.0) / 16.0, dx, dz;
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        switch (f) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        Direction m = Machines.mount(getBlockState());
        Vec3 p = m == Direction.UP ? new Vec3(worldPosition.getX() + 0.5 + dx, worldPosition.getY() + ly, worldPosition.getZ() + 0.5 + dz)
                : Machines.mountPoint(worldPosition, m, dx, ly, dz);
        return level != null && level.isClientSide ? ShipFrame.drawnWorld(level, worldPosition, p, Float.NaN) : SableBridge.toWorld(level, p);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TorpedoLineBlockEntity be) {
        if (be.kind != TorpedoLineKind.SHOP || be.stage == Stage.IDLE) { be.wasSplashing = false; return; }
        TorpedoLineAnim.Pose p = be.pose(0.0F);
        net.minecraft.util.RandomSource r = level.random;
        if (p.welding) be.sparks(level, r, TorpedoLineAnim.TOP_X, TorpedoLineAnim.TOP_Y + 6.2, p.torchZ, 3);
        if (p.cutSparks) be.sparks(level, r, TorpedoLineAnim.X0 + (r.nextDouble() - 0.5) * 10.0, TorpedoLineAnim.AY + 5.0, 60.0, 4);
        for (int a = 0; a < 2; a++)
            if (p.armWeld[a]) be.sparks(level, r, p.wrist[a][0], p.wrist[a][1] - 4.6, TorpedoLineAnim.FIT + p.wrist[a][2], 3);
        double hx = TorpedoLineAnim.X0 + p.hoistX, hz = TorpedoLineAnim.CRANE_REST + p.crane, axis = TorpedoLineAnim.HOOK_Y + p.hookY - TorpedoLineAnim.LOAD_DROP;
        if (p.splashing && !be.wasSplashing) {
            Vec3 c = be.linePoint(hx, TorpedoLineAnim.WATER, hz);
            for (int i = 0; i < 40; i++) {
                Vec3 q = be.linePoint(hx + (r.nextDouble() - 0.5) * 12.0, TorpedoLineAnim.WATER, hz + (r.nextDouble() - 0.5) * 52.0);
                level.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, q.x, q.y, q.z, (r.nextDouble() - 0.5) * 0.12, 0.15 + r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.12);
            }
            level.playLocalSound(c.x, c.y, c.z, WmdSounds.TL_TANK.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F, false);
        }
        be.wasSplashing = p.splashing;
        if (p.bubbling && level.getGameTime() % 2 == 0) {
            Vec3 q = be.linePoint(hx + (r.nextDouble() - 0.5) * 6.0, axis + 2.0 + r.nextDouble() * 4.0, hz + 22.0 + r.nextDouble() * 6.0);
            WmdSmokeParticle.nextSize = 0.25F;
            try { level.addAlwaysVisibleParticle(WmdParticles.UW_GAS.get(), true, q.x, q.y, q.z, 0.0, 0.04, 0.0); } finally { WmdSmokeParticle.nextSize = 1.0F; }
        }
        if (p.dripping && r.nextFloat() < 0.6F) {
            Vec3 q = be.linePoint(hx + (r.nextDouble() - 0.5) * 6.0, axis - 5.8, hz + (r.nextDouble() - 0.5) * 50.0);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, q.x, q.y, q.z, 0.0, 0.0, 0.0);
        }
        if (p.spraying) {
            int[] t = be.type().tinted() ? MissileParts.tint(be.type().paint) : new int[] { 200, 200, 200 };
            net.minecraft.core.particles.DustParticleOptions mist = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(t[0] / 255.0F, t[1] / 255.0F, t[2] / 255.0F), 1.1F);
            for (int i = 0; i < 3; i++) {
                Vec3 q = be.linePoint(hx + (r.nextDouble() - 0.5) * 12.0, axis + 4.0 + r.nextDouble() * 4.0, hz + (r.nextDouble() - 0.5) * 52.0);
                level.addParticle(mist, q.x, q.y, q.z, 0.0, -0.02, 0.0);
            }
        }
    }

    private void sparks(Level level, net.minecraft.util.RandomSource r, double ax, double ay, double az, int n) {
        Vec3 c = linePoint(ax, ay, az);
        for (int i = 0; i < n; i++)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
        if (r.nextFloat() < 0.25F) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, 0.0, 0.01, 0.0);
        if (level.getGameTime() % 10 == 0) level.playLocalSound(c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.8F, false);
    }

    void dropContents() {
        if (level == null) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        if (kind == TorpedoLineKind.SHOP) for (int i = 0; i < inputs.getSlots(); i++) Containers.dropItemStack(level, x, y, z, inputs.getStackInSlot(i));
        if (kind == TorpedoLineKind.TEST) for (int i = 0; i < Balance.TLINE_RACK; i++) Containers.dropItemStack(level, x, y, z, racked.getStackInSlot(i));
    }

    private final IItemHandler hopperIn = new IItemHandler() {
        @Override public int getSlots() { return 9; }
        @Override public ItemStack getStackInSlot(int s) { return inputs.getStackInSlot(s); }
        @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return isItemValid(s, stack) ? inputs.insertItem(s, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int s) { return 64; }
        @Override public boolean isItemValid(int s, ItemStack stack) { return TorpedoLineRecipes.accepts(stack); }
    };

    private final IItemHandler rackOut = new IItemHandler() {
        @Override public int getSlots() { return racked.getSlots(); }
        @Override public ItemStack getStackInSlot(int s) { return racked.getStackInSlot(s); }
        @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return racked.extractItem(s, amount, simulate); }
        @Override public int getSlotLimit(int s) { return 1; }
        @Override public boolean isItemValid(int s, ItemStack stack) { return false; }
    };

    private boolean atPart(BlockPos part, Direction side, int x, int y, int z, Direction localSide) {
        return Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, x, y, z, localSide);
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return kind == TorpedoLineKind.SHOP && atPart(part, side, 2, 0, 4, Direction.EAST) ? store : null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (kind == TorpedoLineKind.SHOP && atPart(part, side, -1, 0, 2, Direction.WEST)) return hopperIn;
        if (kind == TorpedoLineKind.TEST && atPart(part, side, -1, 0, 5, Direction.SOUTH)) return rackOut;
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        if (kind == TorpedoLineKind.TEST) tag.put("racked", racked.serializeNBT(registries));
        if (kind != TorpedoLineKind.SHOP) return;
        tag.put("inputs", inputs.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        writeState(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (kind == TorpedoLineKind.TEST && tag.contains("racked")) racked.deserializeNBT(registries, tag.getCompound("racked"));
        if (kind != TorpedoLineKind.SHOP) return;
        if (tag.contains("inputs")) inputs.deserializeNBT(registries, tag.getCompound("inputs"));
        store.set(tag.getInt("energy"));
        readState(tag);
    }

    private void writeState(CompoundTag tag) {
        tag.putInt("stage", stage.ordinal()); tag.putInt("len", stageLen); tag.putLong("start", stageStart); tag.putInt("type", type); tag.putBoolean("auto", auto);
    }

    private void readState(CompoundTag tag) {
        int s = tag.getInt("stage");
        stage = s >= 0 && s < Stage.values().length ? Stage.values()[s] : Stage.IDLE;
        stageLen = tag.getInt("len"); stageStart = tag.getLong("start"); type = Math.floorMod(tag.getInt("type"), TorpedoKind.values().length); auto = tag.getBoolean("auto");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (kind == TorpedoLineKind.SHOP) writeState(tag);
        if (kind == TorpedoLineKind.TEST) tag.put("racked", racked.serializeNBT(registries));
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (kind == TorpedoLineKind.SHOP) readState(tag);
        if (kind == TorpedoLineKind.TEST && tag.contains("racked")) racked.deserializeNBT(registries, tag.getCompound("racked"));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
