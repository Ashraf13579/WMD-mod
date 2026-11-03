package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DroneCatapultBlockEntity extends BlockEntity implements BigDroneSite, GeoBlockEntity, MachinePorts, EmpVulnerable {
    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public static final int BOMBS = 2, SHUTTLE_BACK = 60;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.CATAPULT_BUFFER_FE, Balance.CATAPULT_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Buffer store = new Buffer();
    public final ItemStackHandler drone = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { DroneType t = DroneType.of(s); return t != null && t.big; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    public final ItemStackHandler bombs = new ItemStackHandler(BOMBS) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { BombType t = BombType.of(s); return t != null && !t.heavy; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    private DroneMission mission = DroneMission.NONE;
    private int out;
    private long shotAt = -1000;

    public DroneCatapultBlockEntity(BlockPos pos, BlockState state) { super(Wmd.DRONE_CATAPULT_BE.get(), pos, state); }

    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide) BigDrones.SITES.add(this); }
    @Override public void setRemoved() { BigDrones.SITES.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { BigDrones.SITES.remove(this); super.onChunkUnloaded(); }

    private void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public int energy() { return store.getEnergyStored(); }
    public long shotAt() { return shotAt; }

    public DroneType drone() { return DroneType.of(drone.getStackInSlot(0)); }

    @Override public void empDrain() { store.set(0); setChanged(); }

    @Override public BlockPos sitePos() { return worldPosition; }
    @Override public boolean isHangar() { return false; }
    @Override public ItemStackHandler droneSlots() { return drone; }
    @Override public ItemStackHandler bombSlots() { return bombs; }
    @Override public DroneMission mission() { return mission; }
    @Override public void setMission(DroneMission m) { mission = m == null ? DroneMission.NONE : m; changedAndSync(); }
    @Override public int outId() { return out; }

    @Override
    public int siteStatus() {
        if (out != 0) return S_OUT;
        if (drone() == null) return S_EMPTY;
        if (!mission.hasTarget()) return S_NO_TARGET;
        if (store.getEnergyStored() < Balance.CATAPULT_FE) return S_NO_POWER;
        return S_READY;
    }

    @Override
    public void act(int action, int[] data, ServerPlayer by) {
        switch (action) {
            case A_LAUNCH -> launch(by);
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

    private void launch(ServerPlayer by) {
        int st = siteStatus();
        if (st != S_READY) { by.displayClientMessage(Component.translatable("gui.wmd.big_drone.st." + st), true); return; }
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition)) { by.displayClientMessage(Component.translatable("message.wmd.hive.jammed"), true); return; }
        int[] id = new int[1];
        Component why = BigDrones.launchCatapult((ServerLevel) level, this, by, id);
        if (why != null) { by.displayClientMessage(why, true); return; }
        out = id[0];
        store.use(Balance.CATAPULT_FE);
        drone.setStackInSlot(0, ItemStack.EMPTY);
        for (int i = 0; i < BOMBS; i++) bombs.setStackInSlot(i, ItemStack.EMPTY);
        shotAt = level.getGameTime();
        changedAndSync();
        by.displayClientMessage(Component.translatable("message.wmd.big_drone.launched", out), true);
    }

    boolean recover(int droneId, DroneType t, int[] left) {
        if (!drone.getStackInSlot(0).isEmpty()) return false;
        if (out == droneId) out = 0;
        drone.setStackInSlot(0, t.stack());
        returnBombs(left);
        level.playSound(null, worldPosition, WmdSounds.CATAPULT_LATCH.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
        changedAndSync();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneCatapultBlockEntity c) {
        if (c.out != 0 && level.getGameTime() % 20 == 7 && !Drones.alive(level.getServer(), c.out)) { c.out = 0; c.changedAndSync(); }
        if (level.getGameTime() % 20 == 0) c.setChanged();
    }

    void dropContents() {
        if (level == null) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.0, z = worldPosition.getZ() + 0.5;
        Containers.dropItemStack(level, x, y, z, drone.getStackInSlot(0));
        for (int i = 0; i < BOMBS; i++) Containers.dropItemStack(level, x, y, z, bombs.getStackInSlot(i));
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, 0, 0, 11, Direction.SOUTH) ? store : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.put("drone", drone.serializeNBT(registries));
        tag.put("bombs", bombs.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        tag.put("mission", mission.save());
        tag.putInt("out", out);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("drone")) drone.deserializeNBT(registries, tag.getCompound("drone"));
        if (tag.contains("bombs")) bombs.deserializeNBT(registries, tag.getCompound("bombs"));
        store.set(tag.getInt("energy"));
        mission = DroneMission.load(tag.getCompound("mission"));
        out = tag.getInt("out");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("drone", drone.serializeNBT(registries));
        tag.put("bombs", bombs.serializeNBT(registries));
        tag.put("mission", mission.save());
        tag.putInt("out", out);
        tag.putLong("shot", shotAt);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag.contains("drone")) drone.deserializeNBT(registries, tag.getCompound("drone"));
        if (tag.contains("bombs")) bombs.deserializeNBT(registries, tag.getCompound("bombs"));
        mission = DroneMission.load(tag.getCompound("mission"));
        out = tag.getInt("out");
        shotAt = tag.getLong("shot");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
