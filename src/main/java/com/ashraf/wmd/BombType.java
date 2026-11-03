package com.ashraf.wmd;

public enum BombType {

    IRON("bomb_iron",       WarheadType.HE,           false, "bomb_iron",    32.0F, 4.0F),
    LASER("bomb_laser",     WarheadType.HEAVY_BLAST,  false, "bomb_laser",   44.0F, 4.0F),
    GLIDE("bomb_glide",     WarheadType.HEAVY_BLAST,  false, "bomb_glide",   40.0F, 4.8F),
    CLUSTER("bomb_cluster", WarheadType.CARPET,       false, "bomb_cluster", 40.0F, 4.8F),

    DELAYED("bomb_delayed", WarheadType.UPHEAVAL,     false, "bomb_delayed", 32.0F, 4.0F),
    NAPALM("bomb_napalm",   WarheadType.NAPALM,       false, "bomb_napalm",  40.0F, 4.8F),
    PENETRATOR("bomb_penetrator", WarheadType.PENETRATOR, true, "bomb_penetrator", 80.0F, 6.4F),
    DAISY("bomb_daisy",     WarheadType.DAISY_CUTTER, true,  "bomb_daisy",  100.0F, 11.2F),
    TALLBOY("bomb_tallboy", WarheadType.EARTHQUAKE,   true,  "bomb_tallboy", 104.0F, 8.0F),
    BOUNCING("bomb_bouncing", WarheadType.DAM_BREAKER, true, "bomb_bouncing", 24.0F, 12.0F),

    TSAR("bomb_tsar",       WarheadType.TSAR,         true,  "bomb_tsar",   128.0F, 16.0F);

    public final String id, mesh;
    public final WarheadType blast;
    public final boolean heavy;
    public final float length, radius;

    BombType(String id, WarheadType blast, boolean heavy, String mesh, float length, float radius) {
        this.id = id; this.blast = blast; this.heavy = heavy; this.mesh = mesh; this.length = length; this.radius = radius;
    }

    public static BombType ofOrdinal(int i) { return i >= 0 && i < values().length ? values()[i] : IRON; }

    public static BombType of(net.minecraft.world.item.ItemStack s) { return !s.isEmpty() && s.getItem() instanceof BombItem b ? b.type() : null; }
}
