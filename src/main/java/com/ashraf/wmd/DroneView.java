package com.ashraf.wmd;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DroneView {

    private DroneView() {}

    public record Contact(int id, UUID owner, double x, double y, double z, int phase) {}

    private static final Map<Integer, Contact> CONTACTS = new ConcurrentHashMap<>();

    public static void set(DronesPayload p) {
        if (p.full()) CONTACTS.clear();
        for (Integer id : p.removed()) CONTACTS.remove(id);
        for (Contact c : p.drones()) CONTACTS.put(c.id(), c);
    }

    public static List<Contact> contacts() { return List.copyOf(CONTACTS.values()); }

    public static void clear() { CONTACTS.clear(); }
}
