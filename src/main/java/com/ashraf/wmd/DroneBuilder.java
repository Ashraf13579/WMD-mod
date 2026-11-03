package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class DroneBuilder {

    private DroneBuilder() {}

    public enum Socket {
        FRAME(DroneKind.FRAME), AVIONICS(DroneKind.AVIONICS), PROPULSION(DroneKind.PROPULSION), TEST(DroneKind.TEST), STORAGE(DroneKind.STORAGE), HANGAR(DroneKind.HANGAR);
        public final DroneKind kind;
        Socket(DroneKind kind) { this.kind = kind; }
    }

    static Item itemFor(DroneKind kind) {
        return switch (kind) {
            case CORE -> Wmd.DRONE_CORE_ITEM.get();
            case FRAME -> Wmd.DRONE_FRAME_ITEM.get();
            case AVIONICS -> Wmd.DRONE_AVIONICS_ITEM.get();
            case PROPULSION -> Wmd.DRONE_PROPULSION_ITEM.get();
            case TEST -> Wmd.DRONE_TEST_ITEM.get();
            case STORAGE -> Wmd.DRONE_STORAGE_ITEM.get();
            case HANGAR -> Wmd.DRONE_HANGAR_ITEM.get();
        };
    }

    public static boolean running(DroneModuleBlockEntity core) { return core.stage() != DroneModuleBlockEntity.Stage.IDLE; }

    public static boolean built(DroneModuleBlockEntity core, Socket s) { return DroneLine.module(core, s.kind) != null; }

    private static boolean accepts(DroneModuleBlockEntity core, Socket s) {
        if (built(core, s)) return false;
        for (Socket o : Socket.values()) if (o.ordinal() < s.ordinal() && !built(core, o)) return false;
        return true;
    }

    public static boolean placeable(DroneModuleBlockEntity core, Socket s) { return !running(core) && accepts(core, s); }

    private static LabBuilder.Result removeBlock(DroneModuleBlockEntity core, Socket s) {
        if (running(core)) return LabBuilder.Result.BUSY;
        for (Socket o : Socket.values()) if (o.ordinal() > s.ordinal() && built(core, o)) return LabBuilder.Result.NOT_LAST;
        return LabBuilder.Result.NONE;
    }

    public static boolean removable(DroneModuleBlockEntity core, Socket s) { return built(core, s) && removeBlock(core, s) == LabBuilder.Result.NONE; }

    public static int placeableMask(DroneModuleBlockEntity core) { int m = 0; for (Socket s : Socket.values()) if (placeable(core, s)) m |= 1 << s.ordinal(); return m; }
    public static int removableMask(DroneModuleBlockEntity core) { int m = 0; for (Socket s : Socket.values()) if (removable(core, s)) m |= 1 << s.ordinal(); return m; }

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

    public static LabBuilder.Outcome place(AbstractContainerMenu menu, ServerPlayer player, DroneModuleBlockEntity core, Socket s) {
        if (running(core)) return new LabBuilder.Outcome(LabBuilder.Result.BUSY, 0);
        if (!accepts(core, s)) return new LabBuilder.Outcome(LabBuilder.Result.NOT_NOW, 0);
        Item item = itemFor(s.kind);
        if (!hasItem(menu, player, item)) return new LabBuilder.Outcome(LabBuilder.Result.NO_ITEM, 0);
        ServerLevel level = (ServerLevel) core.getLevel();
        Direction f = core.getBlockState().getValue(MachineBlock.FACING), mt = Machines.mount(core.getBlockState());
        BlockPos spot = Machines.toWorld(core.getBlockPos(), f, mt, -s.kind.offset(), 0, 0);
        MachineBlock block = (MachineBlock) ((BlockItem) item).getBlock();
        List<BlockPos> bad = blockedCells(level, block, spot, f, mt);
        if (!bad.isEmpty()) {
            mark(level, bad);
            player.displayClientMessage(Component.translatable("gui.wmd.lab.line.result.blocked", bad.size()), true);
            return new LabBuilder.Outcome(LabBuilder.Result.BLOCKED, Math.min(bad.size(), 127));
        }
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos l : block.footprint()) cells.add(Machines.toWorld(spot, f, mt, l.getX(), l.getY(), l.getZ()));
        if (!ClaimGuard.mayPlace(level, player, cells, null)) return new LabBuilder.Outcome(LabBuilder.Result.BLOCKED, 1);
        takeItem(menu, player, item);
        BlockState st = block.defaultBlockState().setValue(MachineBlock.FACING, f).setValue(MachineBlock.MOUNT, mt);
        level.setBlock(spot, st, 3);
        block.setPlacedBy(level, spot, st, player, new ItemStack(item));
        level.playSound(null, spot, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
        return new LabBuilder.Outcome(LabBuilder.Result.BUILT, 0);
    }

    private static void mark(ServerLevel level, List<BlockPos> bad) {
        int step = Math.max(1, bad.size() / 40);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 1.6F);
        for (int i = 0; i < bad.size(); i += step) {
            BlockPos p = bad.get(i);
            level.sendParticles(red, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.0);
        }
    }

    public static LabBuilder.Outcome remove(ServerPlayer player, DroneModuleBlockEntity core, Socket s) {
        if (!Access.mayMine(core, player)) { Access.deny(core, player, CcAlarms.BREAK); return new LabBuilder.Outcome(LabBuilder.Result.NOT_NOW, 0); }
        if (!built(core, s)) return new LabBuilder.Outcome(LabBuilder.Result.NOT_NOW, 0);
        LabBuilder.Result why = removeBlock(core, s);
        if (why != LabBuilder.Result.NONE) return new LabBuilder.Outcome(why, 0);
        DroneModuleBlockEntity m = DroneLine.module(core, s.kind);
        if (m == null) return new LabBuilder.Outcome(LabBuilder.Result.NOT_NOW, 0);
        ServerLevel level = (ServerLevel) core.getLevel();
        BlockPos pos = m.getBlockPos();
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.4F, 1.4F);
        if (!player.getAbilities().instabuild) player.drop(new ItemStack(itemFor(s.kind)), false);
        return new LabBuilder.Outcome(LabBuilder.Result.REMOVED, 0);
    }
}
