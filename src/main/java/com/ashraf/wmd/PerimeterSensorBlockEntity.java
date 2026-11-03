package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class PerimeterSensorBlockEntity extends CctvDeviceEntity {

    static final float LENS_Y = 13.4F, LENS_Z = -2.7F;

    private BlockPos partner;
    private long trippedUntil;
    private boolean tripped;
    private boolean jammed;

    public PerimeterSensorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.PERIMETER_SENSOR_BE.get(), pos, state, 0);
    }

    public Vec3 lens() { return modelPoint(0.0, LENS_Y, LENS_Z); }
    public Vec3 front() { Direction d = Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.NORTH); return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }
    public BlockPos partner() { return partner; }
    public boolean trippedShown() { return tripped; }

    private BlockPos findPartner(ServerLevel sl) {
        Direction d = Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.NORTH);
        for (int i = 1; i <= Balance.SENSOR_BEAM; i++) {
            BlockPos p = worldPosition.relative(d, i);
            if (!sl.isLoaded(p)) return null;
            if (sl.getBlockEntity(p) instanceof PerimeterSensorBlockEntity o) return getOwner() != null && getOwner().equals(o.getOwner()) ? p : null;
            if (sl.getBlockState(p).isCollisionShapeFullBlock(sl, p)) return null;
        }
        return null;
    }

    @Override
    public void deviceTick(ServerLevel sl, long now) {
        if ((now + (worldPosition.asLong() & 31L)) % 40 == 0) {
            BlockPos p = findPartner(sl);
            if (!java.util.Objects.equals(p, partner)) { partner = p; setChanged(); sync(); }
        }
        boolean t = now < trippedUntil;
        if (t != tripped) { tripped = t; sync(); }
        if (!isRunning() || (now + worldPosition.asLong()) % 4 != 0) return;
        if ((now + worldPosition.asLong()) % 20 == 0) jammed = PortableJammerBlockEntity.jams(sl, worldPosition, getOwner());
        if (jammed) return;
        Vec3 a = lens();
        Vec3 b = partner != null && sl.getBlockEntity(partner) instanceof PerimeterSensorBlockEntity o ? o.lens() : null;
        AABB box = b != null ? new AABB(a, b).inflate(0.4) : new AABB(a, a).inflate(Balance.SENSOR_CONE);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> DefenceTargets.hostile(sl, this, e))) {
            if (!sees(sl, a, b, e)) continue;
            if (e instanceof Player p && p.isCrouching() && Math.sqrt(e.distanceToSqr(worldPosition.getCenter())) > Balance.SENSOR_SNEAK
                    && (partner == null || Math.sqrt(e.distanceToSqr(partner.getCenter())) > Balance.SENSOR_SNEAK)) continue;
            trip(sl, e, now);
            return;
        }
    }

    private static Vec3 mid(LivingEntity e) { return e.position().add(0.0, e.getBbHeight() * 0.5, 0.0); }

    private boolean sees(ServerLevel sl, Vec3 a, Vec3 b, LivingEntity e) {
        if (b != null) return e.getBoundingBox().inflate(0.2).clip(a, b).isPresent() || e.getBoundingBox().contains(a);
        Vec3 to = mid(e), d = to.subtract(a);
        double len = d.length();
        if (len > Balance.SENSOR_CONE || len < 1e-3 || d.dot(front()) / len < Math.cos(Math.toRadians(Balance.SENSOR_CONE_DEG))) return false;
        return sl.clip(new ClipContext(a.add(d.scale(0.4 / len)), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null)).getType() == HitResult.Type.MISS;
    }

    private void trip(ServerLevel sl, LivingEntity e, long now) {
        boolean fresh = now >= trippedUntil;
        trippedUntil = now + 60;
        if (fresh) sl.playSound(null, worldPosition, WmdSounds.SENSOR_TRIP.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        Vec3 at = mid(e);
        for (FloodlightBlockEntity f : CctvNet.near(sl, FloodlightBlockEntity.class, at, Balance.CCTV_CUE))
            if (f.getOwner() != null && f.getOwner().equals(getOwner())) f.cue(e, now);
        for (SentryTurretBlockEntity t : CctvNet.near(sl, SentryTurretBlockEntity.class, at, Balance.CCTV_CUE))
            if (t.getOwner() != null && t.getOwner().equals(getOwner())) t.cue(e);
        for (CcSecurityMonitorBlockEntity m : CctvNet.all(sl, CcSecurityMonitorBlockEntity.class))
            if (m.getOwner() != null && m.getOwner().equals(getOwner())) m.alert(at, now);
        if (e instanceof Player) CcAlarms.intruder(this, this, e);
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable(partner != null ? "gui.wmd.info.sensor.beam" : "gui.wmd.info.sensor.cone",
                        partner != null ? (int) Math.round(Math.sqrt(partner.distSqr(worldPosition))) : Balance.SENSOR_CONE),
                Component.translatable("gui.wmd.info.sensor.sneak", Balance.SENSOR_SNEAK));
    }

    @Override protected String detail() { return partner != null ? "beam to " + partner.toShortString() : "motion cone"; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (partner != null) tag.putLong("partner", partner.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        partner = tag.contains("partner") ? BlockPos.of(tag.getLong("partner")) : null;
        if (tag.contains("trip")) tripped = tag.getBoolean("trip");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (partner != null) tag.putLong("partner", partner.asLong());
        tag.putBoolean("trip", tripped);
        return tag;
    }
}
