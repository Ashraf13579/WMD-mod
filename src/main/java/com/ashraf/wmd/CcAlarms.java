package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcAlarms {

    private CcAlarms() {}

    public static final int USE = 0, BREAK = 1, DESTROYED = 2, EXPLOSION = 3, TEST = 4, INTRUDER = 5, HACK = 6;
    static final String[] KIND = { "use", "break", "destroyed", "explosion", "test", "intruder", "hack" };
    public static final int RING = 8;
    private static final long RATE_TICKS = 200L;

    public record Event(long timeMs, int kind, String actor, String machine, int x, int y, int z, String dim) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("t", timeMs); t.putInt("k", kind); t.putString("a", actor); t.putString("m", machine);
            t.putInt("x", x); t.putInt("y", y); t.putInt("z", z); t.putString("d", dim);
            return t;
        }
        static Event load(CompoundTag t) {
            return new Event(t.getLong("t"), t.getInt("k"), t.getString("a"), t.getString("m"), t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getString("d"));
        }
        void write(FriendlyByteBuf buf) {
            buf.writeLong(timeMs); buf.writeVarInt(kind); buf.writeUtf(actor, 64); buf.writeUtf(machine, 128);
            buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeUtf(dim, 128);
        }
        static Event read(FriendlyByteBuf buf) {
            return new Event(buf.readLong(), buf.readVarInt(), buf.readUtf(64), buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(128));
        }
        String kindName() { return KIND[Math.max(0, Math.min(KIND.length - 1, kind))]; }
    }

    public static final class Data extends SavedData {
        private static final String NAME = "wmd_cc_alarms";
        private final Map<UUID, List<Event>> rings = new HashMap<>();

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        public List<Event> events(UUID owner) {
            List<Event> l = owner == null ? null : rings.get(owner);
            return l == null ? List.of() : List.copyOf(l);
        }

        void add(UUID owner, Event e) {
            List<Event> l = rings.computeIfAbsent(owner, k -> new ArrayList<>());
            l.add(e);
            while (l.size() > RING) l.remove(0);
            setDirty();
        }

        void clear(UUID owner) { if (rings.remove(owner) != null) setDirty(); }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag all = new ListTag();
            for (Map.Entry<UUID, List<Event>> me : rings.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("owner", me.getKey());
                ListTag l = new ListTag();
                for (Event e : me.getValue()) l.add(e.save());
                t.put("events", l);
                all.add(t);
            }
            tag.put("rings", all);
            return tag;
        }

        public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag raw : tag.getList("rings", Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) raw;
                if (!t.hasUUID("owner")) continue;
                List<Event> l = new ArrayList<>();
                for (Tag e : t.getList("events", Tag.TAG_COMPOUND)) l.add(Event.load((CompoundTag) e));
                while (l.size() > RING) l.remove(0);
                d.rings.put(t.getUUID("owner"), l);
            }
            return d;
        }
    }

    private static final Set<CcAlarmBoardBlockEntity> BOARDS = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<String, Long> LAST = new HashMap<>();

    static void join(CcAlarmBoardBlockEntity b) { BOARDS.add(b); }
    static void leave(CcAlarmBoardBlockEntity b) { BOARDS.remove(b); }

    public static void refused(Owned o, Player p, int kind) {
        if (p == null || p.level().isClientSide || !(o instanceof BlockEntity be)) return;
        raise(be, o, kind, p.getGameProfile().getName(), p.getUUID());
    }

    public static void intruder(BlockEntity be, Owned o, net.minecraft.world.entity.LivingEntity who) {
        if (be.getLevel() == null || be.getLevel().isClientSide) return;
        boolean player = who instanceof Player;
        raise(be, o, INTRUDER, player ? ((Player) who).getGameProfile().getName() : who.getType().getDescription().getString(), player ? who.getUUID() : null);
    }

    public static void hack(BlockEntity be, Owned o, Player p) {
        if (p == null || p.level().isClientSide) return;
        raise(be, o, HACK, p.getGameProfile().getName(), p.getUUID());
    }

    public static void minedByOther(Owned o, Player p) {
        if (p == null || p.level().isClientSide || !(o instanceof BlockEntity be) || o.getOwner() == null || o.getOwner().equals(p.getUUID())) return;
        raise(be, o, DESTROYED, p.getGameProfile().getName(), p.getUUID());
    }

    public static void exploded(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || level.getBlockEntity(pos) == null) return;
        Owned o = Access.ownerAt(level, pos);
        if (o instanceof BlockEntity be && o.getOwner() != null) raise(be, o, EXPLOSION, "", null);
    }

    @SubscribeEvent
    static void onDetonate(ExplosionEvent.Detonate event) {
        if (ClaimGuard.probing()) return;
        Level level = event.getLevel();
        if (level.isClientSide) return;
        LivingEntity src = event.getExplosion().getIndirectSourceEntity();
        String actor = src instanceof Player p ? p.getGameProfile().getName() : "";
        UUID actorId = src instanceof Player p ? p.getUUID() : null;
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockEntity(pos) == null) continue;
            Owned o = Access.ownerAt(level, pos);
            if (o instanceof BlockEntity be && o.getOwner() != null) raise(be, o, EXPLOSION, actor, actorId);
        }
    }

    private static void raise(BlockEntity be, Owned o, int kind, String actor, UUID actorId) {
        UUID owner = o.getOwner();
        Level level = be.getLevel();
        if (owner == null || level == null || level.getServer() == null) return;
        if (actorId != null && actorId.equals(owner)) return;
        MinecraftServer server = level.getServer();
        BlockPos pos = be.getBlockPos();
        String dim = level.dimension().location().toString();

        String key = (actorId != null ? actorId.toString() : "k" + kind) + (kind == HACK ? "|hack" : "") + "|" + dim + "|" + pos.asLong();
        long tick = server.getTickCount();
        Long last = LAST.get(key);
        if (last != null && tick - last < RATE_TICKS) return;
        LAST.put(key, tick);
        if (LAST.size() > 512) LAST.values().removeIf(t -> tick - t >= RATE_TICKS);
        record(server, owner, new Event(System.currentTimeMillis(), kind, actor == null ? "" : actor,
                be.getBlockState().getBlock().getDescriptionId(), pos.getX(), pos.getY(), pos.getZ(), dim));
    }

    static void record(MinecraftServer server, UUID owner, Event e) {
        Data.get(server).add(owner, e);
        for (CcAlarmBoardBlockEntity b : new ArrayList<>(BOARDS)) if (!b.isRemoved() && owner.equals(b.getOwner())) b.alarm();
        CcBattleLog.onAlarm(server, owner, e);
        CcDefcon.onAlarm(server, owner, e);
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p == null || !CommandCenter.hasModule(owner, CcModule.ALARM_BOARD)) return;
        Component msg = message(e);
        p.sendSystemMessage(Component.translatable("message.wmd.cc.alarm.prefix").withStyle(ChatFormatting.RED).append(msg.copy().withStyle(ChatFormatting.GOLD)));
        p.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    public static Component message(Event e) {
        Component who = e.actor().isEmpty() ? Component.translatable("message.wmd.cc.alarm.someone") : Component.literal(e.actor());
        return Component.translatable("message.wmd.cc.alarm." + e.kindName(), who, Component.translatable(e.machine()), e.x(), e.y(), e.z());
    }

    public static List<Event> events(MinecraftServer server, UUID owner) { return Data.get(server).events(owner); }

    static void clear(MinecraftServer server, UUID owner) { Data.get(server).clear(owner); }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        BOARDS.clear();
        LAST.clear();
    }
}
