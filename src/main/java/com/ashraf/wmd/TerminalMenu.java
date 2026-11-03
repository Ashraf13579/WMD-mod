package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

public class TerminalMenu extends AbstractContainerMenu {

    public static final int COUNTER_X = 146, COUNTER_Y = 118;
    private int counterIndex;
    public static final int SLOT_Y = 82, CARD_X = 14, MISSILE_X = 46, FUEL_X = 78;
    public static final int INV_Y = 194, HOTBAR_Y = 252;

    public static final int STAGE_Y = 197, STAGE_DY = 30;
    private static final int MACHINE_SLOTS = 2, PLAYER_END = MACHINE_SLOTS + 36;

    private final BlockPos pos;
    private final int tier;
    private final int initialX, initialY, initialZ;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public TerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(LaunchTerminalBlockEntity.SLOT_COUNT) {
                    @Override
                    public boolean isItemValid(int slot, ItemStack stack) { return validFor(slot, stack); }
                },
                new SimpleContainerData(LaunchTerminalBlockEntity.DATA_COUNT),
                buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public TerminalMenu(int id, Inventory inventory, LaunchTerminalBlockEntity be) {
        this(id, inventory, be.getInventory(), be.getContainerData(), be.getBlockPos(), be.getTier(),
                be.getTargetX(), be.getTargetY(), be.getTargetZ());
    }

    private TerminalMenu(int id, Inventory inventory, IItemHandler items, ContainerData data,
                         BlockPos pos, int tier, int x, int y, int z) {
        super(Wmd.TERMINAL_MENU.get(), id);
        this.pos = pos;
        this.tier = tier;
        this.initialX = x;
        this.initialY = y;
        this.initialZ = z;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);

        addSlot(new SlotItemHandler(items, LaunchTerminalBlockEntity.SLOT_CARD, CARD_X, SLOT_Y));
        addSlot(new SlotItemHandler(items, LaunchTerminalBlockEntity.SLOT_MISSILE, MISSILE_X, SLOT_Y));
        int dy = tier >= 4 ? STAGE_DY : 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + dy + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y + dy));
        }
        if (tier >= 4) {
            for (int i = 0; i < 2 + LaunchTerminalBlockEntity.BOOSTERS; i++) addSlot(new SlotItemHandler(items, LaunchTerminalBlockEntity.SLOT_CORE + i, 14 + i * 24, STAGE_Y));
        }
        counterIndex = slots.size();
        addSlot(new SlotItemHandler(items, LaunchTerminalBlockEntity.SLOT_COUNTER, COUNTER_X, COUNTER_Y));
        addDataSlots(data);
    }

    static boolean validFor(int slot, ItemStack stack) {
        return switch (slot) {
            case LaunchTerminalBlockEntity.SLOT_CARD -> stack.is(Wmd.NETWORK_CARD.get());
            case LaunchTerminalBlockEntity.SLOT_MISSILE -> stack.getItem() instanceof LaunchMissileItem;
            case LaunchTerminalBlockEntity.SLOT_COUNTER -> LaunchTerminalBlockEntity.isCounter(stack);
            case LaunchTerminalBlockEntity.SLOT_CORE -> stack.getItem() instanceof StageItem s && s.stageKind() == StageItem.CORE;
            case LaunchTerminalBlockEntity.SLOT_UPPER -> (stack.getItem() instanceof StageItem s && s.stageKind() == StageItem.UPPER) || stack.getItem() instanceof PayloadUnitItem;
            case LaunchTerminalBlockEntity.SLOT_BOOST0, LaunchTerminalBlockEntity.SLOT_BOOST0 + 1, LaunchTerminalBlockEntity.SLOT_BOOST0 + 2, LaunchTerminalBlockEntity.SLOT_BOOST0 + 3 -> stack.getItem() instanceof StageItem s && s.stageKind() == StageItem.BOOSTER;
            default -> false;
        };
    }

    public BlockPos getPos() { return pos; }
    public int getTier() { return tier; }

    public int invDy() { return tier >= 4 ? STAGE_DY : 0; }
    public int getInitialX() { return initialX; }
    public int getInitialY() { return initialY; }
    public int getInitialZ() { return initialZ; }

    private int combined(int lo, int hi) {
        return (data.get(lo) & 0xFFFF) | ((data.get(hi) & 0xFFFF) << 16);
    }

    public boolean isFormed() { return data.get(0) == 1; }
    public int getTargetX() { return combined(1, 2); }
    public int getTargetY() { return combined(3, 4); }
    public int getTargetZ() { return combined(5, 6); }
    public int getRange() { return combined(7, 8); }
    public int getRadarState() { return data.get(9); }
    public float getRadarBearing() { return data.get(10) / 10.0F; }
    public float getTargetBearing() { return data.get(11) / 10.0F; }
    public boolean isTargetInArc() { return data.get(12) == 1; }
    public LaunchTerminalBlockEntity.Stage getStage() {
        int s = data.get(13);
        LaunchTerminalBlockEntity.Stage[] all = LaunchTerminalBlockEntity.Stage.values();
        return s >= 0 && s < all.length ? all[s] : LaunchTerminalBlockEntity.Stage.IDLE;
    }
    public float getProgress() { return data.get(14) / 1000.0F; }
    public int getLoadedTier() { return data.get(15); }
    public int getEnergy() { return combined(16, 17); }
    public int getHint() { return data.get(18); }
    public int getSecondsLeft() { return data.get(19); }

    public int getJetMb() { return data.get(20); }

    public int getTankFluid() { return data.get(32); }

    public boolean hasDivert() { return data.get(21) == 1; }
    public int getFakeX() { return combined(22, 23); }
    public int getFakeZ() { return combined(24, 25); }
    public boolean isFakeSet() { return data.get(26) == 1; }

    public int getFuelNeed() { return data.get(27); }

    public double getStatWeight() { return data.get(28) / 10.0; }
    public double getStatSpeed() { return data.get(29) / 100.0; }
    public int getStatHealth() { return data.get(30); }

    public boolean hasUplinkCell() { return (data.get(31) & 1) != 0; }

    public boolean uplinkCoverage() { return (data.get(31) & 2) != 0; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS || index >= PLAYER_END || index == counterIndex) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (LaunchTerminalBlockEntity.isCounter(stack)) {
            if (!moveItemStackTo(stack, counterIndex, counterIndex + 1, false)) return ItemStack.EMPTY;
        } else if (tier >= 4 && stack.getItem() instanceof PayloadUnitItem) {
            if (!moveItemStackTo(stack, PLAYER_END + 1, PLAYER_END + 2, false)) return ItemStack.EMPTY;
        } else if (tier >= 4 && stack.getItem() instanceof StageItem st) {
            int first = PLAYER_END + (st.stageKind() == StageItem.CORE ? 0 : st.stageKind() == StageItem.UPPER ? 1 : 2), last = st.stageKind() == StageItem.BOOSTER ? first + LaunchTerminalBlockEntity.BOOSTERS : first + 1;
            if (!moveItemStackTo(stack, first, last, false)) return ItemStack.EMPTY;
        } else {
            int target = -1;
            for (int i = 0; i < MACHINE_SLOTS; i++) if (validFor(i, stack)) target = i;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof LaunchTerminalBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
    }
}
