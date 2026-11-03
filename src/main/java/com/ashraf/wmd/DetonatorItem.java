package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class DetonatorItem extends Item {

    public DetonatorItem(Properties properties) { super(properties); }

    private static CompoundTag tag(ItemStack s) { return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }

    static List<Long> links(ItemStack s) {
        List<Long> l = new ArrayList<>();
        for (long v : tag(s).getLongArray("c4")) l.add(v);
        return l;
    }

    private static void store(ItemStack s, List<Long> l, String dim) {
        CompoundTag t = tag(s);
        t.putLongArray("c4", l);
        t.putString("dim", dim);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    static void link(ItemStack s, Player p, C4ChargeBlockEntity be) {
        if (be.getLevel() == null) return;
        String dim = be.getLevel().dimension().location().toString();
        List<Long> l = dim.equals(tag(s).getString("dim")) ? links(s) : new ArrayList<>();
        long pos = be.getBlockPos().asLong();
        if (l.contains(pos)) { p.displayClientMessage(Component.translatable("message.wmd.c4.already", l.size()), true); return; }
        if (l.size() >= Balance.C4_LINKS) { p.displayClientMessage(Component.translatable("message.wmd.c4.full", Balance.C4_LINKS), true); return; }
        l.add(pos);
        store(s, l, dim);
        be.getLevel().playSound(null, be.getBlockPos(), WmdSounds.DET_CLICK.get(), SoundSource.PLAYERS, 0.7F, 1.4F);
        p.displayClientMessage(Component.translatable("message.wmd.c4.linked", l.size(), Balance.C4_LINKS), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(s);
        if (p.isShiftKeyDown()) {
            store(s, new ArrayList<>(), "");
            p.displayClientMessage(Component.translatable("message.wmd.c4.cleared"), true);
            return InteractionResultHolder.success(s);
        }
        List<Long> l = links(s);
        int fired = 0, far = 0;
        if (sl.dimension().location().toString().equals(tag(s).getString("dim"))) {
            for (long v : l) {
                BlockPos bp = BlockPos.of(v);
                if (bp.distToCenterSqr(p.position()) > (double) Balance.C4_RANGE * Balance.C4_RANGE || !sl.isLoaded(bp)) { far++; continue; }
                if (sl.getBlockEntity(bp) instanceof C4ChargeBlockEntity c && !c.armed()) { c.arm(Balance.C4_FUSE); fired++; }
            }
        }
        if (fired > 0) {
            List<Long> keep = new ArrayList<>();
            for (long v : l) if (!(sl.getBlockEntity(BlockPos.of(v)) instanceof C4ChargeBlockEntity c && c.armed())) keep.add(v);
            store(s, keep, sl.dimension().location().toString());
        }
        sl.playSound(null, p.blockPosition(), WmdSounds.DET_CLICK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.displayClientMessage(Component.translatable(fired > 0 ? "message.wmd.c4.fired" : l.isEmpty() ? "message.wmd.c4.no_links" : "message.wmd.c4.none_in_range",
                fired, far, Balance.C4_RANGE), true);
        p.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(s);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.detonator", links(s).size(), Balance.C4_LINKS).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
