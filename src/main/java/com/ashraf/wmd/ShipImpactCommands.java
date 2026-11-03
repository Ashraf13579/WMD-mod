package com.ashraf.wmd;

import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipImpactCommands {

    private ShipImpactCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> shot = Commands.literal("shipshot").executes(c -> shot(c.getSource(), Artillery.HOWITZER, Artillery.HE));
        String[] guns = { "mortar", "howitzer", "rocket", "super" };
        int[] gunIds = { Artillery.MORTAR, Artillery.HOWITZER, Artillery.ROCKET, Artillery.SUPER_GUN };
        String[] types = { "he", "cluster", "smoke", "illum" };
        int[] typeIds = { Artillery.HE, Artillery.CLUSTER, Artillery.SMOKE, Artillery.ILLUM };
        for (int g = 0; g < guns.length; g++) {
            final int gun = gunIds[g];
            LiteralArgumentBuilder<CommandSourceStack> gl = Commands.literal(guns[g]).executes(c -> shot(c.getSource(), gun, Artillery.HE));
            if (gun == Artillery.MORTAR || gun == Artillery.HOWITZER) {
                for (int t = 0; t < types.length; t++) {
                    final int type = typeIds[t];
                    gl.then(Commands.literal(types[t]).executes(c -> shot(c.getSource(), gun, type)));
                }
            }
            shot.then(gl);
        }
        LiteralArgumentBuilder<CommandSourceStack> mode = Commands.literal("mode");
        String[] modes = { "auto", "clip", "walk" };
        for (int m = 0; m < modes.length; m++) {
            final int id = m;
            mode.then(Commands.literal(modes[m]).executes(c -> mode(c.getSource(), id)));
        }
        shot.then(mode);
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev")
                        .then(Commands.literal("shipblast").then(Commands.argument("power", FloatArgumentType.floatArg(0.5F, 32.0F))
                                .executes(c -> blast(c.getSource(), FloatArgumentType.getFloat(c, "power")))))
                        .then(shot)));
    }

    private static String v(Vec3 p) { return String.format("%.1f %.1f %.1f", p.x, p.y, p.z); }

    private static boolean ready(CommandSourceStack src) {
        if (!SableBridge.loaded()) { src.sendFailure(Component.literal("Sable is not installed: ships are never hit (WMD behaves as before).")); return false; }
        if (!SableBridge.active()) { src.sendFailure(Component.literal("Sable bridge is off (server config compat.enableSableBridge, or it switched itself off after an error: see the log).")); return false; }
        return true;
    }

    private static int blast(CommandSourceStack src, float power) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(256.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 at = hit == null || hit.getType() == HitResult.Type.MISS ? eye.add(p.getLookAngle().scale(64.0)) : hit.getLocation();
        if (SableBridge.isOnShip(level, BlockPos.containing(at))) at = SableBridge.toWorld(level, at);
        double reach = Balance.SHIP_BLAST_REACH_PER_POWER * power;
        int ships = SableBridge.shipsIntersecting(level, new AABB(at, at).inflate(reach)).size();
        int n = ShipStrike.blast(level, at, 0.0, power, p.getUUID());
        final Vec3 fa = at;
        src.sendSuccess(() -> Component.literal(String.format("Ship blast power %.1f at %s: %d ship box(es) within %.0f blocks, %d explosion(s) set off%s. Friendly fire %s.",
                power, v(fa), ships, reach, n, n == 0 && ships > 0 ? " (spared as friendly, or queued by the per-tick cap)" : "",
                Config.Server.shipFriendlyFire() ? "ON" : "OFF (your own / trusted ships are spared)")), false);
        return n;
    }

    private static int mode(CommandSourceStack src, int m) {
        if (!ready(src)) return 0;
        try { SableImpact.mode = m; } catch (Throwable t) { SableBridge.fail(t); return 0; }
        src.sendSuccess(() -> Component.literal("Ship path test: exact test mode = " + new String[] { "auto (clip, then voxel walk)", "clip only", "voxel walk only" }[m]), false);
        return 1;
    }

    private static int shot(CommandSourceStack src, int gun, int type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ready(src)) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 me = p.position();
        List<SableBridge.ShipInfo> all = SableBridge.allShips(level);
        SableBridge.ShipInfo s = all.stream().min(Comparator.comparingDouble(x -> x.worldCenter().distanceToSqr(me))).orElse(null);
        if (s == null) { src.sendFailure(Component.literal("No Sable ship is loaded in this dimension.")); return 0; }
        Vec3 c = s.worldCenter();
        Vec3 dir = new Vec3(me.x - c.x, 0.0, me.z - c.z);
        if (dir.lengthSqr() < 4.0) { Vec3 l = p.getLookAngle(); dir = new Vec3(-l.x, 0.0, -l.z); }
        if (dir.lengthSqr() < 1.0E-6) dir = new Vec3(1.0, 0.0, 0.0);
        dir = dir.normalize();
        Vec3 size = s.size();
        double d = Math.max(100.0, Math.sqrt(size.x * size.x + size.z * size.z) * 0.5 + 40.0);
        int dur = (int) Math.max(60, Math.round(2.0 * d / 4.0));
        double oy = c.y + 12.0;

        double ty = 2.0 * c.y - oy - Balance.ART_GRAVITY * dur * dur / 4.0;
        Vec3 o = new Vec3(c.x + dir.x * d, oy, c.z + dir.z * d);
        double tx = c.x - dir.x * d, tz = c.z - dir.z * d;
        ArtShell sh = ArtilleryManager.get(level).fire(level, p.getUUID(), p.getGameProfile().getName(), gun, type, o, tx, ty, tz, dur, null, 1, true);
        if (sh == null) { src.sendFailure(Component.literal("Too many shells in the air (artilleryMaxShellsInFlight).")); return 0; }
        src.sendSuccess(() -> Component.literal(String.format("Test shell #%d (%s) fired from %s: passes the centre of '%s' (%s) in %.1f s, lands at %.0f %.0f %.0f if nothing stops it. Exact test: %s%s. Watch the log for 'WMD path hit'.",
                sh.id(), ArtilleryManager.itemKey(sh), v(o), s.name(), v(c), dur / 40.0, tx, ty, tz, modeName(), Config.Server.shipFriendlyFire() ? "" : " (friendly fire OFF)")), false);
        return 1;
    }

    private static String modeName() {
        try { return SableImpact.modeName(); } catch (Throwable t) { return "?"; }
    }
}
