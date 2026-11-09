package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class AlarmMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public AlarmMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainerData(AlarmBlockEntity.DATA_COUNT), buf.readBlockPos());
    }

    public AlarmMenu(int id, Inventory inventory, AlarmBlockEntity be) {
        this(id, inventory, be.getContainerData(), be.getBlockPos());
    }

    private AlarmMenu(int id, Inventory inventory, ContainerData data, BlockPos pos) {
        super(Wmd.ALARM_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addDataSlots(data);
    }

    public BlockPos getPos() { return pos; }
    public int getFrequency() { return data.get(0); }
    public boolean isSounding() { return data.get(1) == 1; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof AlarmBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
    }
}
