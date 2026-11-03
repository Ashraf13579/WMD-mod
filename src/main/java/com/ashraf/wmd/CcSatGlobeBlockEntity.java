package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcSatGlobeBlockEntity extends CcModuleBlockEntity {

    private boolean stationOk;
    private int[] hacked = new int[0], spent = new int[0], rekeyWait = new int[0];

    public CcSatGlobeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_SAT_GLOBE_BE.get(), pos, state, Balance.CC_GLOBE_FE); }

    @Override public CcModule kind() { return CcModule.SAT_CONTROL; }

    public boolean satsControlled() { return isOnline() && stationOk; }

    public boolean stationOk() { return stationOk; }

    public boolean hacked(int id) { for (int h : hacked) if (h == id) return true; return false; }

    public boolean spent(int id) { for (int h : spent) if (h == id) return true; return false; }

    public int rekeyWait(int id) { for (int i = 0; i + 1 < rekeyWait.length; i += 2) if (rekeyWait[i] == id) return rekeyWait[i + 1]; return 0; }

    @Override
    protected void moduleTick(Level level, long now) {
        if ((now + 5) % 20 != 0) return;
        boolean s = GroundStationBlockEntity.hasPoweredStation(getOwner());
        int[] hk = new int[0], sp = new int[0], rw = new int[0];
        if (level instanceof net.minecraft.server.level.ServerLevel sl && getOwner() != null) {
            net.minecraft.server.MinecraftServer server = sl.getServer();
            hk = SatHack.hacked(server, getOwner());
            java.util.List<Integer> spl = new java.util.ArrayList<>(), rwl = new java.util.ArrayList<>();
            long ms = System.currentTimeMillis();
            for (OrbitMath.Sat sat : OrbitData.get(server).sats()) {
                if (!getOwner().equals(sat.owner())) continue;
                if (SatDefence.TYPE.equals(sat.type()) && !SatDefence.charged(server, sat, ms)) spl.add(sat.id());
                long w = SatHack.rekeyIn(server, sat.id());
                if (w > 0) { rwl.add(sat.id()); rwl.add((int) ((w + 999L) / 1000L)); }
            }
            sp = spl.stream().mapToInt(Integer::intValue).toArray();
            rw = rwl.stream().mapToInt(Integer::intValue).toArray();
        }
        if (s != stationOk || !java.util.Arrays.equals(hk, hacked) || !java.util.Arrays.equals(sp, spent) || !java.util.Arrays.equals(rw, rekeyWait)) {
            stationOk = s;
            hacked = hk;
            spent = sp;
            rekeyWait = rw;
            setChanged();
            sync();
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        boolean on = isOnline();
        for (int i = 0; i < 3; i++) {
            final float a = on ? (float) Math.toRadians((tt * CcSpecC.RING_DEG_PER_TICK * (1.0 - 0.25 * i) * (i % 2 == 0 ? 1 : -1)) % 360.0) : 0.0F;
            model.getBone("ring" + i).ifPresent(b -> turn(b, CcSpecC.RING_AXIS, a));
        }
    }

    static void turn(CcBones.Bone b, char axis, float a) {
        switch (axis) {
            case 'x' -> b.setRotX(a);
            case 'z' -> b.setRotZ(a);
            default -> b.setRotY(a);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("stationOk", stationOk);
        tag.putIntArray("hacked", hacked);
        tag.putIntArray("spent", spent);
        tag.putIntArray("rekeyWait", rekeyWait);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stationOk = tag.getBoolean("stationOk");
        hacked = tag.getIntArray("hacked");
        spent = tag.getIntArray("spent");
        rekeyWait = tag.getIntArray("rekeyWait");
    }
}
