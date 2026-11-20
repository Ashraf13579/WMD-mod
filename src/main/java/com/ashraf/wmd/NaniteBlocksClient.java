package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class NaniteBlocksClient {

    private NaniteBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, NaniteBlocks.NANITE_FORGE_BE.get(), context -> new NaniteForgeRenderer());
    }
}
