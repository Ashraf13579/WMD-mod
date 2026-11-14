package com.ashraf.wmd;

import java.util.List;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GunTurretBlock extends UnitBlock {

    public static final MapCodec<GunTurretBlock> CODEC = simpleCodec(GunTurretBlock::new);

    public GunTurretBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction[] h = holeFit(ctx);
        if (h != null) return h;
        Direction mount = ctx.getClickedFace().getOpposite();
        return new Direction[] { mount, AirEngineBlock.thrustFacing(ctx, mount) };
    }

    static Direction[] holeFit(BlockPlaceContext ctx) {
        Level l = ctx.getLevel();
        BlockPos t = ctx.getClickedPos();
        Direction through = null;
        for (Direction.Axis ax : Direction.Axis.values()) {
            Direction a = Direction.fromAxisAndDirection(ax, Direction.AxisDirection.POSITIVE);
            if (!free(l, t.relative(a)) || !free(l, t.relative(a.getOpposite()))) continue;
            if (through != null) return null;
            through = a;
        }
        if (through == null) return null;
        Direction mount;
        if (through.getAxis() == Direction.Axis.Y) {
            net.minecraft.world.entity.player.Player p = ctx.getPlayer();
            double py = p == null ? t.getY() + 0.5 : p.getEyeY();
            if (l.isClientSide || p == null) py = p == null ? py : p.getEyeY();
            Vec3 c = Vec3.atCenterOf(t);
            Vec3 cw = world(l, t, c), uw = world(l, t, c.add(0.0, 1.0, 0.0)).subtract(cw);
            mount = (uw.y >= 0.0) == (py < cw.y) ? Direction.UP : Direction.DOWN;
        } else {
            int pos = hull(l, t, through), neg = hull(l, t, through.getOpposite());
            if (pos == neg) {
                net.minecraft.world.entity.player.Player p = ctx.getPlayer();
                if (p == null) return null;
                Vec3 c = world(l, t, Vec3.atCenterOf(t)), d = world(l, t, Vec3.atCenterOf(t).add(Vec3.atLowerCornerOf(through.getNormal()))).subtract(c);
                mount = d.dot(c.subtract(p.getEyePosition())) > 0.0 ? through : through.getOpposite();
            } else mount = pos > neg ? through : through.getOpposite();
        }
        for (Direction f : Direction.Plane.HORIZONTAL) {
            if (f.getAxis() == mount.getAxis()) continue;
            BlockPos second = Machines.toWorld(t, f, mount, 0, 0, 1);
            if (second.equals(t) || !free(l, second) || !free(l, second.relative(through)) || !free(l, second.relative(through.getOpposite()))) continue;
            return new Direction[] { mount, f };
        }
        return null;
    }

    private static boolean free(Level l, BlockPos p) { return l.isInWorldBounds(p) && l.getBlockState(p).canBeReplaced(); }

    private static int hull(Level l, BlockPos t, Direction d) {
        Direction across = d.getClockWise();
        int n = 0;
        for (int k = 1; k <= 10; k++) for (int u = -3; u <= 3; u++) for (int v = 0; v <= 4; v++) {
            BlockPos q = t.relative(d, k).relative(across, u).above(v);
            if (!l.getBlockState(q).canBeReplaced()) n++;
        }
        return n;
    }

    private static Vec3 world(Level l, BlockPos pos, Vec3 p) {
        Vec3 w = l.isClientSide ? ShipFrame.drawnWorld(l, pos, p, Float.NaN) : SableBridge.toWorld(l, p);
        return w == null ? p : w;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GunTurretBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, GunnerBlocks.GUN_TURRET_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, GunnerBlocks.GUN_TURRET_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("gun_turret", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof GunTurretBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof CiwsAmmoItem) {
            player.displayClientMessage(Component.translatable(be.reload(player, held), be.rounds(), Balance.GTUR_ROUNDS), true);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof GunTurretBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.gun_turret").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.gun_turret.use", Balance.GTUR_ROUNDS, Balance.GTUR_BOX).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
