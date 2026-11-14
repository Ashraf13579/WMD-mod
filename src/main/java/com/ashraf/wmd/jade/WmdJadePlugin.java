package com.ashraf.wmd.jade;

import net.minecraft.world.level.block.Block;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class WmdJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration reg) {
        reg.registerBlockDataProvider(MachineInfoProvider.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration reg) {
        reg.registerBlockComponent(MachineInfoProvider.INSTANCE, Block.class);

        reg.addRayTraceCallback((hit, accessor, original) -> com.ashraf.wmd.ClientCockpit.inJetSeat() ? null : accessor);
    }
}
