package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class BatteryBlockEntity extends BlockEntity implements EmpVulnerable {

    public static final int CAPACITY = 50_000_000;
    public static final int TRANSFER_PER_TICK = 100_000;
    public static final int MAX_ROW = 5;

    private int stored;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.BATTERY_BE.get(), pos, state);
    }

    public int getStored() { return stored; }

    @Override
    public void empDrain() { changeEnergy(-stored); }

    public void changeEnergy(int delta) {
        int next = Mth.clamp(stored + delta, 0, CAPACITY);
        if (next != stored) {
            stored = next;
            setChanged();
            updateIndicator();
        }
    }

    private static int chargeLevel(int stored) {
        return stored <= 0 ? 0 : 1 + (int) ((long) (stored - 1) * 4 / CAPACITY);
    }

    private void updateIndicator() {
        if (level == null || level.isClientSide) return;
        BlockPos top = worldPosition.relative(Machines.mount(getBlockState()));
        BlockState state = level.getBlockState(top);
        int wanted = chargeLevel(stored);
        if (state.getBlock() instanceof BatteryBlock && state.getValue(BatteryBlock.PART) == BatteryBlock.Part.FRONT_TOP
                && state.getValue(BatteryBlock.CHARGE) != wanted) {
            level.setBlock(top, state.setValue(BatteryBlock.CHARGE, wanted), 3);
        }
    }

    public static List<BatteryBlockEntity> row(Level level, BlockPos main, Direction facing, Direction mount) {
        Direction dir = BatteryBlock.rowDirection(facing, mount);
        BlockPos start = main;
        for (int i = 0; i < MAX_ROW; i++) {
            BlockPos prev = start.relative(dir.getOpposite());
            if (!BatteryBlock.isMain(level, prev, facing, mount)) break;
            start = prev;
        }
        List<BatteryBlockEntity> list = new ArrayList<>();
        BlockPos p = start;
        for (int i = 0; i < MAX_ROW && BatteryBlock.isMain(level, p, facing, mount); i++) {
            if (level.getBlockEntity(p) instanceof BatteryBlockEntity be) list.add(be);
            p = p.relative(dir);
        }
        return list;
    }

    private boolean migrated;

    void migrateToT2() {
        if (migrated || level == null || level.isClientSide) return;
        migrated = true;
        Level lv = level;
        BlockState st = getBlockState();
        Direction facing = st.getValue(BatteryBlock.FACING), mount = Machines.mount(st);
        int keep = Math.min(stored, Balance.BAT_CAP[2]);
        BlockPos main = worldPosition;
        for (BatteryBlock.Part part : BatteryBlock.Part.values()) {
            BlockPos p = BatteryBlock.cell(main, facing, mount, part);
            if (lv.getBlockState(p).getBlock() instanceof BatteryBlock) lv.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
        }
        BlockState t2 = Wmd.BATTERY_T2.get().defaultBlockState().setValue(MachineBlock.FACING, facing);
        if (t2.hasProperty(MachineBlock.MOUNT)) t2 = t2.setValue(MachineBlock.MOUNT, mount);
        lv.setBlock(main, t2, 3);
        t2.getBlock().setPlacedBy(lv, main, t2, null, net.minecraft.world.item.ItemStack.EMPTY);
        if (lv.getBlockEntity(main) instanceof BatteryTierBlockEntity be) be.setStored(keep);

        for (BatteryBlock.Part part : new BatteryBlock.Part[] { BatteryBlock.Part.BACK_BOTTOM, BatteryBlock.Part.BACK_TOP }) {
            BlockPos p = BatteryBlock.cell(main, facing, mount, part);
            if (lv.getBlockState(p).isAir()) lv.setBlock(p, Wmd.BUSBAR.get().linkedAt(lv, p), 3);
        }
        Wmd.LOGGER.info("[battery] old module at {} became a T2 cabinet ({} of {} FE kept)", main, keep, stored);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("stored")) {
            stored = Mth.clamp(tag.getInt("stored"), 0, CAPACITY);
        } else if (tag.get("energy") instanceof IntTag old) {
            stored = Mth.clamp(old.getAsInt(), 0, CAPACITY);
        }
    }

    public static class Bank implements IEnergyStorage {
        private final List<BatteryBlockEntity> modules;

        public Bank(List<BatteryBlockEntity> modules) {
            this.modules = modules;
        }

        private int[] split(int amount, int[] weights) {
            int[] share = new int[weights.length];
            long total = 0;
            for (int w : weights) total += w;
            if (total <= 0 || amount <= 0) return share;
            int assigned = 0;
            for (int i = 0; i < weights.length; i++) {
                share[i] = (int) ((long) amount * weights[i] / total);
                assigned += share[i];
            }
            int left = amount - assigned;
            for (int i = 0; i < weights.length && left > 0; i++) {
                int add = Math.min(weights[i] - share[i], left);
                share[i] += add;
                left -= add;
            }
            return share;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int n = modules.size();
            if (n == 0 || toReceive <= 0) return 0;
            int[] space = new int[n];
            long totalSpace = 0;
            for (int i = 0; i < n; i++) {
                space[i] = CAPACITY - modules.get(i).getStored();
                totalSpace += space[i];
            }
            int give = (int) Math.min(Math.min((long) toReceive, (long) TRANSFER_PER_TICK * n), totalSpace);
            if (!simulate && give > 0) {
                int[] share = split(give, space);
                for (int i = 0; i < n; i++) modules.get(i).changeEnergy(share[i]);
            }
            return give;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int n = modules.size();
            if (n == 0 || toExtract <= 0) return 0;
            int[] have = new int[n];
            long totalStored = 0;
            for (int i = 0; i < n; i++) {
                have[i] = modules.get(i).getStored();
                totalStored += have[i];
            }
            int take = (int) Math.min(Math.min((long) toExtract, (long) TRANSFER_PER_TICK * n), totalStored);
            if (!simulate && take > 0) {
                int[] share = split(take, have);
                for (int i = 0; i < n; i++) modules.get(i).changeEnergy(-share[i]);
            }
            return take;
        }

        @Override
        public int getEnergyStored() {
            int sum = 0;
            for (BatteryBlockEntity m : modules) sum += m.getStored();
            return sum;
        }

        @Override
        public int getMaxEnergyStored() {
            return CAPACITY * modules.size();
        }

        @Override
        public boolean canExtract() { return true; }

        @Override
        public boolean canReceive() { return true; }
    }
}
