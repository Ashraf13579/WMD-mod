package com.ashraf.wmd;

import java.util.ArrayList;
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

public class NoseRadarBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> AT = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private boolean off, flush, open, powered;
    private UUID ship;
    private List<AirPicture.Seen> inCone = List.of();
    private float tYaw = Float.NaN, tPitch;

    private float yaw, prevYaw, pitch, prevPitch, lid, prevLid;

    public NoseRadarBlockEntity(BlockPos pos, BlockState state) { super(AirBlocks.NOSE_RADAR_BE.get(), pos, state, 0, 1, 0); }

    public static NoseRadarBlockEntity of(ServerLevel level, UUID ship) {
        if (!AirGate.on() || level == null || ship == null) return null;
        Map<UUID, Set<Long>> m = AT.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return null;
        for (Long p : s)
            if (level.isLoaded(BlockPos.of(p)) && level.getBlockEntity(BlockPos.of(p)) instanceof NoseRadarBlockEntity n && !n.isRemoved()) return n;
        return null;
    }

    public Vec3 at() { double[] a = worldPoint(0.0, 0.5, -1.0); return new Vec3(a[0], a[1], a[2]); }

    public Vec3 boresight() {
        double[] a = worldPoint(0.0, 0.5, 0.0), b = worldPoint(0.0, 0.5, -1.0);
        return new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
    }

    public boolean on() { return !off && powered && !jammed(); }
    public boolean switchedOn() { return !off; }
    public boolean jammed() { return level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition); }
    public void setOn(boolean on) { if (off == on) { off = !on; if (off) { inCone = List.of(); tYaw = Float.NaN; } sync(); } }
    public double range() { return Balance.NOSE_RANGE; }
    public double lockRange() { return Balance.AIR_NOSE_LOCK_RANGE; }
    public double searchCone() { return Balance.NOSE_SEARCH_CONE; }
    public double lockGimbal() { return Balance.AIR_NOSE_GIMBAL; }

    public List<AirPicture.Seen> seenInCone() { return inCone; }

    public void track(Vec3 target) {
        float ny = Float.NaN, np = 0.0F;
        if (target != null && on()) {
            Vec3 f = boresight(), at = at();
            double[] r0 = worldPoint(0.0, 0.5, 0.0), r1 = worldPoint(1.0, 0.5, 0.0), u1 = worldPoint(0.0, 1.5, 0.0);
            Vec3 right = new Vec3(r1[0] - r0[0], r1[1] - r0[1], r1[2] - r0[2]).normalize(), up = new Vec3(u1[0] - r0[0], u1[1] - r0[1], u1[2] - r0[2]).normalize();
            Vec3 d = target.subtract(at).normalize();
            ny = Mth.clamp((float) Math.toDegrees(Math.atan2(d.dot(right), d.dot(f))), -Balance.NOSE_ARRAY_DEG, Balance.NOSE_ARRAY_DEG);
            np = Mth.clamp((float) Math.toDegrees(Math.asin(Mth.clamp(d.dot(up), -1.0, 1.0))), -Balance.NOSE_ARRAY_DEG, Balance.NOSE_ARRAY_DEG);
        }
        boolean was = !Float.isNaN(tYaw), now = !Float.isNaN(ny);
        if (was != now || (now && (Math.abs(ny - tYaw) > 2.0F || Math.abs(np - tPitch) > 2.0F))) { tYaw = ny; tPitch = np; sync(); }
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }
    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }
    public boolean flush() { return flush; }
    void toggleFront() { flush = !flush; if (flush) open = false; sync(); }

    Component toggleRadome(ServerLevel sl) {
        if (!AirGate.on()) return Component.translatable("message.wmd.dispenser.off");
        if (flush) return Component.translatable("message.wmd.nose_radar.flush");
        if (ship != null && AirPicture.isAircraft(sl, ship)) return Component.translatable("message.wmd.nose_radar.airborne");
        open = !open;
        playOnce(sl, WmdSounds.ST_DOCK_CLAMP.get(), Balance.SND_MECH, open ? 1.3F : 1.0F);
        sync();
        return null;
    }

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

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { unregister(sl); powered = false; return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) unregister(sl);
            ship = s;
            if (ship != null) AT.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
            if (open && ship != null && AirPicture.isAircraft(sl, ship)) { open = false; sync(); }
        }
        powered = !off && ship != null && ShipPower.draw(sl, ship, Balance.NOSE_FE);
        if (!on()) { if (!inCone.isEmpty()) inCone = List.of(); return false; }
        if ((now + (worldPosition.asLong() & 3L)) % Balance.NOSE_SCAN_TICKS == 0) scan(sl, now);
        return true;
    }

    private void scan(ServerLevel sl, long now) {
        if (!AirGate.on()) return;
        Vec3 at = at(), f = boresight();
        List<AirPicture.Seen> out = new ArrayList<>();
        for (AirPicture.Seen s : AirPicture.seenFrom(sl, at, Balance.NOSE_RANGE, ship)) {
            Vec3 d = s.a().pos().subtract(at);
            double l = d.length();
            if (l > 1.0E-6 && Math.toDegrees(Math.acos(Mth.clamp(d.dot(f) / l, -1.0, 1.0))) > Balance.NOSE_SEARCH_CONE) continue;
            out.add(s);
            Rwr.search(sl, s.a().id(), at, getOwner(), "nose", worldPosition.asLong(), now);
        }
        inCone = List.copyOf(out);
    }

    private void unregister(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = AT.get(sl.dimension());
        if (m != null) {
            Set<Long> s = m.get(ship);
            if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
            if (m.isEmpty()) AT.remove(sl.dimension());
        }
        ship = null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        RadarEmit.note(this);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) unregister(sl);
        super.setRemoved();
    }

    static void clientTickNose(Level level, BlockPos pos, BlockState state, NoseRadarBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.prevYaw = be.yaw; be.prevPitch = be.pitch; be.prevLid = be.lid;
        long now = level.getGameTime();
        float ty, tp;
        if (!be.isRunning()) { ty = 0.0F; tp = 0.0F; }
        else if (!Float.isNaN(be.tYaw)) { ty = be.tYaw; tp = be.tPitch; }
        else {
            double ph = (now % 20L) / 20.0 * Math.PI * 2.0;
            ty = (float) (Balance.NOSE_ARRAY_DEG * Math.sin(ph));
            tp = (now / 10L) % 2L == 0L ? 3.0F : -3.0F;
        }
        float rate = !Float.isNaN(be.tYaw) ? 20.0F : 12.0F;
        be.yaw += Mth.clamp(ty - be.yaw, -rate, rate);
        be.pitch += Mth.clamp(tp - be.pitch, -rate, rate);
        be.lid += Mth.clamp((be.open ? 1.0F : 0.0F) - be.lid, -1.0F / 24.0F, 1.0F / 24.0F);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float y = prevYaw + (yaw - prevYaw) * pt, p = prevPitch + (pitch - prevPitch) * pt;
        float l = prevLid + (lid - prevLid) * pt;
        final float e = l < 0.5F ? 2 * l * l : 1 - (float) Math.pow(-2 * l + 2, 2) / 2;
        model.getBone("yaw").ifPresent(b -> b.setRotY(CcSpecS52.CBR_YAW_SIGN * (float) Math.toRadians(y)));
        model.getBone("pitch").ifPresent(b -> b.setRotX(CcSpecS52.CBR_TILT_SIGN * (float) Math.toRadians(p)));
        model.getBone("radome").ifPresent(b -> b.setRotY(CcSpecS52.CBR_YAW_SIGN * (float) Math.toRadians(-100.0 * e)));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean ring = !dark && isRunning();
        model.getBone("ring").ifPresent(b -> b.setHidden(!ring));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }
    @Override protected String detail() { return (off ? "switched off" : powered ? "searching, " + inCone.size() + " in the cone" : "no power core charge") + (flush ? ", flush cap" : ", ogive"); }

    @Override
    public Component status() {
        return Component.translatable("message.wmd.nose_radar.status", Component.translatable(off ? "message.wmd.nose_radar.off" : powered ? "message.wmd.nose_radar.on" : "message.wmd.nose_radar.nopower"),
                inCone.size());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        ownerData.write(tag);
        copycat.save(tag, r);
        tag.putBoolean("nrOff", off);
        tag.putBoolean("nrFlush", flush);
        tag.putBoolean("nrOpen", open);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        ownerData.read(tag);
        copycat.load(tag, r, level != null && level.isClientSide);
        off = tag.getBoolean("nrOff");
        flush = tag.getBoolean("nrFlush");
        open = tag.getBoolean("nrOpen");
        if (tag.contains("nrTy")) { tYaw = tag.getFloat("nrTy"); tPitch = tag.getFloat("nrTp"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        CompoundTag tag = super.getUpdateTag(r);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("nrOff", off);
        tag.putBoolean("nrFlush", flush);
        tag.putBoolean("nrOpen", open);
        tag.putFloat("nrTy", tYaw);
        tag.putFloat("nrTp", tPitch);
        return tag;
    }
}
