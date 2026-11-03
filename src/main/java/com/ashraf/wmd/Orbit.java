package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Orbit {

    private Orbit() {}

    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "orbit"));
    private static final String RETURN = "wmd_orbit_return";

    public static boolean isOrbit(Level level) { return level != null && level.dimension() == ORBIT; }

    public static BlockPos slotCentre(int n) { return new BlockPos(n * Balance.ORBIT_SLOT_SPACING, Balance.ORBIT_Y, 0); }

    public static int slotAt(double x, double z) {
        int n = (int) Math.round(x / Balance.ORBIT_SLOT_SPACING);
        return n >= 0 && Math.abs(x - (double) n * Balance.ORBIT_SLOT_SPACING) <= Balance.ORBIT_BUILD_R && Math.abs(z) <= Balance.ORBIT_BUILD_R ? n : -1;
    }

    static final class OrbitSlots extends SavedData {
        static final String NAME = "wmd_orbit_slots";
        final Map<UUID, Integer> slots = new HashMap<>();
        final Map<Integer, Ground> grounds = new HashMap<>();
        int next;

        static OrbitSlots get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(OrbitSlots::new, OrbitSlots::load, null), NAME);
        }

        int slotOf(UUID owner) {
            Integer n = slots.get(owner);
            if (n == null) { n = next++; slots.put(owner, n); setDirty(); }
            return n;
        }

        UUID ownerOf(int n) {
            for (Map.Entry<UUID, Integer> e : slots.entrySet()) if (e.getValue() == n) return e.getKey();
            return null;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag m = new CompoundTag();
            for (Map.Entry<UUID, Integer> e : slots.entrySet()) m.putInt(e.getKey().toString(), e.getValue());
            tag.put("slots", m);
            tag.putInt("next", next);
            CompoundTag g = new CompoundTag();
            for (Map.Entry<Integer, Ground> e : grounds.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putString("dim", e.getValue().dim().location().toString()); t.putInt("x", e.getValue().x()); t.putInt("z", e.getValue().z());
                g.put(Integer.toString(e.getKey()), t);
            }
            tag.put("grounds", g);
            return tag;
        }

        static OrbitSlots load(CompoundTag tag, HolderLookup.Provider registries) {
            OrbitSlots s = new OrbitSlots();
            CompoundTag m = tag.getCompound("slots");
            for (String k : m.getAllKeys()) {
                try { s.slots.put(UUID.fromString(k), m.getInt(k)); } catch (IllegalArgumentException ignored) {}
            }
            s.next = Math.max(tag.getInt("next"), s.slots.values().stream().mapToInt(i -> i + 1).max().orElse(0));
            CompoundTag g = tag.getCompound("grounds");
            for (String k : g.getAllKeys()) {
                CompoundTag t = g.getCompound(k);
                ResourceLocation d = ResourceLocation.tryParse(t.getString("dim"));
                try { if (d != null) s.grounds.put(Integer.parseInt(k), new Ground(ResourceKey.create(Registries.DIMENSION, d), t.getInt("x"), t.getInt("z"))); } catch (NumberFormatException ignored) {}
            }
            return s;
        }
    }

    public static int slotOf(MinecraftServer server, UUID owner) { return OrbitSlots.get(server).slotOf(owner); }

    public record Ground(ResourceKey<Level> dim, int x, int z) {}

    public static Ground ground(MinecraftServer server, int n) {
        Ground g = OrbitSlots.get(server).grounds.get(n);
        if (g != null) return g;
        BlockPos s = server.overworld().getSharedSpawnPos();
        return new Ground(Level.OVERWORLD, s.getX(), s.getZ());
    }

    static boolean hasGround(MinecraftServer server, int n) { return OrbitSlots.get(server).grounds.containsKey(n); }

    static void setGround(MinecraftServer server, int n, ResourceKey<Level> dim, int x, int z) {
        if (dim == ORBIT) return;
        OrbitSlots s = OrbitSlots.get(server);
        s.grounds.put(n, new Ground(dim, x, z));
        s.setDirty();
    }

    static void goUp(ServerPlayer p, boolean platform) {
        ServerLevel orbit = p.server.getLevel(ORBIT);
        if (orbit == null) { p.sendSystemMessage(Component.literal("The orbit dimension is missing (data pack wmd:orbit not loaded).").withStyle(ChatFormatting.RED)); return; }
        if (!isOrbit(p.level())) {
            CompoundTag r = new CompoundTag();
            r.putString("dim", p.level().dimension().location().toString());
            r.putDouble("x", p.getX()); r.putDouble("y", p.getY()); r.putDouble("z", p.getZ());
            p.getPersistentData().put(RETURN, r);
        }
        int n = slotOf(p.server, p.getUUID());
        if (!isOrbit(p.level()) && !hasGround(p.server, n)) setGround(p.server, n, p.level().dimension(), p.getBlockX(), p.getBlockZ());
        BlockPos c = slotCentre(n);
        if (platform) {
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos q = c.offset(dx, -1, dz);
                    if (!orbit.getBlockState(q).isAir()) continue;
                    boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                    orbit.setBlockAndUpdate(q, (dx == 0 && dz == 0 ? Blocks.SEA_LANTERN : edge ? Blocks.BLUE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState());
                }
        }
        p.teleportTo(orbit, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.sendSystemMessage(Component.literal("In orbit: your slot " + n + " at x " + c.getX() + ", y " + c.getY() + ", z " + c.getZ()
                + ". Build within " + Balance.ORBIT_BUILD_R + " blocks of it.").withStyle(ChatFormatting.AQUA));
    }

    static void goDown(ServerPlayer p) {
        CompoundTag r = p.getPersistentData().getCompound(RETURN);
        ServerLevel to = null;
        Vec3 at = null;
        if (r.contains("dim")) {
            ResourceLocation id = ResourceLocation.tryParse(r.getString("dim"));
            if (id != null) to = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            at = new Vec3(r.getDouble("x"), r.getDouble("y"), r.getDouble("z"));
        }
        if (to == null || isOrbit(to)) {
            to = p.server.overworld();
            BlockPos s = to.getSharedSpawnPos();
            at = new Vec3(s.getX() + 0.5, to.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.getX(), s.getZ()), s.getZ() + 0.5);
        }
        p.getPersistentData().remove(RETURN);
        p.teleportTo(to, at.x, at.y, at.z, p.getYRot(), p.getXRot());
    }

    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getSpawnLevel() != ORBIT || event.getNewSpawn() == null) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.literal("No respawn point in orbit: you would wake up on Earth").withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !isOrbit(sl)) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (p.isCreative() && p.hasPermissions(2)) return;
        BlockPos pos = event.getPos();
        int n = slotAt(pos.getX() + 0.5, pos.getZ() + 0.5);
        UUID owner = n < 0 ? null : OrbitSlots.get(sl.getServer()).ownerOf(n);
        if (p.getUUID().equals(owner)) return;
        if (n >= 0 && explosive(event.getPlacedBlock())) return;
        event.setCanceled(true);
        p.displayClientMessage(Component.literal(n < 0 ? "Too far from your station slot to build" : "That is another owner's slot").withStyle(ChatFormatting.RED), true);
    }

    static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> STATION_EXPLOSIVES =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_explosives"));

    static boolean explosive(net.minecraft.world.level.block.state.BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.TntBlock || s.is(STATION_EXPLOSIVES);
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !isOrbit(event.getLevel()) || !(event.getEntity() instanceof ItemEntity it)) return;
        it.setNoGravity(true);
        it.setDeltaMovement(it.getDeltaMovement().scale(0.3));
    }
}
