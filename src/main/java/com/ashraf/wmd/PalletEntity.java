package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PalletEntity extends Entity implements CargoPalletMenu.Opener {

    public static final int ROLL = 0, AIR = 1, LANDED = 2;
    private static final EntityDataAccessor<Boolean> HEAVY = SynchedEntityData.defineId(PalletEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(PalletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHUTE = SynchedEntityData.defineId(PalletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LANDED_AT = SynchedEntityData.defineId(PalletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(PalletEntity.class, EntityDataSerializers.FLOAT);

    private SimpleContainer store = new SimpleContainer(Balance.PALLET_SLOTS[0]);

    private BlockPos ramp;
    private double a, u, v;
    private int rollTicks, airTicks;
    private UUID owner;
    private Vec3 last;

    public PalletEntity(EntityType<? extends PalletEntity> type, Level level) {
        super(type, level);
    }

    public boolean heavy() { return entityData.get(HEAVY); }

    public double mass() {
        int n = 0;
        for (int i = 0; i < store.getContainerSize(); i++) if (!store.getItem(i).isEmpty()) n++;
        return Balance.PALLET_MASS[heavy() ? 1 : 0] + n * Balance.PALLET_MASS_STACK;
    }
    public int phase() { return entityData.get(PHASE); }
    public float rollPitch() { return entityData.get(PITCH); }
    int landedAt() { return entityData.get(LANDED_AT); }

    public float chuteOpen(float pt) {
        int c = entityData.get(CHUTE);
        if (c < 0 || phase() == LANDED) return 0.0F;
        return Math.min(1.0F, (tickCount + pt - c) / (float) Balance.PALLET_CHUTE_OPEN_TICKS);
    }

    void startRoll(CargoRampBlockEntity r, boolean heavy, List<ItemStack> items, double a0, double u0, long now) {
        entityData.set(HEAVY, heavy);
        refreshDimensions();
        store = new SimpleContainer(Balance.PALLET_SLOTS[heavy ? 1 : 0]);
        for (int i = 0; i < Math.min(items.size(), store.getContainerSize()); i++) store.setItem(i, items.get(i));
        ramp = r.getBlockPos();
        owner = r.getOwner();
        a = a0; u = u0; v = 0.0;

        entityData.set(PHASE, ROLL);
        rollTicks = 0;
        Vec3 p = r.toWorld(r.gridPoint(a, u, 0.0));
        moveTo(p.x, p.y, p.z, r.facing().toYRot(), 0.0F);
        last = p;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) { return heavy() ? EntityDimensions.fixed(1.96F, 1.0F) : EntityDimensions.fixed(0.98F, 1.0F); }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (HEAVY.equals(key)) refreshDimensions();
    }

    private long[] held;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { clientTick(); return; }
        if (!(level() instanceof ServerLevel sl)) return;
        if (!isRemoved()) held = RunnerChunks.follow(sl, getUUID(), position(), getDeltaMovement(), held);
        switch (phase()) {
            case ROLL -> roll(sl);
            case AIR -> fall(sl);
            default -> landed(sl);
        }
    }

    private void roll(ServerLevel sl) {
        rollTicks++;
        CargoRampBlockEntity r = ramp != null && sl.isLoaded(ramp) && sl.getBlockEntity(ramp) instanceof CargoRampBlockEntity rb && rb.head() ? rb : null;
        if (r == null || rollTicks > Balance.PALLET_MAX_ROLL_TICKS) { goFree(sl, Vec3.ZERO); return; }
        int k = heavy() ? 1 : 0;
        v = Math.min(Balance.PALLET_ROLL_MAX[k], v + Balance.PALLET_ROLL_ACC[k]);
        a += v;
        double half = heavy() ? 1.0 : 0.5, th = r.theta, y = 0.0, pitch = 0.0;
        double[] tip = r.tip(th);
        if (a - half > tip[0]) {
            Vec3 p = r.toWorld(r.gridPoint(a, u, tip[1]));
            setPos(p.x, p.y, p.z);
            goFree(sl, last == null ? Vec3.ZERO : p.subtract(last));
            return;
        }
        if (a > 0.0) {
            double ly = r.leafHeight(a, th);
            y = Double.isNaN(ly) ? 4.0 / 16.0 : ly;
            if (!Double.isNaN(ly)) pitch = -(th - 90.0);
        } else if (a > -1.0) y = 4.0 / 16.0;
        Vec3 p = r.toWorld(r.gridPoint(a, u, y));
        setPos(p.x, p.y, p.z);
        setDeltaMovement(last == null ? Vec3.ZERO : p.subtract(last));
        last = p;
        entityData.set(PITCH, (float) pitch);
    }

    private void goFree(ServerLevel sl, Vec3 vel) {
        entityData.set(PHASE, AIR);
        entityData.set(PITCH, 0.0F);
        setDeltaMovement(vel);
        airTicks = 0;
        ramp = null;
    }

    private void fall(ServerLevel sl) {
        airTicks++;
        Vec3 vel = getDeltaMovement();
        boolean chute = entityData.get(CHUTE) >= 0;
        if (!chute && airTicks >= Balance.PALLET_CHUTE_DELAY && vel.y < 0.0
                && CargoRampBlockEntity.clearBelow(sl, position(), Balance.PALLET_MIN_CHUTE) >= Balance.PALLET_MIN_CHUTE) {
            entityData.set(CHUTE, tickCount);
            sl.playSound(null, blockPosition(), WmdSounds.PALLET_CHUTE.get(), SoundSource.NEUTRAL, Balance.SND_RELEASE, heavy() ? 0.85F : 1.0F);
            chute = true;
        }
        if (chute) {
            float open = chuteOpen(0.0F);
            double fall = -Balance.PALLET_FALL - (1.0 - open) * 0.4;
            vel = new Vec3(vel.x * (1.0 - Balance.PALLET_DRAG), Math.max(vel.y - Balance.PALLET_GRAVITY * 0.5, fall), vel.z * (1.0 - Balance.PALLET_DRAG));
        } else vel = new Vec3(vel.x * 0.99, vel.y - Balance.PALLET_GRAVITY, vel.z * 0.99);
        setDeltaMovement(vel);
        move(MoverType.SELF, vel);
        Pallets.update(sl, this, chute);
        if (onGround() || isInWater()) land(sl);
    }

    private void land(ServerLevel sl) {
        Pallets.remove(sl, this);
        entityData.set(PHASE, LANDED);
        entityData.set(LANDED_AT, tickCount);
        setDeltaMovement(getDeltaMovement().multiply(0.2, 0.0, 0.2));
        sl.playSound(null, blockPosition(), WmdSounds.PALLET_LAND.get(), SoundSource.NEUTRAL, Balance.SND_MECH, heavy() ? 0.85F : 1.0F);
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p && p.level() == sl) {
            Vec3 d = position().subtract(p.position());
            int dist = (int) Math.round(Math.hypot(d.x, d.z)), brg = (int) Math.round((Math.toDegrees(Math.atan2(d.x, -d.z)) + 360.0) % 360.0);
            p.displayClientMessage(Component.translatable("message.wmd.pallet.down", dist, String.format("%03d", brg)), false);
        }
        Wmd.LOGGER.info("Drop pallet down at {} ({})", blockPosition(), isInWater() ? "water" : "ground");
    }

    private void landed(ServerLevel sl) {
        Vec3 vel = getDeltaMovement();
        if (isInWater()) vel = new Vec3(vel.x * 0.8, Math.min(0.06, vel.y + 0.04), vel.z * 0.8);
        else vel = new Vec3(vel.x * 0.6, vel.y - Balance.PALLET_GRAVITY, vel.z * 0.6);
        setDeltaMovement(vel);
        move(MoverType.SELF, vel);
    }

    private Object smokeLoop;

    private void clientTick() {
        if (phase() != LANDED || landedAt() < 0 || tickCount - landedAt() > Balance.PALLET_SMOKE_TICKS) return;
        smokeLoop = LoopSound.keep(smokeLoop, level().getGameTime(), WmdSounds.PALLET_SMOKE.get(), Balance.SND_SMALL, 1.0F, this::position,
                () -> isAlive() && phase() == LANDED && tickCount - landedAt() <= Balance.PALLET_SMOKE_TICKS);
        if (tickCount % 2 != 0) return;
        DustParticleOptions blue = new DustParticleOptions(new Vector3f(0.29F, 0.64F, 1.0F), 3.0F);
        for (int i = 0; i < 2; i++)
            level().addParticle(blue, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 1.0, getZ() + (random.nextDouble() - 0.5) * 0.4,
                    0.02 + random.nextDouble() * 0.02, 0.12 + random.nextDouble() * 0.05, 0.0);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !isAlive()) return InteractionResult.PASS;
        if (phase() == ROLL) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (phase() != LANDED) return InteractionResult.PASS;
            ItemStack packed = CargoPalletItem.packed(heavy(), items());
            store.clearContent();
            discard();
            if (!player.getInventory().add(packed)) spawnAtLocation(packed);
            return InteractionResult.CONSUME;
        }
        open(sp, 0);
        return InteractionResult.CONSUME;
    }

    private List<ItemStack> items() {
        NonNullList<ItemStack> l = NonNullList.withSize(store.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < l.size(); i++) l.set(i, store.getItem(i));
        return l;
    }

    @Override
    public void open(ServerPlayer sp, int compartment) {
        int comps = CargoPalletMenu.compartments(store.getContainerSize());
        int c = Math.max(0, Math.min(comps - 1, compartment));
        boolean hv = heavy();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CargoPalletMenu(id, inv, store, c, hv, this),
                Component.translatable(hv ? "block.wmd.cargo_pallet_heavy" : "block.wmd.cargo_pallet")),
                buf -> CargoPalletMenu.writeOpen(buf, store.getContainerSize(), c, hv));
    }

    @Override public boolean stillValid(Player player) { return isAlive() && player.distanceToSqr(this) < 64.0; }
    @Override public Vec3 where() { return position(); }

    @Override public boolean isPickable() { return isAlive(); }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sr) held = RunnerChunks.release(sr, getUUID(), held);
        if (level() instanceof ServerLevel sl) Pallets.remove(sl, this);
        super.remove(reason);
    }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(HEAVY, false); b.define(PHASE, AIR); b.define(CHUTE, -1); b.define(LANDED_AT, -1); b.define(PITCH, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        boolean hv = tag.getBoolean("heavy");
        entityData.set(HEAVY, hv);
        store = new SimpleContainer(Balance.PALLET_SLOTS[hv ? 1 : 0]);
        NonNullList<ItemStack> l = NonNullList.withSize(store.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, l, registryAccess());
        for (int i = 0; i < l.size(); i++) store.setItem(i, l.get(i));
        int ph = tag.getInt("phase");
        entityData.set(PHASE, ph == ROLL ? AIR : Math.max(AIR, Math.min(LANDED, ph)));
        entityData.set(CHUTE, tag.contains("chute") ? 0 : -1);
        entityData.set(LANDED_AT, tag.contains("landed") ? -Balance.PALLET_SMOKE_TICKS : -1);
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("heavy", heavy());
        ContainerHelper.saveAllItems(tag, NonNullList.of(ItemStack.EMPTY, items().toArray(new ItemStack[0])), registryAccess());
        tag.putInt("phase", phase());
        if (entityData.get(CHUTE) >= 0) tag.putBoolean("chute", true);
        if (landedAt() >= 0) tag.putBoolean("landed", true);
        if (owner != null) tag.putUUID("owner", owner);
    }
}
