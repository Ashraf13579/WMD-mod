package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FenceEnergizerBlockEntity extends ProcessUnitBlockEntity implements Owned {

    private final OwnerData ownerData = new OwnerData();
    private int pieces;
    private boolean shortOfPower;
    private int clientPieces;

    public FenceEnergizerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FENCE_ENERGIZER_BE.get(), pos, state, Balance.EF_FE_PER_TICK);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private List<BlockPos> starts() {
        List<BlockPos> l = new ArrayList<>();
        for (Direction d : Direction.values()) l.add(worldPosition.relative(d));
        return l;
    }

    public boolean pulseLit(Level level) { return isRunning() && level.getGameTime() % Balance.EF_PULSE_TICKS < 3; }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (now % Balance.EF_PULSE_TICKS != 0) return !shortOfPower || pieces == 0;
        if (PortableJammerBlockEntity.jams(sl, worldPosition, getOwner())) return false;
        List<BlockPos> fence = ElectricFenceBlock.walk(sl, starts(), Balance.EF_MAX_PIECES);
        int before = pieces;
        pieces = fence.size();
        shortOfPower = !fence.isEmpty() && !useExtraEnergy(pieces * Balance.EF_FE_PER_PIECE);
        if (!shortOfPower) for (BlockPos p : fence) ElectricFenceBlock.markLive(sl, p, now + Balance.EF_LIVE_TICKS);
        if (now % 200 == 0) ElectricFenceBlock.prune(sl);
        if (before != pieces) { setChanged(); sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
        return !shortOfPower;
    }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % Balance.EF_PULSE_TICKS != 0) return;
        var r = level.random;
        level.playLocalSound(worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5, WmdSounds.EF_PULSE.get(), SoundSource.BLOCKS, 0.5F, 1.0F, false);
        if (clientPieces <= 0) return;
        List<BlockPos> fence = ElectricFenceBlock.walk(level, starts(), Math.min(clientPieces, Balance.EF_MAX_PIECES));
        for (int i = 0; i < Math.min(3, fence.size()); i++) {
            BlockPos p = fence.get(r.nextInt(fence.size()));
            double y = p.getY() + (r.nextInt(3) == 0 ? 4.0 : r.nextBoolean() ? 8.5 : 13.0) / 16.0;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.3 + r.nextDouble() * 0.4, y, p.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected SoundEvent runSound() { return WmdSounds.FE_PULSE_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return 0.06F; }
    @Override protected float soundPitch() { return 1.7F; }

    @Override
    protected String detail() { return shortOfPower ? "SHORT OF POWER" : pieces + " fence pieces live"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable(shortOfPower ? "gui.wmd.info.fence_energizer.short" : "gui.wmd.info.fence_energizer.live", pieces, Balance.EF_MAX_PIECES),
                Component.translatable("gui.wmd.info.fence_energizer.cost", Balance.EF_FE_PER_PIECE * 20 / Balance.EF_PULSE_TICKS));
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("pieces")) clientPieces = tag.getInt("pieces");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("pieces", pieces);
        ownerData.write(tag);
        return tag;
    }
}
