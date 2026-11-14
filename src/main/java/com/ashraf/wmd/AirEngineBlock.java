package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AirEngineBlock extends UnitBlock implements WrenchUse {

    private final int height;
    private final String mesh;

    protected AirEngineBlock(Properties properties, String mesh, int x0, int x1, int h, int d) {
        super(properties, x0, x1, 0, h - 1, 0, d - 1);
        this.height = h;
        this.mesh = mesh;
    }

    public int height() { return height; }

    public String mesh() { return mesh; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction mount = ctx.getClickedFace().getOpposite();
        return new Direction[] { mount, thrustFacing(ctx, mount) };
    }

    static Direction thrustFacing(BlockPlaceContext ctx, Direction mount) {
        Player p = ctx.getPlayer();
        Level level = ctx.getLevel();
        Vec3 look = p != null ? p.getLookAngle() : Vec3.atLowerCornerOf(ctx.getHorizontalDirection().getNormal());
        Vec3 c = Vec3.atCenterOf(ctx.getClickedPos()), w0 = SableBridge.toWorld(level, c);
        if (w0 == null) w0 = c;
        Direction best = ctx.getHorizontalDirection();
        double bestDot = -Double.MAX_VALUE;
        for (Direction f : Direction.Plane.HORIZONTAL) {
            double[] v = Machines.mountTurn(mount, f.getStepX(), 0.0, f.getStepZ());
            if (Math.abs(v[0] * mount.getStepX() + v[1] * mount.getStepY() + v[2] * mount.getStepZ()) > 0.5) continue;
            Vec3 wv = SableBridge.toWorld(level, c.add(v[0], v[1], v[2]));
            double dot = (wv == null ? c.add(v[0], v[1], v[2]) : wv).subtract(w0).dot(look);
            if (dot > bestDot) { bestDot = dot; best = f; }
        }
        return best;
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        boolean nacelle = level != null && controller != null && level.getBlockEntity(controller) instanceof AirEngineBlockEntity be && be.nacelle();
        VoxelShape g = MachineShapes.get(mesh + (nacelle ? "_nacelle" : "_pod"), local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get(mesh + "_pod", local, facing);
        return g != null ? g : machineShape();
    }

    protected abstract BlockEntityType<? extends AirEngineBlockEntity> beType();

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != beType()) return null;
        return level.isClientSide ? (lv, pos, st, be) -> AirEngineBlockEntity.clientTickAir(lv, pos, st, (AirEngineBlockEntity) be)
                : (lv, pos, st, be) -> ProcessUnitBlockEntity.serverTick(lv, pos, st, (ProcessUnitBlockEntity) be);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof AirEngineBlockEntity be) {
            ItemStack held = player.getMainHandItem();
            if (held.is(Wmd.WRENCH.get())) {
                if (player.isShiftKeyDown()) {
                    ItemStack back = be.setCopy(null, ItemStack.EMPTY);
                    if (back != null) {
                        giveBack(player, back);
                        player.displayClientMessage(Component.translatable("message.wmd.air.skin_reset"), true);
                    }
                } else {
                    be.toggleVariant();
                    player.displayClientMessage(Component.translatable(be.nacelle() ? "message.wmd.air.nacelle" : "message.wmd.air.pod"), true);
                }
                level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
                return;
            }
            if (be.interactExtra(player, held)) return;
            if (held.getItem() instanceof net.minecraft.world.item.BlockItem bi && !(bi instanceof AirEngineItem)) {
                BlockState st = bi.getBlock().defaultBlockState();
                if (ScrewPropellerBlockEntity.copyable(st, level, hit.getBlockPos())) {
                    ItemStack back = be.setCopy(st, held);
                    if (back != null) {
                        if (!player.getAbilities().instabuild) held.shrink(1);
                        giveBack(player, back);
                        player.displayClientMessage(Component.translatable("message.wmd.air.skin", st.getBlock().getName()), true);
                        level.playSound(null, hit.getBlockPos(), st.getSoundType(level, hit.getBlockPos(), player).getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
                    }
                    return;
                }
            }
        }
        super.interact(level, controller, player, hit);
    }

    private static void giveBack(Player player, ItemStack back) {
        if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).getPath();
        tip.add(Component.translatable("tooltip.wmd." + id).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.air.mount").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.air.skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
