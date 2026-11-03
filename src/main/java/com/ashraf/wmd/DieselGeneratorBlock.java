package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DieselGeneratorBlock extends MachineBlock {

    public static final MapCodec<DieselGeneratorBlock> CODEC = simpleCodec(DieselGeneratorBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();
    static {
        for (int x = GeneratorStructure.X0; x <= GeneratorStructure.X1; x++)
            for (int y = GeneratorStructure.Y0; y <= GeneratorStructure.Y1; y++)
                for (int z = GeneratorStructure.Z0; z <= GeneratorStructure.Z1; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public DieselGeneratorBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GeneratorEngineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.DIESEL_GENERATOR_BE.get(), GeneratorEngineBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.DIESEL_GENERATOR_BE.get(), GeneratorEngineBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof GeneratorEngineBlockEntity engine) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GeneratorMenu(id, inventory, engine),
                    Component.translatable("block.wmd.diesel_generator")), buf -> buf.writeBlockPos(controller));
        }
    }
}
