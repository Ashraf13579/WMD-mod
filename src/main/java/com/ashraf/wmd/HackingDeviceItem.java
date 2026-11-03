package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class HackingDeviceItem extends Item {

    public HackingDeviceItem(Properties properties) { super(properties); }

    @net.neoforged.fml.common.EventBusSubscriber(modid = Wmd.MODID)
    public static final class Use {
        private Use() {}

        @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
        static void onRightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            if (event.getItemStack().getItem() instanceof HackingDeviceItem && Access.ownerAt(event.getLevel(), event.getPos()) != null)
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    private static final Map<UUID, BlockPos> PENDING = new HashMap<>();

    public static synchronized void clearStatics() { PENDING.clear(); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (p == null) return InteractionResult.PASS;
        Owned o = Access.ownerAt(level, ctx.getClickedPos());
        if (!(o instanceof BlockEntity be) || o.getOwner() == null) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("message.wmd.hack.nothing"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            if (Access.trusts(o, p.getUUID())) {
                p.displayClientMessage(Component.translatable("message.wmd.hack.yours"), true);
                return InteractionResult.SUCCESS;
            }
            synchronized (HackingDeviceItem.class) { PENDING.put(p.getUUID(), be.getBlockPos().immutable()); }
            CcAlarms.hack(be, o, p);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4F, 2.0F);
        }
        p.startUsingItem(ctx.getHand());
        return InteractionResult.CONSUME;
    }

    private static synchronized BlockPos pending(UUID id) { return PENDING.get(id); }
    private static synchronized void done(UUID id) { PENDING.remove(id); }

    @Override public int getUseDuration(ItemStack s, LivingEntity e) { return Balance.HACK_USE; }
    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.BOW; }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int left) {
        if (level.isClientSide || !(e instanceof Player p)) return;
        BlockPos target = pending(p.getUUID());
        if (target == null || !level.isLoaded(target) || !(level.getBlockEntity(target) instanceof Owned)
                || SableBridge.distSqWithShips(level, p.position(), Vec3.atCenterOf(target)) > (Balance.HACK_REACH + 1.5) * (Balance.HACK_REACH + 1.5)) {
            p.displayClientMessage(Component.translatable("message.wmd.hack.lost"), true);
            done(p.getUUID());
            p.stopUsingItem();
            return;
        }
        int done = Balance.HACK_USE - left;
        if (done % 20 == 0) p.displayClientMessage(Component.translatable("message.wmd.hack.progress", done * 100 / Balance.HACK_USE), true);
        if (done % 80 == 0) level.playSound(null, p.blockPosition(), WmdSounds.HACK_LOOP.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity e) {
        if (level instanceof ServerLevel sl && e instanceof Player p) {
            BlockPos target = pending(p.getUUID());
            done(p.getUUID());
            if (target != null) {
                Hacks.grant(sl, target, p.getUUID(), sl.getGameTime() + Balance.HACK_TICKS);
                boolean weapon = level.getBlockEntity(target) instanceof Owned ow && Hacks.weapon(ow);
                p.displayClientMessage(Component.translatable(weapon ? "message.wmd.hack.done_weapon" : "message.wmd.hack.done", Balance.HACK_TICKS / 20 / 60), true);
                sl.playSound(null, p.blockPosition(), WmdSounds.HACK_DONE.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
                p.getCooldowns().addCooldown(this, Balance.HACK_COOLDOWN);
            }
        }
        return s;
    }

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity e, int left) {
        if (!level.isClientSide && e instanceof Player p && pending(p.getUUID()) != null) {
            done(p.getUUID());
            p.displayClientMessage(Component.translatable("message.wmd.hack.aborted"), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.hacking_device", Balance.HACK_USE / 20, Balance.HACK_TICKS / 20 / 60).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
