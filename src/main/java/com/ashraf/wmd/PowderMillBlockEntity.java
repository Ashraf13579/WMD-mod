package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class PowderMillBlockEntity extends ItemProcessBlockEntity {

    private final Slot nitrate = new Slot(64, false, s -> s.is(wmd("nitrate_dust")));
    private final Slot charcoal = new Slot(64, false, s -> s.is(Items.CHARCOAL));
    private final Slot out = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.EAST);
    private final Slot[] slots = { nitrate, charcoal, out };
    private final List<FluidTank> tanks = List.of(nitrate.gauge, charcoal.gauge, out.gauge);
    private final IItemHandler in = inHandler(nitrate, charcoal), outH = outHandler(out);
    private int progress;

    public PowderMillBlockEntity(BlockPos pos, BlockState state) { super(Wmd.POWDER_MILL_BE.get(), pos, state, Balance.PM_FE_PER_TICK); }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (nitrate.count() < Balance.PM_NITRATE || charcoal.count() < Balance.PM_CHARCOAL) return fail(NO_INPUT);
            if (!out.fits(Items.GUNPOWDER, Balance.PM_GUNPOWDER)) return fail(OUT_FULL);
            nitrate.take(Balance.PM_NITRATE);
            charcoal.take(Balance.PM_CHARCOAL);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.PM_CYCLE_TICKS) {
            progress = 0;
            out.add(Items.GUNPOWDER, Balance.PM_GUNPOWDER);
            setChanged();
        }
        return true;
    }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.MUFFLER || (t == UpgradeItem.Type.AUTO_EJECT && super.acceptsUpgrade(t)); }

    @Override protected String[] iconIds() { return new String[] { "nitrate_dust", "nitrate_dust", "nitrate_dust" }; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(wmd("nitrate_dust")), new ItemStack(Items.CHARCOAL), new ItemStack(Items.GUNPOWDER)); }
    @Override public ItemStack guiPreview(List<ItemStack> in) { return in.isEmpty() ? ItemStack.EMPTY : new ItemStack(Items.GUNPOWDER, Balance.PM_GUNPOWDER); }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.PM_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("pm_nitrate", "pm_charcoal", "pm_gunpowder"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.PM_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.powder_mill", Balance.PM_NITRATE, Balance.PM_CHARCOAL, Balance.PM_GUNPOWDER, Balance.PM_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.PM_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    private static final DustParticleOptions POWDER = new DustParticleOptions(new org.joml.Vector3f(0.18F, 0.18F, 0.2F), 1.0F);

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 3 != 0) return;
        var r = level.random;
        double a = r.nextDouble() * Math.PI * 2.0;
        double[] p = worldPoint(0.5 + Math.cos(a) * 0.38, 0.42, 0.5 + Math.sin(a) * 0.38);
        level.addParticle(POWDER, p[0], p[1], p[2], 0, 0.02, 0);
    }

    @Override
    protected String detail() { return "nitrate " + nitrate.count() + ", charcoal " + charcoal.count() + ", gunpowder " + out.count() + ", cycle " + progress * 100 / Balance.PM_CYCLE_TICKS + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 1, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(0, 1, 2).item(0, 0, 0, Direction.WEST, false).item(1, 0, 0, Direction.EAST, true)
            .power(1, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
