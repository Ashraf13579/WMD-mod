package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class ReconDroneItem extends Item {

    public ReconDroneItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        int range = Math.min(Balance.DRONE_RANGE, Math.max(32, sl.getServer().getPlayerList().getViewDistance() * 16));
        int tx, tz;
        CcTarget des = CcTarget.of(sp.getUUID());
        if (des != null) { tx = des.x(); tz = des.z(); }
        else {
            BlockPos hit = look(sl, sp, range);
            if (hit == null) {
                sp.displayClientMessage(Component.translatable("message.wmd.drone.no_target", range).withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            tx = hit.getX(); tz = hit.getZ();
        }
        Component why = Drones.launch(sp, tx, tz);
        if (why != null) {
            sp.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        sp.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.consume(stack);
    }

    static BlockPos look(ServerLevel level, ServerPlayer p, int range) {
        Vec3 eye = p.getEyePosition(), dir = p.getViewVector(1.0F);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double d = 1.0; d <= range; d += 0.5) {
            double x = eye.x + dir.x * d, y = eye.y + dir.y * d, z = eye.z + dir.z * d;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return null;
            pos.set(x, y, z);
            LevelChunk ch = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (ch == null) return null;
            if (!ch.getBlockState(pos).isAir()) return pos.immutable();
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.recon_drone", Balance.DRONE_LOITER_S, Balance.DRONE_RADIUS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.recon_drone.use", Balance.DRONE_RANGE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
