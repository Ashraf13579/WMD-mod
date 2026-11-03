package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CreativeFluidSourceBlock extends Block {

    public static final IntegerProperty FLUID = IntegerProperty.create("fluid", 0, 31);

    private static final int BIG = 1_000_000_000;
    private static final IFluidHandler[] HANDLERS = new IFluidHandler[32];

    public CreativeFluidSourceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FLUID, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLUID);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int n = Math.min(WmdFluids.ALL.size(), 32);
            int i = state.getValue(FLUID);
            i = player.isShiftKeyDown() ? (i + n - 1) % n : (i + 1) % n;
            level.setBlock(pos, state.setValue(FLUID, i), 3);
            player.displayClientMessage(Component.translatable("block.wmd.creative_fluid_source.now",
                    Component.translatable("fluid_type.wmd." + WmdFluids.ALL.get(i).id())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static IFluidHandler handler(BlockState state) {
        int i = Math.min(state.getValue(FLUID), WmdFluids.ALL.size() - 1);
        IFluidHandler h = HANDLERS[i];
        if (h == null) {
            net.minecraft.world.level.material.Fluid fluid = WmdFluids.ALL.get(i).source().get();
            h = new IFluidHandler() {
                @Override public int getTanks() { return 1; }
                @Override public FluidStack getFluidInTank(int t) { return new FluidStack(fluid, BIG); }
                @Override public int getTankCapacity(int t) { return BIG; }
                @Override public boolean isFluidValid(int t, FluidStack s) { return false; }
                @Override public int fill(FluidStack s, FluidAction a) { return 0; }
                @Override public FluidStack drain(FluidStack s, FluidAction a) {
                    return s.getFluid() == fluid ? new FluidStack(fluid, s.getAmount()) : FluidStack.EMPTY;
                }
                @Override public FluidStack drain(int max, FluidAction a) { return max > 0 ? new FluidStack(fluid, max) : FluidStack.EMPTY; }
            };
            HANDLERS[i] = h;
        }
        return h;
    }
}
