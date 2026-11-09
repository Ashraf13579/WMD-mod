package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FloodlightBlockEntity extends CctvDeviceEntity {

    static final float PAN_Y = 6.0F, TILT_Y = 10.6F, LENS_Z = -3.6F;

    private int target = -1;
    private long cueUntil;
    private boolean on;

    public FloodlightBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FLOODLIGHT_BE.get(), pos, state, 0);
    }

    public void cue(LivingEntity e, long now) {
        if (!(level instanceof ServerLevel sl) || !DefenceTargets.hostile(sl, this, e)) return;
        if (target != e.getId()) { target = e.getId(); sync(); }
        cueUntil = now + Balance.CCTV_CUE_TICKS;
    }

    @Override
    public void deviceTick(ServerLevel sl, long now) {
        if (now % 10 != 0) return;
        if (target >= 0 && (now >= cueUntil || !(sl.getEntity(target) instanceof LivingEntity le) || !le.isAlive())) { target = -1; sync(); }
        BlockPos up = worldPosition.above();
        boolean dark = !sl.isDay() || sl.getRawBrightness(up, sl.getSkyDarken()) < 8;
        boolean want = isRunning() && (target >= 0 || dark);
        if (want != on) { on = want; sync(); if (on) sl.playSound(null, worldPosition, WmdSounds.FLOOD_ON.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F); }
    }

    private float shownYaw, shownPitch, prevYaw, prevPitch;
    private Vec3 beamEnd;

    public boolean onShown() { return on; }
    public float yaw(float pt) { return prevYaw + Mth.wrapDegrees(shownYaw - prevYaw) * pt; }
    public float pitch(float pt) { return Mth.lerp(pt, prevPitch, shownPitch); }
    public Vec3 beamEnd() { return beamEnd; }

    public Vec3 pivot() { return modelPoint(0.0, TILT_Y, 0.0); }

    public Vec3 dir(float pan, float tilt) {
        float worldYaw = facing().toYRot() - pan;
        return Vec3.directionFromRotation(-tilt, worldYaw);
    }

    @Override
    public void deviceClient(Level level) {
        prevYaw = shownYaw;
        prevPitch = shownPitch;
        float wy, wp, turn = 3.0F;
        Entity t = target >= 0 ? level.getEntity(target) : null;
        if (on && t != null) {
            Vec3 d = t.position().add(0.0, t.getBbHeight() * 0.5, 0.0).subtract(pivot());
            float worldYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
            wy = Mth.wrapDegrees(facing().toYRot() - worldYaw);
            wp = (float) Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -45.0, 40.0);
            turn = 8.0F;
        } else if (on) {
            wy = 50.0F * Mth.sin(level.getGameTime() * 0.01F + (worldPosition.asLong() & 63L));
            wp = -12.0F;
        } else { wy = 0.0F; wp = -20.0F; }
        shownYaw = Mth.wrapDegrees(shownYaw + Mth.clamp(Mth.wrapDegrees(wy - shownYaw), -turn, turn));
        shownPitch += Mth.clamp(wp - shownPitch, -turn, turn);
        if (Math.abs(Mth.wrapDegrees(shownYaw - prevYaw)) > 90.0F) prevYaw = shownYaw;
        if (!on) { beamEnd = null; return; }
        Vec3 from = pivot(), to = from.add(dir(shownYaw, shownPitch).scale(Balance.FLOOD_LEN));
        if (level.getGameTime() % 3 == 0 || beamEnd == null) {
            BlockHitResult h = level.clip(new ClipContext(from.add(dir(shownYaw, shownPitch).scale(0.5)), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
            beamEnd = h.getType() == HitResult.Type.BLOCK ? h.getLocation() : to;
        }
        Vec3 spot = beamEnd.subtract(dir(shownYaw, shownPitch).scale(0.6));
        VeilBridge.machineGlow(worldPosition.asLong() * 31L + 7L, spot.x, spot.y, spot.z, 1.0F, 0.95F, 0.82F, 7.0F, 1.3F, level.getGameTime());
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable(target >= 0 ? "gui.wmd.info.flood.tracking" : on ? "gui.wmd.info.flood.sweep" : "gui.wmd.info.flood.off"),
                Component.translatable("gui.wmd.info.flood.how", Balance.CCTV_CUE));
    }

    @Override protected String detail() { return target >= 0 ? "TRACKING" : on ? "ON" : "OFF"; }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tgt")) target = tag.getInt("tgt");
        if (tag.contains("on")) on = tag.getBoolean("on");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("tgt", target);
        tag.putBoolean("on", on);
        return tag;
    }
}
