package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class SalvageGeneratorBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<SalvageGeneratorBlock> CODEC = simpleCodec(SalvageGeneratorBlock::new);

    public SalvageGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineBlock.MOUNT, Direction.UP));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, MachineBlock.MOUNT); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction[] mf = ShipPlace.place(ctx, false, true);
        return mf == null ? null : defaultBlockState().setValue(FACING, mf[1]).setValue(MachineBlock.MOUNT, mf[0]);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
        level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.3, y, z + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.06, 0.0);
        if (random.nextInt(6) == 0) level.playLocalSound(x, y, z, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.25F, 0.5F + random.nextFloat() * 0.1F, false);
        if (random.nextInt(25) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (random.nextDouble() - 0.5), pos.getY() + 0.5, z + (random.nextDouble() - 0.5), 0.0, 0.0, 0.0);
            level.playLocalSound(x, y, z, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.3F, 1.6F, false);
        }
    }
}
