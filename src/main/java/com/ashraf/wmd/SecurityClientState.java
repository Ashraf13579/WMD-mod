package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;

public final class SecurityClientState {
    private SecurityClientState() {}

    public static BlockPos pos;
    public static boolean isPublic, canEdit;
    public static String ownerName = "";
    public static List<String> names = List.of();
    public static int version;

    public static void set(SecurityStatePayload p) {
        pos = p.pos(); isPublic = p.isPublic(); canEdit = p.canEdit(); ownerName = p.ownerName(); names = p.names();
        version++;
    }
}
