package com.ashraf.wmd;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class BatteryBlock extends BaseEntityBlock {

    public enum Part implements StringRepresentable {
        FRONT_BOTTOM("front_bottom"), BACK_BOTTOM("back_bottom"), FRONT_TOP("front_top"), BACK_TOP("back_top");

        private final String name;

        Part(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final MapCodec<BatteryBlock> CODEC = simpleCodec(BatteryBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    public BatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(PART, Part.FRONT_BOTTOM).setValue(CHARGE, 0).setValue(MachineBlock.MOUNT, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, CHARGE, MachineBlock.MOUNT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public static BlockPos cell(BlockPos main, Direction facing, Part part) { return cell(main, facing, Direction.UP, part); }

    public static BlockPos cell(BlockPos main, Direction facing, Direction mount, Part part) {
        Direction back = Machines.dirToWorld(facing, mount, Direction.SOUTH);
        return switch (part) {
            case FRONT_BOTTOM -> main;
            case BACK_BOTTOM -> main.relative(back);
            case FRONT_TOP -> main.relative(mount);
            case BACK_TOP -> main.relative(mount).relative(back);
        };
    }

    public static BlockPos mainOf(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING), mount = Machines.mount(state), front = Machines.dirToWorld(facing, mount, Direction.NORTH), down = mount.getOpposite();
        return switch (state.getValue(PART)) {
            case FRONT_BOTTOM -> pos;
            case BACK_BOTTOM -> pos.relative(front);
            case FRONT_TOP -> pos.relative(down);
            case BACK_TOP -> pos.relative(down).relative(front);
        };
    }

    public static boolean isMain(Level level, BlockPos pos, Direction facing, Direction mount) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BatteryBlock
                && state.getValue(PART) == Part.FRONT_BOTTOM && state.getValue(FACING) == facing && Machines.mount(state) == mount;
    }

    public static Direction rowDirection(Direction facing, Direction mount) {
        return Machines.dirToWorld(facing, mount, Direction.WEST);
    }

    private static int countNeighbours(Level level, BlockPos main, Direction facing, Direction mount, Direction dir) {
        int n = 0;
        BlockPos p = main.relative(dir);
        while (n < BatteryBlockEntity.MAX_ROW && isMain(level, p, facing, mount)) {
            n++;
            p = p.relative(dir);
        }
        return n;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction[] mf = ShipPlace.place(context, false, true);
        if (mf == null) return null;
        Direction mount = mf[0], facing = mf[1];
        List<BlockPos> cells = new java.util.ArrayList<>(4);
        for (Part part : Part.values()) {
            BlockPos p = cell(pos, facing, mount, part);
            cells.add(p);
            if (level.isOutsideBuildHeight(p)) return null;
            if (!p.equals(pos) && !level.getBlockState(p).canBeReplaced()) return null;
            net.minecraft.world.entity.Entity in = PlaceGuard.blocker(level, p);
            if (in != null) { PlaceGuard.tell(level, context.getPlayer(), in); return null; }
        }

        Direction row = rowDirection(facing, mount);
        int length = 1 + countNeighbours(level, pos, facing, mount, row) + countNeighbours(level, pos, facing, mount, row.getOpposite());
        if (length > BatteryBlockEntity.MAX_ROW) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("block.wmd.battery.too_long"), true);
            }
            return null;
        }
        if (!ClaimGuard.mayPlace(level, context.getPlayer(), cells, pos)) return null;
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.FRONT_BOTTOM).setValue(MachineBlock.MOUNT, mount);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING), mount = Machines.mount(state);
        for (Part part : Part.values()) {
            if (part == Part.FRONT_BOTTOM) continue;
            level.setBlock(cell(pos, facing, mount, part), state.setValue(PART, part), 3);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {

        if (!level.isClientSide && !player.isCreative() && state.getValue(PART) != Part.FRONT_BOTTOM) {
            popResource(level, pos, new ItemStack(this));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this)) {
            Direction facing = state.getValue(FACING), mount = Machines.mount(state);
            BlockPos main = mainOf(pos, state);
            for (Part part : Part.values()) {
                BlockPos p = cell(main, facing, mount, part);
                if (p.equals(pos)) continue;
                BlockState other = level.getBlockState(p);
                if (other.is(this) && other.getValue(FACING) == facing && other.getValue(PART) == part) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.BATTERY_BE.get(), (l, p, s, be) -> be.migrateToT2());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.FRONT_BOTTOM ? new BatteryBlockEntity(pos, state) : null;
    }

    @Nullable
    public static IEnergyStorage energyAt(Level level, BlockPos pos, BlockState state) {
        List<BatteryBlockEntity> row = BatteryBlockEntity.row(level, mainOf(pos, state), state.getValue(FACING), Machines.mount(state));
        return row.isEmpty() ? null : new BatteryBlockEntity.Bank(row);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            List<BatteryBlockEntity> row = BatteryBlockEntity.row(level, mainOf(pos, state), state.getValue(FACING), Machines.mount(state));
            BatteryBlockEntity.Bank bank = new BatteryBlockEntity.Bank(row);
            player.displayClientMessage(Component.translatable("block.wmd.battery.bank_status",
                    String.format("%,d", bank.getEnergyStored()),
                    String.format("%,d", bank.getMaxEnergyStored()),
                    row.size()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
