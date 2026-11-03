package com.ashraf.wmd;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

public class DepositOreBlock extends Block implements Hardened {

    public final String ore;

    public DepositOreBlock(String ore) {
        super(Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(8.0F, 1200.0F).sound(SoundType.DEEPSLATE));
        this.ore = ore;
    }

    @Override
    public int rating() { return 3; }
}
