package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class PlasmaMortarBlocksClient {

    private PlasmaMortarBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, PlasmaMortarBlocks.PLASMA_MORTAR_BE.get(), context -> new StagedUnitRenderer<PlasmaMortarBlockEntity>("plasma_mortar", 4, 2));
    }
}
