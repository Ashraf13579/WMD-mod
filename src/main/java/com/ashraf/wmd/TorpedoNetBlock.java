package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TorpedoNetBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<TorpedoNetBlock> CODEC = simpleCodec(TorpedoNetBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public static final BooleanProperty PLACED = BooleanProperty.create("placed");

    public static final BooleanProperty CTRL = BooleanProperty.create("ctrl");

    public static final BooleanProperty HOLE = BooleanProperty.create("hole");
    private static final VoxelShape PLANE_X = Block.box(0, 0, 7, 16, 16, 9), PLANE_Z = Block.box(7, 0, 0, 9, 16, 16);

    public TorpedoNetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(PLACED, false).setValue(CTRL, false).setValue(HOLE, false)
                .setValue(Waterlog.W, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, PLACED, CTRL, HOLE, Waterlog.W);
    }

    static boolean solid(BlockState s) {
        return s.getBlock() instanceof TorpedoNetBlock && s.getValue(PLACED) && !s.getValue(HOLE);
    }

    static boolean placed(BlockState s) {
        return s.getBlock() instanceof TorpedoNetBlock && s.getValue(PLACED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return solid(state) ? (state.getValue(AXIS) == Direction.Axis.X ? PLANE_X : PLANE_Z) : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return getShape(state, level, pos, ctx); }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

    @Override
    protected FluidState getFluidState(BlockState state) {
        FluidState f = Waterlog.fluid(state);
        return f != null ? f : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
        Waterlog.tick(state, level, pos);
        return state;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(CTRL) ? new TorpedoNetBlockEntity(pos, state) : null; }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(Wmd.TORPEDO_NET_ITEM.get());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) { return !state.getValue(PLACED); }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PLACED)) return;
        level.setBlock(pos, water(state), 3);
        Block.popResource(level, pos, new ItemStack(Wmd.TORPEDO_NET_ITEM.get()));
        Wmd.LOGGER.info("Torpedo net: an old (9 Nov) net piece at {} was taken down, its item returned", pos.toShortString());
    }

    static BlockState water(BlockState s) {
        return s.hasProperty(Waterlog.W) && s.getValue(Waterlog.W) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl && state.getValue(PLACED)) {
            TorpedoNetBlockEntity be = TorpedoNetBlockEntity.find(sl, pos);
            if (be != null) be.rollUp(sl, pos, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getValue(CTRL) && !newState.is(this) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof TorpedoNetBlockEntity be && !be.rolling)
            be.collapse(sl);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!state.getValue(PLACED)) { super.onBlockExploded(state, level, pos, explosion); return; }
        if (state.getValue(CTRL)) { if (!state.getValue(HOLE)) level.setBlock(pos, state.setValue(HOLE, true), 3); }
        else level.setBlock(pos, water(state), 3);
    }

    static BlockHitResult clip(ServerLevel sl, Vec3 from, Vec3 to, Entity torpedo) {
        BlockHitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, torpedo));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return solid(sl.getBlockState(hit.getBlockPos())) ? hit : null;
    }

    static boolean cut(ServerLevel sl, BlockPos p) {
        BlockState s = sl.getBlockState(p);
        if (!solid(s) || !BlockRules.blastMay(sl, p)) return false;
        if (s.getValue(CTRL)) sl.setBlock(p, s.setValue(HOLE, true), 3);
        else sl.setBlock(p, water(s), 3);
        sl.playSound(null, p, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, Balance.SND_MECH, 0.7F);
        sl.sendParticles(ParticleTypes.BUBBLE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 16, 0.4, 0.4, 0.4, 0.05);
        return true;
    }

    static int tear(ServerLevel sl, Vec3 at) {
        return cutRound(sl, at, Balance.TORP_NET_TEAR);
    }

    static boolean rip(ServerLevel sl, BlockPos hitCell, Vec3 at) {
        if (!cut(sl, hitCell)) return false;
        cutRound(sl, at, Balance.TORP_NET_T4_HOLE);
        return true;
    }

    private static int cutRound(ServerLevel sl, Vec3 at, double r) {
        int n = 0;
        BlockPos c = BlockPos.containing(at);
        int ri = (int) Math.ceil(r);
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            BlockState s = sl.getBlockState(q);
            if (!solid(s)) continue;
            Vec3 m = Vec3.atCenterOf(q);
            double dx = s.getValue(AXIS) == Direction.Axis.X ? m.x - at.x : 0.0, dz = s.getValue(AXIS) == Direction.Axis.Z ? m.z - at.z : 0.0, dy = m.y - at.y;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > r) continue;
            if (cut(sl, q.immutable())) n++;
        }
        return n;
    }

    static boolean shipNear(ServerLevel sl, Vec3 at) {
        if (!SableBridge.active()) return false;
        double r = Balance.TORP_NET_SHIP_SAFE;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(at, at).inflate(r))) {
            AABB b = s.worldBounds();
            double dx = Math.max(Math.max(b.minX - at.x, 0.0), at.x - b.maxX), dy = Math.max(Math.max(b.minY - at.y, 0.0), at.y - b.maxY),
                    dz = Math.max(Math.max(b.minZ - at.z, 0.0), at.z - b.maxZ);
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= r) return true;
        }
        return false;
    }
}
