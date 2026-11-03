package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class HydrogenFurnaceBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank hydrogen = new FluidTank(Balance.H2F_TANK_MB, fs -> fs.getFluid() == WmdFluids.HYDROGEN.source().get());
    private final Slot oxide = new Slot(64, false, s -> s.is(wmd("tungsten_oxide")));
    private final Slot powder = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.EAST);
    private final Slot[] slots = { oxide, powder };
    private final List<FluidTank> tanks = List.of(hydrogen, oxide.gauge, powder.gauge);
    private final IFluidHandler h2In = fillOnly(hydrogen);
    private final IItemHandler in = inHandler(oxide), outH = outHandler(powder);
    private int progress;

    public HydrogenFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.HYDROGEN_FURNACE_BE.get(), pos, state, Balance.H2F_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (oxide.count() < Balance.H2F_OXIDE || hydrogen.getFluidAmount() < Balance.H2F_H2_MB) return fail(NO_INPUT);
            if (!powder.fits(wmd("tungsten_powder"), Balance.H2F_POWDER)) return fail(OUT_FULL);
            oxide.take(Balance.H2F_OXIDE);
            hydrogen.drain(Balance.H2F_H2_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.H2F_CYCLE_TICKS) {
            progress = 0;
            powder.add(wmd("tungsten_powder"), Balance.H2F_POWDER);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "tungsten_oxide", "tungsten_powder" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        return !in.isEmpty() && in.get(0).is(wmd("tungsten_oxide")) ? new ItemStack(wmd("tungsten_powder"), Balance.H2F_POWDER) : ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.H2F_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.HYDROGEN.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("hydrogen", "oxide", "powder"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.H2F_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.hydrogen_furnace", Balance.H2F_OXIDE, Balance.H2F_H2_MB, Balance.H2F_POWDER, Balance.H2F_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.HF_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return 0.5F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x4846_0000_0000_0000L;
        double[] sight = worldPoint(0.0, 1.38, -0.40), pilot = worldPoint(0.56, 2.96, 0.88), lamp = worldPoint(1.0, 1.32, -0.15);
        VeilBridge.machineGlow(key, sight[0], sight[1], sight[2], 1.0F, 0.55F, 0.2F, 2.5F, 1.1F + 0.1F * (float) Math.sin(t * 0.17), t);
        VeilBridge.machineGlow(key + 1, pilot[0], pilot[1], pilot[2], 0.55F, 0.7F, 1.0F, 1.5F, 0.8F + 0.2F * (float) Math.sin(t * 0.9), t);
        VeilBridge.machineGlow(key + 2, lamp[0], lamp[1], lamp[2], 0.35F, 0.6F, 1.0F, 0.8F, 0.5F, t);
        if (t % 9 != 0) return;
        var r = level.random;
        level.addParticle(ParticleTypes.SMALL_FLAME, pilot[0] + (r.nextDouble() - 0.5) * 0.08, pilot[1] + 0.05, pilot[2] + (r.nextDouble() - 0.5) * 0.08, 0, 0.01, 0);
        level.addParticle(ParticleTypes.CLOUD, pilot[0], pilot[1] + 0.25, pilot[2], 0, 0.03, 0);
    }

    @Override
    protected String detail() { return "hydrogen " + mb(hydrogen) + ", oxide " + oxide.count() + ", powder " + powder.count() + ", cycle " + progress * 100 / Balance.H2F_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 1, Direction.WEST) ? h2In : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 1, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 2).item(-1, 0, 0, Direction.WEST, false).fluid(-1, 0, 1, Direction.WEST, "hydrogen", false)
            .item(1, 0, 0, Direction.EAST, true).power(0, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
