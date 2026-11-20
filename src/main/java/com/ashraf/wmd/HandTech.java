package com.ashraf.wmd;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HandTech {

    private HandTech() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Wmd.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_FE =
            COMPONENTS.registerComponentType("tool_fe", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_HEAT =
            COMPONENTS.registerComponentType("tool_heat", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    private static Item.Properties tool() { return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(); }

    public static final DeferredItem<TractorBeamItem> TRACTOR_BEAM = Wmd.ITEMS.registerItem("tractor_beam", TractorBeamItem::new, tool());
    public static final DeferredItem<PlasmaCutterItem> PLASMA_CUTTER = Wmd.ITEMS.registerItem("plasma_cutter", PlasmaCutterItem::new, tool());

    static void register(IEventBus bus) { COMPONENTS.register(bus); }

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new ComponentEnergyStorage(stack, TOOL_FE.get(), cap(stack), Balance.HTOOL_RECEIVE, 0),
                TRACTOR_BEAM.get(), PLASMA_CUTTER.get());
    }

    static void tab(CreativeModeTab.Output output) {
        for (DeferredItem<? extends Item> it : java.util.List.of(TRACTOR_BEAM, PLASMA_CUTTER)) {
            ItemStack s = new ItemStack(it.get());
            setFe(s, cap(s));
            output.accept(s);
        }
    }

    public static boolean isTool(ItemStack s) { return !s.isEmpty() && (s.getItem() instanceof TractorBeamItem || s.getItem() instanceof PlasmaCutterItem); }

    public static int cap(ItemStack s) { return s.getItem() instanceof PlasmaCutterItem ? Balance.PCU_FE_CAP : Balance.TRB_FE_CAP; }

    public static int fe(ItemStack s) { return s.isEmpty() ? 0 : s.getOrDefault(TOOL_FE.get(), 0); }

    public static void setFe(ItemStack s, int fe) {
        if (s.isEmpty()) return;
        int v = Mth.clamp(fe, 0, cap(s));
        if (v != fe(s)) s.set(TOOL_FE.get(), v);
    }

    public static boolean use(ItemStack s, int n) {
        int f = fe(s);
        if (f < n) return false;
        setFe(s, f - n);
        return true;
    }

    public static int heat(ItemStack s) { return s.isEmpty() ? 0 : s.getOrDefault(TOOL_HEAT.get(), 0); }

    public static void setHeat(ItemStack s, int h) {
        if (s.isEmpty()) return;
        int v = Mth.clamp(h, 0, Balance.PCU_HEAT_MAX + Balance.PCU_OVERHEAT_TICKS);
        if (v != heat(s)) s.set(TOOL_HEAT.get(), v);
    }

    public static boolean overheated(ItemStack s) { return heat(s) > Balance.PCU_HEAT_MAX; }

    static void inventoryTick(ItemStack s, Player p) {
        if (!(p instanceof ServerPlayer sp)) return;
        long now = sp.serverLevel().getGameTime();
        if (now % 20 == 0 && fe(s) > 0 && EmpField.jammed(sp.serverLevel(), sp.blockPosition())) empDrain(sp);
        if (EmpField.jammed(sp.serverLevel(), sp.blockPosition())) return;
        int missing = cap(s) - fe(s);
        if (missing <= 0) return;
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof ExoSuitItem)) return;
        int have = ExoSuitItem.fe(chest);
        if (have <= Balance.EXO_FE_CAP * Balance.HTOOL_SUIT_MIN) return;
        int give = Math.min(Balance.HTOOL_SUIT_FE, missing);
        ExoSuitItem.setFe(chest, have - give);
        setFe(s, fe(s) + give);
    }

    public static void empDrain(ServerPlayer p) {
        boolean any = false;
        for (ItemStack s : p.getInventory().items) if (isTool(s) && fe(s) > 0) { setFe(s, 0); any = true; }
        for (ItemStack s : p.getInventory().offhand) if (isTool(s) && fe(s) > 0) { setFe(s, 0); any = true; }
        if (any) {
            TractorBeam.drop(p);
            PlasmaCutter.stop(p);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.hand_tool.emp").withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }
}
