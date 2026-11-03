package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CamoNetBlockEntity extends BlockEntity {

    static final double MARGIN = 0.35, POLE_START = 3.0 / 16.0, POLE_TOP = 4.2 / 16.0;

    private boolean has, torn;
    private int x0, z0, x1, z1, baseY, topY;
    private BlockPos target = BlockPos.ZERO;
    private boolean tooBig;
    private boolean checked;

    private boolean wallMounted;

    public CamoNetBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CAMO_NET_BE.get(), pos, state); }

    protected CamoNetBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    public boolean thermal() { return getBlockState().getBlock() instanceof ThermalNetBlock; }

    int span() { return thermal() ? Balance.THERMAL_MAX_SPAN : Balance.CAMO_MAX_SPAN; }

    public boolean has() { return has; }
    public boolean torn() { return torn; }
    public int x0() { return x0; }
    public int z0() { return z0; }
    public int x1() { return x1; }
    public int z1() { return z1; }
    public int baseY() { return baseY; }
    public int topY() { return topY; }

    public int poleSections() { return Math.max(1, (int) Math.ceil(topY + 1 + 0.75 - (baseY + POLE_START) - POLE_TOP)); }

    public double roofY() { return baseY + POLE_START + poleSections() + POLE_TOP; }

    boolean covers(BlockPos p) {
        return has && !torn && p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1 && p.getY() >= baseY - 1 && p.getY() <= topY + 1;
    }

    boolean coversColumn(int x, int z) { return has && !torn && x >= x0 && x <= x1 && z >= z0 && z <= z1; }

    boolean hitBy(Vec3 pos, double reach) {
        if (!has || torn) return false;
        double cx = Math.max(x0 - MARGIN, Math.min(x1 + 1 + MARGIN, pos.x)), cz = Math.max(z0 - MARGIN, Math.min(z1 + 1 + MARGIN, pos.z));
        double cy = Math.max(baseY, Math.min(roofY(), pos.y));
        return pos.distanceToSqr(cx, cy, cz) <= reach * reach;
    }

    void tear() {
        if (torn || level == null) return;
        torn = true;
        level.playSound(null, worldPosition, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CamoNetBlockEntity be) {
        Camo.join(be);
        if (!be.checked || (level.getGameTime() + pos.asLong()) % 40 == 0) { be.checked = true; be.refresh(); }
    }

    private Direction facing() { BlockState s = getBlockState(); return s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING) : Direction.NORTH; }

    void refresh() {
        if (level == null || level.isClientSide) return;
        boolean[] big = { false };
        boolean floor = Machines.mount(getBlockState()) == Direction.UP;
        wallMounted = !floor;
        int[] m = floor ? Camo.machineFor(level, worldPosition, facing(), span(), big) : null;
        boolean nh = m != null;
        int nx0 = nh ? m[3] : 0, ny0 = nh ? m[4] : 0, nz0 = nh ? m[5] : 0, nx1 = nh ? m[6] : 0, ny1 = nh ? m[7] : 0, nz1 = nh ? m[8] : 0;
        BlockPos nt = nh ? new BlockPos(m[0], m[1], m[2]) : BlockPos.ZERO;
        if (nh == has && nx0 == x0 && nz0 == z0 && nx1 == x1 && nz1 == z1 && ny0 == baseY && ny1 == topY && nt.equals(target) && big[0] == tooBig) return;
        if (has && (!nh || !nt.equals(target))) torn = false;
        has = nh; x0 = nx0; z0 = nz0; x1 = nx1; z1 = nz1; baseY = ny0; topY = ny1; target = nt; tooBig = big[0];
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    Component status() {
        String k = thermal() ? "message.wmd.thermal." : "message.wmd.camo.";
        if (!has && wallMounted) return Component.translatable("message.wmd.camo.not_floor");
        if (!has) return Component.translatable(k + (tooBig ? "too_big" : "none"), span());
        Component name = level == null ? Component.empty() : Component.translatable(level.getBlockState(target).getBlock().getDescriptionId());
        return Component.translatable(k + (torn ? "torn" : "covering"), name, x1 - x0 + 1, z1 - z0 + 1);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Camo.leave(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        Camo.leave(this);
    }

    private void write(CompoundTag tag) {
        tag.putBoolean("has", has);
        tag.putBoolean("torn", torn);
        tag.putIntArray("box", new int[] { x0, baseY, z0, x1, topY, z1 });
        tag.putLong("target", target.asLong());
        tag.putBoolean("big", tooBig);
    }

    private void read(CompoundTag tag) {
        has = tag.getBoolean("has");
        torn = tag.getBoolean("torn");
        int[] b = tag.getIntArray("box");
        if (b.length == 6) { x0 = b[0]; baseY = b[1]; z0 = b[2]; x1 = b[3]; topY = b[4]; z1 = b[5]; } else has = false;
        target = BlockPos.of(tag.getLong("target"));
        tooBig = tag.getBoolean("big");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
