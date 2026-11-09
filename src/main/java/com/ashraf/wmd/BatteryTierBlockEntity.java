package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class BatteryTierBlockEntity extends ProcessUnitBlockEntity {

    public final int tier;
    private int stored;
    private int movedTicks;

    private int inTick, outTick, dir, dirHold, shownDir;
    private float fanSpin, fanSpeed;
    static final int DIR_HOLD = 20, FLOW_SCALE = 100;

    public BatteryTierBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int tier) {
        super(type, pos, state, 0);
        this.tier = tier;
    }

    public int cap() { return Balance.BAT_CAP[tier]; }
    public int stored() { return stored; }
    public int segments() { return tier == 1 ? 5 : tier == 4 ? 12 : 10; }

    void setStored(int e) { stored = Mth.clamp(e, 0, cap()); setChanged(); }

    @Override public void empDrain() { stored = 0; setChanged(); }

    private final IEnergyStorage own = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int n, boolean sim) {
            int t = Math.min(Math.min(n, Balance.BAT_IO[tier]), cap() - stored);
            if (t > 0 && !sim) { stored += t; inTick += t; movedTicks = 20; setChanged(); }
            return Math.max(0, t);
        }
        @Override
        public int extractEnergy(int n, boolean sim) {
            int t = Math.min(Math.min(n, Balance.BAT_IO[tier]), stored);
            if (t > 0 && !sim) { stored -= t; outTick += t; movedTicks = 20; setChanged(); }
            return Math.max(0, t);
        }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return cap(); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    };

    private Direction rowDir() { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.WEST); }

    private boolean sameRow(BlockPos p) {
        return level != null && level.getBlockEntity(p) instanceof BatteryTierBlockEntity o && o.tier == 1
                && o.getBlockState().getValue(MachineBlock.FACING) == facing() && Machines.mount(o.getBlockState()) == Machines.mount(getBlockState());
    }

    List<BatteryTierBlockEntity> row() {
        List<BatteryTierBlockEntity> l = new ArrayList<>();
        if (tier != 1 || level == null) { l.add(this); return l; }
        Direction d = rowDir();
        BlockPos start = worldPosition;
        for (int i = 1; i < Balance.BAT_T1_ROW && sameRow(start.relative(d.getOpposite())); i++) start = start.relative(d.getOpposite());
        BlockPos p = start;
        for (int i = 0; i < Balance.BAT_T1_ROW && sameRow(p); i++) { l.add((BatteryTierBlockEntity) level.getBlockEntity(p)); p = p.relative(d); }
        if (!l.contains(this)) { l.clear(); l.add(this); }
        return l;
    }

    private IEnergyStorage bank() {
        List<BatteryTierBlockEntity> mods = row();
        if (mods.size() == 1) return own;
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int n, boolean sim) { int got = 0; for (BatteryTierBlockEntity m : mods) { if (got >= n) break; got += m.own.receiveEnergy(n - got, sim); } return got; }
            @Override public int extractEnergy(int n, boolean sim) { int got = 0; for (BatteryTierBlockEntity m : mods) { if (got >= n) break; got += m.own.extractEnergy(n - got, sim); } return got; }
            @Override public int getEnergyStored() { long s = 0; for (BatteryTierBlockEntity m : mods) s += m.stored; return (int) Math.min(Integer.MAX_VALUE, s); }
            @Override public int getMaxEnergyStored() { long s = 0; for (BatteryTierBlockEntity m : mods) s += m.cap(); return (int) Math.min(Integer.MAX_VALUE, s); }
            @Override public boolean canExtract() { return true; }
            @Override public boolean canReceive() { return true; }
        };
    }

    @Override
    protected boolean work() {

        int moved = Math.max(inTick, outTick), net = inTick - outTick;
        flowAcc += Math.round(FLOW_SCALE * Math.min(1.0F, moved / (float) Math.max(1, Balance.BAT_IO[tier])));
        inTick = outTick = 0;
        int nd = net > 0 ? 1 : net < 0 ? -1 : dirHold > 0 ? dir : 0;
        if (net != 0) dirHold = DIR_HOLD; else if (dirHold > 0) dirHold--;
        if (nd != dir) { dir = nd; if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
        if (movedTicks > 0) { movedTicks--; return true; }
        return false;
    }
    @Override protected float levelFraction() { return cap() <= 0 ? 0.0F : stored / (float) cap(); }
    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return FLOW_SCALE; }
    @Override public IEnergyStorage guiEnergy() { return bank(); }
    @Override public int guiFeUse() { return 0; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.battery_tier", tier, String.format("%,d", cap()), String.format("%,d", Balance.BAT_IO[tier] * 20)),
                Component.translatable(tier == 1 ? "gui.wmd.info.battery_t1_row" : "gui.wmd.info.battery_alone", Balance.BAT_T1_ROW));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.BAT_HUM_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }

    @Override
    protected String detail() { return String.format("T%d %,d / %,d FE", tier, stored, cap()); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return switch (tier) {
            case 1 -> at(part, side, 0, 0, 0, Direction.SOUTH) || at(part, side, 0, 0, 0, Direction.EAST) || at(part, side, 0, 0, 0, Direction.WEST) ? bank() : null;
            case 2 -> at(part, side, 0, 0, 0, Direction.SOUTH) ? own : null;
            case 3 -> at(part, side, 0, 0, 1, Direction.SOUTH) ? own : null;
            default -> at(part, side, -1, 0, 1, Direction.SOUTH) ? own : null;
        };
    }

    private static final PortMap[] PORTS = {
            null,
            new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false),
            new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false),
            new PortMap(-1, 1, 2).power(0, 0, 1, Direction.SOUTH, false),
            new PortMap(-2, 2, 2).power(-1, 0, 1, Direction.SOUTH, false) };
    @Override public PortMap guiPortMap() { return PORTS[tier]; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("stored")) stored = Mth.clamp(tag.getInt("stored"), 0, cap());
        if (tag.contains("bdir")) shownDir = Mth.clamp(tag.getInt("bdir"), -1, 1);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("bdir", dir);
        return tag;
    }

    public int chargeDir() { return shownDir; }

    void clientFan() {
        float target = isRunning() ? 0.2F + 0.9F * flowFraction() : 0.0F;
        fanSpeed += (target - fanSpeed) * 0.05F;
        if (!isRunning() && fanSpeed < 0.002F) fanSpeed = 0.0F;
        fanSpin += fanSpeed;
        if (fanSpin > Mth.TWO_PI * 100) fanSpin -= Mth.TWO_PI * 100;
    }

    public float fanAngle(float partialTick) { return fanSpin + fanSpeed * partialTick; }

    public float shownFill() { return levelShown(); }

    @Override protected void clientEffects(Level level) {}
}
