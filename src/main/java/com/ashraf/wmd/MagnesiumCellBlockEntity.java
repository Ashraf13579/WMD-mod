package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class MagnesiumCellBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank brine = new FluidTank(Balance.MGCELL_TANK_MB, fs -> fs.getFluid() == WmdFluids.BRINE.source().get());
    private final FluidTank mgcl = new FluidTank(Balance.MGCELL_TANK_MB, fs -> fs.getFluid() == WmdFluids.MAGNESIUM_CHLORIDE.source().get());
    private final FluidTank chlorine = new FluidTank(Balance.MGCELL_TANK_MB, fs -> fs.getFluid() == WmdFluids.CHLORINE.source().get());
    private final Slot mg = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.NORTH);
    private final Slot li = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.NORTH);
    private final Slot[] slots = { mg, li };
    private final List<FluidTank> tanks = List.of(mgcl, brine, chlorine, mg.gauge, li.gauge);
    private final IFluidHandler brineIn = fillOnly(brine), mgclIn = fillOnly(mgcl), clOut = drainOnly(chlorine);
    private final IItemHandler mgOut = outHandler(mg, li);
    private int progress;
    private int brineCycles;
    private boolean fromMgcl;

    public MagnesiumCellBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.MAGNESIUM_CELL_BE.get(), pos, state, Balance.MGCELL_FE_PER_TICK);
    }

    private static boolean room(FluidTank t, int mb) { return t.getCapacity() - t.getFluidAmount() >= mb; }

    private int cycleTicks() { return fromMgcl ? Balance.MGCELL_CYCLE_TICKS : Balance.MGCELL_BRINE_TICKS; }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (mgcl.getFluidAmount() >= Balance.MGCELL_MGCL_MB) {
                if (!room(chlorine, Balance.MGCELL_CL_FROM_MGCL) || !mg.fits(wmd("magnesium_ingot"), Balance.MGCELL_MG_FROM_MGCL)) return fail(OUT_FULL);
                mgcl.drain(Balance.MGCELL_MGCL_MB, IFluidHandler.FluidAction.EXECUTE);
                fromMgcl = true;
            } else if (brine.getFluidAmount() >= Balance.MGCELL_BRINE_MB) {
                if (!mg.fits(wmd("magnesium_ingot"), Balance.MGCELL_MG_FROM_BRINE)) return fail(OUT_FULL);
                brine.drain(Balance.MGCELL_BRINE_MB, IFluidHandler.FluidAction.EXECUTE);
                fromMgcl = false;
            } else return fail(NO_INPUT);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += (fromMgcl ? Balance.MGCELL_MGCL_MB : Balance.MGCELL_BRINE_MB) / cycleTicks();
        if (++progress > cycleTicks()) {
            progress = 0;
            if (fromMgcl) {
                chlorine.fill(new FluidStack(WmdFluids.CHLORINE.source().get(), Balance.MGCELL_CL_FROM_MGCL), IFluidHandler.FluidAction.EXECUTE);
                mg.add(wmd("magnesium_ingot"), Balance.MGCELL_MG_FROM_MGCL);
            } else {
                mg.add(wmd("magnesium_ingot"), Balance.MGCELL_MG_FROM_BRINE);
                if (++brineCycles >= Balance.MGCELL_LI_EVERY) {
                    brineCycles = 0;
                    if (li.fits(wmd("lithium_dust"), 1)) li.add(wmd("lithium_dust"), 1);
                }
            }
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "magnesium_ingot", "lithium_dust" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) cycleTicks(); }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.MAGNESIUM_CHLORIDE.source().get(), WmdFluids.BRINE.source().get(), WmdFluids.CHLORINE.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("mgcl2", "brine", "chlorine", "magnesium", "lithium"); }
    @Override protected int rateMbPerTick() { return Balance.MGCELL_MGCL_MB / Balance.MGCELL_CYCLE_TICKS; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return t < 3 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.MGCELL_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.mgcell_mgcl", Balance.MGCELL_MGCL_MB, Balance.MGCELL_MG_FROM_MGCL, Balance.MGCELL_CL_FROM_MGCL),
                Component.translatable("gui.wmd.info.mgcell_brine", Balance.MGCELL_BRINE_MB, Balance.MGCELL_MG_FROM_BRINE, Balance.MGCELL_BRINE_TICKS / 20),
                Component.translatable("gui.wmd.info.mgcell_lithium", Balance.MGCELL_LI_EVERY));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.MG_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return 0.5F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x4D47_0000_0000_0000L;
        double[] well = worldPoint(1.0, 1.15, -0.06), p0 = worldPoint(-0.78, 0.69, -0.34), p1 = worldPoint(0.09, 0.69, -0.34);
        VeilBridge.machineGlow(key, well[0], well[1], well[2], 1.0F, 0.5F, 0.15F, 3.0F, 1.3F + 0.15F * (float) Math.sin(t * 0.2), t);
        VeilBridge.machineGlow(key + 1, p0[0], p0[1], p0[2], 1.0F, 0.45F, 0.12F, 1.2F, 0.8F, t);
        VeilBridge.machineGlow(key + 2, p1[0], p1[1], p1[2], 1.0F, 0.45F, 0.12F, 1.2F, 0.8F, t);
        if (t % 6 != 0) return;
        var r = level.random;
        double[] w = worldPoint(1.0, 1.4, -0.06);
        level.addParticle(ParticleTypes.SMOKE, w[0], w[1], w[2], 0, 0.03, 0);
    }

    @Override
    protected String detail() { return "MgCl2 " + mb(mgcl) + ", brine " + mb(brine) + ", chlorine " + mb(chlorine) + ", magnesium " + mg.count() + ", lithium " + li.count(); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 0, Direction.WEST)) return brineIn;
        if (at(part, side, -1, 0, 1, Direction.SOUTH)) return mgclIn;
        if (at(part, side, 1, 0, 1, Direction.EAST)) return clOut;
        return null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 0, Direction.NORTH) ? mgOut : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putBoolean("fromMgcl", fromMgcl); tag.putInt("liCycles", brineCycles); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); fromMgcl = tag.getBoolean("fromMgcl"); brineCycles = tag.getInt("liCycles"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 2).fluid(-1, 0, 0, Direction.WEST, "brine", false).fluid(-1, 0, 1, Direction.SOUTH, "magnesium_chloride", false)
            .fluid(1, 0, 1, Direction.EAST, "chlorine", true).item(1, 0, 0, Direction.NORTH, true).power(0, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
