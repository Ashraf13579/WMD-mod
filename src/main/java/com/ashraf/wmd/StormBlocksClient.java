package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class StormBlocksClient {

    private StormBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, StormBlocks.STORM_GENERATOR_BE.get(), context -> new StagedUnitRenderer<StormGeneratorBlockEntity>("storm_generator", 16, 3));
    }
}
