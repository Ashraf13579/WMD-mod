package com.ashraf.wmd;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ControlSurfaceBlock extends BaseEntityBlock {

    public static final MapCodec<ControlSurfaceBlock> CODEC = simpleCodec(ControlSurfaceBlock::new);

    private static final Map<ResourceKey<Level>, Map<Long, Float>> DEFLECTION = Statics.server(new ConcurrentHashMap<>());

    public static float deflection(ResourceKey<Level> dim, BlockPos pos) {
        if (!AirGate.on()) return 0.0F;
        Map<Long, Float> m = DEFLECTION.get(dim);
        Float f = m == null ? null : m.get(pos.asLong());
        return f == null ? 0.0F : f;
    }

    public ControlSurfaceBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(WingParts.FACING, net.minecraft.core.Direction.NORTH).setValue(WingParts.FIN, false)
                .setValue(WingParts.ROLE, WingParts.Role.AILERON).setValue(WingParts.LEVEL, WingParts.Level.MID).setValue(WingParts.POWER, 0));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
        return state.setValue(WingParts.FACING, rot.rotate(state.getValue(WingParts.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror m) {
        if (m == net.minecraft.world.level.block.Mirror.NONE) return state;
        BlockState s = state.setValue(WingParts.FACING, m.mirror(state.getValue(WingParts.FACING)));
        if (s.hasProperty(WingParts.MIRROR)) s = s.setValue(WingParts.MIRROR, !s.getValue(WingParts.MIRROR));
        return s;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(WingParts.FACING, WingParts.FIN, WingParts.ROLE, WingParts.LEVEL, WingParts.POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = WingParts.placed(defaultBlockState(), ctx);
        if (s.getValue(WingParts.FIN)) s = s.setValue(WingParts.ROLE, WingParts.Role.RUDDER);
        return s.setValue(WingParts.POWER, ctx.getLevel().getBestNeighborSignal(ctx.getClickedPos()));
    }

    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return WingParts.shape(s.getValue(WingParts.FACING), s.getValue(WingParts.FIN), s.getValue(WingParts.LEVEL));
    }

    @Override
    protected void neighborChanged(BlockState s, Level level, BlockPos pos, Block b, BlockPos from, boolean moving) {
        int p = level.getBestNeighborSignal(pos);
        if (p != s.getValue(WingParts.POWER)) level.setBlock(pos, s.setValue(WingParts.POWER, p), 2);
        super.neighborChanged(s, level, pos, b, from, moving);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BE(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AirBlocks.CONTROL_SURFACE_BE.get()) return null;
        return level.isClientSide ? (l, p, s, be) -> ((BE) be).clientTick() : (l, p, s, be) -> ((BE) be).serverTick((ServerLevel) l);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (held.is(Wmd.WRENCH.get()) && !player.isShiftKeyDown()) {
            WingParts.Role r = WingParts.Role.values()[(state.getValue(WingParts.ROLE).ordinal() + 1) % WingParts.Role.values().length];
            level.setBlock(pos, state.setValue(WingParts.ROLE, r), 3);
            level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
            player.displayClientMessage(Component.translatable("message.wmd.wing.role", Component.translatable("wing.wmd.role." + r.getSerializedName())), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (CopycatState.interact(level, player, pos, be)) return ItemInteractionResult.SUCCESS;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            Map<Long, Float> m = DEFLECTION.get(level.dimension());
            if (m != null) m.remove(pos.asLong());
            if (!ShipRelink.shipMove(level, moved) && level.getBlockEntity(pos) instanceof BE be) {
                ItemStack k = be.copycat().takeItem();
                if (!k.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, k);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.control_surface").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }

    public static class BE extends BlockEntity implements CopycatState.Host {
        private final CopycatState copycat = new CopycatState();
        private float current, target;
        private float prev;
        private int sent = Integer.MIN_VALUE;
        private UUID ship;
        private int side = 1, fore = 1;

        public BE(BlockPos pos, BlockState state) { super(AirBlocks.CONTROL_SURFACE_BE.get(), pos, state); }

        @Override public CopycatState copycat() { return copycat; }
        @Override public void copycatChanged() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

        public float shown(float pt) { return prev + (current - prev) * pt; }

        void serverTick(ServerLevel sl) {
            if (!AirGate.on()) { current = target = 0.0F; return; }
            long now = sl.getGameTime();
            BlockState s = getBlockState();
            if ((now + (worldPosition.asLong() & 31L)) % Balance.WING_CS_SHIP_TICKS == 0) refreshShip(sl);
            float max = s.getValue(WingParts.ROLE) == WingParts.Role.RUDDER ? Balance.WING_CS_RUDDER_MAX : Balance.WING_CS_MAX;
            if ((now + (worldPosition.asLong() & 3L)) % 4 == 0) runSig = rowSignal(sl, worldPosition, s);
            int sig = runSig;
            float t = 0.0F;
            ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);

            if (s.getValue(WingParts.ROLE) == WingParts.Role.FLAP) t = sig / 15.0F * Balance.WING_CS_FLAP_MAX;
            else if (o == null && sig > 0) t = Mth.clamp((sig - 7.5F) / 7.0F, -1.0F, 1.0F) * max;
            else if (ship != null) {
                if (o != null) {
                    float a = switch (s.getValue(WingParts.ROLE)) {
                        case AILERON -> -o.roll() * side;
                        case ELEVATOR -> o.planes() * fore;
                        case RUDDER -> o.rudder() * fore;
                        case ELEVON -> Mth.clamp(o.planes() * fore - o.roll() * side, -1.0F, 1.0F);
                        default -> 0.0F;
                    };
                    t = a * max;
                }
            }
            target = t;
            float step = Balance.WING_CS_RATE / 20.0F;
            current += Mth.clamp(target - current, -step, step);
            Map<Long, Float> m = DEFLECTION.computeIfAbsent(sl.dimension(), k -> new ConcurrentHashMap<>());
            if (Math.abs(current) < 1.0E-3F) m.remove(worldPosition.asLong()); else m.put(worldPosition.asLong(), current);
            int q = Math.round(target);
            if (q != sent) { sent = q; sl.sendBlockUpdated(worldPosition, s, s, 2); }
        }

        private int runSig;

        static int rowSignal(Level lv, BlockPos pos, BlockState s) {
            int best = s.getValue(WingParts.POWER);
            Direction span = s.getValue(WingParts.FIN) ? Direction.UP : s.getValue(WingParts.FACING).getClockWise();
            for (Direction d : new Direction[] { span, span.getOpposite() }) {
                BlockPos q = pos;
                for (int i = 0; i < Balance.WING_ROW_MAX; i++) {
                    q = q.relative(d);
                    BlockState o = lv.getBlockState(q);
                    if (!o.is(s.getBlock()) || o.getValue(WingParts.ROLE) != s.getValue(WingParts.ROLE) || o.getValue(WingParts.FACING) != s.getValue(WingParts.FACING)
                            || o.getValue(WingParts.FIN) != s.getValue(WingParts.FIN) || o.getValue(WingParts.LEVEL) != s.getValue(WingParts.LEVEL)) break;
                    best = Math.max(best, o.getValue(WingParts.POWER));
                }
            }
            return best;
        }

        private void refreshShip(ServerLevel sl) {
            ship = SableBridge.shipIdAt(sl, worldPosition);
            SableBridge.ShipInfo si = ship == null ? null : SableBridge.shipInfo(sl, ship);
            Vec3 c = si == null ? null : SableBridge.toShip(sl, si, si.worldCenter());
            if (c == null) return;
            Vec3 d = Vec3.atCenterOf(worldPosition).subtract(c), f = WingParts.forward(getBlockState());
            Vec3 right = new Vec3(-f.z, 0.0, f.x);
            side = d.dot(right) >= 0.0 ? 1 : -1;
            fore = d.dot(f) <= 0.0 ? 1 : -1;
        }

        void clientTick() {
            prev = current;
            float step = Balance.WING_CS_RATE / 20.0F;
            current += Mth.clamp(target - current, -step, step);
        }

        @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) { super.saveAdditional(tag, r); copycat.save(tag, r); }
        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
            super.loadAdditional(tag, r);
            copycat.load(tag, r, level != null && level.isClientSide);
            if (tag.contains("csT")) target = tag.getFloat("csT");
        }
        @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { CompoundTag t = super.getUpdateTag(r); copycat.putSync(t); t.putFloat("csT", target); return t; }
        @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    }
}
