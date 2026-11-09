package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class MineBlockEntity extends BlockEntity implements Owned {

    public static final ResourceKey<DamageType> CLAYMORE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "claymore"));

    private final OwnerData ownerData = new OwnerData();
    private long placedAt = -1, fuseAt = -1;
    private boolean fired;

    public MineBlockEntity(BlockPos pos, BlockState state) { super(Wmd.MINE_BE.get(), pos, state); }

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

    private boolean claymore() { return getBlockState().getBlock() instanceof MineBlock m && m.claymore; }

    public boolean armed() { return level != null && placedAt >= 0 && level.getGameTime() >= placedAt + Balance.MINE_ARM_TICKS; }

    public long age(Level l) { return placedAt < 0 ? -1 : l.getGameTime() - placedAt; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MineBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || be.fired) return;
        long now = sl.getGameTime();
        if (be.placedAt < 0) { be.placedAt = now; be.setChanged(); be.sync(); return; }
        if (now == be.placedAt + Balance.MINE_ARM_TICKS) sl.playSound(null, pos, WmdSounds.MINE_ARM.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        if (EmpField.jammed(sl, pos)) { be.fuseAt = -1; return; }
        if (be.fuseAt >= 0) { if (now >= be.fuseAt) be.detonate(sl); return; }
        if (!be.claymore() || !be.armed() || (now + pos.asLong()) % 4 != 0) return;
        Vec3 c = Vec3.atCenterOf(pos), f = front(state);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(Balance.CLAY_TRIP), e -> DefenceTargets.hostile(sl, be, e))) {
            Vec3 to = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            if (inCone(c, f, to, Balance.CLAY_TRIP, Balance.CLAY_TRIP_DEG) && sees(sl, c, to)) {
                be.fuseAt = now + Balance.CLAY_DELAY;
                sl.playSound(null, c.x, c.y, c.z, WmdSounds.CLAY_CLICK.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
                return;
            }
        }
    }

    void steppedOn(LivingEntity e) {
        if (fired || !(level instanceof ServerLevel sl) || !armed() || EmpField.jammed(sl, worldPosition)) return;
        if (DefenceTargets.hostile(sl, this, e)) detonate(sl);
    }

    private void detonate(ServerLevel sl) {
        if (fired) return;
        fired = true;
        BlockState s = getBlockState();
        boolean c = claymore();
        sl.removeBlock(worldPosition, false);
        blast(sl, worldPosition, s, c);
    }

    static Vec3 front(BlockState s) {
        Direction d = Machines.dirToWorld(Machines.facing(s), Machines.mount(s), Direction.NORTH);
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    static boolean inCone(Vec3 c, Vec3 f, Vec3 to, double range, double halfDeg) {
        Vec3 d = to.subtract(c);
        double len = d.length();
        return len > 1e-3 && len <= range && d.dot(f) / len >= Math.cos(Math.toRadians(halfDeg));
    }

    private static boolean sees(ServerLevel sl, Vec3 from, Vec3 to) {
        Vec3 start = from.add(to.subtract(from).normalize().scale(0.6));
        return sl.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null)).getType() == HitResult.Type.MISS;
    }

    static void blast(ServerLevel sl, BlockPos pos, BlockState s, boolean claymore) {
        Vec3 c = Vec3.atCenterOf(pos);
        if (!claymore) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), c.x, pos.getY() + 0.2, c.z, 60, 0.4, 0.2, 0.4, 0.35);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, pos.getY() + 0.3, c.z, 6, 0.3, 0.1, 0.3, 0.03);
            sl.explode(null, c.x, pos.getY() + 0.2, c.z, Balance.MINE_POWER, Level.ExplosionInteraction.NONE);
            sl.playSound(null, c.x, c.y, c.z, WmdSounds.MINE_BLAST.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            return;
        }
        Vec3 f = front(s);
        Holder<DamageType> type = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CLAYMORE_DAMAGE);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(Balance.CLAY_RANGE))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 to = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            if (!inCone(c, f, to, Balance.CLAY_RANGE, Balance.CLAY_CONE_DEG) || !sees(sl, c, to)) continue;
            double q = 1.0 - c.distanceTo(to) / Balance.CLAY_RANGE;
            if (e.hurt(new DamageSource(type, c), (float) Math.max(2.0, Balance.CLAY_DAMAGE * q))) {
                Vec3 push = to.subtract(c).normalize().scale(0.6 * q + 0.2);
                e.push(push.x, 0.15 + push.y * 0.3, push.z);
                e.hurtMarked = true;
            }
        }

        var r = sl.random;
        for (int i = 0; i < 70; i++) {
            Vec3 d = f.add((r.nextDouble() - 0.5) * 1.3, (r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 1.3).normalize();
            double v = 0.9 + r.nextDouble() * 0.9;
            sl.sendParticles(i % 3 == 0 ? ParticleTypes.SMOKE : ParticleTypes.CRIT, c.x, c.y, c.z, 0, d.x, d.y, d.z, v);
        }
        sl.explode(null, c.x, c.y, c.z, Balance.CLAY_POWER, Level.ExplosionInteraction.NONE);
        sl.playSound(null, c.x, c.y, c.z, WmdSounds.CLAY_BLAST.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MineBlockEntity be) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putLong("placed", placedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("placed")) placedAt = tag.getLong("placed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ownerData.write(tag);
        tag.putLong("placed", placedAt);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
