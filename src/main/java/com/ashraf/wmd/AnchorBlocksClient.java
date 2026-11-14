package com.ashraf.wmd;

final class AnchorBlocksClient {

    private AnchorBlocksClient() {}

    static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, AnchorBlocks.ANCHOR_WINDLASS_BE.get(), context -> new AnchorWindlassRenderer());
        MountedRenderer.register(event, AnchorBlocks.HAWSE_PIPE_BE.get(), context -> new CopycatUnitRenderer<HawsePipeBlockEntity>("hawse_pipe", 1, -1, null));
    }
}
