package com.ashraf.wmd;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public record MissileBuild(int airframe, int motor, int guidance, int warhead, String warheadType, boolean uplink, boolean divert, boolean refined, boolean staged, int boosters, int kind,
                           boolean camPod, String seeker, boolean hypersonic) {

    public MissileBuild(int airframe, int motor, int guidance, int warhead, String warheadType, boolean uplink, boolean divert, boolean refined, boolean staged, int boosters, int kind,
                        boolean camPod, String seeker) {
        this(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, staged, boosters, kind, camPod, seeker, false);
    }

    public MissileBuild withHypersonic(boolean h) {
        return h == hypersonic ? this : new MissileBuild(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, staged, boosters, kind, camPod, seeker, h);
    }

    public static final String SEEK_LASER = "laser", SEEK_HEAT = "heat", SEEK_ARM = "arm", SEEK_TV = "tv";

    public MissileBuild(int airframe, int motor, int guidance, int warhead, String warheadType, boolean uplink, boolean divert, boolean refined, boolean staged, int boosters, int kind,
                        boolean camPod) {
        this(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, staged, boosters, kind, camPod, "");
    }

    public boolean hasCamera() { return camPod || SEEK_TV.equals(seeker) || SEEK_LASER.equals(seeker); }

    public MissileBuild withSeeker(String s) {
        String v = s == null ? "" : s;
        return v.equals(seeker) ? this : new MissileBuild(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, staged, boosters, kind, camPod, v, hypersonic);
    }

    public MissileBuild withUplink(boolean u) {
        return u == uplink ? this : new MissileBuild(airframe, motor, guidance, warhead, warheadType, u, divert, refined, staged, boosters, kind, camPod, seeker, hypersonic);
    }

    public MissileBuild(int airframe, int motor, int guidance, int warhead, String warheadType, boolean uplink, boolean divert, boolean refined, boolean staged, int boosters, int kind) {
        this(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, staged, boosters, kind, false);
    }

    public MissileBuild withCamPod(boolean pod) {
        return pod == camPod ? this : new MissileBuild(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, staged, boosters, kind, pod, seeker, hypersonic);
    }

    public MissileBuild(int airframe, int motor, int guidance, int warhead, String warheadType, boolean uplink, boolean divert, boolean refined, boolean staged, int boosters) {
        this(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, staged, boosters, 0);
    }

    public MissileBuild(int airframe, int motor, int guidance, int warhead, String warheadType, boolean uplink, boolean divert, boolean refined) {
        this(airframe, motor, guidance, warhead, warheadType, uplink, divert, refined, false, 0);
    }

    public MissileBuild(int airframe, int motor, int guidance, int warhead, String warheadType, boolean uplink, boolean divert) {
        this(airframe, motor, guidance, warhead, warheadType, uplink, divert, false);
    }

    public static final Codec<MissileBuild> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("airframe").forGetter(MissileBuild::airframe),
            Codec.INT.fieldOf("motor").forGetter(MissileBuild::motor),
            Codec.INT.fieldOf("guidance").forGetter(MissileBuild::guidance),
            Codec.INT.fieldOf("warhead").forGetter(MissileBuild::warhead),
            Codec.STRING.optionalFieldOf("warhead_type", "he").forGetter(MissileBuild::warheadType),
            Codec.BOOL.optionalFieldOf("uplink", false).forGetter(MissileBuild::uplink),
            Codec.BOOL.optionalFieldOf("divert", false).forGetter(MissileBuild::divert),
            Codec.BOOL.optionalFieldOf("refined", false).forGetter(MissileBuild::refined),
            Codec.BOOL.optionalFieldOf("staged", false).forGetter(MissileBuild::staged),
            Codec.INT.optionalFieldOf("boosters", 0).forGetter(MissileBuild::boosters),
            Codec.INT.optionalFieldOf("kind", 0).forGetter(MissileBuild::kind),
            Codec.BOOL.optionalFieldOf("cam_pod", false).forGetter(MissileBuild::camPod),
            Codec.STRING.optionalFieldOf("seeker", "").forGetter(MissileBuild::seeker),
            Codec.BOOL.optionalFieldOf("hypersonic", false).forGetter(MissileBuild::hypersonic)
    ).apply(i, MissileBuild::new));

    public static final StreamCodec<ByteBuf, MissileBuild> STREAM_CODEC = StreamCodec.of(
            (buf, b) -> {
                ByteBufCodecs.VAR_INT.encode(buf, b.airframe()); ByteBufCodecs.VAR_INT.encode(buf, b.motor());
                ByteBufCodecs.VAR_INT.encode(buf, b.guidance()); ByteBufCodecs.VAR_INT.encode(buf, b.warhead());
                ByteBufCodecs.STRING_UTF8.encode(buf, b.warheadType());
                ByteBufCodecs.BOOL.encode(buf, b.uplink()); ByteBufCodecs.BOOL.encode(buf, b.divert()); ByteBufCodecs.BOOL.encode(buf, b.refined());
                ByteBufCodecs.BOOL.encode(buf, b.staged()); ByteBufCodecs.VAR_INT.encode(buf, b.boosters()); ByteBufCodecs.VAR_INT.encode(buf, b.kind());
                ByteBufCodecs.BOOL.encode(buf, b.camPod());
                ByteBufCodecs.STRING_UTF8.encode(buf, b.seeker());
                ByteBufCodecs.BOOL.encode(buf, b.hypersonic());
            },
            buf -> new MissileBuild(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    public static MissileBuild preset(int tier) {
        int g = Mth.clamp(tier, 1, 4);
        return new MissileBuild(g, g, g, g, "he", false, false);
    }

    public static MissileBuild of(ItemStack stack) {
        MissileBuild b = stack.get(WmdComponents.MISSILE_BUILD.get());
        if (b == null && stack.getItem() instanceof StageItem s) return s.defaultBuild();
        return b != null ? b : preset(Math.max(1, Missiles.tierOf(stack)));
    }

    public static MissileBuild interceptor(int tier, boolean refined) {
        int g = Mth.clamp(tier, 1, 3);
        return new MissileBuild(g, g, g, 1, "interceptor", false, false, refined);
    }

    public boolean isInterceptor() { return warheadType.equals("interceptor"); }

    public boolean isMirv() { return warheadType.startsWith(MirvBusItem.PREFIX); }

    public java.util.List<String> mirvWarheads() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (isMirv()) for (String s : warheadType.substring(MirvBusItem.PREFIX.length()).split("\\+")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public static MissileBuild reentry(String w) { return new MissileBuild(1, 4, 4, Math.max(1, WarheadType.of(w).tier), w, false, false, false, false, 0, KIND_RV); }

    public static final int KIND_CRUISE = 4;
    public boolean isCruise() { return kind == KIND_CRUISE; }

    public static final int KIND_RV = 5;
    public boolean isRv() { return kind == KIND_RV; }

    public static final int KIND_SLBM = 6;
    public boolean isSlbm() { return kind == KIND_SLBM; }

    public boolean isStage() { return kind >= 1 && kind <= 3; }

    public int calibre() { return Mth.clamp(airframe, 1, 4); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("airframe", airframe); t.putInt("motor", motor); t.putInt("guidance", guidance); t.putInt("warhead", warhead);
        t.putString("warheadType", warheadType); t.putBoolean("uplink", uplink); t.putBoolean("divert", divert); t.putBoolean("refined", refined);
        t.putBoolean("staged", staged); t.putInt("boosters", boosters); t.putInt("kind", kind);
        if (camPod) t.putBoolean("camPod", true);
        if (!seeker.isEmpty()) t.putString("seeker", seeker);
        if (hypersonic) t.putBoolean("hypersonic", true);
        return t;
    }

    public static MissileBuild load(CompoundTag t, int fallbackTier) {
        if (!t.contains("airframe")) return preset(fallbackTier);
        return new MissileBuild(t.getInt("airframe"), t.getInt("motor"), t.getInt("guidance"), t.getInt("warhead"),
                t.contains("warheadType") ? t.getString("warheadType") : "he", t.getBoolean("uplink"), t.getBoolean("divert"), t.getBoolean("refined"),
                t.getBoolean("staged"), Mth.clamp(t.getInt("boosters"), 0, 4), Mth.clamp(t.getInt("kind"), 0, KIND_SLBM), t.getBoolean("camPod"), t.getString("seeker"), t.getBoolean("hypersonic"));
    }
}
