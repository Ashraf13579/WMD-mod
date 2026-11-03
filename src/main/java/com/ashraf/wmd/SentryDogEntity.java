package com.ashraf.wmd;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SentryDogEntity extends PathfinderMob {

    public static final int FOLLOW = 0, GUARD = 1;
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sentry_dog"));

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(SentryDogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(SentryDogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(SentryDogEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH = SynchedEntityData.defineId(SentryDogEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(SentryDogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIRING = SynchedEntityData.defineId(SentryDogEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ASLEEP = SynchedEntityData.defineId(SentryDogEntity.class, EntityDataSerializers.BOOLEAN);

    static final float TURRET_Y = 15.6F, GUN_Y = 18.8F, GUN_Z = 1.0F, MUZZLE_Z = -12.4F;

    private UUID owner;
    private boolean radioJammed;
    private String ownerName = "";
    private BlockPos guardPos;

    private long burstSince = -1, lastSeen, lastHurt, firingUntil;
    private float pending;
    private int drain;

    float shownYaw, shownPitch, spin, spinSpeed;

    public SentryDogEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, Balance.SD_HEALTH).add(Attributes.ARMOR, Balance.SD_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, Balance.SD_SPEED).add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(MODE, FOLLOW);
        b.define(CHARGE, Balance.SD_CHARGE_MAX);
        b.define(AIM_YAW, 0.0F);
        b.define(AIM_PITCH, 0.0F);
        b.define(TARGET, -1);
        b.define(FIRING, false);
        b.define(ASLEEP, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FollowOwnerGoal());
        goalSelector.addGoal(1, new HoldGuardGoal());
        goalSelector.addGoal(5, new RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return awake() && super.canUse(); }
        });
    }

    public void setup(Player p, int charge) {
        owner = p.getUUID();
        ownerName = p.getGameProfile().getName();
        entityData.set(CHARGE, Mth.clamp(charge, 0, Balance.SD_CHARGE_MAX));
    }

    public int mode() { return entityData.get(MODE); }
    public int charge() { return entityData.get(CHARGE); }
    public float aimYaw() { return entityData.get(AIM_YAW); }
    public float aimPitch() { return entityData.get(AIM_PITCH); }
    public boolean firing() { return entityData.get(FIRING); }
    public boolean asleep() { return entityData.get(ASLEEP); }
    public boolean locked() { return entityData.get(TARGET) >= 0; }
    public UUID owner() { return owner; }

    boolean awake() { return !asleep(); }

    private Player ownerPlayer() { return owner == null ? null : level().getPlayerByUUID(owner); }

    private final class FollowOwnerGoal extends Goal {
        FollowOwnerGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            Player o = ownerPlayer();
            return awake() && mode() == FOLLOW && o != null && !o.isSpectator() && distanceToSqr(o) > 16.0;
        }

        @Override public boolean canContinueToUse() { return canUse() && distanceToSqr(ownerPlayer()) > 9.0; }

        @Override
        public void tick() {
            Player o = ownerPlayer();
            if (o == null) return;
            getLookControl().setLookAt(o, 10.0F, getMaxHeadXRot());
            if (distanceToSqr(o) > (double) Balance.SD_TELEPORT * Balance.SD_TELEPORT && o.onGround()) {
                moveTo(o.getX(), o.getY(), o.getZ(), getYRot(), getXRot());
                getNavigation().stop();
                return;
            }
            if (tickCount % 10 == 0) getNavigation().moveTo(o, 1.1);
        }

        @Override public void stop() { getNavigation().stop(); }
    }

    private final class HoldGuardGoal extends Goal {
        HoldGuardGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() { return awake() && mode() == GUARD && guardPos != null && distanceToSqr(Vec3.atBottomCenterOf(guardPos)) > 4.0; }

        @Override
        public void tick() {
            if (tickCount % 10 == 0) getNavigation().moveTo(guardPos.getX() + 0.5, guardPos.getY(), guardPos.getZ() + 0.5, 1.0);
        }

        @Override public void stop() { getNavigation().stop(); }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { clientTick(); return; }
        if (!(level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        boolean jammed = EmpField.jammed(sl, blockPosition());
        boolean sleep = jammed || charge() <= 0;
        if (sleep != asleep()) {
            entityData.set(ASLEEP, sleep);
            sl.playSound(null, getX(), getY() + 0.8, getZ(), sleep ? WmdSounds.SD_SLEEP.get() : WmdSounds.SD_WAKE.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
            if (sleep) { getNavigation().stop(); dropTarget(); }
        }
        if (sleep) {
            entityData.set(AIM_PITCH, Math.max(-14.0F, aimPitch() - 1.0F));
            return;
        }
        if (++drain >= Balance.SD_DRAIN_TICKS) { drain = 0; useCharge(1); }
        if (now % 10 == 0) radioJammed = PortableJammerBlockEntity.jams(sl, blockPosition(), owner);
        if (radioJammed) { if (entityData.get(TARGET) >= 0) dropTarget(); return; }
        gun(sl, now);
    }

    private void useCharge(int n) { entityData.set(CHARGE, Math.max(0, charge() - n)); }

    private void dropTarget() {
        entityData.set(TARGET, -1);
        entityData.set(FIRING, false);
        burstSince = -1;
        pending = 0;
    }

    private Vec3 gunPos() { return position().add(0.0, GUN_Y / 16.0, 0.0); }

    private static Vec3 aimPoint(LivingEntity e) { return e.position().add(0.0, e.getBbHeight() * 0.6, 0.0); }

    private boolean sees(ServerLevel sl, Vec3 from, LivingEntity e) {
        return sl.clip(new ClipContext(from, aimPoint(e), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private boolean hostile(ServerLevel sl, LivingEntity e) {
        if (e == this || e instanceof SentryDogEntity) return false;
        if (e instanceof Player p && p.getUUID().equals(owner)) return false;
        return DefenceTargets.hostile(sl, asOwned, e);
    }

    private final SecurityData noList = new SecurityData();
    private final Owned asOwned = new Owned() {
        @Override public UUID getOwner() { return owner; }
        @Override public String getOwnerName() { return ownerName; }
        @Override public void claim(Player player) {}
        @Override public SecurityData security() { return noList; }
    };

    private void gun(ServerLevel sl, long now) {
        Vec3 gun = gunPos();
        Entity cur = entityData.get(TARGET) >= 0 ? sl.getEntity(entityData.get(TARGET)) : null;
        LivingEntity tgt = cur instanceof LivingEntity le && hostile(sl, le) && le.distanceToSqr(gun) <= (Balance.SD_RANGE + 4.0) * (Balance.SD_RANGE + 4.0) ? le : null;
        boolean see = false;
        if (tgt != null) see = now % 3 == 0 ? sees(sl, gun, tgt) : now - lastSeen < 3;
        if (now % 5 == 0 && (tgt == null || !see)) {
            LivingEntity best = null;
            double bd = (double) Balance.SD_RANGE * Balance.SD_RANGE;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(gun, gun).inflate(Balance.SD_RANGE), e -> hostile(sl, e))) {
                double d = e.distanceToSqr(gun);
                if (d < bd && sees(sl, gun, e)) { bd = d; best = e; }
            }
            if (best != null) { tgt = best; see = true; }
        }
        if (see) lastSeen = now;
        if (tgt == null || now - lastSeen > 40) {
            if (entityData.get(TARGET) >= 0) dropTarget();
            entityData.set(AIM_YAW, aimYaw() + Mth.clamp(Mth.wrapDegrees(0.0F - aimYaw()), -4.0F, 4.0F));
            entityData.set(AIM_PITCH, aimPitch() + Mth.clamp(0.0F - aimPitch(), -2.0F, 2.0F));
            return;
        }
        if (entityData.get(TARGET) != tgt.getId()) {
            entityData.set(TARGET, tgt.getId()); burstSince = -1;
            sl.playSound(null, getX(), getY() + 1.0, getZ(), WmdSounds.SD_ALERT.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        Vec3 aim = aimPoint(tgt), d = aim.subtract(gun);
        float wantYaw = Mth.wrapDegrees(yBodyRot - (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG - 90.0));
        float wantPitch = (float) Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -20.0, 60.0);
        float dy = Mth.wrapDegrees(wantYaw - aimYaw()), dp = wantPitch - aimPitch();
        entityData.set(AIM_YAW, Mth.wrapDegrees(aimYaw() + Mth.clamp(dy, -Balance.SD_TURN, Balance.SD_TURN)));
        entityData.set(AIM_PITCH, aimPitch() + Mth.clamp(dp, -Balance.SD_TURN, Balance.SD_TURN));
        boolean aligned = Math.abs(dy) < 6.0F && Math.abs(dp) < 6.0F;
        if (burstSince < 0 && aligned && see) {
            burstSince = now;
            sl.playSound(null, getX(), getY() + 1.2, getZ(), WmdSounds.SD_BURST.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        }
        boolean shooting = false;
        if (burstSince >= 0) {
            long phase = (now - burstSince) % (Balance.SD_BURST + Balance.SD_PAUSE);
            shooting = phase < Balance.SD_BURST && see && aligned;
            if (phase == 0 && now > burstSince) sl.playSound(null, getX(), getY() + 1.2, getZ(), WmdSounds.SD_BURST.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
            if (shooting && now % 2 == 0) {
                double dist = d.length();
                double q = Math.max(0.0, 1.0 - dist / Balance.SD_RANGE);
                double chance = Balance.SD_HIT_FAR + (Balance.SD_HIT_CLOSE - Balance.SD_HIT_FAR) * q;
                if (tgt.isCrouching()) chance *= 0.75;
                if (sl.random.nextDouble() < chance) pending += Balance.SD_DAMAGE;
                useCharge(1);
                firingUntil = now + 4;
            }
        }
        entityData.set(FIRING, now < firingUntil);
        if (pending > 0 && now - lastHurt >= 10) {
            Holder<DamageType> type = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
            tgt.hurt(new DamageSource(type, this), pending);
            pending = 0;
            lastHurt = now;
        }
    }

    public Vec3 muzzleWorld(float bodyYaw, float yaw, float pitch) {
        double p = Math.toRadians(pitch), t = Math.toRadians(yaw), b = Math.toRadians(180.0 - bodyYaw);

        double dz = MUZZLE_Z - GUN_Z;
        double my = -dz * Math.sin(p), mz = dz * Math.cos(p);

        double x = mz * Math.sin(t), z = GUN_Z + mz * Math.cos(t);

        double wx = x * Math.cos(b) + z * Math.sin(b), wz = -x * Math.sin(b) + z * Math.cos(b);
        return new Vec3(getX() + wx / 16.0, getY() + (GUN_Y + my) / 16.0, getZ() + wz / 16.0);
    }

    private static final DustParticleOptions TRACER = new DustParticleOptions(new org.joml.Vector3f(1.0F, 0.85F, 0.35F), 0.6F);

    private void clientTick() {
        shownYaw += Mth.wrapDegrees(aimYaw() - shownYaw) * 0.5F;
        shownPitch += (aimPitch() - shownPitch) * 0.5F;
        float want = firing() ? 60.0F : locked() ? 20.0F : 0.0F;
        spinSpeed += (want - spinSpeed) * 0.15F;
        spin = (spin + spinSpeed) % 360.0F;
        if (!firing() || tickCount % 2 != 0) return;
        Entity t = level().getEntity(entityData.get(TARGET));
        Vec3 m = muzzleWorld(yBodyRot, shownYaw, shownPitch);
        var r = level().random;
        level().addParticle(ParticleTypes.SMALL_FLAME, m.x, m.y, m.z, 0, 0.01, 0);
        if (tickCount % 6 == 0) level().addParticle(ParticleTypes.SMOKE, m.x, m.y + 0.05, m.z, 0, 0.02, 0);
        if (t == null) return;
        Vec3 to = t.position().add((r.nextDouble() - 0.5) * 0.8, t.getBbHeight() * (0.3 + r.nextDouble() * 0.5), (r.nextDouble() - 0.5) * 0.8);
        Vec3 d = to.subtract(m);
        for (int i = 1; i <= 8; i++) {
            double f = (i + r.nextDouble()) / 9.0;
            level().addParticle(TRACER, m.x + d.x * f, m.y + d.y * f, m.z + d.z * f, 0, 0, 0);
        }
        level().addParticle(ParticleTypes.CRIT, to.x, to.y, to.z, 0, 0.05, 0);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.wmd.sentry_dog.not_yours", ownerName), true);
            return InteractionResult.FAIL;
        }
        ItemStack held = player.getItemInHand(hand);
        int add = held.is(Items.REDSTONE) ? Balance.SD_CHARGE_DUST : held.is(Items.REDSTONE_BLOCK) ? Balance.SD_CHARGE_BLOCK : 0;
        if (add > 0) {
            if (charge() >= Balance.SD_CHARGE_MAX) { player.displayClientMessage(Component.translatable("message.wmd.sentry_dog.full"), true); return InteractionResult.SUCCESS; }
            entityData.set(CHARGE, Math.min(Balance.SD_CHARGE_MAX, charge() + add));
            if (!player.getAbilities().instabuild) held.shrink(1);
            level().playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.6F, 1.6F);
            player.displayClientMessage(Component.translatable("message.wmd.sentry_dog.charge", charge() * 100 / Balance.SD_CHARGE_MAX), true);
            return InteractionResult.SUCCESS;
        }
        if (!held.isEmpty()) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            ItemStack item = SentryDogItem.withCharge(charge());
            if (!player.getInventory().add(item)) spawnAtLocation(item);
            level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.7F, 1.4F);
            discard();
            return InteractionResult.SUCCESS;
        }
        if (mode() == FOLLOW) {
            entityData.set(MODE, GUARD);
            guardPos = blockPosition();
            getNavigation().stop();
        } else {
            entityData.set(MODE, FOLLOW);
            guardPos = null;
        }
        level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 0.8F, mode() == GUARD ? 0.8F : 1.4F);
        player.displayClientMessage(Component.translatable(mode() == GUARD ? "message.wmd.sentry_dog.guard" : "message.wmd.sentry_dog.follow", charge() * 100 / Balance.SD_CHARGE_MAX), true);
        return InteractionResult.SUCCESS;
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putInt("mode", mode());
        tag.putInt("charge", charge());
        if (guardPos != null) tag.put("guard", NbtUtils.writeBlockPos(guardPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        entityData.set(MODE, tag.getInt("mode"));
        entityData.set(CHARGE, tag.contains("charge") ? tag.getInt("charge") : Balance.SD_CHARGE_MAX);
        guardPos = NbtUtils.readBlockPos(tag, "guard").orElse(null);
    }
}
