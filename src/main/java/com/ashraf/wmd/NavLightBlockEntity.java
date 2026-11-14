package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class NavLightBlockEntity extends BlockEntity {

    public static final int ON = 0, OFF = 1, AUTO = 2;

    private static final Map<UUID, Long> ABOARD = Statics.server(new HashMap<>());

    private static final Map<UUID, long[]> CRAFT = Statics.server(new HashMap<>());

    private int power = AUTO, bright = 1;
    private boolean lit;
    private int oldMode = -1;

    public NavLightBlockEntity(BlockPos pos, BlockState state) { super(AirBlocks.NAV_LIGHT_BE.get(), pos, state); }

    public int power() { return power; }
    public int bright() { return bright; }
    public boolean lit() { return lit; }

    static boolean aboard(ServerLevel sl, UUID ship) {
        Long t = ship == null ? null : ABOARD.get(ship);
        return t != null && sl.getGameTime() - t <= 40;
    }

    void set(int power, int bright) {
        this.power = Mth.clamp(power, 0, 2);
        this.bright = Mth.clamp(bright, 0, 2);
        if (level instanceof ServerLevel sl) lit = work(sl);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState st = getBlockState();
            if (st.hasProperty(NavLightBlock.LIT) && st.getValue(NavLightBlock.LIT) != lit) level.setBlock(worldPosition, st.setValue(NavLightBlock.LIT, lit), 3);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, NavLightBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.oldMode >= 0) {
            NavLightBlock.Func f = NavLightBlock.Func.values()[Math.min(be.oldMode, 3)];
            be.oldMode = -1;
            sl.setBlock(pos, state.setValue(NavLightBlock.FUNC, f), 3);
            be.setChanged();
            return;
        }
        if ((sl.getGameTime() + (pos.asLong() & 15)) % 20 != 0) return;
        boolean l = be.work(sl);
        if (l != be.lit || state.getValue(NavLightBlock.LIT) != l) { be.lit = l; be.sync(); }
    }

    private boolean work(ServerLevel sl) {
        if (!AirGate.on() || power == OFF) return false;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship != null) ABOARD.put(ship, sl.getGameTime());
        if (power == ON) return true;
        if (ship == null) return true;
        long[] c = craft(sl, ship);
        NavLightBlock.Func f = getBlockState().getValue(NavLightBlock.FUNC);
        if (c[1] == 0) return false;
        if (f == NavLightBlock.Func.STROBE && c[4] == 1 && c[2] == 0) return false;
        return f != NavLightBlock.Func.LANDING || c[3] == 1;
    }

    private static long[] craft(ServerLevel sl, UUID ship) {
        long now = sl.getGameTime();
        long[] c = CRAFT.get(ship);
        if (c != null && now - c[0] < Balance.NL_SCAN && now >= c[0]) return c;
        boolean cockpit = false, av = false, engine = false;
        for (BlockPos q : SableBridge.blockEntitiesOn(sl, ship)) {
            if (!sl.isLoaded(q)) continue;
            BlockEntity be = sl.getBlockEntity(q);
            if (be instanceof JetCockpitBlockEntity jc) { cockpit = true; av |= jc.avionicsOn(); }
            else if (be instanceof AirEngineBlockEntity ae && ae.isRunning()) engine = true;
        }
        if (!cockpit) av = PowerCoreBlockEntity.chargeOf(sl, ship)[0] > 0;
        int g = LandingGearBlockEntity.stateAboard(sl, ship);
        boolean gear = g < 0 || g % 10 >= 1;
        c = new long[] { now, av ? 1 : 0, engine ? 1 : 0, gear ? 1 : 0, cockpit ? 1 : 0 };
        if (CRAFT.size() > 512) CRAFT.clear();
        CRAFT.put(ship, c);
        return c;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.putInt("nlPower", power);
        tag.putInt("nlBright", bright);
        tag.putBoolean("nlLit", lit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        if (tag.contains("nlPower")) power = Mth.clamp(tag.getInt("nlPower"), 0, 2);
        if (tag.contains("nlBright")) bright = Mth.clamp(tag.getInt("nlBright"), 0, 2);
        lit = tag.getBoolean("nlLit");
        if (!tag.contains("nlPower") && tag.contains("nlMode") && (level == null || !level.isClientSide)) oldMode = Mth.clamp(tag.getInt("nlMode"), 0, 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        CompoundTag t = super.getUpdateTag(r);
        t.putInt("nlPower", power);
        t.putInt("nlBright", bright);
        t.putBoolean("nlLit", lit);
        return t;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider r) { super.onDataPacket(net, pkt, r); }
}
