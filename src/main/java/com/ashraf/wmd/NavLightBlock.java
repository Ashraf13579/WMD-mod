package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public class NavLightBlock extends BaseEntityBlock {

    public static final MapCodec<NavLightBlock> CODEC = simpleCodec(NavLightBlock::new);

    public enum Spot implements StringRepresentable {
        CENTRE("centre", 0, 0), LEFT("left", -1, 0), RIGHT("right", 1, 0), UP("up", 0, 1), DOWN("down", 0, -1);
        private final String id;
        final int du, dv;
        Spot(String id, int du, int dv) { this.id = id; this.du = du; this.dv = dv; }
        @Override public String getSerializedName() { return id; }
    }

    public enum Func implements StringRepresentable {
        RED("red", 0), GREEN("green", 0), WHITE("white", 0), STROBE("strobe", 0), BEACON("beacon", 1), LANDING("landing", 2);
        private final String id;

        final int head;
        Func(String id, int head) { this.id = id; this.head = head; }
        @Override public String getSerializedName() { return id; }
        Func next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static final DirectionProperty ATTACH = DirectionProperty.create("attach");
    public static final DirectionProperty NOSE = DirectionProperty.create("nose");
    public static final EnumProperty<Spot> SPOT = EnumProperty.create("spot", Spot.class);
    public static final EnumProperty<Func> FUNC = EnumProperty.create("func", Func.class);

    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

    public static int lightOf(BlockState s) {
        if (!s.getValue(LIT)) return 0;
        return switch (s.getValue(FUNC)) { case LANDING -> 12; case BEACON -> 9; case STROBE -> 7; default -> 6; };
    }

    static final float SPOT_PX = 5.0F, THIN_AXIS = 0.75F;

    static final float[][] HEAD_BOX = { { 1.75F, 3.5F, 4.6F }, { 3.4F, 5.1F, 3.4F }, { 2.8F, 6.5F, 2.7F } };

    static final float[][] LENS_AT = { { 1.8F, 4.0F }, { 3.3F, 0.0F }, { 3.7F, 2.4F } };

    public NavLightBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ATTACH, Direction.DOWN).setValue(NOSE, Direction.NORTH).setValue(SPOT, Spot.CENTRE).setValue(FUNC, Func.RED).setValue(LIT, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(ATTACH, NOSE, SPOT, FUNC, LIT); }

    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NavLightBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AirBlocks.NAV_LIGHT_BE.get(), NavLightBlockEntity::serverTick);
    }

    static Direction[] faceAxes(Direction attach, Direction nose) {
        if (attach.getAxis().isHorizontal()) return new Direction[] { attach.getClockWise(), Direction.UP };
        Direction n = nose.getAxis().isHorizontal() ? nose : Direction.NORTH;
        return new Direction[] { n.getClockWise(), n };
    }

    static Direction defaultNose(Direction attach, Direction look) {
        if (attach.getAxis().isVertical()) return look.getAxis().isHorizontal() ? look : Direction.NORTH;
        return look.getAxis() != attach.getAxis() && look.getAxis().isHorizontal() ? look : attach.getClockWise();
    }

    static Direction nextNose(Direction attach, Direction nose) {
        Direction[] all = Direction.values();
        for (int i = 1; i <= 6; i++) {
            Direction d = all[(nose.ordinal() + i) % 6];
            if (d.getAxis() != attach.getAxis()) return d;
        }
        return nose;
    }

    static Spot spotAt(Vec3 click, BlockPos cell, Direction attach, Direction nose) {
        Direction[] uv = faceAxes(attach, nose);
        Vec3 d = click.subtract(Vec3.atCenterOf(cell));
        double u = d.x * uv[0].getStepX() + d.y * uv[0].getStepY() + d.z * uv[0].getStepZ();
        double v = d.x * uv[1].getStepX() + d.y * uv[1].getStepY() + d.z * uv[1].getStepZ();
        if (Math.max(Math.abs(u), Math.abs(v)) < 1.0 / 6.0) return Spot.CENTRE;
        if (Math.abs(u) >= Math.abs(v)) return u < 0 ? Spot.LEFT : Spot.RIGHT;
        return v < 0 ? Spot.DOWN : Spot.UP;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction attach = ctx.getClickedFace().getOpposite();
        Direction nose = defaultNose(attach, ctx.getHorizontalDirection());
        Spot spot = spotAt(ctx.getClickLocation(), ctx.getClickedPos(), attach, nose);
        return defaultBlockState().setValue(ATTACH, attach).setValue(NOSE, nose).setValue(SPOT, spot);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }

    static float[] frame(BlockGetter level, BlockPos pos, BlockState s) {
        Direction attach = s.getValue(ATTACH), nose = s.getValue(NOSE);
        if (nose.getAxis() == attach.getAxis()) nose = defaultNose(attach, Direction.NORTH);
        float ux = -attach.getStepX(), uy = -attach.getStepY(), uz = -attach.getStepZ();
        float fx = nose.getStepX(), fy = nose.getStepY(), fz = nose.getStepZ();
        float rx = fy * uz - fz * uy, ry = fz * ux - fx * uz, rz = fx * uy - fy * ux;
        Direction[] uv = faceAxes(attach, nose);
        Spot sp = s.getValue(SPOT);
        float k = SPOT_PX / 16.0F;
        float[] p = { 0.5F + attach.getStepX() * 0.5F, 0.5F + attach.getStepY() * 0.5F, 0.5F + attach.getStepZ() * 0.5F };
        for (int i = 0; i < 3; i++) p[i] += k * (sp.du * step(uv[0], i) + sp.dv * step(uv[1], i));
        AABB b = supportBox(level, pos, attach);
        if (b != null) {
            float gap = (float) switch (attach) {
                case DOWN -> 1.0 - b.maxY; case UP -> b.minY;
                case EAST -> b.minX; case WEST -> 1.0 - b.maxX;
                case SOUTH -> b.minZ; default -> 1.0 - b.maxZ;
            };
            if (gap > 0.001F && gap < 0.999F) { p[0] += attach.getStepX() * gap; p[1] += attach.getStepY() * gap; p[2] += attach.getStepZ() * gap; }
            for (int i = 0; i < 3; i++) {
                if (i == attach.getAxis().ordinal()) continue;
                double lo = i == 0 ? b.minX : i == 1 ? b.minY : b.minZ, hi = i == 0 ? b.maxX : i == 1 ? b.maxY : b.maxZ;
                if (hi - lo < THIN_AXIS) p[i] = (float) ((lo + hi) * 0.5);
            }
        }
        return new float[] { p[0], p[1], p[2], rx, ry, rz, ux, uy, uz, fx, fy, fz };
    }

    private static int step(Direction d, int axis) { return axis == 0 ? d.getStepX() : axis == 1 ? d.getStepY() : d.getStepZ(); }

    private static AABB supportBox(BlockGetter level, BlockPos pos, Direction attach) {
        if (level == null) return null;
        BlockPos at = pos.relative(attach);
        BlockState st = level.getBlockState(at);
        if (st.isAir() || st.getBlock() instanceof NavLightBlock) return null;
        VoxelShape sh;
        try { sh = st.getShape(level, at); } catch (RuntimeException e) { return null; }
        if (sh.isEmpty()) return null;
        AABB b = sh.bounds();
        if (b.minX <= 0.001 && b.minY <= 0.001 && b.minZ <= 0.001 && b.maxX >= 0.999 && b.maxY >= 0.999 && b.maxZ >= 0.999) return null;
        return b;
    }

    static float[] lensPoint(float[] f, Func func) {
        float[] l = LENS_AT[func.head];
        float up = l[0] / 16.0F, fw = l[1] / 16.0F;
        return new float[] { f[0] + f[6] * up + f[9] * fw, f[1] + f[7] * up + f[10] * fw, f[2] + f[8] * up + f[11] * fw };
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) {
        float[] f = frame(level, pos, s);
        float[] hb = HEAD_BOX[s.getValue(FUNC).head];
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float mx = ((i & 1) == 0 ? -hb[0] : hb[0]) / 16.0F, my = ((i & 2) == 0 ? 0.0F : hb[1]) / 16.0F, mz = ((i & 4) == 0 ? -hb[2] : hb[2]) / 16.0F;
            double x = f[0] + f[3] * mx + f[6] * my - f[9] * mz, y = f[1] + f[4] * mx + f[7] * my - f[10] * mz, z = f[2] + f[5] * mx + f[8] * my - f[11] * mz;
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z); x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
        }
        return Shapes.create(Math.max(-1.0, x0), Math.max(-1.0, y0), Math.max(-1.0, z0), Math.min(2.0, x1), Math.min(2.0, y1), Math.min(2.0, z1));
    }

    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && NavLightPayloads.mayChange(sp, pos)) PacketDistributor.sendToPlayer(sp, new NavLightPayloads.Open(pos));
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(Wmd.WRENCH.get()) || player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !NavLightPayloads.mayChange(sp, pos)) return ItemInteractionResult.SUCCESS;
        Func n = state.getValue(FUNC).next();
        level.setBlock(pos, state.setValue(FUNC, n), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, Balance.SND_CLICK, 1.4F);
        player.displayClientMessage(Component.translatable("message.wmd.navlight.func", Component.translatable("gui.wmd.navlight.f_" + n.getSerializedName())), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.nav_light").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.nav_light.use").withStyle(ChatFormatting.DARK_GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
