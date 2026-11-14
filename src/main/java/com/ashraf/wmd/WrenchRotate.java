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

    static final String MODE_KEY = "wmdWrenchRotate";

    static boolean rotateMode(net.minecraft.world.item.ItemStack st) {
        net.minecraft.world.item.component.CustomData d = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBoolean(MODE_KEY);
    }

    @SubscribeEvent
    static void onRightClickAir(PlayerInteractEvent.RightClickItem event) {
        net.minecraft.world.item.ItemStack st = event.getItemStack();
        if (!st.is(Wmd.WRENCH.get()) || !event.getEntity().isShiftKeyDown()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getLevel().isClientSide) return;
        boolean rot = !rotateMode(st);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, st, t -> t.putBoolean(MODE_KEY, rot));
        event.getEntity().displayClientMessage(Component.translatable(rot ? "message.wmd.wrench.mode_rotate" : "message.wmd.wrench.mode_use"), true);
        event.getLevel().playSound(null, event.getEntity().blockPosition(), WmdSounds.WRENCH_TURN.get(), SoundSource.PLAYERS, 0.5F, rot ? 1.3F : 0.9F);
    }

    static Kind kind(Level level, BlockPos pos, BlockState s) { return kind(level, pos, s, false); }

    static Kind kind(Level level, BlockPos pos, BlockState s, boolean rotate) {
        Block b = s.getBlock();
        if (b instanceof WrenchUse && !rotate) return Kind.NONE;
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
        Kind k = kind(level, pos, s, rotateMode(event.getItemStack()));
        if (k == Kind.NONE) return;
        Player player = event.getEntity();
        boolean rotMode = rotateMode(event.getItemStack());
        if (k == Kind.MULTI && !player.isShiftKeyDown() && !rotMode) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return;
        if (k == Kind.MULTI) {

            BlockPos ctrl = controllerFor(level, pos);
            if (ctrl == null || !(level.getBlockState(ctrl).getBlock() instanceof MachineBlock mb) || !turnable(mb)) {
                player.displayClientMessage(Component.translatable("message.wmd.wrench.multiblock"), true);
                return;
            }
            Owned om = Access.ownerAt(level, ctrl);
            if (om != null && !Access.mayUse(om, player)) { Access.deny(om, player); return; }
            String why = turnMulti(level, ctrl, player, !(rotMode && player.isShiftKeyDown()));
            if (why != null) player.displayClientMessage(Component.translatable(why), true);
            return;
        }
        Owned o = Access.ownerAt(level, pos);
        if (o != null && !Access.mayUse(o, player)) { Access.deny(o, player); return; }
        rotate(level, pos, s, !player.isShiftKeyDown());
    }

    private static BlockPos controllerFor(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof MachineBlock) return pos;
        if (level.getBlockEntity(pos) instanceof MachinePartBlockEntity part) return part.getController();
        return null;
    }

    private static boolean turnable(MachineBlock mb) {
        if (mb instanceof CcModuleBlock || mb instanceof InterceptorSiloBlock || mb instanceof InterceptorSiloT4Block) return false;
        if (mb instanceof BayModuleBlock b && b.kind != BayKind.CORE) return false;
        if (mb instanceof LabModuleBlock l && l.kind != LabKind.CORE) return false;
        return true;
    }

    static String turnMulti(Level level, BlockPos ctrl, Player player, boolean clockwise) {
        BlockState s = level.getBlockState(ctrl);
        if (!(s.getBlock() instanceof MachineBlock mb)) return "message.wmd.wrench.multiblock";
        Direction mount = Machines.mount(s), f = s.getValue(MachineBlock.FACING);
        Direction nf = clockwise ? f.getClockWise(mount.getAxis()) : f.getCounterClockWise(mount.getAxis());
        if (!MachineBlock.FACING.getPossibleValues().contains(nf)) nf = f.getOpposite();
        if (nf.getAxis() == mount.getAxis() || nf == f || !MachineBlock.FACING.getPossibleValues().contains(nf)) return "message.wmd.wrench.multiblock";
        java.util.Set<BlockPos> old = new java.util.HashSet<>();
        for (BlockPos l : mb.footprint()) old.add(Machines.toWorld(ctrl, f, mount, l.getX(), l.getY(), l.getZ()));
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        java.util.List<BlockPos> fresh = new java.util.ArrayList<>();
        for (BlockPos l : mb.footprint()) {
            BlockPos p = Machines.toWorld(ctrl, nf, mount, l.getX(), l.getY(), l.getZ());
            if (!level.isInWorldBounds(p)) return "message.wmd.wrench.turn_blocked";
            cells.add(p);
            if (p.equals(ctrl) || old.contains(p)) continue;
            if (!level.getBlockState(p).canBeReplaced()) return "message.wmd.wrench.turn_blocked";
            fresh.add(p);
        }
        if (PlaceGuard.blocker(level, fresh) != null) return "message.wmd.wrench.turn_blocked";
        if (!ClaimGuard.mayPlace(level, player, cells, ctrl)) return "message.wmd.wrench.turn_blocked";
        MachineBlock.removing = true;
        try {
            for (BlockPos p : old) if (!p.equals(ctrl) && level.getBlockState(p).is(Wmd.MACHINE_PART.get())) level.removeBlock(p, false);
        } finally {
            MachineBlock.removing = false;
        }
        level.setBlock(ctrl, s.setValue(MachineBlock.FACING, nf), Block.UPDATE_ALL);
        for (BlockPos l : mb.footprint()) {
            BlockPos p = Machines.toWorld(ctrl, nf, mount, l.getX(), l.getY(), l.getZ());
            if (p.equals(ctrl)) continue;
            level.setBlock(p, mb.hullPlug(l) ? Wmd.MACHINE_PART.get().defaultBlockState() : Waterlog.part(level, p), 3);
            if (level.getBlockEntity(p) instanceof MachinePartBlockEntity be) be.setController(ctrl);
        }
        for (BlockPos p : cells) level.invalidateCapabilities(p);
        for (BlockPos p : old) level.invalidateCapabilities(p);
        level.updateNeighborsAt(ctrl, s.getBlock());
        BlockEntity be = level.getBlockEntity(ctrl);
        if (be != null) { be.setChanged(); BlockState ns = level.getBlockState(ctrl); level.sendBlockUpdated(ctrl, ns, ns, Block.UPDATE_CLIENTS); }
        level.playSound(null, ctrl, WmdSounds.WRENCH_TURN.get(), SoundSource.BLOCKS, 0.7F, clockwise ? 1.1F : 0.95F);
        return null;
    }

    static void rotate(Level level, BlockPos pos, BlockState s, boolean clockwise) {
        var prop = s.getBlock() instanceof AlarmBlock ? AlarmBlock.FACING : MachineBlock.FACING;
        Direction f = s.getValue(prop);

        Direction.Axis ax = s.getBlock() instanceof MachineBlock && s.hasProperty(MachineBlock.MOUNT) ? s.getValue(MachineBlock.MOUNT).getAxis() : Direction.Axis.Y;
        Direction nf = f.getAxis() == ax ? f : clockwise ? f.getClockWise(ax) : f.getCounterClockWise(ax);
        if (!prop.getPossibleValues().contains(nf)) nf = f.getOpposite();
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
