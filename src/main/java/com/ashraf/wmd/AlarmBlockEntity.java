package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AlarmBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, EmpVulnerable {
    private final SecurityData security = new SecurityData();
    @Override public SecurityData security() { return security; }

    public static final int CAPACITY = 200_000;
    public static final int FE_PER_TICK = 100;
    public static final int TEST_TICKS = 200;
    public static final int DATA_COUNT = 4;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(CAPACITY, 20_000, 0); }
        int use(int n) { int u = Math.min(n, energy); energy -= u; return u; }
        void set(int e) { energy = Mth.clamp(e, 0, CAPACITY); }
    }

    private final Buffer store = new Buffer();

    @Override
    public void empDrain() { store.set(0); setChanged(); }
    private int frequency = 1;
    private UUID owner;
    private String ownerName = "";
    private boolean sounding;
    private int testTicks;

    private float rotorAngle, rotorSpeed;
    public Object soundHandle;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public AlarmBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ALARM_BE.get(), pos, state);
    }

    public IEnergyStorage getEnergy() { return store; }
    public int getFrequency() { return frequency; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public boolean isSounding() { return sounding; }

    public void claim(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public void setFrequency(int f) {
        frequency = Mth.clamp(f, 1, 9999);
        setChanged();
    }

    public void startTest() { testTicks = TEST_TICKS; }

    public float rotorAngle(float partialTick) { return rotorAngle + rotorSpeed * partialTick; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlarmBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        boolean want = be.testTicks > 0 || (be.owner != null && AlarmNet.isActive((ServerLevel) level, be.owner, be.frequency));
        if (be.testTicks > 0) be.testTicks--;
        boolean now = want && be.store.getEnergyStored() >= FE_PER_TICK;
        if (now) be.store.use(FE_PER_TICK);
        if (now != be.sounding) {
            be.sounding = now;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (level.getGameTime() % 40 == 0) be.setChanged();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AlarmBlockEntity be) {
        be.rotorAngle += be.rotorSpeed;
        float target = be.sounding ? 18.0F : 0.0F;
        be.rotorSpeed += (target - be.rotorSpeed) * (be.sounding ? 0.04F : 0.02F);
        if (be.rotorSpeed < 0.05F && !be.sounding) be.rotorSpeed = 0.0F;
        if (be.rotorAngle > 3600.0F) be.rotorAngle -= 3600.0F;
        if (be.sounding) AlarmClient.ensureSound(be);
    }

    public ContainerData getContainerData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> frequency;
                    case 1 -> sounding ? 1 : 0;
                    case 2 -> store.getEnergyStored() & 0xFFFF;
                    case 3 -> (store.getEnergyStored() >>> 16) & 0xFFFF;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() { return DATA_COUNT; }
        };
    }

    private void saveState(CompoundTag tag) {
        tag.putInt("frequency", frequency);
        tag.putBoolean("sounding", sounding);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        security.write(tag);
        saveState(tag);
        tag.putInt("energy", store.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        security.read(tag);
        frequency = Mth.clamp(tag.getInt("frequency"), 1, 9999);
        sounding = tag.getBoolean("sounding");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        if (tag.contains("energy")) store.set(tag.getInt("energy"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveState(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
