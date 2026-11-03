package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcInterceptorConsoleBlockEntity extends CcConsoleBlockEntity {

    private int mode = SatDefence.MODE_ZONE, eff = SatDefence.MODE_ZONE;
    private ListTag log = new ListTag();

    private float armOpen, knob = -1.0F;

    public CcInterceptorConsoleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_INTERCEPTOR_CONSOLE_BE.get(), pos, state, Balance.CC_INTERCEPTOR_FE); }

    @Override public CcModule kind() { return CcModule.INT_SAT_CONSOLE; }

    public int mode() { return mode; }
    public int effectiveMode() { return eff; }

    public ListTag log() { return log; }

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = new ListTag();
        SatDefenceData d = SatDefenceData.get(level.getServer());
        for (OrbitMath.Sat s : OrbitData.get(level.getServer()).sats()) {
            if (owner == null || !owner.equals(s.owner()) || !SatDefence.TYPE.equals(s.type())) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("id", s.id());
            c.putLong("shot", d.shotMs(s.id()));
            c.putInt("hits", d.hits(s.id()));
            c.putInt("miss", d.misses(s.id()));
            c.putBoolean("ctl", OrbitData.controlled(s));
            out.add(c);
        }
        return out;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        if (p.action() != CcBPayload.ENGAGE || level == null || level.getServer() == null) return null;
        int m = p.arg() == SatDefence.MODE_ANY ? SatDefence.MODE_ANY : SatDefence.MODE_ZONE;
        SatDefenceData.get(level.getServer()).setMode(getOwner(), m);
        refreshView(level.getServer());
        Wmd.LOGGER.info("Interceptor satellites of {}: engage mode {}", player.getGameProfile().getName(), m == SatDefence.MODE_ANY ? "ANY" : "OWN AREA");
        return Component.translatable(m == SatDefence.MODE_ANY ? "message.wmd.intsat.mode_any" : "message.wmd.intsat.mode_zone", String.format("%,d", Balance.INT_SAT_ZONE));
    }

    @Override
    protected void moduleTick(Level level, long now) {
        super.moduleTick(level, now);
        if (level.getServer() != null && (now + (worldPosition.asLong() & 15L)) % 20 == 9) refreshView(level.getServer());
    }

    private void refreshView(net.minecraft.server.MinecraftServer server) {
        SatDefenceData d = SatDefenceData.get(server);
        int m = d.mode(getOwner()), e = isOnline() ? SatDefence.effectiveMode(server, getOwner()) : SatDefence.MODE_ZONE;
        ListTag l = new ListTag();
        l.addAll(d.log(getOwner()));
        if (m != mode || e != eff || !l.equals(log)) {
            mode = m;
            eff = e;
            log = l;
            setChanged();
            sync();
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float target = isOnline() && eff == SatDefence.MODE_ANY ? 1.0F : 0.0F;
        float step = 1.0F / CcSpecS5.ARM_EASE_TICKS;
        if (Math.abs(tt - lastTt) > 0.0) {
            float dt = (float) Math.min(5.0, Math.max(0.0, tt - lastTt));
            armOpen += Math.max(-step * dt, Math.min(step * dt, target - armOpen));
            lastTt = tt;
        }
        final float a = (float) Math.toRadians(CcSpecS5.ARM_OPEN_DEG) * armOpen;
        model.getBone("arm").ifPresent(b -> b.setRotX(a));

        float kt = mode == SatDefence.MODE_ANY ? 1.0F : 0.0F;
        if (knob < 0.0F) knob = kt;
        else knob += Math.max(-step, Math.min(step, kt - knob));
        final float k = (float) Math.toRadians(CcSpecS5.ENGAGE_ZONE_DEG + (CcSpecS5.ENGAGE_ANY_DEG - CcSpecS5.ENGAGE_ZONE_DEG) * knob);
        model.getBone("engage").ifPresent(b -> CcSatGlobeBlockEntity.turn(b, CcSpecS5.ENGAGE_AXIS, k));
    }

    private double lastTt;

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("mode", mode);
        tag.putInt("eff", eff);
        tag.put("log", log);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.getInt("mode");
        eff = tag.getInt("eff");
        if (tag.contains("log")) log = tag.getList("log", Tag.TAG_COMPOUND);
    }
}
