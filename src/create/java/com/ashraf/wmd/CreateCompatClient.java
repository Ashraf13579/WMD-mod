package com.ashraf.wmd;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class CreateCompatClient {

    private CreateCompatClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(CreateCompat.DRIVE_COUPLING_BE.get(), ctx -> new DriveCouplingRenderer()));
    }
}
