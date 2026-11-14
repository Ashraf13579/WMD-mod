package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class MachineMenu extends AbstractContainerMenu {

    public static final int MAX_TANKS = 9;
    private static final int COUNT = 5 + 4 * MAX_TANKS + 2 + ProcessUnitBlockEntity.UPGRADE_SLOTS;

    private final BlockEntity be;
    private final GuiMachine machine;
    private final ContainerData data;

    public static void open(Player player, BlockEntity be) {
        if (!(player instanceof ServerPlayer sp) || !(be instanceof GuiMachine gm)) return;
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new MachineMenu(id, inv, be, null), gm.guiTitle()), buf -> buf.writeBlockPos(be.getBlockPos()));
    }

    public MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()), new SimpleContainerData(COUNT));
    }

    private MachineMenu(int id, Inventory inventory, BlockEntity be, ContainerData mirror) {
        super(Wmd.MACHINE_MENU.get(), id);
        this.be = be;
        this.machine = be instanceof GuiMachine g ? g : null;
        this.data = mirror != null ? mirror : live();
        addDataSlots(data);
        if (be instanceof GuiItemSlots ipe) {

            net.minecraft.world.Container real = ipe.slotContainer();

            final int n = ipe.slotCount();
            net.minecraft.world.Container c = inventory.player.level().isClientSide
                    ? new net.minecraft.world.SimpleContainer(n) { @Override public boolean canPlaceItem(int i, ItemStack st) { return real.canPlaceItem(i, st); } }
                    : real;
            itemSlotCount = ipe.slotCount();
            itemTankOffset = ipe.firstSlotTank();
            for (int i = 0; i < itemSlotCount; i++) {
                final int idx = i;
                addSlot(new net.minecraft.world.inventory.Slot(c, i, MachineLayout.slotAreaX(ipe, ipe.firstSlotTank() + i), MachineLayout.SLOT_AREA_Y) {
                    @Override public boolean mayPlace(ItemStack st) {
                        boolean ok = c.canPlaceItem(idx, st);
                        if (!ok && !st.isEmpty() && LOG.isDebugEnabled()) LOG.debug("machine slot {} refuses {} (client={})", idx, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()), ipe instanceof net.minecraft.world.level.block.entity.BlockEntity b && b.getLevel() != null && b.getLevel().isClientSide);
                        return ok;
                    }
                    @Override public int getMaxStackSize() { return ipe.slotMax(idx); }
                });
            }
            int h = ipe.guiTanks().size() > 3 ? 156 : 136;
            int invY = h + 90 - 82 - 4;
            int dx = (MachineLayout.layoutWidth(ipe.guiTanks().size()) - 176) / 2;
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new net.minecraft.world.inventory.Slot(inventory, col + row * 9 + 9, 8 + col * 18 + dx, invY + row * 18));
            for (int col = 0; col < 9; col++) addSlot(new net.minecraft.world.inventory.Slot(inventory, col, 8 + col * 18 + dx, invY + 58));
        }
    }

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private int itemSlotCount;

    public int machineSlotCount() { return itemSlotCount; }

    private int itemTankOffset;

    public int itemTankOffset() { return itemTankOffset; }

    private ContainerData live() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                if (machine == null) return 0;
                IEnergyStorage e = machine.guiEnergy();
                if (i == 0) return machine.guiRunning() ? 1 : 0;
                if (i <= 4) {
                    if (e == null) return 0;
                    int v = i <= 2 ? e.getEnergyStored() : e.getMaxEnergyStored();
                    return i % 2 == 1 ? v & 0xFFFF : v >>> 16;
                }
                if (i == 5 + 4 * MAX_TANKS) return machine.guiProgress() + 1;
                if (i == 5 + 4 * MAX_TANKS + 1) return machine.guiStatus();
                if (i >= UPG) return machine.guiUpgradeCode(i - UPG);
                int t = (i - 5) / 4, k = (i - 5) % 4;
                if (t >= machine.guiTanks().size()) return 0;
                FluidTank tank = machine.guiTanks().get(t);
                int v = k < 2 ? tank.getFluidAmount() : tank.getCapacity();
                return k % 2 == 0 ? v & 0xFFFF : v >>> 16;
            }

            @Override public void set(int i, int v) {}
            @Override public int getCount() { return COUNT; }
        };
    }

    public GuiMachine machine() { return machine; }

    private int pair(int lo) { return (data.get(lo) & 0xFFFF) | ((data.get(lo + 1) & 0xFFFF) << 16); }

    public boolean running() { return data.get(0) == 1; }
    public int energy() { return pair(1); }
    public int energyCap() { return pair(3); }

    public int progress() { return data.get(5 + 4 * MAX_TANKS) - 1; }
    public int status() { return data.get(5 + 4 * MAX_TANKS + 1); }

    public int upgradeCode(int i) { return data.get(UPG + i); }
    private static final int UPG = 5 + 4 * MAX_TANKS + 2;
    public int tankAmount(int t) { return pair(5 + 4 * t); }
    public int tankCap(int t) { return pair(7 + 4 * t); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (itemSlotCount == 0 || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        net.minecraft.world.inventory.Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < itemSlotCount) { if (!moveItemStackTo(stack, itemSlotCount, slots.size(), true)) return ItemStack.EMPTY; }
        else {

            boolean any = false;
            for (int i = 0; i < itemSlotCount && !stack.isEmpty(); i++)
                if (slots.get(i).mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false)) any = true;
            if (!any) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) { return be instanceof GuiAction a && stillValid(player) && a.guiAction(player, id); }

    @Override
    public boolean stillValid(Player player) {

        return be != null && !be.isRemoved() && Reach.within(player, be.getBlockPos(), be instanceof TurntableBlockEntity ? Balance.TT_MENU_REACH : 20.0);
    }

    private static final class Vec3Helper {
        static net.minecraft.world.phys.Vec3 centre(BlockPos p) { return net.minecraft.world.phys.Vec3.atCenterOf(p); }
    }
}
