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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class WeaponsBayBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host, AirStations.Station {

    static final float HX = 7.2F, HY = 0.6F, STORE_Y = 8.747F, STORE_Z = 18.0F;
    static final float A0 = 11.0F, A1 = 80.0F, DROP_Y = -12.71F, DROP_Z = 12.93F, DOOR_DEG = 100.0F;

    static final float XS = -1.0F;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int loaded = -1;
    private int storeFreq = 1;
    private UUID ship;
    private boolean powered;

    private long doorsAt = Long.MIN_VALUE / 2, doorsUntil = Long.MIN_VALUE / 2;

    private long fireAt = Long.MIN_VALUE / 2;
    private int firedKind = -1;

    private boolean pending;
    private UUID pendTarget;
    private BlockPos pendEmitter;
    private AirMissiles.LockSource pendLock;
    private String pendName = "";
    private int pendSpot = -1;
    private Vec3 pendGps;
    private String pendGpsLabel = "";
    private int[] pendGlide;

    public WeaponsBayBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.WEAPONS_BAY_BE.get(), pos, state, 0, 1, 0);
    }

    public int loadedShown() { return loaded; }

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
        ItemStack m = AirStores.stack(loaded, storeFreq);
        if (!m.isEmpty()) { l.add(m); loaded = -1; }
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private Direction side() { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.EAST); }

    private boolean joins(BlockPos p) {
        return level != null && level.getBlockEntity(p) instanceof WeaponsBayBlockEntity o && o.getBlockPos().equals(p)
                && o.facing() == facing() && Machines.mount(o.getBlockState()) == Machines.mount(getBlockState());
    }

    int[] group() {
        Direction s = side();
        int left = 0;
        BlockPos p = worldPosition;
        while (left < Balance.WBAY_RUN_SCAN && joins(p.relative(s.getOpposite()))) { p = p.relative(s.getOpposite()); left++; }
        int right = 0;
        p = worldPosition;
        while (right < Balance.WBAY_RUN_SCAN && joins(p.relative(s))) { p = p.relative(s); right++; }
        int run = left + right + 1, g = left / Balance.WBAY_GROUP_MAX, idx = left - g * Balance.WBAY_GROUP_MAX;
        return new int[] { idx, Math.min(Balance.WBAY_GROUP_MAX, run - g * Balance.WBAY_GROUP_MAX) };
    }

    private void openGroup(ServerLevel sl, long until) {
        int[] g = group();
        Direction s = side();
        for (int k = 0; k < g[1]; k++) {
            BlockPos p = worldPosition.relative(s, k - g[0]);
            if (sl.getBlockEntity(p) instanceof WeaponsBayBlockEntity o) o.openDoors(sl, until);
        }
    }

    private void openDoors(ServerLevel sl, long until) {
        long now = sl.getGameTime();
        boolean shut = now > doorsUntil + Balance.WBAY_DOOR_CLOSE_TICKS;
        if (shut) {
            doorsAt = now;
            playOnce(sl, WmdSounds.BAY_DOOR_OPEN.get(), Balance.SND_MECH, 1.0F);
        } else if (now >= doorsUntil) doorsAt = now - Balance.WBAY_DOOR_TICKS;
        doorsUntil = Math.max(doorsUntil, until);
        sync();
    }

    @Override
    public boolean exposed(ServerLevel sl) {
        long now = sl.getGameTime();
        return now >= doorsAt && now <= doorsUntil + Balance.WBAY_DOOR_CLOSE_TICKS;
    }

    @Override
    public boolean loseStore(ServerLevel sl) {
        if (!AirGate.on() || loaded < 0 || pending) return false;
        loaded = -1;
        AirFrag.puff(sl, Vec3.atCenterOf(worldPosition));
        sync();
        return true;
    }

    boolean accepts(int kind) { BombType b = AirStores.bomb(kind); if (b != null) return !b.heavy || b == BombType.PENETRATOR;
        return kind == AirMissiles.HEAT || kind == AirMissiles.RADAR || kind == AirMissiles.ARM || kind == AirMissiles.AGM || AirStores.isDrop(kind); }

    boolean load(int kind) { return load(kind, 1); }

    boolean load(int kind, int freq) {
        if (loaded >= 0 || pending || !accepts(kind)) return false;
        loaded = kind;
        storeFreq = Math.max(1, freq);
        sync();
        return true;
    }

    ItemStack unload() {
        ItemStack m = !pending ? AirStores.stack(loaded, storeFreq) : ItemStack.EMPTY;
        if (m.isEmpty()) return ItemStack.EMPTY;
        loaded = -1;
        sync();
        return m;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { if (ship != null) { AirStations.remove(sl, ship, worldPosition); ship = null; } pending = false; return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { AirStations.remove(sl, ship, worldPosition); ship = s; }
            AirStations.add(sl, ship, worldPosition);
        }
        boolean sig = sl.hasNeighborSignal(worldPosition);
        if (sig && !powered) {
            String why = fire(sl, null, null);
            if (why != null) playOnce(sl, WmdSounds.CC_PING.get(), Balance.SND_CLICK, 0.6F);
        }
        powered = sig;
        if (pending && now >= fireAt + Balance.WBAY_LAUNCH_DELAY) launch(sl, now);
        if (now == doorsUntil) playOnce(sl, WmdSounds.BAY_DOOR_CLOSE.get(), Balance.SND_MECH, 1.0F);
        return false;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl && ship != null) AirStations.remove(sl, ship, worldPosition);
        super.setRemoved();
    }

    @Override public int loaded() { return pending ? -1 : loaded; }
    @Override public BlockPos stationPos() { return worldPosition; }

    private double[] storeLocal(int kind) {
        String mesh = AamItem.mesh(kind);
        double r = mesh == null ? 0.16 : AamMeshes.radiusPx(mesh) / 16.0;
        double hang = mesh == null ? 0.35 : AamMeshes.hangPx(mesh) / 16.0;
        return new double[] { 0.0, (STORE_Y + DROP_Y) / 16.0 - hang + r, (STORE_Z + DROP_Z) / 16.0 };
    }

    private Vec3[] axis(int kind) {
        double[] c = storeLocal(kind);
        double[] a = worldPoint(c[0], c[1], c[2]), n = worldPoint(c[0], c[1], c[2] - 1.0);
        return new Vec3[] { new Vec3(a[0], a[1], a[2]), new Vec3(n[0] - a[0], n[1] - a[1], n[2] - a[2]) };
    }

    @Override
    public String fire(ServerLevel sl, UUID target, AirMissiles.LockSource lock) {
        if (!AirGate.on()) return "1.2 air battles are off";
        if (loaded < 0 || pending) return loaded < 0 ? "empty" : "launching";
        long now = sl.getGameTime();
        if (now - fireAt < Balance.STATION_COOLDOWN + Balance.WBAY_LAUNCH_DELAY) return "cycling";
        if (ship == null) return "not on a jet (a station needs the power core of a Sable ship)";
        if (EmpField.jammed(sl, worldPosition)) return "jammed";
        if (AirStores.isDrop(loaded)) {
            Vec3[] bx = axis(loaded);
            int[][] glide = { null };
            String chk = AirStores.bombCheck(sl, loaded, getOwner(), ship, bx[0], bx[1], glide);
            if (chk != null) return chk;
            pendGlide = glide[0];
            if (AirStores.isAshm(loaded)) { String pre = AntiShipMissiles.precheck(sl, getOwner()); if (pre != null) return pre; }
            if (!ShipPower.draw(sl, ship, Balance.STATION_LAUNCH_FE)) return "no charge in the power core";
            pending = true; pendTarget = AirStores.isAshm(loaded) ? target : null; pendEmitter = null; pendLock = null; pendName = "";
            fireAt = now;
            firedKind = loaded;
            openGroup(sl, now + Balance.WBAY_LAUNCH_DELAY + Balance.WBAY_DOOR_HOLD);
            sync();
            return null;
        }
        Vec3[] ax = axis(loaded);
        Vec3 from = ax[0], dir = ax[1];
        String name = "";
        BlockPos emitter = null;
        if (loaded == AirMissiles.RADAR && lock == null) return "a radar missile needs a radar lock";
        if (target != null && loaded != AirMissiles.ARM && AirPicture.get(sl, target) == null) {
            if (loaded == AirMissiles.RADAR) return "the radar lock is not an aircraft (radar in SEA mode)";
            target = null;
            lock = null;
        }
        pendSpot = -1;
        pendGps = null;
        if (loaded == AirMissiles.AGM) {
            if (AirStations.agmGps(ship)) {
                String[] lbl = { "" };
                pendGps = AirStations.gpsPoint(sl, getOwner(), ship, from, dir, lbl);
                pendGpsLabel = lbl[0];
                if (pendGps == null) return "no GPS target (pod spot, laser spot or command center target)";
            } else {
                pendSpot = AirStations.agmSpot(sl, getOwner(), from, dir);
                if (pendSpot < 0) return "no laser spot ahead (designate one: targeting pod, laser tripod)";
            }
            target = null;
            lock = null;
        } else if (loaded == AirMissiles.ARM) {
            emitter = AirStations.armEmitter(sl, from, dir, getOwner(), ship, target);
            if (emitter == null) return "no radar emitting in the seeker's view";
            target = null;
            lock = null;
            name = "radar";
        } else if (target == null) {
            AirPicture.Seen s = AirStations.heatPick(sl, from, dir, ship);
            if (s == null) return "no heat lock";
            target = s.a().id();
            name = s.a().name();
        } else {
            AirPicture.Aircraft ac = AirPicture.get(sl, target);
            if (ac != null) name = ac.name();
            if (loaded == AirMissiles.HEAT && ac != null && !AirHeat.rearAspect(sl, target, ac.pos(), ac.vel(), from, now))
                return "no tail aspect (a heat seeker locks only from behind)";
        }
        if (!ShipPower.draw(sl, ship, Balance.STATION_LAUNCH_FE)) return "no charge in the power core";
        pending = true;
        pendTarget = target;
        pendEmitter = emitter;
        pendLock = lock;
        pendName = name;
        fireAt = now;
        firedKind = loaded;
        openGroup(sl, now + Balance.WBAY_LAUNCH_DELAY + Balance.WBAY_DOOR_HOLD);
        sync();
        return null;
    }

    private void launch(ServerLevel sl, long now) {
        pending = false;
        if (!AirGate.on()) return;
        Vec3[] ax = axis(loaded);
        SableBridge.ShipInfo si = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 carrier = si == null ? Vec3.ZERO : ShipDefence.velocity(sl, si);
        String r = AirStores.isAshm(loaded) ? AntiShipMissiles.launch(sl, getOwner(), ship, ax[0], ax[1], pendTarget, AirStores.refined(loaded, storeFreq), () -> true)
                : AirStores.isDrop(loaded) ? AirStores.release(sl, loaded, storeFreq, ax[0], carrier, getOwner(), ship, worldPosition, ax[1], pendGlide)
                : AirMissiles.refinedShot(AirStores.refined(loaded, storeFreq), () -> loaded == AirMissiles.AGM
                        ? (pendGps != null ? AirMissiles.fireAgmGps(sl, getOwner(), ax[0], ax[1], carrier, ship, pendGps, pendGpsLabel, null)
                                : AirMissiles.fireAgm(sl, getOwner(), ax[0], ax[1], carrier, ship, pendSpot, null))
                        : AirMissiles.fire(sl, loaded, getOwner(), ax[0], ax[1], carrier, ship, pendTarget, null, pendEmitter, pendLock, null, pendName));
        pendLock = null;
        if (r != null) {
            Wmd.LOGGER.info("Weapons bay at {}: launch refused after the doors opened: {}", worldPosition, r);
            playOnce(sl, WmdSounds.CC_PING.get(), Balance.SND_CLICK, 0.6F);
            sync();
            return;
        }
        loaded = -1;
        sync();
    }

    private static float ease(double k) { k = Math.max(0.0, Math.min(1.0, k)); return (float) (k * k * (3.0 - 2.0 * k)); }

    float doorOpen(double tt) {
        double a = tt - doorsAt, c = tt - doorsUntil;
        if (a < 0) return 0.0F;
        float open;
        if (a < Balance.WBAY_DOOR_TICKS) { double k = a / Balance.WBAY_DOOR_TICKS; open = (float) (1.0 - (1.0 - k) * (1.0 - k)) * 1.06F; }
        else if (a < Balance.WBAY_DOOR_TICKS + 2) open = 1.06F - 0.06F * (float) ((a - Balance.WBAY_DOOR_TICKS) / 2.0);
        else open = 1.0F;
        if (c > 0) open = Math.min(open, 1.0F - ease(c / Balance.WBAY_DOOR_CLOSE_TICKS));
        return Math.max(0.0F, open);
    }

    float trapeze(double tt) {
        double s = tt - fireAt;
        if (s < Balance.WBAY_TRAP_AT) return 0.0F;
        if (s < Balance.WBAY_TRAP_AT + Balance.WBAY_TRAP_TICKS) return ease((s - Balance.WBAY_TRAP_AT) / Balance.WBAY_TRAP_TICKS);
        double back = s - Balance.WBAY_LAUNCH_DELAY - 1;
        if (back < 0) return 1.0F;
        return 1.0F - ease(back / Balance.WBAY_TRAP_BACK_TICKS);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        int[] g = group();
        int i = g[0], n = g[1];
        float open = doorOpen(tt);
        float deg = DOOR_DEG * open;

        for (int h = 0; h < 2; h++) {
            float centre = 16.0F * i + (h == 0 ? 4.0F : 12.0F);
            boolean leftDoor = centre < 8.0F * n;
            float bx = h == 0 ? -HX : HX;
            float px = leftDoor ? -HX - 16.0F * i : 16.0F * (n - 1 - i) + HX;
            double th = Math.toRadians(leftDoor ? -deg : deg);
            float vx = px - bx;
            final float dx = (float) (vx * (1.0 - Math.cos(th))), dy = (float) (-vx * Math.sin(th)), rz = (float) th;
            String d = h == 0 ? "l" : "r";
            model.getBone("door_" + d).ifPresent(b -> { b.setRotZ(rz); b.setPosX(XS * dx); b.setPosY(dy); });

        }
        float k = trapeze(tt);
        final float arm = (float) Math.toRadians(-(A1 - A0) * k) * CcSpecS52.CBR_TILT_SIGN;
        model.getBone("trap_f").ifPresent(b -> b.setRotX(arm));
        model.getBone("trap_r").ifPresent(b -> b.setRotX(arm));

        double a = Math.toRadians(A0 + (A1 - A0) * k), a0 = Math.toRadians(A0);
        final float sy = (float) (-16.0 * (Math.sin(a) - Math.sin(a0))), sz = (float) (16.0 * (Math.cos(a0) - Math.cos(a)));
        model.getBone("store").ifPresent(b -> { b.setPosY(sy); b.setPosZ(sz); });
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean glow = !dark && open > 0.05F, lamp = !dark && loaded >= 0;
        model.getBone("glow").ifPresent(b -> b.setHidden(!glow));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }

    @Override
    protected String detail() {
        int[] g = group();
        return AirStores.name(loaded) + ", bay " + (g[0] + 1) + "/" + g[1] + (ship == null ? ", not on a ship" : ", station on " + ship);
    }

    @Override
    public Component status() {
        Component what = loaded < 0 ? Component.translatable("message.wmd.station.empty")
                : Component.translatable("message.wmd.station.loaded", AirStores.label(loaded));
        int[] g = group();
        return Component.translatable("block.wmd.weapons_bay").append(": ").append(what).append(" ").append(Component.translatable("message.wmd.weapons_bay.group", g[0] + 1, g[1]));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("stLoaded", loaded);
        tag.putInt("stFreq", storeFreq);
        tag.putBoolean("stPowered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        loaded = tag.contains("stLoaded") ? tag.getInt("stLoaded") : -1;
        storeFreq = Math.max(1, tag.getInt("stFreq"));
        if (tag.contains("bayDoorsAt")) {
            doorsAt = tag.getLong("bayDoorsAt");
            doorsUntil = tag.getLong("bayDoorsUntil");
            fireAt = tag.getLong("stFireAt");
            firedKind = tag.getInt("stFired");
        }
        if (!client) powered = tag.getBoolean("stPowered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("stLoaded", loaded);
        tag.putInt("stFreq", storeFreq);
        tag.putLong("bayDoorsAt", doorsAt);
        tag.putLong("bayDoorsUntil", doorsUntil);
        tag.putLong("stFireAt", fireAt);
        tag.putInt("stFired", firedKind);
        return tag;
    }

    static Level levelOf(WeaponsBayBlockEntity be) { return be.level; }
}
