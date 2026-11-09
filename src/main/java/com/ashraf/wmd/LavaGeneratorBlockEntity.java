package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class LavaGeneratorBlockEntity extends ProcessUnitBlockEntity {

    private final FluidTank lava = new FluidTank(Balance.LG_TANK_MB, fs -> fs.getFluid() == Fluids.LAVA);
    private final IFluidHandler lavaIn = fillOnly(lava);
    private int stored;
    private int sources;
    private boolean scanned;

    private int heat;
    private int fePerTickNow;
    private int syncedSources = -1;

    private final IEnergyStorage out = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) { return 0; }
        @Override public int extractEnergy(int n, boolean sim) {
            int t = Math.min(Math.min(n, stored), Balance.LG_OUT_PER_TICK);
            if (t > 0 && !sim) { stored -= t; setChanged(); }
            return t;
        }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.LG_BUFFER_FE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    public LavaGeneratorBlockEntity(BlockPos pos, BlockState state) { super(Wmd.LAVA_GENERATOR_BE.get(), pos, state, 0); }

    public boolean tryPourBucket(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.LAVA_BUCKET) || lava.getCapacity() - lava.getFluidAmount() < 1000) return false;
        lava.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        if (!player.getAbilities().instabuild) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        if (level != null) level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.8F, 1.0F);
        setChanged();
        return true;
    }

    private void scanSources() {
        int n = 0;
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) {
            boolean ring = (x == -1 || x == 2) != (z == -1 || z == 2);
            if (ring && isSource(x, 0, z)) n++;
            if (x >= 0 && x <= 1 && z >= 0 && z <= 1 && isSource(x, -1, z)) n++;
        }
        sources = n;
    }

    private boolean isSource(int lx, int ly, int lz) {
        BlockPos p = Machines.toWorld(worldPosition, facing(), lx, ly, lz);
        if (level == null || !level.isLoaded(p)) return false;
        FluidState f = level.getFluidState(p);
        return f.is(Fluids.LAVA) && f.isSource();
    }

    @Override
    public void empDrain() { stored = 0; setChanged(); }

    @Override
    protected boolean work() {
        if (level == null) return false;
        if (!scanned || (level.getGameTime() + (worldPosition.asLong() & 31)) % Balance.LG_SCAN_TICKS == 0) { scanSources(); scanned = true; }
        int h, fe;
        if (lava.getFluidAmount() >= Balance.LG_MB_PER_TICK) { h = 2; fe = Balance.LG_FE_PIPED; }
        else if (sources > 0) { h = 1; fe = Math.min(Balance.LG_FE_PASSIVE_MAX, sources * Balance.LG_FE_PER_SOURCE); }
        else { h = 0; fe = 0; }
        if (stored + fe > Balance.LG_BUFFER_FE) { h = 0; fe = 0; }
        if (h != heat || fe != fePerTickNow || sources != syncedSources) {
            heat = h;
            fePerTickNow = fe;
            syncedSources = sources;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (fe <= 0) return false;
        if (h == 2) lava.drain(Balance.LG_MB_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
        stored += fe;
        flowAcc += fe;
        return true;
    }

    @Override protected List<FluidTank> tanks() { return List.of(lava); }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.LAVA); }
    @Override protected List<String> labelKeys() { return List.of("lava"); }
    @Override protected int rateMbPerTick() { return Balance.LG_FE_PIPED; }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public int guiFeUse() { return 0; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.lava_now", fePerTickNow * 20),
                Component.translatable("gui.wmd.info.lava_piped", Balance.LG_FE_PIPED * 20, 1000 / Balance.LG_MB_PER_TICK / 20),
                Component.translatable("gui.wmd.info.lava_passive", Balance.LG_FE_PER_SOURCE * 20, Balance.LG_FE_PASSIVE_MAX * 20, sources));
    }

    @Override protected SoundEvent runSound() { return SoundEvents.LAVA_AMBIENT; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return heat == 2 ? Balance.SND_MACHINE : Balance.SND_SMALL; }

    public int heatShown() { return heat; }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        double[] top = worldPoint(0.5, 2.05, 0.5);
        if (r.nextInt(heat == 2 ? 3 : 7) == 0)
            level.addParticle(ParticleTypes.WHITE_ASH, top[0] + (r.nextDouble() - 0.5) * 0.6, top[1], top[2] + (r.nextDouble() - 0.5) * 0.6, 0.0, 0.05, 0.0);
        if (r.nextInt(heat == 2 ? 6 : 14) == 0)
            level.addParticle(ParticleTypes.SMOKE, top[0] + (r.nextDouble() - 0.5) * 0.5, top[1], top[2] + (r.nextDouble() - 0.5) * 0.5, 0.0, 0.04, 0.0);
        if (heat == 2 && r.nextInt(40) == 0) level.addParticle(ParticleTypes.LAVA, top[0], top[1], top[2], 0.0, 0.0, 0.0);
    }

    @Override
    protected String detail() { return String.format("%d FE/t, lava %s, %d source(s) beside, stored %,d FE", fePerTickNow, mb(lava), sources, stored); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? out : null; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.WEST) ? lavaIn : null; }

    private static final PortMap PORT_MAP = new PortMap(0, 1, 2).fluid(0, 0, 0, Direction.WEST, "lava", false).power(1, 0, 1, Direction.SOUTH, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("stored")) stored = Mth.clamp(tag.getInt("stored"), 0, Balance.LG_BUFFER_FE);
        if (tag.contains("lavaheat")) { int i = tag.getInt("lavaheat"); heat = i & 3; fePerTickNow = (i >> 2) & 4095; sources = i >> 14; }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("lavaheat", heat | Math.min(fePerTickNow, 4095) << 2 | sources << 14);
        return tag;
    }
}
