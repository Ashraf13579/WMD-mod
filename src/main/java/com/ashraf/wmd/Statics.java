package com.ashraf.wmd;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Statics {

    private Statics() {}

    private static final List<Object> SERVER = new CopyOnWriteArrayList<>(), CLIENT = new CopyOnWriteArrayList<>();

    public static <T extends Map<?, ?>> T server(T m) { SERVER.add(m); return m; }
    public static <T extends Collection<?>> T server(T c) { SERVER.add(c); return c; }

    public static <T extends Map<?, ?>> T client(T m) { CLIENT.add(m); return m; }
    public static <T extends Collection<?>> T client(T c) { CLIENT.add(c); return c; }

    static void clearServer() { clear(SERVER); }

    static void clearClient() { clear(CLIENT); }

    private static void clear(List<Object> l) {
        for (Object o : l) {
            try {
                if (o instanceof Map<?, ?> m) { synchronized (m) { m.clear(); } }
                else if (o instanceof Collection<?> c) { synchronized (c) { c.clear(); } }
            } catch (Throwable t) {
                Wmd.LOGGER.warn("[statics] could not clear a registered collection", t);
            }
        }
    }
}
