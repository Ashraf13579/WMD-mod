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

public class AirStationBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host, AirStations.Station {

    private final boolean rail;
    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int loaded = -1;

    private int count = 1, firedSlot;
    private int storeFreq = 1;
    private UUID ship;
    private boolean powered;
    private long fireAt = Long.MIN_VALUE / 2;
    private int firedKind = -1;

    public AirStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean rail) {
        super(type, pos, state, 0, 1, 0);
        this.rail = rail;
    }

    public boolean rail() { return rail; }

    public int loadedShown() { return loaded; }
    public long fireAt() { return fireAt; }
    public int firedKind() { return firedKind; }

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

    boolean accepts(int kind) {
        if (!rail && heavyBeside()) return false;
        BombType b = AirStores.bomb(kind);
        if (b != null) return !rail && (!b.heavy || AirStores.pylonHeavy(b) && heavyRoom(b));
        return kind == AirMissiles.HEAT || kind == AirMissiles.RADAR || kind == AirMissiles.AGM || (!rail && kind == AirMissiles.ARM) || (!rail && AirStores.isDrop(kind));
    }

    private java.util.List<BlockPos> heavyCells(BombType b) {
        BlockState st = getBlockState();
        Direction f = st.hasProperty(MachineBlock.FACING) ? st.getValue(MachineBlock.FACING) : Direction.NORTH;
        Direction side = f.getClockWise();
        int along = Math.max(1, (int) Math.ceil((b.length / 2.0 - 8.0) / 16.0));
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int a = -1; a <= 1; a++) for (int l = -along; l <= along; l++)
            if (a != 0 || l != 0) out.add(worldPosition.relative(side, a).relative(f, l));
        return out;
    }

    boolean heavyRoom(BombType b) {
        if (level == null || rail) return false;
        BlockState st = getBlockState();
        if (st.hasProperty(MachineBlock.MOUNT) && st.getValue(MachineBlock.MOUNT) != Direction.UP) return false;
        for (BlockPos p : heavyCells(b))
            if (level.getBlockEntity(p) instanceof AirStationBlockEntity o && o.loaded >= 0) return false;
        return true;
    }

    private boolean heavyBeside() {
        if (level == null) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -3; dz <= 3; dz++) for (int sw = 0; sw < 2; sw++) {
            BlockPos p = sw == 0 ? worldPosition.offset(dx, 0, dz) : worldPosition.offset(dz, 0, dx);
            if (p.equals(worldPosition)) continue;
            if (level.getBlockEntity(p) instanceof AirStationBlockEntity o && o != this) {
                BombType ob = AirStores.bomb(o.loaded);
                if (ob != null && ob.heavy && o.heavyCells(ob).contains(worldPosition)) return true;
            }
        }
        return false;
    }

    boolean load(int kind) { return load(kind, 1); }

    boolean load(int kind, int freq) {
        if (rail && loaded >= 0 && loaded == kind && count < Balance.RAIL_TWIN && !AirStores.isDrop(kind) && AirStores.bomb(kind) == null) {
            count++;
            sync();
            return true;
        }
        if (loaded >= 0 || !accepts(kind)) return false;
        loaded = kind;
        count = 1;
        storeFreq = Math.max(1, freq);
        sync();
        return true;
    }

    public int countShown() { return loaded < 0 ? 0 : count; }
    public int firedSlot() { return firedSlot; }

    ItemStack unload() {
        ItemStack m = AirStores.stack(loaded, storeFreq);
        if (m.isEmpty()) return ItemStack.EMPTY;
        if (count > 1) { count--; sync(); return m; }
        loaded = -1;
        count = 1;
        sync();
        return m;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { if (ship != null) { AirStations.remove(sl, ship, worldPosition); ship = null; } return false; }
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
        return false;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl && ship != null) AirStations.remove(sl, ship, worldPosition);
        super.setRemoved();
    }

    private double[] storeLocal() {
        String mesh = AamItem.mesh(loaded < 0 ? AirMissiles.HEAT : loaded);
        double r = mesh == null ? 0.11 : AamMeshes.radiusPx(mesh) / 16.0;
        double hang = mesh == null ? 0.25 : AamMeshes.hangPx(mesh) / 16.0;
        double x = rail && count >= 2 ? Balance.RAIL_TWIN_DX_PX / 16.0 : 0.0;
        return rail ? new double[] { x, 11.0 / 16.0 - hang + r, 0.5 } : new double[] { 0.0, 7.4 / 16.0 - hang + r, 0.0 };
    }

    @Override public int loaded() { return loaded; }
    @Override public BlockPos stationPos() { return worldPosition; }

    @Override
    public String fire(ServerLevel sl, UUID target, AirMissiles.LockSource lock) {
        if (!AirGate.on()) return "1.2 air battles are off";
        if (loaded < 0) return "empty";
        long now = sl.getGameTime();
        if (now - fireAt < Balance.STATION_COOLDOWN) return "cycling";
        if (ship == null) return "not on a jet (a station needs the power core of a Sable ship)";
        if (EmpField.jammed(sl, worldPosition)) return "jammed";
        if (AirStores.isDrop(loaded)) {
            BombType bt = AirStores.bomb(loaded);
            double[] p = bt != null ? worldPoint(0.0, (7.4 - bt.radius) / 16.0 - 0.1, 0.0) : worldPoint(0.0, 7.4 / 16.0 - 0.7, 0.0);
            SableBridge.ShipInfo si0 = SableBridge.shipInfo(sl, ship);
            double[] f0 = worldPoint(0.0, 0.0, 0.0), f1 = worldPoint(0.0, 0.0, -1.0);
            Vec3 at = new Vec3(p[0], p[1], p[2]), fwd = new Vec3(f1[0] - f0[0], f1[1] - f0[1], f1[2] - f0[2]);
            int[][] glide = { null };
            String chk = AirStores.bombCheck(sl, loaded, getOwner(), ship, at, fwd, glide);
            if (chk != null) return chk;
            String why = AirStores.isAshm(loaded)
                    ? AntiShipMissiles.launch(sl, getOwner(), ship, at, fwd, target, AirStores.refined(loaded, storeFreq), () -> ShipPower.draw(sl, ship, Balance.STATION_LAUNCH_FE))
                    : AirStores.release(sl, loaded, storeFreq, at, si0 == null ? Vec3.ZERO : ShipDefence.velocity(sl, si0), getOwner(), ship, worldPosition, fwd, glide[0]);
            if (why != null) return why;
            firedKind = loaded;
            loaded = -1;
            fireAt = now;
            playOnce(sl, WmdSounds.PYLON_EJECT.get(), Balance.SND_MECH, 1.0F);
            sync();
            return null;
        }

        if (!rail) {
            double[] u0 = worldPoint(0.0, 0.0, 0.0), u1 = worldPoint(0.0, 1.0, 0.0);
            double g = AirCrash.loadFactor(ship, new Vec3(u1[0] - u0[0], u1[1] - u0[1], u1[2] - u0[2]));
            if (g > Balance.STATION_PYLON_MAX_G) return String.format(java.util.Locale.ROOT, "pylon: too much g (%.1f g; a launch rail fires in any turn)", g);
            if (g < Balance.STATION_PYLON_MIN_G) return "pylon: negative g / inverted (the store would not fall clear; a launch rail fires in any attitude)";
        }
        double[] c = storeLocal();
        double[] a = worldPoint(c[0], c[1], c[2]), n = worldPoint(c[0], c[1], c[2] - 1.0);
        Vec3 from = new Vec3(a[0], a[1], a[2]), dir = new Vec3(n[0] - a[0], n[1] - a[1], n[2] - a[2]);
        if (loaded == AirMissiles.RADAR && lock == null) return "a radar missile needs a radar lock";
        if (target != null && loaded != AirMissiles.ARM && AirPicture.get(sl, target) == null) {
            if (loaded == AirMissiles.RADAR) return "the radar lock is not an aircraft (radar in SEA mode)";
            target = null;
            lock = null;
        }
        if (loaded == AirMissiles.AGM) {
            boolean gps = AirStations.agmGps(ship);
            String[] lbl = { "" };
            int spot = gps ? -1 : AirStations.agmSpot(sl, getOwner(), from, dir);
            Vec3 gp = gps ? AirStations.gpsPoint(sl, getOwner(), ship, from, dir, lbl) : null;
            if (gps ? gp == null : spot < 0) return gps ? "no GPS target (pod spot, laser spot or command center target)" : "no laser spot ahead (designate one: targeting pod, laser tripod)";
            if (!ShipPower.draw(sl, ship, Balance.STATION_LAUNCH_FE)) return "no charge in the power core";
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, ship);
            Vec3 cv = si == null ? Vec3.ZERO : ShipDefence.velocity(sl, si);
            String r = AirMissiles.refinedShot(AirStores.refined(loaded, storeFreq), () -> gps ? AirMissiles.fireAgmGps(sl, getOwner(), from, dir, cv, ship, gp, lbl[0], null) : AirMissiles.fireAgm(sl, getOwner(), from, dir, cv, ship, spot, null));
            if (r != null) return r;
            firedKind = loaded;

            spendOne();
            fireAt = now;
            if (!rail) playOnce(sl, WmdSounds.PYLON_EJECT.get(), Balance.SND_MECH, 1.0F);
            sync();
            return null;
        }
        String name = "";
        BlockPos emitter = null;
        if (loaded == AirMissiles.ARM) {
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
        SableBridge.ShipInfo si = SableBridge.shipInfo(sl, ship);
        Vec3 carrier = si == null ? Vec3.ZERO : ShipDefence.velocity(sl, si);
        final UUID tgt = target; final BlockPos em = emitter; final AirMissiles.LockSource lk = lock; final String nm = name;

        final int dropT = rail ? 0 : Balance.STATION_DROP_TICKS;
        final Vec3 kick = rail ? dir.normalize().scale(Balance.RAIL_KICK) : Vec3.ZERO;
        String r = AirMissiles.launchStyle(dropT, () -> AirMissiles.refinedShot(AirStores.refined(loaded, storeFreq), () -> AirMissiles.fire(sl, loaded, getOwner(), from, dir, carrier.add(kick), ship, tgt, null, em, lk, null, nm)));
        if (r != null) return r;
        firedKind = loaded;
        spendOne();
        fireAt = now;
        if (!rail) playOnce(sl, WmdSounds.PYLON_EJECT.get(), Balance.SND_MECH, 1.0F);
        sync();
        return null;
    }

    private void spendOne() {
        firedSlot = rail && count >= 2 ? 1 : 0;
        if (rail && count > 1) count--;
        else { loaded = -1; count = 1; }
    }

    @Override
    public boolean loseStore(ServerLevel sl) {
        if (!AirGate.on() || loaded < 0) return false;
        loaded = -1;
        AirFrag.puff(sl, Vec3.atCenterOf(worldPosition));
        sync();
        return true;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double since = tt - fireAt;
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean lamp = !dark && loaded >= 0;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
        if (!rail) {

            float open = (float) (since < 0 ? 0 : since < 2 ? since / 2 : since < 20 ? 1 : since < 26 ? 1 - (since - 20) / 6 : 0);
            final float sw = (float) Math.toRadians(35.0) * open;
            model.getBone("sway_l").ifPresent(b -> b.setRotZ(-sw));
            model.getBone("sway_r").ifPresent(b -> b.setRotZ(sw));
            float out = (float) (since < 0 ? 0 : since < 2 ? since / 2 : since < 12 ? 1 - (since - 2) / 10 : 0);
            final float ram = -1.8F * out;
            model.getBone("ram_f").ifPresent(b -> b.setPosY(ram));
            model.getBone("ram_r").ifPresent(b -> b.setPosY(ram));
        } else {
            final float det = (float) Math.toRadians(since >= 0 && since < 12 ? 30.0 : 0.0) * CcSpecS52.CBR_TILT_SIGN;
            model.getBone("detent").ifPresent(b -> b.setRotX(det));
            float back = (float) (since < 0 ? 0 : since < 2 ? since / 2 : since < 20 ? 1 : since < 40 ? 1 - (since - 20) / 20 : 0);
            final float um = (float) Math.toRadians(-60.0) * back * CcSpecS52.CBR_TILT_SIGN;
            model.getBone("umbilical").ifPresent(b -> b.setRotX(um));
        }
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override
    protected String detail() { return AirStores.name(loaded) + (ship == null ? ", not on a ship" : ", station on " + ship); }

    @Override
    public Component status() {
        Component what = loaded < 0 ? Component.translatable("message.wmd.station.empty")
                : Component.translatable("message.wmd.station.loaded", AirStores.label(loaded));
        return Component.translatable(rail ? "block.wmd.launch_rail" : "block.wmd.pylon").append(": ").append(what);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("stLoaded", loaded);
        tag.putInt("stFreq", storeFreq);
        tag.putInt("stCount", count);
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
        count = Math.max(1, Math.min(Balance.RAIL_TWIN, tag.contains("stCount") ? tag.getInt("stCount") : 1));
        if (tag.contains("stSlot")) firedSlot = tag.getInt("stSlot");
        if (tag.contains("stFireAt")) { fireAt = tag.getLong("stFireAt"); firedKind = tag.getInt("stFired"); }
        if (!client) powered = tag.getBoolean("stPowered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("stLoaded", loaded);
        tag.putInt("stCount", count);
        tag.putInt("stSlot", firedSlot);
        tag.putLong("stFireAt", fireAt);
        tag.putInt("stFired", firedKind);
        return tag;
    }

    static Level levelOf(AirStationBlockEntity be) { return be.level; }
}
