package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class VnLaunch {

    private VnLaunch() {}

    private static final Map<UUID, Long> TOLD = Statics.server(new HashMap<>());

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() || !VnBridge.present() || !(event.getEntity() instanceof ServerPlayer sp)) return;
        try {
            BlockEntity be = sp.level().getBlockEntity(event.getPos());
            if (be instanceof MachinePartBlockEntity part && part.getController() != null) be = sp.level().getBlockEntity(part.getController());
            if (!(be instanceof LaunchTerminalBlockEntity || be instanceof CruiseLauncherBlockEntity || be instanceof CcLaunchConsoleBlockEntity)) return;
            if (be instanceof Owned o && o.getOwner() != null && !Access.mayUse(o, sp)) return;
            long t = System.currentTimeMillis();
            Long last = TOLD.get(sp.getUUID());
            if (last != null && t - last < 2_000L) return;
            TOLD.put(sp.getUUID(), t);
            String why = VnBridge.launchBanReason(VnBridge.countryOf(sp.getUUID()));
            sp.displayClientMessage(Component.translatable("message.wmd.vn.ban_" + (why == null ? "district" : why)).withStyle(ChatFormatting.RED), true);
        } catch (RuntimeException e) { Wmd.LOGGER.warn("[vn] launch reason failed: {}", e.toString()); }
    }

    static void warnTarget(ServerLevel level, UUID launcher, int x, int z) {
        if (level == null || launcher == null || !VnBridge.active()) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(launcher);
        if (p == null) return;
        String mine = VnBridge.countryOf(launcher), there = VnBridge.countryAt(level, new BlockPos(x, 64, z));
        if (there == null || there.equals(mine) || VnBridge.atWar(mine, there)) return;
        p.sendSystemMessage(Component.translatable("message.wmd.vn.not_at_war", VnBridge.name(there)).withStyle(ChatFormatting.GOLD));
    }
}
