package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MissileRobotEntity extends Entity {

    public static final int IDLE = 0, TO_SOURCE = 1, LOADING = 2, TO_TARGET = 3, UNLOADING = 4, HOME = 5, RETURN_LOAD = 6;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MissileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> LOAD = SynchedEntityData.defineId(MissileRobotEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> BATTERY = SynchedEntityData.defineId(MissileRobotEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> STUCK = SynchedEntityData.defineId(MissileRobotEntity.class, EntityDataSerializers.INT);
    public static final int STUCK_NONE = 0, STUCK_NO_SPOT = 1, STUCK_NO_ROUTE = 2, STUCK_BLOCKED = 3, STUCK_BATTERY = 4;

    private UUID owner;
    private String ownerName = "";
    private BlockPos home;
    private int energy;
    private float health;

    private Logistics.Job job;

    private final List<ItemStack> extra = new ArrayList<>();
    private List<Vec3> route;
    private int routeIdx, waitTicks, stateTicks;
    private BlockPos routeGoal;

    private RobotPath.Result lastSearch;

    private int lerpSteps;
    private double lx, ly, lz;
    private float lyaw;

    public MissileRobotEntity(EntityType<? extends MissileRobotEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        energy = battery();
        health = maxHealth();
    }

    boolean heavy() { return false; }

    public static final int KIND_ROBOT = 0, KIND_XL = 1, KIND_RUNNER = 2;
    public int vehicleKind() { return heavy() ? KIND_XL : KIND_ROBOT; }

    int carryStacks() { return 1; }

    public boolean upgraded() { return false; }

    int carryFor(net.minecraft.world.item.Item item) { return vehicleKind() == KIND_RUNNER ? carryStacks() * item.getDefaultMaxStackSize() : 1; }
    int battery() { return Balance.ROBOT_BATTERY; }
    int maxHealth() { return Balance.ROBOT_HEALTH; }
    int reserve() { return Balance.ROBOT_JOB_RESERVE; }
    int fePerBlock() { return Balance.ROBOT_FE_PER_BLOCK; }
    double speed() { return Balance.ROBOT_SPEED; }
    int loadTicks() { return Balance.ROBOT_LOAD_TICKS; }

    int half() { return 1; }
    int head() { return 3; }

    ItemStack asItem() { return new ItemStack(Wmd.MISSILE_ROBOT_ITEM.get()); }

    String infoKey() { return "message.wmd.robot.info."; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, IDLE);
        builder.define(LOAD, ItemStack.EMPTY);
        builder.define(BATTERY, 100);
        builder.define(STUCK, STUCK_NONE);
    }

    void setup(Player placer, BlockPos dock, float yaw) {
        owner = placer.getUUID();
        ownerName = placer.getGameProfile().getName();
        home = dock.immutable();
        setYRot(yaw);
    }

    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public BlockPos home() { return home; }
    public int state() { return entityData.get(STATE); }
    public ItemStack load() { return entityData.get(LOAD); }
    public int batteryPercent() { return entityData.get(BATTERY); }
    public int stuck() { return entityData.get(STUCK); }

    public int[] routeLine(int max) {
        if (route == null) return new int[0];
        int n = Math.min(max, route.size() - routeIdx);
        if (n <= 0) return new int[0];
        int[] out = new int[n * 2];
        for (int i = 0; i < n; i++) { Vec3 w = route.get(routeIdx + i); out[2 * i] = (int) Math.floor(w.x); out[2 * i + 1] = (int) Math.floor(w.z); }
        return out;
    }

    private void setStuck(int s, BlockPos target) {
        if (stuck() == s) return;
        entityData.set(STUCK, s);
        if (s != STUCK_NONE) Wmd.LOGGER.info("{} at {} cannot drive to {}: {}{}", vehicleKind() == KIND_XL ? "XL transporter" : vehicleKind() == KIND_RUNNER ? "Parts runner" : "Missile robot", blockPosition(), target,
                switch (s) { case STUCK_NO_SPOT -> "no free spot next to the target"; case STUCK_NO_ROUTE -> "no route found"; case STUCK_BLOCKED -> "something is in the way"; default -> "flat battery"; },
                s == STUCK_NO_ROUTE && lastSearch != null ? String.format(" (got as far as %s, %.0f blocks short, %d columns searched)", lastSearch.closest().toShortString(),
                        lastSearch.shortBy(), lastSearch.explored()) : "");
    }
    Logistics.Job job() { return job; }
    int energy() { return energy; }
    boolean free() { return job == null && state() == IDLE && empty() && energy >= reserve(); }

    boolean readyForNext() { return job == null && (state() == IDLE || state() == HOME) && empty() && energy >= reserve(); }

    boolean empty() { return load().isEmpty() && extra.isEmpty(); }

    List<ItemStack> cargo() {
        List<ItemStack> l = new ArrayList<>();
        if (!load().isEmpty()) l.add(load());
        for (ItemStack s : extra) if (!s.isEmpty()) l.add(s);
        return l;
    }

    private void setCargo(List<ItemStack> l) {
        extra.clear();
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack s : l) {
            if (s.isEmpty()) continue;
            if (first.isEmpty()) first = s; else extra.add(s.copy());
        }
        setLoad(first);
    }

    private void setState(int s) { if (state() != s) entityData.set(STATE, s); stateTicks = 0; route = null; if (stuck() != STUCK_NONE) entityData.set(STUCK, STUCK_NONE); }
    private void setLoad(ItemStack s) { entityData.set(LOAD, s.copy()); }

    int charge(int fe) {
        int n = Math.min(fe, battery() - energy);
        if (n > 0) { energy += n; syncBattery(); }
        return n;
    }

    private void syncBattery() {
        int p = (int) ((long) energy * 100 / battery());
        if (p != entityData.get(BATTERY)) entityData.set(BATTERY, p);
    }

    void start(Logistics.Job j) {
        job = j;
        setState(TO_SOURCE);
    }

    boolean parked() {
        Vec3 p = RobotDockBlockEntity.parkSpot(level(), home);
        return p != null && state() == IDLE && position().distanceToSqr(p) < 1.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (owner == null) { discard(); return; }
        Logistics.noteRobot(this);
        if (EmpField.jammed(sl, blockPosition())) return;
        stateTicks++;
        if (heavy() && state() == TO_SOURCE && stateTicks <= XlDockAnim.RELEASE_HOLD && onOwnSpot(sl)) return;
        if (job != null && stateTicks % 20 == 0 && (state() == TO_SOURCE || state() == TO_TARGET || state() == RETURN_LOAD)) recheck(sl);
        switch (state()) {
            case TO_SOURCE, RETURN_LOAD -> { if (job == null) { setState(HOME); break; } if (drive(sl, job.sourceBlock())) setState(state() == TO_SOURCE ? LOADING : UNLOADING); }
            case LOADING -> {
                boolean depot = job != null && job.sourceKind() == Logistics.SRC_DEPOT;
                if (depot && stateTicks == 1) Logistics.craneShow(sl, job.sourceBlock(), job, ItemStack.EMPTY, false);
                if (stateTicks < (depot ? MissileDepotBlockEntity.CRANE_TAKE : loadTicks())) break;
                List<ItemStack> got = job == null ? List.of() : Logistics.take(sl, job);
                if (got.isEmpty()) { job = null; setState(HOME); break; }
                setCargo(got);
                level().playSound(null, blockPosition(), WmdSounds.ROBOT_ARM.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                setState(TO_TARGET);
            }
            case TO_TARGET -> { if (job == null) { setState(HOME); break; } if (drive(sl, job.target())) setState(UNLOADING); }
            case UNLOADING -> {
                BlockPos at = job == null ? null : job.returning() ? job.sourceBlock() : job.target();
                boolean depot = at != null && sl.getBlockEntity(at) instanceof MissileDepotBlockEntity;
                if (depot && stateTicks == 1) Logistics.craneShow(sl, at, job, load(), true);
                if (stateTicks < (depot ? MissileDepotBlockEntity.CRANE_GIVE : loadTicks())) break;
                List<ItemStack> rest = new ArrayList<>();
                for (ItemStack s : cargo()) {
                    ItemStack r = job == null ? s : (job.returning() ? Logistics.putBack(sl, job, s) : Logistics.deliver(sl, owner, job, s));
                    if (!r.isEmpty()) rest.add(r);
                }
                setCargo(rest);
                level().playSound(null, blockPosition(), WmdSounds.ROBOT_ARM.get(), SoundSource.BLOCKS, 0.8F, 0.85F);
                if (!empty() && job != null && !job.returning()) { job = job.back(); setState(RETURN_LOAD); break; }
                job = null;
                setState(HOME);
            }
            case HOME -> {
                Vec3 park = home == null ? null : RobotDockBlockEntity.parkSpot(sl, home);
                if (park == null) break;
                if (onDock(sl, park)) {
                    if (approach(sl, park)) setState(IDLE);
                    break;
                }
                if (drive(sl, BlockPos.containing(park))) {
                    setPos(park.x, park.y, park.z);
                    setYRot(RobotDockBlockEntity.parkYaw(sl, home));
                    setState(IDLE);
                }
            }
            default -> {}
        }
    }

    private void recheck(ServerLevel sl) {
        boolean giveUp = stuck() == STUCK_NO_ROUTE && stateTicks >= 400;
        switch (state()) {
            case TO_SOURCE -> {
                boolean targetOk = !sl.isLoaded(job.target()) || Logistics.port(sl, owner, job.target(), new ItemStack(job.item())) != null;
                if (giveUp || !targetOk || !Logistics.sourceOk(sl, job)) { job = null; setState(HOME); }
            }
            case TO_TARGET -> {
                if (giveUp || sl.isLoaded(job.target()) && Logistics.port(sl, owner, job.target(), load()) == null) { job = job.back(); setState(RETURN_LOAD); }
            }
            case RETURN_LOAD -> {
                if (giveUp || !Logistics.sourceStands(sl, job)) { job = null; setState(HOME); }
            }
            default -> {}
        }
    }

    private boolean onOwnSpot(ServerLevel sl) {
        Vec3 p = home == null ? null : RobotDockBlockEntity.parkSpot(sl, home);
        return p != null && position().distanceToSqr(p) < 1.0;
    }

    private boolean onDock(ServerLevel sl, Vec3 park) {
        double r = 0;
        for (BlockPos c : Logistics.cells(sl, home)) r = Math.max(r, Math.hypot(c.getX() + 0.5 - park.x, c.getZ() + 0.5 - park.z));
        return Math.hypot(getX() - park.x, getZ() - park.z) <= r + 1.5;
    }

    private boolean approach(ServerLevel sl, Vec3 park) {
        Vec3 at = position();
        double dx = park.x - at.x, dz = park.z - at.z, d = Math.hypot(dx, dz);
        if (d >= 0.15) {
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, diff = Mth.wrapDegrees(want - getYRot());
            if (d > 1.0 || Math.abs(diff) < 90.0F) {
                setYRot(getYRot() + Mth.clamp(diff, -9.0F, 9.0F));
                if (Math.abs(diff) > 30.0F && d > 1.0) return false;
            }
            double step = Math.min(speed(), d);
            setPos(at.x + dx / d * step, at.y + Mth.clamp(park.y - at.y, -0.25, 0.25), at.z + dz / d * step);
            energy = Math.max(0, energy - (int) Math.ceil(fePerBlock() * step));
            return false;
        }
        setPos(park.x, park.y, park.z);
        float diff = Mth.wrapDegrees(RobotDockBlockEntity.parkYaw(sl, home) - getYRot());
        setYRot(getYRot() + Mth.clamp(diff, -9.0F, 9.0F));
        return Math.abs(diff) <= 9.0F;
    }

    private boolean drive(ServerLevel sl, BlockPos target) {
        if (waitTicks > 0) { waitTicks--; return false; }
        if (route == null || !target.equals(routeGoal)) {

            Vec3 park = home == null ? null : RobotDockBlockEntity.parkSpot(sl, home);
            java.util.Set<Long> dock = park == null ? java.util.Set.of() : RobotPath.columns(Logistics.cells(sl, home));
            double dockY = park == null ? 0 : park.y;
            if (state() == HOME) { route = RobotPath.find(sl, position(), target, half(), head(), dock, dockY); lastSearch = null; }
            else {
                lastSearch = RobotPath.findNear(sl, position(), Logistics.cells(sl, target), half(), head(), dock, dockY);
                route = lastSearch.route();
            }
            routeGoal = target;
            routeIdx = 0;
            if (route == null) { setStuck(STUCK_NO_ROUTE, target); waitTicks = 100; return false; }
            setStuck(STUCK_NONE, target);
        }
        if (routeIdx >= route.size()) return true;
        if (energy <= 0) { setStuck(STUCK_BATTERY, target); return false; }
        Vec3 wp = route.get(routeIdx), at = position();
        double dx = wp.x - at.x, dz = wp.z - at.z, d = Math.hypot(dx, dz);
        if (d < 0.2) { setPos(wp.x, wp.y, wp.z); routeIdx++; return routeIdx >= route.size(); }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float diff = Mth.wrapDegrees(want - getYRot());
        setYRot(getYRot() + Mth.clamp(diff, -9.0F, 9.0F));
        if (Math.abs(diff) > 30.0F) return false;
        double step = Math.min(speed(), d);
        double nx = at.x + dx / d * step, nz = at.z + dz / d * step;
        double g = RobotPath.ground(sl, (int) Math.floor(nx), (int) Math.floor(nz), (int) Math.floor(at.y + 0.2), head());
        Vec3 park = home == null ? null : RobotDockBlockEntity.parkSpot(sl, home);
        if (park != null && Math.abs(park.y - at.y) <= 1.05 && RobotPath.columns(Logistics.cells(sl, home)).contains(BlockPos.asLong((int) Math.floor(nx), 0, (int) Math.floor(nz)))) g = park.y;
        if (Double.isNaN(g) || Math.abs(g - at.y) > 1.05) { route = null; waitTicks = 100; setStuck(STUCK_BLOCKED, target); return false; }
        setPos(nx, at.y + Mth.clamp(g - at.y, -0.25, 0.25), nz);
        energy = Math.max(0, energy - (int) Math.ceil(fePerBlock() * step));
        if (tickCount % 20 == 0) syncBattery();
        return false;
    }

    void clientTick() {
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
            setYRot(getYRot() + Mth.wrapDegrees(lyaw - getYRot()) * (float) k);
            lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lx = x; ly = y; lz = z; lyaw = yRot;
        lerpSteps = Math.max(1, steps + 1);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved() || level().isClientSide) return !level().isClientSide;
        health -= amount;
        level().playSound(null, blockPosition(), WmdSounds.ROBOT_HIT.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);

        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        if (source.getEntity() instanceof Player p && health > 0)
            p.displayClientMessage(Component.translatable("message.wmd.robot.hull", Math.max(1, (int) Math.ceil(health)), maxHealth()), true);
        if (health <= 0) destroyed();
        return true;
    }

    private void destroyed() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ItemStack s : cargo()) spawnAtLocation(s.copy(), 1.0F);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 1.2, 0.6, 1.2, 0.02);
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0, getZ(), 3, 1.0, 0.5, 1.0, 0.0);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.2F, 1.4F);
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.wmd.not_yours", ownerName), true);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            job = null;
            for (ItemStack s : cargo()) if (!player.getInventory().add(s.copy())) spawnAtLocation(s.copy(), 1.0F);
            if (!player.getInventory().add(asItem())) spawnAtLocation(asItem(), 1.0F);
            discard();
            return InteractionResult.SUCCESS;
        }
        Component line = Component.translatable(infoKey() + state(), batteryPercent(), load().isEmpty() ? Component.translatable("message.wmd.robot.empty") : load().getHoverName());
        if (stuck() != STUCK_NONE && state() != IDLE) {
            Component why;
            if (stuck() == STUCK_NO_ROUTE && lastSearch != null) {
                boolean atStart = lastSearch.closest().distManhattan(blockPosition()) <= 1;
                if (lastSearch.explored() < 10) why = Component.translatable("message.wmd.robot.stuck.boxed");
                else if (atStart) why = Component.translatable("message.wmd.robot.stuck.target", Balance.ROBOT_STAND_RADIUS + half() - 1, lastSearch.explored());
                else why = Component.translatable("message.wmd.robot.stuck.far", lastSearch.closest().getX(), lastSearch.closest().getY(), lastSearch.closest().getZ(), Math.round(lastSearch.shortBy()));
            } else why = Component.translatable("message.wmd.robot.stuck." + stuck());
            line = Component.translatable("message.wmd.robot.stuck_line", line, why);
        }
        player.displayClientMessage(line, true);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && level() instanceof ServerLevel sl) showRoute(sl, sp);
        return InteractionResult.SUCCESS;
    }

    private void showRoute(ServerLevel sl, net.minecraft.server.level.ServerPlayer p) {
        if (route != null) {
            Vec3 prev = position();
            for (int i = routeIdx; i < route.size() && i < routeIdx + 60; i++) {
                Vec3 w = route.get(i);
                double len = prev.distanceTo(w);
                for (double t = 0; t < len; t += 0.75) {
                    Vec3 q = prev.lerp(w, t / Math.max(0.001, len));
                    sl.sendParticles(p, ParticleTypes.HAPPY_VILLAGER, true, q.x, q.y + 0.3, q.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
                prev = w;
            }
        } else if (stuck() == STUCK_NO_ROUTE && lastSearch != null) {
            BlockPos c = lastSearch.closest();

            var white = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 1.0F, 1.0F), 2.0F);
            for (int k = 0; k < 24; k++) sl.sendParticles(p, white, true, c.getX() + 0.5, c.getY() + 0.2 + k * 0.25, c.getZ() + 0.5, 2, 0.15, 0.0, 0.15, 0.0);
            if (routeGoal != null) {
                var mag = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.2F, 1.0F), 2.0F);
                for (int k = 0; k < 32; k++) sl.sendParticles(p, mag, true, routeGoal.getX() + 0.5, routeGoal.getY() + 0.2 + k * 0.25, routeGoal.getZ() + 0.5, 2, 0.15, 0.0, 0.15, 0.0);
            }
            List<int[]> ws = new java.util.ArrayList<>(lastSearch.walls().values());
            ws.sort(java.util.Comparator.comparingDouble(w -> Math.hypot(w[0] - c.getX(), w[2] - c.getZ())));
            int[] counts = new int[6];
            for (int[] w : ws) counts[Math.max(0, Math.min(5, w[3]))]++;
            for (int i = 0; i < ws.size() && i < 250; i++) {
                int[] w = ws.get(i);
                org.joml.Vector3f col = switch (w[3]) {
                    case RobotPath.W_WATER -> new org.joml.Vector3f(0.2F, 0.8F, 1.0F);
                    case RobotPath.W_DROP -> new org.joml.Vector3f(0.5F, 0.5F, 0.5F);
                    case RobotPath.W_STEEP -> new org.joml.Vector3f(1.0F, 0.9F, 0.1F);
                    case RobotPath.W_NARROW -> new org.joml.Vector3f(1.0F, 0.5F, 0.0F);
                    default -> new org.joml.Vector3f(1.0F, 0.1F, 0.1F);
                };
                var dust = new net.minecraft.core.particles.DustParticleOptions(col, 1.5F);
                for (int k = 0; k < 3; k++) sl.sendParticles(p, dust, true, w[0] + 0.5, w[1] + 1.2 + k * 0.35, w[2] + 0.5, 1, 0.05, 0.0, 0.05, 0.0);
            }
            p.sendSystemMessage(Component.translatable("message.wmd.robot.walls", counts[RobotPath.W_WALL], counts[RobotPath.W_WATER], counts[RobotPath.W_DROP],
                    counts[RobotPath.W_STEEP], counts[RobotPath.W_NARROW]));
        }
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isAttackable() { return true; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        home = tag.contains("home") ? BlockPos.of(tag.getLong("home")) : null;
        energy = tag.getInt("energy");
        health = tag.contains("health") ? tag.getFloat("health") : maxHealth();
        job = tag.contains("job") ? Logistics.Job.read(tag.getCompound("job"), level().registryAccess()) : null;
        if (tag.contains("load")) setLoad(ItemStack.parseOptional(level().registryAccess(), tag.getCompound("load")));
        extra.clear();
        net.minecraft.nbt.ListTag ex = tag.getList("extra", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < ex.size(); i++) { ItemStack s = ItemStack.parseOptional(level().registryAccess(), ex.getCompound(i)); if (!s.isEmpty()) extra.add(s); }
        entityData.set(STATE, tag.getInt("state"));
        syncBattery();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        if (home != null) tag.putLong("home", home.asLong());
        tag.putInt("energy", energy);
        tag.putFloat("health", health);
        if (job != null) tag.put("job", job.write(level().registryAccess()));
        if (!load().isEmpty()) tag.put("load", load().save(level().registryAccess()));
        if (!extra.isEmpty()) {
            net.minecraft.nbt.ListTag ex = new net.minecraft.nbt.ListTag();
            for (ItemStack s : extra) ex.add(s.save(level().registryAccess()));
            tag.put("extra", ex);
        }
        int st = state();
        if (st == LOADING) st = TO_SOURCE;
        else if (st == UNLOADING) st = job != null && job.returning() ? RETURN_LOAD : TO_TARGET;
        tag.putInt("state", st);
    }
}
