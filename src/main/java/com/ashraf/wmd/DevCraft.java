package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DevCraft {

    private DevCraft() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2)).then(Commands.literal("dev").requires(TestCmds::test)
                .then(Commands.literal("airfield")
                        .then(Commands.literal("f16").executes(c -> f16(c.getSource())))
                        .then(Commands.literal("f35").executes(c -> f35(c.getSource())))
                        .then(Commands.literal("ac130").executes(c -> ac130(c.getSource()))))
                .then(Commands.literal("testboat").executes(c -> boat(c.getSource())))
                .then(Commands.literal("heli").executes(c -> heli(c.getSource())))));
    }

    static void say(CommandSourceStack src, String s) { src.sendSuccess(() -> Component.literal(s), false); }

    record Frame(BlockPos o, Direction fwd) {
        BlockPos at(int x, int y, int z) { return o.relative(fwd.getClockWise(), x).above(y).relative(fwd.getOpposite(), z); }

        Direction d(Direction local) {
            if (local.getAxis() == Direction.Axis.Y) return local;
            return Direction.fromYRot(local.toYRot() + fwd.toYRot() - Direction.NORTH.toYRot());
        }
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    static Block B(String id) { return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)); }

    static boolean gated(CommandSourceStack src) {
        if (AirGate.on()) return false;
        src.sendFailure(Component.literal("1.2 air battles are off this session (/wmd dev airbattles on)."));
        return true;
    }

    static void put(ServerLevel sl, Frame f, int x, int y, int z, BlockState s) {
        sl.setBlock(f.at(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    static void clear(ServerLevel sl, Frame f, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) put(sl, f, x, y, z, AIR);
    }

    static void machine(ServerLevel sl, ServerPlayer pl, Frame f, int x, int y, int z, Block b, Direction localFacing) {
        BlockPos p = f.at(x, y, z);
        Direction facing = f.d(localFacing);
        BlockState st = b.defaultBlockState();
        if (st.hasProperty(MachineBlock.FACING)) st = st.setValue(MachineBlock.FACING, facing);
        if (st.hasProperty(MachineBlock.MOUNT)) st = st.setValue(MachineBlock.MOUNT, Direction.UP);
        if (b instanceof MachineBlock mb) for (BlockPos l : mb.footprint()) {
            BlockPos c = Machines.toWorld(p, facing, Direction.UP, l.getX(), l.getY(), l.getZ());
            if (!c.equals(p)) sl.setBlock(c, AIR, Block.UPDATE_CLIENTS);
        }
        sl.setBlock(p, st, Block.UPDATE_ALL);
        b.setPlacedBy(sl, p, st, pl, ItemStack.EMPTY);
    }

    static BlockPos placeLike(ServerLevel sl, ServerPlayer pl, Frame f, Block b, int sx, int sy, int sz, Direction face, Direction look, List<String> errs) {
        PlaceTurn.set(pl, 0);
        BlockPos sup = f.at(sx, sy, sz);
        Direction wf = f.d(face), wl = f.d(look);
        BlockPos tgt = sup.relative(wf);
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (sl.getBlockState(sup).canBeReplaced()) { errs.add(name + ": nothing to click at " + sup.toShortString()); return null; }
        if (!sl.getBlockState(tgt).canBeReplaced()) sl.setBlock(tgt, AIR, Block.UPDATE_CLIENTS);
        float oy = pl.getYRot(), ox = pl.getXRot();
        if (wl.getAxis() != Direction.Axis.Y) pl.setYRot(wl.toYRot());
        pl.setXRot(wl == Direction.UP ? -89.0F : wl == Direction.DOWN ? 89.0F : 0.0F);
        try {
            ItemStack stack = new ItemStack(b);
            Vec3 hv = Vec3.atCenterOf(sup).add(wf.getStepX() * 0.5, wf.getStepY() * 0.5, wf.getStepZ() * 0.5);
            BlockPlaceContext ctx = new BlockPlaceContext(sl, pl, InteractionHand.MAIN_HAND, stack, new BlockHitResult(hv, wf, sup, false));
            if (b instanceof MachineBlock mb) {
                Direction[] mf = mb.placement(ctx);
                if (mf == null) { errs.add(name + ": its placement refused the spot at " + tgt.toShortString()); return null; }
                for (BlockPos l : mb.footprint()) {
                    BlockPos c = Machines.toWorld(tgt, mf[1], mf[0], l.getX(), l.getY(), l.getZ());
                    if (c.equals(sup)) { errs.add(name + ": its footprint reaches into the block it was clicked on"); return null; }
                    if (!c.equals(tgt)) sl.setBlock(c, AIR, Block.UPDATE_CLIENTS);
                }
            }
            BlockState s = b.getStateForPlacement(ctx);
            if (s == null) { errs.add(name + ": refused at " + tgt.toShortString()); return null; }
            sl.setBlock(tgt, s, Block.UPDATE_ALL);
            b.setPlacedBy(sl, tgt, s, pl, stack);
            return tgt;
        } catch (Exception ex) {
            errs.add(name + ": " + ex.getClass().getSimpleName() + " " + ex.getMessage());
            return null;
        } finally {
            pl.setYRot(oy);
            pl.setXRot(ox);
        }
    }

    static int jetFuel(ServerLevel sl, Frame f, int x, int y, int z, Direction localFacing) {
        BlockPos p = f.at(x, y, z);
        Direction facing = f.d(localFacing);
        if (sl.getBlockEntity(p) instanceof PowerCoreBlockEntity c) {
            IFluidHandler h = c.fluidPort(p, Machines.dirToWorld(facing, Direction.WEST));
            if (h != null) return h.fill(new FluidStack(WmdFluids.JET_FUEL.source().get(), Balance.CORE_TANK_MB), IFluidHandler.FluidAction.EXECUTE);
        }
        if (sl.getBlockEntity(p) instanceof FuelTankBlockEntity t) {
            IFluidHandler h = t.fluidPort(p, Machines.dirToWorld(facing, Direction.NORTH));
            if (h != null) return h.fill(new FluidStack(WmdFluids.JET_FUEL.source().get(), Balance.FUEL_TANK_MB), IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    private static BlockState wing() {
        return B("wing_section").defaultBlockState().setValue(WingParts.FACING, Direction.NORTH).setValue(WingParts.FIN, false)
                .setValue(WingParts.SHAPE, WingParts.Shape.PANEL).setValue(WingParts.LEVEL, WingParts.Level.MID);
    }

    private static BlockState surface(WingParts.Role role, boolean fin) {
        return B("control_surface").defaultBlockState().setValue(WingParts.FACING, Direction.NORTH).setValue(WingParts.FIN, fin)
                .setValue(WingParts.LEVEL, WingParts.Level.MID).setValue(WingParts.ROLE, role);
    }

    static void report(CommandSourceStack src, String what, BlockPos at, int fuel, List<String> errs, String hint) {
        say(src, what + " placed at " + at.toShortString() + (fuel > 0 ? String.format(", %,d mB fuel aboard", fuel) : "") + ". " + hint);
        if (!errs.isEmpty()) {
            say(src, errs.size() + " block(s) could not be placed:");
            for (String e : errs) say(src, "  " + e);
        }
    }

    private static int f16(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        BlockPos o = DevAirfield.origin(src);
        if (pl == null || o == null || gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        Frame f = new Frame(o.offset(0, 3, -75), Direction.NORTH);
        List<String> errs = new ArrayList<>();
        clear(sl, f, -9, 9, -2, 5, -2, 18);
        BlockState hull = Blocks.BIRCH_PLANKS.defaultBlockState();
        for (int z = 2; z <= 16; z++) for (int x = -1; x <= 1; x++) put(sl, f, x, 0, z, hull);
        for (int z = 7; z <= 16; z++) for (int x = -1; x <= 1; x++) put(sl, f, x, 1, z, hull);
        for (int x = -1; x <= 1; x++) { put(sl, f, x, 1, 2, hull); put(sl, f, x, 1, 3, hull); }

        for (int s = -1; s <= 1; s += 2) for (int k = 2; k <= 7; k++) {
            for (int z = 5 + (k - 2); z <= 10; z++) put(sl, f, s * k, 0, z, wing());
            if (k >= 3) put(sl, f, s * k, 0, 11, surface(WingParts.Role.AILERON, false));
            else put(sl, f, s * k, 0, 11, wing());
        }
        for (int s = -1; s <= 1; s += 2) for (int k = 2; k <= 4; k++) {
            put(sl, f, s * k, 0, 14, wing());
            put(sl, f, s * k, 0, 15, wing());
            put(sl, f, s * k, 0, 16, surface(WingParts.Role.ELEVATOR, false));
        }
        BlockState fin = wing().setValue(WingParts.FIN, true);
        for (int z = 12; z <= 15; z++) put(sl, f, 0, 2, z, fin);
        for (int z = 13; z <= 15; z++) put(sl, f, 0, 3, z, fin);
        put(sl, f, 0, 2, 16, surface(WingParts.Role.RUDDER, true));
        put(sl, f, 0, 3, 16, surface(WingParts.Role.RUDDER, true));

        machine(sl, pl, f, 0, 1, 6, B("jet_cockpit"), Direction.SOUTH);
        machine(sl, pl, f, 0, 0, 8, B("jet_power_core"), Direction.NORTH);
        machine(sl, pl, f, 0, 0, 9, B("fuel_tank"), Direction.NORTH);
        machine(sl, pl, f, 0, 1, 9, B("fuel_tank"), Direction.NORTH);
        machine(sl, pl, f, 0, 0, 13, B("single_jet"), Direction.NORTH);
        machine(sl, pl, f, 0, -1, 4, B("landing_gear"), Direction.NORTH);
        machine(sl, pl, f, -1, -1, 11, B("landing_gear"), Direction.NORTH);
        machine(sl, pl, f, 1, -1, 11, B("landing_gear"), Direction.NORTH);
        if (sl.getBlockEntity(f.at(0, -1, 4)) instanceof LandingGearBlockEntity g) for (int i = 0; i < 4 && g.role() != LandingGearBlockEntity.NOSE; i++) g.cycleRole();
        placeLike(sl, pl, f, B("nose_radar"), 0, 0, 2, Direction.NORTH, Direction.NORTH, errs);
        for (int s = -1; s <= 1; s += 2) {
            placeLike(sl, pl, f, B("pylon"), s * 3, 0, 9, Direction.DOWN, Direction.NORTH, errs);
            placeLike(sl, pl, f, B("pylon"), s * 5, 0, 10, Direction.DOWN, Direction.NORTH, errs);
            placeLike(sl, pl, f, B("launch_rail"), s * 7, 0, 10, s < 0 ? Direction.WEST : Direction.EAST, Direction.NORTH, errs);
        }
        placeLike(sl, pl, f, B("pylon"), 0, 0, 8, Direction.DOWN, Direction.NORTH, errs);
        placeLike(sl, pl, f, B("gun_pod"), -1, 0, 6, Direction.DOWN, Direction.NORTH, errs);
        placeLike(sl, pl, f, B("countermeasure_dispenser"), 1, 0, 15, Direction.DOWN, Direction.NORTH, errs);
        int fuel = jetFuel(sl, f, 0, 0, 8, Direction.NORTH) + jetFuel(sl, f, 0, 0, 9, Direction.NORTH) + jetFuel(sl, f, 0, 1, 9, Direction.NORTH);
        report(src, "F-16", f.at(0, 0, 0), fuel, errs, "Assemble it, then /wmd dev arm (or /wmd dev arm aam_heat) and the flight check (C).");
        return 1;
    }

    private static int f35(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        BlockPos o = DevAirfield.origin(src);
        if (pl == null || o == null || gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        Frame f = new Frame(o.offset(0, 3, -35), Direction.NORTH);
        List<String> errs = new ArrayList<>();
        clear(sl, f, -9, 9, -2, 5, -2, 18);
        BlockState hull = Blocks.BIRCH_PLANKS.defaultBlockState();
        for (int z = 2; z <= 16; z++) for (int x = -1; x <= 1; x++) if (z < 7 || z > 9) put(sl, f, x, 0, z, hull);
        for (int z = 10; z <= 16; z++) for (int x = -1; x <= 1; x++) put(sl, f, x, 1, z, hull);
        for (int x = -1; x <= 1; x++) { put(sl, f, x, 1, 2, hull); put(sl, f, x, 1, 3, hull); }
        for (int s = -1; s <= 1; s += 2) for (int k = 2; k <= 7; k++) {
            for (int z = 5 + (k - 2); z <= 10; z++) put(sl, f, s * k, 0, z, wing());
            if (k >= 3) put(sl, f, s * k, 0, 11, surface(WingParts.Role.AILERON, false));
            else put(sl, f, s * k, 0, 11, wing());
        }
        for (int s = -1; s <= 1; s += 2) for (int k = 2; k <= 4; k++) {
            put(sl, f, s * k, 0, 14, wing());
            put(sl, f, s * k, 0, 15, wing());
            put(sl, f, s * k, 0, 16, surface(WingParts.Role.ELEVATOR, false));
        }
        BlockState fin = wing().setValue(WingParts.FIN, true);
        for (int s = -1; s <= 1; s += 2) {
            for (int z = 13; z <= 15; z++) put(sl, f, s * 2, 1, z, fin);
            put(sl, f, s * 2, 2, 14, fin); put(sl, f, s * 2, 2, 15, fin);
            put(sl, f, s * 2, 1, 16, surface(WingParts.Role.RUDDER, true));
        }

        machine(sl, pl, f, 0, 1, 6, B("jet_cockpit"), Direction.SOUTH);
        machine(sl, pl, f, 0, 1, 8, B("lift_fan"), Direction.NORTH);
        machine(sl, pl, f, 0, 1, 10, B("jet_power_core"), Direction.NORTH);
        machine(sl, pl, f, 0, 1, 11, B("fuel_tank"), Direction.NORTH);
        machine(sl, pl, f, 0, 1, 12, B("fuel_tank"), Direction.NORTH);
        machine(sl, pl, f, 0, 0, 13, B("single_jet"), Direction.NORTH);
        machine(sl, pl, f, 0, -1, 4, B("landing_gear"), Direction.NORTH);
        machine(sl, pl, f, -2, -1, 11, B("landing_gear"), Direction.NORTH);
        machine(sl, pl, f, 2, -1, 11, B("landing_gear"), Direction.NORTH);
        if (sl.getBlockEntity(f.at(0, -1, 4)) instanceof LandingGearBlockEntity g) for (int i = 0; i < 4 && g.role() != LandingGearBlockEntity.NOSE; i++) g.cycleRole();
        placeLike(sl, pl, f, B("nose_radar"), 0, 0, 2, Direction.NORTH, Direction.NORTH, errs);
        for (int s = -1; s <= 1; s += 2)
            placeLike(sl, pl, f, B("weapons_bay"), s, 0, 10, Direction.DOWN, Direction.NORTH, errs);
        placeLike(sl, pl, f, B("gun_pod"), -1, 0, 5, Direction.DOWN, Direction.NORTH, errs);
        placeLike(sl, pl, f, B("countermeasure_dispenser"), 1, 0, 15, Direction.DOWN, Direction.NORTH, errs);
        int fuel = jetFuel(sl, f, 0, 1, 10, Direction.NORTH) + jetFuel(sl, f, 0, 1, 11, Direction.NORTH) + jetFuel(sl, f, 0, 1, 12, Direction.NORTH);
        report(src, "F-35", f.at(0, 0, 0), fuel, errs, "Assemble it; U = hover (lift fan, throttle = lift), W/S nudge; load the bays with /wmd dev arm.");
        return 1;
    }

    private static int heli(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        if (pl == null || gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        Direction fwd = pl.getDirection();
        BlockPos ahead = pl.blockPosition().relative(fwd, 24);
        int ground = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead.getX(), ahead.getZ());
        Frame f = new Frame(new BlockPos(ahead.getX(), ground + 2, ahead.getZ()), fwd);
        List<String> errs = new ArrayList<>();
        clear(sl, f, -7, 7, -2, 9, -2, 20);
        BlockState hull = Blocks.BIRCH_PLANKS.defaultBlockState();
        for (int z = 0; z <= 9; z++) for (int x = -1; x <= 1; x++) put(sl, f, x, 0, z, hull);
        for (int z = 5; z <= 8; z++) for (int x = -1; x <= 1; x++) put(sl, f, x, 2, z, hull);
        for (int z = 10; z <= 17; z++) put(sl, f, 0, 1, z, hull);
        put(sl, f, 0, 2, 16, hull); put(sl, f, 0, 2, 17, hull); put(sl, f, 0, 3, 17, hull);
        machine(sl, pl, f, 0, 1, 3, B("jet_cockpit"), Direction.SOUTH);
        machine(sl, pl, f, 0, 1, 6, B("heli_turboshaft"), Direction.NORTH);
        machine(sl, pl, f, 0, 1, 8, B("jet_power_core"), Direction.NORTH);
        machine(sl, pl, f, 0, 1, 9, B("fuel_tank"), Direction.NORTH);
        machine(sl, pl, f, 0, 3, 6, B("heli_rotor"), Direction.NORTH);
        machine(sl, pl, f, 1, 1, 17, B("heli_tail"), Direction.NORTH);
        machine(sl, pl, f, 0, -1, 1, B("landing_gear"), Direction.NORTH);
        machine(sl, pl, f, -1, -1, 7, B("landing_gear"), Direction.NORTH);
        machine(sl, pl, f, 1, -1, 7, B("landing_gear"), Direction.NORTH);
        if (sl.getBlockEntity(f.at(0, -1, 1)) instanceof LandingGearBlockEntity g) for (int i = 0; i < 4 && g.role() != LandingGearBlockEntity.NOSE; i++) g.cycleRole();
        for (String id : new String[] { "jet_cockpit", "heli_turboshaft", "jet_power_core", "fuel_tank", "heli_rotor", "heli_tail", "landing_gear" })
            if (B(id) == Blocks.AIR) errs.add(id + ": not registered (1.2 gate off at start-up?)");
        int fuel = jetFuel(sl, f, 0, 1, 8, Direction.NORTH) + jetFuel(sl, f, 0, 1, 9, Direction.NORTH);
        report(src, "Helicopter", f.at(0, 0, 0), fuel, errs, "Assemble it; throttle = collective, W/S/A/D = cyclic, Q/E = tail rotor, U = hover hold. Wrench the rotor head: blade length; empty hand: blade count.");
        return 1;
    }

    private static int ac130(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        BlockPos o = DevAirfield.origin(src);
        if (pl == null || o == null || gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        Frame f = new Frame(o.offset(0, 3, -125), Direction.NORTH);
        List<String> errs = new ArrayList<>();
        clear(sl, f, -25, 25, -2, 16, -1, 39);

        BlockState hull = Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), glass = Blocks.GLASS.defaultBlockState();

        for (int z = 0; z <= 30; z++) {
            int hw = z == 0 ? 2 : 3, top = z == 0 ? 5 : 6;
            for (int x = -hw; x <= hw; x++) for (int y = 0; y <= top; y++) {
                boolean shell = y == 0 || y == top || Math.abs(x) == hw || z == 0 || z == 30;
                boolean wind = z == 0 && ((y >= 3 && y <= 4 && Math.abs(x) <= 1) || (y == 4 && Math.abs(x) == 2));
                boolean side = z >= 1 && z <= 3 && Math.abs(x) == 3 && (y == 4 || y == 5);
                put(sl, f, x, y, z, !shell ? AIR : wind || side ? glass : hull);
            }
        }

        for (int z = 1; z <= 6; z++) for (int x = -2; x <= 2; x++) put(sl, f, x, 2, z, hull);
        BlockState stair = Blocks.BIRCH_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, f.d(Direction.NORTH));
        for (int x = -1; x <= 1; x++) { put(sl, f, x, 1, 8, stair); put(sl, f, x, 2, 7, stair); put(sl, f, x, 1, 7, hull); }

        for (int z = 31; z <= 37; z++) for (int x = -2; x <= 2; x++) if (Math.abs(x) <= (z <= 33 ? 2 : 1))
            for (int y = 5; y <= 6; y++) put(sl, f, x, y, z, hull);

        for (int sx = -1; sx <= 1; sx += 2) for (int z = 16; z <= 22; z++) for (int y = 0; y <= 1; y++) put(sl, f, sx * 4, y, z, hull);

        for (int x = -23; x <= 23; x++) {
            for (int z = 12; z <= 18; z++) put(sl, f, x, 7, z, wing());
            int ax = Math.abs(x);
            put(sl, f, x, 7, 19, ax >= 14 ? surface(WingParts.Role.AILERON, false) : ax >= 4 ? surface(WingParts.Role.FLAP, false) : wing());
        }
        for (int s = -1; s <= 1; s += 2) for (int k = 2; k <= 9; k++) {
            put(sl, f, s * k, 6, 34, wing());
            put(sl, f, s * k, 6, 35, wing());
            put(sl, f, s * k, 6, 36, surface(WingParts.Role.ELEVATOR, false));
        }
        BlockState fin = wing().setValue(WingParts.FIN, true);
        for (int y = 7; y <= 14; y++) {
            for (int z = 31 + (y - 7) / 2; z <= 36; z++) put(sl, f, 0, y, z, fin);
            put(sl, f, 0, y, 37, surface(WingParts.Role.RUDDER, true));
        }

        machine(sl, pl, f, 0, 3, 3, B("flight_deck"), Direction.SOUTH);
        machine(sl, pl, f, 2, 1, 10, B("jet_power_core"), Direction.NORTH);
        machine(sl, pl, f, 2, 1, 11, B("jet_power_core"), Direction.NORTH);
        for (int z = 12; z <= 15; z++) machine(sl, pl, f, 2, 1, z, B("fuel_tank"), Direction.NORTH);

        for (int x = -1; x <= 1; x += 2) {
            machine(sl, pl, f, x, -1, 4, B("landing_gear"), Direction.NORTH);
            if (sl.getBlockEntity(f.at(x, -1, 4)) instanceof LandingGearBlockEntity g) for (int i = 0; i < 4 && g.role() != LandingGearBlockEntity.NOSE; i++) g.cycleRole();
        }
        for (int x = -4; x <= 4; x += 8) for (int z : new int[] { 17, 20 }) machine(sl, pl, f, x, -1, z, B("landing_gear_bogie"), Direction.NORTH);
        for (int x : new int[] { -16, -8, 8, 16 })
            placeLike(sl, pl, f, B("turboprop"), x, 7, 12, Direction.DOWN, Direction.NORTH, errs);

        placeLike(sl, pl, f, B("gunner_station"), 1, 0, 18, Direction.UP, Direction.WEST, errs);
        for (int z : new int[] { 10, 23 }) {
            put(sl, f, -2, 3, z, hull);
            placeLike(sl, pl, f, B("gun_turret"), -2, 3, z, Direction.WEST, Direction.SOUTH, errs);
        }
        placeLike(sl, pl, f, B("heavy_gun"), -3, 0, 26, Direction.UP, Direction.WEST, errs);

        for (int y = 1; y <= 5; y++) for (int x = -2; x <= 2; x++) put(sl, f, x, y, 30, AIR);
        for (int y = 1; y <= 5; y++) for (int x = -2; x <= 2; x++)
            placeLike(sl, pl, f, B("cargo_ramp"), x, y - 1, 30, Direction.UP, Direction.SOUTH, errs);
        int fuel = 0;
        for (int z = 10; z <= 15; z++) fuel += jetFuel(sl, f, 2, 1, z, Direction.NORTH);
        report(src, "AC-130", f.at(0, 0, 0), fuel, errs, "Assemble it; walk in up the ramp, stairs to the flight deck; the guns face west (port). /wmd dev arm loads the turrets + heavy gun.");
        return 1;
    }

    private static int boat(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        if (pl == null || gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        Direction fwd = pl.getDirection();
        BlockPos ahead = pl.blockPosition().relative(fwd, 6);
        BlockPos mid = ahead.relative(fwd, 16);
        int top = NavalLayer.waterTop(sl, mid.getX(), mid.getZ());
        if (top == NavalLayer.NO_WATER) { src.sendFailure(Component.literal("No water ahead: stand on the shore and look out over open water (the boat is ~33 long).")); return 0; }

        Frame f = new Frame(pl.blockPosition().relative(fwd, 6 + 32).atY(top - 1), fwd);
        List<String> errs = new ArrayList<>();
        BlockState hull = Blocks.SPRUCE_PLANKS.defaultBlockState(), pane = Blocks.GLASS_PANE.defaultBlockState();
        clear(sl, f, -6, 6, 0, 9, -1, 34);

        for (int z = 0; z <= 32; z++) {
            int hw = Math.min(4, 1 + (z * 3) / 5);
            for (int x = -hw; x <= hw; x++) for (int y = 0; y <= 3; y++) {
                boolean shell = y == 0 || y == 3 || Math.abs(x) == hw || z == 0 || z == 32;
                put(sl, f, x, y, z, shell ? hull : AIR);
            }
        }

        for (int z = 15; z <= 20; z++) for (int x = -3; x <= 3; x++) for (int y = 4; y <= 7; y++) {
            boolean wall = y == 7 || Math.abs(x) == 3 || z == 15 || z == 20;
            boolean window = y == 5 && (z == 15 || Math.abs(x) == 3) && !(z == 20);
            boolean door = z == 20 && x == 0 && y <= 5;
            put(sl, f, x, y, z, !wall || door ? AIR : window ? pane : hull);
        }

        for (int y = 8; y <= 11; y++) put(sl, f, 0, y, 18, Blocks.SPRUCE_FENCE.defaultBlockState());

        machine(sl, pl, f, -1, 1, 23, Wmd.SUB_DIESEL.get(), Direction.NORTH);
        machine(sl, pl, f, -2, 0, 31, Wmd.SCREW_PROPELLER.get(), Direction.NORTH);
        machine(sl, pl, f, 2, 0, 31, Wmd.SCREW_PROPELLER.get(), Direction.NORTH);
        for (int z = 25; z <= 31; z++) placeLike(sl, pl, f, Wmd.BUSBAR.get(), 1, 0, z, Direction.UP, Direction.SOUTH, errs);
        for (int x : new int[] { 2, 0, -1, -2 }) placeLike(sl, pl, f, Wmd.BUSBAR.get(), x, 0, 31, Direction.UP, Direction.SOUTH, errs);
        int diesel = 0;
        BlockPos dp = f.at(-1, 1, 23);
        if (sl.getBlockEntity(dp) instanceof SubDieselBlockEntity d) {
            Direction fa = f.d(Direction.NORTH);
            BlockPos in = Machines.toWorld(dp, fa, Direction.UP, 1, 0, 0);
            IFluidHandler h = d.fluidPort(in, Machines.dirToWorld(fa, Direction.NORTH));
            if (h != null) diesel = h.fill(new FluidStack(WmdFluids.DIESEL.source().get(), 64_000), IFluidHandler.FluidAction.EXECUTE);
        }

        placeLike(sl, pl, f, Wmd.HELM_STATION.get(), -1, 3, 17, Direction.UP, Direction.NORTH, errs);
        placeLike(sl, pl, f, Wmd.ENGINE_TELEGRAPH.get(), 1, 3, 17, Direction.UP, Direction.NORTH, errs);
        placeLike(sl, pl, f, B("chart_table"), -1, 3, 19, Direction.UP, Direction.NORTH, errs);

        for (int x = -1; x <= 1; x++) for (int z = 7; z <= 9; z++) put(sl, f, x, 2, z, hull);
        placeLike(sl, pl, f, B("deck_gun"), 0, 2, 8, Direction.UP, Direction.NORTH, errs);

        placeLike(sl, pl, f, B("anchor_windlass"), 0, 3, 4, Direction.UP, Direction.NORTH, errs);
        report(src, "Patrol boat", f.at(0, 3, 0), diesel, errs,
                "Assemble it (it is built in the water, keel 1 under the surface), helm + telegraph on the bridge; the diesel starts on AUTO. Arm the deck gun with /wmd dev arm.");
        return 1;
    }
}
