package com.ashraf.wmd;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DevAirfield {

    private DevAirfield() {}

    private static final Map<ResourceKey<Level>, BlockPos> ORIGIN = Statics.server(new HashMap<>());

    private static final int RW_HALF = 5, RW_LEN = 200;
    private static final int H_X0 = 21, H_X1 = 43, H_Z0 = -32, H_Z1 = -8, H_H = 11;
    private static final int TANK_X = 30, TANK_Z = -3;

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").requires(TestCmds::test).then(Commands.literal("airfield").executes(c -> build(c.getSource()))
                        .then(Commands.literal("savejet").executes(c -> saveJet(c.getSource())))
                        .then(Commands.literal("jet").executes(c -> placeJet(c.getSource(), true)))
                        .then(Commands.literal("testjet").executes(c -> testJet(c.getSource()))))));
    }

    private static void say(CommandSourceStack src, String s) { src.sendSuccess(() -> Component.literal(s), false); }

    private static File jetFile() { return FMLPaths.CONFIGDIR.get().resolve("wmd_dev_testjet.nbt").toFile(); }

    private static int build(CommandSourceStack src) {
        ServerLevel sl = src.getLevel();
        BlockPos o = BlockPos.containing(src.getPosition()).below();
        ORIGIN.put(sl.dimension(), o);
        BlockState grey = Blocks.GRAY_CONCRETE.defaultBlockState(), white = Blocks.WHITE_CONCRETE.defaultBlockState(),
                light = Blocks.SEA_LANTERN.defaultBlockState(), wall = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                floor = Blocks.SMOOTH_STONE.defaultBlockState(), base = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();

        for (int x = -RW_HALF - 2; x <= H_X1 + 2; x++)
            for (int z = -RW_LEN - 2; z <= 6; z++) {
                boolean site = Math.abs(x) <= RW_HALF + 2 || (x >= RW_HALF && x <= H_X1 + 2 && z >= H_Z0 - 2 && z <= 6);
                if (!site) continue;
                for (int y = 1; y <= H_H + 3; y++) set(sl, o.offset(x, y, z), air);
                set(sl, o.offset(x, -1, z), base);
                set(sl, o.offset(x, 0, z), floor);
            }

        for (int z = -RW_LEN; z <= 0; z++)
            for (int x = -RW_HALF; x <= RW_HALF; x++) {
                boolean centre = x == 0 && Math.floorMod(z, 12) < 6;
                boolean threshold = (z > -6 || z < -RW_LEN + 6) && x != 0 && Math.abs(x) <= RW_HALF - 1 && (x & 1) == 0;
                boolean edge = Math.abs(x) == RW_HALF && Math.floorMod(z, 8) == 0;
                set(sl, o.offset(x, 0, z), edge ? light : centre || threshold ? white : grey);
            }

        for (int x = RW_HALF + 1; x < H_X0; x++)
            for (int z = -24; z <= -16; z++) set(sl, o.offset(x, 0, z), x % 6 == 0 && z == -20 ? white : grey);

        for (int x = H_X0; x <= H_X1; x++)
            for (int z = H_Z0; z <= H_Z1; z++) {
                set(sl, o.offset(x, 0, z), grey);
                boolean wallCell = z == H_Z0 || z == H_Z1 || x == H_X1;
                for (int y = 1; y < H_H; y++) if (wallCell) set(sl, o.offset(x, y, z), wall);
                boolean lamp = (x - H_X0) % 5 == 2 && (z - H_Z0) % 6 == 3;
                set(sl, o.offset(x, H_H, z), lamp ? light : wall);
            }
        for (int y = 1; y < H_H; y++) { set(sl, o.offset(H_X0, y, H_Z0), wall); set(sl, o.offset(H_X0, y, H_Z1), wall); }
        int fuel = fuelPoint(sl, o, src.getPlayer());
        say(src, "Test airfield built at " + o.toShortString() + ": runway 11 x 201 north of you, hangar east (door to the runway), fuel point south of the hangar ("
                + String.format("%,d", fuel) + " mB jet fuel; pipe from the EAST face of the tank's bottom middle cell).");
        testJet(src);
        if (jetFile().exists()) placeJet(src, false);
        return 1;
    }

    private static void set(ServerLevel sl, BlockPos p, BlockState s) {
        if (sl.isInWorldBounds(p)) sl.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    private static int fuelPoint(ServerLevel sl, BlockPos o, ServerPlayer player) {
        BlockPos c = o.offset(TANK_X, 1, TANK_Z);
        BlockState st = Wmd.STORAGE_TANK.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH);
        sl.setBlock(c, st, Block.UPDATE_ALL);
        Wmd.STORAGE_TANK.get().setPlacedBy(sl, c, st, player, ItemStack.EMPTY);
        if (!(sl.getBlockEntity(c) instanceof StorageTankBlockEntity t)) return 0;
        BlockPos in = Machines.toWorld(c, Direction.NORTH, -2, 0, 2);
        IFluidHandler h = t.fluidPort(in, Machines.dirToWorld(Direction.NORTH, Direction.WEST));
        if (h == null) return 0;
        return h.fill(new FluidStack(WmdFluids.JET_FUEL.source().get(), Balance.STORAGE_TANK_MB), IFluidHandler.FluidAction.EXECUTE);
    }

    private static BlockPos insideMin() { return new BlockPos(H_X0 + 1, 1, H_Z0 + 1); }
    private static BlockPos insideMax() { return new BlockPos(H_X1 - 1, H_H - 1, H_Z1 - 1); }

    static BlockPos origin(CommandSourceStack src) {
        BlockPos o = ORIGIN.get(src.getLevel().dimension());
        if (o == null) src.sendFailure(Component.literal("Build the airfield first in this session: /wmd dev airfield"));
        return o;
    }

    private static int saveJet(CommandSourceStack src) {
        ServerLevel sl = src.getLevel();
        BlockPos o = origin(src);
        if (o == null) return 0;
        BlockPos a = o.offset(insideMin()), b = o.offset(insideMax());
        ListTag list = new ListTag();
        int machines = 0;
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            BlockState s = sl.getBlockState(p);
            if (s.isAir() || s.is(Wmd.MACHINE_PART.get())) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("x", p.getX() - a.getX()); e.putInt("y", p.getY() - a.getY()); e.putInt("z", p.getZ() - a.getZ());
            e.put("state", NbtUtils.writeBlockState(s));
            BlockEntity be = sl.getBlockEntity(p);
            if (be != null && !(be instanceof MachinePartBlockEntity)) e.put("be", be.saveWithoutMetadata(sl.registryAccess()));
            if (s.getBlock() instanceof MachineBlock) machines++;
            list.add(e);
        }
        if (list.isEmpty()) { src.sendFailure(Component.literal("The hangar is empty: build the jet inside it first.")); return 0; }
        CompoundTag root = new CompoundTag();
        root.put("blocks", list);
        try { NbtIo.writeCompressed(root, jetFile().toPath()); }
        catch (Exception ex) { src.sendFailure(Component.literal("Could not write " + jetFile() + ": " + ex.getMessage())); return 0; }
        final int n = list.size(), m = machines;
        say(src, "Test jet saved: " + n + " blocks (" + m + " machines) -> " + jetFile().getName() + ". /wmd dev airfield jet places it again.");
        return 1;
    }

    private static int placeJet(CommandSourceStack src, boolean needOrigin) {
        ServerLevel sl = src.getLevel();
        BlockPos o = origin(src);
        if (o == null) return 0;
        if (!jetFile().exists()) { src.sendFailure(Component.literal("No test jet saved yet (/wmd dev airfield savejet).")); return 0; }
        CompoundTag root;
        try { root = NbtIo.readCompressed(jetFile().toPath(), NbtAccounter.unlimitedHeap()); }
        catch (Exception ex) { src.sendFailure(Component.literal("Could not read " + jetFile() + ": " + ex.getMessage())); return 0; }
        BlockPos a = o.offset(insideMin()), b = o.offset(insideMax());
        for (BlockPos p : BlockPos.betweenClosed(a, b)) if (!sl.getBlockState(p).isAir()) sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        int placed = placeSaved(sl, root, a, src.getPlayer());
        say(src, "Test jet placed in the hangar: " + placed + " blocks. Assemble it with the Aeronautics assembler, then taxi out west to the runway.");
        return 1;
    }

    static int placeSaved(ServerLevel sl, CompoundTag root, BlockPos a, net.minecraft.server.level.ServerPlayer player) {
        var blocks = sl.holderLookup(Registries.BLOCK);
        ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
        List<CompoundTag> later = new ArrayList<>();
        int placed = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                BlockState s = NbtUtils.readBlockState(blocks, e.getCompound("state"));
                boolean machine = s.getBlock() instanceof MachineBlock;
                if (machine != (pass == 1) || s.isAir()) continue;
                BlockPos p = a.offset(e.getInt("x"), e.getInt("y"), e.getInt("z"));
                sl.setBlock(p, s, Block.UPDATE_ALL);
                if (machine) s.getBlock().setPlacedBy(sl, p, s, player, ItemStack.EMPTY);
                if (e.contains("be")) later.add(e);
                placed++;
            }
        }
        for (CompoundTag e : later) {
            BlockPos p = a.offset(e.getInt("x"), e.getInt("y"), e.getInt("z"));
            BlockEntity be = sl.getBlockEntity(p);
            if (be == null) continue;
            be.loadWithComponents(e.getCompound("be"), sl.registryAccess());
            be.setChanged();
            sl.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), Block.UPDATE_ALL);
        }
        return placed;
    }

    static CompoundTag savedJet() {
        if (!jetFile().exists()) return null;
        try { return NbtIo.readCompressed(jetFile().toPath(), NbtAccounter.unlimitedHeap()); } catch (Exception ex) { return null; }
    }

    static int[] savedSize(CompoundTag root) {
        int[] m = { 0, 0, 0 };
        ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { CompoundTag e = list.getCompound(i); m[0] = Math.max(m[0], e.getInt("x")); m[1] = Math.max(m[1], e.getInt("y")); m[2] = Math.max(m[2], e.getInt("z")); }
        return m;
    }

    private static final int TJ_NOSE_Z = -40, TJ_Y = 2;

    private static int testJet(CommandSourceStack src) {
        ServerLevel sl = src.getLevel();
        BlockPos o = origin(src);
        if (o == null) return 0;
        ServerPlayer pl = src.getPlayer();
        BlockPos n = o.offset(0, TJ_Y, TJ_NOSE_Z);
        BlockState hull = Blocks.BIRCH_PLANKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -7; x <= 7; x++) for (int y = -1; y <= 4; y++) for (int z = -1; z <= 15; z++) set(sl, n.offset(x, y, z), air);

        for (int z = 1; z <= 13; z++) for (int x = -1; x <= 1; x++) for (int y = 0; y <= 1; y++) set(sl, n.offset(x, y, z), hull);
        set(sl, n.offset(0, 0, 0), hull);

        BlockState wing = AirBlocks.WING_SECTION.get().defaultBlockState().setValue(WingParts.FACING, Direction.NORTH).setValue(WingParts.FIN, false)
                .setValue(WingParts.SHAPE, WingParts.Shape.PANEL).setValue(WingParts.LEVEL, WingParts.Level.MID);
        BlockState cs = AirBlocks.CONTROL_SURFACE.get().defaultBlockState().setValue(WingParts.FACING, Direction.NORTH).setValue(WingParts.FIN, false)
                .setValue(WingParts.LEVEL, WingParts.Level.MID);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int k = 2; k <= 6; k++) {
                set(sl, n.offset(sx * k, 0, 7), wing);
                set(sl, n.offset(sx * k, 0, 8), wing);
                if (k >= 3) set(sl, n.offset(sx * k, 0, 9), cs.setValue(WingParts.ROLE, WingParts.Role.AILERON));
            }
            for (int k = 2; k <= 4; k++) {
                set(sl, n.offset(sx * k, 0, 12), wing);
                set(sl, n.offset(sx * k, 0, 13), cs.setValue(WingParts.ROLE, WingParts.Role.ELEVATOR));
            }
        }
        BlockState fin = wing.setValue(WingParts.FIN, true);
        set(sl, n.offset(0, 2, 11), fin);
        set(sl, n.offset(0, 2, 12), fin);
        set(sl, n.offset(0, 2, 13), cs.setValue(WingParts.FIN, true).setValue(WingParts.ROLE, WingParts.Role.RUDDER));
        for (int dy = 2; dy <= 3; dy++) for (int dz = 2; dz <= 4; dz++) set(sl, n.offset(0, dy, dz), air);

        machine(sl, pl, n.offset(0, 1, 4), AirBlocks.JET_COCKPIT.get(), Direction.SOUTH);
        machine(sl, pl, n.offset(0, 0, 7), AirBlocks.POWER_CORE.get(), Direction.NORTH);
        machine(sl, pl, n.offset(0, 0, 8), AirBlocks.FUEL_TANK.get(), Direction.NORTH);
        machine(sl, pl, n.offset(0, 0, 10), AirBlocks.SINGLE_JET.get(), Direction.NORTH);
        machine(sl, pl, n.offset(0, 0, 2), AirBlocks.LANDING_GEAR.get(), Direction.NORTH);
        machine(sl, pl, n.offset(-2, 0, 9), AirBlocks.LANDING_GEAR.get(), Direction.NORTH);
        machine(sl, pl, n.offset(2, 0, 9), AirBlocks.LANDING_GEAR.get(), Direction.NORTH);
        if (sl.getBlockEntity(n.offset(0, 0, 2)) instanceof LandingGearBlockEntity g) for (int i = 0; i < 4 && g.role() != LandingGearBlockEntity.NOSE; i++) g.cycleRole();

        int fuel = 0;
        BlockPos core = n.offset(0, 0, 7), tank = n.offset(0, 0, 8);
        if (sl.getBlockEntity(core) instanceof PowerCoreBlockEntity c) {
            IFluidHandler h = c.fluidPort(core, Machines.dirToWorld(Direction.NORTH, Direction.WEST));
            if (h != null) fuel += h.fill(new FluidStack(WmdFluids.JET_FUEL.source().get(), Balance.CORE_TANK_MB), IFluidHandler.FluidAction.EXECUTE);
        }
        if (sl.getBlockEntity(tank) instanceof FuelTankBlockEntity t) {
            IFluidHandler h = t.fluidPort(tank, Machines.dirToWorld(Direction.NORTH, Direction.NORTH));
            if (h != null) fuel += h.fill(new FluidStack(WmdFluids.JET_FUEL.source().get(), Balance.FUEL_TANK_MB), IFluidHandler.FluidAction.EXECUTE);
        }
        say(src, "Test jet placed on the runway at " + n.toShortString() + " (nose north), " + String.format("%,d", fuel)
                + " mB jet fuel aboard. Assemble it with the Aeronautics assembler, sit in the cockpit, use the flight check (C) before take-off.");
        return 1;
    }

    private static void machine(ServerLevel sl, ServerPlayer pl, BlockPos p, Block b, Direction facing) {
        BlockState st = b.defaultBlockState();
        if (st.hasProperty(MachineBlock.FACING)) st = st.setValue(MachineBlock.FACING, facing);
        if (st.hasProperty(MachineBlock.MOUNT)) st = st.setValue(MachineBlock.MOUNT, Direction.UP);
        if (b instanceof MachineBlock mb) for (BlockPos l : mb.footprint()) {
            BlockPos c = Machines.toWorld(p, facing, Direction.UP, l.getX(), l.getY(), l.getZ());
            if (!c.equals(p)) sl.setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        sl.setBlock(p, st, Block.UPDATE_ALL);
        b.setPlacedBy(sl, p, st, pl, ItemStack.EMPTY);
    }
}
