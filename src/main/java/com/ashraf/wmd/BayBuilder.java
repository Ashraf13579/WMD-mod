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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BayBuilder {

    private BayBuilder() {}

    public enum Socket {
        WELD(BayKind.WELD, 0), CRANE(BayKind.CRANE, 0), WARHEAD(BayKind.WARHEAD, 0), CLEAN(BayKind.CLEAN, 0), PAINT(BayKind.PAINT, 0), CRYO(BayKind.CRYO, 0),
        EXT1(BayKind.EXTEND, 1), EXT2(BayKind.EXTEND, 2), EXT3(BayKind.EXTEND, 3), RACK_L(BayKind.RACK, 0), RACK_R(BayKind.RACK_R, 1),

        EXT4(BayKind.EXTEND, 4), EXT5(BayKind.EXTEND, 5), EXT6(BayKind.EXTEND, 6), RACK_L2(BayKind.RACK, 2), RACK_R2(BayKind.RACK_R, 3);

        public final BayKind kind;

        public final int n;

        Socket(BayKind kind, int n) { this.kind = kind; this.n = n; }
    }

    public enum Result {
        NONE, BUILT, REMOVED, BLOCKED, BUSY, NOT_NOW, NO_ITEM, RACK_FIRST, NOT_LAST, SOON;
        public static Result of(int code) { return code >= 0 && code < values().length ? values()[code] : NONE; }
    }

    public record Outcome(Result result, int arg) {}

    private static Item itemFor(BayKind kind) {
        return switch (kind) {
            case CORE -> Wmd.BAY_CORE_ITEM.get();
            case WELD -> Wmd.BAY_WELD_ITEM.get();
            case CRANE -> Wmd.BAY_CRANE_ITEM.get();
            case WARHEAD -> Wmd.BAY_WARHEAD_ITEM.get();
            case CLEAN -> Wmd.BAY_CLEAN_ITEM.get();
            case PAINT -> Wmd.BAY_PAINT_ITEM.get();
            case CRYO -> Wmd.BAY_CRYO_ITEM.get();
            case EXTEND -> Wmd.BAY_EXTEND_ITEM.get();
            case RACK, RACK_R -> Wmd.BAY_RACK_ITEM.get();
        };
    }

    public static boolean running(BayModuleBlockEntity core) { return core.stage() != BayModuleBlockEntity.Stage.IDLE; }

    private static BayModuleBlockEntity last(BayModuleBlockEntity core) {
        List<BayModuleBlockEntity> c = BayLine.chain(core);
        return c.get(c.size() - 1);
    }

    public static boolean built(BayModuleBlockEntity core, Socket s) {
        if (s.kind.isRack()) return BayLine.rackOf(core, s.n) != null;
        if (s.kind == BayKind.EXTEND) return BayLine.extensions(core) >= s.n;
        for (BayModuleBlockEntity m : BayLine.chain(core)) if (m.kind == s.kind) return true;
        return false;
    }

    private static boolean accepts(BayModuleBlockEntity core, Socket s) {
        if (built(core, s)) return false;
        BayModuleBlockEntity end = last(core);
        if (s.kind.isRack()) return BayLine.extensions(core) >= 3 * (s.n / 2 + 1);
        if (s.kind == BayKind.EXTEND) return end.kind.ordinal() >= BayKind.WARHEAD.ordinal() && BayLine.extensions(core) == s.n - 1;
        BayKind want = end.kind.following();
        return want != null && want != BayKind.EXTEND && want == s.kind;
    }

    public static boolean placeable(BayModuleBlockEntity core, Socket s) { return !running(core) && accepts(core, s); }

    private static Result removeBlock(BayModuleBlockEntity core, Socket s) {
        if (running(core)) return Result.BUSY;
        if (s.kind.isRack()) return Result.NONE;
        BayModuleBlockEntity end = last(core);
        if (end.kind != s.kind || (s.kind == BayKind.EXTEND && BayLine.extensions(core) != s.n)) return Result.NOT_LAST;
        if (s.kind == BayKind.EXTEND)
            for (int side = 0; side < BayLine.RACKS; side++) if (3 * (side / 2 + 1) > s.n - 1 && BayLine.rackOf(core, side) != null) return Result.RACK_FIRST;
        return Result.NONE;
    }

    public static boolean removable(BayModuleBlockEntity core, Socket s) { return built(core, s) && removeBlock(core, s) == Result.NONE; }

    public static int builtMask(BayModuleBlockEntity core) { int m = 0; for (Socket s : Socket.values()) if (built(core, s)) m |= 1 << s.ordinal(); return m; }
    public static int placeableMask(BayModuleBlockEntity core) { int m = 0; for (Socket s : Socket.values()) if (placeable(core, s)) m |= 1 << s.ordinal(); return m; }
    public static int removableMask(BayModuleBlockEntity core) { int m = 0; for (Socket s : Socket.values()) if (removable(core, s)) m |= 1 << s.ordinal(); return m; }

    private static BlockPos spotFor(BayModuleBlockEntity core, Socket s, Direction[] facing) {
        if (s.kind.isRack()) {
            BayModuleBlockEntity ext = BayLine.extension(core, 3 * (s.n / 2) + 1);
            Direction f = ext.getBlockState().getValue(MachineBlock.FACING);
            facing[0] = f;
            return Machines.toWorld(ext.getBlockPos(), f, Machines.mount(ext.getBlockState()), s.kind == BayKind.RACK ? BayLine.RACK_X : -BayLine.RACK_X, 0, 0);
        }
        BayModuleBlockEntity end = last(core);
        Direction f = end.getBlockState().getValue(MachineBlock.FACING);
        facing[0] = f;
        return Machines.toWorld(end.getBlockPos(), f, Machines.mount(end.getBlockState()), 0, 0, end.kind.length);
    }

    private static List<BlockPos> blockedCells(Level level, BayModuleBlock block, BlockPos spot, Direction f, Direction mt) {
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

    public static Outcome place(AbstractContainerMenu menu, ServerPlayer player, BayModuleBlockEntity core, Socket s) {
        if (running(core)) return new Outcome(Result.BUSY, 0);
        if (!accepts(core, s)) return new Outcome(Result.NOT_NOW, 0);
        Item item = itemFor(s.kind);
        if (!hasItem(menu, player, item)) return new Outcome(Result.NO_ITEM, 0);
        ServerLevel level = (ServerLevel) core.getLevel();
        Direction[] fa = new Direction[1];
        BlockPos spot = spotFor(core, s, fa);
        Direction f = fa[0], mt = Machines.mount(core.getBlockState());
        BayModuleBlock block = s.kind == BayKind.RACK_R ? Wmd.BAY_RACK_R.get() : (BayModuleBlock) ((net.minecraft.world.item.BlockItem) item).getBlock();
        List<BlockPos> bad = blockedCells(level, block, spot, f, mt);
        if (!bad.isEmpty()) {
            mark(level, bad);
            player.displayClientMessage(Component.translatable("gui.wmd.bay.line.result.blocked", bad.size()), true);
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

    public static Outcome remove(ServerPlayer player, BayModuleBlockEntity core, Socket s) {
        if (!Access.mayMine(core, player)) { Access.deny(core, player, CcAlarms.BREAK); return new Outcome(Result.NOT_NOW, 0); }
        if (!built(core, s)) return new Outcome(Result.NOT_NOW, 0);
        Result why = removeBlock(core, s);
        if (why != Result.NONE) return new Outcome(why, 0);
        BayModuleBlockEntity m = s.kind.isRack() ? BayLine.rackOf(core, s.n) : last(core);
        if (m == null) return new Outcome(Result.NOT_NOW, 0);
        ServerLevel level = (ServerLevel) core.getLevel();
        BlockPos pos = m.getBlockPos();
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.4F, 1.4F);
        if (!player.getAbilities().instabuild) player.drop(new ItemStack(itemFor(s.kind)), false);
        return new Outcome(Result.REMOVED, 0);
    }
}
