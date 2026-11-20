package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class HoloBlocksClient {

    private HoloBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, HoloBlocks.HOLO_TABLE_BE.get(), context -> new HoloTableRenderer());
    }
}
