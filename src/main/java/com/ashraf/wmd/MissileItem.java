package com.ashraf.wmd;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class MissileItem extends Item {

    public MissileItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            MissileEntity missile = new MissileEntity(Wmd.MISSILE_ENTITY.get(), level);
            missile.setOwner(player);

            missile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());

            missile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.0F, 0.0F);
            level.addFreshEntity(missile);

            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.8F);

        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
