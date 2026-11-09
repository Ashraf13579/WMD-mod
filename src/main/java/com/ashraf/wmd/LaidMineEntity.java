package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LaidMineEntity extends Entity {

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(LaidMineEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Float> START_SCALE = SynchedEntityData.defineId(LaidMineEntity.class, EntityDataSerializers.FLOAT);
    static final float GROW_TICKS = 12.0F;

    private ItemStack mine = ItemStack.EMPTY, torpedo = ItemStack.EMPTY;
    private UUID owner;
    private Vec3 vel = Vec3.ZERO;
    private int age;
    private boolean wasWet;

    public LaidMineEntity(EntityType<? extends LaidMineEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public int kind() { return entityData.get(KIND); }
    public float startScale() { return entityData.get(START_SCALE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, SeaMineBlockEntity.K_INFLUENCE);
        builder.define(START_SCALE, 1.0F);
    }

    static Item torpedoFor(int kind) {
        return kind == SeaMineBlockEntity.K_BOTTOM ? Wmd.TORPEDO_T1.get() : kind == SeaMineBlockEntity.K_CAPTOR ? Wmd.TORPEDO_T2.get() : null;
    }

    static int kindOf(ItemStack s) { return !s.isEmpty() && s.getItem() instanceof SeaMineItem m ? m.kind() : -1; }

    static LaidMineEntity launch(ServerLevel sl, Vec3 at, Vec3 v, ItemStack mine, ItemStack torpedo, UUID owner, float startScale) { return launch(sl, at, v, mine, torpedo, owner, startScale, null); }

    static LaidMineEntity launch(ServerLevel sl, Vec3 at, Vec3 v, ItemStack mine, ItemStack torpedo, UUID owner, float startScale, BlockPos source) {
        int kind = kindOf(mine);
        if (kind < 0 || owner == null) return null;
        Item want = torpedoFor(kind);
        if (!torpedo.isEmpty() && (want == null || !torpedo.is(want))) return null;
        LaidMineEntity e = new LaidMineEntity(Wmd.LAID_MINE.get(), sl);
        e.setPos(at.x, at.y, at.z);
        e.vel = v;
        e.setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        e.mine = mine.copyWithCount(1);
        e.torpedo = torpedo.isEmpty() ? ItemStack.EMPTY : torpedo.copyWithCount(1);
        e.owner = owner;
        e.source = source;
        e.ownShip = source == null || !SableBridge.active() ? null : SableBridge.shipIdAt(sl, source);
        e.entityData.set(KIND, kind);
        e.entityData.set(START_SCALE, Mth.clamp(startScale, 0.05F, 1.0F));
        return sl.addFreshEntity(e) ? e : null;
    }

    static boolean layFrom(ServerPlayer p, InteractionHand hand, int kind) {
        ServerLevel sl = p.serverLevel();
        ItemStack held = p.getItemInHand(hand);
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = Vec3.directionFromRotation(0.0F, p.getYRot());
        flat = flat.normalize();
        Vec3 at = eye.add(flat.scale(Balance.MINE_LAY_REACH)).add(0.0, -0.4, 0.0);

        if (sl.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS
                || hullBetween(sl, eye, at)) return refuse(p, "rail");
        int top = NavalLayer.waterTop(sl, Mth.floor(at.x), Mth.floor(at.z));
        if (top == NavalLayer.NO_WATER) return refuse(p, "nowater");
        Vec3 down = new Vec3(at.x, Math.min(at.y, top + 1.0) - 0.05, at.z);
        if (sl.clip(new ClipContext(at, down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) return refuse(p, "rail");
        if (hullBetween(sl, at, new Vec3(at.x, at.y - Balance.MINE_LAY_HULL_CHECK, at.z))) return refuse(p, "rail");
        int water = waterBelow(sl, Mth.floor(at.x), top, Mth.floor(at.z));
        if (water < Balance.MINE_LAY_MIN_WATER) { p.displayClientMessage(Component.translatable("message.wmd.seamine.lay_shallow", water, Balance.MINE_LAY_MIN_WATER), true); return false; }

        ItemStack torp = ItemStack.EMPTY;
        Item want = kind == SeaMineBlockEntity.K_BOTTOM ? Wmd.TORPEDO_T1.get() : kind == SeaMineBlockEntity.K_CAPTOR ? Wmd.TORPEDO_T2.get() : null;
        if (want != null) {
            ItemStack src = p.getOffhandItem().is(want) ? p.getOffhandItem() : ItemStack.EMPTY;
            if (src.isEmpty()) for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(want)) { src = p.getInventory().getItem(i); break; }
            if (!src.isEmpty()) { torp = src.copyWithCount(1); if (!p.getAbilities().instabuild) src.shrink(1); }
        }
        ItemStack one = held.copyWithCount(1);
        if (!p.getAbilities().instabuild) held.shrink(1);
        LaidMineEntity e = new LaidMineEntity(Wmd.LAID_MINE.get(), sl);
        e.setPos(at.x, at.y, at.z);
        e.vel = flat.scale(Balance.MINE_LAY_THROW).add(0.0, 0.05, 0.0);
        e.setYRot(p.getYRot());
        e.mine = one;
        e.torpedo = torp;
        e.owner = p.getUUID();
        e.entityData.set(KIND, kind);
        sl.addFreshEntity(e);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), WmdSounds.DC_THROW.get(), SoundSource.PLAYERS, 1.0F, 0.75F);
        p.displayClientMessage(Component.translatable(want == null ? "message.wmd.seamine.lay_over" : torp.isEmpty() ? "message.wmd.seamine.lay_over_empty" : "message.wmd.seamine.lay_over_loaded",
                Component.translatable(want == null ? "item.wmd.torpedo_t1" : want.getDescriptionId())), true);
        return true;
    }

    private static boolean refuse(ServerPlayer p, String why) {
        p.displayClientMessage(Component.translatable("message.wmd.seamine.lay_" + why), true);
        return false;
    }

    private static boolean hullBetween(ServerLevel sl, Vec3 a, Vec3 b) {
        if (!SableBridge.active()) return false;
        java.util.List<SableBridge.ShipInfo> ships = SableBridge.shipsAlongSegment(sl, a, b);
        return !ships.isEmpty() && SableBridge.exactHit(sl, a, b, ships) != null;
    }

    static int waterBelow(Level level, int x, int top, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
        int n = 0;
        while (m.getY() > level.getMinBuildHeight() && level.getFluidState(m).is(FluidTags.WATER)) { n++; m.move(Direction.DOWN); }
        return n;
    }

    private BlockPos source;

    private java.util.UUID ownShip;

    static boolean ownShipHit(ServerLevel sl, java.util.UUID ownShip, BlockHitResult hit, Vec3 from, Vec3 to) {
        if (ownShip == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos b = hit.getBlockPos();
        if (!sl.getBlockState(b).getCollisionShape(sl, b).isEmpty()) return false;
        for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, from, to)) if (s.id().equals(ownShip)) return true;
        return false;
    }

    private boolean ownLauncher(ServerLevel sl, BlockPos p) {
        if (source == null) return false;
        if (p.equals(source)) return true;
        return sl.getBlockEntity(p) instanceof MachinePartBlockEntity part && source.equals(part.getController());
    }

    @Override
    public void tick() {
        super.tick();
        Level l = level();
        Vec3 pos = position();
        boolean wet = l.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (!(l instanceof ServerLevel sl)) {
            if (wet && tickCount % 2 == 0) l.addParticle(ParticleTypes.BUBBLE, pos.x + (random.nextDouble() - 0.5) * 0.6, pos.y + 0.9, pos.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.12, 0);
            return;
        }
        age++;
        if (mine.isEmpty()) { discard(); return; }
        if (age > Balance.MINE_LAY_MAX_TICKS || pos.y < sl.getMinBuildHeight() + 1) { giveBack(sl, "lost"); return; }
        if (!wet) vel = new Vec3(vel.x * 0.98, vel.y - 0.06, vel.z * 0.98);
        else {
            if (!wasWet && age > 1) {
                sl.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.8, pos.z, 50, 0.6, 0.2, 0.6, 0.25);
                sl.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 25, 0.5, 0.4, 0.5, 0.1);
                sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.8F, 0.6F);
            }
            vel = new Vec3(vel.x * 0.8, vel.y + (-Balance.MINE_LAY_SINK - vel.y) * 0.25, vel.z * 0.8);
        }
        wasWet = wet;
        Vec3 next = pos.add(vel);
        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK && age <= Balance.DC_SOURCE_PASS_TICKS && ownLauncher(sl, hit.getBlockPos())) hit = BlockHitResult.miss(next, hit.getDirection(), BlockPos.containing(next));
        if (ownShipHit(sl, ownShip, hit, pos, next)) hit = BlockHitResult.miss(next, hit.getDirection(), BlockPos.containing(next));
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (hit.getDirection() != Direction.UP) {
                vel = new Vec3(0.0, Math.min(vel.y, -0.05), 0.0);
                Vec3 down = pos.add(0.0, vel.y, 0.0);
                if (sl.clip(new ClipContext(pos, down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) setPos(down.x, down.y, down.z);
                return;
            }
            land(sl, hit.getBlockPos().above(), sl.getBlockState(hit.getBlockPos()));
            return;
        }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private void land(ServerLevel sl, BlockPos cell, BlockState bed) {
        ServerPlayer p = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
        Direction facing = Direction.fromYRot(getYRot());
        String why = SeaMineBlock.layAt(sl, cell, facing, p, mine, !torpedo.isEmpty());
        if (why != null) { giveBack(sl, why); return; }
        discard();
        mine = ItemStack.EMPTY;
        torpedo = ItemStack.EMPTY;
        double x = cell.getX() + 0.5, y = cell.getY(), z = cell.getZ() + 0.5;
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bed), x, y + 0.1, z, 45, 0.9, 0.1, 0.9, 0.05);
        sl.sendParticles(ParticleTypes.BUBBLE, x, y + 0.5, z, 30, 0.6, 0.3, 0.6, 0.1);
        sl.playSound(null, x, y, z, bed.getSoundType(sl, cell.below(), this).getFallSound(), SoundSource.BLOCKS, 1.6F, 0.5F);
        SeaMineBlockEntity be = sl.getBlockEntity(cell) instanceof SeaMineBlockEntity b ? b : null;
        if (be != null && be.moored()) {
            double eye = SeaMineBlockEntity.eyeFor(sl, cell, be.kind(), be.depth());
            for (double yy = y + 1.5; yy < eye; yy += 1.5) sl.sendParticles(ParticleTypes.BUBBLE, x, yy, z, 2, 0.15, 0.4, 0.15, 0.05);
            sl.playSound(null, x, y + 1.0, z, WmdSounds.TP_RACK_HOIST.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        if (p != null) p.displayClientMessage(Component.translatable(be != null && be.moored() ? "message.wmd.seamine.laid" : "message.wmd.seamine.laid_bottom",
                Component.translatable(sl.getBlockState(cell).getBlock().getDescriptionId()), cell.getX(), cell.getY(), cell.getZ(),
                Balance.SEAMINE_ARM_TICKS / 20, be != null && be.moored() ? be.depth() : 0,
                Component.translatable(be != null && be.loaded() ? "message.wmd.seamine.laid_loaded" : be != null && be.warheadItem() != null ? "message.wmd.seamine.laid_empty" : "message.wmd.seamine.laid_ready")), false);
        Wmd.LOGGER.info("Sea mine laid at {} by {}", cell.toShortString(), owner);
    }

    private void giveBack(ServerLevel sl, String why) {
        if (isRemoved()) return;
        discard();
        ItemStack[] back = { mine, torpedo };
        mine = ItemStack.EMPTY;
        torpedo = ItemStack.EMPTY;
        ServerPlayer p = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
        boolean near = p != null && p.level() == sl && p.position().distanceToSqr(position()) <= Balance.MINE_LAY_RETURN_RANGE * Balance.MINE_LAY_RETURN_RANGE;
        int top = NavalLayer.waterTop(sl, Mth.floor(getX()), Mth.floor(getZ()));
        double dropY = top == NavalLayer.NO_WATER ? getY() + 0.5 : Math.max(getY(), top + 1.0);
        for (ItemStack s : back) {
            if (s.isEmpty()) continue;
            if (near) { if (!p.addItem(s)) p.drop(s, false); }
            else {
                ItemEntity it = new ItemEntity(sl, getX(), dropY, getZ(), s);
                it.setDefaultPickUpDelay();
                sl.addFreshEntity(it);
            }
        }
        if (p != null) p.displayClientMessage(Component.translatable("message.wmd.seamine.lay_back", Component.translatable("message.wmd.seamine.lay_why_" + why),
                Mth.floor(getX()), Mth.floor(getY()), Mth.floor(getZ())), false);
        Wmd.LOGGER.info("Laid sea mine refused ({}) at {} {} {}, given back {}", why, Mth.floor(getX()), Mth.floor(getY()), Mth.floor(getZ()), near ? "to the layer" : "as drops");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!mine.isEmpty()) tag.put("lmMine", mine.save(level().registryAccess()));
        if (!torpedo.isEmpty()) tag.put("lmTorp", torpedo.save(level().registryAccess()));
        if (owner != null) tag.putUUID("lmOwner", owner);
        tag.putDouble("lmVx", vel.x);
        tag.putDouble("lmVy", vel.y);
        tag.putDouble("lmVz", vel.z);
        tag.putInt("lmAge", age);
        tag.putInt("lmKind", kind());
        tag.putBoolean("lmWet", wasWet);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        mine = tag.contains("lmMine") ? ItemStack.parseOptional(level().registryAccess(), tag.getCompound("lmMine")) : ItemStack.EMPTY;
        torpedo = tag.contains("lmTorp") ? ItemStack.parseOptional(level().registryAccess(), tag.getCompound("lmTorp")) : ItemStack.EMPTY;
        owner = tag.hasUUID("lmOwner") ? tag.getUUID("lmOwner") : null;
        vel = new Vec3(tag.getDouble("lmVx"), tag.getDouble("lmVy"), tag.getDouble("lmVz"));
        age = tag.getInt("lmAge");
        entityData.set(KIND, tag.getInt("lmKind"));
        wasWet = tag.getBoolean("lmWet");
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128.0 * 128.0; }
}
