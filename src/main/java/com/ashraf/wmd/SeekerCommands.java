package com.ashraf.wmd;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SeekerCommands {

    private SeekerCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev")
                        .then(Commands.literal("laser")
                                .then(Commands.literal("set").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> light(c.getSource(), Vec3.atBottomCenterOf(BlockPosArgument.getLoadedBlockPos(c, "pos"))))))
                                .then(Commands.literal("here").executes(c -> light(c.getSource(), c.getSource().getPosition())))
                                .then(Commands.literal("enemy")
                                        .then(Commands.literal("here").executes(c -> enemy(c.getSource(), c.getSource().getPosition())))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(c -> enemy(c.getSource(), Vec3.atBottomCenterOf(BlockPosArgument.getLoadedBlockPos(c, "pos"))))))
                                .then(Commands.literal("clear").executes(c -> clear(c.getSource())))
                                .then(Commands.literal("list").executes(c -> list(c.getSource()))))
                        .then(Commands.literal("heat").executes(c -> heat(c.getSource())))
                        .then(Commands.literal("radars").executes(c -> radars(c.getSource())))));
    }

    @SubscribeEvent
    static void levelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel sl) LaserSpots.draw(sl);
    }

    private static int light(CommandSourceStack src, Vec3 pos) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int id = LaserSpots.light(src.getLevel(), p.getUUID(), pos, LaserSpots.SRC_DEV);
        src.sendSuccess(() -> Component.literal("Laser spot " + id + " lit at " + BlockPos.containing(pos).toShortString() + " (dev: stays until /wmd dev laser clear)"), false);
        return id;
    }

    static final java.util.UUID DEV_ENEMY = java.util.UUID.nameUUIDFromBytes("wmd:dev_enemy".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static int enemy(CommandSourceStack src, Vec3 pos) {
        int id = LaserSpots.light(src.getLevel(), DEV_ENEMY, pos, LaserSpots.SRC_DEV, pos.add(-40.0, 10.0, 0.0));
        src.sendSuccess(() -> Component.literal("Enemy laser spot " + id + " lit at " + BlockPos.containing(pos).toShortString() + " (beam from the W; /wmd dev laser clear)"), false);
        return id;
    }

    private static int clear(CommandSourceStack src) throws CommandSyntaxException {
        LaserSpots.clear(src.getPlayerOrException().getUUID());
        LaserSpots.clear(DEV_ENEMY);
        src.sendSuccess(() -> Component.literal("Your laser spots and the test enemy's are cleared"), false);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        var all = LaserSpots.all();
        src.sendSuccess(() -> Component.literal(all.size() + " laser spot(s)"), false);
        for (LaserSpots.Spot s : all)
            src.sendSuccess(() -> Component.literal(" #" + s.id() + " " + BlockPos.containing(s.pos()).toShortString() + " " + s.dim().location() + " source " + s.source()), false);
        return all.size();
    }

    private static int heat(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Vec3 at = src.getPosition();
        var hot = HeatLog.near(level, at.x, at.z, 300);
        long now = level.getGameTime();
        src.sendSuccess(() -> Component.literal(hot.size() + " hot war machine(s) within 300 blocks"), false);
        for (HeatLog.Hot h : hot) src.sendSuccess(() -> Component.literal(" " + h.pos().toShortString() + " fired " + (now - h.firedAt()) / 20 + " s ago"), false);
        return hot.size();
    }

    private static int radars(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Vec3 at = src.getPosition();
        var list = RadarEmit.near(level, at.x, at.z, 300);
        src.sendSuccess(() -> Component.literal(list.size() + " radar(s) within 300 blocks"), false);
        for (BlockEntity be : list)
            src.sendSuccess(() -> Component.literal(" " + be.getBlockPos().toShortString() + " " + be.getClass().getSimpleName().replace("BlockEntity", "") + (RadarEmit.emitting(be) ? ": EMITTING" : ": quiet")), false);
        return list.size();
    }
}
