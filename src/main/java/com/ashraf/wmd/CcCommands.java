package com.ashraf.wmd;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcCommands {

    private CcCommands() {}

    private static final SuggestionProvider<CommandSourceStack> MODULES = (c, b) -> {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (CcModule k : CcModule.values()) if (k.built) ids.add(k.id);
        return SharedSuggestionProvider.suggest(ids, b);
    };

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("cc").requires(TestCmds::test)
                        .then(Commands.literal("build").executes(c -> build(c.getSource())))
                        .then(Commands.literal("install").then(Commands.argument("module", StringArgumentType.word()).suggests(MODULES)
                                .executes(c -> install(c.getSource(), StringArgumentType.getString(c, "module"), true))))
                        .then(Commands.literal("uninstall").then(Commands.argument("module", StringArgumentType.word()).suggests(MODULES)
                                .executes(c -> install(c.getSource(), StringArgumentType.getString(c, "module"), false))))
                        .then(Commands.literal("power").executes(c -> power(c.getSource(), 0))
                                .then(Commands.literal("creative").executes(c -> power(c.getSource(), 1)))
                                .then(Commands.literal("off").executes(c -> power(c.getSource(), 2))))
                        .then(Commands.literal("status").executes(c -> status(c.getSource())))
                        .then(Commands.literal("warn").executes(c -> warn(c.getSource())))));
    }

    private static int build(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        ServerPlayer player = src.getPlayer();
        Direction look = src.getEntity() != null ? src.getEntity().getDirection() : Direction.NORTH;
        Direction facing = look.getOpposite();
        BlockPos feet = BlockPos.containing(src.getPosition());
        BlockPos o = feet.relative(look, 3).below();

        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int y = CcBuildingSpec.Y0; y <= CcBuildingSpec.Y1 + 4; y++) for (int z = CcBuildingSpec.Z0; z <= CcBuildingSpec.Z1; z++)
            for (int x = CcBuildingSpec.X0; x <= CcBuildingSpec.X1; x++)
                level.setBlock(Machines.toWorld(o, facing, x, y, z), air, 2 | 16);

        BlockState st = Wmd.CC_BUILDING.get().defaultBlockState().setValue(MachineBlock.FACING, facing);
        level.setBlock(o, st, 3);
        Wmd.CC_BUILDING.get().setPlacedBy(level, o, st, player, ItemStack.EMPTY);
        if (!(level.getBlockEntity(o) instanceof CcBuildingBlockEntity b)) { src.sendFailure(Component.literal("Building missing")); return 0; }
        b.testPower(true, true);

        StringBuilder bad = new StringBuilder();
        int n = 0;
        for (CcModule k : CcModule.values()) {
            if (!k.built || CcBuildingSpec.block(k) == null) continue;
            b.testInstall(k, true);
            if (b.placed(k)) n++; else bad.append(k.id).append(' ');
        }
        final int placed = n;
        final String blocked = bad.toString().trim();
        src.sendSuccess(() -> Component.literal("Command center built at " + o.toShortString() + " facing " + facing + ": " + placed + " modules installed"
                + (blocked.isEmpty() ? "" : ", BLOCKED: " + blocked) + ", creative power on (/wmd cc power off to test the socket). Modules come online within 2 s."), true);
        return 1;
    }

    private static CcBuildingBlockEntity nearest(CommandSourceStack src) {
        CcBuildingBlockEntity b = CommandCenter.nearestBuilding(src.getLevel(), BlockPos.containing(src.getPosition()), 32);
        if (b == null) src.sendFailure(Component.literal("No command center building within 32 blocks"));
        return b;
    }

    private static int install(CommandSourceStack src, String id, boolean in) {
        CcModule kind = null;
        for (CcModule k : CcModule.values()) if (k.id.equals(id)) kind = k;
        if (kind == null) { src.sendFailure(Component.literal("Unknown module " + id)); return 0; }
        CcBuildingBlockEntity b = nearest(src);
        if (b == null) return 0;
        Component r = b.testInstall(kind, in);
        src.sendSuccess(() -> r, false);
        return 1;
    }

    private static int power(CommandSourceStack src, int mode) {
        CcBuildingBlockEntity b = nearest(src);
        if (b == null) return 0;
        b.testPower(mode != 2, mode == 1);
        src.sendSuccess(() -> Component.literal(mode == 0 ? "Command center buffer filled" : mode == 1 ? "Command center on creative power"
                : "Command center buffer emptied, creative power off"), true);
        return 1;
    }

    private static int status(CommandSourceStack src) {
        CcBuildingBlockEntity b = nearest(src);
        if (b == null) return 0;
        StringBuilder mods = new StringBuilder();
        for (CcModule k : CcModule.values()) {
            if (!b.installed(k)) continue;
            mods.append(k.id).append(b.online(k) ? " ON" : b.placed(k) ? " off" : " BLOCKED").append(", ");
        }
        java.util.UUID o = b.getOwner();
        Component head = Component.literal("CC " + b.getBlockPos().toShortString() + " owner " + b.getOwnerName() + ": "
                + (b.isPowered() ? "powered" : "UNPOWERED") + (b.isCreative() ? " (creative)" : "") + ", door " + (b.isDoorOpen() ? "open" : "closed")
                + ", draw " + b.draw() + " FE/t, buffer " + b.bufferStored() + " / " + b.bufferCapacity()
                + " | modules: " + mods + "| hub active " + CommandCenter.active(o) + ", ground station " + GroundStationBlockEntity.hasPoweredStation(o)
                + " | warning board " + CommandCenter.hasModule(o, CcModule.WARNING_BOARD) + ", recon wall " + CommandCenter.hasModule(o, CcModule.RECON_WALL)
                + ", air defence " + CommandCenter.hasModule(o, CcModule.AIR_DEFENCE) + ", comms array " + CommandCenter.hasModule(o, CcModule.COMMS_ARRAY));
        src.sendSuccess(() -> head, false);
        return 1;
    }

    private static int warn(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        long now = src.getServer().overworld().getGameTime();
        int x = (int) Math.floor(p.getX()), z = (int) Math.floor(p.getZ());
        CommandCenter.addWarning(src.getServer(), p.getUUID(), new CommandCenter.Warning(-now, x + 3000, z, x + 40, z - 30, now, now + 1200, false));
        Recon.warnAt(p, x + 3000, z, x + 40, z - 30, 60, false);
        src.sendSuccess(() -> Component.literal("Fake launch warning sent to your early warning boards (impact in 60 s)"), false);
        return 1;
    }
}
