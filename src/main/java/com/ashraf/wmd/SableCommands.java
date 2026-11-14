package com.ashraf.wmd;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SableCommands {

    private SableCommands() {}

    static final int DEFAULT_RADIUS = 256;

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").requires(TestCmds::test).then(Commands.literal("ships").requires(TestCmds::test)
                        .executes(c -> ships(c.getSource(), DEFAULT_RADIUS))
                        .then(Commands.literal("near").then(Commands.argument("radius", IntegerArgumentType.integer(1, 30_000))
                                .executes(c -> ships(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                        .then(Commands.literal("probe").executes(c -> probe(c.getSource())))
                        .then(Commands.literal("faction").executes(c -> faction(c.getSource(), DEFAULT_RADIUS))))));
    }

    private static String v(Vec3 p) { return String.format("%.1f %.1f %.1f", p.x, p.y, p.z); }

    private static boolean ready(CommandSourceStack src) {
        if (!SableBridge.loaded()) { src.sendFailure(Component.literal("Sable is not installed: the bridge is idle (WMD behaves as before).")); return false; }
        if (!Config.Server.sableBridge()) { src.sendFailure(Component.literal("Sable bridge is off in the server config (compat.enableSableBridge = false).")); return false; }
        if (!SableBridge.active()) { src.sendFailure(Component.literal("Sable bridge switched itself off after an error: see the log (\"Sable bridge switched off\").")); return false; }
        return true;
    }

    private static int ships(CommandSourceStack src, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 me = p.position();
        List<SableBridge.ShipInfo> all = SableBridge.allShips(level);
        List<SableBridge.ShipInfo> near = SableBridge.shipsIntersecting(level, new AABB(me, me).inflate(radius));
        UUID under = SableBridge.shipUnder(p);
        near = near.stream().sorted(Comparator.comparingDouble(s -> s.worldCenter().distanceToSqr(me))).toList();
        final List<SableBridge.ShipInfo> shown = near;
        src.sendSuccess(() -> Component.literal("Sable ships: " + shown.size() + " within " + radius + " blocks (" + all.size() + " loaded in this dimension). You stand on: "
                + (under == null ? "no ship" : name(all, under)) + ". Large = " + Config.Server.largeShipMinBlocks() + "+ blocks (server config largeShipMinBlocks)."), false);
        int i = 0;
        for (SableBridge.ShipInfo s : shown) {
            if (++i > 12) { src.sendSuccess(() -> Component.literal("  ... " + (shown.size() - 12) + " more"), false); break; }
            Vec3 size = s.size();
            boolean mine = s.id().equals(under);
            String line = String.format("%s%s: centre %s, %.0f blocks away, speed %.2f (vel %s), mass %.1f, blocks %d, box %.0f x %.0f x %.0f, %s, %s to you",
                    mine ? "> " : "  ", s.name(), v(s.worldCenter()), Math.sqrt(s.worldCenter().distanceToSqr(me)), s.speed(), v(s.linearVelocity()), s.mass(),
                    s.blockCount(), size.x, size.y, size.z, s.isLarge() ? "LARGE (radar contact)" : "small (invisible to radar)",
                    ShipFaction.relation(level, s.id(), p.getUUID(), null).name());
            src.sendSuccess(() -> Component.literal(line), false);
            int m = 0;
            for (BlockPos bp : SableBridge.blockEntitiesOn(level, s.id())) {
                BlockEntity be = level.getBlockEntity(bp);
                if (be == null) continue;
                var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                if (key == null || !Wmd.MODID.equals(key.getNamespace())) continue;
                if (++m > 8) { src.sendSuccess(() -> Component.literal("      ... more WMD machines aboard"), false); break; }
                Vec3 w = SableBridge.worldPos(level, bp);
                String ml = String.format("      WMD %s at plot %d %d %d -> world %s (%.0f blocks from you)", key.getPath(), bp.getX(), bp.getY(), bp.getZ(), v(w), Math.sqrt(w.distanceToSqr(me)));
                src.sendSuccess(() -> Component.literal(ml), false);
            }
        }
        return shown.size();
    }

    private static int faction(CommandSourceStack src, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 me = p.position();
        List<SableBridge.ShipInfo> near = SableBridge.shipsIntersecting(level, new AABB(me, me).inflate(radius)).stream()
                .sorted(Comparator.comparingDouble(s -> s.worldCenter().distanceToSqr(me))).toList();
        java.util.Map<UUID, String> known = AirshipWatch.known(p.getUUID());
        java.util.Set<UUID> team = CommandCenter.trustedBy(p.getUUID());
        src.sendSuccess(() -> Component.literal("Ship occupants within " + radius + " blocks: " + near.size() + " ship(s). You trust (command center whitelists): "
                + (team.isEmpty() ? "nobody" : team.size() + " player(s)")), false);
        int i = 0;
        for (SableBridge.ShipInfo s : near) {
            if (++i > 8) { final int more = near.size() - 8; src.sendSuccess(() -> Component.literal("  ... " + more + " more"), false); break; }
            ShipFaction.Occupants o = ShipFaction.occupants(level, s.id());
            ShipFaction.Relation rel = ShipFaction.relation(level, s.id(), p.getUUID(), null);
            String head = String.format("  %s (%d blocks, %s): %s to you; %d WMD machine(s) aboard; your warnings know it as: %s", s.name(), s.blockCount(),
                    s.isLarge() ? "LARGE" : "small", rel.name(), o.machines(), known.getOrDefault(s.id(), "not seen"));
            src.sendSuccess(() -> Component.literal(head), false);
            StringBuilder owners = new StringBuilder();
            for (java.util.Map.Entry<UUID, Integer> e : o.machineOwners().entrySet()) {
                String n = o.ownerNames().getOrDefault(e.getKey(), "");
                if (owners.length() > 0) owners.append(", ");
                owners.append(n.isEmpty() ? e.getKey().toString() : n).append(" x").append(e.getValue())
                        .append(ShipFaction.trusted(p.getUUID(), team, null, e.getKey()) ? " (trusted)" : " (FOREIGN)");
            }
            StringBuilder players = new StringBuilder();
            for (java.util.Map.Entry<UUID, String> e : o.players().entrySet()) {
                if (players.length() > 0) players.append(", ");
                players.append(e.getValue()).append(ShipFaction.trusted(p.getUUID(), team, null, e.getKey()) ? " (trusted)" : " (FOREIGN)");
            }
            final String ol = "      machine owners: " + (owners.length() == 0 ? "none" : owners) + " | players aboard: " + (players.length() == 0 ? "none" : players);
            src.sendSuccess(() -> Component.literal(ol), false);
        }
        return near.size();
    }

    private static String name(List<SableBridge.ShipInfo> all, UUID id) {
        for (SableBridge.ShipInfo s : all) if (s.id().equals(id)) return s.name();
        return id.toString();
    }

    private static int probe(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(64.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        List<SableBridge.ShipInfo> boxes = SableBridge.shipsAlongSegment(level, eye, end);
        String boxNames = boxes.isEmpty() ? "none" : String.join(", ", boxes.stream().map(SableBridge.ShipInfo::name).toList());
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            src.sendSuccess(() -> Component.literal("Probe (64 blocks): no block hit. Ship bounding boxes on the line: " + boxNames), false);
            return 0;
        }
        BlockPos bp = hit.getBlockPos();
        boolean onShip = SableBridge.isOnShip(level, bp);
        UUID id = SableBridge.shipIdAt(level, bp);
        SableBridge.ShipInfo ship = id == null ? null : SableBridge.shipInfo(level, id);
        Vec3 w = SableBridge.worldPos(level, bp);
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(bp).getBlock()).toString();
        String line = String.format("Probe: hit %s at %d %d %d (%s), hit point %s, %.1f blocks from your eyes. On a ship: %s. Block centre in the world: %s. Ship boxes on the line: %s",
                block, bp.getX(), bp.getY(), bp.getZ(), onShip ? "PLOT coordinates" : "world coordinates", v(hit.getLocation()), Math.sqrt(hit.getLocation().distanceToSqr(eye)),
                onShip ? "YES (" + (ship != null ? ship.name() : String.valueOf(id)) + ")" : "no", v(w), boxNames);
        src.sendSuccess(() -> Component.literal(line), false);
        return onShip ? 1 : 0;
    }
}
