package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LandingGearBlock extends UnitBlock implements WrenchUse {

    public static final MapCodec<LandingGearBlock> CODEC = simpleCodec(p -> new LandingGearBlock(p, LandingGearBlockEntity.SMALL));

    private final int kind;

    public LandingGearBlock(Properties properties) { this(properties, LandingGearBlockEntity.SMALL); }

    public LandingGearBlock(Properties properties, int kind) {
        super(properties, 0, kind == LandingGearBlockEntity.LARGE ? 1 : 0, 0, 0, 0, kind == LandingGearBlockEntity.BOGIE ? 1 : 0);
        this.kind = kind;
    }

    private String mesh() { return kind == LandingGearBlockEntity.LARGE ? "landing_gear_large" : kind == LandingGearBlockEntity.BOGIE ? "landing_gear_bogie" : "landing_gear"; }
    private BlockEntityType<LandingGearBlockEntity> beType() {
        return kind == LandingGearBlockEntity.LARGE ? AirBlocks.LANDING_GEAR_LARGE_BE.get() : kind == LandingGearBlockEntity.BOGIE ? AirBlocks.LANDING_GEAR_BOGIE_BE.get() : AirBlocks.LANDING_GEAR_BE.get();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction mount = ctx.getClickedFace().getOpposite();
        return new Direction[] { mount, AirEngineBlock.thrustFacing(ctx, mount) };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newLandingGear(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, beType(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, beType(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get(mesh(), local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get(mesh(), local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof LandingGearBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        if (held.is(AirBlocks.TOW_BAR.get())) {
            if (level instanceof net.minecraft.server.level.ServerLevel sl) TowBar.use(sl, player, be);
            return;
        }
        if (held.is(Wmd.WRENCH.get()) && !player.isShiftKeyDown()) {
            be.cycleRole();
            player.displayClientMessage(Component.translatable("message.wmd.gear.to_role", Component.translatable(be.roleKey())), true);
            level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
            return;
        }
        if (held.isEmpty() && !player.isShiftKeyDown() && kind == LandingGearBlockEntity.SMALL) {
            be.cycleSize();
            player.displayClientMessage(Component.translatable("message.wmd.gear.to_size", Component.translatable("message.wmd.gear.size" + be.size())), true);
            level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.1F);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof LandingGearBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof LandingGearBlockEntity be && be.locked() && AirGate.on() ? 15 : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd." + mesh()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd." + mesh() + ".use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
