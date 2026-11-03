package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class C4ChargeBlockEntity extends BlockEntity implements Owned {

    private final OwnerData ownerData = new OwnerData();
    private long fuseEnd = -1, fuseStart = -1;

    public C4ChargeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.C4_CHARGE_BE.get(), pos, state); }

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

    public boolean armed() { return fuseEnd >= 0; }

    public float fuse(Level l, float pt) {
        if (fuseEnd < 0 || l == null || fuseEnd <= fuseStart) return -1.0F;
        return Math.min(1.0F, (l.getGameTime() + pt - fuseStart) / (float) (fuseEnd - fuseStart));
    }

    void arm(int ticks) {
        if (level == null) return;
        fuseStart = level.getGameTime();
        fuseEnd = fuseStart + ticks;
        level.playSound(null, worldPosition, WmdSounds.C4_BEEP.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        setChanged();
        sync();
    }

    void disarm() {
        fuseEnd = fuseStart = -1;
        if (level != null) level.playSound(null, worldPosition, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 1.0F, 1.2F);
        setChanged();
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, C4ChargeBlockEntity be) {
        if (be.fuseEnd < 0 || !(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime(), left = be.fuseEnd - now;
        if (left > 0 && left % (left > 60 ? 20 : left > 20 ? 8 : 3) == 0) sl.playSound(null, pos, WmdSounds.C4_BEEP.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        if (left <= 0) breach(sl, pos, state);
    }

    static void breach(ServerLevel sl, BlockPos pos, BlockState state) {
        Direction face = Machines.mount(state);
        Direction in = face.getOpposite();
        Vec3 c = Vec3.atCenterOf(pos);
        sl.removeBlock(pos, false);
        BlockPos target = pos.relative(in);
        Direction a = in.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
        Direction b = in.getAxis() == Direction.Axis.Y ? Direction.SOUTH : in.getClockWise();
        for (int d = 0; d < Balance.C4_DEPTH; d++) for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
            BlockPos p = target.relative(in, d).relative(a, i).relative(b, j);
            if (!sl.isInWorldBounds(p) || !sl.isLoaded(p)) continue;
            BlockState s = sl.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(sl, p) < 0) continue;
            if (Hardened.rating(sl, p, s) > Balance.C4_RATING) continue;
            if (!Config.Server.canBreak(sl, p)) continue;
            CcAlarms.exploded(sl, p);
            sl.destroyBlock(p, false);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.4, 0.4, 0.4, 0.0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.05);
        sl.explode(null, c.x, c.y, c.z, Balance.C4_POWER, Level.ExplosionInteraction.NONE);
        Collapse.queueAt(sl, target, Balance.C4_COLLAPSE_R);
        Aftermath.dust(sl, c.x, c.y, c.z);
        sl.playSound(null, c.x, c.y, c.z, WmdSounds.C4_BREACH.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putLong("fuse", fuseEnd);
        tag.putLong("fuse0", fuseStart);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("fuse")) fuseEnd = tag.getLong("fuse");
        if (tag.contains("fuse0")) fuseStart = tag.getLong("fuse0");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ownerData.write(tag);
        tag.putLong("fuse", fuseEnd);
        tag.putLong("fuse0", fuseStart);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
