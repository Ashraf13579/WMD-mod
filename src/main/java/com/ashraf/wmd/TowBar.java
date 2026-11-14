package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class TowBar {

    private TowBar() {}

    static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tow_bar");

    static final class Hook {
        final ResourceKey<Level> dim;
        final BlockPos gear;
        final UUID ship;
        final double len;
        int air;
        Hook(ResourceKey<Level> dim, BlockPos gear, UUID ship, double len) { this.dim = dim; this.gear = gear; this.ship = ship; this.len = len; }
    }

    private static final Map<UUID, Hook> HOOKS = Statics.server(new HashMap<>());

    static final Set<Long> CLIENT_TOWED = Statics.client(new HashSet<>());

    public static boolean hooked(Player p) { return p != null && HOOKS.containsKey(p.getUUID()); }

    static void use(ServerLevel sl, Player player, LandingGearBlockEntity g) {
        if (!AirGate.on() || !(player instanceof ServerPlayer sp)) return;
        if (HOOKS.containsKey(sp.getUUID())) { unhook(sp, "message.wmd.tow.off"); return; }
        if (sp.isPassenger()) { say(sp, "message.wmd.tow.seated"); return; }
        if (g.role() != LandingGearBlockEntity.NOSE) { say(sp, "message.wmd.tow.not_nose"); return; }
        UUID ship = SableBridge.shipIdAt(sl, g.getBlockPos());
        if (ship == null) { say(sp, "message.wmd.tow.not_craft"); return; }
        if (!g.locked() || !g.wow) { say(sp, "message.wmd.tow.not_ground"); return; }
        if (!Reach.within(sp, g.getBlockPos(), Balance.TOW_LEN_MAX + 3.0)) { say(sp, "message.wmd.tow.too_far"); return; }
        ShipControl.Order o = ShipControl.newest(sl, ship);
        if (o != null && o.throttle() >= 0.05) { say(sp, "message.wmd.tow.throttle"); return; }
        for (Map.Entry<UUID, Hook> e : HOOKS.entrySet())
            if (e.getValue().ship.equals(ship)) {
                ServerPlayer other = sl.getServer().getPlayerList().getPlayer(e.getKey());
                sp.displayClientMessage(Component.translatable("message.wmd.tow.busy", other == null ? "?" : other.getName().getString()), true);
                return;
            }
        Vec3 w = g.wheelWorld(sl);
        if (w == null) { say(sp, "message.wmd.tow.not_ground"); return; }
        double len = Math.max(Balance.TOW_LEN_MIN, Math.min(Balance.TOW_LEN_MAX, Math.hypot(sp.getX() - w.x, sp.getZ() - w.z)));
        HOOKS.put(sp.getUUID(), new Hook(sl.dimension(), g.getBlockPos().immutable(), ship, len));
        g.towLen = len;
        g.setTowBy(sp.getUUID());
        place(sl, sp, g, ship);
        LandingGearBlockEntity.drive(sl, ship, 4);
        SableBridge.wake(sl, ship);
        slow(sp, true);
        sl.playSound(null, w.x, w.y, w.z, WmdSounds.TOW_COUPLE.get(), SoundSource.PLAYERS, Balance.SND_CLICK, 1.0F);
        sp.displayClientMessage(Component.translatable("message.wmd.tow.on", String.format("%.1f", len)), true);
        Wmd.LOGGER.info("[tow] {} hooked the nose gear at {} of craft {} (bar {} blocks)", sp.getName().getString(), g.getBlockPos().toShortString(), ship, String.format("%.1f", len));
    }

    static boolean letGo(Player player) {
        if (!(player instanceof ServerPlayer sp) || !HOOKS.containsKey(sp.getUUID())) return false;
        unhook(sp, "message.wmd.tow.off");
        return true;
    }

    static void pilotTook(ServerLevel sl, UUID ship, String why) {
        if (HOOKS.isEmpty() || ship == null) return;
        for (Map.Entry<UUID, Hook> e : new ArrayList<>(HOOKS.entrySet()))
            if (e.getValue().ship.equals(ship)) {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(e.getKey());
                if (p != null) unhook(p, why);
                else drop(sl.getServer(), e.getKey());
            }
    }

    private static void unhook(ServerPlayer sp, String why) {
        Hook h = HOOKS.remove(sp.getUUID());
        slow(sp, false);
        if (h == null) return;
        ServerLevel sl = sp.getServer().getLevel(h.dim);
        LandingGearBlockEntity g = sl == null ? null : gear(sl, h);
        if (g != null) {
            g.towHand = null;
            g.setTowBy(null);
            Vec3 w = g.wheelWorld(sl);
            if (w == null) w = SableBridge.worldPos(sl, h.gear);
            sl.playSound(null, w.x, w.y, w.z, WmdSounds.TOW_COUPLE.get(), SoundSource.PLAYERS, Balance.SND_CLICK, 0.75F);
        }
        if (why != null) say(sp, why);
        Wmd.LOGGER.info("[tow] {} let go of craft {} ({})", sp.getName().getString(), h.ship, why);
    }

    private static void drop(net.minecraft.server.MinecraftServer server, UUID tower) {
        Hook h = HOOKS.remove(tower);
        if (h == null || server == null) return;
        ServerLevel sl = server.getLevel(h.dim);
        LandingGearBlockEntity g = sl == null ? null : gear(sl, h);
        if (g != null) { g.towHand = null; g.setTowBy(null); }
    }

    private static LandingGearBlockEntity gear(ServerLevel sl, Hook h) {
        return sl.isLoaded(h.gear) && sl.getBlockEntity(h.gear) instanceof LandingGearBlockEntity g && !g.isRemoved() ? g : null;
    }

    private static void say(ServerPlayer sp, String key) { sp.displayClientMessage(Component.translatable(key), true); }

    private static void slow(ServerPlayer sp, boolean on) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        boolean has = a.hasModifier(SLOW_ID);
        if (on && !has) a.addTransientModifier(new AttributeModifier(SLOW_ID, Balance.TOW_WALK - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!on && has) a.removeModifier(SLOW_ID);
    }

    private static void place(ServerLevel sl, ServerPlayer sp, LandingGearBlockEntity g, UUID ship) {
        Vec3 hand = sp.position().add(0.0, Balance.TOW_HAND_Y, 0.0);
        UUID under = SableBridge.shipUnder(sp);
        Vec3 plot = under != null && !under.equals(ship) ? SableBridge.toShip(sl, under, hand) : null;
        g.towOnShip = plot != null;
        g.towHand = plot != null ? plot : hand;
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (HOOKS.isEmpty()) return;
        net.minecraft.server.MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, Hook> e : new ArrayList<>(HOOKS.entrySet())) {
            Hook h = e.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null) { drop(server, e.getKey()); continue; }
            String why = check(server, sp, h);
            if (why != null) { unhook(sp, why); continue; }
            ServerLevel sl = server.getLevel(h.dim);
            LandingGearBlockEntity g = gear(sl, h);
            place(sl, sp, g, h.ship);
            g.towLen = h.len;
            if (!sp.getUUID().equals(g.towBy())) g.setTowBy(sp.getUUID());
            LandingGearBlockEntity.drive(sl, h.ship, 3);
            if (sp.isSprinting()) sp.setSprinting(false);
        }
    }

    private static String check(net.minecraft.server.MinecraftServer server, ServerPlayer sp, Hook h) {
        if (!AirGate.on()) return "message.wmd.tow.off";
        if (sp.isDeadOrDying() || sp.isRemoved()) return "message.wmd.tow.off";
        if (!sp.level().dimension().equals(h.dim)) return "message.wmd.tow.off";
        if (sp.isPassenger()) return "message.wmd.tow.off";
        if (!sp.getMainHandItem().is(AirBlocks.TOW_BAR.get()) && !sp.getOffhandItem().is(AirBlocks.TOW_BAR.get())) return "message.wmd.tow.stowed";
        ServerLevel sl = server.getLevel(h.dim);
        LandingGearBlockEntity g = sl == null ? null : gear(sl, h);
        if (g == null || !g.locked() || g.role() != LandingGearBlockEntity.NOSE) return "message.wmd.tow.gear_gone";
        if (!h.ship.equals(SableBridge.shipIdAt(sl, h.gear))) return "message.wmd.tow.gear_gone";
        ShipControl.Order o = ShipControl.newest(sl, h.ship);
        if (o != null && o.throttle() >= 0.05) return "message.wmd.tow.pilot";
        h.air = LandingGearBlockEntity.onWheels(sl, h.ship) ? 0 : h.air + 1;
        if (h.air >= Balance.TOW_AIR_TICKS) return "message.wmd.tow.airborne";
        Vec3 w = g.wheelWorld(sl);
        if (w != null) {
            double d = Math.hypot(sp.getX() - w.x, sp.getZ() - w.z);
            if (d > h.len + Balance.TOW_SNAP || Math.abs(sp.getY() - w.y) > Balance.TOW_SNAP + 1.0) return "message.wmd.tow.slipped";
        }
        return null;
    }

    @SubscribeEvent
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && HOOKS.containsKey(sp.getUUID())) { drop(sp.getServer(), sp.getUUID()); slow(sp, false); }
    }

    @SubscribeEvent
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) slow(sp, false);
    }
}
