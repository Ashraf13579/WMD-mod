package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcAirDefenceBlockEntity extends CcModuleBlockEntity {

    public static final int MAX_SITES = 8, LIST_CAP = 256, ROW = 8;
    public static final int READY = 0, RELOADING = 1, EMPTY = 2;

    private int[] sites = new int[0];
    private boolean satTrack;

    public CcAirDefenceBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_AIR_DEFENCE_BE.get(), pos, state, Balance.CC_AIR_DEFENCE_FE); }

    @Override public CcModule kind() { return CcModule.AIR_DEFENCE; }

    public int[] sites() { return sites; }
    public boolean satTracking() { return satTrack; }

    static int tierOf(InterceptorSite s) {
        if (s instanceof InterceptorLauncherBlockEntity) return 1;
        if (s instanceof InterceptorBatteryBlockEntity) return 2;
        if (s instanceof InterceptorSiloBlockEntity) return 3;
        if (s instanceof InterceptorSiloT4BlockEntity) return 4;
        return 0;
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if ((now + 7) % 40 != 0 || !(level instanceof ServerLevel sl)) return;
        int[] n;
        boolean track = false;
        if (isOnline()) {
            List<InterceptorSite> l = CommandCenter.sitesOf(getOwner(), LIST_CAP);
            n = new int[l.size() * ROW];
            for (int i = 0; i < l.size(); i++) {
                InterceptorSite s = l.get(i);
                int loaded = s.loaded();
                int st = loaded <= 0 ? EMPTY : s.cooldownTicks() > 0 ? RELOADING : READY;
                BlockPos p = s.getBlockPos();
                int tier = tierOf(s);
                int range = (int) Math.round(tier == 0 ? Balance.CIWS_RANGE : Interceptors.rangeOf(tier, getOwner()));
                int[] row = { p.getX(), p.getY(), p.getZ(), tier, st, loaded, s.slotCount(), range };
                System.arraycopy(row, 0, n, i * ROW, ROW);
            }
            track = Recon.tracking(sl.getServer().overworld(), getOwner());
        } else n = new int[0];
        if (!java.util.Arrays.equals(n, sites) || track != satTrack) {
            sites = n;
            satTrack = track;
            setChanged();
            sync();
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = isOnline() ? (float) Math.toRadians((tt * CcModelSpec.SWEEP_DEG_PER_TICK) % 360.0) : 0.0F;
        model.getBone("sweep0").ifPresent(b -> turn(b, a));
        model.getBone("sweep1").ifPresent(b -> turn(b, -a * 0.8F));
    }

    private static void turn(CcBones.Bone b, float a) {
        switch (CcModelSpec.SWEEP_AXIS) {
            case 'x' -> b.setRotX(a);
            case 'z' -> b.setRotZ(a);
            default -> b.setRotY(a);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putIntArray("sites", sites);
        tag.putBoolean("satTrack", satTrack);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sites = tag.getIntArray("sites");
        satTrack = tag.getBoolean("satTrack");
    }
}
