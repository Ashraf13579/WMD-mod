package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class GravityWellBlocksClient {

    private GravityWellBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, GravityWellBlocks.GRAVITY_WELL_BE.get(), context -> new StagedUnitRenderer<GravityWellBlockEntity>("gravity_well", 5, 3));
    }
}
