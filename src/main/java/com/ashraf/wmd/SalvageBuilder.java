package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.core.Direction.Axis;

public final class SalvageBuilder {

    private SalvageBuilder() {}

    public static final int LIMIT = 15;

    static ResourceKey<LootTable> loot(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chests/" + name));
    }

    public static boolean build(WorldGenLevel level, SalvageSites.Kind kind, int cx, int cz, RandomSource r) {
        int radius = kind == SalvageSites.Kind.SATELLITE ? 14 : kind == SalvageSites.Kind.RADAR_STATION ? 13 : 12;
        int by = floorLevel(level, cx, cz, radius);
        if (by == Integer.MIN_VALUE) return false;
        return construct(new Ctx(level, cx, by, cz, r, Heightmap.Types.OCEAN_FLOOR_WG), kind, radius);
    }

    public static boolean buildAt(WorldGenLevel level, SalvageSites.Kind kind, int cx, int by, int cz, RandomSource r) {
        int radius = kind == SalvageSites.Kind.SATELLITE ? 14 : kind == SalvageSites.Kind.RADAR_STATION ? 13 : 12;
        return construct(new Ctx(level, cx, by, cz, r, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES), kind, radius);
    }

    private static boolean construct(Ctx c, SalvageSites.Kind kind, int radius) {
        c.prepare(radius, kind == SalvageSites.Kind.SATELLITE);
        switch (kind) {
            case RADAR_STATION -> radarStation(c);
            case SILO -> silo(c);
            case SATELLITE -> satellite(c);
        }
        c.finish();
        return true;
    }

    private static int floorLevel(WorldGenLevel level, int cx, int cz, int radius) {
        int[][] pts = { { 0, 0 }, { radius / 2, 0 }, { -radius / 2, 0 }, { 0, radius / 2 }, { 0, -radius / 2 },
                { radius, 0 }, { -radius, 0 }, { 0, radius }, { 0, -radius }, { radius, radius }, { -radius, radius }, { radius, -radius }, { -radius, -radius } };
        int[] h = new int[pts.length];
        int wet = 0;
        for (int i = 0; i < pts.length; i++) {
            int x = cx + pts[i][0], z = cz + pts[i][1];
            h[i] = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) > h[i]) { wet++; if (i == 0) return Integer.MIN_VALUE; }
        }
        if (wet >= 3) return Integer.MIN_VALUE;
        int[] s = h.clone();
        Arrays.sort(s);
        if (s[s.length - 1] - s[0] > 9) return Integer.MIN_VALUE;
        if (s[0] <= level.getMinBuildHeight() + 16 || s[s.length - 1] + 20 >= level.getMaxBuildHeight()) return Integer.MIN_VALUE;
        return s[s.length / 2];
    }

    static final class Ctx {
        final WorldGenLevel level;
        final int cx, by, cz;
        final RandomSource r;
        final Heightmap.Types ground;
        final List<BlockPos> reshape = new ArrayList<>();
        BlockState top = Blocks.GRASS_BLOCK.defaultBlockState(), sub = Blocks.DIRT.defaultBlockState();

        Ctx(WorldGenLevel level, int cx, int by, int cz, RandomSource r, Heightmap.Types ground) { this.level = level; this.cx = cx; this.by = by; this.cz = cz; this.r = r; this.ground = ground; }

        BlockPos pos(int dx, int dy, int dz) { return new BlockPos(cx + dx, by + dy, cz + dz); }

        boolean inside(int dx, int dz) { return Math.abs(dx) <= LIMIT && Math.abs(dz) <= LIMIT; }

        BlockState get(int dx, int dy, int dz) { return level.getBlockState(pos(dx, dy, dz)); }

        void set(int dx, int dy, int dz, BlockState s) {
            if (!inside(dx, dz)) return;
            level.setBlock(pos(dx, dy, dz), s, 2);
        }

        void conn(int dx, int dy, int dz, BlockState s) {
            if (!inside(dx, dz)) return;
            set(dx, dy, dz, s);
            reshape.add(pos(dx, dy, dz));
        }

        void w(int dx, int dy, int dz, BlockState s, float missing) {
            if (r.nextFloat() < missing) return;
            set(dx, dy, dz, worn(s));
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s, float missing) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) w(x, y, z, s, missing);
        }

        void air(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) set(x, y, z, Blocks.AIR.defaultBlockState());
        }

        void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s, float missing) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
                if (x == x0 || x == x1 || z == z0 || z == z1) w(x, y, z, s, missing);
        }

        BlockState worn(BlockState s) {
            float f = r.nextFloat();
            if (s.is(Wmd.REINFORCED_CONCRETE.get())) {
                if (f < 0.12F) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                if (f < 0.18F) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                if (f < 0.23F) return Blocks.ANDESITE.defaultBlockState();
                if (f < 0.27F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            } else if (s.is(Blocks.LIGHT_GRAY_CONCRETE) || s.is(Blocks.WHITE_CONCRETE)) {
                if (f < 0.15F) return Blocks.GRAY_CONCRETE.defaultBlockState();
                if (f < 0.25F) return Blocks.ANDESITE.defaultBlockState();
            } else if (s.is(Blocks.STONE_BRICKS)) {
                if (f < 0.25F) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                if (f < 0.45F) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            } else if (s.is(Blocks.COBBLESTONE)) {
                if (f < 0.3F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            } else if (s.is(Blocks.POLISHED_ANDESITE)) {
                if (f < 0.25F) return Blocks.ANDESITE.defaultBlockState();
                if (f < 0.35F) return Blocks.GRAVEL.defaultBlockState();
            } else if (s.is(Blocks.EXPOSED_CUT_COPPER)) {
                if (f < 0.3F) return Blocks.WEATHERED_CUT_COPPER.defaultBlockState();
                if (f < 0.4F) return Blocks.OXIDIZED_CUT_COPPER.defaultBlockState();
            } else if (s.is(Wmd.ARMOUR_PLATE.get())) {
                if (f < 0.25F) return Blocks.EXPOSED_CUT_COPPER.defaultBlockState();
            } else if (s.is(Blocks.PACKED_MUD)) {
                if (f < 0.2F) return Blocks.MUD_BRICKS.defaultBlockState();
                if (f < 0.3F) return Blocks.COARSE_DIRT.defaultBlockState();
            }
            return s;
        }

        void prepare(int radius, boolean round) {
            BlockState soil = level.getBlockState(pos(0, -1, 0));
            if (soil.is(BlockTags.SAND)) { top = soil; sub = soil.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState(); }
            else if (soil.is(BlockTags.TERRACOTTA)) { top = soil; sub = soil; }
            else if (soil.is(Blocks.SNOW_BLOCK) || soil.is(Blocks.PODZOL) || soil.is(Blocks.COARSE_DIRT)) { top = soil; sub = Blocks.DIRT.defaultBlockState(); }
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                double d = round ? Math.hypot(dx, dz) : Math.max(Math.abs(dx), Math.abs(dz)) + 0.35 * Math.min(Math.abs(dx), Math.abs(dz)) / radius * 3;
                if (d > radius + 0.5) continue;
                int g = level.getHeight(ground, cx + dx, cz + dz) - by;
                if (g < 0) {
                    for (int y = Math.max(g, -10); y < -1; y++) set(dx, y, dz, sub);
                    set(dx, -1, dz, top);
                } else if (g > 0) {
                    set(dx, -1, dz, top);
                }

                int clearTo = Math.min(Math.max(g, 0) + 2, 14);
                for (int y = 0; y <= clearTo; y++) if (!get(dx, y, dz).isAir()) set(dx, y, dz, Blocks.AIR.defaultBlockState());
            }
        }

        void finish() {
            for (BlockPos p : reshape) {
                BlockState s = level.getBlockState(p);
                if (s.isAir()) continue;
                BlockState n = Block.updateFromNeighbourShapes(s, level, p);
                if (n != s && !n.isAir()) level.setBlock(p, n, 2);
            }
        }

        int surface(int dx, int dz, int topY, int bottomY) {
            for (int y = topY; y >= bottomY; y--) if (!get(dx, y, dz).isAir()) return y + 1;
            return bottomY;
        }

        void chest(int dx, int dy, int dz, Direction facing, String table) {
            if (!inside(dx, dz)) return;
            set(dx, dy, dz, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
            RandomizableContainer.setBlockEntityLootTable(level, r, pos(dx, dy, dz), loot(table));
        }

        void barrel(int dx, int dy, int dz, String table) {
            if (!inside(dx, dz)) return;
            set(dx, dy, dz, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
            RandomizableContainer.setBlockEntityLootTable(level, r, pos(dx, dy, dz), loot(table));
        }

        BlockPos generator(int dx, int dy, int dz, Direction facing) {
            set(dx, dy, dz, Wmd.SALVAGE_GENERATOR.get().defaultBlockState().setValue(SalvageGeneratorBlock.FACING, facing));
            return pos(dx, dy, dz);
        }

        void turret(int dx, int dy, int dz, BlockPos generator) {
            set(dx, dy, dz, Wmd.SALVAGE_TURRET.get().defaultBlockState());
            if (level.getBlockEntity(pos(dx, dy, dz)) instanceof SalvageTurretBlockEntity be) be.setGenerator(generator);
        }

        void sandbagPit(int px, int pz, Direction open, BlockPos generator) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(px + x, -1, pz + z, r.nextFloat() < 0.5F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState());
                if (!ring) continue;
                boolean gap = (open == Direction.SOUTH && z == 2 || open == Direction.NORTH && z == -2) && Math.abs(x) <= 1
                        || (open == Direction.EAST && x == 2 || open == Direction.WEST && x == -2) && Math.abs(z) <= 1;
                if (gap) continue;
                w(px + x, 0, pz + z, Blocks.PACKED_MUD.defaultBlockState(), 0.06F);
                if (r.nextFloat() < 0.75F) set(px + x, 1, pz + z, Blocks.MUD_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
            set(px, 0, pz, Wmd.REINFORCED_CONCRETE.get().defaultBlockState());
            turret(px, 1, pz, generator);
        }

        void lattice(int x, int y, int z, Axis axis, float missing) {
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                int px = x, py = y, pz = z;
                switch (axis) {
                    case Y -> { px += a; pz += b; }
                    case X -> { py += a; pz += b; }
                    default -> { px += a; py += b; }
                }
                if (a == 0 && b == 0) { if (r.nextFloat() > missing) set(px, py, pz, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, axis)); }
                else if (r.nextFloat() > missing) conn(px, py, pz, Blocks.IRON_BARS.defaultBlockState());
            }
        }

        void lever(int dx, int dy, int dz, Direction facing) {
            set(dx, dy, dz, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, facing));
        }

        void plate(int dx, int dy, int dz) {
            set(dx, dy, dz, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)).setValue(TrapDoorBlock.HALF, Half.BOTTOM));
        }
    }

    private static BlockState rc() { return Wmd.REINFORCED_CONCRETE.get().defaultBlockState(); }

    private static BlockState st(Block b) { return b.defaultBlockState(); }

    private static void radarStation(Ctx c) {
        RandomSource r = c.r;

        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            float f = r.nextFloat();
            if (f < 0.35F) c.set(x, -1, z, st(Blocks.GRAVEL));
            else if (f < 0.7F) c.set(x, -1, z, st(Blocks.COARSE_DIRT));
        }

        for (int i = -12; i <= 12; i++) {
            int[][] cells = { { i, -12 }, { i, 12 }, { -12, i }, { 12, i } };
            for (int[] p : cells) {
                int x = p[0], z = p[1];
                if (z == 12 && Math.abs(x) <= 2) continue;
                if (x == 12 && z >= -5 && z <= 0) {
                    if (r.nextFloat() < 0.6F) c.plate(x, 0, z);
                    continue;
                }
                boolean post = Math.floorMod(i, 3) == 0;
                if (post) {
                    int hgt = 2 + (r.nextFloat() < 0.8F ? 1 : 0);
                    for (int y = 0; y < hgt; y++) c.conn(x, y, z, st(Blocks.ANDESITE_WALL));
                } else if (r.nextFloat() > 0.18F) {
                    c.conn(x, 0, z, st(Blocks.IRON_BARS));
                    if (r.nextFloat() > 0.25F) c.conn(x, 1, z, st(Blocks.IRON_BARS));
                }
            }
        }

        c.box(-10, -1, -10, -4, -1, -5, st(Blocks.POLISHED_ANDESITE), 0.0F);
        c.walls(-10, 0, -10, -4, 2, -5, rc(), 0.04F);
        for (int x = -10; x <= -4; x++) for (int z = -10; z <= -5; z++) {
            boolean hole = (x + 7) * (x + 7) + (z + 7) * (z + 7) <= 3 && r.nextFloat() < 0.85F;
            if (hole) { if (x > -10 && x < -4 && z > -10 && z < -5 && r.nextFloat() < 0.5F) c.set(x, 0, z, st(r.nextBoolean() ? Blocks.COBBLESTONE : Blocks.GRAVEL)); }
            else c.w(x, 3, z, st(Blocks.SMOOTH_STONE), 0.08F);
        }
        c.air(-7, 0, -5, -7, 1, -5);
        for (int x : new int[] { -9, -5 }) if (r.nextFloat() < 0.6F) c.set(x, 1, -5, Wmd.BLAST_GLASS.get().defaultBlockState()); else c.set(x, 1, -5, st(Blocks.AIR));
        for (int x : new int[] { -8, -6 }) if (r.nextFloat() < 0.6F) c.set(x, 1, -10, Wmd.BLAST_GLASS.get().defaultBlockState()); else c.set(x, 1, -10, st(Blocks.AIR));

        c.set(-7, 0, -9, Wmd.MACHINE_CASING.get().defaultBlockState());
        c.set(-6, 0, -9, Wmd.MACHINE_CASING.get().defaultBlockState());
        c.lever(-6, 1, -9, Direction.SOUTH);
        c.set(-8, 2, -9, st(Blocks.REDSTONE_LAMP));
        c.set(-5, 2, -6, st(Blocks.COBWEB));
        c.set(-9, 2, -6, st(Blocks.COBWEB));
        c.chest(-9, 0, -9, Direction.SOUTH, "salvage_radar_station");
        c.barrel(-5, 0, -9, "salvage_supplies");

        BlockPos gen = c.generator(-3, 0, -8, Direction.EAST);
        for (int z = -9; z <= -7; z++) c.w(-2, 0, z, st(Blocks.PACKED_MUD), 0.2F);
        c.set(-3, 0, -6, st(Blocks.CAULDRON));
        c.set(-2, 0, -6, st(Blocks.CAULDRON));

        c.box(4, -1, 1, 6, -1, 3, rc(), 0.0F);
        for (int y = 0; y <= 8; y++) {
            float missing = y < 5 ? 0.05F : 0.05F + (y - 4) * 0.18F;
            c.lattice(5, y, 2, Axis.Y, missing);
        }
        for (int z = 0; z >= -7; z--) c.lattice(5, 1, z, Axis.Z, z < -5 ? 0.3F : 0.12F);

        for (int x = 2; x <= 8; x++) for (int y = 0; y <= 5; y++) {
            double dd = (x - 5) * (x - 5) + (y - 2.5) * (y - 2.5);
            if (dd > 8.6) continue;
            int z = y >= 4 ? -10 : -9;
            c.w(x, y, z, st(Blocks.WHITE_CONCRETE), 0.07F);
        }
        c.set(5, 2, -9, Wmd.ARMOUR_PLATE.get().defaultBlockState());
        c.set(5, 2, -8, st(Blocks.LIGHTNING_ROD).setValue(LightningRodBlock.FACING, Direction.SOUTH));

        for (int i = 0; i < 6; i++) {
            int x = r.nextInt(19) - 9, z = r.nextInt(19) - 9;
            if (c.get(x, 0, z).isAir() && !c.get(x, -1, z).isAir()) c.plate(x, 0, z);
        }
        c.barrel(9, 0, 9, "salvage_supplies");

        c.sandbagPit(-7, 7, Direction.NORTH, gen);
        c.sandbagPit(9, -5, Direction.WEST, gen);
    }

    private static void silo(Ctx c) {
        RandomSource r = c.r;

        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            if (r.nextFloat() < 0.07F) { c.set(x, -1, z, r.nextBoolean() ? c.top : st(Blocks.COARSE_DIRT)); continue; }
            boolean joint = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
            c.w(x, -1, z, joint ? st(Blocks.GRAY_CONCRETE) : rc(), 0.0F);
        }

        for (int y = -12; y <= -1; y++) for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3 || y == -12;
            c.set(x, y, z, wall ? c.worn(rc()) : st(Blocks.AIR));
        }
        for (int y = -11; y <= -1; y++) c.set(0, y, -2, st(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH));
        for (int i = 0; i < 4; i++) c.set(r.nextInt(5) - 2, -11, r.nextInt(3), st(r.nextBoolean() ? Blocks.GRAVEL : Blocks.COBBLESTONE));
        c.set(-2, -10, 2, st(Blocks.COBWEB));
        c.set(2, -4, -2, st(Blocks.COBWEB));
        for (int y = -4; y <= -1; y++) c.set(-2, y, -2, st(Blocks.CHAIN));
        for (int y = -3; y <= -1; y++) c.set(2, y, 1, st(Blocks.CHAIN));
        c.chest(1, -11, 1, Direction.NORTH, "salvage_silo");

        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) == 3 || Math.abs(z) == 3) c.set(x, 0, z, Wmd.ARMOUR_PLATE.get().defaultBlockState());
            else if (x == 0) c.set(x, 0, z, Wmd.ARMOUR_PLATE.get().defaultBlockState());
            else c.set(x, 0, z, c.worn(st(Blocks.EXPOSED_CUT_COPPER)));
        }
        c.set(0, 0, -2, st(Blocks.AIR));
        c.set(-1, 0, -2, st(Blocks.IRON_TRAPDOOR).setValue(TrapDoorBlock.FACING, Direction.SOUTH).setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, true));
        c.set(1, 0, -2, st(Blocks.AIR));

        c.walls(-8, 0, -10, -6, 2, -8, rc(), 0.03F);
        c.box(-8, 3, -10, -6, 3, -8, rc(), 0.0F);
        c.air(-8, 0, -9, -8, 1, -9);
        c.air(-7, 0, -9, -7, 2, -9);
        for (int y = 4; y <= 7; y++) c.conn(-8, y, -10, st(Blocks.IRON_BARS));
        c.set(-8, 8, -10, st(Blocks.LIGHTNING_ROD));

        c.box(-8, -6, -9, -6, -1, -6, rc(), 0.0F);
        for (int i = 0; i < 4; i++) {
            int z = -9 + i, y = -2 - i;
            c.set(-7, y, z, st(Blocks.STONE_BRICK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
            for (int a = y + 1; a <= -1; a++) c.set(-7, a, z, st(Blocks.AIR));
        }
        c.set(-7, -1, -9, st(Blocks.AIR));
        c.box(-10, -6, -6, -4, -2, 0, rc(), 0.0F);
        c.air(-9, -5, -5, -5, -3, -1);
        c.set(-7, -5, -6, st(Blocks.STONE_BRICK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        c.set(-7, -4, -6, st(Blocks.AIR)); c.set(-7, -3, -6, st(Blocks.AIR));
        c.box(-9, -6, -5, -5, -6, -1, st(Blocks.POLISHED_ANDESITE), 0.0F);

        c.set(-4, -4, -1, Wmd.BLAST_GLASS.get().defaultBlockState());
        c.set(-3, -4, -1, Wmd.BLAST_GLASS.get().defaultBlockState());

        BlockPos gen = c.generator(-9, -5, -1, Direction.EAST);
        c.set(-5, -5, -5, Wmd.MACHINE_CASING.get().defaultBlockState());
        c.set(-5, -5, -4, Wmd.MACHINE_CASING.get().defaultBlockState());
        c.lever(-5, -4, -4, Direction.WEST);
        c.chest(-9, -5, -5, Direction.EAST, "salvage_supplies");
        c.barrel(-9, -5, -3, "salvage_supplies");
        c.set(-7, -3, -3, st(Blocks.REDSTONE_LAMP));
        c.set(-5, -3, -1, st(Blocks.COBWEB));

        for (int[] p : new int[][] { { 9, 9 }, { -9, 9 }, { 9, -9 } }) {
            int hgt = 3 + r.nextInt(2);
            for (int y = 0; y < hgt; y++) c.conn(p[0], y, p[1], st(Blocks.ANDESITE_WALL));
            if (r.nextFloat() < 0.7F) c.set(p[0], hgt, p[1], st(Blocks.REDSTONE_LAMP));
        }
        for (int i = -11; i <= 11; i++) {
            int[][] cells = { { i, -11 }, { i, 11 }, { -11, i }, { 11, i } };
            for (int[] p : cells) {
                if (r.nextFloat() < 0.45F) continue;
                c.conn(p[0], 0, p[1], st(Math.floorMod(i, 4) == 0 ? Blocks.ANDESITE_WALL : Blocks.IRON_BARS));
            }
        }

        c.sandbagPit(6, -6, Direction.SOUTH, gen);
        c.sandbagPit(5, 6, Direction.NORTH, gen);
    }

    private static int craterDepth(double d) { return d >= 7.0 ? 0 : (int) Math.round(4.0 * (1.0 - (d / 7.0) * (d / 7.0))); }

    private static BlockState scorched(RandomSource r) {
        float f = r.nextFloat();
        if (f < 0.32F) return Blocks.COARSE_DIRT.defaultBlockState();
        if (f < 0.52F) return Blocks.BLACKSTONE.defaultBlockState();
        if (f < 0.68F) return Blocks.TUFF.defaultBlockState();
        if (f < 0.82F) return Blocks.SMOOTH_BASALT.defaultBlockState();
        if (f < 0.85F) return Blocks.MAGMA_BLOCK.defaultBlockState();
        return Blocks.GRAVEL.defaultBlockState();
    }

    private static void satellite(Ctx c) {
        RandomSource r = c.r;
        final int ix = 1, iz = 0;

        for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) {
            double d = Math.hypot(x - ix, z - iz);
            if (d > 13.5) continue;
            double p = d < 8 ? 0.95 : 0.95 - (d - 8) / 6.0;
            if (r.nextFloat() < p) c.set(x, -1, z, scorched(r));
        }

        for (int x = -14; x <= -6; x++) for (int z = -1; z <= 1; z++) {
            c.set(x, -1, z, scorched(r));
            if (z == 0 && x > -12) c.set(x, -1, z, st(Blocks.AIR));
            if (z == 0 && x > -12) c.set(x, -2, z, scorched(r));
        }

        for (int x = -8; x <= 10; x++) for (int z = -9; z <= 9; z++) {
            double d = Math.hypot(x - ix, z - iz);
            int dep = craterDepth(d);
            if (dep > 0) {
                for (int y = -dep; y <= -1; y++) c.set(x, y, z, st(Blocks.AIR));
                c.set(x, -dep - 1, z, scorched(r));
            } else if (d < 8.6 && r.nextFloat() < 0.75F) {
                c.set(x, 0, z, r.nextFloat() < 0.5F ? st(Blocks.COARSE_DIRT) : c.sub);
                if (r.nextFloat() < 0.2F) c.set(x, 1, z, st(Blocks.COARSE_DIRT));
            }
        }

        for (int x = 0; x <= 3; x++) {
            int base = -craterDepth(Math.hypot(x - ix, 0)) + (x >= 2 ? 1 : 0);
            for (int y = base; y <= base + 2; y++) for (int z = -1; z <= 1; z++) {
                float f = r.nextFloat();
                BlockState s = f < 0.45F ? Wmd.ARMOUR_PLATE.get().defaultBlockState() : f < 0.7F ? st(Blocks.HONEYCOMB_BLOCK)
                        : f < 0.85F ? st(Blocks.LIGHT_GRAY_CONCRETE) : Wmd.MACHINE_CASING.get().defaultBlockState();
                if (x == 3 && y == base + 1 && z == 0) s = Wmd.BLAST_GLASS.get().defaultBlockState();
                if (r.nextFloat() < 0.08F) continue;
                c.set(x, y, z, s);
            }
            if (x == 3) c.set(x, base + 3, 0, st(Blocks.LIGHTNING_ROD));
        }

        for (int z = 2; z <= 7; z++) for (int x = 0; x <= 2; x++) {
            if (r.nextFloat() < 0.22F) continue;
            int y = c.surface(x, z, 1, -6);
            c.set(x, y, z, st(Blocks.DAYLIGHT_DETECTOR));
        }

        for (int x = -7; x <= -5; x++) for (int z = -12; z <= -8; z++) {
            if (r.nextFloat() < 0.28F) continue;
            int y = c.surface(x, z, 2, -3);
            if (x == -5 && r.nextFloat() < 0.5F) { c.set(x, y, z, Wmd.ARMOUR_PLATE.get().defaultBlockState()); y++; }
            c.set(x, y, z, st(Blocks.DAYLIGHT_DETECTOR));
        }

        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 4.0 + r.nextDouble() * 9.0;
            int x = ix + (int) Math.round(Math.cos(a) * d), z = iz + (int) Math.round(Math.sin(a) * d);
            if (!c.inside(x, z)) continue;
            int y = c.surface(x, z, 2, -6);
            float f = r.nextFloat();
            if (f < 0.35F) c.plate(x, y, z);
            else if (f < 0.5F) c.set(x, y, z, st(Blocks.CHAIN).setValue(ChainBlock.AXIS, r.nextBoolean() ? Axis.X : Axis.Z));
            else if (f < 0.62F) c.set(x, y, z, st(Blocks.LIGHTNING_ROD).setValue(LightningRodBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)));
            else if (f < 0.75F) c.set(x, y, z, Wmd.ARMOUR_PLATE.get().defaultBlockState());
            else if (f < 0.85F) c.set(x, y, z, st(Blocks.HONEYCOMB_BLOCK));
            else if (f < 0.93F) c.set(x, y, z, st(Blocks.DAYLIGHT_DETECTOR));
            else c.conn(x, y, z, st(Blocks.IRON_BARS));
        }

        int[][] fires = { { -2, 1 }, { 4, -3 } };
        for (int[] f : fires) {
            int y = c.surface(f[0], f[1], 1, -6);
            c.set(f[0], y - 1, f[1], st(Blocks.HAY_BLOCK));
            c.conn(f[0], y, f[1], st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
        }
        int sy = c.surface(6, 7, 2, -3);
        c.set(6, sy, 7, st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, true));

        c.chest(-1, c.surface(-1, -2, 1, -6), -2, Direction.EAST, "salvage_satellite");

        c.set(-10, -1, 7, st(Blocks.PACKED_MUD));
        BlockPos gen = c.generator(-10, 0, 7, Direction.NORTH);
        c.set(-11, 0, 7, Wmd.MACHINE_CASING.get().defaultBlockState());
        c.set(-10, 0, 8, st(Blocks.CAULDRON));
        c.barrel(-11, 0, 8, "salvage_supplies");
        c.sandbagPit(-6, 9, Direction.NORTH, gen);
    }
}
