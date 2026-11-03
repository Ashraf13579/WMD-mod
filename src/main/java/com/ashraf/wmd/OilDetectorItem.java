package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class OilDetectorItem extends Item {

    private static final int RADIUS = 8;
    private static final int COOLDOWN_TICKS = 40;
    private static final String[] DIRECTIONS = {
            "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    public OilDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (EmpField.handheldDead(level, player)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            BlockPos origin = player.blockPosition();
            BlockPos found = findNearestDeposit(level, origin);

            if (found == null) {
                player.displayClientMessage(Component.translatable("item.wmd.oil_detector.none", RADIUS), true);
                level.playSound(null, origin, WmdSounds.SCANNER_PING.get(), SoundSource.PLAYERS, 0.7F, 0.75F);
            } else {
                int dx = found.getX() - origin.getX();
                int dz = found.getZ() - origin.getZ();
                int depth = origin.getY() - found.getY();

                if (dx == 0 && dz == 0) {
                    player.displayClientMessage(Component.translatable("item.wmd.oil_detector.below", depth), true);
                    level.playSound(null, origin, WmdSounds.TOOL_SCANNER.get(), SoundSource.PLAYERS, 0.85F, 1.25F);
                } else {
                    int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                    player.displayClientMessage(Component.translatable("item.wmd.oil_detector.found",
                            distance, compassDirection(dx, dz), depth), true);
                    level.playSound(null, origin, WmdSounds.TOOL_SCANNER.get(), SoundSource.PLAYERS, 0.75F, 1.0F);
                }
            }
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static BlockPos findNearestDeposit(Level level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        int bottom = level.getMinBuildHeight();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist > bestDist) continue;
                for (int y = origin.getY(); y >= bottom; y--) {
                    cursor.set(origin.getX() + dx, y, origin.getZ() + dz);
                    if (level.getBlockState(cursor).is(Wmd.CRUDE_OIL_DEPOSIT.get())) {
                        if (dist < bestDist || (best != null && y > best.getY())) {
                            best = cursor.immutable();
                            bestDist = dist;
                        }
                        break;
                    }
                }
            }
        }
        return best;
    }

    private static String compassDirection(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        return DIRECTIONS[(int) Math.round(angle / 45.0) % 8];
    }
}
