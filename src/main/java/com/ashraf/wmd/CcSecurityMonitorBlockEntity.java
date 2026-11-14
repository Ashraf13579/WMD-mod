package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcSecurityMonitorBlockEntity extends CcModuleBlockEntity implements RadarLinked {

    public static final int LIVE = 0, STATIC = 1, LOST = 2, NONE = 3;
    static final double REACH = 8.0;

    private int frequency = 1;
    private List<CctvNet.Cam> cams = List.of();
    private long camsAt = Long.MIN_VALUE / 2;
    private int alert = -1;
    private long alertUntil;
    private final int[] screen = { NONE, NONE, NONE, NONE };
    private int screenAlert = -1;
    private boolean joined;

    public CcSecurityMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CC_SECURITY_MONITOR_BE.get(), pos, state, Balance.CC_SECURITY_FE);
    }

    @Override public CcModule kind() { return CcModule.SECURITY_MONITOR; }

    @Override public int getFrequency() { return frequency; }

    @Override
    public void setFrequency(int f) {
        frequency = Mth.clamp(f, 1, 9999);
        camsAt = Long.MIN_VALUE / 2;
        setChanged();
        sync();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!joined) { CctvNet.join(this); joined = true; }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CctvNet.leave(this);
        joined = false;
    }

    Vec3 centre() { return Vec3.atCenterOf(worldPosition); }

    List<CctvNet.Cam> cams() {
        if (!(level instanceof ServerLevel sl)) return List.of();
        long now = sl.getGameTime();
        if (now - camsAt >= 20 || now < camsAt) { camsAt = now; cams = CctvNet.reachable(sl, centre(), getOwner(), frequency); }
        return cams;
    }

    int state(ServerLevel sl, BlockPos cam) {
        if (!isOnline() || EmpField.jammed(sl, cam)) return LOST;
        return CctvNet.jammed(sl, cam, getOwner()) ? STATIC : LIVE;
    }

    int alert() { return level != null && level.getGameTime() < alertUntil ? alert : -1; }

    void alert(Vec3 at, long now) {
        List<CctvNet.Cam> l = cams();
        int best = -1;
        double bd = Balance.CCTV_SEE * 1.5;
        for (int i = 0; i < l.size(); i++) {
            double d = Vec3.atCenterOf(l.get(i).pos()).distanceTo(at);
            if (d < bd) { bd = d; best = i; }
        }
        if (best < 0) return;
        boolean fresh = now >= alertUntil || alert != best;
        alert = best;
        alertUntil = now + 200;
        if (fresh && level != null) level.playSound(null, worldPosition, WmdSounds.CC_ALERT.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.2F);
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if (!(level instanceof ServerLevel sl) || (now + (worldPosition.asLong() & 15L)) % 20 != 0) return;
        List<CctvNet.Cam> l = cams();
        int a = alert();

        List<Integer> order = new ArrayList<>();
        if (a >= 0 && a < l.size()) order.add(a);
        for (int i = 0; i < l.size() && order.size() < 4; i++) if (i != a) order.add(i);
        boolean changed = (a >= 0 ? 0 : -1) != screenAlert;
        screenAlert = a >= 0 ? 0 : -1;
        for (int k = 0; k < 4; k++) {
            int s = k < order.size() ? state(sl, l.get(order.get(k)).pos()) : NONE;
            if (s != screen[k]) { screen[k] = s; changed = true; }
        }
        if (changed) { setChanged(); sync(); }
    }

    public int screenState(int k) { return screen[k]; }
    public int screenAlert() { return screenAlert; }

    boolean mayWork(ServerPlayer p) {
        return !isRemoved() && p.level() == level && Reach.within(p, worldPosition, REACH) && Access.mayUse(this, p);
    }

    void open(ServerPlayer p) {
        if (!mayWork(p)) return;
        List<CctvPayloads.CamInfo> l = new ArrayList<>();
        ServerLevel sl = p.serverLevel();
        for (CctvNet.Cam c : cams()) l.add(new CctvPayloads.CamInfo(c.pos().asLong(), c.yaw(), c.covert(), state(sl, c.pos())));
        PacketDistributor.sendToPlayer(p, new CctvPayloads.Open(worldPosition, frequency, isOnline(), l, alert()));
    }

    CctvNet.Cam cam(long pos) {
        for (CctvNet.Cam c : cams()) if (c.pos().asLong() == pos) return c;
        return null;
    }

    static CcSecurityMonitorBlockEntity at(ServerPlayer p, BlockPos pos) {
        return p.level().isLoaded(pos) && p.level().getBlockEntity(pos) instanceof CcSecurityMonitorBlockEntity m && m.mayWork(p) ? m : null;
    }

    static boolean feedAllowed(ServerPlayer p, int cx, int cz) {
        for (CcSecurityMonitorBlockEntity m : CctvNet.near(p.level(), CcSecurityMonitorBlockEntity.class, p.position(), REACH + 2.0)) {
            if (!m.mayWork(p) || !m.isOnline()) continue;
            for (CctvNet.Cam c : m.cams()) if (Math.hypot(c.pos().getX() - cx, c.pos().getZ() - cz) <= Balance.CCTV_VIEW) return true;
        }
        return false;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", kind().fe() * 20), Component.translatable("gui.wmd.info.cctv.monitor", frequency, Balance.CCTV_DIRECT));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("freq", frequency);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("freq")) frequency = Math.max(1, tag.getInt("freq"));
        if (tag.contains("scr")) { int[] s = tag.getIntArray("scr"); for (int k = 0; k < 4 && k < s.length; k++) screen[k] = s[k]; }
        if (tag.contains("scrA")) screenAlert = tag.getInt("scrA");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("freq", frequency);
        tag.putIntArray("scr", screen.clone());
        tag.putInt("scrA", screenAlert);
        return tag;
    }
}
