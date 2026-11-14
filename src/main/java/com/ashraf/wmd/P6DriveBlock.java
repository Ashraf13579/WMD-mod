package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public abstract class P6DriveBlock extends HeliBlock implements WrenchUse {

    private final boolean styled;

    protected P6DriveBlock(Properties properties, String mesh, int w, int h, int d, boolean styled) {
        super(properties, mesh, w, h, d);
        this.styled = styled;
    }

    protected boolean wrench(Level level, AirEngineBlockEntity be, Player player) { return false; }

    @Override
    protected boolean interactOwn(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof AirEngineBlockEntity be)) return false;
        ItemStack held = player.getMainHandItem();
        boolean wrench = held.is(Wmd.WRENCH.get()) && !player.isShiftKeyDown();
        if (wrench && wrench(level, be, player)) return true;
        if (styled && ((held.isEmpty() && player.isShiftKeyDown()) || wrench)) {
            be.toggleVariant();
            player.displayClientMessage(Component.translatable(be.nacelle() ? "message.wmd.p6.bare" : "message.wmd.p6.boxed"), true);
            level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.2F);
            return true;
        }
        return false;
    }
}
