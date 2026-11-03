package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Cargo {

    private Cargo() {}

    public static final String ID = "cargo";

    public static boolean isCargo(MissileBuild b) { return b != null && ID.equals(b.warheadType()); }

    record Manifest(long id, UUID owner, ResourceKey<Level> dim, NonNullList<ItemStack> items) {}

    static String key(ResourceKey<Level> dim, long id) { return dim.location() + "#" + id; }

    static final class Data extends SavedData {
        static final String NAME = "wmd_cargo";
        final Map<String, Manifest> flying = new HashMap<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag f = new CompoundTag();
            for (Map.Entry<String, Manifest> e : flying.entrySet()) {
                CompoundTag t = new CompoundTag();
                if (e.getValue().owner() != null) t.putUUID("owner", e.getValue().owner());
                t.putString("dim", e.getValue().dim().location().toString());
                ContainerHelper.saveAllItems(t, e.getValue().items(), registries);
                f.put(e.getKey(), t);
            }
            tag.put("flying", f);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            CompoundTag f = tag.getCompound("flying");
            for (String k : f.getAllKeys()) {
                CompoundTag t = f.getCompound(k);
                NonNullList<ItemStack> items = NonNullList.withSize(Balance.CARGO_SLOTS, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(t, items, registries);
                ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
                try {
                    int hash = k.lastIndexOf('#');
                    long id = Long.parseLong(hash >= 0 ? k.substring(hash + 1) : k);
                    ResourceKey<Level> dk = ResourceKey.create(Registries.DIMENSION, dim == null ? Level.OVERWORLD.location() : dim);
                    d.flying.put(key(dk, id), new Manifest(id, t.hasUUID("owner") ? t.getUUID("owner") : null, dk, items));
                } catch (NumberFormatException ignored) {}
            }
            return d;
        }
    }

    static NonNullList<ItemStack> contents(ItemStack pod) {
        NonNullList<ItemStack> l = NonNullList.withSize(Balance.CARGO_SLOTS, ItemStack.EMPTY);
        ItemContainerContents c = pod.get(DataComponents.CONTAINER);
        if (c != null) c.copyInto(l);
        return l;
    }

    static int stacks(List<ItemStack> l) {
        int n = 0;
        for (ItemStack s : l) if (!s.isEmpty()) n++;
        return n;
    }

    static void launched(ServerLevel level, long flightId, ItemStack pod, UUID owner) {
        Data d = Data.get(level.getServer());
        d.flying.put(key(level.dimension(), flightId), new Manifest(flightId, owner, level.dimension(), contents(pod)));
        d.setDirty();
    }

    static void tick(ServerLevel level, List<Flight> flights) {
        Data d = Data.get(level.getServer());
        if (d.flying.isEmpty()) return;
        for (Iterator<Map.Entry<String, Manifest>> it = d.flying.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Manifest> e = it.next();
            if (e.getValue().dim() != level.dimension()) continue;
            long id = e.getValue().id();
            if (flights.stream().anyMatch(f -> f.id() == id)) continue;
            it.remove();
            d.setDirty();
            ServerPlayer p = e.getValue().owner() == null ? null : level.getServer().getPlayerList().getPlayer(e.getValue().owner());
            if (p != null) p.sendSystemMessage(Component.translatable("message.wmd.cargo.lost", stacks(e.getValue().items())).withStyle(ChatFormatting.RED));
            Wmd.LOGGER.info("[cargo] pod of flight {} lost on the way up", id);
        }
    }

    static final int[] PORTS = { Station.DOCK, 0, 1, 2, 3 };

    static Station.Module frame(MinecraftServer server, int slot, int k) { return Station.spotFrame(server, slot, k, ID); }

    static BlockPos podController(MinecraftServer server, int slot, int k) {
        Station.Module f = frame(server, slot, k);
        return f == null ? null : Station.world(f, 2, 1, 0);
    }

    static boolean podAt(ServerLevel orbit, int slot, int k) {
        BlockPos c = orbit == null ? null : podController(orbit.getServer(), slot, k);
        return c != null && orbit.getBlockState(c).is(Wmd.CARGO_POD_BLOCK.get());
    }

    private static boolean clear(ServerLevel orbit, int slot, int k) {
        Station.Module f = frame(orbit.getServer(), slot, k);
        if (f == null) return false;
        for (int x = 1; x <= 3; x++) for (int y = 1; y <= 3; y++) for (int z = -1; z <= 1; z++) {
            BlockState b = orbit.getBlockState(Station.world(f, x, y, z));
            if (!b.isAir()) return false;
        }
        return true;
    }

    static int freePort(ServerLevel orbit, int slot) {
        if (orbit == null) return -1;
        Station.Module core = Station.core(orbit.getServer(), slot);
        if (core == null || !core.built()) return -1;
        return Station.spotState(orbit.getServer(), slot, Station.DOCK) == 0 && clear(orbit, slot, Station.DOCK) ? Station.DOCK : -1;
    }

    static int status(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return 0;
        int slot = Orbit.slotOf(server, owner);
        Station.Module core = Station.core(server, slot);
        if (core == null) return LaunchTerminalBlockEntity.STATION_NO_CORE;
        return freePort(server.getLevel(Orbit.ORBIT), slot) < 0 ? LaunchTerminalBlockEntity.CARGO_NO_PORT : 0;
    }

    static void arrive(ServerLevel level, Flight f) {
        MinecraftServer server = level.getServer();
        Data d = Data.get(server);
        Manifest man = d.flying.remove(key(level.dimension(), f.id()));
        d.setDirty();
        UUID owner = null;
        try { if (!f.ownerId().isEmpty()) owner = UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) {}
        if (owner == null && man != null) owner = man.owner();
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (orbit == null || owner == null) return;
        int slot = Orbit.slotOf(server, owner);
        NonNullList<ItemStack> items = man != null ? man.items() : NonNullList.withSize(Balance.CARGO_SLOTS, ItemStack.EMPTY);
        int port = freePort(orbit, slot);
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (port < 0) {
            if (p != null) p.sendSystemMessage(Component.translatable("message.wmd.cargo.no_port", stacks(items)).withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos c = podController(server, slot, port);
        BlockState st = Wmd.CARGO_POD_BLOCK.get().defaultBlockState().setValue(MachineBlock.FACING, Station.spotDir(port));
        orbit.setBlockAndUpdate(c, st);
        st.getBlock().setPlacedBy(orbit, c, st, null, ItemStack.EMPTY);
        if (orbit.getBlockEntity(c) instanceof CargoPodBlockEntity be) be.setUp(owner, port, items, orbit.getGameTime());
        if (p != null) p.sendSystemMessage(Component.translatable("message.wmd.cargo.arriving", port, stacks(items)).withStyle(ChatFormatting.AQUA));
        Wmd.LOGGER.info("[cargo] pod docking on port {} of slot {} ({} stacks)", port, slot, stacks(items));
    }

    static List<int[]> docked(MinecraftServer server, int slot) {
        List<int[]> out = new ArrayList<>();
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Module core = Station.core(server, slot);
        if (orbit == null || core == null) return out;
        for (int k : PORTS) {
            BlockPos c = podController(server, slot, k);
            if (c != null && orbit.getBlockEntity(c) instanceof CargoPodBlockEntity be) out.add(new int[] { k, be.stacks() });
        }
        return out;
    }

    static int podCells(MinecraftServer server, int slot) {
        int n = 0;
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Module core = Station.core(server, slot);
        if (orbit == null || core == null) return 0;
        for (int k : PORTS) {
            BlockPos c = podController(server, slot, k);
            if (c != null && orbit.getBlockEntity(c) instanceof CargoPodBlockEntity be)
                for (int i = 0; i < be.items().getSlots(); i++) if (be.items().getStackInSlot(i).is(Wmd.STATION_FUEL_CELL.get())) n += be.items().getStackInSlot(i).getCount();
        }
        return n;
    }

    static void takeCells(ServerPlayer p, int slot, int need) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(Wmd.STATION_FUEL_CELL.get())) continue;
            int n = Math.min(need, s.getCount());
            s.shrink(n);
            need -= n;
        }
        ServerLevel orbit = p.server.getLevel(Orbit.ORBIT);
        Station.Module core = Station.core(p.server, slot);
        if (orbit == null || core == null) return;
        for (int k : PORTS) {
            if (need <= 0) break;
            BlockPos c = podController(p.server, slot, k);
            if (c != null && orbit.getBlockEntity(c) instanceof CargoPodBlockEntity be)
                for (int i = 0; i < be.items().getSlots() && need > 0; i++) {
                    if (!be.items().getStackInSlot(i).is(Wmd.STATION_FUEL_CELL.get())) continue;
                    need -= be.items().extractItem(i, need, false).getCount();
                }
        }
    }

    static int playerCells(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Wmd.STATION_FUEL_CELL.get())) n += s.getCount();
        return n;
    }

    static void openFromConsole(ServerPlayer p, int slot, int k) {
        if (!((k >= 0 && k <= 3) || k == Station.DOCK) || !p.getUUID().equals(Orbit.OrbitSlots.get(p.server).ownerOf(slot))) return;
        if (!Orbit.isOrbit(p.level()) || Orbit.slotAt(p.getX(), p.getZ()) != slot) return;
        ServerLevel orbit = p.serverLevel();
        Station.Module core = Station.core(p.server, slot);
        BlockPos c = core == null ? null : podController(p.server, slot, k);
        if (c != null && orbit.isLoaded(c) && orbit.getBlockEntity(c) instanceof CargoPodBlockEntity be) CargoPodMenu.openBlock(p, be, true);
    }

    static void release(ServerPlayer p, BlockPos pos) {
        if (!(p.level() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit)) return;

        if (!orbit.isLoaded(pos)) return;
        boolean console = p.containerMenu instanceof CargoPodMenu m && m.isBlock() && m.isRemote() && pos.equals(m.getPos());
        if (!console && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5) > 8.0 * 8.0) return;
        if (!(orbit.getBlockEntity(pos) instanceof CargoPodBlockEntity be) || be.arriving(orbit.getGameTime())) return;
        if (be.owner() == null || !be.owner().equals(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.wmd.cargo.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        int port = be.port(), burnt = be.stacks();
        p.closeContainer();
        orbit.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        orbit.playSound(null, pos, WmdSounds.CG_RELEASE.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
        CargoPayloads.Release fx = new CargoPayloads.Release(pos, port, orbit.getGameTime());
        for (ServerPlayer o : orbit.players()) if (o.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 512.0 * 512.0) PacketDistributor.sendToPlayer(o, fx);
        p.sendSystemMessage(Component.translatable(burnt > 0 ? "message.wmd.cargo.released_burnt" : "message.wmd.cargo.released", burnt).withStyle(ChatFormatting.AQUA));
        Wmd.LOGGER.info("[cargo] pod on port {} released ({} stacks burnt up)", port, burnt);
    }
}
