package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class InterceptorSiloT4BlockEntity extends BlockEntity implements Owned, HardenedSite, GeoBlockEntity, MachinePorts, EmpVulnerable, InterceptorSite {
    private final SecurityData security = new SecurityData();
    @Override public SecurityData security() { return security; }

    public static final int SLOTS = Balance.SILO4_SLOTS, TIER = 4;

    private static final double DRUM_RATE = 1.5;
    private static final int MOVE_FE = 140;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.INT_BUFFER_FE, Balance.INT_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private record Pending(long flightId, int slot, Vec3 to, long arrive, long launchAt) {}

    public final ItemStackHandler magazine = new ItemStackHandler(SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof InterceptorItem i && i.tier() == TIER; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); sync(); }
    };
    private final Buffer store = new Buffer();
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int frequency = 1, cooldown, tracked, link;
    private long lastShot = -1000;
    private UUID owner;
    private String ownerName = "";
    private boolean hardened;
    private float lid, lift;
    private float drum;

    private Pending pending;
    private long lastTrackSeen = -100_000;
    private List<Long> lastIds = new ArrayList<>();

    float shownLid, shownDrum, shownLift;
    long clientTicks;

    public InterceptorSiloT4BlockEntity(BlockPos pos, BlockState state) { super(Wmd.SILO4_BE.get(), pos, state); }

    @Override public ItemStackHandler magazineHandler() { return magazine; }
    @Override public int slotCount() { return SLOTS; }
    @Override public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    @Override public int getFrequency() { return frequency; }
    @Override public int energy() { return store.getEnergyStored(); }
    @Override public int cooldownTicks() { return cooldown; }
    @Override public void shipShot(int fe, int reloadTicks) { store.use(fe); cooldown = Math.max(cooldown, reloadTicks); if (level != null) { lastShot = level.getGameTime(); lastTrackSeen = lastShot; } sync(); }
    void wakeForShip(long now) { lastTrackSeen = now; }
    @Override public int trackedCount() { return tracked; }
    @Override public int linkState() { return link; }
    public float lid() { return lid; }
    public boolean hardened() { return hardened; }

    @Override public int blastRating() { return hardened && lid < 0.05F ? 3 : 1; }

    public boolean harden() { if (hardened) return false; hardened = true; setChanged(); sync(); return true; }
    public float drum() { return drum; }
    public float lift() { return lift; }

    public boolean canisterLoaded(int i) { return i >= 0 && i < SLOTS && !magazine.getStackInSlot(i).isEmpty(); }

    @Override
    public int loaded() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) n += magazine.getStackInSlot(i).getCount();
        return n;
    }

    @Override public void empDrain() { store.set(0); setChanged(); }

    public void claim(Player p) { owner = p.getUUID(); ownerName = p.getGameProfile().getName(); setChanged(); sync(); }

    @Override public void setFrequency(int f) { frequency = Mth.clamp(f, 1, 9999); setChanged(); sync(); }

    private int firstLoaded() {
        for (int i = 0; i < SLOTS; i++) if (!magazine.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    private Direction facing() { return getBlockState().getValue(MachineBlock.FACING); }

    Vec3 mouth() { return SableBridge.toWorld(level, LauncherGeometry.toWorld(worldPosition, facing(), new double[] { 0.0, 72.0, 32.0 })); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InterceptorSiloT4BlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        CommandCenter.siteSeen(be);
        if (level instanceof ServerLevel sl) be.serverStep(sl, level.getGameTime());
    }

    private void serverStep(ServerLevel sl, long now) {
        if (cooldown > 0) cooldown--;
        if (now % 10 == 0 && owner != null) {
            lastIds = TrackNet.tracks(sl, owner, frequency, now);
            int ls = TrackNet.linkState(sl, owner, frequency, now);
            if (lastIds.size() != tracked || ls != link) { tracked = lastIds.size(); link = ls; sync(); }
            if (tracked > 0) lastTrackSeen = now;
        }
        boolean powered = store.getEnergyStored() >= MOVE_FE;
        boolean changed = false;
        boolean wantOpen = pending != null || now - lastTrackSeen < Balance.SILO_STOW_DELAY;
        int target = pending != null ? pending.slot() : Math.max(0, firstLoaded());
        if (powered) {
            float dl = Mth.clamp((wantOpen ? 1.0F : 0.0F) - lid, -1.0F / Balance.SILO_LID_TICKS, 1.0F / Balance.SILO_LID_TICKS);
            if (dl != 0.0F) { lid = Mth.clamp(lid + dl, 0.0F, 1.0F); changed = true; }
            boolean aligned = pending != null && Math.abs(LauncherGeometry.wrap(pending.slot() * 90.0 - drum)) < 1.0 && lid >= 0.99F;
            boolean wantUp = false;
            float du = Mth.clamp((wantUp ? 1.0F : 0.0F) - lift, -1.0F / Balance.SILO_LIFT_TICKS * 2.0F, 1.0F / Balance.SILO_LIFT_TICKS);
            if (du != 0.0F) { lift = Mth.clamp(lift + du, 0.0F, 1.0F); changed = true; }
            double dd = LauncherGeometry.wrap(target * 90.0 - drum);
            if (Math.abs(dd) > 0.01 && lid >= 0.5F && (now - lastShot > 24 || now < lastShot)) { drum = (float) LauncherGeometry.wrap(drum + Mth.clamp(dd, -DRUM_RATE, DRUM_RATE)); changed = true; }
            if (changed && now % 4 == 0) store.use(MOVE_FE);
        }
        if (pending == null) {
            if (now % 5 == 0 && cooldown == 0 && powered && owner != null && store.getEnergyStored() >= Balance.INT_SHOT_FE && firstLoaded() >= 0 && !lastIds.isEmpty()) tryPlan(sl, now);
        } else {
            FlightManager mgr = FlightManager.get(sl);
            Flight f = mgr.find(pending.flightId());
            if (f == null || now >= pending.arrive() - 10) cancel(now);
            else if (now >= pending.launchAt() - Balance.SILO4_RISE_TICKS && lid >= 0.99F && Math.abs(LauncherGeometry.wrap(pending.slot() * 90.0 - drum)) < 1.0 && store.getEnergyStored() >= Balance.INT_SHOT_FE) fire(sl, mgr, now);
        }
        if (changed && now % 2 == 0) sync();
    }

    private void cancel(long now) {
        pending = null;
        cooldown = Math.max(cooldown, 20);
        sync();
    }

    private void tryPlan(ServerLevel sl, long now) {
        FlightManager mgr = FlightManager.get(sl);
        int slot = firstLoaded();
        Vec3 site = mouth();
        for (long id : lastIds) {
            if (Interceptors.chased(mgr, id)) continue;
            Flight f = mgr.find(id);
            if (f == null) continue;
            double turn = Math.abs(LauncherGeometry.wrap(slot * 90.0 - drum)) / DRUM_RATE + ((lid < 0.5F) ? (0.5F - lid) * Balance.SILO_LID_TICKS : 0.0);
            long need = (long) Math.ceil(Math.max((1.0F - lid) * Balance.SILO_LID_TICKS, turn)) + Balance.SILO4_RISE_TICKS + 8;
            long launchAt = now + need;
            Interceptors.Shot s = Interceptors.plan(0L, site, f, launchAt, TIER, Balance.SILO_MIN_ELEV, Balance.SILO_MAX_ELEV, owner);
            if (s == null) continue;
            pending = new Pending(f.id(), slot, s.to(), s.arrive(), launchAt);
            HeatLog.fired(level, worldPosition, owner);
            sync();
            return;
        }
    }

    private void fire(ServerLevel sl, FlightManager mgr, long now) {
        Pending p = pending;
        Vec3 site = mouth();
        long exit = Math.max(now + Balance.SILO4_RISE_TICKS, p.launchAt());
        int duration = (int) (p.arrive() - exit);
        if (duration < 12 || site.distanceTo(p.to()) / duration > Balance.INT_SPEED[TIER - 1] * 1.08) { cancel(now); return; }
        int slot = magazine.getStackInSlot(p.slot()).isEmpty() ? firstLoaded() : p.slot();
        if (slot < 0) { cancel(now); return; }
        boolean refined = magazine.getStackInSlot(slot).getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0;
        Interceptors.Shot shot = new Interceptors.Shot(mgr.nextShotId(), p.flightId(), site, p.to(), exit, duration, TIER, refined);
        magazine.extractItem(slot, 1, false);
        store.use(Balance.INT_SHOT_FE);
        cooldown = Balance.INT_RELOAD_TICKS;
        lastShot = now;
        pending = null;
        mgr.addShot(shot);
        PacketDistributor.sendToPlayersNear(sl, null, site.x, site.y, site.z, 4000,
                new InterceptPayload(shot.id(), site.x, site.y, site.z, p.to().x, p.to().y, p.to().z, exit, duration, 2));
        Interceptors.announce(sl, mgr, shot);
        sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, InterceptorSiloT4BlockEntity be) {
        be.clientTicks++;
        be.shownLid += (be.lid - be.shownLid) * 0.4F;
        be.shownDrum += (float) (LauncherGeometry.wrap(be.drum - be.shownDrum) * 0.4);
        be.shownLift += (be.lift - be.shownLift) * 0.4F;
        if (be.shownLid > 0.05F) {
            Vec3 c = be.mouth().add(0.0, 0.6, 0.0);
            VeilBridge.glow(0x1A7C_0000_0000_0004L ^ pos.asLong(), c.x, c.y, c.z, 0.3F, 0.55F, 1.0F, 10.0F, 0.9F * be.shownLid, level.getGameTime());
        }
    }

    void dropContents() {
        if (level == null) return;
        for (int i = 0; i < SLOTS; i++) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, magazine.getStackInSlot(i));
    }

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return Machines.atPart(worldPosition, facing(), Machines.mount(getBlockState()), part, side, 0, 0, 8, Direction.SOUTH) ? store : null; }

    private final IItemHandler magazinePort = new InsertOnlyHandler(magazine);

    @Override public IItemHandler itemPort(BlockPos part, Direction side) { return magazinePort; }

    private void readMagazine(HolderLookup.Provider registries, CompoundTag tag) {
        if (!tag.contains("magazine")) return;
        ItemStackHandler tmp = new ItemStackHandler(SLOTS);
        tmp.deserializeNBT(registries, tag.getCompound("magazine"));
        for (int i = 0; i < SLOTS; i++) magazine.setStackInSlot(i, i < tmp.getSlots() ? tmp.getStackInSlot(i) : ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        security.write(tag);
        tag.put("magazine", magazine.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        security.read(tag);
        readMagazine(registries, tag);
        store.set(tag.getInt("energy"));
        read(tag);
    }

    private void write(CompoundTag tag) {
        tag.putInt("frequency", frequency); tag.putLong("lastShot", lastShot); tag.putInt("tracked", tracked); tag.putInt("link", link);
        tag.putBoolean("hardened", hardened); tag.putFloat("lid", lid); tag.putFloat("drum", drum); tag.putFloat("lift", lift);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
    }

    private void read(CompoundTag tag) {
        frequency = Math.max(1, tag.getInt("frequency")); lastShot = tag.contains("lastShot") ? tag.getLong("lastShot") : -1000;
        tracked = tag.getInt("tracked"); link = tag.getInt("link");
        hardened = tag.getBoolean("hardened"); lid = tag.getFloat("lid"); drum = tag.getFloat("drum"); lift = tag.getFloat("lift");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("magazine", magazine.serializeNBT(registries));
        write(tag);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        readMagazine(registries, tag);
        read(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
