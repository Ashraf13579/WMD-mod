package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SorterMenu extends AbstractContainerMenu {

    public static final int INV_X = 8, INV_Y = 140;

    private final BlockPos pos;
    private final ContainerLevelAccess access;

    public SorterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, buf.readBlockPos()); }

    public SorterMenu(int id, Inventory inventory, BlockPos pos) {
        super(Wmd.SORTER_MENU.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INV_X + col * 18, INV_Y + 58));
    }

    public BlockPos pos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof SorterBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
    }
}
