package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcStrikeData extends SavedData {

    private static final String NAME = "wmd_cc_strikes";

    public static final int LINGER_TICKS = 600;
    public static final int WAITING = 0, FIRED = 1, SKIPPED = 2;

    public static final int CRUISE = -2;

    public static final class Shot {
        final long plan;
        final UUID owner;
        final String ownerName, dim;
        long pos;
        final int x, z;
        final long fireAt, impactAt;

        final int art;
        int state;

        Shot(long plan, UUID owner, String ownerName, String dim, long pos, int x, int z, long fireAt, long impactAt, int state, int art) {
            this.plan = plan; this.owner = owner; this.ownerName = ownerName; this.dim = dim; this.pos = pos; this.x = x; this.z = z;
            this.fireAt = fireAt; this.impactAt = impactAt; this.state = state; this.art = art;
        }

        public boolean artillery() { return art >= 0; }

        public boolean cruise() { return art == CRUISE; }
        public int shellType() { return art; }

        public long plan() { return plan; }
        public BlockPos pos() { return BlockPos.of(pos); }
        public int x() { return x; }
        public int z() { return z; }
        public long fireAt() { return fireAt; }
        public long impactAt() { return impactAt; }
        public int state() { return state; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("plan", plan); t.putUUID("owner", owner); t.putString("name", ownerName); t.putString("dim", dim); t.putLong("pos", pos);
            t.putInt("x", x); t.putInt("z", z); t.putLong("fire", fireAt); t.putLong("impact", impactAt); t.putInt("state", state);
            t.putInt("art", art);
            return t;
        }

        static Shot load(CompoundTag t) {
            return new Shot(t.getLong("plan"), t.getUUID("owner"), t.getString("name"), t.getString("dim"), t.getLong("pos"), t.getInt("x"), t.getInt("z"),
                    t.getLong("fire"), t.getLong("impact"), t.getInt("state"), t.contains("art") ? t.getInt("art") : -1);
        }
    }

    private final List<Shot> shots = new ArrayList<>();
    private long nextPlan = 1;

    public static CcStrikeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CcStrikeData::new, CcStrikeData::load, null), NAME);
    }

    public List<Shot> shotsOf(UUID owner) {
        List<Shot> l = new ArrayList<>();
        for (Shot s : shots) if (s.owner.equals(owner)) l.add(s);
        l.sort((a, b) -> Long.compare(a.fireAt, b.fireAt));
        return l;
    }

    public int relink(ServerLevel level, java.util.function.LongUnaryOperator map) {
        String d = level.dimension().location().toString();
        int n = 0;
        for (Shot s : shots) {
            if (!s.dim.equals(d)) continue;
            long to = map.applyAsLong(s.pos);
            if (to != s.pos) { s.pos = to; n++; }
        }
        if (n > 0) setDirty();
        return n;
    }

    public boolean busy(Level level, BlockPos pos) {
        String d = level.dimension().location().toString();
        for (Shot s : shots) if (s.state == WAITING && s.pos == pos.asLong() && s.dim.equals(d)) return true;
        return false;
    }

    public long add(Level level, UUID owner, String ownerName, List<long[]> pads, int x, int z, long impactAt) {
        long id = nextPlan++;
        String d = level.dimension().location().toString();
        for (long[] p : pads) shots.add(new Shot(id, owner, ownerName, d, p[0], x, z, p[1], impactAt, WAITING, p.length > 2 ? (int) p[2] : -1));
        setDirty();
        return id;
    }

    public int cancel(UUID owner) {
        int n = 0;
        for (Iterator<Shot> it = shots.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            if (s.owner.equals(owner) && s.state == WAITING) { it.remove(); n++; }
        }
        if (n > 0) setDirty();
        return n;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        CcStrikeData d = server.overworld().getDataStorage().get(new SavedData.Factory<>(CcStrikeData::new, CcStrikeData::load, null), NAME);
        if (d != null && !d.shots.isEmpty()) d.tick(server);
    }

    private void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        boolean changed = false;
        for (Shot s : List.copyOf(shots)) {
            if (s.state == WAITING && now >= s.fireAt) { fire(server, s); changed = true; }
        }
        if (shots.removeIf(s -> s.state != WAITING && now > s.impactAt + LINGER_TICKS)) changed = true;
        if (changed) setDirty();
    }

    private void fire(MinecraftServer server, Shot s) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.dim)));
        BlockPos pos = BlockPos.of(s.pos);
        String P = "message.wmd.cc.strike.";
        Component why;
        if (s.artillery()) {
            ArtilleryGunBlockEntity g = level == null ? null : ArtilleryGuns.gunAt(level, pos, s.owner);
            if (g == null) why = Component.translatable(P + "gone");
            else if (!CcReach.reach(level, s.owner, pos)) why = Component.translatable(P + "no_reach");
            else {
                int st = g.order(s.x, s.z, s.art, 1, s.ownerName);
                why = st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING || st == ArtilleryGunBlockEntity.S_FIRING ? null
                        : Component.translatable("gui.wmd.art.st." + st);
            }
        } else if (s.cruise()) {
            CruiseLauncherBlockEntity l = level == null ? null : CruiseLaunchers.launcherAt(level, pos, s.owner);
            if (l == null) why = Component.translatable(P + "gone");
            else if (!CcReach.reach(level, s.owner, pos)) why = Component.translatable(P + "no_reach");
            else {
                int st = l.order(s.x, s.z, 1, s.ownerName);
                why = st == CruiseLauncherBlockEntity.S_RAISING || st == CruiseLauncherBlockEntity.S_FIRING ? null : Component.translatable("gui.wmd.cruise.st." + st);
            }
        } else {
            LaunchTerminalBlockEntity t = level == null ? null : CcLaunch.terminalAt(level, pos, s.owner);
            if (t == null) why = Component.translatable(P + "gone");
            else if (!CcReach.reach(level, s.owner, pos)) why = Component.translatable(P + "no_reach");
            else why = t.remoteLaunch(s.owner, s.ownerName, s.x, s.z);
        }
        s.state = why == null ? FIRED : SKIPPED;
        ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
        if (why == null) {
            Wmd.LOGGER.info("Strike planner: plan {} fired the pad at {} at {} {}", s.plan, pos.toShortString(), s.x, s.z);
            if (p != null) p.displayClientMessage(Component.translatable(P + "fired", pos.toShortString(), OrbitMath.mmss(Math.max(0L, s.impactAt - s.fireAt) * 50L)), true);
        } else {
            Wmd.LOGGER.info("Strike planner: plan {} skipped the pad at {}: {}", s.plan, pos.toShortString(), why.getString());
            if (p != null) p.displayClientMessage(Component.translatable(P + "skipped", pos.toShortString(), why), false);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Shot s : shots) l.add(s.save());
        tag.put("shots", l);
        tag.putLong("nextPlan", nextPlan);
        return tag;
    }

    public static CcStrikeData load(CompoundTag tag, HolderLookup.Provider registries) {
        CcStrikeData d = new CcStrikeData();
        for (Tag t : tag.getList("shots", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("owner")) d.shots.add(Shot.load(c));
        }
        d.nextPlan = Math.max(1, tag.getLong("nextPlan"));
        return d;
    }
}
