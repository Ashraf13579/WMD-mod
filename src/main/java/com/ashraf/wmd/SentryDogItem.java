package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public class SentryDogItem extends Item {

    public SentryDogItem(Properties properties) { super(properties.stacksTo(1)); }

    static int charge(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return Balance.SD_CHARGE_MAX;
        CompoundTag t = d.copyTag();
        return t.contains("charge") ? t.getInt("charge") : Balance.SD_CHARGE_MAX;
    }

    static ItemStack withCharge(int charge) {
        ItemStack s = new ItemStack(Wmd.SENTRY_DOG_ITEM.get());
        CompoundTag t = new CompoundTag();
        t.putInt("charge", charge);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return s;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        SentryDogEntity dog = Wmd.SENTRY_DOG.get().create(sl);
        if (dog == null) return InteractionResult.FAIL;
        dog.setup(player, charge(ctx.getItemInHand()));
        dog.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0.0F);
        dog.setYHeadRot(player.getYRot());
        dog.yBodyRot = player.getYRot();
        if (!sl.noCollision(dog)) { player.displayClientMessage(Component.translatable("message.wmd.sentry_dog.no_room"), true); return InteractionResult.FAIL; }
        sl.addFreshEntity(dog);
        if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        sl.playSound(null, at, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.8F, 1.3F);
        player.displayClientMessage(Component.translatable("message.wmd.sentry_dog.follow", dog.charge() * 100 / Balance.SD_CHARGE_MAX), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.wmd.sentry_dog.charge", charge(stack) * 100 / Balance.SD_CHARGE_MAX));
        lines.add(Component.translatable("tooltip.wmd.sentry_dog.use"));
    }
}
