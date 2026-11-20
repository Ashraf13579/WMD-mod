package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class PulseEmpBlocksClient {

    private PulseEmpBlocksClient() {}

    static final float[][] HOUSING = {
            { -56F, 0F, -56F, 56F, 15F, 54F }, { -56F, 0F, 54F, -6.6F, 15F, 56F }, { 6.6F, 0F, 54F, 56F, 15F, 56F },
            { -6.6F, 0F, 54F, 6.6F, 1.8F, 56F }, { -6.6F, 14F, 54F, 6.6F, 15F, 56F } };

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, PulseEmpBlocks.PULSE_EMP_BE.get(), context -> new CopycatUnitRenderer<PulseEmpBlockEntity>("pulse_emp", 8, 4, HOUSING));
    }
}
