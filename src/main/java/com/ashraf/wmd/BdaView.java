package com.ashraf.wmd;

public final class BdaView {

    private BdaView() {}

    public static volatile BdaPayload last = BdaPayload.NONE;
    public static volatile int version;

    public static void set(BdaPayload p) {
        last = p;
        version++;
    }

    public static int key() { BdaPayload p = last; return p.has() ? p.key() : 0; }

    public static BdaPayload done() { BdaPayload p = last; return p.has() && p.done() && p.after().length == CcBda.CROP * CcBda.CROP ? p : null; }

    public static void clear() { last = BdaPayload.NONE; version++; }
}
