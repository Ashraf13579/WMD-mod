package com.ashraf.wmd;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CrewCapsuleBlockEntity extends BlockEntity {

    private UUID owner;
    private ResourceKey<Level> padDim = Level.OVERWORLD;
    private BlockPos pad = BlockPos.ZERO;
    private boolean landed;
    private int boardSlot = -1, boardPort = -1;
    private long cutStart;

    public CrewCapsuleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CREW_CAPSULE_BE.get(), pos, state); }

    void setUp(UUID owner, ResourceKey<Level> padDim, BlockPos pad) {
        this.owner = owner; this.padDim = padDim; this.pad = pad;
        setChanged();
    }

    void setLanded() { landed = true; setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public UUID owner() { return owner; }
    public ResourceKey<Level> padDim() { return padDim; }
    public BlockPos pad() { return pad; }
    public boolean landed() { return landed; }
    public int boardSlot() { return boardSlot; }
    public int boardPort() { return boardPort; }
    public long cutStart() { return cutStart; }
    public boolean cutDone(long now) { return boardSlot >= 0 && now >= cutStart + Balance.BOARD_CUT_TICKS; }

    void setBoarding(int slot, int port, long now) {
        boardSlot = slot; boardPort = port; cutStart = now;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && boardSlot >= 0 && !cutDone(level.getGameTime())) Boarding.resume(worldPosition, boardSlot, boardPort, cutStart);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("pad_dim", padDim.location().toString());
        tag.putLong("pad", pad.asLong());
        tag.putBoolean("landed", landed);
        tag.putInt("board_slot", boardSlot); tag.putInt("board_port", boardPort); tag.putLong("cut_start", cutStart);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ResourceLocation d = ResourceLocation.tryParse(tag.getString("pad_dim"));
        padDim = d == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, d);
        pad = BlockPos.of(tag.getLong("pad"));
        landed = tag.getBoolean("landed");
        boardSlot = tag.contains("board_slot") ? tag.getInt("board_slot") : -1;
        boardPort = tag.contains("board_port") ? tag.getInt("board_port") : -1;
        cutStart = tag.getLong("cut_start");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("landed", landed);
        t.putInt("board_slot", boardSlot); t.putInt("board_port", boardPort); t.putLong("cut_start", cutStart);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
