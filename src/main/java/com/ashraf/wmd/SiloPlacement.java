package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class SiloPlacement {

    private SiloPlacement() {}

    static Component blocker(Level level, BlockPos origin, Direction facing, Direction mount, int half, int size, int depth, int above) {
        for (int x = -half; x <= half; x++) for (int z = 0; z < size; z++) for (int y = -depth; y <= above; y++) {
            BlockPos p = Machines.toWorld(origin, facing, mount, x, y, z);
            if (p.equals(origin)) continue;
            if (level.isOutsideBuildHeight(p)) return Component.translatable("message.wmd.silo.height", p.getY());
            BlockState b = level.getBlockState(p);
            if (b.isAir()) continue;
            if (level.getBlockEntity(p) != null) return Component.translatable("message.wmd.silo.machine", b.getBlock().getName(), p.getX(), p.getY(), p.getZ());
            if (b.getDestroySpeed(level, p) < 0 && !b.canBeReplaced()) return Component.translatable("message.wmd.silo.unbreakable", b.getBlock().getName(), p.getX(), p.getY(), p.getZ());
        }
        if (above >= 0) {
            net.minecraft.world.entity.Entity in = PlaceGuard.blocker(level, Machines.toWorld(origin, facing, mount, -half, 0, 0), Machines.toWorld(origin, facing, mount, half, above, size - 1));
            if (in != null) return PlaceGuard.message(in);
        }
        return null;
    }

    static void tell(Player player, Component reason) {
        if (player != null && !player.level().isClientSide) player.displayClientMessage(reason, true);
    }
}
