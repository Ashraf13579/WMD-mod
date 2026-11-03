package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcManaged {

    private CcManaged() {}

    static CcModuleBlockEntity managedAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachinePartBlockEntity part && part.getController() != null) be = level.getBlockEntity(part.getController());
        return be instanceof CcModuleBlockEntity m && m.isManaged() ? m : null;
    }

    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        Player p = event.getEntity();
        if (event.getPosition().isEmpty()) return;
        if (managedAt(p.level(), event.getPosition().get()) != null) event.setNewSpeed(0.0F);
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (managedAt(level, event.getPos()) == null) return;
        event.setCanceled(true);
        if (event.getPlayer() != null) event.getPlayer().displayClientMessage(Component.translatable("message.wmd.ccb.managed"), true);
    }
}
