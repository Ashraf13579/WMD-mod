package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class MirvBusItem extends MissilePartItem {

    public static final String PREFIX = "mirv:";

    public MirvBusItem(Properties p) { super(p, 3, 4, "mirv"); }

    public static List<String> loaded(ItemStack s) {
        List<String> out = new ArrayList<>();
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return out;
        String v = d.copyTag().getString("mirv");
        for (String w : v.split("\\+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    private static void set(ItemStack s, List<String> l) {
        CompoundTag t = s.has(DataComponents.CUSTOM_DATA) ? s.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        t.putString("mirv", String.join("+", l));
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    public static String type(ItemStack s) {
        List<String> l = loaded(s);
        return l.isEmpty() ? "he" : PREFIX + String.join("+", l);
    }

    static boolean fits(MissilePartItem w) {
        if (w.slot() != 3 || w instanceof MirvBusItem) return false;
        String v = w.variant();
        return !(v.equals("interceptor") || v.equals("decoy") || v.equals("asat") || v.equals("cubesat") || v.startsWith(Satellites.PREFIX));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bus = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(bus);
        if (level.isClientSide) return InteractionResultHolder.success(bus);
        List<String> l = loaded(bus);
        if (player.isShiftKeyDown()) {
            if (l.isEmpty()) return InteractionResultHolder.pass(bus);
            for (String w : l) {
                ItemStack back = LabRecipes.warheadStack(WarheadType.of(w));
                if (!back.isEmpty() && !player.getInventory().add(back)) player.drop(back, false);
            }
            set(bus, new ArrayList<>());
            player.displayClientMessage(Component.translatable("message.wmd.mirv.emptied"), true);
            level.playSound(null, player.blockPosition(), WmdSounds.MV_LOAD.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            return InteractionResultHolder.success(bus);
        }
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof MissilePartItem w) || !fits(w)) {
            player.displayClientMessage(Component.translatable("message.wmd.mirv.hint"), true);
            return InteractionResultHolder.pass(bus);
        }
        if (l.size() >= Balance.MIRV_MAX) { player.displayClientMessage(Component.translatable("message.wmd.mirv.full", Balance.MIRV_MAX).withStyle(ChatFormatting.YELLOW), true); return InteractionResultHolder.pass(bus); }
        l.add(w.variant());
        set(bus, l);
        if (!player.getAbilities().instabuild) off.shrink(1);
        player.displayClientMessage(Component.translatable("message.wmd.mirv.loaded", Component.translatable("warhead.wmd." + w.variant()), l.size(), Balance.MIRV_MAX).withStyle(ChatFormatting.AQUA), true);
        level.playSound(null, player.blockPosition(), WmdSounds.MV_LOAD.get(), SoundSource.PLAYERS, 0.8F, 0.75F);
        return InteractionResultHolder.success(bus);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<String> l = loaded(stack);
        tooltip.add(Component.translatable("tooltip.wmd.mirv.count", l.size(), Balance.MIRV_MAX).withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < l.size(); i++) tooltip.add(Component.literal("  " + (i + 1) + ". ").append(Component.translatable("warhead.wmd." + l.get(i))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.mirv.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
