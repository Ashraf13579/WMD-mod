package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class JetEngineBlockEntity extends AirEngineBlockEntity implements CopycatState.Host {

    public static final int OFF = 0, STARTING = 1, RUNNING = 2;
    static final int NP = 12;

    static final float PET_IDLE = -2.0F, PET_CRUISE = 8.0F, PET_BURNER = -11.0F;

    static final float PAD_CRUISE = 5.0F, PAD_BURNER = -9.0F, VFLAP_CRUISE = 4.0F, VFLAP_BURNER = -10.0F;

    private final CopycatState copycat = new CopycatState();

    private int variant;
    private int state = OFF, stopTicks;
    private float n1, noz;

    private double fuelAcc, share;
    private int lastStep = STOP, sentState = -1;
    private float sentN1 = -9, sentNoz = 999;
    private boolean spoolCue, sentAb;
    private int[] fuelShown = { 0, 0 };

    private int cVariant, cState = OFF;
    private float tN1, cN1, tNoz, cNoz, pNoz, cPet, pPet, cAbK, pAbK;
    private double fan, pFan;
    private boolean cSpoolCue, cDownCue, cAbWas;
    private int cFuel, cFuelCap;

    public JetEngineBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.JET_ENGINE_BE.get(), pos, state, 0, 1, 0);
    }

    public JetEngineBlock.Kind kind() {
        return getBlockState().getBlock() instanceof JetEngineBlock b ? b.kind() : JetEngineBlock.Kind.SINGLE;
    }

    private int variantNow() { return level != null && level.isClientSide ? cVariant : variant; }

    public String meshName() { return kind().mesh(variantNow()); }

    public boolean bare() { return (variantNow() & 1) == 1; }

    int layout() { return kind() == JetEngineBlock.Kind.TWIN ? variantNow() / 2 : 0; }

    void toggleStyle() { variant ^= 1; syncNow(); }

    void nextLayout() {
        int n = kind().meshes.length / 2;
        variant = ((variant / 2 + 1) % n) * 2 + (variant & 1);
        syncNow();
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { syncNow(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private double dryThrust() {
        return switch (kind()) { case SINGLE -> Balance.JET1_THRUST; case TWIN -> Balance.JET2_THRUST; default -> Balance.JETS_THRUST; };
    }

    private double abThrust() {
        return switch (kind()) { case SINGLE -> Balance.JET1_AB; case TWIN -> Balance.JET2_AB; default -> Balance.JETS_THRUST; };
    }

    private boolean abLit() { return kind().afterburner() && afterburner && state == RUNNING; }

    private double vecMax() {
        return layout() == 1 ? Balance.JET_VEC_MAX : layout() == 2 ? Balance.JET_VFLAP_MAX : 0.0;
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 0.5, -0.45 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, 2.9 }; }
    @Override protected double airflow() { return Balance.JET_AIRFLOW; }

    @Override
    protected double airShare(double thrustSign) {
        if (ship == null || !dry) return 0.0;
        double along = Math.signum(thrustSign) * vFwd, a = airflow();
        double r = a <= 0.0 ? 0.0 : Math.max(0.0, along / a);
        double flow = Mth.clamp(1.0 - Math.pow(r, Balance.JET_FLOW_EXP), 0.0, 1.0);

        return Math.pow(pressure, Balance.JET_AIR_EXP) * flow * speedCapShare(along, vShip) * bellyShare(along);
    }
    @Override protected boolean contrails() { return true; }
    @Override protected double hitSize() { return kind() == JetEngineBlock.Kind.SINGLE ? 1.0 : kind() == JetEngineBlock.Kind.TWIN ? 1.5 : 2.0; }
    @Override protected boolean hotExhaust() { return true; }
    @Override protected double[] exhaustLocal() { return new double[] { 0.0, 0.5, kind() == JetEngineBlock.Kind.STEALTH ? 3.3 : 3.5 }; }
    @Override protected boolean afterburnerClient() { return kind().afterburner() && cAb && runningClient(); }
    @Override protected boolean runningClient() { return cState == RUNNING || (cState != OFF && cN1 > 0.15F); }

    @Override
    protected double heatSignature() {
        double h = abLit() ? Balance.HEAT_SIG_HOT : Balance.HEAT_SIG_TURBOFAN;
        return kind() == JetEngineBlock.Kind.STEALTH ? h * Balance.JETS_HEAT : h;
    }

    @Override protected double noiseDb() { return kind() == JetEngineBlock.Kind.STEALTH ? Balance.AIR_NOISE_DB_FAN : Balance.AIR_NOISE_DB_TURBOFAN; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            report(sl, share, state == RUNNING, 0.0, noz);
            int[] f = ShipPower.fuelAboard(sl, ship);
            if (f[0] != fuelShown[0] || f[1] != fuelShown[1]) { fuelShown = f; syncNow(); }
        }
        boolean wantRun = throttle > 1.0E-3 && damageStage() < 3;
        stopTicks = step == STOP && redstone == 0 ? stopTicks + 1 : 0;
        float idle = (float) Balance.TF_IDLE_N1;
        switch (state) {
            case OFF -> {
                n1 = approach(n1, 0.0F, 1.0F / Balance.JET_SPOOL_DOWN);
                if (wantRun && dry && ship != null && ShipPower.fuelAboard(sl, ship)[0] > 0) state = STARTING;
            }
            case STARTING -> {
                boolean starter = ShipPower.draw(sl, ship, Balance.JET_STARTER_FE);
                if (!starter || !dry || damageStage() >= 3) { state = OFF; break; }
                n1 = Math.min(idle, n1 + idle / Balance.JET_START_TICKS);
                if (n1 >= idle - 1.0E-4) state = RUNNING;
            }
            default -> {
                float target = idle + (1.0F - idle) * (float) Math.max(0.0, throttle);
                n1 = approach(n1, target, (1.0F - idle) / (target > n1 ? Balance.JET_SPOOL_UP : Balance.JET_SPOOL_DOWN));
                double thr = Math.max(0.0, (n1 - idle) / (1.0 - idle));
                double full = Balance.JET_FUEL_PER_KT * dryThrust() / 1000.0;
                fuelAcc += full * (Balance.JET_FUEL_IDLE_SHARE + (1.0 - Balance.JET_FUEL_IDLE_SHARE) * thr) * (abLit() ? Balance.JET_AB_FUEL : 1.0);
                int burn = (int) fuelAcc;
                if (burn > 0) {
                    fuelAcc -= burn;
                    if (ShipPower.drawFuel(sl, ship, burn) < burn) state = OFF;
                }
                if (!dry || damageStage() >= 3) state = OFF;
                if (stopTicks > Balance.TF_SHUTDOWN) state = OFF;
            }
        }

        double[] s = state == RUNNING && vecMax() > 0.0 ? steerLocal(false) : new double[] { 0.0, 0.0 };
        noz = approach(noz, (float) (s[1] * vecMax()), (float) Balance.JET_NOZZLE_RATE * 0.05F);
        double thrShare = state == RUNNING ? Math.max(0.0, (n1 - idle) / (1.0 - idle)) : 0.0;
        double thrust = thrShare * (abLit() ? abThrust() : dryThrust()) * airShare(1.0);
        share = thrust / dryThrust();
        double tp = Math.toRadians(noz);
        setForce(thrust * Math.cos(tp), 0.0, thrust * Math.sin(tp));
        if (Math.abs(step - lastStep) >= 2 && state == RUNNING && step > lastStep) spoolCue = true;
        lastStep = step;
        syncMaybe(sl.getGameTime());
        return state != OFF || n1 > 1.0E-3;
    }

    private void syncMaybe(long now) {
        boolean ab = abLit();
        boolean big = state != sentState || spoolCue || ab != sentAb;
        boolean small = Math.abs(n1 - sentN1) > 0.04F || Math.abs(noz - sentNoz) > 1.0F;
        if (!big && !(small && now % 4 == 0)) return;
        sentState = state; sentN1 = n1; sentNoz = noz; sentAb = ab;
        syncNow();
        spoolCue = false;
    }

    @Override
    public Component status() {
        return Component.translatable("block.wmd." + kind().id).append(": ")
                .append(Component.translatable("gui.wmd.jet.state" + state, Math.round(n1 * 100.0F)))
                .append(" | ").append(Component.translatable("gui.wmd.jet.fuel", fuelShown[0], fuelShown[1]))
                .append(" | ").append(Component.translatable("variant.wmd." + meshName()));
    }

    @Override
    protected void easeTick(Level lv) {
        pNoz = cNoz; pFan = fan; pPet = cPet; pAbK = cAbK;
        float idle = (float) Balance.TF_IDLE_N1;
        cN1 = approach(cN1, tN1, (tN1 > cN1 ? (cState == STARTING ? idle / Balance.JET_START_TICKS : (1.0F - idle) / Balance.JET_SPOOL_UP) : (1.0F - idle) / Balance.JET_SPOOL_DOWN) * 1.3F);
        cNoz = approach(cNoz, tNoz, (float) Balance.JET_NOZZLE_RATE / 20.0F * 1.3F);
        boolean abOn = afterburnerClient();
        cAbK = approach(cAbK, abOn ? 1.0F : 0.0F, 1.0F / 6.0F);
        float sched = Mth.clamp((cN1 - idle) / (0.7F - idle), 0.0F, 1.0F);
        float dryPet = cState == OFF && cN1 < 0.05F ? PET_IDLE : PET_IDLE + (PET_CRUISE - PET_IDLE) * sched;
        cPet = approach(cPet, dryPet + (PET_BURNER - dryPet) * cAbK, 1.5F);
        fan += Math.PI * 2.0 * 3.5 / 20.0 * cN1;
        if (fan > Math.PI * 2000.0) { fan -= Math.PI * 2000.0; pFan -= Math.PI * 2000.0; }
        if (cSpoolCue) { cSpoolCue = false; playOnce(lv, WmdSounds.JET_SPOOL_UP.get(), Balance.SND_RELEASE, 1.0F); }
        if (cDownCue) { cDownCue = false; playOnce(lv, WmdSounds.JET_SHUTDOWN.get(), Balance.SND_RELEASE, 1.0F); }
        if (abOn && !cAbWas) playOnce(lv, WmdSounds.JET_AB_LIGHT.get(), Balance.SND_RELEASE, 1.0F);
        cAbWas = abOn;
        if (cState != OFF && cN1 > 0.15F)
            for (double[] e : exhausts()) {
                double[] c = worldPoint(e[0], e[1], e[2]);
                VeilBridge.glow(0x4A45_0000_0000_0000L ^ worldPosition.asLong() ^ Double.doubleToLongBits(e[0]), c[0], c[1], c[2], 1.0F, abOn ? 0.45F : 0.55F, abOn ? 0.1F : 0.2F,
                        abOn ? 6.0F : 2.5F + 3.0F * cN1, abOn ? 2.0F : 0.5F + 0.7F * cN1, lv.getGameTime());
            }
    }

    float abShown(float pt) { return pAbK + (cAbK - pAbK) * pt; }

    float n1Shown() { return cN1; }

    double[][] nozzlePlot(double[] e) {
        net.minecraft.world.phys.Vec3 c = plotPoint(e[0], e[1], e[2]), a = plotPoint(e[0] + e[3], e[1], e[2] + e[4]);
        net.minecraft.world.phys.Vec3 d = a.subtract(c).normalize();
        return new double[][] { { c.x, c.y, c.z }, { d.x, d.y, d.z } };
    }

    double[][] exhausts() {
        return switch (kind()) {
            case SINGLE -> new double[][] { { 0.0, 0.5, 3.45, 0.0, 1.0 } };
            case STEALTH -> new double[][] { { -1.2, 0.4, 3.3, 0.0, 1.0 }, { 1.2, 0.4, 3.3, 0.0, 1.0 } };
            default -> layout() == 2 ? new double[][] { { -0.8, 0.55, 3.0, -0.5, 0.87 }, { 0.8, 0.55, 3.0, 0.5, 0.87 } }
                    : new double[][] { { -1.0, 0.5, 3.45, 0.0, 1.0 }, { 1.0, 0.5, 3.45, 0.0, 1.0 } };
        };
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float fa = (float) Mth.lerp(partialTick, pFan, fan) * RZ;
        model.getBone("fan").ifPresent(b -> b.setRotZ(fa));
        model.getBone("fan_l").ifPresent(b -> b.setRotZ(fa));
        model.getBone("fan_r").ifPresent(b -> b.setRotZ(fa * 1.03F));
        float pet = (float) Math.toRadians(Mth.lerp(partialTick, pPet, cPet)) * RX;
        for (String sfx : new String[] { "", "_l", "_r" })
            for (int k = 0; k < NP; k++) {
                model.getBone("pet" + sfx + k).ifPresent(b -> b.setRotX(pet));
                model.getBone("seal" + sfx + k).ifPresent(b -> b.setRotX(pet));
            }
        float ab = Mth.lerp(partialTick, pAbK, cAbK), vec = Mth.lerp(partialTick, pNoz, cNoz);
        boolean lit = cState == RUNNING || (cState != OFF && cN1 > 0.15F);
        float cruise = lit ? 1.0F : 0.0F;

        float base = PAD_CRUISE * cruise + (PAD_BURNER - PAD_CRUISE * cruise) * ab;
        float up = (float) Math.toRadians(base + vec) * RX, dn = (float) Math.toRadians(-base + vec) * RX;
        for (String sfx : new String[] { "_l", "_r" }) {
            model.getBone("flap_up" + sfx).ifPresent(b -> b.setRotX(up));
            model.getBone("flap_dn" + sfx).ifPresent(b -> b.setRotX(dn));
        }

        float vf = (float) Math.toRadians(VFLAP_CRUISE * cruise + (VFLAP_BURNER - VFLAP_CRUISE * cruise) * ab + vec) * RX;
        model.getBone("flap_l").ifPresent(b -> b.setRotX(vf));
        model.getBone("flap_r").ifPresent(b -> b.setRotX(vf));
        model.getBone("glow").ifPresent(b -> b.setHidden(!lit));
        model.getBone("ab").ifPresent(b -> b.setHidden(true));
        boolean blink = cState == STARTING && ((long) (tt / 5.0)) % 2 == 0;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!(cState == RUNNING || blink)));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cState == OFF || cN1 < 0.15F) return;
        var r = lv.random;
        boolean abOn = afterburnerClient();
        for (double[] e : exhausts()) {
            double[] c = worldPoint(e[0], e[1], e[2]), a = worldPoint(e[0] + e[3], e[1], e[2] + e[4]);
            double vx = a[0] - c[0], vy = a[1] - c[1], vz = a[2] - c[2];

            if (abOn) AirFx.haze(lv, new double[] { c[0] + vx * Balance.JET_PLUME_LEN_AB, c[1] + vy * Balance.JET_PLUME_LEN_AB, c[2] + vz * Balance.JET_PLUME_LEN_AB });
        }
    }

    @Override protected void runStarted(Level lv) { playOnce(lv, WmdSounds.JET_START.get(), Balance.SND_RELEASE, 1.0F); }

    @Override
    protected SoundEvent runSound() {
        if (afterburnerClient()) return WmdSounds.JET_AB_LOOP.get();
        return kind() == JetEngineBlock.Kind.SINGLE ? WmdSounds.JET1_LOOP.get() : kind() == JetEngineBlock.Kind.TWIN ? WmdSounds.JET2_LOOP.get() : WmdSounds.JETS_LOOP.get();
    }
    @Override protected float soundVolume() { return (kind() == JetEngineBlock.Kind.TWIN ? Balance.SND_GIANT : kind() == JetEngineBlock.Kind.SINGLE ? Balance.SND_HEAVY : Balance.SND_MACHINE) * (0.35F + 0.65F * cN1) * (1.0F + 0.3F * cAbK); }
    @Override protected float soundPitch() { return afterburnerClient() ? 1.0F : 0.8F + 0.3F * cN1; }
    @Override protected boolean soundAlive() { return cState != OFF || cN1 > 0.05F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }

    double flightThrust() { return dryThrust(); }
    double flightBurn() { return Balance.JET_FUEL_PER_KT * dryThrust() / 1000.0; }

    @Override protected String detail() { return kind().id + " " + meshName() + ", state " + state + ", N1 " + Math.round(n1 * 100) + "%, nozzle " + Math.round(noz) + (dry ? "" : " (intake wet)"); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        tag.putInt("jeStop", stopTicks);
        copycat.save(tag, registries);
    }

    private void put(CompoundTag tag) {
        tag.putInt("jeVar", variant);
        tag.putInt("jeState", state);
        tag.putFloat("jeN1", n1);
        tag.putFloat("jeNoz", noz);
        tag.putBoolean("jeAb", abLit());
        tag.putInt("jeFuel", fuelShown[0]);
        tag.putInt("jeFuelCap", fuelShown[1]);
        if (spoolCue) tag.putBoolean("jeCue", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        int v = Math.max(0, tag.getInt("jeVar"));
        int st = Mth.clamp(tag.getInt("jeState"), OFF, RUNNING);
        float n = Mth.clamp(tag.getFloat("jeN1"), 0.0F, 1.0F);
        float lim = (float) Balance.JET_VEC_MAX;
        float nz = Mth.clamp(tag.getFloat("jeNoz"), -lim, lim);
        if (client) {
            if (cState == RUNNING && st == OFF) cDownCue = true;
            cVariant = v; cState = st; tN1 = n; tNoz = nz;
            cAb = tag.getBoolean("jeAb");
            cFuel = tag.getInt("jeFuel"); cFuelCap = tag.getInt("jeFuelCap");
            if (tag.getBoolean("jeCue")) cSpoolCue = true;
            return;
        }
        variant = v; state = st == STARTING ? OFF : st; n1 = n; noz = nz; stopTicks = tag.getInt("jeStop");
        cVariant = v; cState = state; tN1 = cN1 = n; tNoz = cNoz = pNoz = nz;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        copycat.putSync(tag);
        return tag;
    }
}
