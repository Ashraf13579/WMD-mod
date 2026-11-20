package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WarPartyLock {
    private WarPartyLock() {}

    static final String ROOT = "oparties";
    static final Set<String> LOCKED = Set.of("leave", "destroy", "kick");

    @SubscribeEvent
    static void onCommand(CommandEvent event) {
        if (!OpacBridge.active() || !Config.Server.warEnabled()) return;
        List<String> names = new ArrayList<>();
        for (CommandContextBuilder<CommandSourceStack> c = event.getParseResults().getContext(); c != null; c = c.getChild())
            for (ParsedCommandNode<CommandSourceStack> n : c.getNodes()) names.add(n.getNode().getName());
        if (names.size() < 2 || !names.get(0).equals(ROOT)) return;
        String sub = names.get(1).equals("member") && names.size() > 2 ? names.get(2) : names.get(1);
        if (!LOCKED.contains(sub)) return;
        ServerPlayer p = event.getParseResults().getContext().getSource().getPlayer();
        if (p == null || !WarGate.frozen(p.server, p.getUUID())) return;
        event.setCanceled(true);
        p.sendSystemMessage(Component.translatable("message.wmd.war.party_locked").withStyle(ChatFormatting.RED));
    }
}
