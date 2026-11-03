package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ReactorCommands {

    private ReactorCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reactor")
                        .then(Commands.literal("build")
                                .executes(c -> build(c.getSource(), true))
                                .then(Commands.literal("bare").executes(c -> build(c.getSource(), false))))
                        .then(Commands.literal("fill").executes(c -> fill(c.getSource())))
                        .then(Commands.literal("heat")
                                .then(Commands.argument("heat", IntegerArgumentType.integer(0, Balance.REACTOR_HEAT_MAX))
                                        .executes(c -> heat(c.getSource(), IntegerArgumentType.getInteger(c, "heat")))))));
    }

    private static int build(CommandSourceStack src, boolean full) {
        ServerLevel level = src.getLevel();
        Direction look = src.getEntity() != null ? src.getEntity().getDirection() : Direction.NORTH;
        Direction facing = look.getOpposite();
        BlockPos feet = BlockPos.containing(src.getPosition());

        BlockPos ctrl = feet.relative(look, 3).above(ReactorStructure.CTRL_Y - ReactorStructure.Y0);

        for (int y = ReactorStructure.Y0; y <= ReactorStructure.Y1 + 1; y++)
            for (int z = ReactorStructure.Z0; z <= ReactorStructure.Z1; z++)
                for (int x = ReactorStructure.X0; x <= ReactorStructure.X1; x++)
                    level.setBlock(ReactorStructure.world(ctrl, facing, x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);

        if (full) level.setBlock(ctrl, Wmd.NUCLEAR_REACTOR.get().defaultBlockState().setValue(NuclearReactorBlock.FACING, facing), 3);
        for (int y = ReactorStructure.Y0; y <= ReactorStructure.Y1; y++)
            for (int z = ReactorStructure.Z0; z <= ReactorStructure.Z1; z++)
                for (int x = ReactorStructure.X0; x <= ReactorStructure.X1; x++) {
                    char c = ReactorStructure.at(x, y, z);
                    if (c == 'K' || c == '.') continue;
                    Block b = ReactorStructure.blockFor(c);
                    if (b != null) level.setBlock(ReactorStructure.world(ctrl, facing, x, y, z), b.defaultBlockState(), 3);
                }
        if (!full) {
            src.sendSuccess(() -> Component.literal("Reactor parts placed. The controller cell is empty at " + ctrl.toShortString()
                    + ": place a Nuclear Reactor Controller there (from where you stand) and it forms"), true);
            return 1;
        }

        BlockState cs = level.getBlockState(ctrl);
        ReactorStructure.Check chk = ReactorStructure.check(level, ctrl, facing, Direction.UP);
        if (!chk.complete()) { src.sendFailure(Component.literal("Pattern check failed: " + chk.wrong() + " cells wrong, first at " + chk.first().toShortString())); return 0; }
        if (cs.getBlock() instanceof NuclearReactorBlock rb && !cs.getValue(NuclearReactorBlock.FORMED)) rb.form(level, ctrl, null);
        if (level.getBlockEntity(ctrl) instanceof NuclearReactorBlockEntity be) be.testFill();
        if (level.getBlockEntity(ctrl) instanceof NuclearReactorBlockEntity be) be.setTestPowered();

        Direction turbineFacing = facing.getOpposite();
        Direction towerFacing = facing.getCounterClockWise();
        BlockPos turbine = ReactorStructure.world(ctrl, facing, 0, 0, 13);
        BlockPos tower = ReactorStructure.world(ctrl, facing, -19, 0, 9);
        clear(level, turbine, turbineFacing, SteamTurbineBlockEntity.X0, SteamTurbineBlockEntity.X1, SteamTurbineBlockEntity.Y0, SteamTurbineBlockEntity.Y1,
                SteamTurbineBlockEntity.Z0, SteamTurbineBlockEntity.Z1);
        clear(level, tower, towerFacing, CoolingTowerBlockEntity.X0, CoolingTowerBlockEntity.X1, CoolingTowerBlockEntity.Y0, CoolingTowerBlockEntity.Y1 + 1,
                CoolingTowerBlockEntity.Z0, CoolingTowerBlockEntity.Z1);
        placeMachine(level, Wmd.STEAM_TURBINE.get(), turbine, turbineFacing);
        placeMachine(level, Wmd.COOLING_TOWER.get(), tower, towerFacing);
        if (level.getBlockEntity(tower) instanceof CoolingTowerBlockEntity t) { t.setTestPowered(); t.testFill(); }

        java.util.List<BlockPos> pipes = new java.util.ArrayList<>();
        int[][] steam = { { 5, 1, 8 }, { 4, 1, 9 }, { 5, 1, 9 } };
        for (int[] c : steam) pipes.add(ReactorStructure.world(ctrl, facing, c[0], c[1], c[2]));
        for (int x = 5; x <= 6; x++) for (int z = 8; z <= 12; z++) pipes.add(ReactorStructure.world(ctrl, facing, x, 0, z));
        for (int x = -8; x <= -5; x++) for (int z = 11; z <= 12; z++) pipes.add(ReactorStructure.world(ctrl, facing, x, 0, z));
        for (int x = -8; x <= -5; x++) for (int z = 6; z <= 7; z++) pipes.add(ReactorStructure.world(ctrl, facing, x, 0, z));
        for (int x = -6; x <= -5; x++) for (int z = -1; z <= 5; z++) pipes.add(ReactorStructure.world(ctrl, facing, x, 0, z));
        pipes.add(ReactorStructure.world(ctrl, facing, -5, 1, 4));
        for (int x = -4; x <= 5; x++) pipes.add(ReactorStructure.world(ctrl, facing, x, 0, -1));
        for (int z = 0; z <= 4; z++) pipes.add(ReactorStructure.world(ctrl, facing, 5, 0, z));
        pipes.add(ReactorStructure.world(ctrl, facing, 5, 1, 4));
        BlockState pipe = Wmd.FLUID_PIPE.get().defaultBlockState();
        for (BlockPos p : pipes) level.setBlock(p, pipe, 3);
        for (BlockPos p : pipes) {
            BlockState now = level.getBlockState(p);
            BlockState next = Block.updateFromNeighbourShapes(now, level, p);
            if (next != now) level.setBlock(p, next, 3);
        }
        src.sendSuccess(() -> Component.literal("Steam loop built at " + ctrl.toShortString() + ": reactor (formed, 4 fuel rods, full water, test-powered pumps), steam turbine,"
                + " cooling tower (test-powered, full water) and " + pipes.size() + " pipes. Open the controller and pick a rod level."), true);
        return 1;
    }

    private static void clear(ServerLevel level, BlockPos c, Direction f, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++)
            level.setBlock(Machines.toWorld(c, f, x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    private static void placeMachine(ServerLevel level, MachineBlock block, BlockPos c, Direction f) {
        BlockState st = block.defaultBlockState().setValue(MachineBlock.FACING, f);
        level.setBlock(c, st, 3);
        block.setPlacedBy(level, c, st, null, ItemStack.EMPTY);
    }

    private static NuclearReactorBlockEntity nearest(CommandSourceStack src) {
        java.util.List<NuclearReactorBlockEntity> l = NuclearReactorBlockEntity.near(src.getLevel(), BlockPos.containing(src.getPosition()), 32);
        if (l.isEmpty()) src.sendFailure(Component.literal("No formed reactor within 32 blocks"));
        return l.isEmpty() ? null : l.get(0);
    }

    private static int fill(CommandSourceStack src) {
        NuclearReactorBlockEntity be = nearest(src);
        if (be == null) return 0;
        be.testFill();
        src.sendSuccess(() -> Component.literal("Reactor filled: 4 fuel rods + full water"), true);
        return 1;
    }

    private static int heat(CommandSourceStack src, int h) {
        NuclearReactorBlockEntity be = nearest(src);
        if (be == null) return 0;
        be.testHeat(h);
        src.sendSuccess(() -> Component.literal("Reactor heat set to " + Math.min(h, Balance.REACTOR_HEAT_MAX - 1)), true);
        return 1;
    }
}
