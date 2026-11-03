package com.ashraf.wmd;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class StationKit {

    private StationKit() {}

    public static final String PREFIX = "stm_";

    public static boolean isKit(MissileBuild b) { return b != null && b.warheadType() != null && b.warheadType().startsWith(PREFIX); }

    static String warhead(ItemStack kit) {
        StationKitItem k = (StationKitItem) kit.getItem();
        int port = StationKitItem.port(kit);
        return PREFIX + k.type() + "_" + (port < 0 ? "a" : Integer.toString(port));
    }

    public static String type(MissileBuild b) {
        String w = b.warheadType().substring(PREFIX.length());
        int u = w.lastIndexOf('_');
        return u < 0 ? w : w.substring(0, u);
    }

    public static int port(MissileBuild b) {
        String w = b.warheadType();
        int u = w.lastIndexOf('_');
        try { return u < 0 ? -1 : Integer.parseInt(w.substring(u + 1)); } catch (NumberFormatException e) { return -1; }
    }

    static ItemStack item(MissileBuild b) {
        ItemStack s = new ItemStack(switch (type(b)) {
            case Station.HABITAT -> Wmd.STATION_KIT_HABITAT.get();
            case Station.WING -> Wmd.STATION_KIT_WING.get();
            case Station.SENSOR -> Wmd.STATION_KIT_SENSOR.get();
            case Station.LASER -> Wmd.STATION_KIT_LASER.get();
            case Station.ROD -> Wmd.STATION_KIT_ROD.get();
            case Station.MIRROR -> Wmd.STATION_KIT_MIRROR.get();
            case Station.BAY -> Wmd.STATION_KIT_BAY.get();
            case Station.TURRET -> Wmd.STATION_KIT_TURRET.get();
            default -> Wmd.STATION_KIT_CORE.get();
        });
        if (port(b) >= 0) StationKitItem.setPort(s, port(b));
        return s;
    }

    static int status(MinecraftServer server, UUID owner, MissileBuild b) {
        if (server == null || owner == null) return 0;
        int slot = Orbit.slotOf(server, owner);
        String type = type(b);
        Station.Module core = Station.core(server, slot);
        if (Station.CORE.equals(type)) return core != null ? LaunchTerminalBlockEntity.STATION_HAS_CORE : 0;
        if (core == null) return LaunchTerminalBlockEntity.STATION_NO_CORE;
        if (Station.LASER.equals(type)) for (Station.Module m : Station.modules(server, slot)) if (Station.LASER.equals(m.type())) return LaunchTerminalBlockEntity.STATION_LASER_TAKEN;
        if (Station.ROD.equals(type)) for (Station.Module m : Station.modules(server, slot)) if (Station.ROD.equals(m.type())) return LaunchTerminalBlockEntity.STATION_ROD_TAKEN;
        if (Station.MIRROR.equals(type)) for (Station.Module m : Station.modules(server, slot)) if (Station.MIRROR.equals(m.type())) return LaunchTerminalBlockEntity.STATION_MIRROR_TAKEN;
        if (Station.BAY.equals(type)) for (Station.Module m : Station.modules(server, slot)) if (Station.BAY.equals(m.type())) return LaunchTerminalBlockEntity.STATION_BAY_TAKEN;
        if (Station.freePort(server, slot, -1, type) >= 0) return 0;
        String why = Station.spotWhy(server, slot, type);
        return why != null && why.startsWith("dock the habitat") ? LaunchTerminalBlockEntity.STATION_NEED_HABITAT : LaunchTerminalBlockEntity.STATION_PORT_TAKEN;
    }

    static void arrive(ServerLevel level, Flight f) {
        MinecraftServer server = level.getServer();
        UUID owner = null;
        try { if (!f.ownerId().isEmpty()) owner = UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) {}
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (orbit == null || owner == null) return;
        int slot = Orbit.slotOf(server, owner);
        String type = type(f.build());
        String why = Station.deliver(orbit, slot, type, port(f.build()), owner);
        if (why == null && Station.CORE.equals(type))
            Orbit.setGround(server, slot, level.dimension(), (int) Math.floor(f.ox()), (int) Math.floor(f.oz()));
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p == null) return;
        if (why == null) p.sendSystemMessage(Component.translatable("message.wmd.station.arriving", Component.translatable("station.wmd.module." + type)).withStyle(ChatFormatting.AQUA));
        else p.sendSystemMessage(Component.translatable("message.wmd.station.lost_kit", Component.translatable("station.wmd.module." + type), why).withStyle(ChatFormatting.RED));
    }
}
