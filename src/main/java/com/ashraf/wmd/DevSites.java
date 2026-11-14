package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ashraf.wmd.DevCraft.Frame;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DevSites {

    private DevSites() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2)).then(Commands.literal("dev").requires(TestCmds::test)
                .then(Commands.literal("airfield")
                        .then(Commands.literal("crew").executes(c -> crew(c.getSource())))
                        .then(Commands.literal("carrier").executes(c -> carrier(c.getSource()))))
                .then(Commands.literal("testfleet").executes(c -> fleet(c.getSource())))));
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST, UP = Direction.UP;

    static Set<BlockPos> cells(ServerLevel sl, BlockPos ctrl) {
        Set<BlockPos> out = new HashSet<>();
        BlockState st = sl.getBlockState(ctrl);
        if (st.getBlock() instanceof MachineBlock mb && st.hasProperty(MachineBlock.FACING)) {
            Direction f = st.getValue(MachineBlock.FACING), m = Machines.mount(st);
            for (BlockPos l : mb.footprint()) out.add(Machines.toWorld(ctrl, f, m, l.getX(), l.getY(), l.getZ()).immutable());
        } else out.add(ctrl.immutable());
        return out;
    }

    static Object[] socket(ServerLevel sl, BlockPos ctrl) {
        if (!(sl.getBlockEntity(ctrl) instanceof MachinePorts mp)) return null;
        Set<BlockPos> cs = cells(sl, ctrl);
        for (BlockPos c : cs) for (Direction d : Direction.values()) {
            BlockPos n = c.relative(d);
            if (cs.contains(n)) continue;
            try { if (mp.energyPort(c, d) == null) continue; } catch (RuntimeException e) { continue; }
            Block nb = sl.getBlockState(n).getBlock();
            if (nb instanceof MachineBlock || nb instanceof MachinePartBlock) continue;
            return new Object[] { n.immutable(), d };
        }
        return null;
    }

    static void busbar(ServerLevel sl, ServerPlayer pl, BlockPos p) {
        sl.setBlock(p, AIR, Block.UPDATE_CLIENTS);
        Block b = Wmd.BUSBAR.get();
        BlockState s = b.getStateForPlacement(new BlockPlaceContext(sl, pl, InteractionHand.MAIN_HAND, new ItemStack(b), new BlockHitResult(Vec3.atCenterOf(p), UP, p, false)));
        sl.setBlock(p, s != null ? s : b.defaultBlockState(), Block.UPDATE_ALL);
    }

    static boolean power(ServerLevel sl, ServerPlayer pl, BlockPos ctrl, List<String> errs) {
        Object[] s = socket(sl, ctrl);
        String name = BuiltInRegistries.BLOCK.getKey(sl.getBlockState(ctrl).getBlock()).getPath();
        if (s == null) { errs.add(name + ": no free busbar socket found"); return false; }
        BlockPos n = (BlockPos) s[0];
        Direction d = (Direction) s[1];

        if (sl.getBlockState(n).isAir() && hugRun(sl, pl, ctrl, n)) return true;
        busbar(sl, pl, n);
        Set<BlockPos> cs = cells(sl, ctrl);
        List<Direction> order = new ArrayList<>(List.of(d, Direction.DOWN, Direction.UP));
        for (Direction o : Direction.values()) if (!order.contains(o)) order.add(o);
        for (Direction o : order) {
            BlockPos q = n.relative(o);
            BlockState qs = sl.getBlockState(q);
            if (cs.contains(q) || !qs.getFluidState().isEmpty() || qs.is(Wmd.BUSBAR.get()) || qs.getBlock() instanceof MachineBlock || qs.getBlock() instanceof MachinePartBlock) continue;
            sl.setBlock(q, Wmd.CREATIVE_BATTERY.get().defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        errs.add(name + ": no room for its creative battery");
        return true;
    }

    private static boolean hugRun(ServerLevel sl, ServerPlayer pl, BlockPos ctrl, BlockPos n) {
        Set<BlockPos> own = cells(sl, ctrl);
        Map<BlockPos, Integer> dist = new HashMap<>();
        Map<BlockPos, BlockPos> prev = new HashMap<>();
        java.util.PriorityQueue<BlockPos> q = new java.util.PriorityQueue<>(java.util.Comparator.comparingInt(dist::get));
        dist.put(n, 0);
        q.add(n);
        BlockPos target = null;
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            if (!c.equals(n) && standing(sl, c, own)) { target = c; break; }
            for (Direction dir : Direction.values()) {
                BlockPos m = c.relative(dir);
                if (m.distManhattan(n) > 12 || own.contains(m)) continue;
                BlockState ms = sl.getBlockState(m);
                if (!ms.isAir() || !ms.getFluidState().isEmpty()) continue;
                int cost = dist.get(c) + 1 + (standing(sl, m, own) ? 0 : 2) + (beside(sl, m, own) ? 0 : 1);
                if (dist.containsKey(m) && dist.get(m) <= cost) continue;
                dist.put(m, cost);
                prev.put(m, c);
                q.remove(m);
                q.add(m);
            }
        }
        if (target == null) return false;
        for (BlockPos c = prev.get(target); c != null; c = prev.get(c)) {
            busbar(sl, pl, c);
            if (c.equals(n)) break;
        }
        sl.setBlock(target, Wmd.CREATIVE_BATTERY.get().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private static boolean standing(ServerLevel sl, BlockPos c, Set<BlockPos> own) {
        BlockPos b = c.below();
        return !own.contains(b) && sl.getBlockState(b).isFaceSturdy(sl, b, Direction.UP);
    }

    private static boolean beside(ServerLevel sl, BlockPos c, Set<BlockPos> own) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos b = c.relative(d);
            if (!own.contains(b) && sl.getBlockState(b).isFaceSturdy(sl, b, d.getOpposite())) return true;
        }
        return false;
    }

    static int fill(ServerLevel sl, BlockPos ctrl, Fluid fluid, int mb) {
        if (!(sl.getBlockEntity(ctrl) instanceof MachinePorts mp)) return 0;
        Set<BlockPos> cs = cells(sl, ctrl);
        int total = 0;
        for (BlockPos c : cs) for (Direction d : Direction.values()) {
            if (cs.contains(c.relative(d))) continue;
            IFluidHandler h;
            try { h = mp.fluidPort(c, d); } catch (RuntimeException e) { h = null; }
            if (h != null) total += h.fill(new FluidStack(fluid, mb), IFluidHandler.FluidAction.EXECUTE);
        }
        return total;
    }

    static boolean busPath(ServerLevel sl, ServerPlayer pl, BlockPos from, BlockPos to, BlockPos lo, BlockPos hi, List<String> errs) {
        Object[] a = socket(sl, from), b = socket(sl, to);
        if (a == null || b == null) { errs.add("busbar path: a socket is missing"); return false; }
        BlockPos start = (BlockPos) a[0], goal = (BlockPos) b[0];
        Map<BlockPos, BlockPos> prev = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        prev.put(start, start);
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            if (p.equals(goal)) break;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (prev.containsKey(n)) continue;
                if (n.equals(goal)) { prev.put(n, p); q.clear(); q.add(n); break; }
                if (n.getX() < lo.getX() || n.getY() < lo.getY() || n.getZ() < lo.getZ() || n.getX() > hi.getX() || n.getY() > hi.getY() || n.getZ() > hi.getZ()) continue;
                BlockState ns = sl.getBlockState(n);
                if (!ns.isAir() && !ns.is(Wmd.BUSBAR.get())) continue;
                prev.put(n, p);
                q.add(n);
            }
        }
        if (!prev.containsKey(goal)) { errs.add("busbar path: no way from " + start.toShortString() + " to " + goal.toShortString()); return false; }
        for (BlockPos p = goal; ; p = prev.get(p)) {
            if (!sl.getBlockState(p).is(Wmd.BUSBAR.get())) busbar(sl, pl, p);
            if (p.equals(start)) break;
        }
        return true;
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)); }

    private static int crew(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        BlockPos o = DevAirfield.origin(src);
        if (pl == null || o == null || DevCraft.gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        Frame f = new Frame(o.offset(-48, 0, -76), N);
        List<String> errs = new ArrayList<>();
        DevCraft.clear(sl, f, -1, 25, 1, 14, -2, 32);
        BlockState floor = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), wall = Blocks.GRAY_CONCRETE.defaultBlockState(),
                lamp = Blocks.SEA_LANTERN.defaultBlockState(), paint = Blocks.YELLOW_CONCRETE.defaultBlockState();
        for (int x = 0; x <= 24; x++) for (int z = 0; z <= 30; z++) {
            boolean box = x >= 7 && x <= 19 && z >= 7 && z <= 19 && (x == 7 || x == 19 || z == 7 || z == 19);
            DevCraft.put(sl, f, x, 0, z, box ? paint : floor);
            DevCraft.put(sl, f, x, 11, z, x % 6 == 3 && z % 6 == 3 ? lamp : wall);
            if (x == 0 || z == 0 || z == 30) for (int y = 1; y <= 10; y++) DevCraft.put(sl, f, x, y, z, wall);
        }

        for (int x = -23; x <= -6; x++) for (int z = -65; z <= -57; z++) {
            sl.setBlock(o.offset(x, 0, z), Blocks.GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int y = 1; y <= 6; y++) sl.setBlock(o.offset(x, y, z), AIR, Block.UPDATE_CLIENTS);
        }

        DevCraft.machine(sl, pl, f, 4, 1, 15, DevCraft.B("ammo_depot"), E);
        BlockPos dep = f.at(4, 1, 15);
        power(sl, pl, dep, errs);
        if (sl.getBlockEntity(dep) instanceof AmmoDepotBlockEntity d) {
            String[][] stock = { { "aam_heat", "8" }, { "aam_radar", "8" }, { "aam_arm", "4" }, { "aam_agm", "4" }, { "air_rocket", "64" }, { "ciws_ammo", "16" },
                    { "flare_pack", "16" }, { "chaff_pod", "16" }, { "ashm", "2" } };
            int slot = 0;
            for (String[] s : stock) {
                Item it = item(s[0]);
                if (it == Items.AIR) continue;
                ItemStack st = new ItemStack(it, Math.min(Integer.parseInt(s[1]), it.getDefaultMaxStackSize()));
                if (Ammo.isAmmo(st) && slot < d.store().getContainerSize()) d.store().setItem(slot++, st);
            }
        }

        loader(sl, pl, f, "ordnance_loader", 9, 5, 2, 14, errs);
        loader(sl, pl, f, "heavy_loader", 19, 5, 3, 14, errs);

        DevCraft.machine(sl, pl, f, 13, -2, 26, DevCraft.B("service_arm"), N);
        BlockPos sa = f.at(13, -2, 26);
        power(sl, pl, sa, errs);
        int fuel = fill(sl, sa, WmdFluids.JET_FUEL.source().get(), Balance.SA_JET_MB) + fill(sl, sa, WmdFluids.DIESEL.source().get(), Balance.SA_DIESEL_MB);

        BlockPos sc = DevCraft.placeLike(sl, pl, f, DevCraft.B("survey_scanner"), 13, 11, 13, Direction.DOWN, UP, errs);
        if (sc != null) power(sl, pl, sc, errs);

        for (int x = 6; x <= 7; x++) for (int y = 1; y <= 2; y++) DevCraft.put(sl, f, x, y, 0, AIR);
        DevCraft.machine(sl, pl, f, 7, 1, 0, DevCraft.B("gantry_cabinet"), S);
        power(sl, pl, f.at(7, 1, 0), errs);
        for (int x = 1; x <= 23; x++) {
            DevCraft.machine(sl, pl, f, x, 8, 0, DevCraft.B("gantry_rail"), S);
            DevCraft.machine(sl, pl, f, x, 8, 30, DevCraft.B("gantry_rail"), N);
        }
        DevCraft.report(src, "Ground crew hangar", f.at(13, 1, 13), fuel, errs,
                "Taxi a jet into the yellow box (from the runway, the taxiway west at z -61). Depot stocked; loaders, service arm, scanner + gantry powered.");
        return 1;
    }

    private static void loader(ServerLevel sl, ServerPlayer pl, Frame f, String id, int cx, int cz, int n, int len, List<String> errs) {
        Block b = DevCraft.B(id);
        DevCraft.machine(sl, pl, f, cx, 0, cz, b, S);
        BlockPos c = f.at(cx, 0, cz);
        Direction fa = f.d(S);
        for (int k = 1; k <= len; k++) for (int gx : new int[] { 0, n - 1 }) {
            BlockPos t = Machines.toWorld(c, fa, UP, gx, 0, -k);
            BlockState ts = DevCraft.B("loader_track").defaultBlockState();
            if (ts.hasProperty(MachineBlock.FACING)) ts = ts.setValue(MachineBlock.FACING, fa);
            if (ts.hasProperty(MachineBlock.MOUNT)) ts = ts.setValue(MachineBlock.MOUNT, UP);
            sl.setBlock(t, ts, Block.UPDATE_ALL);
            DevCraft.B("loader_track").setPlacedBy(sl, t, ts, pl, ItemStack.EMPTY);
        }
        power(sl, pl, c, errs);
    }

    private static final int DW = 31, DL = 140, DC = DW / 2;

    private static int carrier(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        BlockPos o = DevAirfield.origin(src);
        if (pl == null || o == null || DevCraft.gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        Frame f = new Frame(o.offset(-66, 0, -220), N);
        List<String> errs = new ArrayList<>();
        DevCraft.clear(sl, f, -3, DW + 15, 1, 12, -3, DL + 2);
        BlockState deck = Blocks.GRAY_CONCRETE.defaultBlockState(), stripe = Blocks.WHITE_CONCRETE.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(),
                foul = Blocks.YELLOW_CONCRETE.defaultBlockState();
        for (int x = 0; x < DW; x++) for (int z = 0; z <= DL; z++) {
            boolean centre = x == DC && z % 6 < 3 && z > 24;
            boolean edgeLine = (x == 2 || x == DW - 3) && z > 20;
            DevCraft.put(sl, f, x, 0, z, centre ? stripe : edgeLine ? foul : deck);
            DevCraft.put(sl, f, x, -1, z, stone);
        }

        for (int x = -35; x <= -6; x++) for (int z = -84; z <= -80; z++) {
            sl.setBlock(o.offset(x, 0, z), deck, Block.UPDATE_CLIENTS);
            for (int y = 1; y <= 6; y++) sl.setBlock(o.offset(x, y, z), AIR, Block.UPDATE_CLIENTS);
        }

        for (int z = 3; z <= 18; z++) {
            DevCraft.machine(sl, pl, f, DC, 0, z, DevCraft.B("catapult_track"), N);
            power(sl, pl, f.at(DC, 0, z), errs);
        }

        int w0 = DC - 11, w1 = DC + 11;
        for (int z : new int[] { 106, 111, 116, 121 }) {
            DevCraft.machine(sl, pl, f, w0, 0, z, DevCraft.B("arresting_wire"), E);
            DevCraft.machine(sl, pl, f, w1, 0, z, DevCraft.B("arresting_wire"), W);
            power(sl, pl, f.at(w0, 0, z), errs);
            power(sl, pl, f.at(w1, 0, z), errs);
        }

        Block light = DevCraft.B("deck_light");
        for (int z = 0; z <= DL; z += 6) {
            if (z >= 104 && z <= 123) continue;
            DevCraft.machine(sl, pl, f, 0, 0, z, light, N);
            DevCraft.machine(sl, pl, f, DW - 1, 0, z, light, N);
        }
        for (int z = 24; z <= DL - 2; z += 8) {
            if (z >= 56 && z <= 79) continue;
            DevCraft.machine(sl, pl, f, DC, 0, z, light, N);
            if (sl.getBlockEntity(f.at(DC, 0, z)) instanceof DeckLightBlockEntity d) d.cycleMode();
        }
        DevCraft.put(sl, f, -1, 0, 126, deck);
        DevCraft.machine(sl, pl, f, -1, 0, 126, light, N);
        if (sl.getBlockEntity(f.at(-1, 0, 126)) instanceof DeckLightBlockEntity d) for (int i = 0; i < 4; i++) d.cycleMode();

        int lx0 = DW - 21, lz0 = 58, ln = 20;
        for (int x = lx0 - 1; x <= lx0 + ln; x++) for (int z = lz0 - 1; z <= lz0 + ln; z++) for (int y = -17; y <= -1; y++) {
            boolean shaft = x >= lx0 && x < lx0 + ln && z >= lz0 && z < lz0 + ln && y >= -16;
            DevCraft.put(sl, f, x, y, z, shaft ? AIR : stone);
        }
        for (int x = lx0; x < lx0 + ln; x++) for (int z = lz0; z < lz0 + ln; z++) DevCraft.machine(sl, pl, f, x, 0, z, DevCraft.B("deck_lift"), N);
        power(sl, pl, f.at(lx0, 0, lz0), errs);

        for (int x = 2; x <= 12; x++) for (int z = 28; z <= 38; z++) DevCraft.machine(sl, pl, f, x, 0, z, DevCraft.B("turntable"), N);
        power(sl, pl, f.at(2, 0, 28), errs);

        for (int z = 83; z <= 85; z++) for (int x = DW; x <= DW + 3; x++) DevCraft.put(sl, f, x, 0, z, deck);
        DevCraft.machine(sl, pl, f, DW + 1, 1, 84, DevCraft.B("deck_console"), W);
        power(sl, pl, f.at(DW + 1, 1, 84), errs);

        BlockState shed = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        int sx0 = DW + 3, sx1 = DW + 13, sz0 = 90, sz1 = 104;
        for (int x = sx0 - 3; x < sx0; x++) for (int z = 92; z <= 100; z++) DevCraft.put(sl, f, x, 0, z, deck);
        for (int x = sx0; x <= sx1; x++) for (int z = sz0; z <= sz1; z++) {
            DevCraft.put(sl, f, x, 0, z, deck);
            DevCraft.put(sl, f, x, 8, z, shed);
            boolean wallCell = x == sx0 || x == sx1 || z == sz0 || z == sz1;
            for (int y = 1; y <= 7; y++) {
                boolean opening = x == sx0 && z >= 93 && z <= 99 && y <= (z >= 98 ? 5 : 4);
                DevCraft.put(sl, f, x, y, z, wallCell && !opening ? shed : AIR);
            }
        }
        for (int z = 93; z <= 94; z++) for (int x = sx0 - 1; x <= sx0 + 1; x++) DevCraft.put(sl, f, x, 1, z, deck);
        for (int z = 93; z <= 99; z++) {
            int y = z >= 98 ? 6 : 5;
            DevCraft.put(sl, f, sx0, y, z, AIR);
            DevCraft.machine(sl, pl, f, sx0, y, z, DevCraft.B("hangar_door"), W);
        }
        for (int z = 93; z <= 99; z++) {
            int y = z >= 98 ? 6 : 5;
            if (socket(sl, f.at(sx0, y, z)) != null) { power(sl, pl, f.at(sx0, y, z), errs); break; }
        }
        DevCraft.report(src, "Carrier deck", f.at(DC, 0, 70), 0, errs,
                "31 x 141, bow north (taxiway from the stern to the runway). Catapult on the bow centre line, 4 wires near the stern, beacon off the port side, 20 x 20 lift starboard, 11 x 11 turntable port side aft of the catapult, console starboard, door shed east.");
        return 1;
    }

    private static int fleet(CommandSourceStack src) {
        ServerPlayer pl = src.getPlayer();
        BlockPos o = DevAirfield.origin(src);
        if (pl == null || o == null || DevCraft.gated(src)) return 0;
        ServerLevel sl = src.getLevel();
        BlockPos b0 = o.offset(-150, 0, -130);
        BlockState water = Blocks.WATER.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), edge = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = 0; x <= 80; x++) for (int z = 0; z <= 60; z++) {
            boolean rim = x == 0 || z == 0 || x == 80 || z == 60;
            boolean trench = x >= 58 && x <= 74 && z >= 6 && z <= 22;
            for (int y = 1; y <= 10; y++) sl.setBlock(b0.offset(x, y, z), AIR, Block.UPDATE_CLIENTS);
            if (rim) { for (int y = -8; y <= 0; y++) sl.setBlock(b0.offset(x, y, z), y == 0 ? edge : stone, Block.UPDATE_CLIENTS); continue; }
            int bottom = trench ? -30 : -8;
            sl.setBlock(b0.offset(x, bottom, z), stone, Block.UPDATE_CLIENTS);
            for (int y = bottom + 1; y <= 0; y++) sl.setBlock(b0.offset(x, y, z), water, Block.UPDATE_CLIENTS);
        }
        List<String> errs = new ArrayList<>();
        int top = 0;

        int[] xs = { 8, 19, 30, 41, 52 };
        for (int i = 0; i < xs.length; i++) {
            Frame f = new Frame(b0.offset(xs[i], top - 2, 20), N);
            barge(sl, f);
            switch (i) {
                case 0 -> {
                    DevCraft.machine(sl, pl, f, 0, 1, 10, DevCraft.B("pump_jet"), N);
                    power(sl, pl, f.at(0, 1, 10), errs);
                }
                case 1 -> {
                    DevCraft.machine(sl, pl, f, -1, 1, 3, Wmd.SUB_DIESEL.get(), N);
                    fill(sl, f.at(-1, 1, 3), WmdFluids.DIESEL.source().get(), 64_000);
                    DevCraft.machine(sl, pl, f, -2, 1, 11, Wmd.SCREW_PROPELLER.get(), N);
                    DevCraft.machine(sl, pl, f, 2, 1, 11, Wmd.SCREW_PROPELLER.get(), N);
                    BlockPos lo = inner(f, -2, 1, 1, 2, 3, 11, true), hi = inner(f, -2, 1, 1, 2, 3, 11, false);
                    busPath(sl, pl, f.at(-1, 1, 3), f.at(-2, 1, 11), lo, hi, errs);
                    busPath(sl, pl, f.at(-1, 1, 3), f.at(2, 1, 11), lo, hi, errs);
                    DevCraft.machine(sl, pl, f, 0, 1, 8, DevCraft.B("snorkel_mast"), N);
                    power(sl, pl, f.at(0, 1, 8), errs);
                }
                case 2 -> {
                    DevCraft.machine(sl, pl, f, -1, 1, 4, DevCraft.B("jet_power_core"), N);
                    DevCraft.machine(sl, pl, f, 1, 1, 4, DevCraft.B("fuel_tank"), N);
                    DevCraft.jetFuel(sl, f, -1, 1, 4, N);
                    DevCraft.jetFuel(sl, f, 1, 1, 4, N);
                    DevCraft.placeLike(sl, pl, f, DevCraft.B("ducted_fan"), -2, 4, 7, UP, N, errs);
                    DevCraft.placeLike(sl, pl, f, DevCraft.B("turbofan"), 2, 4, 7, UP, N, errs);
                    DevCraft.placeLike(sl, pl, f, DevCraft.B("jato_rack"), 0, 4, 11, UP, N, errs);
                }
                case 3 -> {
                    DevCraft.machine(sl, pl, f, -1, 1, 3, DevCraft.B("ship_turbine"), N);
                    fill(sl, f.at(-1, 1, 3), WmdFluids.JET_FUEL.source().get(), 64_000);
                    power(sl, pl, f.at(-1, 1, 3), errs);
                    DevCraft.machine(sl, pl, f, -2, 1, 10, DevCraft.B("waterjet"), N);
                    DevCraft.machine(sl, pl, f, 2, 1, 10, DevCraft.B("waterjet"), N);
                    DevCraft.machine(sl, pl, f, 0, 1, 1, DevCraft.B("bow_thruster"), N);
                    power(sl, pl, f.at(0, 1, 1), errs);
                }
                default -> {
                    DevCraft.machine(sl, pl, f, -2, 1, 10, DevCraft.B("azimuth_pod"), N);
                    DevCraft.machine(sl, pl, f, 1, 1, 10, DevCraft.B("azimuth_pod"), N);
                    power(sl, pl, f.at(-2, 1, 10), errs);
                    power(sl, pl, f.at(1, 1, 10), errs);
                }
            }
            DevCraft.placeLike(sl, pl, f, Wmd.HELM_STATION.get(), -1, 4, 6, UP, N, errs);
            DevCraft.placeLike(sl, pl, f, Wmd.ENGINE_TELEGRAPH.get(), 1, 4, 6, UP, N, errs);
        }

        Frame c = new Frame(b0.offset(66, top - 2, 10), N);
        cruiser(sl, pl, c, errs);
        DevCraft.report(src, "Test fleet", b0.offset(40, 1, 30), 0, errs,
                "Barges west to east: pump-jet, diesel + snorkel + screws, air engines, gas turbine + waterjets + bow thruster, azimuth pods. Cruiser east over the 30-deep trench. Assemble each, then /wmd dev arm.");
        return 1;
    }

    private static BlockPos inner(Frame f, int x0, int y0, int z0, int x1, int y1, int z1, boolean min) {
        BlockPos p = f.at(x0, y0, z0), q = f.at(x1, y1, z1);
        return min ? new BlockPos(Math.min(p.getX(), q.getX()), Math.min(p.getY(), q.getY()), Math.min(p.getZ(), q.getZ()))
                : new BlockPos(Math.max(p.getX(), q.getX()), Math.max(p.getY(), q.getY()), Math.max(p.getZ(), q.getZ()));
    }

    private static void barge(ServerLevel sl, Frame f) {
        BlockState hull = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 6; y++) for (int z = 0; z <= 12; z++) {
            boolean shell = y <= 4 && (y == 0 || y == 4 || Math.abs(x) == 3 || z == 0 || z == 12);
            DevCraft.put(sl, f, x, y, z, shell ? hull : AIR);
        }
    }

    private static void cruiser(ServerLevel sl, ServerPlayer pl, Frame f, List<String> errs) {
        BlockState hull = Blocks.GRAY_CONCRETE.defaultBlockState(), pane = Blocks.GLASS_PANE.defaultBlockState();
        for (int z = 0; z <= 44; z++) {
            int hw = Math.min(5, 1 + (z * 4) / 6);
            for (int x = -5; x <= 5; x++) for (int y = 0; y <= 10; y++) {
                boolean in = Math.abs(x) <= hw;
                if (!in) continue;
                boolean shell = in && y <= 4 && (y == 0 || y == 4 || Math.abs(x) == hw || z == 0 || z == 44);
                DevCraft.put(sl, f, x, y, z, shell ? hull : AIR);
            }
        }

        for (int x = -2; x <= 2; x++) for (int z = 10; z <= 14; z++) { DevCraft.put(sl, f, x, 4, z, AIR); DevCraft.put(sl, f, x, 3, z, AIR); DevCraft.put(sl, f, x, 2, z, hull); }
        DevCraft.placeLike(sl, pl, f, DevCraft.B("battleship_turret"), 0, 2, 12, UP, N, errs);

        for (int x = -1; x <= 1; x++) for (int z = 35; z <= 37; z++) { DevCraft.put(sl, f, x, 4, z, AIR); DevCraft.put(sl, f, x, 3, z, hull); }
        DevCraft.placeLike(sl, pl, f, DevCraft.B("deck_gun"), 0, 3, 36, UP, S, errs);

        for (int z = 20; z <= 25; z++) for (int x = -4; x <= 4; x++) for (int y = 5; y <= 8; y++) {
            boolean wallC = y == 8 || Math.abs(x) == 4 || z == 20 || z == 25;
            boolean window = y == 6 && (z == 20 || Math.abs(x) == 4);
            boolean door = z == 25 && x == 0 && y <= 6;
            DevCraft.put(sl, f, x, y, z, !wallC || door ? AIR : window ? pane : hull);
        }
        DevCraft.placeLike(sl, pl, f, Wmd.HELM_STATION.get(), -1, 4, 22, UP, N, errs);
        DevCraft.placeLike(sl, pl, f, Wmd.ENGINE_TELEGRAPH.get(), 1, 4, 22, UP, N, errs);
        DevCraft.placeLike(sl, pl, f, DevCraft.B("chart_table"), -2, 4, 24, UP, N, errs);
        DevCraft.placeLike(sl, pl, f, DevCraft.B("gunner_station"), 2, 4, 24, UP, N, errs);

        DevCraft.placeLike(sl, pl, f, DevCraft.B("anchor_windlass"), 0, 4, 5, UP, N, errs);

        DevCraft.machine(sl, pl, f, -1, 1, 30, Wmd.SUB_DIESEL.get(), N);
        fill(sl, f.at(-1, 1, 30), WmdFluids.DIESEL.source().get(), 64_000);
        DevCraft.machine(sl, pl, f, -3, 1, 43, Wmd.SCREW_PROPELLER.get(), N);
        DevCraft.machine(sl, pl, f, 3, 1, 43, Wmd.SCREW_PROPELLER.get(), N);
        BlockPos a = inner(f, -4, 1, 26, 4, 3, 43, true), b = inner(f, -4, 1, 26, 4, 3, 43, false);
        busPath(sl, pl, f.at(-1, 1, 30), f.at(-3, 1, 43), a, b, errs);
        busPath(sl, pl, f.at(-1, 1, 30), f.at(3, 1, 43), a, b, errs);

        for (BlockPos g : new BlockPos[] { f.at(0, 3, 12), f.at(0, 4, 36) })
            if (sl.getBlockEntity(g) != null && socket(sl, g) != null) power(sl, pl, g, errs);
    }
}
