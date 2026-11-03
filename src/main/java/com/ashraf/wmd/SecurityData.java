package com.ashraf.wmd;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class SecurityData {
    private boolean isPublic;
    private final Map<UUID, String> trusted = new LinkedHashMap<>();

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean b) { isPublic = b; }
    public Map<UUID, String> trusted() { return trusted; }
    public boolean allows(UUID id) { return isPublic || trusted.containsKey(id); }

    public void write(CompoundTag tag) {
        tag.putBoolean("public", isPublic);
        ListTag l = new ListTag();
        for (Map.Entry<UUID, String> e : trusted.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putString("name", e.getValue());
            l.add(c);
        }
        tag.put("trusted", l);
    }

    public void read(CompoundTag tag) {
        isPublic = tag.getBoolean("public");
        trusted.clear();
        for (Tag t : tag.getList("trusted", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("id")) trusted.put(c.getUUID("id"), c.getString("name"));
        }
    }
}
