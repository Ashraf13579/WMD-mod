package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class TorpedoNetBlockEntity extends BlockEntity {

    private UUID owner;
    private int len = 1, depth = 1;

    boolean rolling;

    private long seenAt = Long.MIN_VALUE;
    private boolean[] intact = new boolean[0];
    private boolean surfaced;
    private int bedLow, bedHigh;

    public TorpedoNetBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.TORPEDO_NET_BE.get(), pos, state);
    }

    Direction.Axis axis() {
        BlockState s = getBlockState();
        return s.hasProperty(TorpedoNetBlock.AXIS) ? s.getValue(TorpedoNetBlock.AXIS) : Direction.Axis.X;
    }

    int len() { return len; }
    int depth() { return depth; }

    BlockPos cell(int i, int j) {
        return axis() == Direction.Axis.X ? worldPosition.offset(i, j, 0) : worldPosition.offset(0, j, i);
    }

    boolean contains(BlockPos p) {
        int i = axis() == Direction.Axis.X ? p.getX() - worldPosition.getX() : p.getZ() - worldPosition.getZ();
        int across = axis() == Direction.Axis.X ? p.getZ() - worldPosition.getZ() : p.getX() - worldPosition.getX();
        int j = p.getY() - worldPosition.getY();
        return across == 0 && i >= 0 && i < len && j >= 0 && j < depth;
    }

    AABB netBox() {
        BlockPos b = cell(len - 1, depth - 1);
        return new AABB(Math.min(worldPosition.getX(), b.getX()), worldPosition.getY(), Math.min(worldPosition.getZ(), b.getZ()),
                Math.max(worldPosition.getX(), b.getX()) + 1, b.getY() + 1, Math.max(worldPosition.getZ(), b.getZ()) + 1);
    }

    void setup(UUID owner, int len, int depth) {
        this.owner = owner; this.len = len; this.depth = depth;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    static TorpedoNetBlockEntity find(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!TorpedoNetBlock.placed(s)) return null;
        Direction.Axis a = s.getValue(TorpedoNetBlock.AXIS);
        for (int j = 0; j < Balance.NET_MAX_DEPTH; j++) {
            for (int i = 0; i < Balance.NET_MAX_LEN; i++) {
                BlockPos q = a == Direction.Axis.X ? p.offset(-i, -j, 0) : p.offset(0, -j, -i);
                BlockState qs = level.getBlockState(q);
                if (qs.getBlock() instanceof TorpedoNetBlock && qs.getValue(TorpedoNetBlock.CTRL) && qs.getValue(TorpedoNetBlock.AXIS) == a
                        && level.getBlockEntity(q) instanceof TorpedoNetBlockEntity be && be.contains(p)) return be;
            }
        }
        return null;
    }

    void rollUp(ServerLevel sl, BlockPos broken, Player player) {
        if (rolling) return;
        rolling = true;
        int intactN = 0, torn = 0;
        BlockPos ctrl = worldPosition;
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < len; i++) {
                BlockPos q = cell(i, j);
                BlockState s = sl.getBlockState(q);
                if (TorpedoNetBlock.solid(s)) intactN++; else torn++;
                if (!TorpedoNetBlock.placed(s) || q.equals(broken) || q.equals(ctrl)) continue;
                sl.setBlock(q, TorpedoNetBlock.water(s), 3);
            }
        }
        if (!ctrl.equals(broken)) sl.setBlock(ctrl, TorpedoNetBlock.water(sl.getBlockState(ctrl)), 3);
        int back = (intactN + Balance.NET_AREA_PER_ITEM - 1) / Balance.NET_AREA_PER_ITEM;
        if (player != null && !player.getAbilities().instabuild) {
            for (int left = back; left > 0; ) {
                int n = Math.min(left, 64);
                ItemStack st = new ItemStack(Wmd.TORPEDO_NET_ITEM.get(), n);
                if (!player.getInventory().add(st)) player.drop(st, false);
                left -= n;
            }
        }
        if (player != null) player.displayClientMessage(Component.translatable("message.wmd.torpedo_net.rolled", player.getAbilities().instabuild ? 0 : back, torn).withStyle(ChatFormatting.AQUA), true);
        sl.playSound(null, broken, WmdSounds.NET_DEPLOY.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.85F);
        Wmd.LOGGER.info("Torpedo net {} x {} at {} rolled up by {}: {} intact, {} torn, {} item(s) back", len, depth, ctrl.toShortString(),
                player == null ? "?" : player.getName().getString(), intactN, torn, back);
    }

    void collapse(ServerLevel sl) {
        if (rolling) return;
        rolling = true;
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < len; i++) {
                BlockPos q = cell(i, j);
                BlockState s = sl.getBlockState(q);
                if (q.equals(worldPosition) || !TorpedoNetBlock.placed(s)) continue;
                sl.setBlock(q, TorpedoNetBlock.water(s), 3);
            }
        }
        Wmd.LOGGER.info("Torpedo net {} x {} at {}: its controller cell was removed, the net collapsed", len, depth, worldPosition.toShortString());
    }

    void refresh(Level lv) {
        long now = lv.getGameTime();
        if (now - seenAt < 10 && intact.length == len * depth) return;
        seenAt = now;
        if (intact.length != len * depth) intact = new boolean[len * depth];
        for (int j = 0; j < depth; j++) for (int i = 0; i < len; i++) intact[j * len + i] = TorpedoNetBlock.solid(lv.getBlockState(cell(i, j)));
        surfaced = lv.getFluidState(cell(len / 2, depth)).isEmpty();
        bedLow = bed(lv, 0);
        bedHigh = bed(lv, len - 1);
    }

    private int bed(Level lv, int i) {
        for (int k = 1; k <= 64; k++) {
            BlockPos q = cell(i, -k);
            if (!lv.getFluidState(q).is(FluidTags.WATER) || !lv.getBlockState(q).getCollisionShape(lv, q).isEmpty()) return k - 1;
        }
        return 64;
    }

    boolean intact(int i, int j) { return i >= 0 && j >= 0 && i < len && j < depth && j * len + i < intact.length && intact[j * len + i]; }
    boolean surfaced() { return surfaced; }
    int bed(boolean high) { return high ? bedHigh : bedLow; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("nl", len);
        tag.putInt("nd", depth);
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        len = Math.max(1, Math.min(Balance.NET_MAX_LEN, tag.getInt("nl")));
        depth = Math.max(1, Math.min(Balance.NET_MAX_DEPTH, tag.getInt("nd")));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        seenAt = Long.MIN_VALUE;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("nl", len);
        tag.putInt("nd", depth);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
