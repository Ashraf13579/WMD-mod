package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class WingSectionBlock extends BaseEntityBlock {

    public static final MapCodec<WingSectionBlock> CODEC = simpleCodec(WingSectionBlock::new);

    public WingSectionBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(WingParts.FACING, net.minecraft.core.Direction.NORTH).setValue(WingParts.FIN, false)
                .setValue(WingParts.SHAPE, WingParts.Shape.PANEL).setValue(WingParts.MIRROR, false).setValue(WingParts.LEVEL, WingParts.Level.MID)
                .setValue(WingParts.THICK, WingParts.Thick.THIN).setValue(WingParts.GROW, WingParts.Grow.BOTH));
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
        b.add(WingParts.FACING, WingParts.FIN, WingParts.SHAPE, WingParts.MIRROR, WingParts.LEVEL, WingParts.THICK, WingParts.GROW);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return WingParts.placed(defaultBlockState(), ctx); }

    static boolean copycatLayer(ItemStack held) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        return id != null && "copycats".equals(id.getNamespace()) && "copycat_layer".equals(id.getPath());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            if (hit.getDirection().getAxis() != Direction.Axis.Y || !(level.getBlockEntity(pos) instanceof BE be)) return InteractionResult.PASS;
            boolean top = hit.getDirection() == Direction.UP;
            int px = halfPx(be.halves, top);
            if (halfSide(be.halves, top) == 0 || px <= 0) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            be.setHalves(withHalf(be.halves, top, halfSide(be.halves, top), px - 2));
            level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 0.6F, 1.4F);
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState n = state.setValue(WingParts.THICK, state.getValue(WingParts.THICK).next()).setValue(WingParts.GROW, WingParts.Grow.BOTH);
        level.setBlock(pos, n, 3);
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.2F);
        player.displayClientMessage(Component.translatable("message.wmd.wing.thick", n.getValue(WingParts.THICK).pxText()), true);
        return InteractionResult.SUCCESS;
    }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        VoxelShape base = getShapeBase(s);
        if (s.getValue(WingParts.FIN) || !(l.getBlockEntity(p) instanceof BE be) || be.halves == 0) return base;
        net.minecraft.world.phys.AABB w = base.bounds();
        for (float[] k : halfBoxes(be.halves, (float) w.minY, (float) w.maxY))
            base = net.minecraft.world.phys.shapes.Shapes.or(base, net.minecraft.world.phys.shapes.Shapes.box(k[0], k[1], k[2], k[3], k[4], k[5]));
        return base;
    }

    static VoxelShape getShapeBase(BlockState s) {
        return WingParts.shape(s.getValue(WingParts.FACING), s.getValue(WingParts.FIN), s.getValue(WingParts.LEVEL), s.getValue(WingParts.THICK), s.getValue(WingParts.GROW));
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BE(pos, state); }

    static BlockState nextShape(BlockState s) {
        WingParts.Shape sh = s.getValue(WingParts.SHAPE);
        boolean m = s.getValue(WingParts.MIRROR);
        if (sh.handed() && !m) return s.setValue(WingParts.MIRROR, true);
        WingParts.Shape n = WingParts.Shape.values()[(sh.ordinal() + 1) % WingParts.Shape.values().length];
        return s.setValue(WingParts.SHAPE, n).setValue(WingParts.MIRROR, false);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        boolean ours = held.is(Wmd.WRENCH.get())
                || held.getItem() instanceof net.minecraft.world.item.BlockItem bi && ScrewPropellerBlockEntity.copyable(bi.getBlock().defaultBlockState(), level, pos);
        if (level.isClientSide) return ours ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (held.is(Wmd.WRENCH.get()) && !player.isShiftKeyDown()) {
            BlockState n = nextShape(state);
            level.setBlock(pos, n, 3);
            level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
            player.displayClientMessage(Component.translatable("message.wmd.wing.shape", Component.translatable("wing.wmd.shape." + n.getValue(WingParts.SHAPE).getSerializedName()),
                    Component.translatable(n.getValue(WingParts.MIRROR) ? "wing.wmd.left" : "wing.wmd.right")), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (CopycatState.interact(level, player, pos, be)) return ItemInteractionResult.SUCCESS;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this) && !ShipRelink.shipMove(level, moved) && level.getBlockEntity(pos) instanceof BE be) {
            ItemStack k = be.copycat().takeItem();
            if (!k.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, k);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.wing_section").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }

    public static final ModelProperty<BlockState> SKIN = new ModelProperty<>();

    public static final ModelProperty<Integer> HALVES = new ModelProperty<>();

    static int halfSide(int h, boolean top) { return (h >> (top ? 0 : 8)) & 7; }
    static int halfPx(int h, boolean top) { return (h >> (top ? 3 : 11)) & 31; }
    static int withHalf(int h, boolean top, int side, int px) {
        int sh = top ? 0 : 8;
        h &= ~(0xFF << sh);
        return px <= 0 || side == 0 ? h : h | ((side & 7) << sh) | ((px & 31) << (sh + 3));
    }

    static java.util.List<float[]> halfBoxes(int h, float y0, float y1) {
        java.util.List<float[]> l = new java.util.ArrayList<>(2);
        for (int t = 0; t < 2; t++) {
            boolean top = t == 0;
            int side = halfSide(h, top), px = halfPx(h, top);
            if (side == 0 || px <= 0) continue;
            float a = top ? y1 : Math.max(0.0F, y0 - px / 16.0F), c = top ? Math.min(1.0F, y1 + px / 16.0F) : y0;
            if (c - a < 1.0E-3F) continue;
            float x0 = side == 4 ? 0.0F : side == 2 ? 0.5F : 0.0F, x1 = side == 4 ? 0.5F : 1.0F, z0 = side == 3 ? 0.5F : 0.0F, z1 = side == 1 ? 0.5F : 1.0F;
            if (side == 2 || side == 4) { z0 = 0.0F; z1 = 1.0F; } else { x0 = 0.0F; x1 = 1.0F; }
            l.add(new float[] { x0, a, z0, x1, c, z1 });
        }
        return l;
    }

    static boolean stackLayer(Level level, BlockPos pos, BlockState state, Player player, BlockHitResult hit, ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof BE be)) return false;
        boolean layer = copycatLayer(held), half = copycatHalf(held);
        if (!layer && !half) return false;
        if (half) {
            if (state.getValue(WingParts.FIN) || hit.getDirection().getAxis() != Direction.Axis.Y) return false;
            boolean top = hit.getDirection() == Direction.UP;
            int side = clickedHalf(hit, pos), h = be.halves;
            int px = halfSide(h, top) == side ? halfPx(h, top) + 2 : 2;
            net.minecraft.world.phys.AABB w = getShapeBase(state).bounds();
            double room = (top ? 1.0 - w.maxY : w.minY) * 16.0;
            if (px > room + 0.01) { player.displayClientMessage(Component.translatable("message.wmd.wing.half_full"), true); return true; }
            be.setHalves(withHalf(h, top, side, px));
            level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
            player.displayClientMessage(Component.translatable("message.wmd.wing.half", px), true);
            return true;
        }
        WingParts.Thick t = state.getValue(WingParts.THICK), n = t.addLayer();
        if (n == t) { player.displayClientMessage(Component.translatable("message.wmd.wing.thick_max"), true); return true; }

        WingParts.Grow g = hit.getDirection() == Direction.UP ? WingParts.Grow.UP : hit.getDirection() == Direction.DOWN ? WingParts.Grow.DOWN : state.getValue(WingParts.GROW);
        level.setBlock(pos, state.setValue(WingParts.THICK, n).setValue(WingParts.GROW, g), 3);
        level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
        player.displayClientMessage(Component.translatable("message.wmd.wing.thick", n.pxText()), true);
        return true;
    }

    static boolean takesLayer(BlockState state, BlockHitResult hit, ItemStack held) {
        if (copycatLayer(held)) return true;
        return copycatHalf(held) && !state.getValue(WingParts.FIN) && hit.getDirection().getAxis() == Direction.Axis.Y;
    }

    static boolean copycatHalf(ItemStack held) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        return id != null && "copycats".equals(id.getNamespace()) && "copycat_half_layer".equals(id.getPath());
    }

    static int clickedHalf(BlockHitResult hit, BlockPos pos) {
        double dx = hit.getLocation().x - pos.getX() - 0.5, dz = hit.getLocation().z - pos.getZ() - 0.5;
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 2 : 4) : (dz > 0 ? 3 : 1);
    }

    public static class BE extends BlockEntity implements CopycatState.Host {
        private final CopycatState copycat = new CopycatState();

        int halves;

        public BE(BlockPos pos, BlockState state) { super(AirBlocks.WING_SECTION_BE.get(), pos, state); }

        void setHalves(int h) {
            if (h == halves) return;
            halves = h;
            copycatChanged();
        }

        @Override public CopycatState copycat() { return copycat; }

        @Override
        public void copycatChanged() {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        @Override public ModelData getModelData() {
            if (copycat.shown() == null && halves == 0) return ModelData.EMPTY;
            ModelData.Builder m = ModelData.builder();
            if (copycat.shown() != null) m.with(SKIN, copycat.shown());
            if (halves != 0) m.with(HALVES, halves);
            return m.build();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) { super.saveAdditional(tag, r); copycat.save(tag, r); if (halves != 0) tag.putInt("wsHalf", halves); }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
            super.loadAdditional(tag, r);
            copycat.load(tag, r, level != null && level.isClientSide);
            halves = tag.getInt("wsHalf");
        }

        @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { CompoundTag t = super.getUpdateTag(r); copycat.putSync(t); t.putInt("wsHalf", halves); return t; }
        @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

        @Override
        public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider r) {
            super.onDataPacket(net, pkt, r);
            requestModelDataUpdate();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }
}
