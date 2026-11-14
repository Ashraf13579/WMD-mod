package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class RocketPodBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host, AirStations.Station {

    static final float PETAL_SIGN = 1.0F, RELOAD_SIGN = 1.0F;
    static final float PETAL_OPEN_DEG = 60.0F, TRAY_DROP = 9.6F, TELE_DROP = 4.8F, RELOAD_SLIDE = 28.0F;

    static final int[] ORDER = { 0, 3, 1, 4, 2, 5, 6 };

    static final double[][] TUBES_POD = new double[7][], TUBES_FLUSH = { { -5.1, 2.4 }, { -1.7, 2.4 }, { 1.7, 2.4 }, { 5.1, 2.4 }, { -3.4, 5.4 }, { 0.0, 5.4 }, { 3.4, 5.4 } };
    static {
        for (int k = 0; k < 6; k++) { double a = Math.toRadians(30.0 + 60.0 * k); TUBES_POD[k] = new double[] { 3.9 * Math.sin(a), 6.6 + 3.9 * Math.cos(a) }; }
        TUBES_POD[6] = new double[] { 0.0, 6.6 };
    }

    static final double MOUTH_AHEAD = 0.6, AXIS_DOWN_POD = 1.4 / 16.0, AXIS_DOWN_FLUSH = 13.7 / 16.0;
    static final int ALL = (1 << Balance.RPOD_TUBES) - 1;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private boolean flush, open;
    private int mask, pending, lastTube = -1, loadMask, loadClicks, sigTicks, rippleRun;

    private int prepaid;
    private long openChanged = -1000L, lastActive = -1000L, nextShot, lastShotAt = -1000L, loadAt = -1000L, nextClick, holdUntil, nextHold;
    private UUID ship;

    public RocketPodBlockEntity(BlockPos pos, BlockState state) {
        super(RocketPods.ROCKET_POD_BE.get(), pos, state, 0, 1, 0);
    }

    public boolean flush() { return flush; }

    public int rockets() { return Integer.bitCount(mask); }

    int available() { return Math.max(0, rockets() - pending); }
    UUID shipId() { return ship; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        if (rockets() > 0 && AirGate.AVAILABLE) l.add(new ItemStack(RocketPods.AIR_ROCKET.get(), rockets()));
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    boolean toggleLook() {
        if (open || pending > 0 || loadClicks > 0) return false;
        flush = !flush;
        sync();
        return true;
    }

    private boolean airborne(ServerLevel sl) {
        if (!AirGate.on()) return false;
        UUID s = SableBridge.shipIdAt(sl, worldPosition);
        return s != null && AirPicture.isAircraft(sl, s);
    }

    String reload(Player player, ItemStack held) {
        int n = loadRockets(player.getAbilities().instabuild ? Balance.RPOD_TUBES : held.getCount());
        if (n > 0 && !player.getAbilities().instabuild) held.shrink(n);
        return n > 0 ? "message.wmd.rocket_pod.loaded" : lastRefusal;
    }

    private String lastRefusal = "message.wmd.rocket_pod.full";

    boolean loadOne() { return loadRockets(1) == 1; }

    private int loadRockets(int max) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) { lastRefusal = "message.wmd.rocket_pod.off"; return 0; }
        if (airborne(sl)) { lastRefusal = "message.wmd.rocket_pod.land_first"; return 0; }
        if (pending > 0) { lastRefusal = "message.wmd.rocket_pod.busy"; return 0; }
        if (mask == ALL) { lastRefusal = "message.wmd.rocket_pod.full"; return 0; }
        long now = sl.getGameTime();
        if (now >= loadAt + (long) Integer.bitCount(loadMask) * Balance.RPOD_LOAD_TICKS) { loadMask = 0; loadAt = now; }
        int added = 0;
        for (int k = 0; k < Balance.RPOD_TUBES && added < max; k++)
            if ((mask & (1 << k)) == 0) { mask |= 1 << k; loadMask |= 1 << k; added++; }
        loadClicks += added;
        if (nextClick < now) nextClick = now + Balance.RPOD_LOAD_TICKS;
        lastActive = now + (long) loadClicks * Balance.RPOD_LOAD_TICKS;
        if (flush) setOpen(sl, true);
        sync();
        return added;
    }

    String queue(ServerLevel sl, int n, int delay) {
        if (!AirGate.on()) return "the 1.2 content is off";
        if (ship == null) return "the rocket pod is not on a craft";
        int k = Math.min(n, available());
        if (k <= 0) return "the rocket pod is empty";
        if (loadClicks > 0) return "the rocket pod is being loaded";
        if (prepaid == 0 && !ShipPower.draw(sl, ship, Balance.RPOD_FE)) return "no power for the rocket ignition (power core)";
        prepaid = 1;
        long now = sl.getGameTime();
        if (pending == 0) { rippleRun = k; nextShot = Math.max(nextShot, now + Math.max(0, delay)); }
        else rippleRun += k;
        pending += k;
        lastActive = now;
        return null;
    }

    String ripple(ServerLevel sl, int slot) { return queue(sl, available(), slot); }

    void hold(ServerLevel sl, int holdTicks) {
        long now = sl.getGameTime();
        holdUntil = Math.max(holdUntil, now + Math.max(1, Math.min(holdTicks, Balance.GPOD_HOLD_MAX)));
    }

    @Override public int loaded() { return available() > 0 ? AirStores.ROCKET : -1; }
    @Override public BlockPos stationPos() { return worldPosition; }
    @Override
    public String fire(ServerLevel sl, UUID target, AirMissiles.LockSource lock) { return queue(sl, 1, 0); }

    Vec3[] launchRay(ServerLevel sl) {
        Direction f = facing(), m = Machines.mount(getBlockState());
        Vec3 nrm = new Vec3(f.getStepX(), f.getStepY(), f.getStepZ());
        Vec3 down = new Vec3(-m.getStepX(), -m.getStepY(), -m.getStepZ());
        Vec3 plot = Vec3.atCenterOf(worldPosition).add(nrm.scale(MOUTH_AHEAD)).add(down.scale(flush ? AXIS_DOWN_FLUSH : AXIS_DOWN_POD));
        Vec3 a = SableBridge.toWorld(sl, plot), b = SableBridge.toWorld(sl, plot.add(nrm));
        if (a == null || b == null) return null;
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return null;
        return new Vec3[] { a, d.normalize() };
    }

    Vec3 carrierVel(ServerLevel sl) {
        SableBridge.ShipInfo own = ship == null ? null : SableBridge.shipInfo(sl, ship);
        return own == null ? Vec3.ZERO : ShipDefence.velocity(sl, own);
    }

    Vec3[] airPipper(ServerLevel sl, Vec3 tgt, Vec3 tgtVel) {
        if (ship == null || tgt == null || tgtVel == null) return null;
        Vec3[] ray = launchRay(sl);
        return ray == null ? null : AirRockets.airPoint(ray[0], ray[1], carrierVel(sl), tgt, tgtVel);
    }

    Vec3 pipper(ServerLevel sl) {
        if (ship == null) return null;
        Vec3[] ray = launchRay(sl);
        return ray == null ? null : AirRockets.impactPoint(sl, ray[0], ray[1], carrierVel(sl), ship);
    }

    private void cancelRun() {
        if (pending > 0) AirRockets.cut(getOwner());
        pending = 0;
    }

    private void launch(ServerLevel sl, long now) {
        int tube = -1;
        for (int k : ORDER) if ((mask & (1 << k)) != 0) { tube = k; break; }
        if (tube < 0 || ship == null) { cancelRun(); return; }
        Vec3[] ray = launchRay(sl);
        if (ray == null) { cancelRun(); return; }

        UUID s = SableBridge.shipIdAt(sl, worldPosition);
        if (s == null || !s.equals(ship) || SableBridge.shipInfo(sl, ship) == null) { cancelRun(); return; }
        boolean lead = rippleRun >= 3 ? pending == rippleRun : now - lastShotAt > Balance.RPOD_HOLD_TICKS * 2L;
        if (prepaid == 0 && !ShipPower.draw(sl, ship, Balance.RPOD_FE)) { cancelRun(); return; }
        prepaid = 1;
        String why = AirRockets.fire(sl, getOwner(), ray[0], ray[1], carrierVel(sl), ship, lead, pending == 1);
        if (why != null) { cancelRun(); return; }
        prepaid = 0;
        mask &= ~(1 << tube);
        pending--;
        lastTube = tube;
        lastShotAt = now;
        lastActive = now;
        HeatLog.fired(sl, worldPosition, getOwner());
        if (rippleRun >= 3) { if (pending == rippleRun - 1) playOnce(sl, WmdSounds.RP_RIPPLE.get(), Balance.SND_RELEASE, 1.0F); }
        else playOnce(sl, WmdSounds.RP_FIRE.get(), Balance.SND_RELEASE, 0.95F + sl.random.nextFloat() * 0.1F);
        sync();
    }

    private void setOpen(ServerLevel sl, boolean v) {
        if (v == open) return;
        open = v;
        openChanged = sl.getGameTime();
        SoundEvent e = flush ? (v ? WmdSounds.RP_TRAY_DOWN.get() : WmdSounds.RP_TRAY_UP.get()) : (v ? WmdSounds.RP_PETALS_OPEN.get() : WmdSounds.RP_PETALS_CLOSE.get());
        playOnce(sl, e, Balance.SND_MECH, 1.0F);
        sync();
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); cancelRun(); return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; cancelRun(); }
            if (ship != null) AirStations.add(sl, ship, worldPosition);
        }

        if (sl.hasNeighborSignal(worldPosition)) {
            sigTicks++;
            if (sigTicks == 1 || (sigTicks > Balance.RPOD_OPEN_TICKS && sigTicks % Balance.RPOD_RIPPLE_TICKS == 0 && pending == 0)) queue(sl, 1, 0);
        } else sigTicks = 0;

        if (now < holdUntil && now >= nextHold && pending == 0) { queue(sl, 1, 0); nextHold = now + Balance.RPOD_HOLD_TICKS; }

        if (loadClicks > 0 && now >= nextClick) {
            playOnce(sl, WmdSounds.RP_LOAD.get(), Balance.SND_CLICK, 0.95F + sl.random.nextFloat() * 0.1F);
            loadClicks--;
            nextClick = now + Balance.RPOD_LOAD_TICKS;
        }
        if (pending > 0) {
            setOpen(sl, true);
            if (now - openChanged >= Balance.RPOD_OPEN_TICKS && now >= nextShot) {
                launch(sl, now);
                nextShot = now + Balance.RPOD_RIPPLE_TICKS;
            }
        } else if (open && loadClicks == 0 && now >= lastActive + Balance.RPOD_CLOSE_TICKS) setOpen(sl, false);
        return false;
    }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        AirStations.remove(sl, ship, worldPosition);
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) leave(sl);
        super.setRemoved();
    }

    private float openFrac(double tt) {
        double k = Math.max(0.0, Math.min(1.0, (tt - openChanged) / Balance.RPOD_OPEN_TICKS));
        k = k * k * (3.0 - 2.0 * k);
        return (float) (open ? k : 1.0 - k);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float o = openFrac(tt);
        if (flush) {
            model.getBone("tray").ifPresent(b -> b.setPosY(-TRAY_DROP * o));
            model.getBone("tele").ifPresent(b -> b.setPosY(-TELE_DROP * o));
        } else {
            final float r = (float) Math.toRadians(PETAL_SIGN * PETAL_OPEN_DEG * o);
            for (int k = 0; k < 6; k++) model.getBone("pet" + k).ifPresent(b -> b.setRotX(r));
        }
        int idx = 0;
        for (int k = 0; k < Balance.RPOD_TUBES; k++) {
            final boolean in = (mask & (1 << k)) != 0;
            float slide = 0.0F;
            boolean hide = !in;
            if (in && (loadMask & (1 << k)) != 0) {
                double start = loadAt + (double) idx * Balance.RPOD_LOAD_TICKS, f = (tt - start) / Balance.RPOD_LOAD_TICKS;
                idx++;
                if (f < 0.0) hide = true;
                else if (f < 1.0) slide = (float) (RELOAD_SIGN * RELOAD_SLIDE * (1.0 - f));
            }
            final boolean h = hide;
            final float sl = slide;
            model.getBone("rocket" + k).ifPresent(b -> { b.setHidden(h); b.setPosZ(sl); });
        }
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean fl = lastTube >= 0 && tt - lastShotAt < 2.0 && !dark;
        final double[][] tubes = flush ? TUBES_FLUSH : TUBES_POD;
        final int t = Math.max(0, lastTube);
        model.getBone("flash").ifPresent(b -> {
            b.setHidden(!fl);
            b.setPosX((float) -(tubes[t][0] - tubes[0][0]));
            b.setPosY((float) (tubes[t][1] - tubes[0][1]));
        });
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RP_FLIGHT_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override
    protected String detail() { return rockets() + " rockets" + (pending > 0 ? ", " + pending + " queued" : "") + (ship == null ? ", not on a ship" : ", on " + ship); }

    @Override
    public Component status() {
        String k = pending > 0 ? "message.wmd.rocket_pod.firing" : rockets() == 0 ? "message.wmd.rocket_pod.empty" : ship == null ? "message.wmd.rocket_pod.no_ship" : "message.wmd.rocket_pod.ready";
        return Component.translatable("block.wmd.rocket_pod").append(": ").append(Component.translatable(k, rockets(), Balance.RPOD_TUBES));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putBoolean("rpFlush", flush);
        tag.putInt("rpMask", mask);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        flush = tag.getBoolean("rpFlush");
        mask = tag.getInt("rpMask") & ALL;
        if (tag.contains("rpOpen")) {
            open = tag.getBoolean("rpOpen");
            openChanged = tag.getLong("rpOpenAt");
            lastTube = tag.getInt("rpTube");
            lastShotAt = tag.getLong("rpShotAt");
            loadMask = tag.getInt("rpLoadMask");
            loadAt = tag.getLong("rpLoadAt");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("rpFlush", flush);
        tag.putInt("rpMask", mask);
        tag.putBoolean("rpOpen", open);
        tag.putLong("rpOpenAt", openChanged);
        tag.putInt("rpTube", lastTube);
        tag.putLong("rpShotAt", lastShotAt);
        tag.putInt("rpLoadMask", loadMask);
        tag.putLong("rpLoadAt", loadAt);
        return tag;
    }
}
