package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class ChartTableBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    static final float CAR_X = -3.0F, CAR_MIN = -2.2F, CAR_MAX = 24.2F, HEAD_MIN = -5.2F, HEAD_MAX = 4.4F;
    static final float GX0 = -6.0F, GX1 = 22.0F, GZ0 = -6.0F, GZ1 = 6.0F, GY = 13.6F;

    static final float X_SIGN = -1.0F, RY = 1.0F;

    static final float PLOT_SPEED = 10.0F;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private boolean lit, night, onCraft;
    private int pageX0, pageZ0;
    private double shipX = Double.NaN, shipZ = Double.NaN;
    private float hdg;
    private int[] glass = new int[0], route = new int[0], track = new int[0];
    private int why;

    private int glassVer, sentGlassVer = -1;
    private long lastSync = Long.MIN_VALUE / 2, glassAt = Long.MIN_VALUE / 2, helmAt = Long.MIN_VALUE / 2;
    private BlockPos helmPos;

    private float carX, headZ, ruleDeg;
    private double lastT = Double.NaN;

    public ChartTableBlockEntity(BlockPos pos, BlockState state) {
        super(ChartTableBlocks.CHART_TABLE_BE.get(), pos, state, Balance.CHART_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    Direction front() { return getBlockState().getValue(MachineBlock.FACING); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return part.equals(worldPosition) && side == front().getOpposite() ? energy() : null; }

    HelmStationBlockEntity helm(ServerLevel sl) {
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship == null || getOwner() == null || !Crafts.ownedBy(sl, ship, getOwner())) return null;
        if (helmPos != null && sl.getBlockEntity(helmPos) instanceof HelmStationBlockEntity h && !h.isRemoved() && ship.equals(SableBridge.shipIdAt(sl, helmPos))) return h;
        long now = sl.getGameTime();
        if (now - helmAt < 100) return null;
        helmAt = now;
        helmPos = null;
        HelmStationBlockEntity best = null;
        int seen = 0;
        for (BlockPos p : SableBridge.blockEntitiesOn(sl, ship)) {
            if (++seen > 4096) break;
            if (!(sl.getBlockEntity(p) instanceof HelmStationBlockEntity h)) continue;
            if (best == null || h.pilot.on()) best = h;
            if (h.pilot.on()) break;
        }
        if (best != null) helmPos = best.getBlockPos();
        return best;
    }

    void open(ServerPlayer sp) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return;
        HelmStationBlockEntity h = helm(sl);
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (lit) playOnce(sl, WmdSounds.CT_WAKE.get(), Balance.SND_CLICK, 1.15F);
        PacketDistributor.sendToPlayer(sp, new SeaPilotPayloads.State(worldPosition, true, SeaChart.state(sl, worldPosition, ship, h, h == null ? null : h.apCtx(sl), "")));
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        tick(sl, true);
        return lit;
    }

    @Override
    protected void idle() {
        if (!(level instanceof ServerLevel sl)) return;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        tick(sl, ship != null && AirGate.on() && ShipPower.draw(sl, ship, Balance.CHART_FE));
    }

    private void tick(ServerLevel sl, boolean power) {
        long now = sl.getGameTime();
        boolean want = power && AirGate.on();
        boolean changed = want != lit;
        if (want && !lit) playOnce(sl, WmdSounds.CT_WAKE.get(), Balance.SND_CLICK, 1.0F);
        lit = want;
        if (now - lastSync < Balance.CHART_SYNC_TICKS && !changed) return;
        lastSync = now;
        boolean n = sl.isNight();
        if (n != night) { night = n; changed = true; if (lit) playOnce(sl, WmdSounds.CT_LAMP.get(), Balance.SND_CLICK, 1.0F); }
        if (!lit) { if (changed) sync(); return; }
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        onCraft = ship != null;
        Vec3 at = SableBridge.toWorld(sl, Vec3.atCenterOf(worldPosition));
        HelmStationBlockEntity h = ship == null ? null : helm(sl);
        SeaPilot.Ctx c = h == null ? null : h.apCtx(sl);
        if (c != null) { at = c.at(); hdg = (float) c.heading(); }
        shipX = at.x; shipZ = at.z;

        double mx = pageX0 + Balance.CHART_PAGE_W / 2.0, mz = pageZ0 + Balance.CHART_PAGE_D / 2.0;
        boolean turn = glass.length == 0 || Math.abs(shipX - mx) > Balance.CHART_PAGE_EDGE * Balance.CHART_PAGE_W || Math.abs(shipZ - mz) > Balance.CHART_PAGE_EDGE * Balance.CHART_PAGE_D;
        if (turn || now - glassAt > 1200) {
            if (turn) {
                if (glass.length > 0) playOnce(sl, WmdSounds.CT_PAGE.get(), Balance.SND_CLICK, 1.0F);
                pageX0 = Mth.floor(shipX) - Balance.CHART_PAGE_W / 2;
                pageZ0 = Mth.floor(shipZ) - Balance.CHART_PAGE_D / 2;
            }
            int cell = Balance.CHART_PAGE_W / Balance.CHART_GLASS_W;
            glass = SeaChart.area(sl, pageX0, pageZ0, cell, Balance.CHART_GLASS_W, Balance.CHART_GLASS_D, shipX, shipZ);
            glassVer++;
            glassAt = now;
        }
        if (h != null) {
            SeaPilot p = h.pilot;
            int[] r = new int[p.route.size() * 2];
            for (int i = 0; i < p.route.size(); i++) { r[2 * i] = Mth.floor(p.route.get(i).x()); r[2 * i + 1] = Mth.floor(p.route.get(i).z()); }
            route = r;
            int[] t = p.trackXZ();
            track = t.length > 128 ? java.util.Arrays.copyOfRange(t, t.length - 128, t.length) : t;
            int w = p.reasonShown(now);
            why = w;
        } else { route = new int[0]; why = SeaPilot.R_NONE; }
        sync();
    }

    boolean lit() { return lit; }
    boolean night() { return night; }
    int pageX0() { return pageX0; }
    int pageZ0() { return pageZ0; }
    int[] glass() { return glass; }
    int[] route() { return route; }
    int[] track() { return track; }
    double shipX() { return shipX; }
    double shipZ() { return shipZ; }
    float heading() { return hdg; }

    boolean letGo() { return why != SeaPilot.R_NONE; }

    float[] plotTarget() {
        if (!lit || Double.isNaN(shipX)) return new float[] { 0.0F, 0.0F };
        double u = Mth.clamp((shipX - pageX0) / Balance.CHART_PAGE_W, 0.0, 1.0), v = Mth.clamp((shipZ - pageZ0) / Balance.CHART_PAGE_D, 0.0, 1.0);
        float mx = (float) (GX1 - u * (GX1 - GX0)), mz = (float) (GZ1 - v * (GZ1 - GZ0));
        return new float[] { Mth.clamp(mx - CAR_X, CAR_MIN, CAR_MAX), Mth.clamp(mz, HEAD_MIN, HEAD_MAX) };
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float[] t = plotTarget();
        float wantRule = lit ? -hdg : 0.0F;
        if (!Double.isNaN(lastT) && tt > lastT) {
            float step = (float) Math.min(5.0, tt - lastT) / 20.0F * PLOT_SPEED;
            carX += Mth.clamp(t[0] - carX, -step, step);
            headZ += Mth.clamp(t[1] - headZ, -step, step);
            ruleDeg += Mth.clamp(Mth.wrapDegrees(wantRule - ruleDeg), -step * 6.0F, step * 6.0F);
        } else if (Double.isNaN(lastT)) { carX = t[0]; headZ = t[1]; ruleDeg = wantRule; }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        final float cx = X_SIGN * carX, hz = headZ, rr = (float) Math.toRadians(ruleDeg) * RY;
        model.getBone("carriage").ifPresent(b -> b.setPosX(cx));
        model.getBone("head").ifPresent(b -> b.setPosZ(hz));
        model.getBone("rule").ifPresent(b -> b.setRotY(rr));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!(lit && night)));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected boolean soundAlive() { return false; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override
    protected String detail() { return (lit ? "lit" : "dark") + (onCraft ? ", aboard" : ", ashore") + ", page " + pageX0 + " " + pageZ0 + ", route " + route.length / 2; }

    @Override
    public Component status() {
        String k = lit ? (onCraft ? "message.wmd.chart_table.status_aboard" : "message.wmd.chart_table.status_ashore") : "message.wmd.chart_table.status_dark";
        return Component.translatable("block.wmd.chart_table").append(": ").append(Component.translatable(k));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        if (tag.contains("ctLit")) {
            lit = tag.getBoolean("ctLit"); night = tag.getBoolean("ctNight"); onCraft = tag.getBoolean("ctCraft");
            pageX0 = tag.getInt("ctPx"); pageZ0 = tag.getInt("ctPz");
            shipX = tag.contains("ctSx") ? tag.getDouble("ctSx") : Double.NaN; shipZ = tag.contains("ctSz") ? tag.getDouble("ctSz") : Double.NaN;
            hdg = tag.getFloat("ctHdg");
            route = tag.getIntArray("ctRt"); track = tag.getIntArray("ctTr");
            why = tag.getInt("ctWhy");
        }
        if (tag.contains("ctGlass")) {
            int[] g = tag.getIntArray("ctGlass");
            glass = g.length == Balance.CHART_GLASS_W * Balance.CHART_GLASS_D ? g : new int[0];
        }
    }

    private void putState(CompoundTag tag) {
        tag.putBoolean("ctLit", lit); tag.putBoolean("ctNight", night); tag.putBoolean("ctCraft", onCraft);
        tag.putInt("ctPx", pageX0); tag.putInt("ctPz", pageZ0);
        if (!Double.isNaN(shipX)) { tag.putDouble("ctSx", shipX); tag.putDouble("ctSz", shipZ); }
        tag.putFloat("ctHdg", hdg);
        tag.put("ctRt", new IntArrayTag(route)); tag.put("ctTr", new IntArrayTag(track));
        tag.putInt("ctWhy", why);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        putState(tag);
        tag.put("ctGlass", new IntArrayTag(glass));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, (be, registries) -> ((ChartTableBlockEntity) be).deltaTag(registries));
    }

    private CompoundTag deltaTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        putState(tag);
        if (sentGlassVer != glassVer) { tag.put("ctGlass", new IntArrayTag(glass)); sentGlassVer = glassVer; }
        return tag;
    }
}
