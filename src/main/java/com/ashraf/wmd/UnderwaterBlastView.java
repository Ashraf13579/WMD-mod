package com.ashraf.wmd;

final class UnderwaterBlastView {
    private UnderwaterBlastView() {}

    static void onReceive(UnderwaterBlastPayload p) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) Client.go(p);
    }

    private static final class Client {
        static void go(UnderwaterBlastPayload p) { ClientUnderwaterBlast.onReceive(p); }
    }
}
