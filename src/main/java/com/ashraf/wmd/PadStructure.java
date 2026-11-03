package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PadStructure {

    private PadStructure() {}

    public static int size(int tier) {
        return switch (tier) { case 1 -> 5; case 2 -> 9; case 3 -> 11; default -> 17; };
    }

    public static int maxRange(int tier) {
        return switch (tier) { case 1 -> 2000; case 2 -> 5000; case 3 -> 10000; default -> 20000; };
    }

    public static BlockPos cell(BlockPos terminal, Direction facing, int tier, int l, int d) { return cell(terminal, facing, Direction.UP, tier, l, d); }

    public static BlockPos cell(BlockPos terminal, Direction facing, Direction mount, int tier, int l, int d) {
        Direction right = Machines.dirToWorld(facing, mount, Direction.WEST);
        Direction inward = Machines.dirToWorld(facing, mount, Direction.SOUTH);
        return terminal.relative(right, l - size(tier) / 2).relative(inward, d);
    }

    private static BlockPos fillerCell(BlockPos terminal, Direction facing, Direction mount, int tier, Filler f) {
        return cell(terminal, facing, mount, tier, f.l(), f.d()).relative(mount, f.layer());
    }

    public record Filler(int l, int d, int layer, boolean step) {}

    private static boolean unformingNow;

    public static boolean unforming() { return unformingNow; }

    public static java.util.List<Filler> fillers(int tier) {
        java.util.List<Filler> out = new java.util.ArrayList<>();
        if (tier < 4) return out;
        int c = size(tier) / 2;
        for (int l = c - 6; l <= c + 6; l++) for (int d = 4; d <= 12; d++) { out.add(new Filler(l, d, 1, false)); out.add(new Filler(l, d, 2, false)); }
        for (int l = c - 1; l <= c + 1; l++) {
            out.add(new Filler(l, 2, 1, true));
            out.add(new Filler(l, 3, 1, false));
            out.add(new Filler(l, 3, 2, true));
        }
        return out;
    }

    public record Check(int wrong, BlockPos firstWrong) {
        public boolean complete() { return wrong == 0; }
    }

    public static Check check(Level level, BlockPos terminal, Direction facing, int tier) { return check(level, terminal, facing, Direction.UP, tier); }

    public static Check check(Level level, BlockPos terminal, Direction facing, Direction mount, int tier) {
        int n = size(tier), wrong = 0;
        BlockPos first = null;
        for (int d = 0; d < n; d++) {
            for (int l = 0; l < n; l++) {
                if (d == 0 && l == n / 2) continue;
                BlockPos p = cell(terminal, facing, mount, tier, l, d);
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof PadPlateBlock) || s.getValue(PadPlateBlock.FORMED)) {
                    wrong++;
                    if (first == null) first = p;
                }
            }
        }
        for (Filler f : fillers(tier)) {
            BlockPos p = fillerCell(terminal, facing, mount, tier, f);
            BlockState s = level.getBlockState(p);
            if (!(s.isAir() || s.canBeReplaced()) || PlaceGuard.blocker(level, p) != null) {
                wrong++;
                if (first == null) first = p;
            }
        }
        return new Check(wrong, first);
    }

    public static void form(Level level, BlockPos terminal, Direction facing, int tier) { form(level, terminal, facing, Direction.UP, tier); }

    public static void form(Level level, BlockPos terminal, Direction facing, Direction mount, int tier) {
        setPlates(level, terminal, facing, mount, tier, true);
        Direction up = facing.getOpposite();
        for (Filler f : fillers(tier)) {
            BlockPos p = fillerCell(terminal, facing, mount, tier, f);
            level.setBlock(p, Wmd.PAD_FILLER.get().defaultBlockState().setValue(PadFillerBlock.FACING, up).setValue(PadFillerBlock.STEP, f.step())
                    .setValue(MachineBlock.MOUNT, mount), 3);
        }
    }

    public static void unform(Level level, BlockPos terminal, Direction facing, int tier) { unform(level, terminal, facing, Direction.UP, tier); }

    public static void unform(Level level, BlockPos terminal, Direction facing, Direction mount, int tier) {
        unformingNow = true;
        try {
            for (Filler f : fillers(tier)) {
                BlockPos p = fillerCell(terminal, facing, mount, tier, f);
                if (level.getBlockState(p).getBlock() instanceof PadFillerBlock) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            setPlates(level, terminal, facing, mount, tier, false);
        } finally {
            unformingNow = false;
        }
    }

    private static void setPlates(Level level, BlockPos terminal, Direction facing, Direction mount, int tier, boolean formed) {
        int n = size(tier);
        for (int d = 0; d < n; d++) {
            for (int l = 0; l < n; l++) {
                if (d == 0 && l == n / 2) continue;
                BlockPos p = cell(terminal, facing, mount, tier, l, d);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof PadPlateBlock) {
                    level.setBlock(p, s.setValue(PadPlateBlock.FORMED, formed).setValue(MachineBlock.MOUNT, mount), 3);
                }
            }
        }
    }

    public static net.neoforged.neoforge.energy.IEnergyStorage energyAt(Level level, BlockPos plate) {
        BlockPos t = findOwner(level, plate);
        return t != null && level.getBlockEntity(t) instanceof LaunchTerminalBlockEntity be ? be.getEnergy() : null;
    }

    public static BlockPos findOwner(Level level, BlockPos plate) { return findOwner(level, plate, Machines.mount(level.getBlockState(plate))); }

    public static BlockPos findOwner(Level level, BlockPos plate, Direction mount) {
        Direction a = mount.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST, b = mount.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        for (int da = -17; da <= 17; da++) {
            for (int db = -17; db <= 17; db++) {
                BlockPos t = plate.relative(a, da).relative(b, db);
                BlockState s = level.getBlockState(t);
                if (!(s.getBlock() instanceof LaunchTerminalBlock term) || !s.getValue(LaunchTerminalBlock.FORMED) || Machines.mount(s) != mount) continue;
                int tier = term.getTier();
                Direction facing = s.getValue(LaunchTerminalBlock.FACING);
                int n = size(tier);
                for (int d = 0; d < n; d++) {
                    for (int l = 0; l < n; l++) {
                        if (cell(t, facing, mount, tier, l, d).equals(plate)) return t;
                    }
                }
            }
        }
        return null;
    }
}
