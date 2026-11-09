package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SentryTurretBlockEntity extends ProcessUnitBlockEntity implements Owned {

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sentry_turret"));
    public static final float YAW_Y = 14.0F, PIVOT_Z = 6.0F, PITCH_Y = 21.0F, MUZZLE_Z = -8.6F;
    private static final float PITCH_MIN = -15.0F, PITCH_MAX = 60.0F;

    private final OwnerData ownerData = new OwnerData();

    private float aimYaw, aimPitch;
    private int targetId = -1;
    private boolean firing, blinded, jammed;
    private long burstSince = -1, lastSeen, lastHurt, firingUntil, cueUntil;
    private int cueId = -1;
    private float pending;

    private float shownYaw, shownPitch, prevYaw, prevPitch;
    private int clientTarget = -1;
    private boolean clientFiring;
    private int clientTicks;

    public SentryTurretBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SENTRY_TURRET_BE.get(), pos, state, Balance.ST_FE_PER_TICK);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    @Override
    public void onLoad() { super.onLoad(); CctvNet.join(this); }

    @Override
    public void setRemoved() { super.setRemoved(); CctvNet.leave(this); }

    public void cue(LivingEntity e) {
        if (!(level instanceof ServerLevel sl) || !DefenceTargets.hostile(sl, this, e)) return;
        cueId = e.getId();
        cueUntil = sl.getGameTime() + 200;
    }

    private float facingYaw() { return facing().toYRot(); }

    private static double[] gunPoint(float yaw, float pitch, double dz) {
        double p = Math.toRadians(pitch), t = Math.toRadians(yaw);
        double my = -dz * Math.sin(p), mz = dz * Math.cos(p);
        double x = mz * Math.sin(t), z = mz * Math.cos(t);
        return new double[] { x / 16.0, (PITCH_Y + my) / 16.0, (PIVOT_Z + z) / 16.0 };
    }

    private Vec3 pivotWorld() { double[] w = worldPoint(0.0, PITCH_Y / 16.0, PIVOT_Z / 16.0); return new Vec3(w[0], w[1], w[2]); }

    public Vec3 muzzleWorld(float yaw, float pitch) {
        double[] l = gunPoint(yaw, pitch, MUZZLE_Z - PIVOT_Z);
        double[] w = worldPoint(l[0], l[1], l[2]);
        return new Vec3(w[0], w[1], w[2]);
    }

    private static Vec3 aimPoint(LivingEntity e) { return e.position().add(0.0, e.getBbHeight() * 0.6, 0.0); }

    private float[] want(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        float worldYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.wrapDegrees(facingYaw() - worldYaw);
        float pitch = (float) Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), PITCH_MIN, PITCH_MAX);
        return new float[] { yaw, pitch };
    }

    private boolean sees(ServerLevel sl, Vec3 from, LivingEntity e) {
        Vec3 to = aimPoint(e);
        java.util.Set<BlockPos> own = ownCells();
        net.minecraft.world.phys.BlockHitResult hit = net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, sl, (lv, pos) -> {
            if (own.contains(pos)) return null;
            net.minecraft.world.level.block.state.BlockState st = lv.getBlockState(pos);
            return lv.clipWithInteractionOverride(from, to, pos, st.getCollisionShape(lv, pos), st);
        }, lv -> null);
        if (hit != null && hit.getType() != HitResult.Type.MISS) return false;
        return SmokeScreens.entry(sl, from, to) < 0.0;
    }

    private java.util.Set<BlockPos> ownCellsCache;
    private BlockState ownCellsFor;

    private java.util.Set<BlockPos> ownCells() {
        if (ownCellsCache != null && ownCellsFor == getBlockState()) return ownCellsCache;
        java.util.Set<BlockPos> s = new java.util.HashSet<>();
        s.add(worldPosition);
        if (getBlockState().getBlock() instanceof MachineBlock mb)
            for (BlockPos l : mb.footprint()) s.add(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()));
        ownCellsFor = getBlockState();
        return ownCellsCache = s;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (now % 10 == 0) jammed = PortableJammerBlockEntity.jams(sl, worldPosition, getOwner());
        if (jammed) { setTarget(-1, false); burstSince = -1; return false; }
        Vec3 gun = pivotWorld();
        double reach = Balance.ST_RANGE + 4.0;
        Entity cur = targetId >= 0 ? sl.getEntity(targetId) : null;
        LivingEntity tgt = cur instanceof LivingEntity le && DefenceTargets.hostile(sl, this, le) && le.distanceToSqr(gun) <= reach * reach ? le : null;
        if (now < cueUntil && sl.getEntity(cueId) instanceof LivingEntity cue && DefenceTargets.hostile(sl, this, cue) && cue.distanceToSqr(gun) <= reach * reach)
            tgt = cue;
        boolean see = false;
        if (tgt != null) see = now % 3 == 0 ? sees(sl, gun, tgt) : now - lastSeen < 3;
        if (now % 5 == 0 && (tgt == null || !see)) {
            LivingEntity best = null;
            double bd = (double) Balance.ST_RANGE * Balance.ST_RANGE;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(gun, gun).inflate(Balance.ST_RANGE), e -> DefenceTargets.hostile(sl, this, e))) {
                double d = e.distanceToSqr(gun);
                if (d < bd && sees(sl, gun, e)) { bd = d; best = e; }
            }
            if (best != null) { tgt = best; see = true; }
        }
        if (see) lastSeen = now;
        blinded = tgt != null && !see && SmokeScreens.entry(sl, gun, aimPoint(tgt)) >= 0.0;
        if (tgt == null || now - lastSeen > 40) {
            setTarget(-1, false);
            aimYaw += Mth.clamp(Mth.wrapDegrees(0.0F - aimYaw), -4.0F, 4.0F);
            aimPitch += Mth.clamp(0.0F - aimPitch, -2.0F, 2.0F);
            burstSince = -1;
            pending = 0;
            return true;
        }
        if (targetId != tgt.getId()) { burstSince = -1; pending = 0; sl.playSound(null, worldPosition, WmdSounds.ST_ALERT.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F); }
        float[] w = want(gun, aimPoint(tgt));
        float dy = Mth.wrapDegrees(w[0] - aimYaw), dp = w[1] - aimPitch;
        aimYaw = Mth.wrapDegrees(aimYaw + Mth.clamp(dy, -Balance.ST_TURN, Balance.ST_TURN));
        aimPitch += Mth.clamp(dp, -Balance.ST_TURN, Balance.ST_TURN);
        boolean aligned = Math.abs(dy) < 5.0F && Math.abs(dp) < 5.0F;
        Vec3 muzzle = muzzleWorld(aimYaw, aimPitch);
        if (burstSince < 0 && aligned && see) {
            burstSince = now;
            sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, WmdSounds.ST_BURST.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
        }
        boolean shooting = false;
        if (burstSince >= 0) {
            long phase = (now - burstSince) % (Balance.ST_BURST + Balance.ST_PAUSE);
            shooting = phase < Balance.ST_BURST && see && aligned;
            if (phase == 0 && now > burstSince && shooting) sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, WmdSounds.ST_BURST.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
            if (shooting && now % 2 == 0 && useExtraEnergy(Balance.ST_FE_PER_VOLLEY)) {
                double q = Math.max(0.0, 1.0 - gun.distanceTo(aimPoint(tgt)) / Balance.ST_RANGE);
                double chance = Balance.ST_HIT_FAR + (Balance.ST_HIT_CLOSE - Balance.ST_HIT_FAR) * q;
                if (tgt.isCrouching()) chance *= 0.75;
                if (sl.random.nextDouble() < chance) pending += Balance.ST_DAMAGE;
                firingUntil = now + 4;
            }
        }
        if (pending > 0 && now - lastHurt >= 10) {
            Holder<DamageType> type = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
            tgt.hurt(new DamageSource(type, muzzle), pending);
            pending = 0;
            lastHurt = now;
        }
        setTarget(tgt.getId(), now < firingUntil);
        return true;
    }

    @Override
    protected void idle() {
        setTarget(-1, false);
        burstSince = -1;
        pending = 0;
    }

    private void setTarget(int id, boolean fire) {
        if (id == targetId && fire == firing) return;
        targetId = id;
        firing = fire;
        sync();
    }

    public static void clientTickTurret(Level level, BlockPos pos, BlockState state, ProcessUnitBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (be instanceof SentryTurretBlockEntity t) t.clientTurret(level);
    }

    private void clientTurret(Level level) {
        clientTicks++;
        prevYaw = shownYaw;
        prevPitch = shownPitch;
        Entity t = clientTarget >= 0 ? level.getEntity(clientTarget) : null;
        float wy = 0.0F, wp = 0.0F, turn = 4.0F;
        if (t instanceof LivingEntity le && isRunning()) {
            float[] w = want(pivotWorld(), aimPoint(le));
            wy = w[0]; wp = w[1]; turn = Balance.ST_TURN;
        }
        shownYaw = Mth.wrapDegrees(shownYaw + Mth.clamp(Mth.wrapDegrees(wy - shownYaw), -turn, turn));
        shownPitch += Mth.clamp(wp - shownPitch, -turn, turn);
        if (Math.abs(Mth.wrapDegrees(shownYaw - prevYaw)) > 90.0F) prevYaw = shownYaw;
        if (!clientFiring || !isRunning() || clientTicks % 2 != 0) return;
        Vec3 m = muzzleWorld(shownYaw, shownPitch);
        var r = level.random;

        VeilBridge.glow(0x5E47_0000_0000_0000L ^ worldPosition.asLong(), m.x, m.y, m.z, 1.0F, 0.72F, 0.3F, 3.0F, 0.9F, level.getGameTime());
        if (clientTicks % 6 == 0) level.addParticle(ParticleTypes.SMOKE, m.x, m.y + 0.05, m.z, 0, 0.03, 0);
        if (t == null) return;
        Vec3 to = t.position().add((r.nextDouble() - 0.5) * 0.8, t.getBbHeight() * (0.3 + r.nextDouble() * 0.5), (r.nextDouble() - 0.5) * 0.8);
        ClientCiws.smallRound(level, m, to, (clientTicks / 2) % ClientCiws.SMALL_TRACER_EVERY == 0);
    }

    public float yaw(float pt) { return prevYaw + Mth.wrapDegrees(shownYaw - prevYaw) * pt; }
    public float pitch(float pt) { return Mth.lerp(pt, prevPitch, shownPitch); }
    public boolean firingShown() { return clientFiring && isRunning(); }
    public boolean trackingShown() { return clientTarget >= 0; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected SoundEvent runSound() { return WmdSounds.ST_IDLE_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }
    @Override protected float soundPitch() { return 1.4F; }

    @Override
    protected String detail() { return targetId >= 0 ? (firing ? "FIRING" : "TRACKING") : blinded ? "SMOKE" : "SEARCHING"; }

    @Override
    public List<Component> guiInfo() {
        String state = targetId >= 0 ? (firing ? "firing" : "tracking") : blinded ? "smoke" : "searching";
        return List.of(Component.translatable("gui.wmd.info.sentry_turret." + state),
                Component.translatable("gui.wmd.info.sentry_turret.range", Balance.ST_RANGE, String.format("%,d", Balance.ST_FE_PER_VOLLEY * 10)),
                Component.translatable("gui.wmd.info.sentry_turret.spares", getOwnerName() == null || getOwnerName().isEmpty() ? "-" : getOwnerName()));
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(0, 0, 2).power(0, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("tgt")) clientTarget = tag.getInt("tgt");
        if (tag.contains("fire")) clientFiring = tag.getBoolean("fire");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("tgt", targetId);
        tag.putBoolean("fire", firing);
        ownerData.write(tag);
        return tag;
    }
}
