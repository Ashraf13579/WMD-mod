package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class CcModuleBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ShipRelink.Holder {

    private final OwnerData ownerData = new OwnerData();
    private boolean registered;

    private BlockPos building;

    protected CcModuleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fe) {
        super(type, pos, state, Math.max(1, fe));
    }

    public abstract CcModule kind();

    public BlockPos buildingPos() { return building; }

    public boolean isManaged() { return building != null; }

    public CcBuildingBlockEntity building() {
        if (building == null || level == null || !level.isLoaded(building)) return null;
        return level.getBlockEntity(building) instanceof CcBuildingBlockEntity b && !b.isRemoved() ? b : null;
    }

    @Override
    public int relink(ServerLevel level, java.util.function.LongUnaryOperator map) {
        if (building == null) return 0;
        long to = map.applyAsLong(building.asLong());
        if (to == building.asLong()) return 0;
        building = BlockPos.of(to);
        setChanged();
        sync();
        return 1;
    }

    void manage(BlockPos buildingPos, CompoundTag ownerTag) {
        building = buildingPos.immutable();
        ownerData.read(ownerTag);
        setChanged();
        sync();
    }

    protected void moduleTick(Level level, long now) {}

    protected void onlineChanged() {}

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }

    @Override public SecurityData security() {
        CcBuildingBlockEntity b = building();
        return b != null ? b.security() : ownerData.security();
    }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    public boolean isOnline() { return running; }

    public static void serverTickModule(Level level, BlockPos pos, BlockState state, CcModuleBlockEntity be) {
        if (!be.registered) {
            CommandCenter.join(be);
            be.registered = true;
        }
        long now = level.getGameTime();
        if ((now + (pos.asLong() & 15L)) % 20 == 0) {

            CcBuildingBlockEntity host = be.building();
            if (be.isManaged() && host != null && !host.hosts(be)) { level.removeBlock(pos, false); return; }
            boolean jammed = level instanceof ServerLevel sl && EmpField.jammed(sl, pos);
            boolean on = !jammed && CommandCenter.activeRoomFor(be) != null;
            if (on != be.running) {
                be.running = on;
                be.setChanged();
                be.sync();
                be.onlineChanged();
            }
            BlockState st = level.getBlockState(pos);
            if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != on) level.setBlock(pos, st.setValue(BlockStateProperties.LIT, on), 3);
        }
        be.moduleTick(level, now);
    }

    public static void clientTickModule(Level level, BlockPos pos, BlockState state, CcModuleBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CommandCenter.leave(this);
        registered = false;
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private final IEnergyStorage roomView = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean s) { return 0; }
        @Override public int extractEnergy(int n, boolean s) { return 0; }
        @Override public int getEnergyStored() { CcBuildingBlockEntity b = building(); return b == null ? 0 : b.bufferStored(); }
        @Override public int getMaxEnergyStored() { return Balance.CC_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return false; }
    };

    @Override protected boolean work() { return false; }
    @Override public boolean hasPower() { return running; }
    @Override public IEnergyStorage guiEnergy() { return roomView; }
    @Override public int guiFeUse() { return kind().fe(); }
    @Override public int guiStatus() { return running ? 1 : 2; }
    @Override public void empDrain() {}

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", kind().fe() * 20), Component.translatable("gui.wmd.info.cc_module"));
    }

    @Override protected String detail() { return running ? "online" : (building == null ? "offline: not installed in a command center" : "offline: the command center has no power"); }

    @Override protected SoundEvent runSound() { return WmdSounds.CC_SERVER_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return 0.05F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        if (building != null) tag.putLong("ccBuilding", building.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        building = tag.contains("ccBuilding") ? BlockPos.of(tag.getLong("ccBuilding")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        if (building != null) tag.putLong("ccBuilding", building.asLong());
        return tag;
    }
}
