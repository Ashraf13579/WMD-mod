package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class GroundScannerItem extends Item {

    public static final String[] ORES = {"rutile", "wolframite", "uraninite"};
    private static final int RADIUS = 48;
    private static final int BAND_TOP = -32;
    private static final int COOLDOWN_SCAN = 60, COOLDOWN_SAMPLE = 100;
    private static final String[] DIRECTIONS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    public GroundScannerItem(Properties properties) {
        super(properties);
    }

    public static int tuned(ItemStack stack) {
        Integer v = stack.get(WmdComponents.SCANNER_ORE.get());
        return v == null ? 0 : Math.floorMod(v, ORES.length);
    }

    private static Component oreName(int i) { return Component.translatable("item.wmd." + ORES[i] + "_deposit.ore"); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (EmpField.handheldDead(level, player)) return InteractionResult.FAIL;
        BlockState s = level.getBlockState(ctx.getClickedPos());
        if (player.isShiftKeyDown() && s.getBlock() instanceof DepositOreBlock dep) {
            if (!level.isClientSide) {
                ItemStack sample = new ItemStack(ItemProcessBlockEntity.wmd(dep.ore + "_core_sample"));
                if (!player.getInventory().add(sample)) player.drop(sample, false);
                player.displayClientMessage(Component.translatable("item.wmd.ground_scanner.sample", oreName(indexOf(dep.ore))), true);
                level.playSound(null, ctx.getClickedPos(), SoundEvents.DEEPSLATE_HIT, SoundSource.PLAYERS, 0.8F, 0.7F);
                player.getCooldowns().addCooldown(this, COOLDOWN_SAMPLE);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (EmpField.handheldDead(level, player)) return InteractionResultHolder.fail(stack);
        int ore = tuned(stack);
        if (player.isShiftKeyDown()) {
            ore = (ore + 1) % ORES.length;
            stack.set(WmdComponents.SCANNER_ORE.get(), ore);
            player.displayClientMessage(Component.translatable("item.wmd.ground_scanner.tuned", oreName(ore)), true);
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        Block target = ItemProcessBlockEntity.wmdBlock(ORES[ore] + "_deposit");
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE, count = 0;
        int bottom = level.getMinBuildHeight(), top = Math.min(BAND_TOP, origin.getY());
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist > RADIUS * RADIUS) continue;
                for (int y = top; y >= bottom; y--) {
                    c.set(origin.getX() + dx, y, origin.getZ() + dz);
                    if (!level.getBlockState(c).is(target)) continue;
                    count++;
                    if (dist < bestDist || (dist == bestDist && best != null && y > best.getY())) { best = c.immutable(); bestDist = dist; }
                }
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("item.wmd.ground_scanner.none", oreName(ore), RADIUS), true);
            level.playSound(null, origin, WmdSounds.SCANNER_PING.get(), SoundSource.PLAYERS, 0.7F, 0.75F);
        } else {
            int dx = best.getX() - origin.getX(), dz = best.getZ() - origin.getZ(), depth = origin.getY() - best.getY();
            Component size = Component.translatable("item.wmd.ground_scanner.size." + (count < 100 ? 0 : count < 300 ? 1 : count < 600 ? 2 : 3));
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) player.displayClientMessage(Component.translatable("item.wmd.ground_scanner.below", oreName(ore), depth, size, count), true);
            else player.displayClientMessage(Component.translatable("item.wmd.ground_scanner.found", oreName(ore), (int) Math.round(Math.sqrt(dx * dx + dz * dz)), compass(dx, dz), depth, size, count), true);
            level.playSound(null, origin, WmdSounds.TOOL_SCANNER.get(), SoundSource.PLAYERS, 0.75F, Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? 1.25F : 1.0F);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_SCAN);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.wmd.ground_scanner.tuned", oreName(tuned(stack))));
        tip.add(Component.translatable("item.wmd.ground_scanner.tip"));
    }

    private static int indexOf(String ore) {
        for (int i = 0; i < ORES.length; i++) if (ORES[i].equals(ore)) return i;
        return 0;
    }

    private static String compass(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        return DIRECTIONS[(int) Math.round(angle / 45.0) % 8];
    }
}
