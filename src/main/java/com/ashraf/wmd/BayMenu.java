package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BayMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 20;
    public static final int PART_X = 12, PART_STEP = 24, SLOT_Y = 30, OUT_X = 156;

    public static final int ROW2 = 28, MOD_Y = SLOT_Y + ROW2;
    public static final int INV_Y = 150 + ROW2, HOTBAR_Y = 208 + ROW2;

    public static final int UPLINK_X = PART_X, UPLINK_INDEX = 5;

    public static final int CAM_X = PART_X + 2 * PART_STEP, CAM_INDEX = 6, ARROW_X = 120;

    public static final int SEEKER_X = PART_X + PART_STEP, SEEKER_INDEX = 7;
    private static final int MACHINE_SLOTS = 8, PLAYER_END = MACHINE_SLOTS + 36;

    private final BlockPos pos;
    private final ContainerData data;

    public boolean lineTab;

    private int[] lastResult;
    private final ContainerLevelAccess access;

    public BayMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, dummyParts(), new ItemStackHandler(1) {
            @Override public boolean isItemValid(int slot, ItemStack s) { return false; }
        }, new ItemStackHandler(1) {
            @Override public boolean isItemValid(int slot, ItemStack s) { return Uplink.isCell(s); }
            @Override public int getSlotLimit(int slot) { return 1; }
        }, new ItemStackHandler(1) {
            @Override public boolean isItemValid(int slot, ItemStack s) { return BayModuleBlockEntity.isCamPod(s); }
            @Override public int getSlotLimit(int slot) { return 1; }
        }, new ItemStackHandler(1) {
            @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof SeekerModuleItem; }
            @Override public int getSlotLimit(int slot) { return 1; }
        }, new SimpleContainerData(DATA_COUNT), buf.readBlockPos());
    }

    public BayMenu(int id, Inventory inventory, BayModuleBlockEntity core) {
        this(id, inventory, core, new int[1]);
    }

    private BayMenu(int id, Inventory inventory, BayModuleBlockEntity core, int[] result) {
        this(id, inventory, core.parts, core.out, core.uplinkSlot, core.camSlot, core.seekerSlot, new ContainerData() {
            @Override public int get(int i) {
                var level = core.getLevel();
                return switch (i) {
                    case 0 -> core.stage().ordinal();
                    case 1 -> 0;
                    case 2 -> level == null ? 0 : (int) (core.progress(level) * 1000);
                    case 3 -> core.energy() & 0xFFFF;
                    case 4 -> core.energy() >>> 16;
                    case 5 -> core.flags();
                    case 6 -> core.stockMask();
                    case 7 -> core.needKfe();
                    case 8 -> BayBuilder.builtMask(core);
                    case 9 -> BayBuilder.placeableMask(core);
                    case 10 -> BayBuilder.removableMask(core);
                    case 11 -> core.neededStations() & 3 | (core.sizeLevel() & 7) << 2 | (core.airframeGrade() & 7) << 5 | (BayBuilder.running(core) ? 256 : 0);
                    case 12 -> result[0];
                    case 13 -> (int) Math.round(MissileStats.weight(core.buildFromParts()) * 10.0);
                    case 14 -> MissileStats.range(core.buildFromParts());
                    case 15 -> (int) Math.round(MissileStats.speed(core.buildFromParts()) * 100.0);
                    case 16 -> MissileStats.health(core.buildFromParts());
                    case 17 -> MissileStats.fuel(core.buildFromParts());
                    case 19 -> core.mode();
                    default -> {
                        int n = 0;
                        for (int s = 0; s < core.parts.getSlots(); s++) if (!core.parts.getStackInSlot(s).isEmpty()) n++;
                        yield n;
                    }
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, core.getBlockPos());
        this.lastResult = result;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof BayModuleBlockEntity core) || core.kind != BayKind.CORE) return false;
        if (id == 32) { core.cycleMode(); return true; }
        int s = id & 15, action = id >> 4;
        if (s >= BayBuilder.Socket.values().length || action > 1) return false;
        if (action == 1 && !Access.mayMine(core, sp)) { Access.deny(core, sp, CcAlarms.BREAK); return false; }
        BayBuilder.Socket sock = BayBuilder.Socket.values()[s];
        BayBuilder.Outcome o = action == 0 ? BayBuilder.place(this, sp, core, sock) : BayBuilder.remove(sp, core, sock);
        lastResult[0] = o.result().ordinal() | o.arg() << 8;
        return true;
    }

    private static ItemStackHandler dummyParts() {
        return new ItemStackHandler(4) {
            @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof MissilePartItem p && p.slot() == slot; }
        };
    }

    private BayMenu(int id, Inventory inventory, IItemHandler parts, IItemHandler out, IItemHandler uplink, IItemHandler cam, IItemHandler seeker, ContainerData data, BlockPos pos) {
        super(Wmd.BAY_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < 4; i++) addSlot(new SlotItemHandler(parts, i, PART_X + i * PART_STEP, SLOT_Y) {
            @Override public boolean isActive() { return !lineTab; }
        });
        addSlot(new SlotItemHandler(out, 0, OUT_X, SLOT_Y) {
            @Override public boolean isActive() { return !lineTab; }
        });
        addSlot(new SlotItemHandler(uplink, 0, UPLINK_X, MOD_Y) {
            @Override public boolean isActive() { return !lineTab; }
        });
        addSlot(new SlotItemHandler(cam, 0, CAM_X, MOD_Y) {
            @Override public boolean isActive() { return !lineTab && hasItem(); }
        });
        addSlot(new SlotItemHandler(seeker, 0, SEEKER_X, MOD_Y) {
            @Override public boolean isActive() { return !lineTab; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, BayModuleBlockEntity core) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.bay_core"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new BayMenu(id, inv, core); }
        }, buf -> buf.writeBlockPos(core.getBlockPos()));
    }

    public BlockPos getPos() { return pos; }
    public BayModuleBlockEntity.Stage stage() { return BayModuleBlockEntity.Stage.values()[Math.max(0, Math.min(data.get(0), BayModuleBlockEntity.Stage.values().length - 1))]; }
    public float progress() { return data.get(2) / 1000.0F; }
    public int energy() { return (data.get(3) & 0xFFFF) | (data.get(4) << 16); }
    public int flags() { return data.get(5); }

    public int needKfe() { return data.get(7); }

    public int builtMask() { return data.get(8) & 0xFFFF; }
    public int placeableMask() { return data.get(9) & 0xFFFF; }
    public int removableMask() { return data.get(10) & 0xFFFF; }
    public int neededStations() { return data.get(11) & 3; }
    public int sizeLevel() { return data.get(11) >> 2 & 7; }
    public int airframeGrade() { return data.get(11) >> 5 & 7; }
    public boolean running() { return (data.get(11) & 256) != 0; }

    public int partsIn() { return data.get(18); }
    public int mode() { return data.get(19); }
    public double statWeight() { return data.get(13) / 10.0; }
    public int statRange() { return data.get(14); }
    public double statSpeed() { return data.get(15) / 100.0; }
    public int statHealth() { return data.get(16); }
    public int statFuel() { return data.get(17); }
    public BayBuilder.Result result() { return BayBuilder.Result.of(data.get(12) & 255); }
    public int resultArg() { return data.get(12) >> 8 & 127; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_END, true)) return ItemStack.EMPTY;
        } else {
            int target = -1;
            for (int i = 0; i < 4; i++) if (slots.get(i).mayPlace(stack)) target = i;
            if (slots.get(UPLINK_INDEX).mayPlace(stack)) target = UPLINK_INDEX;
            if (slots.get(CAM_INDEX).mayPlace(stack)) target = CAM_INDEX;
            if (slots.get(SEEKER_INDEX).mayPlace(stack)) target = SEEKER_INDEX;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof BayModuleBlock.Core
                && level.getBlockEntity(p) instanceof BayModuleBlockEntity core && BayLine.nearLine(player, core), true);
    }
}
