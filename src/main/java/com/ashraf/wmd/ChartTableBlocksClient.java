package com.ashraf.wmd;

final class ChartTableBlocksClient {

    private ChartTableBlocksClient() {}

    static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, ChartTableBlocks.CHART_TABLE_BE.get(), context -> new ChartTableRenderer());
    }
}
