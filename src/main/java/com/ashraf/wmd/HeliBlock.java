package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class HeliBlock extends UnitBlock {

    private final String mesh;

    protected HeliBlock(Properties properties, String mesh, int depth) {
        super(properties, 0, 0, 0, 0, 0, depth - 1);
        this.mesh = mesh;
    }

    protected HeliBlock(Properties properties, String mesh, int w, int h, int d) {
        super(properties, 0, w - 1, 0, h - 1, 0, d - 1);
        this.mesh = mesh;
    }

    protected abstract BlockEntityType<? extends AirEngineBlockEntity> beType();

    protected boolean interactOwn(Level level, BlockPos controller, Player player, BlockHitResult hit) { return false; }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != beType()) return null;
        return level.isClientSide ? (lv, pos, st, be) -> AirEngineBlockEntity.clientTickAir(lv, pos, st, (AirEngineBlockEntity) be)
                : (lv, pos, st, be) -> ProcessUnitBlockEntity.serverTick(lv, pos, st, (ProcessUnitBlockEntity) be);
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get(mesh, local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get(mesh, local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (interactOwn(level, controller, player, hit)) return;
        BlockEntity be = level.getBlockEntity(controller);
        if (be instanceof CopycatState.Host h && CopycatState.interact(level, player, hit.getBlockPos(), h)) return;
        if (be instanceof ProcessUnitBlockEntity p) player.displayClientMessage(p.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof ProcessUnitBlockEntity p) player.displayClientMessage(p.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd." + mesh).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd." + mesh + ".use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}
