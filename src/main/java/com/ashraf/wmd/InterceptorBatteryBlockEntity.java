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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

public class InterceptorBatteryBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, MachinePorts, EmpVulnerable, InterceptorSite {
    private final SecurityData security = new SecurityData();
    @Override public SecurityData security() { return security; }

    public static final int SLOTS = Balance.BATTERY_SLOTS, TIER = 2;

    private static final double AZ_RATE = 1.2, EL_RATE = 0.8;
    private static final int CAP_TICKS = 18, ARM_TICKS = 90, STOW_DELAY = 600, MOVE_FE = 200;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.INT_BUFFER_FE, Balance.INT_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private record Pending(long flightId, int slot, Vec3 to, long arrive, double az, double el, long openedAt) {}

    public final ItemStackHandler magazine = new ItemStackHandler(SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof InterceptorItem i && i.tier() == TIER; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); sync(); }
    };
    private final Buffer store = new Buffer();
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int frequency = 1, cooldown, lastSlot = -1, tracked, capMask, link;
    private long lastShot = -1000;
    private UUID owner;
    private String ownerName = "";
    private float az, el = (float) BatteryGeometry.STOW_EL, ramp;

    private double tAz, tEl = BatteryGeometry.STOW_EL;
    private Pending pending;
    private long lastTrackSeen = -100_000;
    private List<Long> lastIds = new ArrayList<>();
    private final long[] capCloseAt = new long[SLOTS];

    float shownAz, shownEl = (float) BatteryGeometry.STOW_EL, shownRamp;
    final float[] shownCap = new float[SLOTS];
    long clientTicks;

    public InterceptorBatteryBlockEntity(BlockPos pos, BlockState state) { super(Wmd.HEAVY_BATTERY_BE.get(), pos, state); }

    @Override public ItemStackHandler magazineHandler() { return magazine; }
    @Override public int slotCount() { return SLOTS; }
    @Override public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    @Override public int getFrequency() { return frequency; }
    @Override public int energy() { return store.getEnergyStored(); }
    @Override public int cooldownTicks() { return cooldown; }
    @Override public void shipShot(int fe, int reloadTicks) { store.use(fe); cooldown = Math.max(cooldown, reloadTicks); setChanged(); }
    @Override public int trackedCount() { return tracked; }

    @Override public int linkState() { return link; }
    public long lastShot() { return lastShot; }
    public int lastSlot() { return lastSlot; }
    public int capMask() { return capMask; }
    public float ramp() { return ramp; }

    @Override public int loaded() {
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

    private Vec3 muzzle(int tube, double elevation, double azimuth) {
        return SableBridge.toWorld(level, BatteryGeometry.toWorld(worldPosition, facing(), BatteryGeometry.tubeCentre(tube, elevation, azimuth)));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InterceptorBatteryBlockEntity be) {
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
        boolean want = pending != null || now - lastTrackSeen < STOW_DELAY;
        if (powered) {
            float dr = (float) Mth.clamp((want ? 1.0 : 0.0) - ramp, -1.0 / ARM_TICKS, 1.0 / ARM_TICKS);
            if (dr != 0.0F) { ramp += dr; changed = true; }
            if (pending == null) tEl = BatteryGeometry.STOW_EL + (BatteryGeometry.READY_EL - BatteryGeometry.STOW_EL) * ramp;
            double dAz = BatteryGeometry.wrap(tAz - az), dEl = tEl - el;
            if (Math.abs(dAz) > 0.01) { az = (float) BatteryGeometry.wrap(az + Mth.clamp(dAz, -AZ_RATE, AZ_RATE)); changed = true; }
            if (Math.abs(dEl) > 0.01) { el += (float) Mth.clamp(dEl, -EL_RATE, EL_RATE); changed = true; }
            if (changed && now % 4 == 0) store.use(MOVE_FE);
        }

        int mask = capMask;
        if (pending != null) mask |= 1 << pending.slot();
        for (int i = 0; i < SLOTS; i++) if ((mask >> i & 1) != 0 && capCloseAt[i] != 0 && now >= capCloseAt[i] && (pending == null || pending.slot() != i)) { mask &= ~(1 << i); capCloseAt[i] = 0; }
        if (mask != capMask) { capMask = mask; changed = true; }

        if (pending == null) {
            if (now % 5 == 0 && cooldown == 0 && powered && owner != null && store.getEnergyStored() >= Balance.INT_SHOT_FE && firstLoaded() >= 0 && !lastIds.isEmpty()) tryPlan(sl, now);
        } else {
            FlightManager mgr = FlightManager.get(sl);
            Flight f = mgr.find(pending.flightId());
            boolean aligned = Math.abs(BatteryGeometry.wrap(pending.az() - az)) < 1.2 && Math.abs(pending.el() - el) < 1.2;
            if (f == null || now >= pending.arrive() - 10) cancel(now);
            else if (aligned && ramp >= 0.99F && now - pending.openedAt() >= CAP_TICKS && store.getEnergyStored() >= Balance.INT_SHOT_FE) fire(sl, mgr, now);
        }
        if (changed && now % 2 == 0) sync();
    }

    private void cancel(long now) {
        if (pending != null) capCloseAt[pending.slot()] = now + 10;
        pending = null;
        cooldown = Math.max(cooldown, 20);
        sync();
    }

    private void tryPlan(ServerLevel sl, long now) {
        FlightManager mgr = FlightManager.get(sl);
        int slot = firstLoaded();
        for (long id : lastIds) {
            if (Interceptors.chased(mgr, id)) continue;
            Flight f = mgr.find(id);
            if (f == null) continue;
            double a = az, e = el;
            long launchAt = now + 40;
            Interceptors.Shot s = null;
            for (int pass = 0; pass < 3; pass++) {
                s = Interceptors.plan(0L, muzzle(slot, e, a), f, launchAt, TIER, owner);
                if (s == null) break;
                Vec3 site = muzzle(slot, e, a), d = s.to().subtract(site);
                a = BatteryGeometry.azimuthFor(facing(), d.x, d.z);
                e = Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), BatteryGeometry.MIN_EL, BatteryGeometry.MAX_EL);
                double slew = Math.max(Math.abs(BatteryGeometry.wrap(a - az)) / AZ_RATE, Math.abs(e - el) / EL_RATE) + (1.0 - ramp) * ARM_TICKS + CAP_TICKS + 8;
                launchAt = now + (long) Math.ceil(slew);
            }
            if (s == null) continue;
            s = Interceptors.plan(0L, muzzle(slot, e, a), f, launchAt, TIER, owner);
            if (s == null) continue;
            pending = new Pending(f.id(), slot, s.to(), s.arrive(), a, e, now);
            HeatLog.fired(level, worldPosition, owner);
            tAz = a; tEl = e;
            capMask |= 1 << slot; capCloseAt[slot] = 0;
            sync();
            return;
        }
    }

    private void fire(ServerLevel sl, FlightManager mgr, long now) {
        Pending p = pending;
        Vec3 site = muzzle(p.slot(), el, az);
        int duration = (int) (p.arrive() - now);
        if (duration < 12 || site.distanceTo(p.to()) / duration > Balance.INT_SPEED[TIER - 1] * 1.08) { cancel(now); return; }
        boolean refined = magazine.getStackInSlot(p.slot()).getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0;
        Interceptors.Shot shot = new Interceptors.Shot(mgr.nextShotId(), p.flightId(), site, p.to(), now, duration, TIER, refined);
        magazine.extractItem(p.slot(), 1, false);
        store.use(Balance.INT_SHOT_FE);
        cooldown = Balance.BATTERY_RELOAD_TICKS;
        lastShot = now; lastSlot = p.slot();
        capCloseAt[p.slot()] = now + 50;
        pending = null;
        mgr.addShot(shot);
        PacketDistributor.sendToPlayersNear(sl, null, site.x, site.y, site.z, 4000,
                new InterceptPayload(shot.id(), site.x, site.y, site.z, p.to().x, p.to().y, p.to().z, now, duration, 3));
        Interceptors.announce(sl, mgr, shot);
        sync();
    }

    double[] drawnAim(float pt) {
        double[] s = ShipFrame.modelAim(level, worldPosition, facing(), shownAz, shownEl, pt);
        return s != null ? s : new double[] { shownAz, shownEl };
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, InterceptorBatteryBlockEntity be) {
        be.clientTicks++;
        be.shownAz += (float) (BatteryGeometry.wrap(be.az - be.shownAz) * 0.45);
        be.shownEl += (be.el - be.shownEl) * 0.45F;
        be.shownRamp += (be.ramp - be.shownRamp) * 0.3F;
        for (int i = 0; i < SLOTS; i++) {
            float want = (be.capMask >> i & 1) != 0 ? 1.0F : 0.0F;
            be.shownCap[i] += Mth.clamp(want - be.shownCap[i], -0.08F, 0.08F);
        }
        if (be.shownRamp > 0.05F) {
            Vec3 c = Vec3.atCenterOf(pos).add(0.0, 4.0, 0.0);
            VeilBridge.glow(0x1A7C_0000_0000_0002L ^ pos.asLong(), c.x, c.y, c.z, 0.3F, 0.55F, 1.0F, 14.0F, 0.9F * be.shownRamp, level.getGameTime());
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

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return Machines.atPart(worldPosition, facing(), Machines.mount(getBlockState()), part, side, 0, 0, 5, Direction.SOUTH) ? store : null; }

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
        tAz = az; tEl = el;
    }

    private void write(CompoundTag tag) {
        tag.putInt("frequency", frequency); tag.putInt("lastSlot", lastSlot); tag.putLong("lastShot", lastShot); tag.putInt("tracked", tracked); tag.putInt("link", link);
        tag.putFloat("az", az); tag.putFloat("el", el); tag.putFloat("ramp", ramp); tag.putInt("caps", capMask);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
    }

    private void read(CompoundTag tag) {
        frequency = tag.getInt("frequency"); lastSlot = tag.contains("lastSlot") ? tag.getInt("lastSlot") : -1; lastShot = tag.contains("lastShot") ? tag.getLong("lastShot") : -1000;
        tracked = tag.getInt("tracked"); link = tag.getInt("link");
        if (frequency < 1) frequency = 1;
        az = tag.getFloat("az"); el = tag.contains("el") ? tag.getFloat("el") : (float) BatteryGeometry.STOW_EL; ramp = tag.getFloat("ramp"); capMask = tag.getInt("caps");
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
