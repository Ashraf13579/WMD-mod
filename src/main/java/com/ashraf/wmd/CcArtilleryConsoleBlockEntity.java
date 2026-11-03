package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class CcArtilleryConsoleBlockEntity extends CcConsoleBlockEntity {

    public static final int[] ROUNDS = { 1, 2, 3, 5, 10, 0 };

    private int charge;

    private float coverOpen, knob = Float.NaN;
    private double lastTt;

    public CcArtilleryConsoleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_ARTILLERY_CONSOLE_BE.get(), pos, state, Balance.ART_CONSOLE_FE); }

    @Override public CcModule kind() { return CcModule.ARTILLERY_CONSOLE; }

    @Override protected boolean wantsIntel() { return true; }

    public int chargeSetting() { return charge; }

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = new ListTag();
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(owner)) out.add(row(level, owner, g));
        return out;
    }

    static CompoundTag row(ServerLevel consoleLevel, UUID owner, ArtilleryGunBlockEntity g) {
        CompoundTag c = new CompoundTag();
        BlockPos p = g.getBlockPos();
        c.putLong("p", p.asLong());
        c.putInt("g", g.gun());
        c.putInt("pk", g.picked());
        for (int t = 0; t < Artillery.TYPES.length; t++) c.putInt("a" + t, g.ammo(t));
        if (g instanceof HowitzerBlockEntity h) c.putInt("pc", h.propellant());
        c.putInt("st", g.gunStatus());
        c.putInt("m", g.mission());
        c.putBoolean("ht", g.hasTarget());
        c.putInt("tx", g.targetX());
        c.putInt("tz", g.targetZ());
        c.putInt("uc", g.usedCharge());
        boolean dim = g.getLevel() != consoleLevel;
        c.putBoolean("dim", dim);
        c.putBoolean("in", !dim && CcReach.reach(consoleLevel, owner, p));
        return c;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        if (!(level instanceof ServerLevel sl)) return null;
        String P = "message.wmd.art_console.";
        if (p.action() != CcBPayload.ART_FIRE && p.action() != CcBPayload.ART_CEASE) return null;
        List<Long> guns = new ArrayList<>(new LinkedHashSet<>(p.pads()));
        if (guns.isEmpty()) return Component.translatable(P + "no_guns");
        if (p.action() == CcBPayload.ART_CEASE) {
            int n = 0;
            for (long l : guns) {
                ArtilleryGunBlockEntity g = ArtilleryGuns.gunAt(sl, BlockPos.of(l), getOwner());
                if (g == null || g.mission() == 0) continue;
                g.act(ArtilleryGunBlockEntity.A_CEASE, 0, 0, player);
                n++;
            }
            refreshSoon();
            return Component.translatable(P + "ceased", n);
        }
        int x = Mth.clamp(p.x(), -29_999_000, 29_999_000), z = Mth.clamp(p.z(), -29_999_000, 29_999_000);
        int type = Artillery.clampType((int) (p.arg() & 0xFF));
        int rounds = (int) ((p.arg() >> 8) & 0xFF);
        int ch = Mth.clamp((int) ((p.arg() >> 16) & 0xFF), 0, Balance.HOWITZER_CHARGES);
        charge = ch;
        setChanged();
        int ok = 0;
        String by = player.getGameProfile().getName();
        for (long l : guns) {
            BlockPos gp = BlockPos.of(l);
            ArtilleryGunBlockEntity g = ArtilleryGuns.gunAt(sl, gp, getOwner());
            if (g == null) { player.displayClientMessage(Component.translatable(P + "gone", gp.toShortString()), false); continue; }
            CcReach.Result r = CcReach.check(sl, getOwner(), gp);
            if (!r.ok()) { player.displayClientMessage(Component.translatable(P + "out_of_reach", gp.toShortString(), r.text()), false); continue; }
            if (!Artillery.canFire(g.gun(), type)) {
                player.displayClientMessage(Component.translatable(P + "cant_type", Component.translatable(Artillery.gunNameKey(g.gun())), gp.toShortString(),
                        Component.translatable("gui.wmd.art_console.t." + Artillery.TYPES[type])), false);
                continue;
            }
            if (g.gun() == Artillery.HOWITZER) g.act(ArtilleryGunBlockEntity.A_CHARGE, ch, 0, null);
            int st = g.order(x, z, type, rounds <= 0 ? -1 : rounds, by);
            boolean accepted = st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING || st == ArtilleryGunBlockEntity.S_FIRING;
            if (accepted) ok++;
            player.displayClientMessage(Component.translatable(P + "gun_row", Component.translatable(Artillery.gunNameKey(g.gun())), gp.toShortString(),
                    Component.translatable("gui.wmd.art.st." + st)), false);
        }
        Wmd.LOGGER.info("Artillery console: {} ordered {} gun(s) ({} accepted) at {} {}: {} x {}", by, guns.size(), ok, x, z, Artillery.TYPES[type],
                rounds <= 0 ? "all" : Integer.toString(rounds));
        if (ok > 0) arm();
        sync();
        return Component.translatable(ok > 0 ? P + "fired" : P + "none_fired", ok, guns.size(), x, z);
    }

    public List<CompoundTag> gunRows() {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : rows()) if (t instanceof CompoundTag c) l.add(c);
        return l;
    }

    public boolean missionArmed(long now) {
        if (armed(now)) return true;
        for (CompoundTag c : gunRows()) if (c.getInt("m") != 0) return true;
        return false;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float target = isOnline() && missionArmed((long) tt) ? 1.0F : 0.0F;
        float step = 1.0F / CcSpecS52.ART_EASE_TICKS;
        float dt = (float) Math.min(5.0, Math.max(0.0, tt - lastTt));
        lastTt = tt;
        coverOpen += Math.max(-step * dt, Math.min(step * dt, target - coverOpen));
        final float a = (float) Math.toRadians(CcSpecS52.ART_COVER_OPEN_DEG) * coverOpen;
        model.getBone("cover").ifPresent(b -> b.setRotX(a));
        int k = charge <= 0 ? 3 : charge;
        float kt = CcSpecS52.ART_KNOB_K1_DEG + CcSpecS52.ART_KNOB_STEP_DEG * (k - 1);
        if (Float.isNaN(knob)) knob = kt;
        else knob += Math.max(-6.0F * dt, Math.min(6.0F * dt, kt - knob));
        final float r = (float) Math.toRadians(knob);
        model.getBone("knob").ifPresent(b -> CcSatGlobeBlockEntity.turn(b, CcSpecS52.ART_KNOB_AXIS, r));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("artCharge", charge);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("artCharge", charge);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = Mth.clamp(tag.getInt("artCharge"), 0, Balance.HOWITZER_CHARGES);
    }
}
