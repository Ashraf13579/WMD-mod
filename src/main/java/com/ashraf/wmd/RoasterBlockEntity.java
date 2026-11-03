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

public class RoasterBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank lpg = new FluidTank(Balance.ROAST_TANK_MB, fs -> fs.getFluid() == WmdFluids.LPG.source().get());
    private final Slot conc = new Slot(64, false, s -> s.is(wmd("tungsten_concentrate")));
    private final Slot oxide = new Slot(64, true, s -> false).chute(2, 0, 0, Direction.EAST);
    private final Slot[] slots = { conc, oxide };
    private final List<FluidTank> tanks = List.of(lpg, conc.gauge, oxide.gauge);
    private final IFluidHandler lpgIn = fillOnly(lpg);
    private final IItemHandler in = inHandler(conc), outH = outHandler(oxide);
    private int progress;

    public RoasterBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ROASTER_BE.get(), pos, state, Balance.ROAST_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (conc.count() < Balance.ROAST_CONC || lpg.getFluidAmount() < Balance.ROAST_LPG_MB) return fail(NO_INPUT);
            if (!oxide.fits(wmd("tungsten_oxide"), Balance.ROAST_OXIDE)) return fail(OUT_FULL);
            conc.take(Balance.ROAST_CONC);
            lpg.drain(Balance.ROAST_LPG_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.ROAST_CYCLE_TICKS) {
            progress = 0;
            oxide.add(wmd("tungsten_oxide"), Balance.ROAST_OXIDE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "tungsten_concentrate", "tungsten_oxide" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        return !in.isEmpty() && in.get(0).is(wmd("tungsten_concentrate")) ? new ItemStack(wmd("tungsten_oxide"), Balance.ROAST_OXIDE) : ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.ROAST_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.LPG.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("lpg", "concentrate", "oxide"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.ROAST_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.roaster", Balance.ROAST_CONC, Balance.ROAST_LPG_MB, Balance.ROAST_OXIDE, Balance.ROAST_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.RO_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(2, 0, 0); }
    @Override protected float soundVolume() { return 0.6F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x524F_0000_0000_0000L;
        double[] peep = worldPoint(1.98, 1.15, -0.45), sight = worldPoint(2.5, 1.125, 0.5), lamp = worldPoint(1.6, 0.89, -0.28);
        VeilBridge.machineGlow(key, peep[0], peep[1], peep[2], 1.0F, 0.5F, 0.15F, 3.0F, 1.2F + 0.2F * (float) Math.sin(t * 0.37), t);
        VeilBridge.machineGlow(key + 1, sight[0], sight[1], sight[2], 1.0F, 0.7F, 0.25F, 1.5F, 0.8F, t);
        VeilBridge.machineGlow(key + 2, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.4F, 0.8F, 0.5F, t);
        if (t % 7 != 0) return;
        var r = level.random;
        double[] stack = worldPoint(-1.95, 2.0, 0.825);
        level.addParticle(ParticleTypes.SMOKE, stack[0] + (r.nextDouble() - 0.5) * 0.1, stack[1], stack[2] + (r.nextDouble() - 0.5) * 0.1, 0, 0.04, 0);
    }

    @Override
    protected String detail() { return "LPG " + mb(lpg) + ", concentrate " + conc.count() + ", oxide " + oxide.count() + ", cycle " + progress * 100 / Balance.ROAST_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -2, 0, 1, Direction.SOUTH) ? lpgIn : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 2, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 2).item(-2, 0, 0, Direction.WEST, false).fluid(-2, 0, 1, Direction.SOUTH, "lpg", false)
            .item(2, 0, 0, Direction.EAST, true).power(1, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
