package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcCommandsC {

    private CcCommandsC() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("cc")
                        .then(Commands.literal("jam").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> jam(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("alarm").executes(c -> alarm(c.getSource())))
                        .then(Commands.literal("print").executes(c -> print(c.getSource())))
                        .then(Commands.literal("deorbit").then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(c -> deorbit(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
                        .then(Commands.literal("designate")
                                .then(Commands.literal("clear").executes(c -> designate(c.getSource(), false, 0, 0)))
                                .then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> designate(c.getSource(), true, IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))));
    }

    private static int jam(CommandSourceStack src, int x, int z) {

        GroundStationBlockEntity s = GroundStationBlockEntity.jammerNear(src.getLevel(), java.util.UUID.randomUUID(), x + 0.5, z + 0.5, Balance.CC_JAM_RADIUS);
        if (s == null) {
            src.sendSuccess(() -> Component.literal("Not jammed: no jamming ground station within " + Balance.CC_JAM_RADIUS + " blocks of " + x + " " + z
                    + " (a station jams while powered + its owner has an online EW rack switched on)"), false);
            return 0;
        }
        net.minecraft.world.phys.Vec3 sw = SableBridge.worldPos(s.getLevel(), s.getBlockPos());
        int d = (int) Math.round(Math.hypot(sw.x - x, sw.z - z));
        src.sendSuccess(() -> Component.literal("JAMMED: an enemy uplinked missile aimed at " + x + " " + z + " loses the uplink help (lock, homing, retarget, miss radius): "
                + s.getOwnerName() + "'s ground station at " + s.getBlockPos().toShortString() + ", " + d + " blocks away. Its owner's own (and trusted) missiles are not jammed.")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int designate(CommandSourceStack src, boolean set, int x, int z) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        java.util.List<CcBuildingBlockEntity> bs = CommandCenter.activeBuildings(p.getUUID());
        if (bs.isEmpty()) { src.sendFailure(Component.literal("You have no ACTIVE command center (loaded + powered: /wmd cc power)")); return 0; }
        CcTarget t = set ? new CcTarget(x, z, "gui.wmd.cc.des.spot", CcTarget.SRC_COMMAND, p.getGameProfile().getName(), System.currentTimeMillis()) : null;
        int n = CcTarget.designate(p, bs, t);
        src.sendSuccess(() -> Component.literal((set ? "Designated " + x + " " + z : "Designated target cleared") + " on " + n + " building(s)"), false);
        return n;
    }

    private static int alarm(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        CcAlarms.record(src.getServer(), p.getUUID(), new CcAlarms.Event(System.currentTimeMillis(), CcAlarms.TEST, "Test intruder", "block.wmd.cc_alarm_board",
                p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.level().dimension().location().toString()));
        boolean board = CommandCenter.hasModule(p.getUUID(), CcModule.ALARM_BOARD);
        src.sendSuccess(() -> Component.literal("Test alarm recorded" + (board ? " (chat + your boards)" : " (no online alarm board: no chat, the ring keeps it)")), false);
        return 1;
    }

    private static int print(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        ReconData.Snapshot s = CcMapPrint.snapshot(src.getServer(), p.getUUID());
        if (s == null) {
            src.sendFailure(Component.literal("No recon snapshot of yours (set a target and image it: /wmd sat recon target <x> <z>, then /wmd sat recon snap)"));
            return 0;
        }
        ItemStack map = CcMapPrint.print(src.getServer(), s);
        if (map == null) { src.sendFailure(Component.literal("The snapshot does not fit a vanilla map")); return 0; }
        if (!p.getInventory().add(map)) p.drop(map, false);
        int[] c = CcMapPrint.centre(s);
        src.sendSuccess(() -> Component.literal("Recon map of " + c[0] + " " + c[1] + " printed into your inventory (" + s.icons().length / 3 + " icons)"), false);
        return 1;
    }

    private static int deorbit(CommandSourceStack src, int id) {
        OrbitMath.Sat sat = null;
        for (OrbitMath.Sat s : OrbitData.get(src.getServer()).sats()) if (s.id() == id) sat = s;
        if (sat == null || !OrbitData.get(src.getServer()).remove(src.getServer(), id)) { src.sendFailure(Component.literal("No satellite #" + id)); return 0; }
        ServerPlayer owner = src.getServer().getPlayerList().getPlayer(sat.owner());
        if (owner != null) owner.sendSystemMessage(Component.translatable("message.wmd.cc.globe.deorbited", OrbitData.typeName(sat.type()), id).withStyle(ChatFormatting.YELLOW));
        OrbitMath.Sat fs = sat;
        src.sendSuccess(() -> Component.literal("Satellite #" + id + " (" + fs.type() + ", " + fs.ownerName() + ") deorbited"), true);
        return 1;
    }
}
