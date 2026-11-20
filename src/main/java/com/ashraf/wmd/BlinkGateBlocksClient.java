package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class BlinkGateBlocksClient {

    private BlinkGateBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, BlinkGateBlocks.BLINK_CORE_BE.get(), context -> new StagedUnitRenderer<BlinkGateCoreBlockEntity>("blink_gate_core", 2, 2));
        MountedRenderer.register(event, BlinkGateBlocks.BLINK_SEGMENT_BE.get(), context -> new StagedUnitRenderer<BlinkGatePieceBlockEntity>("blink_gate_segment", 2, 2));
        MountedRenderer.register(event, BlinkGateBlocks.BLINK_CORNER_BE.get(), context -> new StagedUnitRenderer<BlinkGatePieceBlockEntity>("blink_gate_corner", 5, 4));
    }
}
