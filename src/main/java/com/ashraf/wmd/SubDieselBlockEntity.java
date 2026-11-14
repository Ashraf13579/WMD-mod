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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SubDieselBlockEntity extends ProcessUnitBlockEntity implements NavalPanel, CcAnimated {

    public static final int ROW_RUN = 0, ROW_AUTO = 1;
    public static final int S_STOPPED = 0, S_CRANK = 1, S_RUN = 2, S_STALL = 3;
    public static final int M_OFF = 0, M_ON = 1, M_AUTO = 2;
    private static final PortMap PORT_MAP = new PortMap(0, 1, 3).fluid(1, 0, 0, Direction.NORTH, "fuel", false).power(1, 0, 2, Direction.EAST, true);

    static final float RK_LIFT = 8.0F, VALVE_DROP = 0.6F, FLY_REST = 35.0F, FLY_RUN = 62.0F;

    private static final double[] OUTLET = { -1.2 / 16.0, 2.05, 28.0 / 16.0 };

    private final FluidTank fuel = new FluidTank(Balance.SD_TANK_MB, fs -> fs.getFluid() == WmdFluids.DIESEL.source().get());
    private final IFluidHandler fuelIn = fillOnly(fuel);
    private final GenBuffer out = new GenBuffer(Balance.SD_BUFFER_FE, Balance.SD_OUT_PER_TICK, this::setChanged);

    private int mode = M_AUTO, state = S_STOPPED, credit;
    private float temp;

    private int phaseTicks, coughAt;
    private boolean air = true, submerged, wantRun, producing, viaSnorkel;
    private float rpm;
    private UUID ship;
    private int sentState = -1, sentMode = -1;
    private boolean sentAir = true, sentProd, sentVia, sentWant;
    private float sentTemp = -1;

    private int cState, cMode = M_AUTO;
    private boolean cAir = true, cProd, cVia, cWant;
    private float cTemp, cRpm, cLever, cFlyK, cOil, cTempShown;
    private double crank, pCrank;
    private float pLever, pFly, pOil, pTempShown, cRpmShown, pRpmShown;
    private int lastFire = -1;

    public SubDieselBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SUB_DIESEL_BE.get(), pos, state, 0); }

    @Override public void empDrain() { out.stored = 0; setChanged(); }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (now % 20 == (worldPosition.asLong() & 15) || ship == null && now % 100 == 0) {
            ship = SableBridge.shipIdAt(sl, worldPosition);
            SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
            submerged = info != null && NavalLayer.submerged(sl, info);
        }

        viaSnorkel = ship != null && Snorkels.air(sl, ship);
        air = !submerged || viaSnorkel;
        float fill = out.stored / (float) Balance.SD_BUFFER_FE;
        if (mode == M_ON) wantRun = true;
        else if (mode == M_OFF) wantRun = false;
        else wantRun = state == S_STOPPED ? fill < Balance.SD_AUTO_LOW : fill < Balance.SD_AUTO_HIGH;
        boolean fuelOk = credit >= Balance.SD_FE_PER_TICK || fuel.getFluidAmount() >= 1;
        if (wantRun && submerged && ship != null) Snorkels.want(sl, ship);
        phaseTicks++;
        producing = false;
        switch (state) {
            case S_STOPPED -> {
                reason = !fuelOk ? 4 : 0;
                if (wantRun && fuelOk && air && out.hasRoom(Balance.SD_FE_PER_TICK)) go(sl, S_CRANK);
            }
            case S_CRANK -> {
                if (!air || !wantRun) { cough(sl); go(sl, S_STOPPED); }
                else if (phaseTicks >= Balance.SD_CRANK_TICKS) { go(sl, S_RUN); playOnce(sl, WmdSounds.DIESEL_FIRE.get(), Balance.SND_MECH, 1.0F); }
            }
            case S_RUN -> {
                if (!air) { go(sl, S_STALL); cough(sl); }
                else if (!wantRun || !fuelOk) { go(sl, S_STOPPED); playOnce(sl, WmdSounds.DIESEL_STOP.get(), Balance.SND_MECH, 1.0F); }
                else burn();
            }
            case S_STALL -> {
                if (air) go(sl, S_RUN);
                else if (phaseTicks >= Balance.SD_STALL_TICKS) { go(sl, S_STOPPED); playOnce(sl, WmdSounds.DIESEL_STOP.get(), Balance.SND_MECH, 0.8F); }
                else if (phaseTicks >= coughAt) cough(sl);
            }
            default -> state = S_STOPPED;
        }
        boolean turning = state != S_STOPPED;
        float target = state == S_CRANK ? 0.25F : state == S_RUN ? (producing ? 1.0F : 0.5F) : state == S_STALL ? 0.5F * (1.0F - phaseTicks / (float) Balance.SD_STALL_TICKS) : 0.0F;
        rpm += (target - rpm) * 0.08F;
        temp = Mth.clamp(temp + (state == S_RUN ? 1.0F / 1200.0F : -1.0F / 2400.0F), 0.0F, 1.0F);

        if (state == S_RUN || state == S_STALL) {
            double[] w = worldPoint(0.5, 1.0, 1.0);
            double db = Balance.SD_DB;
            if (ship != null) db = Math.max(1.0, db - Anechoic.noiseCut(sl, ship));
            NoiseNet.emit(sl, this, new Vec3(w[0], w[1], w[2]), db, NoiseNet.Kind.ENGINE, null, ship);
            if (ship != null && state == S_RUN) Snorkels.exhaust(sl, ship);
        } else if (now % 20 == 0) NoiseNet.silence(sl, this);
        syncMaybe(now);
        return turning;
    }

    private void burn() {
        if (!out.hasRoom(Balance.SD_FE_PER_TICK)) { reason = 3; return; }
        if (credit < Balance.SD_FE_PER_TICK) {
            if (fuel.getFluidAmount() < 1) { reason = 4; return; }
            credit += Balance.SD_FE_PER_MB;
            fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
        }
        reason = 0;
        credit -= Balance.SD_FE_PER_TICK;
        out.stored += Balance.SD_FE_PER_TICK;
        flowAcc += 1;
        producing = true;
    }

    private void go(ServerLevel sl, int s) {
        state = s;
        phaseTicks = 0;
        coughAt = 0;
        if (s == S_CRANK) playOnce(sl, WmdSounds.DIESEL_START.get(), Balance.SND_MECH, 1.0F);
        syncNow();
    }

    private void cough(ServerLevel sl) {
        playOnce(sl, WmdSounds.DIESEL_COUGH.get(), Balance.SND_MECH, 0.9F + sl.random.nextFloat() * 0.2F);
        coughAt = phaseTicks + 12 + sl.random.nextInt(9);
        rpm *= 0.8F;
    }

    private void syncMaybe(long now) {
        boolean big = state != sentState || mode != sentMode || air != sentAir || producing != sentProd || viaSnorkel != sentVia || wantRun != sentWant;
        if (!big && !(Math.abs(temp - sentTemp) > 0.05F && now % 20 == 0)) return;
        sentState = state; sentMode = mode; sentAir = air; sentProd = producing; sentVia = viaSnorkel; sentWant = wantRun; sentTemp = temp;
        syncNow();
    }

    private void syncNow() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public String panelTitle() { return "gui.wmd.sub_diesel.panel"; }

    @Override
    public List<Component> panelLines() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("gui.wmd.sub_diesel.state" + cState));
        l.add(Component.translatable(cMode == M_AUTO ? "gui.wmd.sub_diesel.mode_auto" : cMode == M_ON ? "gui.wmd.sub_diesel.mode_on" : "gui.wmd.sub_diesel.mode_off"));
        l.add(Component.translatable(!cAir ? "gui.wmd.sub_diesel.no_air" : cVia ? "gui.wmd.sub_diesel.air_snorkel" : "gui.wmd.sub_diesel.air_open"));
        l.add(Component.translatable("gui.wmd.sub_diesel.output", cProd ? Balance.SD_FE_PER_TICK * 20 : 0));
        return l;
    }

    @Override
    public List<Component> panelRows() {
        boolean on = cMode == M_ON || cMode == M_AUTO && cState != S_STOPPED;
        return List.of(Component.translatable(on ? "gui.wmd.sub_diesel.row_stop" : "gui.wmd.sub_diesel.row_start"),
                Component.translatable(cMode == M_AUTO ? "gui.wmd.sub_diesel.row_auto_off" : "gui.wmd.sub_diesel.row_auto_on"));
    }

    @Override public int panelChipKind() { return cState == S_RUN ? WmdUi.K_GOOD : cState == S_STALL || !cAir && cWant ? WmdUi.K_BAD : cState == S_CRANK ? WmdUi.K_INFO : WmdUi.K_IDLE; }
    @Override public Component panelChip() { return Component.translatable("gui.wmd.sub_diesel.chip" + cState); }

    @Override
    public void setMode(int row) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        if (row == ROW_RUN) mode = (mode == M_ON || mode == M_AUTO && state != S_STOPPED) ? M_OFF : M_ON;
        else if (row == ROW_AUTO) mode = mode == M_AUTO ? M_OFF : M_AUTO;
        else return;
        playOnce(sl, WmdSounds.HATCH_OPEN.get(), Balance.SND_CLICK, 1.3F);
        syncNow();
    }

    static float valveLift(int i, double phase) {
        double off = new double[] { 0.0, 540.0, 180.0, 360.0 }[i / 2];
        double a = ((phase + off) % 720.0 + 720.0) % 720.0;
        double lo = i % 2 == 0 ? 140.0 : 340.0, hi = i % 2 == 0 ? 380.0 : 580.0;
        return a < lo || a > hi ? 0.0F : (float) Math.sin(Math.PI * (a - lo) / (hi - lo));
    }

    static void clientTickDiesel(Level lv, BlockPos pos, BlockState state, SubDieselBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
        be.easeTick(lv);
    }

    private void easeTick(Level lv) {
        pCrank = crank; pLever = cLever; pFly = cFlyK; pOil = cOil; pTempShown = cTempShown; pRpmShown = cRpmShown;
        float target = cState == S_CRANK ? 0.25F : cState == S_RUN ? (cProd ? 1.0F : 0.5F) : cState == S_STALL ? 0.2F : 0.0F;
        cRpm += (target - cRpm) * (target > cRpm ? 0.06F : 0.03F);
        if (cRpm < 0.003F && target == 0.0F) cRpm = 0.0F;
        crank += cRpm * 6.0 * 360.0 / 20.0;
        if (crank > 720.0 * 1000.0) { crank -= 720.0 * 1000.0; pCrank -= 720.0 * 1000.0; }
        cLever = ScrewPropellerBlockEntity.approach(cLever, cState == S_CRANK ? 1.0F : 0.0F, cState == S_CRANK ? 1.0F / 6.0F : 1.0F / 8.0F);
        cFlyK += (Math.min(1.0F, cRpm) - cFlyK) * 0.1F;
        cOil = ScrewPropellerBlockEntity.approach(cOil, cState == S_RUN || cState == S_STALL ? 1.0F : cState == S_CRANK ? 0.3F : 0.0F, 1.0F / 6.0F);
        cTempShown += (cTemp - cTempShown) * 0.05F;
        cRpmShown += (cRpm - cRpmShown) * 0.3F;

        int fire = (int) Math.floor(crank / 180.0);
        if (fire != lastFire && (cState == S_RUN || cState == S_STALL)) {
            boolean first = lastFire < 0;
            lastFire = fire;
            if (!cVia && lv.random.nextInt(2) == 0) {
                double[] w = worldPoint(OUTLET[0], OUTLET[1], OUTLET[2]);
                lv.addAlwaysVisibleParticle(first ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, true, w[0], w[1], w[2],
                        (lv.random.nextDouble() - 0.5) * 0.02, 0.05 + lv.random.nextDouble() * 0.03, (lv.random.nextDouble() - 0.5) * 0.02);
            }
        } else if (cState != S_RUN && cState != S_STALL) lastFire = -1;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float rx = ScrewPropellerBlockEntity.RX, ry = ScrewPropellerBlockEntity.RY, rz = ScrewPropellerBlockEntity.RZ;
        double ph = Mth.lerp(partialTick, pCrank, crank);
        float cr = (float) Math.toRadians(ph % 360.0) * rz;
        model.getBone("crank").ifPresent(b -> b.setRotZ(cr));
        model.getBone("genfan").ifPresent(b -> b.setRotZ(cr));
        model.getBone("gov").ifPresent(b -> b.setRotY((float) Math.toRadians((ph * 0.5) % 360.0) * ry));
        double cyc = ph % 720.0;
        for (int i = 0; i < 8; i++) {
            float l = valveLift(i, cyc);
            model.getBone("rock" + i).ifPresent(b -> b.setRotZ((float) Math.toRadians(RK_LIFT * l) * rz));
            model.getBone("valve" + i).ifPresent(b -> b.setPosY(-VALVE_DROP * l));
        }
        float fk = Mth.lerp(partialTick, pFly, cFlyK), fly = (FLY_RUN - FLY_REST) * fk;
        model.getBone("fly0").ifPresent(b -> b.setRotZ((float) Math.toRadians(fly) * rz));
        model.getBone("fly1").ifPresent(b -> b.setRotZ((float) Math.toRadians(-fly) * rz));
        model.getBone("govcol").ifPresent(b -> b.setPosY(fk));

        boolean rough = cState == S_CRANK || cState == S_STALL;
        double w = Math.toRadians(cyc) * 4.0, amp = cRpm < 0.01F ? 0.0 : rough ? 0.8 : 0.3;
        model.getBone("engine").ifPresent(b -> { b.setRotX((float) Math.toRadians(amp * Math.sin(w)) * rx); b.setRotZ((float) Math.toRadians(-amp * Math.sin(w + 1.0)) * rz); });
        boolean firing = cState == S_RUN || cState == S_STALL;
        float flap = firing ? -34.0F - 8.0F * (float) Math.max(0.0, Math.sin(w)) : 0.0F;
        model.getBone("exflap").ifPresent(b -> b.setRotX((float) Math.toRadians(flap) * rx));
        float rs = Mth.lerp(partialTick, pRpmShown, cRpmShown), oil = Mth.lerp(partialTick, pOil, cOil), tmp = Mth.lerp(partialTick, pTempShown, cTempShown);
        model.getBone("g_rpm").ifPresent(b -> b.setRotZ((float) Math.toRadians(-180.0F * Math.min(1.0F, rs)) * rz));
        model.getBone("g_oil").ifPresent(b -> b.setRotZ((float) Math.toRadians(-120.0F * oil) * rz));
        model.getBone("g_temp").ifPresent(b -> b.setRotZ((float) Math.toRadians(-20.0F - 75.0F * tmp) * rz));
        float lv = Mth.lerp(partialTick, pLever, cLever);
        model.getBone("startlever").ifPresent(b -> b.setRotZ((float) Math.toRadians(-70.0F * lv) * rz));
        boolean blink = cWant && !cAir && cState == S_STOPPED;
        model.getBone("lamp").ifPresent(b -> b.setHidden(blink ? ((long) tt / 10) % 2 == 0 : cState == S_STOPPED));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.DIESEL_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY * (0.5F + 0.5F * Math.min(1.0F, cRpm * 1.5F)); }
    @Override protected float soundPitch() { return 0.7F + 0.5F * cRpm; }
    @Override protected boolean soundAlive() { return cState == S_RUN || cState == S_STALL || cRpm > 0.3F; }

    @Override protected List<FluidTank> tanks() { return List.of(fuel); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.DIESEL.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("fuel"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected float levelFraction() { return fuel.getFluidAmount() / (float) Balance.SD_TANK_MB; }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.gen_output", String.format("%,d", Balance.SD_FE_PER_TICK * 20)),
                Component.translatable("gui.wmd.info.sub_diesel", String.format("%,d", Balance.SD_FE_PER_MB)));
    }

    @Override
    protected String detail() { return "state " + state + ", diesel " + mb(fuel) + ", stored " + out.stored + " FE" + (air ? "" : " (no air)"); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 2, Direction.EAST) ? out : null; }

    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 0, Direction.NORTH) ? fuelIn : null; }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) NoiseNet.silence(sl, this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        tag.putInt("sdCredit", credit);
        tag.putInt("sdStored", out.stored);
    }

    private void put(CompoundTag tag) {
        tag.putInt("sdState", state);
        tag.putInt("sdMode", mode);
        tag.putFloat("sdTemp", temp);
        tag.putBoolean("sdAir", air);
        tag.putBoolean("sdProd", producing);
        tag.putBoolean("sdVia", viaSnorkel);
        tag.putBoolean("sdWant", wantRun);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int st = Mth.clamp(tag.getInt("sdState"), S_STOPPED, S_STALL), md = tag.contains("sdMode") ? Mth.clamp(tag.getInt("sdMode"), M_OFF, M_AUTO) : M_AUTO;
        cState = st; cMode = md; cTemp = Mth.clamp(tag.getFloat("sdTemp"), 0.0F, 1.0F);
        cAir = !tag.contains("sdAir") || tag.getBoolean("sdAir"); cProd = tag.getBoolean("sdProd"); cVia = tag.getBoolean("sdVia"); cWant = tag.getBoolean("sdWant");
        if (level != null && level.isClientSide) return;
        state = st == S_CRANK ? S_STOPPED : st;
        mode = md;
        temp = cTemp;
        credit = tag.getInt("sdCredit");
        if (tag.contains("sdStored")) out.stored = Mth.clamp(tag.getInt("sdStored"), 0, Balance.SD_BUFFER_FE);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}
