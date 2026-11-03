package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ReactorStructure {

    private ReactorStructure() {}

    public static final int X0 = -4, X1 = 4, Y0 = 0, Y1 = 9, Z0 = 0, Z1 = 8;

    public static final int CTRL_X = 0, CTRL_Y = 1, CTRL_Z = 0;

    private static final String[] BASE = {
            "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP" };
    private static final String[] FLOOR = {
            "I...K...F",
            ".........",
            "..C...C..",
            "...SSS...",
            "W..SSS..W",
            "...SSS...",
            "..C...C..",
            ".........",
            "O...E...O" };
    private static final String[] MID = {
            "F...F...F",
            ".........",
            ".........",
            "...SSS...",
            "F..SSS..F",
            "...SSS...",
            ".........",
            ".........",
            "F...F...F" };
    private static final String[] CROWN = {
            "FFFFFFFFF",
            "F.......F",
            "F.......F",
            "F..RRR..F",
            "F..RRR..F",
            "F..RRR..F",
            "F.......F",
            "F.......F",
            "FFFFFFFFF" };

    private static final String[][] LAYERS = { BASE, FLOOR, MID, MID, MID, MID, MID, MID, MID, CROWN };

    public static char at(int x, int y, int z) {
        if (x < X0 || x > X1 || y < Y0 || y > Y1 || z < Z0 || z > Z1) return ' ';
        return LAYERS[y][z].charAt(x - X0);
    }

    public static Block blockFor(char c) {
        return switch (c) {
            case 'P' -> Wmd.REACTOR_PLATING.get();
            case 'F' -> Wmd.REACTOR_FRAME.get();
            case 'S' -> Wmd.REACTOR_CORE_SHIELD.get();
            case 'R' -> Wmd.CONTROL_ROD_DRIVE.get();
            case 'C' -> Wmd.COOLANT_PUMP.get();
            case 'K' -> Wmd.NUCLEAR_REACTOR.get();
            case 'I' -> Wmd.REACTOR_FUEL_PORT.get();
            case 'W' -> Wmd.REACTOR_WATER_PORT.get();
            case 'O' -> Wmd.REACTOR_STEAM_PORT.get();
            case 'E' -> Wmd.REACTOR_POWER_PORT.get();
            default -> null;
        };
    }

    public static java.util.Map<Character, Integer> counts() {
        java.util.Map<Character, Integer> m = new java.util.TreeMap<>();
        for (int y = Y0; y <= Y1; y++) for (int z = Z0; z <= Z1; z++) for (int x = X0; x <= X1; x++) {
            char c = at(x, y, z);
            if (c != '.') m.merge(c, 1, Integer::sum);
        }
        return m;
    }

    public static BlockPos world(BlockPos ctrl, Direction facing, int x, int y, int z) { return world(ctrl, facing, Direction.UP, x, y, z); }

    public static BlockPos world(BlockPos ctrl, Direction facing, Direction mount, int x, int y, int z) {
        return Machines.toWorld(ctrl, facing, mount, x - CTRL_X, y - CTRL_Y, z - CTRL_Z);
    }

    public static BlockPos structureOf(BlockPos ctrl, Direction facing, BlockPos world) { return structureOf(ctrl, facing, Direction.UP, world); }

    public static BlockPos structureOf(BlockPos ctrl, Direction facing, Direction mount, BlockPos world) {
        BlockPos l = Machines.toLocal(ctrl, facing, mount, world);
        return new BlockPos(l.getX() + CTRL_X, l.getY() + CTRL_Y, l.getZ() + CTRL_Z);
    }

    public static List<BlockPos> partCells() {
        List<BlockPos> l = new ArrayList<>();
        for (int y = Y0; y <= Y1; y++) for (int z = Z0; z <= Z1; z++) for (int x = X0; x <= X1; x++) {
            char c = at(x, y, z);
            if (c != '.' && c != 'K') l.add(new BlockPos(x, y, z));
        }
        return l;
    }

    public record Port(char kind, int x, int y, int z, Direction side) {}

    private static List<Port> ports;

    public static List<Port> ports() {
        if (ports != null) return ports;
        List<Port> l = new ArrayList<>();
        for (int y = Y0; y <= Y1; y++) for (int z = Z0; z <= Z1; z++) for (int x = X0; x <= X1; x++) {
            char c = at(x, y, z);
            if (c != 'I' && c != 'W' && c != 'O' && c != 'E') continue;
            int lx = x - CTRL_X, ly = y - CTRL_Y, lz = z - CTRL_Z;
            if (x == X0) l.add(new Port(c, lx, ly, lz, Direction.WEST));
            if (x == X1) l.add(new Port(c, lx, ly, lz, Direction.EAST));
            if (z == Z0) l.add(new Port(c, lx, ly, lz, Direction.NORTH));
            if (z == Z1) l.add(new Port(c, lx, ly, lz, Direction.SOUTH));
        }
        ports = List.copyOf(l);
        return ports;
    }

    private static List<BlockPos> partCells;
    private static List<BlockPos> parts() { if (partCells == null) partCells = partCells(); return partCells; }

    public record Check(int wrong, BlockPos first, Component expected) {
        public boolean complete() { return wrong == 0; }
    }

    public static Check check(Level level, BlockPos ctrl, Direction facing) { return check(level, ctrl, facing, Direction.UP); }

    public static Check check(Level level, BlockPos ctrl, Direction facing, Direction mount) {
        int wrong = 0;
        BlockPos first = null;
        Component exp = null;
        for (int y = Y0; y <= Y1; y++) for (int z = Z0; z <= Z1; z++) for (int x = X0; x <= X1; x++) {
            char c = at(x, y, z);
            if (c == 'K') continue;
            BlockPos p = world(ctrl, facing, mount, x, y, z);
            BlockState s = level.getBlockState(p);
            boolean ok;
            if (c == '.') ok = s.isAir();
            else ok = s.is(blockFor(c)) && !(s.hasProperty(ReactorPartBlock.FORMED) && s.getValue(ReactorPartBlock.FORMED));
            if (!ok) {
                wrong++;
                if (first == null) { first = p; exp = c == '.' ? Component.translatable("message.wmd.reactor_form.air") : blockFor(c).getName(); }
            }
        }
        return new Check(wrong, first, exp);
    }

    public static void form(Level level, BlockPos ctrl, Direction facing, Direction mount) {
        for (BlockPos c : parts()) {
            BlockPos p = world(ctrl, facing, mount, c.getX(), c.getY(), c.getZ());
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(ReactorPartBlock.FORMED)) level.setBlock(p, s.setValue(ReactorPartBlock.FORMED, true), 3);
            if (level.getBlockEntity(p) instanceof ReactorPortBlockEntity port) port.link(ctrl);
            level.invalidateCapabilities(p);
        }
    }

    private static boolean unformingNow;

    public static boolean busy() { return unformingNow; }

    public static void unform(Level level, BlockPos ctrl, Direction facing, Direction mount) {
        unformingNow = true;
        try {
            for (BlockPos c : parts()) {
                BlockPos p = world(ctrl, facing, mount, c.getX(), c.getY(), c.getZ());
                BlockState s = level.getBlockState(p);
                if (s.hasProperty(ReactorPartBlock.FORMED) && s.getValue(ReactorPartBlock.FORMED)) level.setBlock(p, s.setValue(ReactorPartBlock.FORMED, false), 3);
                if (level.getBlockEntity(p) instanceof ReactorPortBlockEntity port) port.link(null);
                level.invalidateCapabilities(p);
            }
        } finally {
            unformingNow = false;
        }
    }

    public static void destroy(Level level, BlockPos ctrl, Direction facing, Direction mount) {
        unformingNow = true;
        try {
            for (BlockPos c : parts()) {
                BlockPos p = world(ctrl, facing, mount, c.getX(), c.getY(), c.getZ());
                if (level.getBlockState(p).getBlock() instanceof ReactorPartBlock) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        } finally {
            unformingNow = false;
        }
    }

    public static BlockPos findController(Level level, BlockPos part, boolean formed) {
        int reach = Math.max(Math.max(X1 - X0, Z1 - Z0), Y1 - CTRL_Y);
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos c = part.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(c);
                    if (!(s.getBlock() instanceof NuclearReactorBlock) || s.getValue(NuclearReactorBlock.FORMED) != formed) continue;
                    BlockPos st = structureOf(c, s.getValue(NuclearReactorBlock.FACING), Machines.mount(s), part);
                    char ch = at(st.getX(), st.getY(), st.getZ());
                    if (ch != ' ' && ch != '.' && ch != 'K') return c;
                }
            }
        }
        return null;
    }

    public static void tryFormNear(Level level, BlockPos placed) {
        BlockState ps = level.getBlockState(placed);
        BlockPos ctrl = ps.getBlock() instanceof NuclearReactorBlock ? placed : findController(level, placed, false);
        if (ctrl == null) return;
        BlockState cs = level.getBlockState(ctrl);
        if (cs.getBlock() instanceof NuclearReactorBlock rb && check(level, ctrl, cs.getValue(NuclearReactorBlock.FACING), Machines.mount(cs)).complete()) rb.form(level, ctrl, null);
    }
}
