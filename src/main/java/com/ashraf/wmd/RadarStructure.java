package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class RadarStructure {

    public static final int HALF = 5;
    public static final int HEIGHT = 15;

    private static boolean unforming = false;

    private RadarStructure() {}

    public record Check(int wrong, BlockPos first) {
        public boolean complete() { return wrong == 0; }
    }

    private static boolean isFree(BlockState s) {
        return s.isAir() || s.canBeReplaced();
    }

    public static BlockPos at(BlockPos controller, Direction mount, int dx, int dy, int dz) { return Machines.toWorld(controller, Direction.NORTH, mount, dx, dy, dz); }

    public static Check check(Level level, BlockPos controller) { return check(level, controller, Direction.UP); }

    public static Check check(Level level, BlockPos controller, Direction mount) {
        int wrong = 0;
        BlockPos first = null;
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = at(controller, mount, dx, dy, dz);
                    if (level.isOutsideBuildHeight(p) || !isFree(level.getBlockState(p))) {
                        wrong++;
                        if (first == null) first = p;
                    }
                }
            }
        }

        net.minecraft.world.entity.Entity in = entityInside(level, controller, mount);
        if (in != null) {
            wrong++;
            if (first == null) first = in.blockPosition();
        }
        return new Check(wrong, first);
    }

    public static net.minecraft.world.entity.Entity entityInside(Level level, BlockPos controller) { return entityInside(level, controller, Direction.UP); }

    public static net.minecraft.world.entity.Entity entityInside(Level level, BlockPos controller, Direction mount) {
        return PlaceGuard.blocker(level, at(controller, mount, -HALF, 0, -HALF), at(controller, mount, HALF, HEIGHT - 1, HALF));
    }

    public static void form(Level level, BlockPos controller) {
        Direction mount = Machines.mount(level.getBlockState(controller));
        BlockState part = Wmd.RADAR_PART.get().defaultBlockState().setValue(MachineBlock.MOUNT, mount);
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    level.setBlock(at(controller, mount, dx, dy, dz), part, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        BlockState s = level.getBlockState(controller);
        level.setBlock(controller, s.setValue(RadarStationBlock.FORMED, true), Block.UPDATE_ALL);
    }

    public static void unform(Level level, BlockPos controller) {
        BlockState s = level.getBlockState(controller);
        if (s.getBlock() instanceof RadarStationBlock && s.getValue(RadarStationBlock.FORMED)) {
            level.setBlock(controller, s.setValue(RadarStationBlock.FORMED, false), Block.UPDATE_ALL);
        }
        removeParts(level, controller, Machines.mount(s));
    }

    public static void removeParts(Level level, BlockPos controller, Direction mount) {
        unforming = true;
        try {
            for (int dy = 0; dy < HEIGHT; dy++) {
                for (int dx = -HALF; dx <= HALF; dx++) {
                    for (int dz = -HALF; dz <= HALF; dz++) {
                        BlockPos p = at(controller, mount, dx, dy, dz);
                        if (level.getBlockState(p).getBlock() instanceof RadarPartBlock) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
        } finally {
            unforming = false;
        }
    }

    public static boolean isUnforming() { return unforming; }

    public static BlockPos findController(Level level, BlockPos pos) { return findController(level, pos, Machines.mount(level.getBlockState(pos))); }

    public static BlockPos findController(Level level, BlockPos pos, Direction mount) {
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    BlockPos c = at(pos, mount, -dx, -dy, -dz);
                    BlockState s = level.getBlockState(c);
                    if (s.getBlock() instanceof RadarStationBlock && s.getValue(RadarStationBlock.FORMED) && Machines.mount(s) == mount) return c;
                }
            }
        }
        return null;
    }

    public static IEnergyStorage energyAt(Level level, BlockPos partPos, net.minecraft.core.Direction side) {
        BlockPos c = findController(level, partPos);
        if (c == null) return null;

        BlockState cs = level.getBlockState(c);
        Direction facing = cs.hasProperty(MachineBlock.FACING) ? cs.getValue(MachineBlock.FACING) : Direction.NORTH;
        boolean edge = side != null && Machines.atPart(c, facing, Machines.mount(cs), partPos, side, 0, 0, HALF, Direction.SOUTH);
        return edge && level.getBlockEntity(c) instanceof RadarBlockEntity be ? be.getEnergy() : null;
    }

    public static void openScreen(Player player, Level level, BlockPos controller) {
        if (level.getBlockEntity(controller) instanceof RadarBlockEntity be) {
            if (be.getOwner() != null && !Access.mayUse(be, player)) { Access.deny(be, player); return; }
            if (be.getOwner() == null) be.claim(player);
            player.openMenu(new SimpleMenuProvider(
                            (id, inventory, p) -> new RadarMenu(id, inventory, be),
                            Component.translatable("block.wmd.radar_station")),
                    buf -> buf.writeBlockPos(controller));
        }
    }
}
