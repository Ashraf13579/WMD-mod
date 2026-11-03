package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcIronDomeBlockEntity extends CcConsoleBlockEntity {

    private boolean hasCentre;
    private int cx, cz, radius = 128;

    private boolean active;
    private final IronDome.Dome dome = new IronDome.Dome();
    private boolean joined;

    private float dial = Float.NaN;
    private double lastTt;

    public CcIronDomeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_IRON_DOME_BE.get(), pos, state, Balance.DOME_FE); }

    @Override public CcModule kind() { return CcModule.IRON_DOME; }

    @Override protected boolean wantsIntel() { return true; }

    IronDome.Dome dome() { return dome; }

    public boolean hasCentre() { return hasCentre; }
    public int centreX() { return cx; }
    public int centreZ() { return cz; }
    public int radius() { return radius; }

    public boolean domeActive() { return active; }

    @Override
    protected void moduleTick(Level level, long now) {
        if (!joined && !level.isClientSide) { IronDome.join(this); joined = true; }
        if (level instanceof ServerLevel sl && (now + (worldPosition.asLong() & 15L)) % 20 == 2) refreshDome(sl);
        super.moduleTick(level, now);
    }

    private void refreshDome(ServerLevel sl) {
        boolean was = active;
        dome.check(sl, getOwner(), getOwnerName(), hasCentre, isOnline(), cx, cz, radius);
        boolean now = dome.active;
        if (now != was) {
            active = now;
            setChanged();
            sync();
            if (now) IronDome.announceUp(sl.getServer(), getOwner(), cx, cz, radius);
            else if (hasCentre && isOnline()) {
                int down = 0;
                for (int s : dome.state) if (s != IronDome.M_OK) down++;
                IronDome.announceDown(sl.getServer(), getOwner(), cx, cz, down);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        IronDome.leave(this);
        joined = false;
    }

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = new ListTag();
        if (!hasCentre) return out;
        for (int i = 0; i < IronDome.MARKERS; i++) {
            int[] m = IronDome.marker(i, cx, cz, radius);
            CompoundTag c = new CompoundTag();
            c.putInt("i", i);
            c.putInt("x", m[0]);
            c.putInt("z", m[1]);
            c.putInt("st", dome.state[i]);
            c.putLong("cp", dome.guns[i] != null ? dome.guns[i].getBlockPos().asLong() : 0L);
            out.add(c);
        }
        return out;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        if (!(level instanceof ServerLevel sl)) return null;
        String P = "message.wmd.dome.";
        if (p.action() == CcBPayload.DOME_CLEAR) {
            hasCentre = false;
            setChanged();
            refreshDome(sl);
            sync();
            return Component.translatable(P + "cleared");
        }
        if (p.action() != CcBPayload.DOME_SET) return null;
        int x = Mth.clamp(p.x(), -29_999_000, 29_999_000), z = Mth.clamp(p.z(), -29_999_000, 29_999_000);
        int r = IronDome.clampRadius((int) p.arg());
        CcReach.Result reach = CcReach.check(sl, getOwner(), x + 0.5, z + 0.5);
        if (!reach.ok()) return Component.translatable(P + "out_of_reach", x, z, reach.text());
        hasCentre = true;
        cx = x;
        cz = z;
        radius = r;
        setChanged();
        refreshDome(sl);
        sync();
        arm();
        Wmd.LOGGER.info("Iron Dome: {} set the centre {} {} radius {}", player.getGameProfile().getName(), x, z, r);
        return Component.translatable(P + "set", x, z, r, Balance.DOME_MARKER_TOL);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float f = (radius - Balance.DOME_RADIUS_MIN) / (float) Math.max(1, Balance.DOME_RADIUS_MAX - Balance.DOME_RADIUS_MIN);
        float target = CcSpecS52.DOME_DIAL_MIN_DEG + (CcSpecS52.DOME_DIAL_MAX_DEG - CcSpecS52.DOME_DIAL_MIN_DEG) * Mth.clamp(f, 0.0F, 1.0F);
        float dt = (float) Math.min(5.0, Math.max(0.0, tt - lastTt));
        lastTt = tt;
        if (Float.isNaN(dial)) dial = target;
        else dial += Math.max(-8.0F * dt, Math.min(8.0F * dt, target - dial));
        final float a = (float) Math.toRadians(dial);
        model.getBone("dial").ifPresent(b -> CcSatGlobeBlockEntity.turn(b, CcSpecS52.DOME_DIAL_AXIS, a));
    }

    private void writeDome(CompoundTag tag) {
        tag.putBoolean("domeSet", hasCentre);
        tag.putInt("domeX", cx);
        tag.putInt("domeZ", cz);
        tag.putInt("domeR", radius);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeDome(tag);
        tag.putBoolean("domeWas", active);
        tag.putIntArray("domeSt", dome.state.clone());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeDome(tag);
        tag.putBoolean("domeOn", active);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hasCentre = tag.getBoolean("domeSet");
        cx = tag.getInt("domeX");
        cz = tag.getInt("domeZ");
        radius = tag.contains("domeR") ? IronDome.clampRadius(tag.getInt("domeR")) : 128;
        active = tag.contains("domeOn") ? tag.getBoolean("domeOn") : tag.getBoolean("domeWas");
        int[] st = tag.getIntArray("domeSt");
        if (st.length == IronDome.MARKERS) System.arraycopy(st, 0, dome.state, 0, IronDome.MARKERS);
        dome.active = active;
    }
}
