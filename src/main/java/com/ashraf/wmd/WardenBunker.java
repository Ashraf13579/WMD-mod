package com.ashraf.wmd;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class WardenBunker {

    private WardenBunker() {}

    public static final int F = -35;
    static final int HALL = 28, WALL = 3, HALL_H = 26;
    static final int SHAFT = 10;
    static final int PIT = 9, PIT_D = 20;
    static final int DOOR = 8, DOOR_H = 16, DOOR_Z = -(HALL + 2);
    static final int BAY = 16, BAY_Z_HALL = -(HALL + WALL + 1), BAY_Z_FAR = -52, BAY_H = 18;
    static final int TUN = 3, TUN_H = 6, TUN_Z0 = BAY_Z_FAR - WALL - 1;
    static final int TUN_STEPS = -F - 1;
    static final int TUN_Z_END = TUN_Z0 - TUN_STEPS;

    public static final int[][] NODES = {{HALL + 1, F + 10, -14}, {HALL + 1, F + 10, 14}, {-(HALL + 1), F + 10, -14}, {-(HALL + 1), F + 10, 14}};

    public static final int X0 = -(HALL + WALL), X1 = HALL + WALL, Z0 = TUN_Z_END - WALL, Z1 = HALL + WALL, H0 = F - PIT_D - 2, H1 = 9;

    public static final int WARDEN_H = F - PIT_D + 1;
    public static final int BAY_SPAWN_Z = -42;

    private static final class P {
        static final BlockState AIR = Blocks.AIR.defaultBlockState();
        static final BlockState CONCRETE = Wmd.REINFORCED_CONCRETE.get().defaultBlockState();
        static final BlockState PLATE = Wmd.ARMOUR_PLATE.get().defaultBlockState();
        static final BlockState GLASS = Wmd.BLAST_GLASS.get().defaultBlockState();
        static final BlockState SKIRT = Blocks.DEEPSLATE_TILES.defaultBlockState();
        static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
        static final BlockState DESK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        static final BlockState TERMINAL = Wmd.WARDEN_TERMINAL.get().defaultBlockState().setValue(WardenTallBlock.FACING, Direction.NORTH);
        static final BlockState TERMINAL_UP = TERMINAL.setValue(WardenTallBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);

        static final BlockState DRUM = byId("hangar_door") instanceof HangarDoorBlock b ? b.defaultBlockState().setValue(HangarDoorBlock.FACING, Direction.NORTH) : null;
        static final BlockState CURTAIN = byId("hangar_curtain") instanceof HangarCurtainBlock b ? b.defaultBlockState().setValue(HangarCurtainBlock.FACING, Direction.NORTH)
                .setValue(HangarCurtainBlock.DRAWN, false) : null;

        private static net.minecraft.world.level.block.Block byId(String id) {
            return AirGate.AVAILABLE ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)) : null;
        }
        static final BlockState RAM_HOUSING = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        static final BlockState RAM_ROD = Blocks.IRON_BLOCK.defaultBlockState();
        static final BlockState STAIR = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        static final BlockState CRACKED = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        static final BlockState FOOTING = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        static final BlockState COLLAR = Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState();
        static final BlockState MAST = Blocks.IRON_BARS.defaultBlockState();
        static final BlockState ROD = Blocks.LIGHTNING_ROD.defaultBlockState();
        static final BlockState VENT_FIRE = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true);
        static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();
        static final BlockState NODE_E = Wmd.WARDEN_NODE.get().defaultBlockState().setValue(WardenTallBlock.FACING, Direction.WEST);
        static final BlockState NODE_W = Wmd.WARDEN_NODE.get().defaultBlockState().setValue(WardenTallBlock.FACING, Direction.EAST);
        static final BlockState NODE_E_UP = NODE_E.setValue(WardenTallBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        static final BlockState NODE_W_UP = NODE_W.setValue(WardenTallBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
    }

    public static BlockState at(int x, int h, int z) {
        if (x < X0 || x > X1 || z < Z0 || z > Z1 || h < H0 || h > H1) return null;
        BlockState s = signs(x, h, z);
        if (s != null) return s;
        if (z <= TUN_Z0 && z >= Z0) return tunnel(x, h, z);
        if (z >= -(HALL + WALL)) return hall(x, h, z);
        return bay(x, h, z);
    }

    private static int ring(int x, int z) { return Math.max(Math.abs(x), Math.abs(z)); }

    static final int PILLAR_H = 7;
    static final int[][] PILLARS = {{26, 14}, {26, -14}, {-26, 14}, {-26, -14}, {14, 26}, {-14, 26}, {20, -26}, {-20, -26}};

    static boolean pillar(int x, int z) {
        for (int[] p : PILLARS) if (Math.abs(x - p[0]) <= 1 && Math.abs(z - p[1]) <= 1) return true;
        return false;
    }

    private static int hash(int x, int h, int z) {
        int v = x * 73_856_093 ^ h * 19_349_663 ^ z * 83_492_791;
        return (v ^ (v >>> 13)) & 0x7fffffff;
    }

    private static BlockState hall(int x, int h, int z) {
        int r = ring(x, z);
        if (r > HALL + WALL) return null;

        if (h < F - 2) {
            if (h < F - PIT_D - 2 || r > PIT + WALL) return null;
            if (r == PIT + 1 && h >= F - PIT_D + 1 && (Math.abs(x) <= 1 || Math.abs(z) <= 1))
                return h <= F - 11 ? P.RAM_HOUSING : P.RAM_ROD;
            if (r > PIT || h <= F - PIT_D - 1) return P.CONCRETE;
            if (h == F - PIT_D) return P.PLATE;
            return P.AIR;
        }
        if (r <= PIT && h <= F) {
            if (h == F) return P.PLATE;
            return P.AIR;
        }
        if (h > F + HALL_H + WALL) {
            if (h > -1 || r > SHAFT + WALL) return null;
            if (r > SHAFT) return P.CONCRETE;
            return h >= -2 ? P.PLATE : P.AIR;
        }
        boolean wall = r > HALL || h < F + 1 || h > F + HALL_H;
        if (!wall) return h <= F + PILLAR_H && pillar(x, z) ? P.CONCRETE : P.AIR;

        if (h > F + HALL_H && r <= SHAFT) return P.AIR;

        if (z <= -(HALL + 1) && Math.abs(x) <= HALL) {
            if (Math.abs(x) <= DOOR && h >= F + 1 && h <= F + DOOR_H) {
                if (z != DOOR_Z) return P.AIR;
                if (P.CURTAIN == null) return P.PLATE;
                return P.CURTAIN.setValue(HangarCurtainBlock.EDGE, x == -DOOR ? 1 : x == DOOR ? 2 : 0).setValue(HangarCurtainBlock.BOTTOM, h == F + 1);
            }
            if (Math.abs(x) <= DOOR && h == F + DOOR_H + 1 && z == DOOR_Z && P.DRUM != null) return P.DRUM;
            if (x >= 11 && x <= 15 && h >= F + 2 && h <= F + 5) return P.GLASS;
        }

        if (Math.abs(x) == HALL + 1 && Math.abs(z) == 14 && (h == F + 10 || h == F + 11))
            return x > 0 ? (h == F + 10 ? P.NODE_E : P.NODE_E_UP) : (h == F + 10 ? P.NODE_W : P.NODE_W_UP);

        if (r == HALL + 1 && h > F && h <= F + HALL_H) {
            int along = Math.abs(x) == HALL + 1 ? z : x;
            if ((h == F + 8 || h == F + 20) && Math.floorMod(along, 6) == 0) return P.LIGHT;
            if (h <= F + 2) return P.SKIRT;
        }
        if (h == F + HALL_H + 1 && r <= HALL && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) return P.LIGHT;

        if (h == F && r == PIT + 3 && Math.floorMod(x + z, 3) == 0) return P.LIGHT;
        return P.CONCRETE;
    }

    private static BlockState bay(int x, int h, int z) {
        if (Math.abs(x) > BAY + WALL || z < BAY_Z_FAR - WALL || h < F - 2 || h > F + BAY_H + WALL) return null;
        boolean inside = Math.abs(x) <= BAY && z >= BAY_Z_FAR && h >= F + 1 && h <= F + BAY_H;
        if (inside) {

            if (z == -36 && x >= 11 && x <= 15 && h == F + 1) return P.DESK;
            if (z == -36 && x == 13 && h == F + 2) return P.TERMINAL;
            if (z == -36 && x == 13 && h == F + 3) return P.TERMINAL_UP;
            return P.AIR;
        }

        if (z < BAY_Z_FAR && Math.abs(x) <= TUN && h >= F + 1 && h <= F + TUN_H) return P.AIR;
        if (h == F + BAY_H + 1 && Math.abs(x) <= BAY && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 5) == 0) return P.LIGHT;
        if (Math.abs(x) == BAY + 1 && h == F + 10 && Math.floorMod(z, 5) == 0) return P.LIGHT;
        return P.CONCRETE;
    }

    private static BlockState tunnel(int x, int h, int z) {
        if (Math.abs(x) > TUN + WALL) return null;
        if (z < TUN_Z_END) {
            if (z < TUN_Z_END - WALL + 1 || Math.abs(x) <= TUN || h != 0) return null;
            return hash(x, h, z) % 3 == 0 ? P.MOSSY : P.CRACKED;
        }
        int k = TUN_Z0 - z;
        int s = F + 1 + k;
        boolean covered = s + TUN_H + WALL <= 0;
        if (Math.abs(x) <= TUN) {
            if (h < s - 3) return null;
            if (h < s) return P.CONCRETE;
            if (h == s) return P.STAIR;
            if (h <= s + TUN_H || !covered && h <= 2) return P.AIR;
            if (covered && h <= s + TUN_H + WALL) return P.CONCRETE;
            return null;
        }

        if (h < s - 3) return null;
        if (covered) {
            if (h > s + TUN_H + WALL) return null;
            if (h == s + 4 && Math.abs(x) == TUN + 1 && Math.floorMod(k, 6) == 3) return P.LIGHT;
            return P.CONCRETE;
        }
        if (h > 0) return null;
        return hash(x, h, z) % 3 == 0 ? P.MOSSY : P.CRACKED;
    }

    private static BlockState signs(int x, int h, int z) {
        if (h < -3) return null;
        BlockState m = mast(x, h, z, 22, 10, 8);
        if (m != null) return m;
        m = mast(x, h, z, -24, 20, 5);
        if (m != null) return m;
        BlockState v = vent(x, h, z, 20, -18);
        if (v != null) return v;
        return vent(x, h, z, -20, -18);
    }

    private static BlockState mast(int x, int h, int z, int mx, int mz, int tall) {
        if (x != mx || z != mz || h > tall + 1) return null;
        if (h < 0) return P.FOOTING;
        return h <= tall ? P.MAST : P.ROD;
    }

    private static BlockState vent(int x, int h, int z, int vx, int vz) {
        int dx = Math.abs(x - vx), dz = Math.abs(z - vz);
        if (dx > 1 || dz > 1 || h > 0) return null;
        boolean centre = dx == 0 && dz == 0;
        if (h == 0) return centre ? P.AIR : P.COLLAR;
        if (centre) return h == -1 ? P.VENT_FIRE : h == -2 ? P.HAY : P.FOOTING;
        return P.FOOTING;
    }
}
