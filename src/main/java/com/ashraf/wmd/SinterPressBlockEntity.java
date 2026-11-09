package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class SinterPressBlockEntity extends ItemProcessBlockEntity implements ModeMachine {

    public static final int INGOT = 0, ROD = 1;

    private final Slot powder = new Slot(64, false, s -> s.is(wmd("tungsten_powder")));
    private final Slot out = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.EAST);
    private final Slot[] slots = { powder, out };
    private final List<FluidTank> tanks = List.of(powder.gauge, out.gauge);
    private final IItemHandler in = inHandler(powder), outH = outHandler(out);
    private int progress;
    private int mode = INGOT;
    private boolean runRod;

    public SinterPressBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SINTER_PRESS_BE.get(), pos, state, Balance.SINTER_FE_PER_TICK);
    }

    @Override public int mode() { return mode; }
    @Override public int modeCount() { return 2; }

    @Override
    public void setMode(int m) {
        int nm = m == ROD ? ROD : INGOT;
        if (nm == mode) return;
        mode = nm;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public void setModeClient(int m) { mode = m == ROD ? ROD : INGOT; }

    public int powderPerRun() { return powderFor(mode); }

    public static int powderFor(int mode) { return mode == ROD ? Balance.SINTER_ROD_POWDER : Balance.SINTER_INGOT_POWDER; }

    public static String productFor(int mode) { return mode == ROD ? "tungsten_rod" : "tungsten_ingot"; }

    @Override
    protected boolean process() {
        if (progress == 0) {
            int need = powderFor(mode);
            if (powder.count() < need) return fail(NO_INPUT);
            if (!out.fits(wmd(productFor(mode)), 1)) return fail(OUT_FULL);
            powder.take(need);
            runRod = mode == ROD;
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.SINTER_CYCLE_TICKS) {
            progress = 0;
            out.add(wmd(productFor(runRod ? ROD : INGOT)), 1);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "tungsten_powder", productFor(mode) }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        return !in.isEmpty() && in.get(0).is(wmd("tungsten_powder")) ? new ItemStack(wmd(productFor(mode)), 1) : ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.SINTER_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("powder", mode == ROD ? "rods" : "ingots"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.SINTER_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.sinter_press", Balance.SINTER_INGOT_POWDER, Balance.SINTER_ROD_POWDER, Balance.SINTER_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.SINTER_PRESS_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x5350_0000_0000_0000L;
        double[] oven = worldPoint(0.96, 0.79, -0.42), die = worldPoint(0.0, 0.85, 0.2), lamp = worldPoint(1.1, 1.44, -0.19);
        VeilBridge.machineGlow(key, oven[0], oven[1], oven[2], 1.0F, 0.5F, 0.15F, 2.5F, 1.0F + 0.15F * (float) Math.sin(t * 0.21), t);
        VeilBridge.machineGlow(key + 1, lamp[0], lamp[1], lamp[2], 0.35F, 0.6F, 1.0F, 0.8F, 0.5F, t);
        if (t % 14 != 0) return;
        var r = level.random;
        level.addParticle(ParticleTypes.SMOKE, die[0] + (r.nextDouble() - 0.5) * 0.3, die[1] + 0.1, die[2] + (r.nextDouble() - 0.5) * 0.3, 0, 0.03, 0);
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, die[0], die[1] + 0.05, die[2], (r.nextDouble() - 0.5) * 0.1, 0.05, (r.nextDouble() - 0.5) * 0.1);
    }

    @Override
    protected String detail() { return "mode " + (mode == ROD ? "rod" : "ingot") + ", powder " + powder.count() + ", out " + out.count() + ", cycle " + progress * 100 / Balance.SINTER_CYCLE_TICKS + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 1, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putInt("mode", mode); tag.putBoolean("runRod", runRod); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); mode = tag.getInt("mode") == ROD ? ROD : INGOT; runRod = tag.getBoolean("runRod"); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("mode", mode);
        tag.putBoolean("runRod", runRod);
        return tag;
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 2).item(-1, 0, 0, Direction.WEST, false).item(1, 0, 0, Direction.EAST, true).power(0, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
