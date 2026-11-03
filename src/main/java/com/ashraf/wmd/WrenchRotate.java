package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WrenchRotate {
    private WrenchRotate() {}

    private enum Kind { NONE, SINGLE, MULTI }

    static Kind kind(Level level, BlockPos pos, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CcModuleBlock) return Kind.MULTI;
        if (b instanceof MachineBlock mb) return mb.footprint().size() == 1 && s.hasProperty(MachineBlock.FACING) ? Kind.SINGLE : Kind.MULTI;
        if (b instanceof MachinePartBlock || b instanceof RadarPartBlock || b instanceof BatteryBlock || b instanceof NuclearReactorBlock) return Kind.MULTI;
        if (b instanceof AlarmBlock && s.hasProperty(AlarmBlock.FACING)) return Kind.SINGLE;
        return Kind.NONE;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Wmd.WRENCH.get())) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState s = level.getBlockState(pos);
        Kind k = kind(level, pos, s);
        if (k == Kind.NONE) return;
        Player player = event.getEntity();
        if (k == Kind.MULTI && !player.isShiftKeyDown()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return;
        if (k == Kind.MULTI) {
            player.displayClientMessage(Component.translatable("message.wmd.wrench.multiblock"), true);
            return;
        }
        Owned o = Access.ownerAt(level, pos);
        if (o != null && !Access.mayUse(o, player)) { Access.deny(o, player); return; }
        rotate(level, pos, s, !player.isShiftKeyDown());
    }

    static void rotate(Level level, BlockPos pos, BlockState s, boolean clockwise) {
        var prop = s.getBlock() instanceof AlarmBlock ? AlarmBlock.FACING : MachineBlock.FACING;
        Direction f = s.getValue(prop);
        Direction nf = clockwise ? f.getClockWise() : f.getCounterClockWise();
        level.setBlock(pos, s.setValue(prop, nf), Block.UPDATE_ALL);
        level.invalidateCapabilities(pos);
        level.updateNeighborsAt(pos, s.getBlock());
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            be.setChanged();
            BlockState ns = level.getBlockState(pos);
            level.sendBlockUpdated(pos, ns, ns, Block.UPDATE_CLIENTS);
        }
        level.playSound(null, pos, WmdSounds.WRENCH_TURN.get(), SoundSource.BLOCKS, 0.7F, clockwise ? 1.1F : 0.95F);
    }
}
