package com.ashraf.wmd;

final class DeckConsoleBlocksClient {

    private DeckConsoleBlocksClient() {}

    static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, DeckConsoleBlocks.DECK_CONSOLE_BE.get(), context -> new DeckConsoleRenderer());
    }
}
