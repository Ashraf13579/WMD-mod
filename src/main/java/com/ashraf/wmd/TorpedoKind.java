package com.ashraf.wmd;

public enum TorpedoKind {
    T1("torpedo_t1", 1),
    T2("torpedo_t2", 2),
    T3("torpedo_t3", 3),
    T4("torpedo_t4", 4),
    UUV("uuv", 0),

    ATT("torpedo_att", 2);

    public final String id;
    public final int paint;

    TorpedoKind(String id, int paint) { this.id = id; this.paint = paint; }

    public String mesh() { return id; }

    public float axisPx() { return this == UUV ? 4.4F : this == ATT ? 4.0F : 6.0F; }

    public boolean tinted() { return this != UUV; }

    public boolean drone() { return this == UUV; }

    public boolean homing() { return this == T2 || this == T3; }

    public String label() { return this == UUV ? "UUV" : this == ATT ? "ATT" : "T" + (ordinal() + 1); }

    public double db() { return this == T4 ? Balance.TORP_T4_DB : this == UUV ? Balance.UUV_DB : this == ATT ? Balance.ATT_DB : Balance.TORP_DB; }

    public double speed() { return Balance.TORP_SPEED[ordinal()]; }
    public double range() { return Balance.TORP_RANGE[ordinal()]; }
    public float power() { return Balance.TORP_POWER[ordinal()]; }

    public static TorpedoKind of(int i) { TorpedoKind[] v = values(); return v[Math.max(0, Math.min(v.length - 1, i))]; }

    public static TorpedoKind of(net.minecraft.world.item.ItemStack s) { return s != null && s.getItem() instanceof TorpedoItem t ? t.kind : null; }
}
