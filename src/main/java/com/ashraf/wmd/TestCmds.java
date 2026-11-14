package com.ashraf.wmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class TestCmds {

    private TestCmds() {}

    public static boolean on() {
        if (!FMLEnvironment.production) return true;
        try { return Config.Server.TEST_COMMANDS.get(); } catch (RuntimeException e) { return false; }
    }

    public static boolean test(CommandSourceStack s) {
        return s.hasPermission(2) && on();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void register(RegisterCommandsEvent event) {
        CommandNode<CommandSourceStack> wmd = event.getDispatcher().getRoot().getChild("wmd");
        if (wmd == null) return;
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin").requires(s -> s.hasPermission(2));
        int n = 0;
        n += adopt(admin, "winter", wmd, "winter");
        n += adopt(admin, "retrogen", wmd, "retrogen");
        n += adopt(admin, "satellites", wmd, "sat");
        n += adopt(admin, "debris", wmd, "dev", "debris");
        n += adopt(admin, "bluemap", wmd, "dev", "bluemap");
        n += adopt(admin, "load", wmd, "dev", "load");
        n += adopt(admin, "collapse", wmd, "collapse");
        if (n > 0) event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2)).then(admin));
    }

    private static int adopt(LiteralArgumentBuilder<CommandSourceStack> into, String name, CommandNode<CommandSourceStack> from, String... path) {
        CommandNode<CommandSourceStack> node = from;
        for (String p : path) {
            node = node.getChild(p);
            if (node == null) return 0;
        }
        LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal(name);
        if (node.getCommand() != null) b.executes(node.getCommand());
        for (CommandNode<CommandSourceStack> child : node.getChildren()) b.then(child);
        into.then(b);
        return 1;
    }
}
