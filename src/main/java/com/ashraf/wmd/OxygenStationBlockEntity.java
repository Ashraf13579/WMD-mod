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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class OxygenStationBlockEntity extends ItemProcessBlockEntity implements CcAnimated {

    private final FluidTank water = new FluidTank(Balance.O2_STATION_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final Slot item = new Slot(1, false, s -> s.getItem() instanceof OxygenTankItem || s.getItem() == Wmd.EVA_TORSO.get());
    private final Slot[] slots = { item };
    private final List<FluidTank> tanks = List.of(water, item.gauge);
    private final IFluidHandler waterIn = fillOnly(water);
    private int ticks;
    private boolean clientItem;

    public OxygenStationBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.OXYGEN_STATION_BE.get(), pos, state, Balance.O2_STATION_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        ItemStack s = item.stack;
        if (s.isEmpty()) return fail(NO_INPUT);
        int o2 = EvaSuitItem.oxygen(s);
        if (o2 >= Balance.O2_FULL) return fail(OUT_FULL);
        if (++ticks >= Balance.O2_STATION_WATER_EVERY) {
            if (water.getFluidAmount() < Balance.O2_STATION_WATER_MB) { ticks--; return fail(NO_INPUT); }
            water.drain(Balance.O2_STATION_WATER_MB, IFluidHandler.FluidAction.EXECUTE);
            flowAcc += Balance.O2_STATION_WATER_MB;
            ticks = 0;
        }
        EvaSuitItem.setOxygen(s, o2 + Balance.O2_STATION_FILL);
        if (level != null && level.getGameTime() % 20 == 0) setChanged();
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "oxygen_tank" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return item.stack.isEmpty() ? 0.0F : EvaSuitItem.oxygen(item.stack) / (float) Balance.O2_FULL; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("water", "oxygen_item"); }
    @Override protected int rateMbPerTick() { return Math.max(1, Balance.O2_STATION_WATER_MB / Balance.O2_STATION_WATER_EVERY); }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.O2_STATION_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.oxygen_station", Balance.O2_STATION_FILL_TICKS / 20,
                        Balance.O2_FULL / Balance.O2_STATION_FILL / Balance.O2_STATION_WATER_EVERY * Balance.O2_STATION_WATER_MB),
                Component.translatable("gui.wmd.info.oxygen_station.item", item.stack.isEmpty() ? "-" : EvaSuitItem.clock(EvaSuitItem.oxygen(item.stack))));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.OR_O2_STATION_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 8 != 0) return;
        var r = level.random;
        double[] w = worldPoint(-0.28, 2.0, 0.19);
        level.addParticle(ParticleTypes.CLOUD, w[0] + (r.nextDouble() - 0.5) * 0.06, w[1], w[2] + (r.nextDouble() - 0.5) * 0.06, 0, 0.02, 0);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        final boolean lit = isRunning() && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        model.getBone("cell").ifPresent(b -> b.setHidden(!lit));
        model.getBone("tank").ifPresent(b -> b.setHidden(!clientItem));
    }

    @Override
    protected String detail() { return "water " + mb(water) + ", item " + (item.stack.isEmpty() ? "-" : EvaSuitItem.clock(EvaSuitItem.oxygen(item.stack))); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? waterIn : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.EAST) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("o2_ticks", ticks); }
    @Override protected void loadExtra(CompoundTag tag) { ticks = tag.getInt("o2_ticks"); clientItem = tag.getBoolean("o2_item"); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("o2_item", !item.stack.isEmpty());
        return tag;
    }

    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).fluid(0, 0, 0, Direction.SOUTH, "water", false).power(0, 0, 0, Direction.EAST, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
