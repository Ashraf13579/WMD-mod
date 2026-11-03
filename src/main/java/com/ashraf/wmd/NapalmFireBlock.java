package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NapalmFireBlock extends Block {

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 15);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public NapalmFireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }

    static boolean canSit(LevelReader level, BlockPos p) {
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return Shapes.empty(); }
    @Override protected boolean canBeReplaced(BlockState state, Fluid fluid) { return false; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return canSit(level, pos); }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState other, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return dir == Direction.DOWN && !canSit(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, Balance.NAPALM_STEP + level.random.nextInt(10));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);

        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.getBlockState(n).isFlammable(level, n, d.getOpposite())) continue;
            for (Direction e : Direction.values()) {
                BlockPos air = n.relative(e);
                if (air.equals(pos) || !level.getBlockState(air).isAir() || random.nextInt(3) != 0) continue;
                if (BaseFireBlock.canBePlacedAt(level, air, e) && Config.Server.canBreak(level, air)) { level.setBlock(air, BaseFireBlock.getState(level, air), Block.UPDATE_ALL); break; }
            }
        }
        if (age >= 15) { level.removeBlock(pos, false); return; }
        level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, Balance.NAPALM_STEP + random.nextInt(10));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || entity.fireImmune()) return;
        entity.igniteForSeconds((float) Balance.NAPALM_BURN);
        if (level.getGameTime() % 10 == 0) entity.hurt(level.damageSources().inFire(), 2.0F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.8, pos.getZ() + random.nextDouble(), 0, 0.05, 0);
        if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.FLAME, pos.getX() + random.nextDouble(), pos.getY() + 0.2, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        if (random.nextInt(30) == 0) level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.6F, false);
    }
}
