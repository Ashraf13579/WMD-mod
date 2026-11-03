package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcMapTableBlockEntity extends CcModuleBlockEntity {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 2;
    public static final int VIEW_HOME = 0, VIEW_RECON = 1;
    public static final int ASSET_TERMINAL = 0, ASSET_DEFENCE = 1, ASSET_RADAR = 2, ASSET_STATION = 3, ASSET_CC = 4;

    public static final int ASSET_TRIPOD = 5, ASSET_LASER = 6, ASSET_ENEMY_LASER = 7;

    public static final int ASSET_ORBIT_OWN = 8, ASSET_ORBIT_SEEN = 9;
    public static final int MAX_ASSETS = 64, ASSET_TICKS = 60;

    private int view = VIEW_HOME;

    private int[] assets = new int[0];
    private List<CommandCenter.Warning> warnings = List.of();

    private boolean bPowered, bSats, bFound;
    private int bDraw, bInstalled, bOnline, bBlocked;

    public CcMapTableBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CC_MAP_TABLE_BE.get(), pos, state, Balance.CC_TABLE_FE_PER_TICK);
    }

    @Override public CcModule kind() { return CcModule.MAP_TABLE; }

    public boolean buildingFound() { return bFound; }
    public boolean buildingPowered() { return bPowered; }
    public boolean satsControlled() { return bSats; }
    public int buildingDraw() { return bDraw; }
    public boolean installed(CcModule k) { return (bInstalled >> k.ordinal() & 1) != 0; }
    public boolean moduleOnline(CcModule k) { return (bOnline >> k.ordinal() & 1) != 0; }
    public boolean moduleBlocked(CcModule k) { return (bBlocked >> k.ordinal() & 1) != 0; }
    public int view() { return view; }
    public int[] assets() { return assets; }
    public List<CommandCenter.Warning> warnings() { return warnings; }

    public CcTarget designated() { CcBuildingBlockEntity b = building(); return b != null ? b.designated() : null; }

    public int defcon() { CcBuildingBlockEntity b = building(); return b != null ? b.defcon() : CcDefcon.PEACE; }

    void setView(int v) {
        v = v == VIEW_RECON ? VIEW_RECON : VIEW_HOME;
        if (v == view) return;
        view = v;
        setChanged();
        sync();
    }

    private static int[] collectAssets(ServerLevel level, UUID owner) {
        List<int[]> l = new ArrayList<>();
        if (owner != null) {
            for (LaunchTerminalBlockEntity t : CcLaunch.terminalsIn(level, owner, MAX_ASSETS))
                if (t.shownBuild() != null) l.add(new int[] { t.getBlockPos().getX(), t.getBlockPos().getZ(), ASSET_TERMINAL });
            for (InterceptorSite s : CommandCenter.sitesOf(owner, MAX_ASSETS))
                if (s instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getLevel() == level) l.add(new int[] { s.getBlockPos().getX(), s.getBlockPos().getZ(), ASSET_DEFENCE });
            for (BlockPos p : TrackNet.radarsOf(level, owner, level.getGameTime())) l.add(new int[] { p.getX(), p.getZ(), ASSET_RADAR });
            for (BlockPos p : GroundStationBlockEntity.stationsOf(level, owner)) l.add(new int[] { p.getX(), p.getZ(), ASSET_STATION });
            for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner))
                if (b.getLevel() == level) { BlockPos c = b.hallCentre(); l.add(new int[] { c.getX(), c.getZ(), ASSET_CC }); }
            for (LaserTripodBlockEntity t : LaserTripodBlockEntity.tripodsOf(level, owner)) l.add(new int[] { t.getBlockPos().getX(), t.getBlockPos().getZ(), ASSET_TRIPOD });
            for (LaserSpots.Spot s : LaserSpots.all())
                if (s.owner().equals(owner) && s.dim().equals(level.dimension()) && LaserSpots.get(level, s.id()) != null)
                    l.add(new int[] { (int) Math.floor(s.pos().x), (int) Math.floor(s.pos().z), ASSET_LASER });
            for (int[] e : LaserReceiverBlockEntity.enemySpots(owner, level.getGameTime())) l.add(new int[] { e[0], e[1], ASSET_ENEMY_LASER });

            net.minecraft.server.MinecraftServer server = level.getServer();
            int mine = Orbit.slotOf(server, owner);
            boolean sees = mine >= 0 && SensorArray.working(server, mine);
            for (int slot : Station.Data.get(server).slots.keySet()) {
                if (Station.core(server, slot) == null || slot != mine && !sees) continue;
                Orbit.Ground g = Orbit.ground(server, slot);
                if (g.dim() != level.dimension()) continue;
                l.add(new int[] { g.x(), g.z(), slot == mine ? ASSET_ORBIT_OWN : ASSET_ORBIT_SEEN });
            }
        }
        int n = Math.min(MAX_ASSETS, l.size());
        int[] out = new int[n * 3];
        for (int i = 0; i < n; i++) System.arraycopy(l.get(i), 0, out, i * 3, 3);
        return out;
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if ((now + 11) % ASSET_TICKS == 0 && level instanceof ServerLevel sl) {
            int[] a = isOnline() ? collectAssets(sl, getOwner()) : new int[0];
            List<CommandCenter.Warning> w = isOnline() ? CommandCenter.warnings(getOwner()) : List.of();
            if (!java.util.Arrays.equals(a, assets) || !w.equals(warnings)) {
                assets = a;
                warnings = List.copyOf(w);
                sync();
            }
        }
        if ((now + 11) % 20 != 0) return;
        CcBuildingBlockEntity b = building();
        boolean f = b != null, p = f && b.isActive(), s = f && b.satsControlled();
        int d = f ? b.draw() : 0, i = 0, o = f ? b.onlineMask() : 0, k = f ? b.blockedMask() : 0;
        if (f) for (CcModule m : CcModule.values()) if (b.installed(m)) i |= 1 << m.ordinal();
        if (f != bFound || p != bPowered || s != bSats || d != bDraw || i != bInstalled || o != bOnline || k != bBlocked) {
            bFound = f; bPowered = p; bSats = s; bDraw = d; bInstalled = i; bOnline = o; bBlocked = k;
            setChanged();
            sync();
        }
    }

    @Override public int guiFeUse() { return bDraw > 0 ? bDraw : kind().fe(); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.cc_table"));
    }

    private void writeSummary(CompoundTag tag) {
        tag.putBoolean("bFound", bFound);
        tag.putBoolean("bPowered", bPowered);
        tag.putBoolean("bSats", bSats);
        tag.putInt("bDraw", bDraw);
        tag.putInt("bInstalled", bInstalled);
        tag.putInt("bOnline", bOnline);
        tag.putInt("bBlocked", bBlocked);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeSummary(tag);
        tag.putInt("view", view);
        tag.putIntArray("assets", assets);
        tag.put("warnings", CommandCenter.saveList(warnings));
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("view", view);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bFound = tag.getBoolean("bFound");
        bPowered = tag.getBoolean("bPowered");
        bSats = tag.getBoolean("bSats");
        bDraw = tag.getInt("bDraw");
        bInstalled = tag.getInt("bInstalled");
        bOnline = tag.getInt("bOnline");
        bBlocked = tag.getInt("bBlocked");
        view = tag.getInt("view") == VIEW_RECON ? VIEW_RECON : VIEW_HOME;
        if (tag.contains("assets")) { int[] a = tag.getIntArray("assets"); assets = a.length % 3 == 0 ? a : new int[0]; }
        if (tag.contains("warnings")) warnings = List.copyOf(CommandCenter.loadList(tag.getList("warnings", Tag.TAG_COMPOUND)));
    }
}
