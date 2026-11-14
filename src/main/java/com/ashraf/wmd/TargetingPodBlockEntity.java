package com.ashraf.wmd;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class TargetingPodBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    static final float YAW_SIGN = 1.0F, PITCH_SIGN = 1.0F;

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> PODS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private boolean flush, lasing;
    private UUID ship;
    private int spotId = -1;

    private Vec3 spotAt;
    private UUID spotShip;

    private UUID spotEntity;
    private long nextLook;

    private long memSince = -1L;

    private float aimYaw, aimPitch;

    private double lastT = Double.NaN;
    private float yawShown, pitchShown;

    public TargetingPodBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.TARGETING_POD_BE.get(), pos, state, 0, 1, 0);
    }

    public boolean flush() { return flush; }
    public boolean lasing() { return lasing; }

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
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void toggleLook() { flush = !flush; sync(); }

    private static List<TargetingPodBlockEntity> podsOf(ServerLevel level, UUID ship) {
        List<TargetingPodBlockEntity> out = new java.util.ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = PODS.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof TargetingPodBlockEntity g && !g.isRemoved()) out.add(g);
        }
        return out;
    }

    public static String toggle(ServerLevel level, UUID ship, Vec3 eye, Vec3 look) {
        List<TargetingPodBlockEntity> l = podsOf(level, ship);
        if (l.isEmpty()) return "message.wmd.tpod.none";
        boolean any = false;
        for (TargetingPodBlockEntity p : l) if (p.lasing) { p.lose(level, null); any = true; }
        if (any) return "message.wmd.tpod.cleared";
        Object[] hit = groundRay(level, eye, look, Balance.TPOD_RANGE, ship, Balance.TPOD_RAY_LOADS);

        double reach = hit == null ? Balance.TPOD_RANGE : eye.distanceTo((Vec3) hit[0]);
        net.minecraft.world.entity.Entity ent = entityOnRay(level, eye, look, reach, ship);
        if (hit == null && ent == null) return "message.wmd.tpod.no_ground";
        Vec3 w = ent != null ? ent.getBoundingBox().getCenter() : (Vec3) hit[0];
        for (TargetingPodBlockEntity p : l) {
            if (!p.sees(level, w)) continue;
            if (!ShipPower.draw(level, ship, Balance.TPOD_FE)) return "message.wmd.tpod.no_power";
            if (ent != null) { p.start(level, w, null, null); p.spotEntity = ent.getUUID(); p.setChanged(); return "message.wmd.tpod.tracking"; }
            p.start(level, w, (UUID) hit[1], (Vec3) hit[2]);
            return "message.wmd.tpod.lasing";
        }
        return "message.wmd.tpod.no_view";
    }

    private static net.minecraft.world.entity.Entity entityOnRay(ServerLevel level, Vec3 eye, Vec3 look, double max, UUID ship) {
        Vec3 d = look.normalize(), end = eye.add(d.scale(max));
        double widen = Math.max(Balance.TPOD_TRACK_R, max * Math.tan(Math.toRadians(Balance.TPOD_TRACK_DEG)));
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(eye, end).inflate(widen);
        net.minecraft.world.entity.Entity best = null;
        double bestT = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box,
                x -> x.isAlive() && !x.isSpectator() && (x instanceof net.minecraft.world.entity.LivingEntity || x instanceof net.minecraft.world.entity.vehicle.VehicleEntity)
                        && !(x instanceof CockpitSeatEntity))) {
            if (ship != null && ship.equals(SableBridge.shipUnder(e))) continue;
            if (e.getVehicle() instanceof CockpitSeatEntity) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double t = c.subtract(eye).dot(d);
            if (t < 2.0 || t > max) continue;
            double off = c.subtract(eye.add(d.scale(t))).length();

            double lim = Math.max(Balance.TPOD_TRACK_R, t * Math.tan(Math.toRadians(Balance.TPOD_TRACK_DEG))) + e.getBbWidth() * 0.5;
            double ang = Math.max(0.0, off - e.getBbWidth() * 0.5) / t;
            if (off <= lim && ang < bestT) { bestT = ang; best = e; }
        }
        return best;
    }

    public static BlockPos podAboard(ServerLevel level, UUID ship) {
        List<TargetingPodBlockEntity> l = podsOf(level, ship);
        for (TargetingPodBlockEntity p : l) if (p.lasing) return p.getBlockPos();
        return l.isEmpty() ? null : l.get(0).getBlockPos();
    }

    public Vec3 lensLocal() {
        Direction m = mount(), f = facing();
        double down = flush ? 7.4 / 16.0 : 2.0 / 16.0, fwd = flush ? 3.6 / 16.0 : 8.2 / 16.0;
        return Vec3.atCenterOf(worldPosition).add(-m.getStepX() * down + f.getStepX() * fwd, -m.getStepY() * down + f.getStepY() * fwd, -m.getStepZ() * down + f.getStepZ() * fwd);
    }

    public static boolean memAboard(ServerLevel level, UUID ship) {
        for (TargetingPodBlockEntity p : podsOf(level, ship)) if (p.lasing) return p.memSince >= 0L;
        return false;
    }

    public static void slew(ServerLevel level, UUID ship, Vec3 eye, Vec3 look) {
        for (TargetingPodBlockEntity p : podsOf(level, ship)) {
            if (!p.lasing) continue;
            Object[] hit = groundRay(level, eye, look, Balance.TPOD_RANGE, ship, Balance.TPOD_SLEW_LOADS);
            if (hit == null) return;
            Vec3 w = (Vec3) hit[0];
            if (!p.sees(level, w)) return;
            p.spotShip = (UUID) hit[1];
            p.spotEntity = null;
            p.spotAt = p.spotShip != null ? (Vec3) hit[2] : w;
            p.memSince = -1L;
            if (p.spotId < 0 || !LaserSpots.refresh(level, p.spotId, w, p.ballWorld(level))) p.spotId = LaserSpots.light(level, p.getOwner(), w, LaserSpots.SRC_POD, p.ballWorld(level));
            p.aim(level, w);
            p.sync();
            return;
        }
    }

    public static Vec3 spotAboard(ServerLevel level, UUID ship) {
        for (TargetingPodBlockEntity p : podsOf(level, ship)) if (p.lasing) { Vec3 w = p.spotWorld(level); if (w != null) return w; }
        return null;
    }

    static Object[] groundRay(ServerLevel level, Vec3 from, Vec3 dir, double max, UUID ownShip) { return groundRay(level, from, dir, max, ownShip, 0); }

    static Object[] groundRay(ServerLevel level, Vec3 from, Vec3 dir, double max, UUID ownShip, int loads) {
        if (dir.lengthSqr() < 1.0E-9) return null;
        Vec3 d = dir.normalize();
        Vec3 ground = null;
        long col = Long.MIN_VALUE;
        int base = Integer.MIN_VALUE;
        for (double t = 1.0; t <= max; t += 0.5) {
            Vec3 p = from.add(d.scale(t));
            BlockPos bp = BlockPos.containing(p);
            if (bp.getY() < level.getMinBuildHeight() || bp.getY() >= level.getMaxBuildHeight()) { if (d.y < 0 && bp.getY() < level.getMinBuildHeight()) break; continue; }
            if (level.getChunkSource().getChunkNow(bp.getX() >> 4, bp.getZ() >> 4) == null) {
                if (loads <= 0) break;
                long c = ((long) (bp.getX() >> 2) << 32) ^ ((bp.getZ() >> 2) & 0xFFFFFFFFL);
                if (c != col) {
                    col = c;
                    base = level.getChunkSource().getGenerator().getBaseHeight(bp.getX(), bp.getZ(), net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                            level, level.getChunkSource().randomState());
                }
                if (p.y > base + Balance.TPOD_RAY_AIR) continue;
                loads--;
                level.getChunk(bp.getX() >> 4, bp.getZ() >> 4);
            }
            BlockState s = level.getBlockState(bp);
            if (!s.isAir() && (!s.getCollisionShape(level, bp).isEmpty() || !s.getFluidState().isEmpty())) { ground = p; break; }
        }
        Vec3 end = ground != null ? ground : from.add(d.scale(max));
        java.util.List<SableBridge.ShipInfo> cands = new java.util.ArrayList<>();
        for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(level, from, end)) if (!s.id().equals(ownShip)) cands.add(s);
        ProjectilePath.PathHit h = cands.isEmpty() ? null : SableBridge.exactHit(level, from, end, cands);
        if (h != null && h.shipId() != null) {
            Vec3 plot = SableBridge.toShip(level, h.shipId(), h.pos());
            return new Object[] { h.pos(), plot == null ? null : h.shipId(), plot };
        }
        return ground == null ? null : new Object[] { ground, null, null };
    }

    private void start(ServerLevel sl, Vec3 world, UUID onShip, Vec3 plot) {
        UUID owner = getOwner();
        if (owner == null) return;
        spotShip = onShip;
        spotEntity = null;
        spotAt = onShip != null ? plot : world;
        memSince = -1L;
        if (spotId >= 0) LaserSpots.remove(spotId);
        spotId = LaserSpots.light(sl, owner, world, LaserSpots.SRC_POD, ballWorld(sl));
        lasing = true;
        nextLook = sl.getGameTime() + Balance.TPOD_LOS_TICKS;
        aim(sl, world);
        playOnce(sl, WmdSounds.TPOD_CLICK.get(), Balance.SND_CLICK, 1.2F);
        if (Math.abs(aimYaw) > 1.2F) playOnce(sl, WmdSounds.TPOD_SLEW.get(), Balance.SND_MECH, 1.0F);
        Wmd.LOGGER.info("Targeting pod at {}: lasing {} ({})", worldPosition, BlockPos.containing(world).toShortString(), onShip != null ? "on body " + onShip : "ground");
        sync();
    }

    private void lose(ServerLevel sl, String why) {
        if (spotId >= 0) LaserSpots.remove(spotId);
        spotId = -1;
        spotAt = null;
        spotShip = null;
        spotEntity = null;
        memSince = -1L;
        if (!lasing) return;
        lasing = false;
        playOnce(sl, WmdSounds.TPOD_CLICK.get(), Balance.SND_CLICK, 0.8F);
        if (why != null && getOwner() != null && sl.getServer().getPlayerList().getPlayer(getOwner()) instanceof net.minecraft.server.level.ServerPlayer sp)
            sp.displayClientMessage(Component.translatable(why), true);
        sync();
    }

    private Vec3 spotWorld(ServerLevel sl) {
        if (spotAt == null) return null;
        if (spotEntity != null) {
            net.minecraft.world.entity.Entity e = sl.getEntity(spotEntity);
            if (e == null || !e.isAlive()) return null;
            spotAt = e.getBoundingBox().getCenter();
            return spotAt;
        }
        if (spotShip == null) return spotAt;
        if (SableBridge.shipInfo(sl, spotShip) == null) return null;
        return SableBridge.toWorld(sl, spotAt);
    }

    private Vec3 ballWorld(ServerLevel sl) { return SableBridge.toWorld(sl, lensLocal()); }

    Direction mountSide() { return mount(); }

    private Direction mount() {
        BlockState st = getBlockState();
        return st.hasProperty(MachineBlock.MOUNT) ? st.getValue(MachineBlock.MOUNT) : Direction.UP;
    }

    boolean sees(ServerLevel sl, Vec3 w) {
        Vec3 b = ballWorld(sl);
        if (b == null || w == null) return false;
        Vec3 d = w.subtract(b);
        double l = d.length();
        if (l > Balance.TPOD_RANGE || l < 1.0) return l < 1.0;
        Direction m = mount();
        Vec3 plot = Vec3.atCenterOf(worldPosition), out = SableBridge.toWorld(sl, plot.add(-m.getStepX(), -m.getStepY(), -m.getStepZ())), c = SableBridge.toWorld(sl, plot);
        if (out == null || c == null) return false;
        Vec3 n = out.subtract(c).normalize();
        double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, d.scale(1.0 / l).dot(n)))));
        if (ang > 90.0 + Balance.TPOD_GIMBAL_UP) return false;
        if (SmokeScreens.entry(sl, b, w) >= 0.0) return false;
        Vec3 u = d.scale(1.0 / l);
        for (double t = 1.0; t < l - 1.5; t += 0.5) {
            BlockPos bp = BlockPos.containing(b.add(u.scale(t)));
            if (bp.getY() < sl.getMinBuildHeight() || bp.getY() >= sl.getMaxBuildHeight()) continue;
            if (sl.getChunkSource().getChunkNow(bp.getX() >> 4, bp.getZ() >> 4) == null) continue;
            BlockState s = sl.getBlockState(bp);
            if (!s.isAir() && !s.getCollisionShape(sl, bp).isEmpty()) return false;
        }
        return true;
    }

    private void aim(ServerLevel sl, Vec3 w) {
        Vec3 b = ballWorld(sl);
        Vec3 bp = b == null ? null : SableBridge.toShip(sl, ship, b), wp = SableBridge.toShip(sl, ship, w);
        Vec3 dir;
        if (ship == null || bp == null || wp == null) dir = b == null ? null : w.subtract(b);
        else dir = wp.subtract(bp);
        if (dir == null || dir.lengthSqr() < 1.0E-9) return;
        dir = dir.normalize();
        Direction f = facing(), m = mount();
        Vec3 fw = new Vec3(f.getStepX(), f.getStepY(), f.getStepZ()), up = new Vec3(-m.getStepX(), -m.getStepY(), -m.getStepZ());
        Vec3 right = fw.cross(up);
        double x = dir.dot(right), y = dir.dot(up), z = dir.dot(fw);
        aimYaw = (float) Math.atan2(x, z);
        aimPitch = (float) Math.atan2(-y, Math.sqrt(x * x + z * z));
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); if (lasing) lose(sl, null); return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; }
            if (ship != null) PODS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        if (!lasing) return false;
        if (ship == null) { lose(sl, "message.wmd.tpod.lost"); return false; }
        if (EmpField.jammed(sl, worldPosition)) { lose(sl, "message.wmd.tpod.lost"); return false; }
        if (!ShipPower.draw(sl, ship, Balance.TPOD_FE)) { lose(sl, "message.wmd.tpod.no_power"); return false; }
        Vec3 w = spotWorld(sl);
        if (w == null) { lose(sl, "message.wmd.tpod.lost"); return false; }
        if (now >= nextLook) {
            nextLook = now + Balance.TPOD_LOS_TICKS;
            if (!sees(sl, w)) {
                if (memSince < 0L) { memSince = now; sync(); }
                if (now - memSince > Balance.TPOD_MEM_TICKS) { lose(sl, "message.wmd.tpod.lost"); return false; }
            } else {
                if (memSince >= 0L) { memSince = -1L; playOnce(sl, WmdSounds.TPOD_CLICK.get(), Balance.SND_CLICK, 1.4F); sync(); }
                float y0 = aimYaw, p0 = aimPitch;
                aim(sl, w);
                if (Math.abs(aimYaw - y0) > 0.02F || Math.abs(aimPitch - p0) > 0.02F) sync();
            }
        }
        if (spotId < 0 || !LaserSpots.refresh(sl, spotId, w, ballWorld(sl))) spotId = LaserSpots.light(sl, getOwner(), w, LaserSpots.SRC_POD, ballWorld(sl));
        return false;
    }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = PODS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) { leave(sl); if (spotId >= 0) LaserSpots.remove(spotId); }
        super.setRemoved();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (!Double.isNaN(lastT) && tt > lastT) {
            double k = Math.min(1.0, 0.2 * Math.min(5.0, tt - lastT));
            float ty = lasing ? aimYaw : 0.0F, tp = lasing ? aimPitch : 0.0F;
            yawShown += (float) (Mth.wrapRad(ty - yawShown) * k);
            pitchShown += (float) ((tp - pitchShown) * k);
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        final float y = yawShown * YAW_SIGN, p = pitchShown * PITCH_SIGN;
        model.getBone("ball").ifPresent(b -> b.setRotY(y));
        model.getBone("head").ifPresent(b -> b.setRotX(p));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean g = lasing && !dark && ((long) tt) % 8 < 6;
        model.getBone("glint").ifPresent(b -> b.setHidden(!g));
    }

    private static final class Mth { static double wrapRad(double a) { a %= Math.PI * 2.0; if (a >= Math.PI) a -= Math.PI * 2.0; if (a < -Math.PI) a += Math.PI * 2.0; return a; } }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GPOD_SPIN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override
    protected String detail() { return (lasing ? "LASING spot " + spotId : "idle") + (ship == null ? ", not on a ship" : ", on " + ship); }

    @Override
    public Component status() {
        String k = lasing ? "message.wmd.tpod.status_lasing" : ship == null ? "message.wmd.tpod.status_no_ship" : "message.wmd.tpod.status_ready";
        return Component.translatable("block.wmd.targeting_pod").append(": ").append(Component.translatable(k, (int) Balance.TPOD_RANGE));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putBoolean("tpFlush", flush);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        flush = tag.getBoolean("tpFlush");
        if (tag.contains("tpLase")) lasing = tag.getBoolean("tpLase");
        if (tag.contains("tpYaw")) { aimYaw = tag.getFloat("tpYaw"); aimPitch = tag.getFloat("tpPitch"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("tpFlush", flush);
        tag.putBoolean("tpLase", lasing);
        tag.putFloat("tpYaw", aimYaw);
        tag.putFloat("tpPitch", aimPitch);
        return tag;
    }
}
