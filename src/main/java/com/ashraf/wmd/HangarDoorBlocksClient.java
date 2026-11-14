package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class HangarDoorBlocksClient {

    private HangarDoorBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, HangarDoorBlocks.HANGAR_DOOR_BE.get(), context -> new HangarDoorRenderer());
    }
}
