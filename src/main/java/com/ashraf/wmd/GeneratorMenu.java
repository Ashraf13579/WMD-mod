package com.ashraf.wmd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GeneratorMenu extends AbstractContainerMenu {

    private final ContainerData data;
    private final ContainerLevelAccess access;

    public GeneratorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainerData(GeneratorEngineBlockEntity.DATA_COUNT),
                ContainerLevelAccess.create(inventory.player.level(), buf.readBlockPos()));
    }

    public GeneratorMenu(int id, Inventory inventory, GeneratorEngineBlockEntity engine) {
        this(id, inventory, engine.getContainerData(), ContainerLevelAccess.create(engine.getLevel(), engine.getBlockPos()));
    }

    private GeneratorMenu(int id, Inventory inventory, ContainerData data, ContainerLevelAccess access) {
        super(Wmd.GENERATOR_MENU.get(), id);
        this.data = data;
        this.access = access;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    public int getEnergy() {
        return (data.get(GeneratorEngineBlockEntity.DATA_ENERGY_LOW) & 0xFFFF)
                | ((data.get(GeneratorEngineBlockEntity.DATA_ENERGY_HIGH) & 0xFFFF) << 16);
    }

    public int getFuelMb() { return data.get(GeneratorEngineBlockEntity.DATA_FUEL); }
    public boolean isBurning() { return data.get(GeneratorEngineBlockEntity.DATA_RUN) == 1; }
    public boolean isFormed() { return data.get(GeneratorEngineBlockEntity.DATA_FORMED) == 1; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, Wmd.DIESEL_GENERATOR.get());
    }
}
