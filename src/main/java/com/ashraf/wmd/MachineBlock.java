package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class MachineBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final DirectionProperty MOUNT = DirectionProperty.create("mount");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    static boolean removing;

    protected MachineBlock(Properties properties) {
        super(properties);
        BlockState d = stateDefinition.any().setValue(FACING, Direction.NORTH);
        if (d.hasProperty(MOUNT)) d = d.setValue(MOUNT, Direction.UP);
        registerDefaultState(d);
    }

    protected boolean mountable() { return true; }

    public boolean anyFace() { return false; }

    protected abstract List<BlockPos> footprint();

    public VoxelShape machineShape() { return SHAPE; }

    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        return g != null ? g : machineShape();
    }

    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) { return machineShape(local, facing); }

    public VoxelShape machineCollision(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) { return machineShape(level, controller, local, facing); }

    protected final VoxelShape generated(BlockPos local, Direction facing) {
        return MachineShapes.get(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).getPath(), local, facing);
    }

    public abstract void interact(Level level, BlockPos controller, Player player);

    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof Owned o && !gate(level, controller, player, o)) return;
        interact(level, controller, player);
    }

    static boolean gate(Level level, BlockPos controller, Player player, Owned o) {
        if (player.getUUID().equals(o.getOwner()) && Hacks.clear(level, controller))
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.hack.relocked"), true);
        if (o.getOwner() == null) o.claim(player);
        else if (!Access.mayUse(o, player)) { Access.deny(o, player); return false; }
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        if (mountable()) builder.add(MOUNT);
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
        BlockState s = state.setValue(FACING, rot.rotate(state.getValue(FACING)));
        if (s.hasProperty(MOUNT) && s.getValue(MOUNT).getAxis() != Direction.Axis.Y) s = s.setValue(MOUNT, rot.rotate(s.getValue(MOUNT)));
        return s;
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror m) {
        BlockState s = state.setValue(FACING, m.mirror(state.getValue(FACING)));
        if (s.hasProperty(MOUNT) && s.getValue(MOUNT).getAxis() != Direction.Axis.Y) s = s.setValue(MOUNT, m.mirror(s.getValue(MOUNT)));
        return s;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Machines.mountShape(machineShape(BlockPos.ZERO, state.getValue(FACING)), Machines.mount(state));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] mf = ShipPlace.place(context, anyFace());
        if (mf == null) return null;
        Direction mount = mf[0], facing = mf[1];
        BlockPos origin = context.getClickedPos();
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (BlockPos l : footprint()) {
            BlockPos p = Machines.toWorld(origin, facing, mount, l.getX(), l.getY(), l.getZ());
            if (!context.getLevel().isInWorldBounds(p)) return null;
            if (!p.equals(origin) && !context.getLevel().getBlockState(p).canBeReplaced()) return null;
            cells.add(p);
        }

        net.minecraft.world.entity.Entity in = PlaceGuard.blocker(context.getLevel(), cells);
        if (in != null) { PlaceGuard.tell(context.getLevel(), context.getPlayer(), in); return null; }
        if (!ClaimGuard.mayPlace(context.getLevel(), context.getPlayer(), cells, origin)) return null;
        BlockState st = defaultBlockState().setValue(FACING, facing);
        return st.hasProperty(MOUNT) ? st.setValue(MOUNT, mount) : st;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING), mount = Machines.mount(state);
        for (BlockPos l : footprint()) {
            BlockPos p = Machines.toWorld(pos, facing, mount, l.getX(), l.getY(), l.getZ());
            if (p.equals(pos)) continue;
            level.setBlock(p, Wmd.MACHINE_PART.get().defaultBlockState(), 3);
            if (level.getBlockEntity(p) instanceof MachinePartBlockEntity be) be.setController(pos);
        }
        if (placer instanceof Player pl && level.getBlockEntity(pos) instanceof Owned o) o.claim(pl);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) Hacks.clear(level, pos);
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this)) {
            removing = true;
            try {
                Direction facing = state.getValue(FACING), mount = Machines.mount(state);
                for (BlockPos l : footprint()) {
                    BlockPos p = Machines.toWorld(pos, facing, mount, l.getX(), l.getY(), l.getZ());

                    if (!p.equals(pos) && level.getBlockState(p).is(Wmd.MACHINE_PART.get()) && ownPart(level, p, pos)) level.removeBlock(p, false);
                }
            } finally {
                removing = false;
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static boolean ownPart(Level level, BlockPos p, BlockPos ctrl) {
        return !(level.getBlockEntity(p) instanceof MachinePartBlockEntity part) || part.getController() == null || part.getController().equals(ctrl);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                               net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof HackingDeviceItem) return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) interact(level, pos, player, hit);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
