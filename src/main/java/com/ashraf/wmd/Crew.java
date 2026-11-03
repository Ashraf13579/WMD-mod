package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Crew {

    private Crew() {}

    public static final String ID = "crew";
    static final ResourceKey<DamageType> LOST = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "capsule_lost"));
    private static final String PREV = "wmd_crew_prev", PAD = "wmd_crew_pad";

    public static boolean isCrew(MissileBuild b) { return b != null && ID.equals(b.warheadType()); }

    record Ride(List<UUID> crew, ResourceKey<Level> padDim, BlockPos pad, UUID owner, int board) {}

    private record RideKey(ResourceKey<Level> dim, long id) {}

    private static final Map<RideKey, Ride> RIDES = new HashMap<>();

    static void embark(ServerPlayer p, Vec3 inside, Vec3 back) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains(PREV)) d.putInt(PREV, p.gameMode.getGameModeForPlayer().getId());
        d.putLong(PAD, BlockPos.containing(back).asLong());
        d.putString(PAD + "_dim", p.level().dimension().location().toString());
        p.stopRiding();
        p.setGameMode(GameType.SPECTATOR);
        p.teleportTo(p.serverLevel(), inside.x, inside.y, inside.z, p.getYRot(), p.getXRot());
    }

    static boolean embarked(ServerPlayer p) { return p.getPersistentData().contains(PREV) && p.isSpectator(); }

    static void disembark(MinecraftServer server, List<UUID> crew) {
        for (UUID u : crew) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            restore(server, u, true);
            if (p != null) p.displayClientMessage(Component.translatable("message.wmd.crew.off").withStyle(ChatFormatting.YELLOW), false);
        }
    }

    static void board(ServerLevel level, long flightId, Vec3 padCentre, UUID owner, List<UUID> boarded) {
        List<UUID> crew = new ArrayList<>();
        BlockPos pad = BlockPos.containing(padCentre);
        for (UUID u : boarded) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p == null || !embarked(p) || crew.size() >= Balance.CREW_SEATS) continue;
            p.getPersistentData().putLong(PAD, pad.asLong());
            PacketDistributor.sendToPlayer(p, new CrewRidePayload(flightId, true));
            p.sendSystemMessage(Component.translatable("message.wmd.crew.aboard").withStyle(ChatFormatting.AQUA));
            crew.add(p.getUUID());
        }
        ServerPlayer op = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        int board = Boarding.launched(level, flightId, owner, op != null ? op.getGameProfile().getName() : "?");
        RIDES.put(new RideKey(level.dimension(), flightId), new Ride(crew, level.dimension(), pad, owner, board));
        Wmd.LOGGER.info("[crew] capsule flight {} lifted off from {} with {} aboard", flightId, pad.toShortString(), crew.size());
    }

    static void arrive(ServerLevel level, Flight f) {
        Ride r = RIDES.remove(new RideKey(level.dimension(), f.id()));
        MinecraftServer server = level.getServer();
        UUID owner = r != null ? r.owner() : null;
        if (owner == null) try { if (!f.ownerId().isEmpty()) owner = UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) {}
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (orbit == null || owner == null) { if (r != null) for (UUID id : r.crew()) restore(server, id, true); return; }
        ResourceKey<Level> padDim = r != null ? r.padDim() : level.dimension();
        BlockPos padPos = r != null ? r.pad() : BlockPos.containing(f.ox(), f.oy(), f.oz());
        BlockPos at = null;
        if (r != null && r.board() >= 0) {
            at = Boarding.dock(orbit, r.board(), owner, padDim, padPos);
            if (at == null) { ServerPlayer op = server.getPlayerList().getPlayer(owner); if (op != null) op.sendSystemMessage(Component.translatable("message.wmd.board.no_port").withStyle(ChatFormatting.YELLOW)); }
        }
        if (at == null) at = CrewCapsuleBlock.park(orbit, Orbit.slotOf(server, owner), owner, padDim, padPos);
        if (r == null) return;
        int seat = 0;
        for (UUID id : r.crew()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            restore(server, id, false);
            PacketDistributor.sendToPlayer(p, new CrewRidePayload(f.id(), false));
            if (at != null) CrewSeatEntity.seat(orbit, at, p, seat++);
            p.sendSystemMessage(Component.translatable("message.wmd.crew.arrived").withStyle(ChatFormatting.AQUA));
        }
    }

    static void tick(ServerLevel level, List<Flight> flights) {
        if (RIDES.isEmpty()) return;
        for (Iterator<Map.Entry<RideKey, Ride>> it = RIDES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<RideKey, Ride> e = it.next();
            if (e.getKey().dim() != level.dimension()) continue;
            long id = e.getKey().id();
            if (flights.stream().anyMatch(f -> f.id() == id)) continue;
            it.remove();
            for (UUID u : e.getValue().crew()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
                restore(level.getServer(), u, true);
                if (p == null) continue;
                PacketDistributor.sendToPlayer(p, new CrewRidePayload(id, false));
                p.hurt(new DamageSource(p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LOST)), Float.MAX_VALUE);
            }
        }
    }

    static void restore(MinecraftServer server, UUID id, boolean toPad) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null) return;
        CompoundTag d = p.getPersistentData();
        if (!d.contains(PREV)) return;
        p.setGameMode(GameType.byId(d.getInt(PREV)));
        if (toPad && d.contains(PAD)) {
            ResourceLocation dim = ResourceLocation.tryParse(d.getString(PAD + "_dim"));
            ServerLevel l = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            BlockPos pad = BlockPos.of(d.getLong(PAD));
            if (l != null) p.teleportTo(l, pad.getX() + 0.5, pad.getY() + 1.0, pad.getZ() + 0.5, p.getYRot(), p.getXRot());
        }
        d.remove(PREV); d.remove(PAD); d.remove(PAD + "_dim");
    }

    static void openMenu(ServerPlayer p, ServerLevel level, BlockPos capsule) {
        if (!(level.getBlockEntity(capsule) instanceof CrewCapsuleBlockEntity be)) return;
        PacketDistributor.sendToPlayer(p, new CrewCapsulePayloads.Open(capsule, seated(level, capsule).size(), be.landed(), be.pad().getX(), be.pad().getZ(),
                (be.boardSlot() < 0 && dockedCore(level, capsule) != null) || Boarding.entry(level, capsule) != null));
    }

    static Station.Module dockedCore(ServerLevel level, BlockPos capsule) {
        if (!Orbit.isOrbit(level)) return null;
        int slot = Orbit.slotAt(capsule.getX(), capsule.getZ());
        Station.Module core = slot < 0 ? null : Station.core(level.getServer(), slot);
        return core != null && core.built() && Station.world(core, 0, 9, 0).equals(capsule) ? core : null;
    }

    static List<ServerPlayer> seated(ServerLevel level, BlockPos capsule) {
        List<ServerPlayer> out = new ArrayList<>();
        for (CrewSeatEntity s : level.getEntitiesOfClass(CrewSeatEntity.class, new net.minecraft.world.phys.AABB(capsule).inflate(2.0)))
            for (var e : s.getPassengers()) if (e instanceof ServerPlayer sp) out.add(sp);
        return out;
    }

    static void action(ServerPlayer p, BlockPos capsule, int action, int x, int z) {
        if (!(p.level() instanceof ServerLevel level)) return;
        if (p.distanceToSqr(capsule.getX() + 0.5, capsule.getY() + 1.0, capsule.getZ() + 0.5) > 64.0 || !level.isLoaded(capsule)) return;
        if (!(level.getBlockEntity(capsule) instanceof CrewCapsuleBlockEntity be)) return;
        switch (action) {
            case CrewCapsulePayloads.SEAT -> {
                if (be.landed()) return;
                if (!CrewSeatEntity.seat(level, capsule, p, -1)) p.displayClientMessage(Component.translatable("message.wmd.crew.full").withStyle(ChatFormatting.YELLOW), true);
            }
            case CrewCapsulePayloads.RETURN_PAD, CrewCapsulePayloads.RETURN_XZ -> {
                if (be.landed() || !Orbit.isOrbit(level)) return;
                if (!mayDrive(p, level, capsule, be)) { denyDrive(p, be); return; }
                boolean pad = action == CrewCapsulePayloads.RETURN_PAD;
                startReturn(p, level, capsule, be, pad ? be.pad().getX() + Balance.CREW_PAD_OFFSET : x, pad ? be.pad().getZ() : z);
            }
            case CrewCapsulePayloads.ENTER -> {
                BlockPos deck = be.boardSlot() >= 0 ? Boarding.entry(level, capsule) : null;
                if (be.boardSlot() < 0) { Station.Module core = dockedCore(level, capsule); if (core != null) deck = Station.world(core, 0, 1, 0); }
                if (deck == null) { if (be.boardSlot() >= 0) p.displayClientMessage(Component.translatable("message.wmd.board.still_cutting").withStyle(ChatFormatting.YELLOW), true); return; }
                p.stopRiding();
                p.teleportTo(level, deck.getX() + 0.5, deck.getY(), deck.getZ() + 0.5, p.getYRot(), p.getXRot());
                level.playSound(null, capsule, WmdSounds.OR_CAPSULE_HATCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.9F);
            }
            case CrewCapsulePayloads.RECOVER -> {
                if (!be.landed()) return;
                if (!mayDrive(p, level, capsule, be)) { denyDrive(p, be); return; }

                if (!ClaimGuard.mayPlace(level, p, java.util.List.of(capsule), null)) return;
                level.removeBlock(capsule, false);
                net.minecraft.world.item.ItemStack used = new net.minecraft.world.item.ItemStack(Wmd.USED_CREW_CAPSULE.get());
                if (!p.getInventory().add(used)) p.drop(used, false);
                p.displayClientMessage(Component.translatable("message.wmd.crew.recovered").withStyle(ChatFormatting.AQUA), true);
            }
            default -> {}
        }
    }

    private static boolean mayDrive(ServerPlayer p, ServerLevel level, BlockPos capsule, CrewCapsuleBlockEntity be) {
        UUID o = be.owner();
        return o == null || o.equals(p.getUUID()) || p.hasPermissions(2) || seated(level, capsule).contains(p);
    }

    private static void denyDrive(ServerPlayer p, CrewCapsuleBlockEntity be) {
        var cache = p.server.getProfileCache();
        String name = be.owner() == null || cache == null ? "?" : cache.get(be.owner()).map(com.mojang.authlib.GameProfile::getName).orElse("?");
        p.displayClientMessage(Component.translatable("message.wmd.not_yours", name), true);
    }

    static void clearStatics() { RIDES.clear(); BURNS.clear(); }

    record Descent(ResourceKey<Level> dim, int x, int z, int ground, long end, List<UUID> crew, UUID owner, ResourceKey<Level> padDim, BlockPos pad) {}

    static final class Descents extends net.minecraft.world.level.saveddata.SavedData {
        static final String NAME = "wmd_crew_descents";
        final List<Descent> list = new ArrayList<>();

        static Descents get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Descents::new, Descents::load, null), NAME);
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (Descent d : list) {
                CompoundTag t = new CompoundTag();
                t.putString("dim", d.dim().location().toString()); t.putInt("x", d.x()); t.putInt("z", d.z()); t.putInt("ground", d.ground());
                t.putLong("end", d.end()); t.putUUID("owner", d.owner());
                t.putString("padDim", d.padDim().location().toString()); t.putLong("pad", d.pad().asLong());
                net.minecraft.nbt.ListTag c = new net.minecraft.nbt.ListTag();
                for (UUID u : d.crew()) c.add(net.minecraft.nbt.NbtUtils.createUUID(u));
                t.put("crew", c);
                l.add(t);
            }
            tag.put("descents", l);
            return tag;
        }

        static Descents load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            Descents s = new Descents();
            net.minecraft.nbt.ListTag l = tag.getList("descents", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag t = l.getCompound(i);
                ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim")), pd = ResourceLocation.tryParse(t.getString("padDim"));
                if (dim == null || pd == null || !t.hasUUID("owner")) continue;
                List<UUID> crew = new ArrayList<>();
                for (net.minecraft.nbt.Tag c : t.getList("crew", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) crew.add(net.minecraft.nbt.NbtUtils.loadUUID(c));
                s.list.add(new Descent(ResourceKey.create(Registries.DIMENSION, dim), t.getInt("x"), t.getInt("z"), t.getInt("ground"), t.getLong("end"),
                        crew, t.getUUID("owner"), ResourceKey.create(Registries.DIMENSION, pd), BlockPos.of(t.getLong("pad"))));
            }
            return s;
        }
    }

    private record Burn(ServerLevel orbit, BlockPos capsule, UUID by, int x, int z, ResourceKey<Level> to, long start, java.util.Set<Long> held) {}

    private static final List<Burn> BURNS = new ArrayList<>();

    static void startReturn(ServerPlayer by, ServerLevel orbit, BlockPos capsule, CrewCapsuleBlockEntity be, int x, int z) {
        MinecraftServer server = orbit.getServer();
        ServerLevel to = server.getLevel(be.padDim());
        if (to == null) to = server.overworld();
        if (!to.getWorldBorder().isWithinBounds(x, z)) { by.displayClientMessage(Component.translatable("message.wmd.crew.border").withStyle(ChatFormatting.RED), true); return; }
        for (Burn b : BURNS) if (b.capsule().equals(capsule) && b.orbit() == orbit) return;
        Burn b = new Burn(orbit, capsule.immutable(), by.getUUID(), x, z, to.dimension(), orbit.getGameTime(), new java.util.HashSet<>());
        preload(to, b, true);
        BURNS.add(b);
        List<ServerPlayer> crew = seated(orbit, capsule);
        if (!crew.contains(by)) crew.add(by);
        for (ServerPlayer p : crew) {
            PacketDistributor.sendToPlayer(p, new CrewCapsulePayloads.Burn(b.start(), Balance.CREW_BURN_TICKS, capsule.getX(), capsule.getY(), capsule.getZ()));
            p.sendSystemMessage(Component.translatable("message.wmd.crew.burn", Balance.CREW_BURN_TICKS / 20).withStyle(ChatFormatting.AQUA));
        }
        Wmd.LOGGER.info("[crew] de-orbit burn: capsule {} -> {} {} {} (preloading {} chunks)", capsule.toShortString(), to.dimension().location(), x, z, b.held().size());
    }

    private static void preload(ServerLevel to, Burn b, boolean put) {
        long owner = b.capsule().asLong() ^ 0x4255524EL;
        int cx = b.x() >> 4, cz = b.z() >> 4, r = Balance.CREW_PRELOAD_R;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            long k = net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz);
            if (put ? b.held().add(k) : b.held().remove(k)) AsyncChunks.ticket(to, owner, cx + dx, cz + dz, put);
        }
    }

    private static boolean landReady(ServerLevel to, Burn b) {
        for (long k : b.held()) if (AsyncChunks.now(to, net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k)) == null) return false;
        return true;
    }

    private static void tickBurns(ServerLevel orbit) {
        if (BURNS.isEmpty()) return;
        long now = orbit.getGameTime();
        for (Iterator<Burn> it = BURNS.iterator(); it.hasNext(); ) {
            Burn b = it.next();
            if (b.orbit() != orbit) continue;
            long el = now - b.start();
            ServerLevel to = orbit.getServer().getLevel(b.to());
            if (to == null || !(orbit.getBlockEntity(b.capsule()) instanceof CrewCapsuleBlockEntity be)) { it.remove(); if (to != null) preload(to, b, false); continue; }
            if (el < Balance.CREW_BURN_TICKS || el < Balance.CREW_BURN_TICKS + Balance.CREW_BURN_WAIT && !landReady(to, b)) continue;
            it.remove();
            ServerPlayer by = orbit.getServer().getPlayerList().getPlayer(b.by());
            if (by == null) { List<ServerPlayer> s = seated(orbit, b.capsule()); by = s.isEmpty() ? null : s.get(0); }
            if (by != null) doReturn(by, orbit, b.capsule(), be, b.x(), b.z());
            preload(to, b, false);
        }
    }

    static void doReturn(ServerPlayer by, ServerLevel orbit, BlockPos capsule, CrewCapsuleBlockEntity be, int x, int z) {
        MinecraftServer server = orbit.getServer();
        ServerLevel to = server.getLevel(be.padDim());
        if (to == null) to = server.overworld();
        to.getChunk(x >> 4, z >> 4);
        int[] spot = landingSpot(to, x, z);
        if (spot != null) { x = spot[0]; z = spot[2]; }
        int ground = spot != null ? spot[1] : to.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        List<ServerPlayer> crew = seated(orbit, capsule);
        if (!crew.contains(by)) crew.add(by);
        UUID owner = be.owner();
        ResourceKey<Level> padDim = be.padDim();
        BlockPos pad = be.pad();
        for (ServerPlayer p : crew) p.stopRiding();
        Station.Module docked = dockedCore(orbit, capsule);
        if (docked != null && orbit.getBlockEntity(Station.controller(docked)) instanceof StationModuleBlockEntity sb) sb.setDock(0L);
        orbit.removeBlock(capsule, false);
        long now = to.getGameTime();
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer p : crew) {
            CompoundTag d = p.getPersistentData();
            d.putInt(PREV, p.gameMode.getGameModeForPlayer().getId());
            d.putLong(PAD, new BlockPos(x + 3, ground, z).asLong());
            d.putString(PAD + "_dim", to.dimension().location().toString());
            p.setGameMode(GameType.SPECTATOR);
            p.teleportTo(to, x + 0.5, ground + 40.0, z + 0.5, p.getYRot(), p.getXRot());
            PacketDistributor.sendToPlayer(p, new CrewCapsulePayloads.Descent(x + 0.5, z + 0.5, ground, now, Balance.CREW_DESCENT_TICKS, true));
            p.sendSystemMessage(Component.translatable("message.wmd.crew.deorbit").withStyle(ChatFormatting.AQUA));
            ids.add(p.getUUID());
        }
        for (ServerPlayer o : to.players())
            if (!ids.contains(o.getUUID()) && Math.hypot(o.getX() - x, o.getZ() - z) < Balance.CREW_SEEN_R)
                PacketDistributor.sendToPlayer(o, new CrewCapsulePayloads.Descent(x + 0.5, z + 0.5, ground, now, Balance.CREW_DESCENT_TICKS, false));
        Descents saved = Descents.get(server);
        saved.list.add(new Descent(to.dimension(), x, z, ground, now + Balance.CREW_DESCENT_TICKS, ids, owner, padDim, pad));
        saved.setDirty();
        Wmd.LOGGER.info("[crew] capsule returning to {} {} {} with {} aboard", to.dimension().location(), x, z, ids.size());
    }

    @SubscribeEvent
    static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (Orbit.isOrbit(level)) tickBurns(level);
        Descents saved = Descents.get(level.getServer());
        if (saved.list.isEmpty()) return;
        long now = level.getGameTime();
        List<Descent> due = new ArrayList<>();
        for (Iterator<Descent> it = saved.list.iterator(); it.hasNext(); ) {
            Descent d = it.next();
            if (d.dim() != level.dimension() || now < d.end()) continue;
            it.remove();
            due.add(d);
        }
        if (due.isEmpty()) return;
        saved.setDirty();
        for (Descent d : due) land(level, d);
    }

    static int[] landingSpot(ServerLevel level, int x, int z) {
        for (int r = 0; r <= Balance.CREW_LAND_SEARCH; r += 2)
            for (int dx = -r; dx <= r; dx += 2) for (int dz = -r; dz <= r; dz += 2) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                Integer g = spotGround(level, x + dx, z + dz);
                if (g != null) return new int[] { x + dx, g, z + dz };
            }
        return null;
    }

    private static Integer spotGround(ServerLevel level, int cx, int cz) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + i, cz + k);
            BlockPos under = new BlockPos(cx + i, h - 1, cz + k);
            BlockState b = level.getBlockState(under);
            if (!b.getFluidState().isEmpty() || b.is(net.minecraft.tags.BlockTags.LOGS) || b.is(net.minecraft.tags.BlockTags.LEAVES)
                    || !b.isFaceSturdy(level, under, net.minecraft.core.Direction.UP)) return null;
            lo = Math.min(lo, h); hi = Math.max(hi, h);
        }
        if (hi - lo > 1) return null;
        for (int i = -1; i <= 1; i++) for (int y = 0; y <= 2; y++) for (int k = -1; k <= 1; k++)
            if (!clearable(level.getBlockState(new BlockPos(cx + i, hi + y, cz + k)))) return null;
        return hi;
    }

    private static boolean clearable(BlockState s) {
        return s.getFluidState().isEmpty() && (s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES));
    }

    private static void land(ServerLevel level, Descent d) {
        BlockPos at = null;
        for (int dy = 0; dy <= 10 && at == null; dy++) {
            BlockPos c = new BlockPos(d.x(), d.ground() + dy, d.z());
            boolean free = true;
            for (int x = -1; x <= 1 && free; x++) for (int y = 0; y <= 2 && free; y++) for (int z = -1; z <= 1 && free; z++)
                { BlockState s = level.getBlockState(c.offset(x, y, z)); if (!s.canBeReplaced() && !s.is(net.minecraft.tags.BlockTags.LEAVES)) free = false; }
            if (free) at = c;
        }

        if (at != null) {
            ServerPlayer op = d.owner() == null ? null : level.getServer().getPlayerList().getPlayer(d.owner());
            for (BlockPos k : List.of(at.offset(-1, 0, -1), at.offset(1, 0, -1), at.offset(-1, 0, 1), at.offset(1, 0, 1)))
                if (op != null && op.level() == level ? !ClaimGuard.mayPlaceQuiet(level, op, k) : ClaimGuard.protectedAt(level, k)) { at = null; break; }
        }
        if (at == null) {
            net.minecraft.world.item.ItemStack used = new net.minecraft.world.item.ItemStack(Wmd.USED_CREW_CAPSULE.get());
            ServerPlayer to = null;
            for (UUID id : d.crew()) { ServerPlayer p = level.getServer().getPlayerList().getPlayer(id); if (p != null && (to == null || id.equals(d.owner()))) to = p; }
            if (to != null && to.getInventory().add(used)) to.displayClientMessage(Component.translatable("message.wmd.crew.recovered").withStyle(ChatFormatting.AQUA), true);
            else level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, d.x() + 0.5, d.ground() + 1.0, d.z() + 0.5, used));
        }
        if (at != null) {
            BlockState st = Wmd.CREW_CAPSULE_BLOCK.get().defaultBlockState();
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) level.removeBlock(at.offset(x, y, z), false);
            level.setBlockAndUpdate(at, st);
            st.getBlock().setPlacedBy(level, at, st, null, net.minecraft.world.item.ItemStack.EMPTY);
            if (level.getBlockEntity(at) instanceof CrewCapsuleBlockEntity be) { be.setUp(d.owner(), d.padDim(), d.pad()); be.setLanded(); }
            level.playSound(null, at, WmdSounds.OR_CAPSULE_TOUCHDOWN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 2.0, 0.3, 2.0, 0.02);
        }
        for (UUID id : d.crew()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            restore(level.getServer(), id, true);
            p.sendSystemMessage(Component.translatable("message.wmd.crew.landed").withStyle(ChatFormatting.AQUA));
        }
        Wmd.LOGGER.info("[crew] capsule landed at {} {} {}", d.x(), at == null ? d.ground() : at.getY(), d.z());
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getPersistentData().contains(PREV)) return;
        for (Map.Entry<RideKey, Ride> e : RIDES.entrySet())
            if (e.getValue().crew().contains(p.getUUID())) {
                PacketDistributor.sendToPlayer(p, new CrewRidePayload(e.getKey().id(), true));
                return;
            }
        for (Descent d : Descents.get(p.server).list)
            if (d.crew().contains(p.getUUID())) {
                PacketDistributor.sendToPlayer(p, new CrewCapsulePayloads.Descent(d.x() + 0.5, d.z() + 0.5, d.ground(), d.end() - Balance.CREW_DESCENT_TICKS,
                        Balance.CREW_DESCENT_TICKS, true));
                return;
            }
        restore(p.server, p.getUUID(), true);
    }
}
