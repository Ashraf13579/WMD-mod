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
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class GunTurretBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host, GunnerGun {

    static final float YAW_SIGN = 1.0F, ELEV_SIGN = 1.0F, DOOR_SIGN = 1.0F, LIFT_SIGN = 1.0F, RECOIL_SIGN = 1.0F;

    static final double PIVOT_Y = -6.2, DROP = 10.2, MUZZLE = 20.8, RECOIL_PX = 1.6, BELT_PX = 1.0;
    static final double SIGHT_X = -2.9, SIGHT_Y = 7.0, SIGHT_Z = -3.2, LENS = 1.8;
    static final int STATION = 1, COCKPIT = 2;
    static final int COLS = 360 / Balance.GTUR_ARC_STEP, ROWS = 90 / Balance.GTUR_ARC_STEP + 1;

    static final int HIT_NONE = 0, HIT_GROUND = 1, HIT_TARGET = 2;

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gun_turret"));

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> TURRETS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int rounds;
    private UUID ship;

    private float deploy, yaw, elev;
    private int claimKind;
    private long claimBy, lastClaim = Long.MIN_VALUE / 2, triggerUntil, nextShot, nextSound, lastSyncAt, lastHitAt = Long.MIN_VALUE / 2;
    private UUID claimPlayer;
    private Vec3 aimPoint;

    private double shotAcc;
    private long nextHelmetRay;
    private final BitSet arc = new BitSet(COLS * ROWS), arcNext = new BitSet(COLS * ROWS);
    private boolean arcReady, firing, blocked, wasBlocked;
    private int arcCol;
    private long arcAt;
    private int[] arcBox;
    private float syncedYaw = 999.0F, syncedElev, syncedDeploy = -1.0F;
    private int syncedFlags = -1, arcHash;

    private float yawT, elevT, deployT, yawShown, elevShown, deployShown, recoil, belt;
    private int flagsShown;
    private long flashUntil;
    private double lastT = Double.NaN;

    public GunTurretBlockEntity(BlockPos pos, BlockState state) {
        super(GunnerBlocks.GUN_TURRET_BE.get(), pos, state, 0, 1, 0);
    }

    @Override public int rounds() { return rounds; }
    UUID ship() { return ship; }
    @Override public BlockPos gunPos() { return worldPosition; }
    @Override public int roundsMax() { return Balance.GTUR_ROUNDS; }
    @Override public boolean heavy() { return false; }
    @Override public boolean deployed() { return deploy >= 1.0F; }
    @Override public boolean blockedNow() { return blocked; }
    @Override public boolean firingNow() { return firing; }
    @Override public boolean arcReady() { return arcReady; }
    @Override public BitSet arcMap() { return arc; }
    int arcHash() { return arcHash; }
    @Override public float yawDeg() { return yaw; }
    @Override public float elevDeg() { return elev; }
    @Override public float deploy01() { return deploy; }
    @Override public long lastHitAt() { return lastHitAt; }

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
        int boxes = rounds / Balance.GTUR_BOX;
        if (boxes > 0 && AirGate.AVAILABLE) { l.add(new ItemStack(Wmd.CIWS_AMMO.get(), Math.min(boxes, 64))); rounds -= boxes * Balance.GTUR_BOX; }
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    String reload(Player player, ItemStack held) {
        String k = loadBox();
        if ("message.wmd.gun_turret.loaded".equals(k) && !player.getAbilities().instabuild) held.shrink(1);
        return k;
    }

    String loadBox() {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return "message.wmd.gun_turret.off";
        UUID s = SableBridge.shipIdAt(sl, worldPosition);
        if (s != null && AirPicture.isAircraft(sl, s)) return "message.wmd.gun_turret.land_first";
        if (rounds + Balance.GTUR_BOX > Balance.GTUR_ROUNDS) return "message.wmd.gun_turret.full";
        rounds += Balance.GTUR_BOX;
        playOnce(sl, WmdSounds.GPOD_RELOAD.get(), Balance.SND_CLICK, 0.9F);
        sync();
        return "message.wmd.gun_turret.loaded";
    }

    static List<GunTurretBlockEntity> turretsOf(ServerLevel level, UUID ship) {
        List<GunTurretBlockEntity> out = new ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = TURRETS.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof GunTurretBlockEntity g && !g.isRemoved() && ship.equals(g.ship)) out.add(g);
        }
        out.sort(Comparator.comparingLong(g -> g.worldPosition.asLong()));
        return out;
    }

    boolean claimedByOther(long now, int kind, long by) {
        if (claimKind == 0 || now - lastClaim > Balance.GTUR_CLAIM_TICKS) return false;
        return claimKind != kind || claimBy != by;
    }

    @Override
    public boolean stationCommand(long now, BlockPos station, Vec3 point, boolean trigger) {
        if (!AirGate.on()) return false;
        long by = station.asLong();
        if (claimKind == STATION && claimBy != by && now - lastClaim <= Balance.GTUR_CLAIM_TICKS) return false;
        claimKind = STATION; claimBy = by; claimPlayer = null; lastClaim = now;
        if (point != null) aimPoint = point;
        triggerUntil = trigger ? now + 3 : 0;
        return true;
    }

    static void cockpitInput(ServerLevel sl, UUID ship, BlockPos cockpit, ServerPlayer p, boolean gunHeld) {
        if (!AirGate.on() || sl == null || ship == null || p == null) return;
        long now = sl.getGameTime();
        for (GunTurretBlockEntity t : turretsOf(sl, ship)) {
            if (t.claimKind == STATION && now - t.lastClaim <= Balance.GTUR_CLAIM_TICKS) continue;
            if (t.claimKind != COCKPIT || t.claimBy != cockpit.asLong()) t.nextHelmetRay = now;
            t.claimKind = COCKPIT; t.claimBy = cockpit.asLong(); t.claimPlayer = p.getUUID(); t.lastClaim = now;
            t.triggerUntil = gunHeld ? now + Balance.GTUR_CLAIM_TICKS / 2 : 0;
        }
    }

    private void helmet(ServerLevel sl, long now) {
        if (claimKind != COCKPIT || claimPlayer == null || now < nextHelmetRay) return;
        nextHelmetRay = now + Balance.GUNNER_HELMET_RAY;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(claimPlayer);
        if (p == null || p.level() != sl || !(p.getVehicle() instanceof CockpitSeatEntity seat) || seat.cockpit().asLong() != claimBy
                || !ship.equals(SableBridge.shipIdAt(sl, seat.cockpit()))) { claimKind = 0; claimPlayer = null; triggerUntil = 0; return; }
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        if (SableBridge.shipIdAt(sl, BlockPos.containing(eye)) != null) eye = SableBridge.toWorld(sl, eye);
        Object[] g = TargetingPodBlockEntity.groundRay(sl, eye, look, Balance.GTUR_RANGE, ship);
        aimPoint = g != null ? (Vec3) g[0] : eye.add(look.scale(Balance.GTUR_RANGE));
    }

    private Vec3[] frame() {
        Vec3 o = plotPoint(0.0, 0.5, 0.0);
        return new Vec3[] { o, plotPoint(1.0, 0.5, 0.0).subtract(o), plotPoint(0.0, 1.5, 0.0).subtract(o), plotPoint(0.0, 0.5, 1.0).subtract(o) };
    }

    Vec3 pivotPlot() { return plotPoint(0.0, PIVOT_Y / 16.0, 0.0); }

    @Override
    public Vec3 clampAimPlot(Vec3 d, double marginDeg) {
        Vec3[] f = frame();
        double lx = d.dot(f[1]), ly = d.dot(f[2]), lz = d.dot(f[3]), min = Balance.GTUR_ELEV_MIN + marginDeg;
        if (Math.toDegrees(Math.atan2(-ly, Math.hypot(lx, lz))) >= min) return d;
        double y = Math.atan2(lx, lz), e = Math.toRadians(min);
        return f[1].scale(Math.cos(e) * Math.sin(y)).add(f[2].scale(-Math.sin(e))).add(f[3].scale(Math.cos(e) * Math.cos(y))).normalize();
    }

    Vec3 dirPlot(double yawDeg, double elevDeg) {
        double y = Math.toRadians(yawDeg), e = Math.toRadians(elevDeg);
        double lx = Math.cos(e) * Math.sin(y), ly = -Math.sin(e), lz = Math.cos(e) * Math.cos(y);
        Vec3[] f = frame();
        return f[1].scale(lx).add(f[2].scale(ly)).add(f[3].scale(lz));
    }

    private double[] anglesTo(Vec3 plot) {
        Vec3[] f = frame();
        Vec3 d = plot.subtract(pivotPlot());
        double lx = d.dot(f[1]), ly = d.dot(f[2]), lz = d.dot(f[3]);
        if (lx * lx + ly * ly + lz * lz < 1.0E-6) return null;
        return new double[] { Math.toDegrees(Math.atan2(lx, lz)), Math.toDegrees(Math.atan2(-ly, Math.hypot(lx, lz))) };
    }

    @Override
    public Vec3 lensPlot(double yawDeg, double elevDeg, double deploy01) {
        double lift = Mth.clamp((deploy01 - 0.4) / 0.6, 0.0, 1.0);
        double y = Math.toRadians(yawDeg), c = Math.cos(y), s = Math.sin(y);
        double bx = SIGHT_X * c + SIGHT_Z * s, bz = -SIGHT_X * s + SIGHT_Z * c, by = SIGHT_Y - DROP * lift;
        double e = Math.toRadians(elevDeg);
        double lx = Math.cos(e) * Math.sin(y), ly = -Math.sin(e), lz = Math.cos(e) * Math.cos(y);
        return plotPoint((bx + lx * LENS) / 16.0, (by + ly * LENS) / 16.0, (bz + lz * LENS) / 16.0);
    }

    private static int cell(int col, int row) { return col * ROWS + row; }

    private boolean ownBlockOnLine(ServerLevel sl, Vec3 from, Vec3 dir, int[] box) { return blockOnLine(sl, from, dir, box, p -> isOwnCell(p) || p.equals(worldPosition)); }

    static boolean blockOnLine(ServerLevel sl, Vec3 from, Vec3 dir, int[] box, java.util.function.Predicate<BlockPos> skip) {
        if (dir.lengthSqr() < 1.0E-9) return false;
        Vec3 d = dir.normalize();
        int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
        int sx = d.x > 0 ? 1 : -1, sy = d.y > 0 ? 1 : -1, sz = d.z > 0 ? 1 : -1;
        double tx = d.x == 0 ? Double.MAX_VALUE : ((d.x > 0 ? x + 1 - from.x : from.x - x) / Math.abs(d.x));
        double ty = d.y == 0 ? Double.MAX_VALUE : ((d.y > 0 ? y + 1 - from.y : from.y - y) / Math.abs(d.y));
        double tz = d.z == 0 ? Double.MAX_VALUE : ((d.z > 0 ? z + 1 - from.z : from.z - z) / Math.abs(d.z));
        double dx = d.x == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(d.x), dy = d.y == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(d.y), dz = d.z == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(d.z);
        double t = 0.0;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        while (t <= Balance.GTUR_ARC_REACH) {
            if (box != null && (x < box[0] - 1 || y < box[1] - 1 || z < box[2] - 1 || x > box[3] + 1 || y > box[4] + 1 || z > box[5] + 1)) return false;
            mp.set(x, y, z);
            if (skip.test(mp)) {  }
            else {
                if (!sl.isLoaded(mp)) return false;
                BlockState s = sl.getBlockState(mp);
                if (!s.isAir() && s.blocksMotion()) return true;
            }
            if (tx <= ty && tx <= tz) { t = tx; tx += dx; x += sx; }
            else if (ty <= tz) { t = ty; ty += dy; y += sy; }
            else { t = tz; tz += dz; z += sz; }
        }
        return false;
    }

    private boolean isOwnCell(BlockPos p) {
        if (p.equals(worldPosition)) return true;
        Vec3 rear = plotPoint(0.0, 0.5, 1.0);
        return p.equals(BlockPos.containing(rear));
    }

    private void arcTick(ServerLevel sl, long now) {
        if (deploy <= 0.0F || ship == null) { arcReady = false; arcCol = 0; return; }
        if (arcCol == 0) {
            if (arcReady && now - arcAt < Balance.GTUR_ARC_REFRESH) return;
            arcBox = SableBridge.plotBox(sl, ship);
            if (arcBox != null && AirframeRecords.tooBig(arcBox)) arcBox = null;
            arcNext.clear();
        }
        Vec3 piv = pivotPlot();
        int end = Math.min(COLS, arcCol + Balance.GTUR_ARC_COLS);
        for (int c = arcCol; c < end; c++)
            for (int r = 0; r < ROWS; r++)
                if (ownBlockOnLine(sl, piv, dirPlot(c * Balance.GTUR_ARC_STEP, r * Balance.GTUR_ARC_STEP), arcBox)) arcNext.set(cell(c, r));
        arcCol = end;
        if (arcCol >= COLS) {
            arc.clear(); arc.or(arcNext);
            arcReady = true; arcAt = now; arcCol = 0;
            arcHash = arc.hashCode();
        }
    }

    boolean arcBlocked(double yawDeg, double elevDeg) {
        if (!arcReady) return true;
        int c = Math.floorMod((int) Math.round(yawDeg / Balance.GTUR_ARC_STEP), COLS);
        int r = Mth.clamp((int) Math.round(elevDeg / Balance.GTUR_ARC_STEP), 0, ROWS - 1);
        return arc.get(cell(c, r));
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); deploy = 0.0F; firing = false; claimKind = 0; return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; claimKind = 0; arcReady = false; arcCol = 0; }
            if (ship != null) TURRETS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        if (ship == null) { deploy = Math.max(0.0F, deploy - 1.0F / Balance.GTUR_DEPLOY_TICKS); firing = false; syncAim(sl, now); return false; }
        helmet(sl, now);
        boolean claimed = claimKind != 0 && now - lastClaim <= Balance.GTUR_CLAIM_TICKS;
        boolean want = claimKind != 0 && now - lastClaim <= Balance.GTUR_STOW_DELAY;
        boolean moving = false;
        float step = 1.0F / Balance.GTUR_DEPLOY_TICKS;
        boolean power = want && ShipPower.draw(sl, ship, Balance.GTUR_FE_MOVE);
        if (want && deploy < 1.0F) {
            if (power) {
                if (deploy <= 0.0F) playOnce(sl, WmdSounds.GT_DEPLOY.get(), Balance.SND_MECH, 1.0F);
                deploy = Math.min(1.0F, deploy + step); moving = true;
            }
        } else if (!want && deploy > 0.0F) {
            if (Math.abs(yaw) > 0.5F || elev > 0.5F) { moving |= slew(0.0, 0.0, false); }
            else {
                if (deploy >= 1.0F) playOnce(sl, WmdSounds.GT_STOW.get(), Balance.SND_MECH, 1.0F);
                deploy = Math.max(0.0F, deploy - step); moving = true;
            }
            if (deploy <= 0.0F) { claimKind = 0; claimPlayer = null; }
        }
        arcTick(sl, now);
        boolean ready = deploy >= 1.0F;
        double[] tgt = null;

        boolean outOfCone = false;
        if (ready && claimed && aimPoint != null) {
            Vec3 ap = SableBridge.toShip(sl, ship, aimPoint);
            tgt = ap == null ? null : anglesTo(ap);
            if (tgt != null) {
                outOfCone = tgt[1] < Balance.GTUR_ELEV_MIN - Balance.GTUR_ALIGN_DEG;
                tgt[1] = Mth.clamp(tgt[1], Balance.GTUR_ELEV_MIN, Balance.GTUR_ELEV_MAX);
                if (power) moving |= slew(tgt[0], tgt[1], true);
            }
        }

        boolean trig = claimed && now < triggerUntil;
        blocked = ready && (outOfCone || arcReady && arcBlocked(yaw, elev));
        boolean aligned = tgt != null && Math.abs(Mth.wrapDegrees(tgt[0] - yaw)) <= Balance.GTUR_ALIGN_DEG && Math.abs(tgt[1] - elev) <= Balance.GTUR_ALIGN_DEG;
        boolean shoot = trig && ready && arcReady && rounds > 0 && !blocked && aligned;
        if (trig && blocked && !wasBlocked) playOnce(sl, WmdSounds.COCKPIT_SWITCH.get(), Balance.SND_CLICK, 0.7F);
        wasBlocked = trig && blocked;

        if (shoot) {
            shotAcc += Balance.GTUR_RATE / 20.0;
            while (shotAcc >= 1.0 && rounds > 0) {
                if (!ShipPower.draw(sl, ship, Balance.GTUR_FE_FIRE)) { shoot = false; shotAcc = 0.0; break; }
                fire(sl, now);
                shotAcc -= 1.0;
            }
        } else shotAcc = 0.0;
        firing = shoot;
        syncAim(sl, now);
        return moving;
    }

    private boolean slew(double tyaw, double telev, boolean guard) {
        float ny = (float) (yaw + Mth.clamp(Mth.wrapDegrees(tyaw - yaw), -Balance.GTUR_YAW_RATE, Balance.GTUR_YAW_RATE));
        float ne = (float) (elev + Mth.clamp(telev - elev, -Balance.GTUR_ELEV_RATE, Balance.GTUR_ELEV_RATE));
        ny = Mth.wrapDegrees(ny);
        ne = Mth.clamp(ne, 0.0F, (float) Balance.GTUR_ELEV_MAX);
        if (guard && arcReady && arcBlocked(ny, ne) && !arcBlocked(yaw, elev)) {
            if (!arcBlocked(ny, elev)) ne = elev;
            else if (!arcBlocked(yaw, ne)) ny = yaw;
            else return false;
        }
        boolean moved = Math.abs(Mth.wrapDegrees(ny - yaw)) > 1.0E-3F || Math.abs(ne - elev) > 1.0E-3F;
        yaw = ny; elev = ne;
        return moved;
    }

    private void fire(ServerLevel sl, long now) {
        if (!AirGate.on() || rounds <= 0 || ship == null) return;
        double sy = yaw + sl.random.nextGaussian() * Balance.GTUR_SPREAD * 0.5, se = elev + sl.random.nextGaussian() * Balance.GTUR_SPREAD * 0.5;
        Vec3 dPlot = dirPlot(sy, se).normalize(), piv = pivotPlot();
        if (ownBlockOnLine(sl, piv, dPlot, arcBox)) { blocked = true; return; }
        rounds--;
        Vec3 mPlot = piv.add(dPlot.scale(MUZZLE / 16.0));
        Vec3 m = SableBridge.toWorld(sl, mPlot), ahead = SableBridge.toWorld(sl, mPlot.add(dPlot));
        if (m == null || ahead == null) return;
        Vec3 dir = ahead.subtract(m);
        if (dir.lengthSqr() < 1.0E-6) return;
        dir = dir.normalize();

        if (aimPoint != null && aimPoint.distanceToSqr(m) > 4.0) {
            Vec3 at = aimPoint.subtract(m).normalize();
            double k = Math.tan(Math.toRadians(Balance.GTUR_SPREAD)) * 0.5;
            Vec3 jit = new Vec3(sl.random.nextGaussian(), sl.random.nextGaussian(), sl.random.nextGaussian()).scale(k);
            jit = jit.subtract(at.scale(jit.dot(at)));
            dir = at.add(jit).normalize();
        }
        Vec3 to;
        int kind;
        AirGuns.Jet jet = AirGuns.boresightTarget(sl, ship, m, dir, Balance.GTUR_RANGE, Balance.GTUR_AIR_CONE);
        if (jet != null) {
            SableBridge.ShipInfo own = SableBridge.shipInfo(sl, ship);
            Vec3 ownVel = own == null ? Vec3.ZERO : ShipDefence.velocity(sl, own);
            double rel = jet.vel().subtract(ownVel).length() * 20.0;
            double p = AirGuns.roundHit(jet.dist(), Balance.GTUR_RANGE, jet.blocks(), rel);
            int h = AirGuns.burst(sl, jet.id(), m, jet.pos(), 1, p, 1, getOwner());
            to = m.add(dir.scale(jet.dist()));
            kind = h > 0 ? HIT_TARGET : HIT_NONE;
        } else {
            Object[] g = TargetingPodBlockEntity.groundRay(sl, m, dir, Balance.GTUR_RANGE, ship);
            double dist = g == null ? Balance.GTUR_RANGE : ((Vec3) g[0]).distanceTo(m);
            LivingEntity e = firstLiving(sl, m, dir, dist);
            if (e != null) {
                Holder<DamageType> type = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
                ServerPlayer shooter = gunnerPlayer(sl);
                e.hurt(shooter != null ? new DamageSource(type, shooter) : new DamageSource(type, m), Balance.GTUR_DMG);
                to = e.getBoundingBox().getCenter();
                kind = HIT_TARGET;
            } else if (g != null && g[1] instanceof UUID other) {
                to = (Vec3) g[0];
                if (!other.equals(ship) && !ShipStrike.spare(sl, other, getOwner()) && sl.random.nextDouble() < Balance.GTUR_CHEW_CHANCE)
                    ShipStrike.chew(sl, other, m, to.add(dir.scale(0.8)), 1, (float) Balance.CIWS_SHIP_MAX_HARDNESS, getOwner());
                kind = HIT_TARGET;
            } else if (g != null) { to = (Vec3) g[0]; kind = HIT_GROUND; AirGuns.chewGround(sl, to, dir, 1, 1); }
            else { to = m.add(dir.scale(Balance.GTUR_RANGE)); kind = HIT_NONE; }
        }
        if (kind == HIT_TARGET) lastHitAt = now;
        HeatLog.fired(sl, worldPosition, getOwner());
        PacketDistributor.sendToPlayersNear(sl, null, m.x, m.y, m.z, 400, new GunnerPayloads.Fire(worldPosition, m.x, m.y, m.z, to.x, to.y, to.z, kind));
        if (now >= nextSound) {
            playOnce(sl, WmdSounds.GT_SHOT.get(), Balance.SND_RELEASE, 0.95F + sl.random.nextFloat() * 0.1F);
            nextSound = now + Balance.GTUR_SOUND_TICKS;
        }
        if (rounds == 0) sync();
    }

    private LivingEntity firstLiving(ServerLevel sl, Vec3 from, Vec3 dir, double max) {
        LivingEntity best = null;
        double bestD = max;
        for (double s = 0.0; s < max; s += 16.0) {
            Vec3 a = from.add(dir.scale(s)), b = from.add(dir.scale(Math.min(max, s + 16.0)));
            for (Entity en : sl.getEntities((Entity) null, new AABB(a, b).inflate(1.5), x -> x instanceof LivingEntity le && le.isAlive() && !le.isSpectator())) {
                if (ownCrew(sl, en)) continue;
                var hit = en.getBoundingBox().inflate(0.2).clip(from, from.add(dir.scale(max)));
                if (hit.isEmpty()) continue;
                double d = hit.get().distanceTo(from);
                if (d < bestD) { bestD = d; best = (LivingEntity) en; }
            }
            if (best != null) break;
        }
        return best;
    }

    private boolean ownCrew(ServerLevel sl, Entity e) {
        if (ship == null) return false;
        if (ship.equals(SableBridge.shipUnder(e))) return true;
        Entity v = e.getVehicle();
        if (v instanceof GunnerSeatEntity gs) return ship.equals(SableBridge.shipIdAt(sl, gs.station()));
        if (v instanceof CockpitSeatEntity cs) return ship.equals(SableBridge.shipIdAt(sl, cs.cockpit()));
        return false;
    }

    private ServerPlayer gunnerPlayer(ServerLevel sl) {
        if (claimKind == COCKPIT && claimPlayer != null) return sl.getServer().getPlayerList().getPlayer(claimPlayer);
        if (claimKind == STATION && sl.getBlockEntity(BlockPos.of(claimBy)) instanceof GunnerStationBlockEntity st) return st.gunner(sl);
        return null;
    }

    private void syncAim(ServerLevel sl, long now) {
        int flags = (firing ? 1 : 0) | (blocked ? 2 : 0) | (rounds <= 0 ? 4 : 0);
        boolean changed = Math.abs(Mth.wrapDegrees(yaw - syncedYaw)) > 0.5F || Math.abs(elev - syncedElev) > 0.5F || Math.abs(deploy - syncedDeploy) > 0.02F
                || flags != syncedFlags || (deploy == 0.0F) != (syncedDeploy == 0.0F) || (deploy == 1.0F) != (syncedDeploy == 1.0F);
        if (!changed || now - lastSyncAt < Balance.GTUR_SYNC_TICKS) return;
        Vec3 w = SableBridge.toWorld(sl, Vec3.atCenterOf(worldPosition));
        if (w == null) return;
        syncedYaw = yaw; syncedElev = elev; syncedDeploy = deploy; syncedFlags = flags; lastSyncAt = now;
        PacketDistributor.sendToPlayersNear(sl, null, w.x, w.y, w.z, Balance.GTUR_SYNC_RANGE, new GunnerPayloads.Aim(worldPosition, yaw, elev, deploy, flags));
    }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = TURRETS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) leave(sl);
        super.setRemoved();
    }

    @Override
    public void clientAim(float y, float e, float d, int flags) { yawT = y; elevT = e; deployT = d; flagsShown = flags; }

    void clientKick() {
        recoil = 1.0F;
        belt = (belt + 0.5F) % 1.0F;
        if (level != null) flashUntil = level.getGameTime() + 2;
    }

    @Override public float[] shownPose() { return new float[] { yawShown, elevShown, deployShown }; }
    int shownFlags() { return flagsShown; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (Double.isNaN(lastT)) { yawShown = yawT; elevShown = elevT; deployShown = deployT; }
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT);
            yawShown = Mth.wrapDegrees(yawShown + (float) Mth.clamp(Mth.wrapDegrees(yawT - yawShown), -Balance.GTUR_YAW_RATE * dt, Balance.GTUR_YAW_RATE * dt));
            elevShown += (float) Mth.clamp(elevT - elevShown, -Balance.GTUR_ELEV_RATE * dt, Balance.GTUR_ELEV_RATE * dt);
            deployShown += (float) Mth.clamp(deployT - deployShown, -dt / Balance.GTUR_DEPLOY_TICKS, dt / Balance.GTUR_DEPLOY_TICKS);
            recoil = Math.max(0.0F, recoil - (float) (dt * 0.6));
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        float doors = Mth.clamp(deployShown / 0.4F, 0.0F, 1.0F), lift = Mth.clamp((deployShown - 0.4F) / 0.6F, 0.0F, 1.0F);
        final float dr = (float) Math.toRadians(180.0 * doors) * DOOR_SIGN;
        final float ly = (float) (-DROP * lift) * LIFT_SIGN;
        final float yr = (float) Math.toRadians(yawShown) * YAW_SIGN, er = (float) Math.toRadians(elevShown) * ELEV_SIGN;
        final float rz = (float) (-RECOIL_PX * Math.min(1.0F, recoil * 1.6F)) * RECOIL_SIGN, bl = (float) (-BELT_PX * belt);
        model.getBone("housing_door_w").ifPresent(b -> b.setRotZ(-dr));
        model.getBone("housing_door_e").ifPresent(b -> b.setRotZ(dr));
        model.getBone("lift").ifPresent(b -> b.setPosY(ly));
        model.getBone("yaw").ifPresent(b -> b.setRotY(yr));
        model.getBone("elev").ifPresent(b -> b.setRotX(er));
        model.getBone("sight").ifPresent(b -> b.setRotX(er));
        model.getBone("barrel").ifPresent(b -> b.setPosZ(rz));
        model.getBone("belt").ifPresent(b -> b.setPosY(bl));
        boolean fl = level != null && level.getGameTime() < flashUntil;
        model.getBone("flash").ifPresent(b -> b.setHidden(!fl));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GT_DRIVE_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override
    protected String detail() { return rounds + " rounds, deploy " + Math.round(deploy * 100) + "%" + (firing ? ", FIRING" : "") + (ship == null ? ", not on a craft" : ""); }

    @Override
    public Component status() {
        String k = firing ? "message.wmd.gun_turret.firing" : rounds == 0 ? "message.wmd.gun_turret.empty" : ship == null ? "message.wmd.gun_turret.no_ship"
                : "message.wmd.gun_turret.ready";
        return Component.translatable("block.wmd.gun_turret").append(": ").append(Component.translatable(k, rounds, Balance.GTUR_ROUNDS));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("gtRounds", rounds);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        rounds = Math.max(0, Math.min(Balance.GTUR_ROUNDS, tag.getInt("gtRounds")));
        if (tag.contains("gtDeploy")) { deployT = tag.getFloat("gtDeploy"); yawT = tag.getFloat("gtYaw"); elevT = tag.getFloat("gtElev"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("gtRounds", rounds);
        tag.putFloat("gtDeploy", deploy);
        tag.putFloat("gtYaw", yaw);
        tag.putFloat("gtElev", elev);
        return tag;
    }
}
