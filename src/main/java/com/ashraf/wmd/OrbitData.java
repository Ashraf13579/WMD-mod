package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public class OrbitData extends SavedData {

    private static final String NAME = "wmd_orbit";
    public static final List<String> TYPES = List.of("nav", "recon", "relay", "interceptor", "cubesat", "designator");

    private final List<OrbitMath.Sat> sats = new ArrayList<>();

    private final List<CompoundTag> notes = new ArrayList<>();
    private int nextId = 1;

    public static OrbitData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(OrbitData::new, OrbitData::load, null), NAME);
    }

    public List<OrbitMath.Sat> sats() { return List.copyOf(sats); }

    public int count() {
        int n = 0;
        for (OrbitMath.Sat s : sats) if (!s.isCubesat()) n++;
        return n;
    }

    public OrbitMath.Sat byId(int id) {
        for (OrbitMath.Sat s : sats) if (s.id() == id) return s;
        return null;
    }

    public static int climbing(MinecraftServer server) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) for (Flight f : FlightManager.get(l).flights()) if (Satellites.isSatellite(f.build())) n++;
        return n;
    }

    public static boolean full(MinecraftServer server) { return get(server).count() + climbing(server) >= Balance.SAT_MAX; }

    public OrbitMath.Sat add(MinecraftServer server, UUID owner, String ownerName, String type, RandomSource rnd) {
        if (!OrbitMath.CUBESAT.equals(type) && count() >= Balance.SAT_MAX) return null;
        double heading = rnd.nextDouble() * Math.PI * 2.0;
        double cross = (0.25 + rnd.nextDouble() * 0.45) * (rnd.nextBoolean() ? 1.0 : -1.0);
        OrbitMath.Sat s = new OrbitMath.Sat(nextId++, owner, ownerName == null ? "" : ownerName, TYPES.contains(type) ? type : "nav",
                System.currentTimeMillis(), server.overworld().getGameTime(), rnd.nextDouble(), heading, cross);
        sats.add(s);
        setDirty();
        sync(server);
        return s;
    }

    public boolean remove(MinecraftServer server, int id) {
        boolean r = sats.removeIf(s -> s.id() == id);
        if (r) { setDirty(); sync(server); }
        return r;
    }

    public boolean transfer(MinecraftServer server, int id, UUID owner, String ownerName) {
        OrbitMath.Sat s = byId(id);
        if (s == null || owner == null) return false;
        replace(server, s.withOwner(owner, ownerName));
        return true;
    }

    public void replace(MinecraftServer server, OrbitMath.Sat s) {
        for (int i = 0; i < sats.size(); i++) if (sats.get(i).id() == s.id()) { sats.set(i, s); setDirty(); sync(server); return; }
    }

    public static boolean controlled(OrbitMath.Sat s) {
        if (s.isCubesat()) return CommandCenter.active(s.owner());
        return GroundStationBlockEntity.hasPoweredStation(s.owner()) && CommandCenter.active(s.owner());
    }

    public static Component typeName(String type) { return Component.translatable("sat.wmd.type." + type); }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) return;
        OrbitData d = server.overworld().getDataStorage().get(new SavedData.Factory<>(OrbitData::new, OrbitData::load, null), NAME);
        if (d != null && !d.sats.isEmpty()) d.expire(server);
    }

    private void expire(MinecraftServer server) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Iterator<OrbitMath.Sat> it = sats.iterator(); it.hasNext(); ) {
            OrbitMath.Sat s = it.next();
            if (now < s.endMs()) continue;
            it.remove();
            changed = true;
            Wmd.LOGGER.info("Satellite #{} ({}, owner {}) ran out of RTG power and was removed", s.id(), s.type(), s.ownerName());
            if (s.isCubesat()) Cubesat.burnUp(server, s);
            ServerPlayer p = server.getPlayerList().getPlayer(s.owner());
            if (p != null) p.sendSystemMessage(expiredMessage(s.type(), s.id()));
            else {
                CompoundTag n = new CompoundTag();
                n.putUUID("owner", s.owner()); n.putString("type", s.type()); n.putInt("id", s.id());
                notes.add(n);
            }
        }
        if (changed) { setDirty(); sync(server); }
    }

    private static Component expiredMessage(String type, int id) {
        if (OrbitMath.CUBESAT.equals(type)) return Component.translatable("message.wmd.cubesat.burned", id).withStyle(ChatFormatting.YELLOW);
        return Component.translatable("message.wmd.sat.expired", typeName(type), id).withStyle(ChatFormatting.YELLOW);
    }

    public static void sync(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new OrbitPayload(get(server).sats(), System.currentTimeMillis()));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        OrbitData d = get(p.server);
        PacketDistributor.sendToPlayer(p, new OrbitPayload(d.sats(), System.currentTimeMillis()));
        boolean changed = false;
        for (Iterator<CompoundTag> it = d.notes.iterator(); it.hasNext(); ) {
            CompoundTag n = it.next();
            if (!n.hasUUID("owner") || !n.getUUID("owner").equals(p.getUUID())) continue;
            p.sendSystemMessage(expiredMessage(n.getString("type"), n.getInt("id")));
            it.remove();
            changed = true;
        }
        if (changed) d.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (OrbitMath.Sat s : sats) l.add(s.save());
        tag.put("sats", l);
        ListTag nl = new ListTag();
        nl.addAll(notes);
        tag.put("notes", nl);
        tag.putInt("nextId", nextId);
        return tag;
    }

    public static OrbitData load(CompoundTag tag, HolderLookup.Provider registries) {
        OrbitData d = new OrbitData();
        for (Tag t : tag.getList("sats", Tag.TAG_COMPOUND)) d.sats.add(OrbitMath.Sat.load((CompoundTag) t));
        for (Tag t : tag.getList("notes", Tag.TAG_COMPOUND)) d.notes.add((CompoundTag) t);
        d.nextId = Math.max(1, tag.getInt("nextId"));
        return d;
    }
}
