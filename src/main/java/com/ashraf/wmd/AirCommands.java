package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AirCommands {

    private AirCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        if (!AirGate.AVAILABLE) return;
        LiteralArgumentBuilder<CommandSourceStack> aam = Commands.literal("aam");
        for (int k = 0; k < AirMissiles.NAMES.length; k++) {
            final int kind = k;
            aam.then(Commands.literal(AirMissiles.NAMES[k]).executes(c -> aam(c.getSource(), kind, null))
                    .then(Commands.argument("target", EntityArgument.entity()).executes(c -> aam(c.getSource(), kind, EntityArgument.getEntity(c, "target")))));
        }
        aam.then(Commands.literal("flare").executes(c -> deploy(c.getSource(), AirMissiles.FLARE)))
           .then(Commands.literal("chaff").executes(c -> deploy(c.getSource(), AirMissiles.CHAFF)))
           .then(Commands.literal("refill").executes(c -> refill(c.getSource())))
           .then(Commands.literal("list").executes(c -> list(c.getSource())));
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").requires(TestCmds::test)
                        .then(Commands.literal("airbattles").executes(c -> gate(c.getSource(), null))
                                .then(Commands.literal("on").executes(c -> gate(c.getSource(), true)))
                                .then(Commands.literal("off").executes(c -> gate(c.getSource(), false))))
                        .then(Commands.literal("aircraft").executes(c -> aircraft(c.getSource(), (int) Balance.AIR_DETECT_BASE))
                                .then(Commands.argument("base", IntegerArgumentType.integer(1, 30_000)).executes(c -> aircraft(c.getSource(), IntegerArgumentType.getInteger(c, "base")))))
                        .then(aam)
                        .then(Commands.literal("thrust").executes(c -> thrust(c.getSource(), -1.0))
                                .then(Commands.argument("scale", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.05, 50.0))
                                        .executes(c -> thrust(c.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "scale")))))
                        .then(Commands.literal("rwr").executes(c -> rwr(c.getSource())))
                        .then(Commands.literal("airjam").executes(c -> airjam(c.getSource(), 60))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3_600)).executes(c -> airjam(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))));
    }

    private static int thrust(CommandSourceStack src, double scale) {
        if (scale > 0.0) AirEngineBlockEntity.devThrustScale = scale;
        double s = AirEngineBlockEntity.devThrustScale;
        say(src, String.format("Air engine thrust x%.2f: ducted fan %.0f, turbofan %.0f, JATO %.0f at full (Sable units; one andesite propeller at 256 RPM = 256). Resets on restart.",
                s, Balance.EDF_THRUST * s, Balance.TF_THRUST * s, Balance.JATO_THRUST * s));
        return 1;
    }

    private static void say(CommandSourceStack src, String s) { src.sendSuccess(() -> Component.literal(s), false); }

    private static boolean ready(CommandSourceStack src) {
        if (!AirGate.on()) { src.sendFailure(Component.literal("1.2 air battles are switched off for this session (/wmd dev airbattles on).")); return false; }
        if (!SableBridge.active()) { src.sendFailure(Component.literal("Sable is not active (not installed / bridge off): there is no air picture.")); return false; }
        return true;
    }

    private static String v(Vec3 p) { return String.format("%.0f %.0f %.0f", p.x, p.y, p.z); }

    private static String shortId(UUID id) { return id == null ? "-" : id.toString().substring(0, 8); }

    private static int gate(CommandSourceStack src, Boolean set) {
        if (set != null) AirGate.setSession(set);
        say(src, "1.2 AIR BATTLES gate: " + (AirGate.on() ? "ON" : "OFF") + " (available in this run: dev game = " + !net.neoforged.fml.loading.FMLEnvironment.production
                + ", -D" + AirGate.FLAG + " = " + Boolean.getBoolean(AirGate.FLAG) + "; the session switch resets when the server stops).");
        return 1;
    }

    private static int aircraft(CommandSourceStack src, int base) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 me = p.getEyePosition();
        UUID own = SableBridge.shipUnder(p);
        List<AirPicture.Seen> all = AirPicture.allFrom(level, me, base);
        long built = AirPicture.builtAt(level);
        int seen = 0;
        for (AirPicture.Seen s : all) if (s.dist() <= s.range() && !s.a().id().equals(own)) seen++;
        final int fSeen = seen;
        say(src, "AIR PICTURE: " + all.size() + " aircraft in this dimension (" + SableBridge.allShips(level).size() + " Sable bodies; rebuilt "
                + (built < 0 ? "never" : (level.getGameTime() - built) + " ticks ago") + "). A sensor here with base range " + base + " (400-block body) sees " + fSeen + ".");
        say(src, "  rule: airborne = " + (int) Balance.AIR_MIN_AGL + "+ blocks over the ground or " + (int) Balance.AIR_MIN_SPEED + "+ b/s; range = base x clamp((blocks/"
                + (int) Balance.AIR_SIZE_REF + ")^0.25, " + Balance.AIR_SIZE_MIN + ", " + Balance.AIR_SIZE_MAX + ")");
        int i = 0;
        for (AirPicture.Seen s : all) {
            if (++i > 12) { say(src, "  ... " + (all.size() - 12) + " more"); break; }
            AirPicture.Aircraft a = s.a();
            boolean mine = a.id().equals(own);
            String rel = ShipFaction.relation(level, a.id(), p.getUUID(), null).name();
            say(src, String.format("  %s %s \"%s\" %d blocks x%.2f: %.0f blocks away, seen to %.0f -> %s | at %s, %s over ground, %.1f b/s | owner %s%s, %s",
                    mine ? "*" : "-", shortId(a.id()), a.name(), a.blocks(), a.sizeFactor(), s.dist(), s.range(),
                    mine ? "YOU ARE ON IT" : s.dist() <= s.range() ? "SEEN" : "not seen", v(a.pos()), Double.isNaN(a.agl()) ? "?" : String.format("%.0f", a.agl()),
                    a.speedBps(), a.ownerName().isEmpty() ? shortId(a.owner()) : a.ownerName(), a.piloted() ? " (pilot)" : "", rel));
        }
        if (all.isEmpty()) say(src, "  (no airborne Sable body: lift one " + (int) Balance.AIR_MIN_AGL + "+ blocks or fly it at " + (int) Balance.AIR_MIN_SPEED + "+ b/s; the picture is rebuilt every second)");
        return 1;
    }

    private static int aam(CommandSourceStack src, int kind, Entity named) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        UUID own = SableBridge.shipUnder(p);
        Vec3 carrier = Vec3.ZERO;
        if (own != null) { SableBridge.ShipInfo si = SableBridge.shipInfo(level, own); if (si != null) carrier = ShipDefence.velocity(level, si); }
        UUID ship = null, entity = null;
        BlockPos emitter = null;
        String name;
        Vec3 t;
        if (kind == AirMissiles.AGM) {
            net.minecraft.world.phys.BlockHitResult bh = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(Balance.AAM_RANGE[kind])),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            if (bh.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) { src.sendFailure(Component.literal("No ground within " + (int) Balance.AAM_RANGE[kind] + " blocks of your look.")); return 0; }
            int spot = LaserSpots.light(level, p.getUUID(), bh.getLocation(), LaserSpots.SRC_DEV);
            String why = AirMissiles.fireAgm(level, p.getUUID(), eye.add(look.scale(3.0)), look, carrier, own, spot, p.getUUID());
            if (why != null) { LaserSpots.remove(spot); src.sendFailure(Component.literal("Not fired: " + why)); return 0; }
            say(src, String.format("AGM away at a dev laser spot %s (%.0f blocks): it stays lit until /wmd dev laser clear.", bh.getBlockPos().toShortString(), eye.distanceTo(bh.getLocation())));
            return 1;
        }
        if (kind == AirMissiles.ARM) {
            emitter = nearestEmitterOnLook(level, eye, look);
            if (emitter == null) { src.sendFailure(Component.literal("No emitting radar within " + (int) Balance.AAM_RANGE[kind] + " blocks and " + (int) Balance.AAM_DEV_PICK_DEG + " deg of your look.")); return 0; }
            t = SableBridge.worldPos(level, emitter);
            name = "radar " + BlockPos.containing(t).toShortString();
        } else if (named == null && aircraftOnLook(level, eye, look, own, Balance.AAM_RANGE[kind]) == null) {
            named = entityOnLook(level, eye, look, Math.min(Balance.AAM_RANGE[kind], 256.0));
            if (named == null) { src.sendFailure(Component.literal("No aircraft within " + (int) Balance.AAM_RANGE[kind] + " blocks and " + (int) Balance.AAM_DEV_PICK_DEG + " deg of your look (see /wmd dev aircraft), and no entity named or in view.")); return 0; }
            entity = named.getUUID();
            t = named.getBoundingBox().getCenter();
            name = named.getName().getString();
        } else if (named != null) {
            UUID on = SableBridge.shipUnder(named);
            entity = named.getUUID();
            t = named.getBoundingBox().getCenter();
            name = named.getName().getString() + (on != null ? " (on a ship)" : "");
        } else {
            AirPicture.Aircraft a = aircraftOnLook(level, eye, look, own, Balance.AAM_RANGE[kind]);
            ship = a.id();
            t = a.pos();
            name = "\"" + a.name() + "\" " + shortId(a.id());
        }
        int blocks = 0;
        if (ship != null) { SableBridge.ShipInfo si = SableBridge.shipInfo(level, ship); blocks = si == null ? 0 : si.blockCount(); }
        if (kind == AirMissiles.HEAT) {
            Vec3 d = t.subtract(eye);
            double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, d.normalize().dot(look)))));
            if (d.length() > Balance.AAM_HEAT_LOCK_RANGE || ang > Balance.AAM_HEAT_CONE) {
                src.sendFailure(Component.literal(String.format("No heat tone: %s is %.0f blocks / %.0f deg off your look (needs <= %.0f blocks, <= %.0f deg).", name, d.length(), ang,
                        Balance.AAM_HEAT_LOCK_RANGE, Balance.AAM_HEAT_CONE)));
                return 0;
            }
        }
        AirMissiles.LockSource lock = null;
        if (kind == AirMissiles.RADAR) {
            lock = new AirMissiles.PlayerLock(p);
            if (!AirMissiles.lockable(level, eye, look, t, blocks)) {
                src.sendFailure(Component.literal(String.format("No radar lock on %s: %.0f blocks (lock range %.0f) / keep it within %.0f deg of your look, no chaff between.", name,
                        eye.distanceTo(t), Balance.AIR_NOSE_LOCK_RANGE * (blocks > 0 ? AirPicture.sizeFactor(blocks) : 1.0), Balance.AIR_NOSE_GIMBAL)));
                return 0;
            }
        }
        String why = AirMissiles.fire(level, kind, p.getUUID(), eye.add(look.scale(3.0)), look, carrier, own, ship, entity, emitter, lock, p.getUUID(), name);
        if (why != null) { src.sendFailure(Component.literal("Not fired: " + why)); return 0; }
        final String fname = name;
        say(src, String.format("%s missile away at %s (%.0f blocks)%s.", AirMissiles.NAMES[kind].toUpperCase(), fname, eye.distanceTo(t),
                kind == AirMissiles.RADAR ? ": HOLD the lock (keep it in your look) until PITBULL at " + (int) Balance.AAM_PITBULL + " blocks" : kind == AirMissiles.HEAT ? ": fire and forget (no RWR warning)" : ""));
        return 1;
    }

    private static AirPicture.Aircraft aircraftOnLook(ServerLevel level, Vec3 eye, Vec3 look, UUID own, double range) {
        AirPicture.Aircraft best = null;
        double ba = Balance.AAM_DEV_PICK_DEG;
        for (AirPicture.Aircraft a : AirPicture.all(level)) {
            if (a.id().equals(own)) continue;
            Vec3 d = a.pos().subtract(eye);
            if (d.length() > range || d.length() < 1.0) continue;
            double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, d.normalize().dot(look)))));
            if (ang <= ba) { ba = ang; best = a; }
        }
        return best;
    }

    private static BlockPos nearestEmitterOnLook(ServerLevel level, Vec3 eye, Vec3 look) {
        BlockPos best = null;
        double ba = Balance.AAM_DEV_PICK_DEG;
        for (BlockEntity be : RadarEmit.near(level, eye.x, eye.z, Balance.AAM_RANGE[AirMissiles.ARM])) {
            if (!RadarEmit.emitting(be)) continue;
            Vec3 d = SableBridge.worldPos(level, be.getBlockPos()).subtract(eye);
            if (d.length() < 2.0) continue;
            double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, d.normalize().dot(look)))));
            if (ang <= ba) { ba = ang; best = be.getBlockPos(); }
        }
        return best;
    }

    private static int deploy(CommandSourceStack src, int type) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        UUID id = SableBridge.shipUnder(p);
        if (id == null) {
            java.util.Map<UUID, Integer> n = new java.util.HashMap<>();
            for (AirMissiles.Shot s : AirMissiles.inFlight(level)) if (s.targetShip() != null) n.merge(s.targetShip(), 1, Integer::sum);
            int best = 0;
            for (var e : n.entrySet()) if (e.getValue() > best) { best = e.getValue(); id = e.getKey(); }
        }
        SableBridge.ShipInfo si = id == null ? null : SableBridge.shipInfo(level, id);
        if (si == null) { src.sendFailure(Component.literal("Stand on an aircraft, or fire a missile at one first.")); return 0; }
        int fooled = AirMissiles.deploy(level, id, si.worldCenter(), type);
        if (fooled == AirMissiles.COOLING) { src.sendFailure(Component.literal("Dispenser cooling down (" + Balance.AAM_DECOY_COOLDOWN + " ticks between salvos).")); return 0; }
        if (fooled == AirMissiles.EMPTY) { src.sendFailure(Component.literal("Dispenser EMPTY: no " + (type == AirMissiles.FLARE ? "flares" : "chaff") + " left (land, then /wmd dev aam refill).")); return 0; }
        say(src, (type == AirMissiles.FLARE ? "FLARES" : "CHAFF") + " from \"" + si.name() + "\": " + fooled + " missile(s) fooled (match " + (int) (Balance.COUNTER_MATCH * 100)
                + " %, wrong counter " + (int) (Balance.COUNTER_MISMATCH * 100) + " %, ARM never; only missiles within " + (int) Balance.AAM_DECOY_REACH + " blocks roll). Left: "
                + AirMissiles.loadLeft(id, AirMissiles.FLARE) + " flares / " + AirMissiles.loadLeft(id, AirMissiles.CHAFF) + " chaff.");
        return 1;
    }

    private static int refill(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        UUID id = SableBridge.shipUnder(p);
        if (id == null) { src.sendFailure(Component.literal("Stand on the aircraft to refill its dispenser.")); return 0; }
        int f = AirMissiles.refill(level, id, AirMissiles.FLARE, Balance.AAM_DISPENSER_FLARES);
        if (f < 0) { src.sendFailure(Component.literal("Airborne: the dispenser is refilled on the ground only.")); return 0; }
        int c = AirMissiles.refill(level, id, AirMissiles.CHAFF, Balance.AAM_DISPENSER_CHAFF);
        say(src, "Dispenser refilled: +" + f + " flares, +" + c + " chaff (now " + AirMissiles.loadLeft(id, AirMissiles.FLARE) + " / "
                + AirMissiles.loadLeft(id, AirMissiles.CHAFF) + ").");
        return 1;
    }

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        List<AirMissiles.Shot> l = AirMissiles.inFlight(p.serverLevel());
        say(src, "Air missiles in flight here: " + l.size());
        for (AirMissiles.Shot s : l)
            say(src, String.format("  %d %s at %s%s", s.id(), AirMissiles.NAMES[s.kind()], v(s.position()), s.kind() == AirMissiles.RADAR ? (s.pitbull() ? " PITBULL" : " (launcher lock)") : ""));
        return 1;
    }

    private static int airjam(CommandSourceStack src, int seconds) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        UUID own = SableBridge.shipUnder(p);
        if (own == null) { say(src, "Stand on a Sable ship (the aircraft that should jam)."); return 0; }
        AirJam.jamFor(level, own, seconds * 20L);
        say(src, seconds <= 0 ? "Jamming stopped on " + shortId(own) + "." : "Ship " + shortId(own) + " JAMS for " + seconds + " s: enemy radars lock it only at x" + Balance.AIRJAM_LOCK_FACTOR + " range.");
        return 1;
    }

    private static int rwr(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        UUID own = SableBridge.shipUnder(p);
        List<UUID> ids = own != null ? List.of(own) : Rwr.aircraftWithEvents(level, now);
        if (ids.isEmpty()) { say(src, "RWR: quiet (no aircraft has events; stand on one to read its receiver)."); return 1; }
        int shown = 0;
        for (UUID id : ids) {
            if (++shown > 6) break;
            Vec3 at = Rwr.position(level, id);
            List<Rwr.Event> ev = Rwr.of(level, id, now);
            AirPicture.Aircraft a = AirPicture.get(level, id);
            say(src, "RWR of " + (a == null ? shortId(id) + " (not airborne)" : "\"" + a.name() + "\" " + shortId(id)) + ": " + (ev.isEmpty() ? "quiet" : ev.size() + " event(s)"));
            for (Rwr.Event e : ev) {
                double b = at == null ? 0.0 : Rwr.bearing(at, e.from()), d = at == null ? 0.0 : at.distanceTo(e.from());
                say(src, String.format("  %s%s from %s: bearing %03.0f, %.0f blocks, %s, for %d ticks (owner %s)", Rwr.KIND_NAMES[e.kind()], e.active() ? " ACTIVE (pitbull)" : "",
                        e.source(), b, d, at == null ? "?" : (e.from().y > at.y ? "above" : "below"), now - e.first(), shortId(e.emitterOwner())));
            }
        }
        say(src, "  (heat missiles never show here: a passive seeker gives no warning)");
        return 1;
    }

    static LivingEntity entityOnLook(ServerLevel level, Vec3 eye, Vec3 look, double range) {
        LivingEntity best = null;
        double ba = Balance.AAM_DEV_PICK_DEG;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(look.scale(range))).inflate(16.0))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            if (d.length() < 2.0) continue;
            double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, d.normalize().dot(look)))));
            if (ang <= ba) { ba = ang; best = e; }
        }
        return best;
    }
}
