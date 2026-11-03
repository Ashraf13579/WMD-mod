package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class SeismicScannerBlockEntity extends CctvDeviceEntity {

    public static final int KIND_MACHINE = 0, KIND_MINE = 1, KIND_C4 = 2, KIND_JAMMER = 3, KIND_CAMERA = 4;

    private long sweepAt = -1;
    private UUID sweeper;

    public SeismicScannerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SEISMIC_SCANNER_BE.get(), pos, state, 0);
    }

    private int sweepTicks() { return Balance.SEIS_THUMPS * Balance.SEIS_THUMP_TICKS; }

    public float lift(Level l, float pt) {
        if (sweepAt < 0 || l == null) return 0.0F;
        double t = l.getGameTime() - sweepAt + pt;
        if (t < 0 || t >= sweepTicks()) return 0.0F;
        double ph = (t % Balance.SEIS_THUMP_TICKS) / Balance.SEIS_THUMP_TICKS;
        return ph < 0.85 ? (float) (ph / 0.85) : 1.0F;
    }

    public boolean sweeping(Level l) { return sweepAt >= 0 && l != null && l.getGameTime() - sweepAt < sweepTicks(); }

    void start(ServerPlayer p) {
        long now = p.level().getGameTime();
        if (sweepAt >= 0 && now - sweepAt < sweepTicks() + Balance.SEIS_COOLDOWN) {
            long left = (sweepAt + sweepTicks() + Balance.SEIS_COOLDOWN - now) / 20;
            p.displayClientMessage(Component.translatable("message.wmd.seismic.wait", left), true);
            return;
        }
        sweepAt = now;
        sweeper = p.getUUID();
        setChanged();
        sync();
        p.displayClientMessage(Component.translatable("message.wmd.seismic.start", Balance.SEIS_RANGE), true);
    }

    @Override
    public void deviceTick(ServerLevel sl, long now) {
        if (sweepAt < 0) return;
        long t = now - sweepAt;
        if (t > 0 && t <= sweepTicks() && t % Balance.SEIS_THUMP_TICKS == 0) {
            Vec3 c = Vec3.atBottomCenterOf(worldPosition);
            sl.playSound(null, worldPosition, WmdSounds.SEIS_THUMP.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
            BlockState under = sl.getBlockState(worldPosition.below());
            if (!under.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.1, c.z, 24, 0.5, 0.05, 0.5, 0.15);
            sl.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.1, c.z, 4, 0.4, 0.05, 0.4, 0.02);
        }
        if (t >= 0 && t < sweepTicks() && t % Balance.SEIS_THUMP_TICKS == 2) sl.playSound(null, worldPosition, WmdSounds.SEIS_HOIST.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        if (t == sweepTicks()) report(sl);
    }

    private void report(ServerLevel sl) {
        ServerPlayer p = sweeper == null ? null : sl.getServer().getPlayerList().getPlayer(sweeper);
        if (p == null || p.level() != sl) return;
        Direction d = Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.NORTH);
        Vec3 o = Vec3.atCenterOf(worldPosition), f = new Vec3(d.getStepX(), 0, d.getStepZ());
        double cos = Math.cos(Math.toRadians(Balance.SEIS_HALF_DEG)), r = Balance.SEIS_RANGE;
        List<SeismicResultPayload.Hit> hits = new ArrayList<>();
        int c0x = (worldPosition.getX() - (int) r) >> 4, c1x = (worldPosition.getX() + (int) r) >> 4, c0z = (worldPosition.getZ() - (int) r) >> 4, c1z = (worldPosition.getZ() + (int) r) >> 4;
        for (int cx = c0x; cx <= c1x && hits.size() < 64; cx++) for (int cz = c0z; cz <= c1z && hits.size() < 64; cz++) {
            LevelChunk ch = sl.getChunkSource().getChunkNow(cx, cz);
            if (ch == null) continue;
            for (BlockEntity be : ch.getBlockEntities().values()) {
                int kind = kind(be);
                if (kind < 0 || !(be instanceof Owned ow) || hits.size() >= 64) continue;
                if (ow.getOwner() != null && (ow.getOwner().equals(getOwner()) || Access.trusts(this, ow.getOwner()))) continue;
                Vec3 v = Vec3.atCenterOf(be.getBlockPos()).subtract(o), h = new Vec3(v.x, 0, v.z);
                double len = h.length();
                if (len > r || (len > 2.0 && h.dot(f) / len < cos)) continue;
                hits.add(new SeismicResultPayload.Hit(be.getBlockPos().asLong(), kind));
            }
        }
        PacketDistributor.sendToPlayer(p, new SeismicResultPayload(List.copyOf(hits)));
        p.displayClientMessage(Component.translatable(hits.isEmpty() ? "message.wmd.seismic.none" : "message.wmd.seismic.found", hits.size()), false);
        sl.playSound(null, worldPosition, WmdSounds.SEIS_RESULT.get(), SoundSource.BLOCKS, 0.9F, hits.isEmpty() ? 0.8F : 1.0F);
    }

    static int kind(BlockEntity be) {
        if (be instanceof MineBlockEntity) return KIND_MINE;
        if (be instanceof C4ChargeBlockEntity) return KIND_C4;
        if (be instanceof PortableJammerBlockEntity) return KIND_JAMMER;
        if (be instanceof CctvCameraBlockEntity) return KIND_CAMERA;
        if (be instanceof MachinePartBlockEntity) return -1;
        return be instanceof Owned ? KIND_MACHINE : -1;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.seismic.how", Balance.SEIS_RANGE, (int) Balance.SEIS_HALF_DEG * 2),
                Component.translatable("gui.wmd.info.seismic.use"));
    }

    @Override protected String detail() { return level != null && sweeping(level) ? "SWEEPING" : "READY"; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("sweep", sweepAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("sweep")) sweepAt = tag.getLong("sweep");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("sweep", sweepAt);
        return tag;
    }
}
