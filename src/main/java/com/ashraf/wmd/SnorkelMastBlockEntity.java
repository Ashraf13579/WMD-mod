package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SnorkelMastBlockEntity extends ProcessUnitBlockEntity implements NavalPanel, CcAnimated, CopycatHull.Owner {

    public static final int ROW_RAISE = 0, ROW_AUTO = 1;
    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);

    static final float HATCH_OPEN = -115.0F, VALVE_LIFT = 1.0F, EXFLAP_OPEN = -40.0F, VALVE_TURN = -90.0F;

    private static final double FLAP_Y = 39.6 / 16.0, FLAP_Z = 2.95 / 16.0;

    private boolean up, auto = true, blocked;
    private int ext, lastRedstone;

    private boolean headWet, exhaust, submerged;
    private int slam, deepTicks, noWantTicks;
    private final CopycatHull hull = new CopycatHull();

    private float cExt, pExt, cValve, pValve, cFlap, pFlap;
    private boolean cMoving;
    private int lastSlam;
    private Vec3 lastHead;

    public SnorkelMastBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SNORKEL_MAST_BE.get(), pos, state, Balance.SN_FE); }

    static float ss(float a, float b, float t) {
        float x = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    static float hatchDeg(float t) { return HATCH_OPEN * ss(0.0F, 0.14F, t); }
    static float stage1(float t) { return Balance.SN_STAGE1_PX * ss(0.12F, 0.5F, t); }
    static float stage2(float t) { return Balance.SN_STAGE2_PX * ss(0.42F, 0.74F, t); }
    static float valveDeg(float t) { return VALVE_TURN * ss(0.74F, 0.82F, t); }

    private float t() { return Mth.clamp((level != null && level.isClientSide ? cExt : ext) / (float) Balance.SN_RAISE_TICKS, 0.0F, 1.0F); }
    public boolean fullyUp() { return up && t() >= 0.82F; }
    public boolean breathing() { return fullyUp() && !headWet && slam <= 0; }

    private Vec3 headAt(float t) {
        double rise = (stage1(t) + stage2(t)) / (Balance.SN_STAGE1_PX + Balance.SN_STAGE2_PX) * Balance.SN_HEAD_UP;
        double[] p = worldPoint(0.0, 3.0 + rise - 0.15, 0.0);
        return new Vec3(p[0], p[1], p[2]);
    }

    public boolean mastClear() { return HullPlug.open(this, facing(), Machines.mount(getBlockState()), new int[] { 0, 3, 0 }, new int[] { 0, 4, 0 }, new int[] { 0, 5, 0 }); }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        boolean moved = tick(sl, true);
        return moved || up;
    }

    @Override
    protected void idle() {
        if (getLevel() instanceof ServerLevel sl) tick(sl, false);
    }

    private boolean tick(ServerLevel sl, boolean powered) {
        long now = sl.getGameTime();
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (now % 20 == (worldPosition.asLong() & 15)) {
            SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
            submerged = info != null && NavalLayer.submerged(sl, info);
        }

        int rs = sl.getBestNeighborSignal(worldPosition);
        if (rs > 0 && lastRedstone == 0) setUp(sl, true);
        else if (rs == 0 && lastRedstone > 0) setUp(sl, false);
        lastRedstone = rs;
        if (auto && rs == 0 && ship != null) autoOrders(sl, ship);
        if (up && now % 10 == 0 && !mastClear()) { up = false; blocked = true; sync(); }

        boolean moved = false;
        if (powered) {
            if (up && ext < Balance.SN_RAISE_TICKS) { ext++; moved = true; }
            else if (!up && ext > 0) { ext--; moved = true; }
        }
        if (moved) {
            if (up && ext == 1) playOnce(sl, WmdSounds.SNORKEL_HATCH.get(), Balance.SND_MECH, 1.1F);
            if (up && ext == 10 || !up && ext == Balance.SN_RAISE_TICKS - 1) playOnce(sl, WmdSounds.SNORKEL_HOIST.get(), Balance.SND_MECH, up ? 1.0F : 0.9F);
            if (!up && ext == 0) playOnce(sl, WmdSounds.SNORKEL_HATCH.get(), Balance.SND_MECH, 0.85F);
            if (ext == 0 || ext == Balance.SN_RAISE_TICKS || ext % 15 == 0) sync();
        }

        boolean wet = fullyUp() && NavalLayer.depthBelowSurface(sl, headAt(t())) > 0.05;
        if (wet != headWet) { headWet = wet; sync(); }
        if (slam > 0 && --slam == 0) sync();
        if (fullyUp() && !headWet && slam == 0 && NavalLayer.depthBelowSurface(sl, headAt(t()).subtract(0.0, Balance.SN_AWASH, 0.0)) > 0.0
                && sl.random.nextDouble() < Balance.SN_SLAM_CHANCE / 20.0) {
            slam = Balance.SN_SLAM_TICKS;
            playOnce(sl, WmdSounds.SNORKEL_SLAM.get(), Balance.SND_MECH, 0.9F + sl.random.nextFloat() * 0.2F);
            sync();
        }

        if (ship != null) {
            if (breathing()) Snorkels.breathe(sl, ship);
            if (fullyUp() && !headWet && now % Balance.MAST_BEAT == 0) Masts.beat(sl, ship, Masts.SNORKEL);
            if (!fullyUp() && ext == Balance.SN_RAISE_TICKS - 1) Masts.drop(sl, ship, Masts.SNORKEL);
        }
        boolean ex = ship != null && breathing() && Snorkels.exhausting(sl, ship);
        if (ex != exhaust) { exhaust = ex; sync(); }
        return moved;
    }

    private void autoOrders(ServerLevel sl, UUID ship) {
        boolean want = Snorkels.wanted(sl, ship);
        if (!up) {
            if (want && submerged && NavalLayer.depthBelowSurface(sl, headAt(1.0F)) <= 0.05 && mastClear()) setUp(sl, true);
            return;
        }
        noWantTicks = want ? 0 : noWantTicks + 1;
        deepTicks = fullyUp() && headWet ? deepTicks + 1 : 0;
        if (noWantTicks > Balance.SN_AUTO_DOWN_TICKS || deepTicks > Balance.SN_DEEP_TICKS) setUp(sl, false);
    }

    private void setUp(ServerLevel sl, boolean u) {
        if (u == up) return;
        if (u && !mastClear()) { blocked = true; sync(); return; }
        blocked = false;
        up = u;
        noWantTicks = 0;
        deepTicks = 0;
        if (!u) { UUID ship = SableBridge.shipIdAt(sl, worldPosition); if (ship != null) Masts.drop(sl, ship, Masts.SNORKEL); }
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public CopycatHull hull() { return hull; }
    @Override public void hullChanged() { sync(); }
    public BlockState copyShown() { return hull.shown(); }

    @Override
    public List<net.minecraft.world.item.ItemStack> takeUpgrades() {
        List<net.minecraft.world.item.ItemStack> l = super.takeUpgrades();
        net.minecraft.world.item.ItemStack s = hull.take();
        if (!s.isEmpty()) l.add(s);
        return l;
    }

    @Override public String panelTitle() { return "gui.wmd.snorkel.panel"; }

    @Override
    public List<Component> panelLines() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable(fullyUp() ? (headWet ? "gui.wmd.snorkel.too_deep" : slam > 0 ? "gui.wmd.snorkel.slammed" : "gui.wmd.snorkel.breathing")
                : up ? "gui.wmd.snorkel.raising" : ext > 0 ? "gui.wmd.snorkel.lowering" : "gui.wmd.snorkel.down"));
        l.add(Component.translatable("gui.wmd.periscope.ext", Math.round(t() * 100.0F)));
        l.add(Component.translatable(auto ? "gui.wmd.snorkel.auto_on" : "gui.wmd.snorkel.auto_off"));
        if (exhaust) l.add(Component.translatable("gui.wmd.snorkel.exhaust"));
        if (blocked) l.add(Component.translatable("gui.wmd.periscope.blocked"));
        return l;
    }

    @Override
    public List<Component> panelRows() {
        return List.of(Component.translatable(up ? "gui.wmd.periscope.row_lower" : "gui.wmd.periscope.row_raise"),
                Component.translatable(auto ? "gui.wmd.snorkel.row_auto_off" : "gui.wmd.snorkel.row_auto_on"));
    }

    @Override public int panelChipKind() { return breathing() ? WmdUi.K_WARN : up ? WmdUi.K_INFO : WmdUi.K_IDLE; }
    @Override public Component panelChip() { return Component.translatable(breathing() ? "gui.wmd.snorkel.chip_up" : "gui.wmd.periscope.chip_down"); }

    @Override
    public void setMode(int row) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        if (row == ROW_RAISE) { auto = false; setUp(sl, !up); }
        else if (row == ROW_AUTO) { auto = !auto; sync(); }
        else return;
        playOnce(sl, WmdSounds.HATCH_OPEN.get(), Balance.SND_CLICK, 1.2F);
    }

    static void clientTickSnorkel(Level lv, BlockPos pos, BlockState state, SnorkelMastBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
        be.easeTick(lv);
    }

    private void easeTick(Level lv) {
        pExt = cExt; pValve = cValve; pFlap = cFlap;

        float target = up ? Balance.SN_RAISE_TICKS : 0.0F;
        if (Math.abs(cExt - ext) > 12.0F) cExt = ext;
        if (cMoving || Math.abs(cExt - ext) > 0.5F) cExt = ScrewPropellerBlockEntity.approach(cExt, target, 1.0F);
        boolean open = fullyUp() && slam <= 0 && !headWet;
        cValve = ScrewPropellerBlockEntity.approach(cValve, open ? 1.0F : 0.0F, open ? 1.0F / 6.0F : 1.0F / 1.6F);
        cFlap = ScrewPropellerBlockEntity.approach(cFlap, exhaust ? 1.0F : 0.0F, 1.0F / 6.0F);
        if (slam > 0 && lastSlam <= 0) { Vec3 h = headAt(t()); for (int k = 0; k < 6; k++) lv.addParticle(ParticleTypes.SPLASH, h.x + (lv.random.nextDouble() - 0.5) * 0.4, h.y, h.z + (lv.random.nextDouble() - 0.5) * 0.4, 0.0, 0.1, 0.0); }
        lastSlam = slam;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float t = Mth.clamp(Mth.lerp(partialTick, pExt, cExt) / Balance.SN_RAISE_TICKS, 0.0F, 1.0F);
        float rx = ScrewPropellerBlockEntity.RX;
        model.getBone("housing_hatch").ifPresent(b -> b.setRotX((float) Math.toRadians(hatchDeg(t)) * rx));
        model.getBone("mast").ifPresent(b -> b.setPosY(stage1(t)));
        model.getBone("mast2").ifPresent(b -> b.setPosY(stage2(t)));
        model.getBone("valve").ifPresent(b -> b.setRotX((float) Math.toRadians(valveDeg(t)) * rx));
        float v = Mth.lerp(partialTick, pValve, cValve), f = Mth.lerp(partialTick, pFlap, cFlap);
        model.getBone("headvalve").ifPresent(b -> b.setPosY(VALVE_LIFT * v));
        model.getBone("exflap").ifPresent(b -> b.setRotX((float) Math.toRadians(EXFLAP_OPEN * f) * rx));

        boolean moving = t > 0.0F && t < 1.0F;
        model.getBone("lamp").ifPresent(b -> b.setHidden(moving ? ((long) tt / 8) % 2 == 0 : !breathing()));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (!fullyUp()) { lastHead = null; return; }
        var r = lv.random;
        Vec3 h = headAt(t());

        if (exhaust && lv.getGameTime() % 3 == 0) {
            float tt = t();
            double fy = FLAP_Y + (stage1(tt) + stage2(tt)) / 16.0;
            double[] q = worldPoint(0.0, fy, FLAP_Z + 0.05);
            lv.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, q[0], q[1], q[2], (r.nextDouble() - 0.5) * 0.02, 0.05 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.02);
        }

        Vec3 prev = lastHead;
        lastHead = h;
        if (prev == null) return;
        double sp = Math.hypot(h.x - prev.x, h.z - prev.z);
        if (sp < Balance.SN_WAKE_SPEED) return;
        int top = NavalLayer.waterTop(lv, Mth.floor(h.x), Mth.floor(h.z));
        if (top == NavalLayer.NO_WATER || top + 1.0 > h.y) return;
        double y = top + 1.02, bx = (prev.x - h.x) / sp, bz = (prev.z - h.z) / sp;
        for (int k = 0; k < 3; k++)
            lv.addParticle(ParticleTypes.SPLASH, h.x + (r.nextDouble() - 0.5) * 0.3, y, h.z + (r.nextDouble() - 0.5) * 0.3, bx * 0.06, 0.1, bz * 0.06);
        lv.addParticle(ParticleTypes.CLOUD, h.x + bx * 0.4, y + 0.05, h.z + bz * 0.4, bx * 0.02, 0.01, bz * 0.02);
        if (r.nextInt(2) == 0) lv.addParticle(ParticleTypes.BUBBLE_POP, h.x + bx * 0.8, y, h.z + bz * 0.8, 0.0, 0.0, 0.0);
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override public List<Component> guiInfo() { return List.of(Component.translatable("gui.wmd.info.use", Balance.SN_FE * 20), Component.translatable("gui.wmd.info.snorkel_mast")); }
    @Override protected SoundEvent runSound() { return WmdSounds.SNORKEL_AIR.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected boolean soundAlive() { return exhaust && fullyUp(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return (up ? "up" : "down") + " " + Math.round(t() * 100) + "%" + (auto ? " auto" : "") + (headWet ? " (head under water)" : breathing() ? " (breathing)" : ""); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) { UUID ship = SableBridge.shipIdAt(sl, worldPosition); if (ship != null) Masts.drop(sl, ship, Masts.SNORKEL); }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        hull.save(tag, registries);
        tag.putInt("snRs", lastRedstone);
    }

    private void put(CompoundTag tag) {
        tag.putBoolean("snUp", up);
        tag.putBoolean("snAuto", auto);
        tag.putInt("snExt", ext);
        tag.putBoolean("snWet", headWet);
        tag.putBoolean("snBlk", blocked);
        tag.putBoolean("snEx", exhaust);
        tag.putInt("snSlam", slam);
        tag.putBoolean("snMov", up ? ext < Balance.SN_RAISE_TICKS : ext > 0);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        up = tag.getBoolean("snUp");
        auto = !tag.contains("snAuto") || tag.getBoolean("snAuto");
        ext = Mth.clamp(tag.getInt("snExt"), 0, Balance.SN_RAISE_TICKS);
        headWet = tag.getBoolean("snWet");
        blocked = tag.getBoolean("snBlk");
        exhaust = tag.getBoolean("snEx");
        slam = tag.getInt("snSlam");
        cMoving = tag.getBoolean("snMov");
        boolean client = level != null && level.isClientSide;
        hull.load(tag, registries, client);
        if (!client) { lastRedstone = tag.getInt("snRs"); cExt = pExt = ext; }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        hull.putSync(tag);
        return tag;
    }
}
