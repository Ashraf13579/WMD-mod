package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class CokeOvenBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank tar = new FluidTank(Balance.CO_TAR_TANK_MB, fs -> fs.getFluid() == WmdFluids.COAL_TAR.source().get());
    private final Slot coal = new Slot(64, false, s -> s.is(Items.COAL));
    private final Slot coke = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.NORTH);
    private final Slot[] slots = { coal, coke };
    private final List<FluidTank> tanks = List.of(tar, coal.gauge, coke.gauge);
    private final IItemHandler in = inHandler(coal), outH = outHandler(coke);
    private final IFluidHandler tarOut = drainOnly(tar);
    private int progress;

    public CokeOvenBlockEntity(BlockPos pos, BlockState state) { super(Wmd.COKE_OVEN_BE.get(), pos, state, 1); }

    @Override protected int feCost() { return 0; }
    @Override public int guiFeUse() { return 0; }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (coal.count() < Balance.CO_COAL) return fail(NO_INPUT);
            if (!coke.fits(wmd("coke"), 1) || tar.getCapacity() - tar.getFluidAmount() < Balance.CO_TAR_MB) return fail(OUT_FULL);
            coal.take(Balance.CO_COAL);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.CO_CYCLE_TICKS) {
            progress = 0;
            coke.add(wmd("coke"), 1);
            tar.fill(new FluidStack(WmdFluids.COAL_TAR.source().get(), Balance.CO_TAR_MB), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }
        return true;
    }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.MUFFLER || (t == UpgradeItem.Type.AUTO_EJECT && super.acceptsUpgrade(t)); }

    @Override protected String[] iconIds() { return new String[] { "coke", "coke" }; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(ItemStack.EMPTY, new ItemStack(Items.COAL), new ItemStack(wmd("coke"))); }
    @Override public ItemStack guiPreview(List<ItemStack> in) { return !in.isEmpty() && in.get(0).is(Items.COAL) ? new ItemStack(wmd("coke")) : ItemStack.EMPTY; }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.CO_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.COAL_TAR.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("coal_tar", "fuel", "co_coke"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 0; }
    @Override public boolean guiTankOutput(int t) { return t != 1; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.coke_oven", Balance.CO_COAL, Balance.CO_TAR_MB, Balance.CO_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.coke_oven_use", Balance.BF_STEEL_COKE),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CO_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return 0.35F; }

    public static final double DOOR_FROM = 0.8;

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x434F_0000_0000_0000L;
        double[] vent = worldPoint(0.5, 2.0, 0.5), mouth = worldPoint(0.5, 0.65, -0.4);
        VeilBridge.machineGlow(key, mouth[0], mouth[1], mouth[2], 1.0F, 0.5F, 0.15F, 2.2F, 0.9F + 0.2F * (float) Math.sin(t * 0.29), t);
        var r = level.random;
        if (t % 4 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, vent[0] + (r.nextDouble() - 0.5) * 0.15, vent[1], vent[2] + (r.nextDouble() - 0.5) * 0.15,
                    (r.nextDouble() - 0.5) * 0.01, 0.05 + r.nextDouble() * 0.02, (r.nextDouble() - 0.5) * 0.01);
        if (t % 29 == 0) level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, vent[0], vent[1] + 0.3, vent[2], 0, 0.04, 0);
        if (t % 9 == 0) level.addParticle(ParticleTypes.SMALL_FLAME, vent[0] + (r.nextDouble() - 0.5) * 0.1, vent[1] + 0.02, vent[2] + (r.nextDouble() - 0.5) * 0.1, 0, 0.01, 0);
    }

    @Override
    protected String detail() { return "coal " + coal.count() + ", coke " + coke.count() + ", tar " + mb(tar) + ", batch " + progress * 100 / Balance.CO_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.EAST) ? tarOut : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 1, Direction.SOUTH)) return in;
        if (at(part, side, 1, 0, 0, Direction.NORTH)) return outH;
        return null;
    }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(0, 1, 2).item(0, 0, 1, Direction.SOUTH, false).item(1, 0, 0, Direction.NORTH, true)
            .fluid(1, 0, 1, Direction.EAST, "coal_tar", true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
