package com.ashraf.wmd;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SalvageCommands {

    private SalvageCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("salvage").requires(TestCmds::test)
                        .then(Commands.literal("locate").executes(c -> locate(c.getSource())))
                        .then(Commands.literal("turret").executes(c -> turretDebug(c.getSource())))
                        .then(Commands.literal("build")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(SalvageSites.Kind.values()).map(k -> k.id), b))
                                        .executes(c -> build(c.getSource(), StringArgumentType.getString(c, "kind")))))));
    }

    private static int locate(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos from = BlockPos.containing(src.getPosition());
        SalvageSites.Site s = SalvageSites.nearest(level, from, 3);
        if (s == null) { src.sendFailure(Component.literal("No salvage site within 3 regions (" + 3 * Balance.SALVAGE_REGION_BLOCKS + " blocks).")); return 0; }
        int dist = (int) Math.hypot(s.centreX() - from.getX(), s.centreZ() - from.getZ());
        String tp = "/tp @s " + s.centreX() + " 200 " + s.centreZ();
        Component msg = Component.literal("Salvage site: " + s.kind().id + " at " + s.centreX() + ", " + s.centreZ() + " (" + dist + " blocks)"
                + (Config.salvageSites() ? "" : " [salvage sites are OFF in the config]"))
                .withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp))));
        src.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int turretDebug(CommandSourceStack src) {
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) { src.sendFailure(Component.literal("Run it as a player")); return 0; }
        ServerLevel level = src.getLevel();
        BlockPos at = p.blockPosition();
        int n = 0;
        for (int cx = (at.getX() - 40) >> 4; cx <= (at.getX() + 40) >> 4; cx++)
            for (int cz = (at.getZ() - 40) >> 4; cz <= (at.getZ() + 40) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (var be : level.getChunk(cx, cz).getBlockEntities().values())
                    if (be instanceof SalvageTurretBlockEntity t && t.getBlockPos().distSqr(at) < 40 * 40) {
                        String line = t.debugLine(level, p);
                        src.sendSuccess(() -> Component.literal(line), false);
                        n++;
                    }
            }
        if (n == 0) src.sendFailure(Component.literal("No salvage turret within 40 blocks"));
        return n;
    }

    private static int build(CommandSourceStack src, String id) {
        SalvageSites.Kind kind = SalvageSites.Kind.byId(id);
        if (kind == null) { src.sendFailure(Component.literal("Unknown kind: " + id)); return 0; }
        ServerLevel level = src.getLevel();
        BlockPos p = BlockPos.containing(src.getPosition());

        int x = p.getX(), z = p.getZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(level.getGameTime() ^ p.asLong());
        boolean ok = SalvageBuilder.buildAt(level, kind, x, y, z, r);
        if (ok) src.sendSuccess(() -> Component.literal("Built a " + kind.id + " salvage site at " + x + ", " + y + ", " + z), true);
        return ok ? 1 : 0;
    }
}
