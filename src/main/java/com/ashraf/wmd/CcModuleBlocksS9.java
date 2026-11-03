package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CcModuleBlocksS9 {

    private CcModuleBlocksS9() {}

    public static class SecurityMonitor extends CcModuleBlock {
        public static final MapCodec<SecurityMonitor> CODEC = simpleCodec(SecurityMonitor::new);
        public SecurityMonitor(Properties p) { super(p, 0, 1, 0, 1, 0, 0, true); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcSecurityMonitorBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_SECURITY_MONITOR_BE.get(); }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (player.isShiftKeyDown()) { MachineMenu.open(player, level.getBlockEntity(controller)); return; }
            if (player instanceof ServerPlayer sp && level.getBlockEntity(controller) instanceof CcSecurityMonitorBlockEntity m) m.open(sp);
        }
    }
}
