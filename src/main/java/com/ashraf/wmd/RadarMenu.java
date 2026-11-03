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

public class RadarMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public RadarMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainerData(RadarBlockEntity.DATA_COUNT), buf.readBlockPos());
    }

    public RadarMenu(int id, Inventory inventory, RadarBlockEntity be) {
        this(id, inventory, be.getContainerData(), be.getBlockPos());
    }

    private RadarMenu(int id, Inventory inventory, ContainerData data, BlockPos pos) {
        super(Wmd.RADAR_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addDataSlots(data);
    }

    public BlockPos getPos() { return pos; }
    public boolean isFormed() { return data.get(0) == 1; }
    public float getBearing() { return data.get(1) / 10.0F; }
    public float getTargetBearing() { return data.get(2) / 10.0F; }
    public boolean isPowered() { return data.get(3) == 1; }
    public int getEnergy() { return (data.get(4) & 0xFFFF) | ((data.get(5) & 0xFFFF) << 16); }

    public int getMode() { return data.get(6); }
    public int getFrequency() { return data.get(7); }
    public boolean isLocked() { return data.get(8) == 1; }

    public boolean isSatFeed() { return data.get(9) == 1; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof RadarStationBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 400.0, true);
    }
}
