package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class LabBuilder {

    private LabBuilder() {}

    public enum Socket {
        PRESS(LabKind.PRESS), FILLER(LabKind.FILLER), REACTOR(LabKind.REACTOR), OVEN(LabKind.OVEN), STORAGE(LabKind.STORAGE);
        public final LabKind kind;
        Socket(LabKind kind) { this.kind = kind; }
    }

    public enum Result {
        NONE, BUILT, REMOVED, BLOCKED, BUSY, NOT_NOW, NO_ITEM, NOT_LAST;
        public static Result of(int code) { return code >= 0 && code < values().length ? values()[code] : NONE; }
    }

    public record Outcome(Result result, int arg) {}

    static Item itemFor(LabKind kind) {
        return switch (kind) {
            case CORE -> Wmd.LAB_CORE_ITEM.get();
            case PRESS -> Wmd.LAB_PRESS_ITEM.get();
            case FILLER -> Wmd.LAB_FILLER_ITEM.get();
            case REACTOR -> Wmd.WARHEAD_REACTOR_ITEM.get();
            case OVEN -> Wmd.LAB_OVEN_ITEM.get();
            case STORAGE -> Wmd.LAB_STORAGE_ITEM.get();
        };
    }

    public static boolean running(LabModuleBlockEntity core) { return core.stage() != LabModuleBlockEntity.Stage.IDLE; }

    public static boolean built(LabModuleBlockEntity core, Socket s) { return LabLine.module(core, s.kind) != null; }

    private static boolean accepts(LabModuleBlockEntity core, Socket s) {
        if (built(core, s)) return false;
        for (Socket o : Socket.values()) if (o.ordinal() < s.ordinal() && !built(core, o)) return false;
        return true;
    }

    public static boolean placeable(LabModuleBlockEntity core, Socket s) { return !running(core) && accepts(core, s); }

    private static Result removeBlock(LabModuleBlockEntity core, Socket s) {
        if (running(core)) return Result.BUSY;
        for (Socket o : Socket.values()) if (o.ordinal() > s.ordinal() && built(core, o)) return Result.NOT_LAST;
        return Result.NONE;
    }

    public static boolean removable(LabModuleBlockEntity core, Socket s) { return built(core, s) && removeBlock(core, s) == Result.NONE; }

    public static int placeableMask(LabModuleBlockEntity core) { int m = 0; for (Socket s : Socket.values()) if (placeable(core, s)) m |= 1 << s.ordinal(); return m; }
    public static int removableMask(LabModuleBlockEntity core) { int m = 0; for (Socket s : Socket.values()) if (removable(core, s)) m |= 1 << s.ordinal(); return m; }

    private static BlockPos spotFor(LabModuleBlockEntity core, Socket s, Direction f) {
        return Machines.toWorld(core.getBlockPos(), f, Machines.mount(core.getBlockState()), -LabKind.STEP * s.kind.ordinal(), 0, 0);
    }

    private static List<BlockPos> blockedCells(Level level, MachineBlock block, BlockPos spot, Direction f, Direction mt) {
        List<BlockPos> bad = new ArrayList<>();
        for (BlockPos l : block.footprint()) {
            BlockPos p = Machines.toWorld(spot, f, mt, l.getX(), l.getY(), l.getZ());
            if (!level.isLoaded(p) || p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight() || !level.getBlockState(p).canBeReplaced()) bad.add(p);
            else if (PlaceGuard.blocker(level, p) != null) bad.add(p);
        }
        return bad;
    }

    private static boolean takeItem(AbstractContainerMenu menu, ServerPlayer player, Item item) {
        if (player.getAbilities().instabuild) return true;
        ItemStack c = menu.getCarried();
        if (c.is(item)) { c.shrink(1); menu.setCarried(c.isEmpty() ? ItemStack.EMPTY : c); return true; }
        Inventory inv = player.getInventory();
        for (ItemStack st : inv.items) if (st.is(item)) { st.shrink(1); inv.setChanged(); return true; }
        return false;
    }

    private static boolean hasItem(AbstractContainerMenu menu, ServerPlayer player, Item item) {
        if (player.getAbilities().instabuild || menu.getCarried().is(item)) return true;
        for (ItemStack st : player.getInventory().items) if (st.is(item)) return true;
        return false;
    }

    public static Outcome place(AbstractContainerMenu menu, ServerPlayer player, LabModuleBlockEntity core, Socket s) {
        if (running(core)) return new Outcome(Result.BUSY, 0);
        if (!accepts(core, s)) return new Outcome(Result.NOT_NOW, 0);
        Item item = itemFor(s.kind);
        if (!hasItem(menu, player, item)) return new Outcome(Result.NO_ITEM, 0);
        ServerLevel level = (ServerLevel) core.getLevel();
        Direction f = core.getBlockState().getValue(MachineBlock.FACING), mt = Machines.mount(core.getBlockState());
        BlockPos spot = spotFor(core, s, f);
        MachineBlock block = (MachineBlock) ((BlockItem) item).getBlock();
        List<BlockPos> bad = blockedCells(level, block, spot, f, mt);
        if (!bad.isEmpty()) {
            mark(level, bad);
            player.displayClientMessage(Component.translatable("gui.wmd.lab.line.result.blocked", bad.size()), true);
            return new Outcome(Result.BLOCKED, Math.min(bad.size(), 127));
        }
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos l : block.footprint()) cells.add(Machines.toWorld(spot, f, mt, l.getX(), l.getY(), l.getZ()));
        if (!ClaimGuard.mayPlace(level, player, cells, null)) return new Outcome(Result.BLOCKED, 1);
        takeItem(menu, player, item);
        BlockState st = block.defaultBlockState().setValue(MachineBlock.FACING, f).setValue(MachineBlock.MOUNT, mt);
        level.setBlock(spot, st, 3);
        block.setPlacedBy(level, spot, st, player, new ItemStack(item));
        level.playSound(null, spot, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
        return new Outcome(Result.BUILT, 0);
    }

    private static void mark(ServerLevel level, List<BlockPos> bad) {
        int step = Math.max(1, bad.size() / 40);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 1.6F);
        for (int i = 0; i < bad.size(); i += step) {
            BlockPos p = bad.get(i);
            level.sendParticles(red, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.0);
        }
    }

    public static Outcome remove(ServerPlayer player, LabModuleBlockEntity core, Socket s) {
        if (!Access.mayMine(core, player)) { Access.deny(core, player, CcAlarms.BREAK); return new Outcome(Result.NOT_NOW, 0); }
        if (!built(core, s)) return new Outcome(Result.NOT_NOW, 0);
        Result why = removeBlock(core, s);
        if (why != Result.NONE) return new Outcome(why, 0);
        var m = LabLine.module(core, s.kind);
        if (m == null) return new Outcome(Result.NOT_NOW, 0);
        ServerLevel level = (ServerLevel) core.getLevel();
        BlockPos pos = m.getBlockPos();
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.4F, 1.4F);
        if (!player.getAbilities().instabuild) player.drop(new ItemStack(itemFor(s.kind)), false);
        return new Outcome(Result.REMOVED, 0);
    }
}
