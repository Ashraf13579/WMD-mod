package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class PortableJammerBlockEntity extends CctvDeviceEntity {

    private int charge = Balance.JAM_CHARGE;
    private long placedAt = -1;

    public PortableJammerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.PORTABLE_JAMMER_BE.get(), pos, state, 0);
    }

    public int charge() { return charge; }
    void setCharge(int c) { charge = Mth.clamp(c, 0, Balance.JAM_CHARGE); setChanged(); }

    public float deployed(Level l, float pt) {
        if (placedAt < 0 || l == null) return 0.0F;
        return Mth.clamp((l.getGameTime() - placedAt + pt) / Balance.JAM_DEPLOY, 0.0F, 1.0F);
    }

    public boolean jamming() { return isRunning() && charge > 0 && level != null && deployed(level, 0.0F) >= 1.0F; }

    public static boolean jams(Level level, BlockPos pos, UUID victim) {
        if (level == null) return false;
        for (PortableJammerBlockEntity j : CctvNet.near(level, PortableJammerBlockEntity.class, Vec3.atCenterOf(pos), Balance.JAM_RADIUS))
            if (j.jamming() && (victim == null || j.getOwner() == null || !victim.equals(j.getOwner()))) return true;
        return false;
    }

    private static final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap CLIENT_JAM = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

    public static boolean jamsCached(Level level, BlockPos pos, UUID victim) {
        if (level == null) return false;
        long now = level.getGameTime(), v = CLIENT_JAM.getOrDefault(pos.asLong(), Long.MIN_VALUE);
        if (v != Long.MIN_VALUE && now >= (v >> 1) && now - (v >> 1) < 10) return (v & 1L) != 0;
        boolean j = jams(level, pos, victim);
        if (CLIENT_JAM.size() > 512) CLIENT_JAM.clear();
        CLIENT_JAM.put(pos.asLong(), (now << 1) | (j ? 1L : 0L));
        return j;
    }

    public static void clearClientStatics() { CLIENT_JAM.clear(); }

    @Override
    protected boolean work() {
        if (charge <= 0) return false;
        charge--;
        if (charge % 200 == 0) setChanged();
        return true;
    }

    @Override
    public void deviceTick(ServerLevel sl, long now) {
        if (placedAt < 0) {
            placedAt = now;
            setChanged();
            sync();
            sl.playSound(null, worldPosition, WmdSounds.JAM_DEPLOY.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        }
    }

    @Override
    public void deviceClient(Level level) {
        if (!jamming() || level.getGameTime() % 40 != 0) return;
        var r = level.random;
        for (int k = 0; k < 3; k++)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.6,
                    worldPosition.getY() + 0.9 + r.nextDouble() * 0.4, worldPosition.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.jammer.charge", charge * 100 / Balance.JAM_CHARGE, charge / 20 / 60, (charge / 20) % 60),
                Component.translatable("gui.wmd.info.jammer.how", Balance.JAM_RADIUS));
    }

    @Override protected String detail() { return jamming() ? "JAMMING" : charge <= 0 ? "BATTERY EMPTY" : "UNFOLDING"; }
    @Override protected net.minecraft.sounds.SoundEvent runSound() { return WmdSounds.JAM_LOOP.get(); }
    @Override protected float soundVolume() { return 0.35F; }
    @Override protected float soundPitch() { return 1.0F; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("charge", charge);
        tag.putLong("placed", placedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("charge")) charge = tag.getInt("charge");
        if (tag.contains("placed")) placedAt = tag.getLong("placed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("charge", charge);
        tag.putLong("placed", placedAt);
        return tag;
    }
}
