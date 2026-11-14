package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class HeavyGunBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host, GunnerGun {

    static final float YAW_SIGN = 1.0F, ELEV_SIGN = 1.0F, DOOR_SIGN = 1.0F, BARREL_SIGN = 1.0F, WEDGE_SIGN = 1.0F, TRAY_SIGN = 1.0F, RAM_SIGN = 1.0F;

    static final double PX = 8.0, PY = 10.0, PZ = -2.0, MUZZLE = 19.0, RUN = 14.0, RECOIL = 10.0, DOOR = 6.4, WEDGE = 6.0, RAM = 12.0;
    static final double LENS_Y = 3.0, LENS_Z = -5.5;
    static final int SHOT_KIND = 10;

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> GUNS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private final int[] rack = new int[Balance.HGUN_RACK];
    private int rackN, loaded = -1, seq;
    private boolean breechOpen;
    private long cycleAt = -1L;
    private UUID ship;

    private float deploy, yaw, elev;
    private int claimKind;
    private long claimBy, lastClaim = Long.MIN_VALUE / 2, triggerUntil, lastShot = Long.MIN_VALUE / 2, nextShotAt, firingUntil, lastSyncAt, nextLineCheck;
    private Vec3 aimPoint;
    private boolean outOfField, lineBlocked;
    private float syncedYaw = 999.0F, syncedElev, syncedDeploy = -1.0F;
    private int syncedFlags = -1;

    private float yawT, elevT, deployT, yawShown, elevShown, deployShown, wedgeShown, kick;
    private long flashUntil;
    private double lastT = Double.NaN;

    public HeavyGunBlockEntity(BlockPos pos, BlockState state) {
        super(GunnerBlocks.HEAVY_GUN_BE.get(), pos, state, 0, 1, 0);
    }

    @Override public BlockPos gunPos() { return worldPosition; }
    @Override public boolean deployed() { return deploy >= 1.0F; }
    @Override public float deploy01() { return deploy; }
    @Override public boolean blockedNow() { return deploy >= 1.0F && (outOfField || lineBlocked); }
    @Override public boolean firingNow() { return level != null && level.getGameTime() < firingUntil; }
    @Override public int rounds() { return rackN + (loaded >= 0 ? 1 : 0); }
    @Override public int roundsMax() { return Balance.HGUN_RACK + 1; }
    @Override public boolean arcReady() { return false; }
    @Override public BitSet arcMap() { return new BitSet(); }
    @Override public float yawDeg() { return yaw; }
    @Override public float elevDeg() { return elev; }
    @Override public long lastHitAt() { return Long.MIN_VALUE / 2; }
    @Override public boolean heavy() { return true; }
    int rackCount() { return rackN; }
    int loadedType() { return loaded; }

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
        for (int i = 0; i < rackN; i++) l.add(new ItemStack(Artillery.shellItem(Artillery.HOWITZER, rack[i])));
        if (loaded >= 0) l.add(new ItemStack(Artillery.shellItem(Artillery.HOWITZER, loaded)));
        rackN = 0; loaded = -1;
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    static boolean fits(int type) { return type == Artillery.GUNSHIP_105 || (Artillery.canFire(Artillery.HOWITZER, type) && type != Artillery.CANISTER); }

    static boolean accepts(ItemStack s) {
        return s.getItem() instanceof ArtShellItem a && a.gun() == Artillery.HOWITZER && fits(a.type());
    }

    String handLoad(Player player, ItemStack held) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return "message.wmd.heavy_gun.off";
        if (!accepts(held)) return "message.wmd.heavy_gun.wrong_shell";
        int type = ((ArtShellItem) held.getItem()).type();
        String k;
        if (loaded < 0 && deploy >= 1.0F) {
            loaded = type; breechOpen = false; cycleAt = -1L;
            nextShotAt = Math.max(sl.getGameTime() + 4, lastShot + Balance.HGUN_HAND_TICKS);
            playOnce(sl, WmdSounds.HG_BREECH.get(), Balance.SND_MECH, 1.2F);
            k = "message.wmd.heavy_gun.hand_loaded";
        } else if (rackN < rack.length) {
            rack[rackN++] = type;
            playOnce(sl, WmdSounds.GPOD_RELOAD.get(), Balance.SND_CLICK, 0.7F);
            k = "message.wmd.heavy_gun.racked";
        } else return "message.wmd.heavy_gun.full";
        if (!player.getAbilities().instabuild) held.shrink(1);
        sync();
        return k;
    }

    boolean loadRack(ItemStack one) {
        if (!(level instanceof ServerLevel) || !AirGate.on() || !accepts(one) || rackN >= rack.length) return false;
        rack[rackN++] = ((ArtShellItem) one.getItem()).type();
        sync();
        return true;
    }

    boolean rackFull() { return rackN >= rack.length; }

    static List<HeavyGunBlockEntity> gunsOf(ServerLevel level, UUID ship) {
        List<HeavyGunBlockEntity> out = new ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = GUNS.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof HeavyGunBlockEntity g && !g.isRemoved() && ship.equals(g.ship)) out.add(g);
        }
        out.sort(Comparator.comparingLong(g -> g.worldPosition.asLong()));
        return out;
    }

    @Override
    public boolean stationCommand(long now, BlockPos station, Vec3 point, boolean trigger) {
        if (!AirGate.on()) return false;
        long by = station.asLong();
        if (claimKind == GunTurretBlockEntity.STATION && claimBy != by && now - lastClaim <= Balance.GTUR_CLAIM_TICKS) return false;
        claimKind = GunTurretBlockEntity.STATION; claimBy = by; lastClaim = now;
        if (point != null) aimPoint = point;
        triggerUntil = trigger ? now + 3 : 0;
        return true;
    }

    private Vec3[] frame() {
        Vec3 o = plotPoint(0.0, 0.5, 0.0);
        return new Vec3[] { o, plotPoint(1.0, 0.5, 0.0).subtract(o), plotPoint(0.0, 1.5, 0.0).subtract(o), plotPoint(0.0, 0.5, 1.0).subtract(o) };
    }

    Vec3 pivotPlot() { return plotPoint(PX / 16.0, PY / 16.0, PZ / 16.0); }

    @Override
    public Vec3 clampAimPlot(Vec3 d, double marginDeg) {
        Vec3[] f = frame();
        double lx = d.dot(f[1]), ly = d.dot(f[2]), lz = d.dot(f[3]);
        double y = Math.toDegrees(Math.atan2(-lx, -lz)), e = Math.toDegrees(Math.atan2(ly, Math.hypot(lx, lz)));
        double ym = Balance.HGUN_YAW_MAX - marginDeg;
        double cy = Mth.clamp(y, -ym, ym), ce = Mth.clamp(e, -Balance.HGUN_ELEV_DOWN + marginDeg, Balance.HGUN_ELEV_UP - marginDeg);
        if (Math.abs(cy - y) < 1.0E-3 && Math.abs(ce - e) < 1.0E-3) return d;
        double ry = Math.toRadians(cy), re = Math.toRadians(ce);
        return f[1].scale(-Math.cos(re) * Math.sin(ry)).add(f[2].scale(Math.sin(re))).add(f[3].scale(-Math.cos(re) * Math.cos(ry))).normalize();
    }

    Vec3 dirPlot(double yawDeg, double elevDeg) {
        double t = Math.toRadians(elevDeg), p = Math.toRadians(yawDeg);
        double lx = -Math.cos(t) * Math.sin(p), ly = Math.sin(t), lz = -Math.cos(t) * Math.cos(p);
        Vec3[] f = frame();
        return f[1].scale(lx).add(f[2].scale(ly)).add(f[3].scale(lz));
    }

    private double[] anglesTo(Vec3 plot) {
        Vec3[] f = frame();
        Vec3 d = plot.subtract(pivotPlot());
        double lx = d.dot(f[1]), ly = d.dot(f[2]), lz = d.dot(f[3]);
        if (lx * lx + ly * ly + lz * lz < 1.0E-6) return null;
        return new double[] { Math.toDegrees(Math.atan2(-lx, -lz)), Math.toDegrees(Math.atan2(ly, Math.hypot(lx, lz))) };
    }

    @Override
    public Vec3 lensPlot(double yawDeg, double elevDeg, double deploy01) {
        double t = Math.toRadians(elevDeg), p = Math.toRadians(yawDeg);
        double y1 = LENS_Y * Math.cos(t) - LENS_Z * Math.sin(t), z1 = LENS_Y * Math.sin(t) + LENS_Z * Math.cos(t);
        double x2 = z1 * Math.sin(p), z2 = z1 * Math.cos(p);
        return plotPoint((PX + x2) / 16.0, (PY + y1) / 16.0, (PZ + z2) / 16.0);
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); deploy = 0.0F; claimKind = 0; return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; claimKind = 0; }
            if (ship != null) GUNS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        if (ship == null) { deploy = Math.max(0.0F, deploy - 1.0F / (Balance.HGUN_DOOR_TICKS + Balance.HGUN_RUN_TICKS)); syncAim(sl, now); return false; }
        boolean claimed = claimKind != 0 && now - lastClaim <= Balance.GTUR_CLAIM_TICKS;
        boolean want = claimKind != 0 && now - lastClaim <= Balance.GTUR_STOW_DELAY;
        boolean power = want && ShipPower.draw(sl, ship, Balance.HGUN_FE_LAY);
        float step = 1.0F / (Balance.HGUN_DOOR_TICKS + Balance.HGUN_RUN_TICKS);
        boolean cycling = cycleAt >= 0;
        if (want && deploy < 1.0F && power) {
            if (deploy <= 0.0F) playOnce(sl, WmdSounds.HG_PORT.get(), Balance.SND_MECH, 1.0F);
            deploy = Math.min(1.0F, deploy + step);
        } else if (!want && deploy > 0.0F && !cycling) {
            if (Math.abs(yaw) > 0.5F || Math.abs(elev) > 0.5F) lay(0.0, 0.0);
            else {
                if (deploy >= 1.0F) playOnce(sl, WmdSounds.HG_PORT.get(), Balance.SND_MECH, 0.92F);
                deploy = Math.max(0.0F, deploy - step);
            }
            if (deploy <= 0.0F) claimKind = 0;
        }
        boolean ready = deploy >= 1.0F;

        boolean wantsLoad = ready && power && loaded < 0 && !cycling && rackN > 0 && now >= lastShot + 10;
        if (wantsLoad && Math.abs(elev) <= 0.5F) { cycleAt = now; breechOpen = true; playOnce(sl, WmdSounds.HG_BREECH.get(), Balance.SND_MECH, 0.9F); sync(); }
        cycling = cycleAt >= 0;
        if (cycling) {
            long t = now - cycleAt;
            if (t == Balance.HGUN_TRAY_TICKS + Balance.HGUN_RAM_TICKS && rackN > 0 && loaded < 0) {
                loaded = rack[0];
                System.arraycopy(rack, 1, rack, 0, rackN - 1);
                rackN--;
                breechOpen = false;
                playOnce(sl, WmdSounds.HG_BREECH.get(), Balance.SND_MECH, 1.15F);
                sync();
            }
            if (t >= 2L * Balance.HGUN_TRAY_TICKS + Balance.HGUN_RAM_TICKS) { cycleAt = -1L; sync(); }
        }

        double[] tgt = null;
        outOfField = false;
        boolean loadPose = wantsLoad || cycleAt >= 0;
        if (loadPose && (!claimed || aimPoint == null) && Math.abs(elev) > 0.5F) lay(yaw, 0.0);
        if (ready && claimed && aimPoint != null && power) {
            Vec3 pw = SableBridge.toWorld(sl, pivotPlot());
            if (pw != null) {
                double dist = pw.distanceTo(aimPoint);
                int dur = Math.max(2, (int) Math.ceil(dist / Balance.HGUN_SPEED));
                Vec3 up = aimPoint.add(0.0, Balance.ART_GRAVITY * dur * dur * 0.5, 0.0);
                Vec3 ap = SableBridge.toShip(sl, ship, up), aw = SableBridge.toShip(sl, ship, aimPoint);
                tgt = ap == null ? null : anglesTo(ap);
                double[] look = aw == null ? null : anglesTo(aw);
                if (tgt != null) {

                    double ty0 = Math.abs(tgt[0]) > 90.0 ? Math.copySign(Balance.HGUN_YAW_MAX, yaw == 0.0F ? tgt[0] : yaw) : tgt[0];
                    double cy = Mth.clamp(ty0, -Balance.HGUN_YAW_MAX, Balance.HGUN_YAW_MAX), ce = Mth.clamp(tgt[1], -Balance.HGUN_ELEV_DOWN, Balance.HGUN_ELEV_UP);

                    if (look == null) look = tgt;
                    outOfField = Math.abs(look[0]) > Balance.HGUN_YAW_MAX + 0.5 || look[1] > Balance.HGUN_ELEV_UP + 0.5 || look[1] < -Balance.HGUN_ELEV_DOWN - 0.5;
                    tgt[0] = cy; tgt[1] = ce;
                    lay(tgt[0], loadPose ? 0.0 : tgt[1]);
                }
            }
        }
        if (ready && now >= nextLineCheck) {
            nextLineCheck = now + 5;
            Vec3 d = dirPlot(yaw, elev).normalize();
            lineBlocked = GunTurretBlockEntity.blockOnLine(sl, pivotPlot().add(d.scale(MUZZLE / 16.0)), d, SableBridge.plotBox(sl, ship), p -> false);
        }
        boolean trig = claimed && now < triggerUntil;
        boolean aligned = tgt != null && Math.abs(tgt[0] - yaw) <= Balance.HGUN_ALIGN_DEG && Math.abs(tgt[1] - elev) <= Balance.HGUN_ALIGN_DEG;
        if (trig && ready && aligned && loaded >= 0 && cycleAt < 0 && now >= nextShotAt && !outOfField && !lineBlocked) fire(sl, now);
        syncAim(sl, now);
        return false;
    }

    private void lay(double ty, double te) {
        yaw = (float) (yaw + Mth.clamp(ty - yaw, -Balance.HGUN_YAW_RATE, Balance.HGUN_YAW_RATE));
        elev = (float) (elev + Mth.clamp(te - elev, -Balance.HGUN_ELEV_RATE, Balance.HGUN_ELEV_RATE));
    }

    private void fire(ServerLevel sl, long now) {
        if (!AirGate.on() || loaded < 0 || ship == null || aimPoint == null) return;
        Vec3 d = dirPlot(yaw, elev).normalize();
        Vec3 mPlot = pivotPlot().add(d.scale(MUZZLE / 16.0));
        if (GunTurretBlockEntity.blockOnLine(sl, mPlot, d, SableBridge.plotBox(sl, ship), p -> false)) { lineBlocked = true; return; }
        Vec3 m = SableBridge.toWorld(sl, mPlot);
        if (m == null) return;
        Vec3 target = aimPoint;
        double dist = m.distanceTo(target);
        if (dist < Balance.HGUN_RANGE_MIN) return;
        if (dist > Balance.HGUN_RANGE) { target = m.add(target.subtract(m).normalize().scale(Balance.HGUN_RANGE)); dist = Balance.HGUN_RANGE; }
        double spread = loaded == Artillery.GUIDED && Mlrs.navGuided(sl.getServer(), getOwner()) ? Math.min(Balance.ART_MIN_SPREAD, Balance.GUIDED_SHELL_SPREAD)
                : Balance.ART_MIN_SPREAD;
        double a = sl.random.nextDouble() * Math.PI * 2.0, r = spread * sl.random.nextDouble();
        double ix = target.x + Math.cos(a) * r, iz = target.z + Math.sin(a) * r;
        if (OfflineGuard.refuse(sl, ix, iz) != null) return;
        if (!ShipPower.draw(sl, ship, Balance.HGUN_FE_SHOT)) return;
        int dur = Math.max(2, (int) Math.ceil(dist / Balance.HGUN_SPEED));
        ArtShell s = ArtilleryManager.get(sl).fire(sl, getOwner(), getOwnerName(), Artillery.HOWITZER, loaded, m, ix, target.y, iz, dur, BlockPos.containing(m), ++seq,
                rackN == 0);
        if (s == null) return;
        loaded = -1;
        breechOpen = true;
        lastShot = now;
        nextShotAt = now + Balance.HGUN_RELOAD_TICKS;
        firingUntil = now + 6;

        PacketDistributor.sendToPlayersNear(sl, null, m.x, m.y, m.z, 600, new GunnerPayloads.Fire(worldPosition, m.x, m.y, m.z, ix, target.y, iz, SHOT_KIND));
        sync();
    }

    private void syncAim(ServerLevel sl, long now) {
        int flags = (firingNow() ? 1 : 0) | (blockedNow() ? 2 : 0) | (rounds() <= 0 ? 4 : 0);
        boolean changed = Math.abs(yaw - syncedYaw) > 0.3F || Math.abs(elev - syncedElev) > 0.3F || Math.abs(deploy - syncedDeploy) > 0.02F || flags != syncedFlags
                || (deploy == 0.0F) != (syncedDeploy == 0.0F) || (deploy == 1.0F) != (syncedDeploy == 1.0F);
        if (!changed || now - lastSyncAt < Balance.GTUR_SYNC_TICKS) return;
        Vec3 w = SableBridge.toWorld(sl, Vec3.atCenterOf(worldPosition));
        if (w == null) return;
        syncedYaw = yaw; syncedElev = elev; syncedDeploy = deploy; syncedFlags = flags; lastSyncAt = now;
        PacketDistributor.sendToPlayersNear(sl, null, w.x, w.y, w.z, Balance.GTUR_SYNC_RANGE, new GunnerPayloads.Aim(worldPosition, yaw, elev, deploy, flags));
    }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = GUNS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) leave(sl);
        super.setRemoved();
    }

    @Override public void clientAim(float y, float e, float d, int flags) { yawT = y; elevT = e; deployT = d; }
    @Override public float[] shownPose() { return new float[] { yawShown, elevShown, deployShown }; }

    void clientKick() {
        kick = 1.0F;
        if (level != null) flashUntil = level.getGameTime() + 2;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (Double.isNaN(lastT)) { yawShown = yawT; elevShown = elevT; deployShown = deployT; wedgeShown = breechOpen ? 1.0F : 0.0F; }
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT);
            yawShown += (float) Mth.clamp(yawT - yawShown, -Balance.HGUN_YAW_RATE * dt, Balance.HGUN_YAW_RATE * dt);
            elevShown += (float) Mth.clamp(elevT - elevShown, -Balance.HGUN_ELEV_RATE * dt, Balance.HGUN_ELEV_RATE * dt);
            float total = Balance.HGUN_DOOR_TICKS + Balance.HGUN_RUN_TICKS;
            deployShown += (float) Mth.clamp(deployT - deployShown, -dt / total, dt / total);
            wedgeShown += (float) Mth.clamp((breechOpen ? 1.0F : 0.0F) - wedgeShown, -dt / 6.0, dt / 6.0);
            kick = Math.max(0.0F, kick - (float) (dt / 14.0));
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        float total = Balance.HGUN_DOOR_TICKS + Balance.HGUN_RUN_TICKS;
        float doors = Mth.clamp(deployShown * total / Balance.HGUN_DOOR_TICKS, 0.0F, 1.0F);
        float run = Mth.clamp((deployShown * total - Balance.HGUN_DOOR_TICKS) / Balance.HGUN_RUN_TICKS, 0.0F, 1.0F);
        long now = level == null ? 0L : level.getGameTime();
        double ct = cycleAt >= 0 ? (now - cycleAt) + pt : -1.0;
        float out = ct < 0 ? 0.0F : (float) Mth.clamp(ct / Balance.HGUN_TRAY_TICKS, 0.0, 1.0);
        float ram = ct < 0 ? 0.0F : (float) Mth.clamp((ct - Balance.HGUN_TRAY_TICKS) / Balance.HGUN_RAM_TICKS, 0.0, 1.0);
        float back = ct < 0 ? 0.0F : (float) Mth.clamp((ct - Balance.HGUN_TRAY_TICKS - Balance.HGUN_RAM_TICKS) / Balance.HGUN_TRAY_TICKS, 0.0, 1.0);
        boolean shellOnTray = ct >= 0 && ct < Balance.HGUN_TRAY_TICKS + Balance.HGUN_RAM_TICKS;
        int shownRack = rackN - (ct >= 0 && ct < Balance.HGUN_TRAY_TICKS + Balance.HGUN_RAM_TICKS ? 1 : 0);
        final float dx = (float) (DOOR * doors) * DOOR_SIGN;
        final float bz = (float) (-RUN * run + RECOIL * Math.min(1.0F, kick * 1.5F)) * BARREL_SIGN;
        final float yr = (float) Math.toRadians(yawShown) * YAW_SIGN, er = (float) Math.toRadians(elevShown) * ELEV_SIGN;
        final float wy = (float) (-WEDGE * wedgeShown) * WEDGE_SIGN;
        final float tr = (float) Math.toRadians(180.0 * (out - back)) * TRAY_SIGN;
        final float rz = (float) (RAM * ram * (1.0F - back)) * RAM_SIGN;
        final boolean fl = now < flashUntil;
        model.getBone("housing_door_l").ifPresent(b -> b.setPosX(-dx));
        model.getBone("housing_door_r").ifPresent(b -> b.setPosX(dx));
        model.getBone("gun").ifPresent(b -> { b.setRotX(er); b.setRotY(yr); });
        model.getBone("barrel").ifPresent(b -> b.setPosZ(bz));
        model.getBone("wedge").ifPresent(b -> b.setPosY(wy));
        model.getBone("tray").ifPresent(b -> b.setRotY(tr));
        model.getBone("ram").ifPresent(b -> b.setPosZ(rz));
        model.getBone("tshell").ifPresent(b -> b.setHidden(!shellOnTray));
        model.getBone("flash").ifPresent(b -> b.setHidden(!fl));
        for (int i = 0; i < Balance.HGUN_RACK; i++) {
            final boolean show = i < shownRack;
            model.getBone("rs" + i).ifPresent(b -> b.setHidden(!show));
        }
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected boolean soundAlive() { return false; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override
    protected String detail() { return rackN + " racked" + (loaded >= 0 ? ", 1 in the breech" : "") + ", deploy " + Math.round(deploy * 100) + "%" + (ship == null ? ", not on a craft" : ""); }

    @Override
    public Component status() {
        Component next = loaded >= 0 ? Component.translatable("item.wmd." + Artillery.shellId(Artillery.HOWITZER, loaded))
                : Component.translatable("message.wmd.heavy_gun.breech_empty");
        return Component.translatable("block.wmd.heavy_gun").append(": ").append(Component.translatable("message.wmd.heavy_gun.status", rackN, Balance.HGUN_RACK, next));
    }

    private void writeAmmo(CompoundTag tag) {
        tag.putIntArray("hgRack", java.util.Arrays.copyOf(rack, rackN));
        tag.putInt("hgLoaded", loaded);
    }

    private void readAmmo(CompoundTag tag) {
        int[] r = tag.getIntArray("hgRack");
        rackN = 0;
        for (int v : r) if (rackN < rack.length && v >= 0 && fits(v)) rack[rackN++] = v;
        int l = tag.contains("hgLoaded") ? tag.getInt("hgLoaded") : -1;
        loaded = l >= 0 && fits(l) ? l : -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        writeAmmo(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        readAmmo(tag);
        if (tag.contains("hgOpen")) breechOpen = tag.getBoolean("hgOpen");
        if (tag.contains("hgCycle")) cycleAt = tag.getLong("hgCycle");
        if (tag.contains("hgDeploy")) { deployT = tag.getFloat("hgDeploy"); yawT = tag.getFloat("hgYaw"); elevT = tag.getFloat("hgElev"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        writeAmmo(tag);
        tag.putBoolean("hgOpen", breechOpen);
        tag.putLong("hgCycle", cycleAt);
        tag.putFloat("hgDeploy", deploy);
        tag.putFloat("hgYaw", yaw);
        tag.putFloat("hgElev", elev);
        return tag;
    }
}
