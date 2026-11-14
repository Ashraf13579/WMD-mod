package com.ashraf.wmd;

final class TurntableBlocksClient {

    private TurntableBlocksClient() {}

    static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, TurntableBlocks.TURNTABLE_BE.get(), context -> new TurntableRenderer());
    }
}
