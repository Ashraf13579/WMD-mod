package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class SatelliteFacilityBlockEntity extends ItemProcessBlockEntity implements Owned, ModeMachine {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    public static final int X0 = -3, X1 = 3, Y0 = 0, Y1 = 4, Z0 = 0, Z1 = 4;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(-3, 0, 2, Direction.WEST, false).item(3, 0, 2, Direction.EAST, true).power(0, 0, 4, Direction.SOUTH, false);

    private static final TagKey<Item> TITANIUM_PLATES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/titanium"));
    private static final TagKey<Item> ALUMINIUM_PLATES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/aluminum"));

    private final OwnerData ownerData = new OwnerData();

    private final Slot rtg = new Slot(4, false, s -> s.is(Wmd.RTG_CELL.get()));
    private final Slot titanium = new Slot(64, false, s -> s.is(TITANIUM_PLATES) || s.is(wmd("titanium_plate")));
    private final Slot silicon = new Slot(64, false, s -> s.is(wmd("silicon")));
    private final Slot circuits = new Slot(64, false, s -> s.is(wmd("advanced_circuit")));
    private final Slot aluminium = new Slot(64, false, s -> s.is(ALUMINIUM_PLATES) || s.is(wmd("aluminium_plate")));
    private final Slot module = new Slot(16, false, SatelliteFacilityBlockEntity::isModule);
    private final Slot out = new Slot(1, true, s -> false);
    private final Slot[] slots = { rtg, titanium, silicon, circuits, aluminium, module, out };
    private final List<FluidTank> tanks = List.of(rtg.gauge, titanium.gauge, silicon.gauge, circuits.gauge, aluminium.gauge, module.gauge, out.gauge);
    private final IItemHandler hatchIn = inHandler(rtg, titanium, silicon, circuits, aluminium, module), hatchOut = outHandler(out);

    private int progress;
    private int mode;
    private String runType = "";
    private int prevProgress;

    public SatelliteFacilityBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SATELLITE_FACILITY_BE.get(), pos, state, Balance.SATFAC_FE_PER_TICK);
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
    @Override public int modeCount() { return Satellites.TYPES.size(); }

    @Override
    public void setMode(int m) {
        int nm = Mth.clamp(m, 0, Satellites.TYPES.size() - 1);
        if (nm == mode) return;
        mode = nm;
        setChanged();
        sync();
    }

    @Override public void setModeClient(int m) { mode = Mth.clamp(m, 0, Satellites.TYPES.size() - 1); }

    public String pickedType() { return Satellites.TYPES.get(Mth.clamp(mode, 0, Satellites.TYPES.size() - 1)); }

    public String runType() { return progress > 0 ? runType : ""; }

    public static Item moduleFor(String type) {
        return switch (type) {
            case "nav" -> Items.CLOCK;
            case "recon" -> Items.SPYGLASS;
            case "relay" -> wmd("gold_wire");
            case "interceptor" -> wmd("kill_vehicle_pack");
            case "designator" -> wmd("laser_designator_pod");
            default -> null;
        };
    }

    public static int moduleCount(String type) { return type.equals("relay") ? Balance.SATFAC_GOLD_WIRE : 1; }

    static boolean isModule(ItemStack s) {
        for (String t : Satellites.TYPES) { Item m = moduleFor(t); if (m != null && s.is(m)) return true; }
        return false;
    }

    public boolean wrongModule() { return wrongModule(pickedType(), module.stack); }

    public static boolean wrongModule(String type, ItemStack st) { return !st.isEmpty() && !st.is(moduleFor(type)); }

    public static boolean moduleMatches(String type, ItemStack st) {
        Item m = moduleFor(type);
        return m != null && st.is(m) && st.getCount() >= moduleCount(type);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            String type = pickedType();
            if (!out.stack.isEmpty()) return fail(OUT_FULL);
            if (rtg.count() < 1 || titanium.count() < Balance.SATFAC_TITANIUM || silicon.count() < Balance.SATFAC_SILICON
                    || circuits.count() < Balance.SATFAC_CIRCUITS || aluminium.count() < Balance.SATFAC_ALUMINIUM || !moduleMatches(type, module.stack))
                return fail(NO_INPUT);
            rtg.take(1);
            titanium.take(Balance.SATFAC_TITANIUM);
            silicon.take(Balance.SATFAC_SILICON);
            circuits.take(Balance.SATFAC_CIRCUITS);
            aluminium.take(Balance.SATFAC_ALUMINIUM);
            module.take(moduleCount(type));
            runType = type;
            progress = 1;
            setChanged();
            sync();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.SATFAC_TICKS) {
            out.stack = PayloadUnitItem.of(runType);
            out.sync();
            progress = 0;
            runType = "";
            setChanged();
            sync();
        } else if (progress % 100 == 0) sync();
        return true;
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>(super.takeAll());
        if (progress > 0 && !runType.isEmpty()) {
            l.add(new ItemStack(Wmd.RTG_CELL.get()));
            l.add(new ItemStack(wmd("titanium_plate"), Balance.SATFAC_TITANIUM));
            l.add(new ItemStack(wmd("silicon"), Balance.SATFAC_SILICON));
            l.add(new ItemStack(wmd("advanced_circuit"), Balance.SATFAC_CIRCUITS));
            l.add(new ItemStack(wmd("aluminium_plate"), Balance.SATFAC_ALUMINIUM));
            Item m = moduleFor(runType);
            if (m != null) l.add(new ItemStack(m, moduleCount(runType)));
            progress = 0;
            runType = "";
        }
        return l;
    }

    public static void clientTickFacility(Level level, BlockPos pos, BlockState state, SatelliteFacilityBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.prevProgress = be.progress;
        if (be.isRunning() && be.progress > 0 && be.progress < Balance.SATFAC_TICKS) be.progress++;
    }

    public float build01(float pt) {
        if (progress <= 0 || runType.isEmpty()) return -1.0F;
        float p = prevProgress > 0 ? prevProgress + (progress - prevProgress) * pt : progress;
        return Mth.clamp(p / Balance.SATFAC_TICKS, 0.0F, 1.0F);
    }

    @Override protected String[] iconIds() { return new String[] { "rtg_cell", "titanium_plate", "silicon", "advanced_circuit", "aluminium_plate", "gold_wire", "payload_unit" }; }

    @Override
    public List<ItemStack> guiSlotIcons() {
        String t = pickedType();
        Item m = moduleFor(t);
        return List.of(new ItemStack(Wmd.RTG_CELL.get()), new ItemStack(wmd("titanium_plate"), Balance.SATFAC_TITANIUM), new ItemStack(wmd("silicon"), Balance.SATFAC_SILICON),
                new ItemStack(wmd("advanced_circuit"), Balance.SATFAC_CIRCUITS), new ItemStack(wmd("aluminium_plate"), Balance.SATFAC_ALUMINIUM),
                m != null ? new ItemStack(m, moduleCount(t)) : new ItemStack(Items.BARRIER), PayloadUnitItem.of(t));
    }

    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        return in.size() >= 6 && moduleMatches(pickedType(), in.get(5)) ? PayloadUnitItem.of(pickedType()) : ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.SATFAC_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected int inputTanks() { return 6; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("rtg_cell", "titanium", "silicon", "circuits", "aluminium", "sat_module", "payload_unit"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.SATFAC_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.satellite_facility", Balance.SATFAC_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override
    protected String detail() {
        return "type " + pickedType() + (progress > 0 ? ", building " + runType + " " + progress * 100 / Balance.SATFAC_TICKS + "%" : ", stand empty")
                + (wrongModule() ? ", wrong module" : "") + (out.stack.isEmpty() ? "" : ", payload unit ready");
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -3, 0, 2, Direction.WEST)) return hatchIn;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? energy() : null; }

    @Override protected SoundEvent runSound() { return WmdSounds.SF_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }
    @Override protected float soundVolume() { return 0.35F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x5346_0000_0000_0000L;
        double[] w = worldPoint(0.0, 3.6, 2.0);
        VeilBridge.machineGlow(key, w[0], w[1], w[2], 0.85F, 0.92F, 1.0F, 4.0F, 0.6F, t);
        float p = build01(0.0F);
        if (p < 0.10F || p > 0.70F || t % 11 != 0) return;
        var r = level.random;
        double[] s = worldPoint((r.nextDouble() - 0.5) * 1.2, 0.9 + r.nextDouble() * 1.2, 2.0 + (r.nextDouble() - 0.5) * 1.0);
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, s[0], s[1], s[2], (r.nextDouble() - 0.5) * 0.08, 0.04, (r.nextDouble() - 0.5) * 0.08);
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("progress", progress);
        tag.putInt("mode", mode);
        tag.putString("runType", runType);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        int sp = Math.max(0, tag.getInt("progress"));
        String rt = tag.getString("runType");

        boolean keep = level != null && level.isClientSide && progress > 0 && sp > 0 && rt.equals(runType) && Math.abs(sp - progress) < 30;
        if (!keep) { progress = sp; prevProgress = sp; }
        runType = rt.isEmpty() ? "" : Satellites.validType(rt);
        mode = Mth.clamp(tag.getInt("mode"), 0, Satellites.TYPES.size() - 1);
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
