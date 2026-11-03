package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

public final class BombView {

    private BombView() {}

    public record Contact(int id, UUID owner, double x, double y, double z, int kind, double ix, double iz) {}

    private static volatile List<Contact> contacts = List.of();

    public static void set(BombsPayload p) { contacts = List.copyOf(p.bombs()); }

    public static List<Contact> contacts() { return contacts; }

    public static void clear() { contacts = List.of(); }
}
