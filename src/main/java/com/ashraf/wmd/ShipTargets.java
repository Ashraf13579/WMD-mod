package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipTargets {

    private ShipTargets() {}

    public record Desig(UUID ship, String name, long until) {}

    private static final Map<UUID, Desig> MAP = new HashMap<>();

    public static void set(UUID owner, UUID ship, String name, long now) {
        if (owner == null || ship == null) return;
        MAP.put(owner, new Desig(ship, name == null ? "" : name, now + Balance.SHIP_DESIGNATION_TICKS));
    }

    public static Desig get(UUID owner, long now) {
        Desig d = owner == null ? null : MAP.get(owner);
        if (d == null) return null;
        if (now >= d.until()) { MAP.remove(owner); return null; }
        return d;
    }

    public static boolean isDesignated(UUID owner, UUID ship, long now) {
        Desig d = get(owner, now);
        return d != null && d.ship().equals(ship);
    }

    public static void clear(UUID owner) { if (owner != null) MAP.remove(owner); }

    public record Result(boolean ok, boolean needConfirm, net.minecraft.network.chat.Component msg) {}

    public static Result designate(net.minecraft.server.level.ServerLevel sl, UUID owner, UUID ship, boolean confirm) {
        if (!SableBridge.active()) return new Result(false, false, net.minecraft.network.chat.Component.translatable("message.wmd.ship.no_sable"));
        SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
        if (info == null) return new Result(false, false, net.minecraft.network.chat.Component.translatable("message.wmd.ship.gone"));
        if (!info.isLarge()) return new Result(false, false, net.minecraft.network.chat.Component.translatable("message.wmd.ship.small"));
        boolean hostile = ShipFaction.isHostileTo(owner, sl, ship);
        if (!hostile && !confirm) return new Result(false, true, net.minecraft.network.chat.Component.translatable("message.wmd.ship.confirm", info.name().isEmpty() ? "airship" : info.name()));
        set(owner, ship, info.name(), sl.getGameTime());
        return new Result(true, false, net.minecraft.network.chat.Component.translatable("message.wmd.ship.designated", info.name().isEmpty() ? "airship" : info.name(), Balance.SHIP_DESIGNATION_TICKS / 1200));
    }

    static void clearStatics() { MAP.clear(); }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { clearStatics(); }
}
