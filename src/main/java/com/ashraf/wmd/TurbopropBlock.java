package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TurbopropBlock extends P6DriveBlock {

    public static final MapCodec<TurbopropBlock> CODEC = simpleCodec(TurbopropBlock::new);

    public TurbopropBlock(Properties properties) { super(properties, "turboprop", 1, 1, 3, true); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.TURBOPROP_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newTurboprop(pos, state); }

    @Override
    protected boolean wrench(Level level, AirEngineBlockEntity be, Player player) {
        if (!(be instanceof TurbopropBlockEntity tp)) return false;
        String refused = tp.cycleBlades();
        player.displayClientMessage(refused != null ? Component.translatable(refused)
                : Component.translatable("message.wmd.turboprop.blades", tp.blades(), Component.translatable(tp.contra() ? "message.wmd.turboprop.contra" : "message.wmd.turboprop.single")), true);
        if (refused == null) level.playSound(null, be.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
        return true;
    }
}
