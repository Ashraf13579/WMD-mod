package com.ashraf.wmd;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class TorpedoItem extends Item {

    public final TorpedoKind kind;

    public TorpedoItem(TorpedoKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.wmd." + kind.id + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        if (kind.drone()) {
            lines.add(Component.translatable("tooltip.wmd.uuv.stats", String.format("%.0f", kind.speed() * 20.0), String.format("%,d", (int) kind.range()),
                    Balance.UUV_LIFE / 1200, String.format("%,d", (int) Balance.UUV_RANGE)).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            return;
        }
        lines.add(Component.translatable("tooltip.wmd.torpedo.stats", String.format("%.0f", kind.speed() * 20.0), String.format("%,d", (int) kind.range()),
                String.format("%.0f", kind.power())).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        if (AirGate.AVAILABLE && (kind == TorpedoKind.T1 || kind == TorpedoKind.T2))
            lines.add(Component.translatable("tooltip.wmd.torpedo.air").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (kind != TorpedoKind.T1) lines.add(Component.translatable("tooltip.wmd.torpedo.guide." + kind.id, (int) Balance.TORP_SEEK_RANGE, (int) Balance.TORP_WIRE_LEN)
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
