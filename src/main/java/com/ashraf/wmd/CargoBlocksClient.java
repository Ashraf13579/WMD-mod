package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

final class CargoBlocksClient {

    private CargoBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, CargoBlocks.CARGO_RAMP_BE.get(), context -> new CargoRampRenderer());
        MountedRenderer.register(event, CargoBlocks.CARGO_PALLET_BE.get(), context -> new CargoPalletRenderer());
        event.registerEntityRenderer(CargoBlocks.PALLET.get(), PalletEntityRenderer::new);
        event.registerEntityRenderer(CargoBlocks.PARACHUTE.get(), ParachuteRenderer::new);
    }

    static void screens(RegisterMenuScreensEvent event) {
        event.register(CargoBlocks.CARGO_PALLET_MENU.get(), CargoPalletScreen::new);
    }
}
