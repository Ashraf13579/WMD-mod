package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class ShellPressBlockEntity extends ItemProcessBlockEntity implements Owned, ModeMachine, CcAnimated {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 3, Z0 = 0, Z1 = 4;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(-2, 0, 2, Direction.WEST, false).item(2, 0, 2, Direction.EAST, true).power(0, 0, 4, Direction.SOUTH, false);
    private static final TagKey<Item> STEEL_PLATES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/steel"));

    public record Recipe(String out, int outN, String casing, int casingN, String filler, int fillerN, String fuze, int fuzeN) {
        public Item outItem() { return item(out); }
        public ItemStack outStack() { return new ItemStack(outItem(), outN); }
        public ItemStack casingStack() { return new ItemStack(item(casing), casingN); }
        public ItemStack fillerStack() { return new ItemStack(item(filler), fillerN); }
        public ItemStack fuzeStack() { return fuzeN <= 0 ? ItemStack.EMPTY : new ItemStack(item(fuze), fuzeN); }
    }

    static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }

    private static final String PLATE = "wmd:steel_plate", HE = "wmd:he_charge", CHAR = "minecraft:charcoal", MG = "wmd:magnesium_ingot", RS = "minecraft:redstone",
            CIRC = "wmd:basic_circuit";

    public static final List<Recipe> RECIPES = List.of(
            new Recipe("wmd:shell_mortar_he", 4, PLATE, 1, HE, 1, RS, 1),
            new Recipe("wmd:shell_mortar_smoke", 4, PLATE, 1, CHAR, 2, RS, 1),
            new Recipe("wmd:shell_mortar_illum", 4, PLATE, 1, MG, 1, RS, 1),
            new Recipe("wmd:shell_mortar_cluster", 4, PLATE, 1, HE, 2, CIRC, 1),
            new Recipe("wmd:shell_howitzer_he", 2, PLATE, 2, HE, 2, RS, 1),
            new Recipe("wmd:shell_howitzer_smoke", 2, PLATE, 2, CHAR, 4, RS, 1),
            new Recipe("wmd:shell_howitzer_illum", 2, PLATE, 2, MG, 2, RS, 1),
            new Recipe("wmd:shell_howitzer_cluster", 2, PLATE, 2, HE, 4, CIRC, 1),
            new Recipe("wmd:shell_super_he", 1, PLATE, 8, HE, 8, CIRC, 2),

            new Recipe("wmd:super_charge", 2, "minecraft:paper", 4, "wmd:alkylate_canister", Math.max(1, Alkylate.superChargeCanisters(2)), RS, 1),
            new Recipe("wmd:propellant_charge", 4, "minecraft:paper", 1, "wmd:nitrate_dust", 2, RS, 0),

            new Recipe("wmd:shell_mortar_guided", 2, PLATE, 1, HE, 1, "wmd:guidance_t1", 1),
            new Recipe("wmd:shell_howitzer_guided", 2, PLATE, 2, HE, 2, "wmd:guidance_t1", 1),
            new Recipe("wmd:mlrs_pod", 1, "wmd:aluminium_casing", 1, HE, 6, "wmd:propellant_charge", 6),
            new Recipe("wmd:mlrs_pod_guided", 1, "wmd:mlrs_pod", 1, HE, 1, "wmd:guidance_t1", 2),

            new Recipe("wmd:shell_howitzer_laser", 1, PLATE, 2, HE, 2, "wmd:laser_seeker", 1),
            new Recipe("wmd:shell_howitzer_canister", 2, PLATE, 2, "minecraft:iron_nugget", 16, RS, 1),
            new Recipe("wmd:shell_howitzer_sensor", 1, PLATE, 2, HE, 2, "wmd:heat_seeker", 1),
            new Recipe("wmd:shell_howitzer_pierce", 2, "wmd:tungsten_ingot", 2, HE, 2, RS, 1),
            new Recipe("wmd:shell_howitzer_incendiary", 2, PLATE, 2, MG, 3, RS, 1),
            new Recipe("wmd:shell_howitzer_flak", 2, PLATE, 2, HE, 2, CIRC, 1),
            new Recipe("wmd:shell_howitzer_camera", 1, PLATE, 2, "wmd:cam_pod", 1, CIRC, 1),
            new Recipe("wmd:shell_howitzer_jamming", 2, PLATE, 2, "wmd:aluminium_plate", 4, RS, 1),
            new Recipe("wmd:shell_howitzer_rocket", 2, PLATE, 2, HE, 2, "wmd:alkylate_canister", 1),
            new Recipe("wmd:shell_howitzer_leaflet", 4, PLATE, 2, "minecraft:paper", 16, RS, 1),
            new Recipe("wmd:shell_super_nuke", 1, PLATE, 8, "wmd:warhead_nuke", 1, CIRC, 2));

    static boolean anyRecipe(ItemStack s, int slot) {
        for (Recipe r : RECIPES) {
            String id = slot == 0 ? r.casing() : slot == 1 ? r.filler() : r.fuze();
            int n = slot == 0 ? r.casingN() : slot == 1 ? r.fillerN() : r.fuzeN();
            if (n > 0 && (id.equals(PLATE) ? isSteel(s) : s.is(item(id)))) return true;
        }
        return false;
    }

    static boolean isLeaflet(Recipe r) { return r.out().equals("wmd:shell_howitzer_leaflet"); }

    private final OwnerData ownerData = new OwnerData();

    private final Slot casing = new Slot(64, false, s -> isSteel(s) || s.is(item("minecraft:paper")) || Mlrs.pressCasing(s) || anyRecipe(s, 0));
    private final Slot filler = new Slot(64, false, s -> s.is(item(HE)) || Alkylate.isCanister(s) || s.is(item(CHAR)) || s.is(item(MG)) || s.is(item("wmd:nitrate_dust")) || anyRecipe(s, 1));
    private final Slot fuze = new Slot(64, false, s -> s.is(item(RS)) || s.is(item(CIRC)) || Mlrs.pressFuze(s) || anyRecipe(s, 2));
    private final Slot out = new Slot(64, true, s -> false).chute(2, 0, 2, Direction.EAST);
    private final Slot[] slots = { casing, filler, fuze, out };
    private final List<FluidTank> tanks = List.of(casing.gauge, filler.gauge, fuze.gauge, out.gauge);
    private final IItemHandler hatchIn = inHandler(casing, filler, fuze), hatchOut = outHandler(out);

    private int progress;
    private int mode;
    private boolean on;
    private int runRecipe = -1;
    private int prevProgress;

    private String leafletText = "";

    public String leafletText() { return leafletText; }

    void setLeafletText(String t) {
        String n = t == null ? "" : t.length() > Balance.LEAFLET_CHARS ? t.substring(0, Balance.LEAFLET_CHARS) : t;
        if (n.equals(leafletText)) return;
        leafletText = n;
        setChanged();
        sync();
    }

    public ShellPressBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SHELL_PRESS_BE.get(), pos, state, Balance.SHELL_PRESS_FE_PER_TICK);
    }

    static boolean isSteel(ItemStack s) { return s.is(item(PLATE)) || s.is(STEEL_PLATES); }

    static boolean holds(ItemStack s, String id, int n) {
        if (n <= 0) return true;
        boolean ok = id.equals(PLATE) ? isSteel(s) : s.is(item(id));
        return ok && s.getCount() >= n;
    }

    public static boolean ready(Recipe r, ItemStack c, ItemStack f, ItemStack z) {
        return holds(c, r.casing(), r.casingN()) && holds(f, r.filler(), r.fillerN()) && holds(z, r.fuze(), r.fuzeN());
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    @Override public int mode() { return mode; }

    @Override public int modeCount() { return RECIPES.size() + 2; }

    public static int startMode() { return RECIPES.size(); }
    public static int stopMode() { return RECIPES.size() + 1; }
    public boolean isOn() { return on; }

    @Override
    public void setMode(int m) {
        if (m == startMode() || m == stopMode()) {
            boolean n = m == startMode();
            if (n == on) return;
            on = n;
            setChanged();
            sync();
            return;
        }
        int nm = Mth.clamp(m, 0, RECIPES.size() - 1);
        if (nm == mode) return;
        mode = nm;
        setChanged();
        sync();
    }

    @Override public void setModeClient(int m) { if (m == startMode() || m == stopMode()) on = m == startMode(); else mode = Mth.clamp(m, 0, RECIPES.size() - 1); }

    public Recipe picked() { return RECIPES.get(Mth.clamp(mode, 0, RECIPES.size() - 1)); }

    public Recipe running() { return progress > 0 && runRecipe >= 0 && runRecipe < RECIPES.size() ? RECIPES.get(runRecipe) : null; }

    @Override
    protected boolean logisticsSource() { return true; }

    @Override
    protected boolean process() {
        if (progress == 0 && !on) return false;
        if (progress == 0) {
            Recipe r = picked();
            Item o = r.outItem();
            if (!out.fits(o, r.outN()) || out.count() + r.outN() > o.getDefaultMaxStackSize()) return fail(OUT_FULL);
            if (isLeaflet(r) && !out.stack.isEmpty() && !ArtShellItem.leaflet(out.stack).equals(leafletText)) return fail(OUT_FULL);
            if (!ready(r, casing.stack, filler.stack, fuze.stack)) return fail(NO_INPUT);
            casing.take(r.casingN());
            filler.take(r.fillerN());
            if (r.fuzeN() > 0) fuze.take(r.fuzeN());
            runRecipe = mode;
            progress = 1;
            setChanged();
            sync();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.SHELL_PRESS_TICKS) {
            Recipe r = RECIPES.get(Mth.clamp(runRecipe, 0, RECIPES.size() - 1));
            out.add(r.outItem(), r.outN());
            if (isLeaflet(r) && !leafletText.isEmpty()) {
                CompoundTag t = new CompoundTag();
                t.putString(ArtShellItem.LEAFLET_KEY, leafletText);
                out.stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
            }
            progress = 0;
            runRecipe = -1;
            setChanged();
            sync();
        } else if (progress % 40 == 0) sync();
        return true;
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>(super.takeAll());
        Recipe r = running();
        if (r != null) {
            l.add(r.casingStack());
            l.add(r.fillerStack());
            if (r.fuzeN() > 0) l.add(r.fuzeStack());
            progress = 0;
            runRecipe = -1;
        }
        return l;
    }

    public static void clientTickPress(Level level, BlockPos pos, BlockState state, ShellPressBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.prevProgress = be.progress;
        if (be.isRunning() && be.progress > 0 && be.progress < Balance.SHELL_PRESS_TICKS) be.progress++;
    }

    public float cycle01(float pt) {
        if (progress <= 0 || runRecipe < 0) return -1.0F;
        float p = prevProgress > 0 ? prevProgress + (progress - prevProgress) * pt : progress;
        return Mth.clamp(p / Balance.SHELL_PRESS_TICKS, 0.0F, 1.0F);
    }

    private static float smooth(float k) { k = Mth.clamp(k, 0.0F, 1.0F); return k * k * (3.0F - 2.0F * k); }

    private static float pulse(float p, float a, float b) { if (p < a || p > b) return 0.0F; return (float) Math.sin(Math.PI * (p - a) / (b - a)); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float p = cycle01(pt);
        final float xs = -1.0F;
        final float feed = p < 0 ? 0.0F : pulse(p, 0.02F, 0.18F) * 4.0F;
        final float fill = p < 0 ? 0.0F : pulse(p, 0.20F, 0.40F) * 6.0F;
        final float arm = p < 0 ? 0.0F : (float) Math.toRadians(35.0F * pulse(p, 0.42F, 0.58F));
        float pr = 0.0F;
        if (p >= 0.60F && p < 0.68F) pr = smooth((p - 0.60F) / 0.08F);
        else if (p >= 0.68F && p < 0.72F) pr = 1.0F;
        else if (p >= 0.72F && p < 0.80F) pr = 1.0F - smooth((p - 0.72F) / 0.08F);
        final float press = pr * 8.0F;
        final float tray = p < 0 ? 0.0F : pulse(p, 0.82F, 1.0F) * 6.0F;
        model.getBone("hopper_feed").ifPresent(b -> b.setPosX(xs * feed));
        model.getBone("filler").ifPresent(b -> b.setPosY(-fill));
        model.getBone("fuze").ifPresent(b -> b.setRotY(arm));
        model.getBone("press").ifPresent(b -> b.setPosY(-press));
        model.getBone("tray").ifPresent(b -> b.setPosX(xs * tray));
    }

    @Override
    protected void clientEffects(Level level) {
        float p = cycle01(0.0F);
        if (p < 0.67F || p > 0.70F || level.getGameTime() % 2 != 0) return;
        var r = level.random;
        double[] w = worldPoint(0.6, 1.2, 2.0);
        for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.CLOUD, w[0] + (r.nextDouble() - 0.5) * 0.6, w[1], w[2] + (r.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.1, 0.05, (r.nextDouble() - 0.5) * 0.1);
    }

    @Override protected String[] iconIds() { return new String[] { "steel_plate", "he_charge", "basic_circuit", "shell_mortar_he" }; }

    @Override
    public List<ItemStack> guiSlotIcons() {
        Recipe r = picked();
        ItemStack fz = r.fuzeStack();
        return List.of(r.casingStack(), r.fillerStack(), fz.isEmpty() ? new ItemStack(item(RS)) : fz, r.outStack());
    }

    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        Recipe r = picked();
        return in.size() >= 3 && ready(r, in.get(0), in.get(1), in.get(2)) ? r.outStack() : ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.SHELL_PRESS_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected int inputTanks() { return 3; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("casing", "filler", "fuze", "shells"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.SHELL_PRESS_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.shell_press", Balance.SHELL_PRESS_TICKS / 20), Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override
    protected String detail() {
        Recipe r = running();
        return "recipe " + picked().out() + (r != null ? ", pressing " + r.out() + " " + progress * 100 / Balance.SHELL_PRESS_TICKS + "%" : ", idle")
                + (out.stack.isEmpty() ? "" : ", " + out.count() + " ready");
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 2, Direction.WEST)) return hatchIn;
        if (at(part, side, 2, 0, 2, Direction.EAST)) return hatchOut;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? energy() : null; }

    @Override protected SoundEvent runSound() { return WmdSounds.SP_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }
    @Override protected float soundVolume() { return 0.4F; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("progress", progress);
        tag.putInt("mode", mode);
        tag.putInt("runRecipe", runRecipe);
        tag.putString("leaflet", leafletText);
        tag.putBoolean("on", on);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        int sp = Math.max(0, tag.getInt("progress"));
        int rr = tag.contains("runRecipe") ? tag.getInt("runRecipe") : -1;
        boolean keep = level != null && level.isClientSide && progress > 0 && sp > 0 && rr == runRecipe && Math.abs(sp - progress) < 20;
        if (!keep) { progress = sp; prevProgress = sp; }
        runRecipe = rr >= 0 && rr < RECIPES.size() ? rr : -1;
        if (runRecipe < 0) progress = 0;
        mode = Mth.clamp(tag.getInt("mode"), 0, RECIPES.size() - 1);
        leafletText = tag.getString("leaflet");
        on = tag.getBoolean("on");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        ownerData.write(tag);
        return tag;
    }
}
