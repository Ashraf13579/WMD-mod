package com.ashraf.wmd;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class StampMillBlockEntity extends ItemProcessBlockEntity {

    private static final Map<Item, String> CRUSH = Map.of(Items.RAW_IRON, "crushed_iron", Items.RAW_COPPER, "crushed_copper", Items.RAW_GOLD, "crushed_gold");
    private static final Map<String, String> CRUSH_WMD = Map.of("raw_tin", "crushed_tin", "raw_lead", "crushed_lead");

    private final Slot ore = new Slot(64, false, s -> crushedOf(s) != null);
    private final Slot out = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.NORTH);
    private final Slot[] slots = { ore, out };
    private final List<FluidTank> tanks = List.of(ore.gauge, out.gauge);
    private final IItemHandler in = inHandler(ore), outH = outHandler(out);
    private int progress;

    public StampMillBlockEntity(BlockPos pos, BlockState state) { super(Wmd.STAMP_MILL_BE.get(), pos, state, Balance.SM_FE_PER_TICK); }

    static Item crushedOf(ItemStack s) {
        if (s.isEmpty()) return null;
        String id = CRUSH.get(s.getItem());
        if (id == null) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
            if (Wmd.MODID.equals(key.getNamespace())) id = CRUSH_WMD.get(key.getPath());
        }
        return id == null ? null : wmd(id);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            Item c = crushedOf(ore.stack);
            if (c == null) return fail(NO_INPUT);
            if (!out.fits(c, Balance.SM_YIELD)) return fail(OUT_FULL);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.SM_CYCLE_TICKS) {
            Item c = crushedOf(ore.stack);
            progress = 0;
            if (c != null && out.fits(c, Balance.SM_YIELD)) { ore.take(1); out.add(c, Balance.SM_YIELD); }
            setChanged();
        }
        return true;
    }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.MUFFLER || (t == UpgradeItem.Type.AUTO_EJECT && super.acceptsUpgrade(t)); }

    @Override protected String[] iconIds() { return new String[] { "crushed_iron", "crushed_iron" }; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(Items.RAW_IRON), new ItemStack(wmd("crushed_iron"))); }
    @Override public ItemStack guiPreview(List<ItemStack> in) { Item c = in.isEmpty() ? null : crushedOf(in.get(0)); return c == null ? ItemStack.EMPTY : new ItemStack(c, Balance.SM_YIELD); }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.SM_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("raw_ore", "sm_crushed"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.SM_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.stamp_mill", Balance.SM_YIELD, Balance.SM_CYCLE_TICKS / 20.0F),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.SM_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    public static final int CAM_LOOP = 24;

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime();
        var r = level.random;
        for (int k = 0; k < 3; k++) {
            if ((t + k * CAM_LOOP / 3) % CAM_LOOP != Math.round(CAM_LOOP * 0.68F)) continue;
            double[] die = worldPoint(k * 0.4375, 0.78, -0.125);
            for (int i = 0; i < 4; i++)
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), die[0] + (r.nextDouble() - 0.5) * 0.25, die[1],
                        die[2] + (r.nextDouble() - 0.5) * 0.25, (r.nextDouble() - 0.5) * 0.1, 0.1, (r.nextDouble() - 0.5) * 0.1);
            if (mufflerFactor() > 0)
                level.playLocalSound(die[0], die[1], die[2], WmdSounds.SM_STAMP.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                        0.08F * mufflerFactor(), 0.55F + r.nextFloat() * 0.1F, false);
        }
    }

    @Override
    protected String detail() { return "ore " + ore.count() + ", crushed " + out.count() + ", cycle " + progress * 100 / Balance.SM_CYCLE_TICKS + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 0, Direction.SOUTH)) return in;
        if (at(part, side, 1, 0, 0, Direction.NORTH)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 0, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(0, 1, 1).item(0, 0, 0, Direction.SOUTH, false).item(1, 0, 0, Direction.NORTH, true)
            .power(1, 0, 0, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
