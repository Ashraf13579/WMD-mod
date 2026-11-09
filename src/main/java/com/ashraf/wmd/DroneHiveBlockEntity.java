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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DroneHiveBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, MachinePorts, EmpVulnerable {
    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public static final int BAYS = 4, LID_TICKS = 50;

    static final double[][] PAD = { { -12, -12 }, { 12, -12 }, { -12, 12 }, { 12, 12 } };
    static final double PAD_Y = 22.6;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.HIVE_BUFFER_FE, Balance.HIVE_RECEIVE_FE, 0); }
        int take(int n) { int t = Math.min(n, energy); energy -= t; return t; }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Buffer store = new Buffer();
    public final ItemStackHandler bays = new ItemStackHandler(BAYS) {
        @Override protected void onContentsChanged(int slot) { if (getStackInSlot(slot).isEmpty()) charge[slot] = 0; changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { DroneType t = DroneType.of(s); return t != null && !t.big && out[slot] == 0; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    private final int[] charge = new int[BAYS], out = new int[BAYS], mode = new int[BAYS];
    private final long[] lidAt = { -1000, -1000, -1000, -1000 };
    private boolean[] chargingSynced = new boolean[BAYS];

    public DroneHiveBlockEntity(BlockPos pos, BlockState state) { super(Wmd.DRONE_HIVE_BE.get(), pos, state); }

    private void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public int energy() { return store.getEnergyStored(); }
    public int charge(int bay) { return charge[bay]; }
    public int out(int bay) { return out[bay]; }
    public int mode(int bay) { return mode[bay]; }
    public long lidAt(int bay) { return lidAt[bay]; }

    public DroneType docked(int bay) { return DroneType.of(bays.getStackInSlot(bay)); }
    public boolean charging(int bay) { return chargingSynced[bay]; }
    boolean huntPlayers(int bay) { return mode[bay] != 2; }
    boolean huntMobs(int bay) { return mode[bay] != 1; }

    boolean flyMode(int bay) { return mode[bay] == 1; }

    @Override public void empDrain() { store.set(0); setChanged(); }

    Vec3 bayWorld(int bay) {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        double lx = PAD[bay][0] / 16.0, lz = PAD[bay][1] / 16.0, dx, dz;
        switch (f) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        Direction m = Machines.mount(getBlockState());
        Vec3 p = m == Direction.UP ? new Vec3(worldPosition.getX() + 0.5 + dx, worldPosition.getY() + PAD_Y / 16.0, worldPosition.getZ() + 0.5 + dz)
                : Machines.mountPoint(worldPosition, m, dx, PAD_Y / 16.0, dz);
        return SableBridge.toWorld(level, p);
    }

    void openLid(int bay) { lidAt[bay] = level.getGameTime(); changedAndSync(); }

    public void cycleMode(int bay) {
        DroneType t = docked(bay);
        mode[bay] = t == DroneType.T2 ? (mode[bay] == 1 ? 0 : 1) : (mode[bay] + 1) % 3;
        changedAndSync();
    }

    public void launchOrRecall(int bay, ServerPlayer p) {
        if (out[bay] != 0) {
            StrikeDrones.recall(p.server, out[bay]);
            p.displayClientMessage(Component.translatable("message.wmd.hive.recalled", out[bay]), true);
            return;
        }
        DroneType t = docked(bay);
        if (t == null) { p.displayClientMessage(Component.translatable("message.wmd.hive.empty"), true); return; }
        if (charge[bay] < Balance.DRONE_CHARGE_FE) { p.displayClientMessage(Component.translatable("message.wmd.hive.charging", charge[bay] * 100 / Balance.DRONE_CHARGE_FE), true); return; }
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition)) { p.displayClientMessage(Component.translatable("message.wmd.hive.jammed"), true); return; }
        boolean fly = t == DroneType.T2 && flyMode(bay);
        int[] id = new int[1];
        Component why = StrikeDrones.launch((ServerLevel) level, this, bay, t, p, fly, id);
        if (why != null) { p.displayClientMessage(why, true); return; }
        out[bay] = id[0];
        bays.setStackInSlot(bay, ItemStack.EMPTY);
        charge[bay] = 0;
        openLid(bay);
        if (fly) p.closeContainer();
    }

    boolean dock(int bay, int droneId, DroneType t) {
        if (bay < 0 || bay >= BAYS || !bays.getStackInSlot(bay).isEmpty()) return false;
        if (out[bay] == droneId) out[bay] = 0;
        bays.setStackInSlot(bay, t.stack());
        charge[bay] = 0;
        openLid(bay);
        level.playSound(null, worldPosition, WmdSounds.HIVE_LID.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneHiveBlockEntity h) {
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, pos)) return;
        boolean sync = false;
        for (int i = 0; i < BAYS; i++) {
            boolean ch = h.docked(i) != null && h.charge[i] < Balance.DRONE_CHARGE_FE;
            if (ch) {
                int got = h.store.take(Math.min(Balance.HIVE_CHARGE_RATE, Balance.DRONE_CHARGE_FE - h.charge[i]));
                h.charge[i] += got;
                ch = got > 0;
            }
            if (ch != h.chargingSynced[i]) { h.chargingSynced[i] = ch; sync = true; }
            if (h.out[i] != 0 && level.getGameTime() % 20 == i && !StrikeDrones.flying(level.getServer(), h.out[i])) { h.out[i] = 0; sync = true; }
        }
        if (level.getGameTime() % 20 == 0) h.setChanged();
        if (sync) h.changedAndSync();
    }

    void dropContents() {
        if (level == null) return;
        for (int i = 0; i < BAYS; i++) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, bays.getStackInSlot(i));
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, 0, 0, 1, Direction.SOUTH) ? store : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.put("bays", bays.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        tag.putIntArray("charge", charge); tag.putIntArray("out", out); tag.putIntArray("mode", mode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("bays")) bays.deserializeNBT(registries, tag.getCompound("bays"));
        store.set(tag.getInt("energy"));
        copy(tag.getIntArray("charge"), charge); copy(tag.getIntArray("out"), out); copy(tag.getIntArray("mode"), mode);
    }

    private static void copy(int[] from, int[] to) { for (int i = 0; i < to.length; i++) to[i] = i < from.length ? from[i] : 0; }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("bays", bays.serializeNBT(registries));
        tag.putIntArray("out", out);
        tag.putLongArray("lid", lidAt);
        int c = 0;
        for (int i = 0; i < BAYS; i++) if (chargingSynced[i]) c |= 1 << i;
        tag.putInt("chg", c);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag.contains("bays")) bays.deserializeNBT(registries, tag.getCompound("bays"));
        copy(tag.getIntArray("out"), out);
        long[] l = tag.getLongArray("lid");
        for (int i = 0; i < BAYS; i++) lidAt[i] = i < l.length ? l[i] : -1000;
        int c = tag.getInt("chg");
        for (int i = 0; i < BAYS; i++) chargingSynced[i] = (c >> i & 1) != 0;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
