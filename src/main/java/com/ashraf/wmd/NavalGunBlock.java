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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class NavalGunBlock extends UnitBlock {

    private final NavalGuns.Spec spec;

    protected NavalGunBlock(Properties properties, NavalGuns.Spec spec) {
        super(properties, -spec.half(), spec.half(), 0, spec.height() - 1, -spec.half(), spec.half());
        this.spec = spec;
    }

    NavalGuns.Spec spec() { return spec; }

    public static class Deck extends NavalGunBlock {
        public static final MapCodec<Deck> CODEC = simpleCodec(Deck::new);
        public Deck(Properties p) { super(p, NavalGuns.DECK); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NavalGunBlockEntity(NavalGuns.DECK_GUN_BE.get(), pos, state, NavalGuns.DECK); }
        @Override protected BlockEntityType<NavalGunBlockEntity> type() { return NavalGuns.DECK_GUN_BE.get(); }
    }

    public static class Turret extends NavalGunBlock {
        public static final MapCodec<Turret> CODEC = simpleCodec(Turret::new);
        public Turret(Properties p) { super(p, NavalGuns.TURRET); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NavalGunBlockEntity(NavalGuns.BATTLESHIP_TURRET_BE.get(), pos, state, NavalGuns.TURRET); }
        @Override protected BlockEntityType<NavalGunBlockEntity> type() { return NavalGuns.BATTLESHIP_TURRET_BE.get(); }
    }

    protected abstract BlockEntityType<NavalGunBlockEntity> type();

    @Override
    public boolean faceAway() { return true; }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, type(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, type(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get(spec.id(), local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof NavalGunBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        if (NavalGunBlockEntity.isRound(held)) {
            player.displayClientMessage(Component.translatable(be.handLoad(player, held), be.rackCount(), be.spec().rack(), be.charges(), be.spec().charges()), true);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof NavalGunBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String k = "tooltip.wmd." + spec.id();
        tip.add(Component.translatable(k).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable(k + ".use", spec.rack()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.naval_gun.aim").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
