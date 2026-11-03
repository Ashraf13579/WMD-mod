package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DeadHand extends SavedData {

    private static final String NAME = "wmd_dead_hand";

    static final class Site {
        UUID owner;
        String ownerName = "";
        ResourceKey<Level> dim;
        BlockPos pos;
        boolean armed;

        boolean spent;
        long alarmAt = -1;
        final List<Long> pads = new ArrayList<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("o", owner);
            t.putString("n", ownerName);
            t.putString("d", dim.location().toString());
            t.putLong("p", pos.asLong());
            t.putBoolean("a", armed);
            t.putBoolean("s", spent);
            t.putLong("t", alarmAt);
            t.putLongArray("pads", pads.stream().mapToLong(Long::longValue).toArray());
            return t;
        }

        static Site load(CompoundTag t) {
            Site s = new Site();
            s.owner = t.getUUID("o");
            s.ownerName = t.getString("n");
            s.dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("d")));
            s.pos = BlockPos.of(t.getLong("p"));
            s.armed = t.getBoolean("a");
            s.spent = t.getBoolean("s");
            s.alarmAt = t.getLong("t");
            for (long p : t.getLongArray("pads")) s.pads.add(p);
            return s;
        }
    }

    private final Map<String, Site> sites = new HashMap<>();

    private static String key(ResourceKey<Level> dim, BlockPos pos) { return dim.location() + "@" + pos.asLong(); }

    static DeadHand get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DeadHand::new, DeadHand::load, null), NAME);
    }

    static long update(ServerLevel sl, BlockPos pos, UUID owner, String ownerName, boolean armed, List<Long> pads) {
        if (owner == null) return -1;
        DeadHand d = get(sl.getServer());
        Site s = d.sites.computeIfAbsent(key(sl.dimension(), pos), k -> { Site n = new Site(); n.dim = sl.dimension(); n.pos = pos.immutable(); return n; });
        s.owner = owner;
        s.ownerName = ownerName == null ? "" : ownerName;
        if (!armed) { s.alarmAt = -1; s.spent = false; }
        s.armed = armed && !s.spent;
        s.pads.clear();
        s.pads.addAll(pads);
        d.setDirty();
        return s.alarmAt;
    }

    static boolean spent(ServerLevel sl, BlockPos pos) {
        Site s = get(sl.getServer()).sites.get(key(sl.dimension(), pos));
        return s != null && s.spent;
    }

    static long alarmAt(ServerLevel sl, BlockPos pos) {
        Site s = get(sl.getServer()).sites.get(key(sl.dimension(), pos));
        return s == null ? -1 : s.alarmAt;
    }

    static boolean cancel(ServerLevel sl, BlockPos pos) {
        DeadHand d = get(sl.getServer());
        Site s = d.sites.get(key(sl.dimension(), pos));
        if (s == null || s.alarmAt < 0) return false;
        s.alarmAt = -1;
        d.setDirty();
        Wmd.LOGGER.info("[deadhand] {} {}: countdown CANCELLED", s.dim.location(), s.pos.toShortString());
        return true;
    }

    static void remove(ServerLevel sl, BlockPos pos) {
        DeadHand d = get(sl.getServer());
        Site s = d.sites.remove(key(sl.dimension(), pos));
        if (s != null) {
            d.setDirty();
            Wmd.LOGGER.info("[deadhand] {} {}: the bunker is gone{}", s.dim.location(), s.pos.toShortString(), s.alarmAt >= 0 ? " (its countdown dies with it)" : "");
        }
    }

    public static void ccLost(ServerLevel level, UUID owner, BlockPos where) {
        if (owner == null) return;
        MinecraftServer server = level.getServer();
        DeadHand d = get(server);
        long now = server.overworld().getGameTime();
        int n = 0;
        for (Site s : d.sites.values()) {
            if (!owner.equals(s.owner) || !s.armed || s.alarmAt >= 0) continue;
            s.alarmAt = now;
            n++;
            Wmd.LOGGER.info("[deadhand] {} {}: the command center at {} is gone: countdown {} s, {} pad(s)", s.dim.location(), s.pos.toShortString(), where.toShortString(),
                    Balance.DH_COUNTDOWN / 20, s.pads.size());
            sound(server, s, WmdSounds.DH_ALARM.get(), 6.0F);
        }
        if (n == 0) return;
        d.setDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) {
            p.sendSystemMessage(Component.translatable("message.wmd.deadhand.alarm", Balance.DH_COUNTDOWN / 20).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            p.playNotifySound(WmdSounds.DH_ALARM.get(), SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    private static void sound(MinecraftServer server, Site s, SoundEvent e, float vol) {
        ServerLevel sl = server.getLevel(s.dim);
        if (sl != null && sl.isLoaded(s.pos)) sl.playSound(null, s.pos.getX() + 0.5, s.pos.getY() + 1.5, s.pos.getZ() + 0.5, e, SoundSource.BLOCKS, vol, 1.0F);
    }

    private record Due(ResourceKey<Level> dim, BlockPos pad, UUID owner, String name, int[] tries) {}

    private static final List<Due> DUE = new ArrayList<>();

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (!DUE.isEmpty()) pads(server);
        if (now % 5 != 0) return;
        DeadHand d = get(server);
        for (Site s : d.sites.values()) {
            if (s.alarmAt < 0) continue;
            long el = now - s.alarmAt;
            if (el % 20 < 5) sound(server, s, WmdSounds.DH_TICK.get(), 2.0F);
            if (el % 100 < 5) sound(server, s, WmdSounds.DH_ALARM.get(), 4.0F);
            if (el < Balance.DH_COUNTDOWN) continue;
            fire(server, s);
            s.alarmAt = -1;
            s.armed = false;
            s.spent = true;
            d.setDirty();
        }
    }

    private static void fire(MinecraftServer server, Site s) {
        Wmd.LOGGER.info("[deadhand] {} {}: the countdown ran out: {} pad(s) launch", s.dim.location(), s.pos.toShortString(), s.pads.size());
        sound(server, s, WmdSounds.DH_LAUNCH.get(), 8.0F);
        ServerLevel sl = server.getLevel(s.dim);
        if (sl == null) return;
        if (sl.isLoaded(s.pos) && sl.getBlockEntity(s.pos) instanceof DeadHandBlockEntity be) be.fired();
        for (long p : s.pads) {
            BlockPos pad = BlockPos.of(p);
            ImpactChunks.launchHold(sl, pad, true);
            DUE.add(new Due(s.dim, pad, s.owner, s.ownerName, new int[1]));
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { DUE.clear(); }

    private static void pads(MinecraftServer server) {
        for (Iterator<Due> it = DUE.iterator(); it.hasNext(); ) {
            Due u = it.next();
            ServerLevel sl = server.getLevel(u.dim());
            if (sl == null) { it.remove(); continue; }
            if (!(sl.isLoaded(u.pad()) && sl.getBlockEntity(u.pad()) instanceof LaunchTerminalBlockEntity t)) {
                if (++u.tries()[0] > 100) { ImpactChunks.launchHold(sl, u.pad(), false); Wmd.LOGGER.info("[deadhand] pad {} never loaded", u.pad().toShortString()); it.remove(); }
                continue;
            }
            Component why = !u.owner().equals(t.getOwner()) ? Component.literal("not the bunker owner's pad")
                    : !t.isTargetSet() ? Component.literal("no target set on the pad")
                    : t.remoteLaunch(u.owner(), u.name(), t.getTargetX(), t.getTargetZ());

            if (why != null && u.owner().equals(t.getOwner()) && t.isTargetSet() && t.radarTurnTicks(t.getTargetX(), t.getTargetZ()) > 0 && ++u.tries()[0] < Balance.DH_RADAR_WAIT) continue;
            it.remove();
            if (why == null) Wmd.LOGGER.info("[deadhand] pad {} launches at {} {}", u.pad().toShortString(), t.getTargetX(), t.getTargetZ());
            else {

                if (t.getStage() != LaunchTerminalBlockEntity.Stage.COUNTDOWN && t.getStage() != LaunchTerminalBlockEntity.Stage.LAUNCHING) ImpactChunks.launchHold(sl, u.pad(), false);
                Wmd.LOGGER.info("[deadhand] pad {} could not launch: {}", u.pad().toShortString(), why.getString());
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Site s : sites.values()) if (s.owner != null && s.dim != null) l.add(s.save());
        tag.put("sites", l);
        return tag;
    }

    static DeadHand load(CompoundTag tag, HolderLookup.Provider registries) {
        DeadHand d = new DeadHand();
        for (Tag t : tag.getList("sites", Tag.TAG_COMPOUND)) {
            try { Site s = Site.load((CompoundTag) t); d.sites.put(key(s.dim, s.pos), s); }
            catch (RuntimeException e) { Wmd.LOGGER.warn("[deadhand] skipped a bad site: {}", e.toString()); }
        }
        return d;
    }
}
