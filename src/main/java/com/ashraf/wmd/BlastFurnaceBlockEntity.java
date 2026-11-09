package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class BlastFurnaceBlockEntity extends ItemProcessBlockEntity {

    private final Slot charge = new Slot(64, false, this::smeltable);
    private final Slot tin = new Slot(64, false, s -> s.is(TIN) || isFuel(s));
    private final Slot coal = new Slot(64, false, BlastFurnaceBlockEntity::isFuel);
    private final Slot out = new Slot(64, true, s -> false).chute(0, 0, 0, Direction.NORTH);
    private final Slot[] slots = { charge, tin, coal, out };
    private final List<FluidTank> tanks = List.of(charge.gauge, tin.gauge, coal.gauge, out.gauge);
    private final IItemHandler in = balancedIn(), outH = outHandler(out);

    private static final net.minecraft.tags.TagKey<Item> TIN = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:ingots/tin"));

    private int progress, cycle, fuel, pendingCount;
    private Item pending = Items.AIR;
    private boolean blowing;

    private record Cook(Item item, int count, int ticks) {}
    private Item cookedFrom;
    private Cook cooked;

    public BlastFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.BLAST_FURNACE_BE.get(), pos, state, Balance.BF_BLOWER_FE);
    }

    private static boolean isIron(ItemStack s) { return s.is(Tags.Items.INGOTS_IRON); }

    private static boolean isCopper(ItemStack s) { return s.is(Tags.Items.INGOTS_COPPER); }

    private static boolean isFuel(ItemStack s) { return s.is(ItemTags.COALS) || s.is(wmd("coke")); }

    private IItemHandler balancedIn() {
        Slot[] ss = { charge, tin, coal };
        return new IItemHandler() {
            @Override public int getSlots() { return ss.length; }
            @Override public ItemStack getStackInSlot(int i) { return ss[i].stack; }
            @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int i) { return ss[i].max; }
            @Override public boolean isItemValid(int i, ItemStack s) { return ss[i].valid.test(s); }
            @Override
            public ItemStack insertItem(int i, ItemStack s, boolean sim) {
                ItemStack r;
                if (i > 0 && isFuel(s)) {
                    boolean addFirst = (tin.stack.isEmpty() || ItemStack.isSameItemSameComponents(tin.stack, s)) && tin.count() < coal.count();
                    Slot a = addFirst ? tin : coal, b = addFirst ? coal : tin;
                    r = a.insert(s, sim);
                    if (!r.isEmpty()) r = b.insert(r, sim);
                } else r = ss[i].insert(s, sim);
                if (!sim) setChanged();
                return r;
            }
        };
    }

    private boolean isCoke() { return coal.stack.is(wmd("coke")); }

    private boolean smeltable(ItemStack s) { return isIron(s) || isCopper(s) || level == null || cook(s) != null; }

    private Cook cook(ItemStack s) {
        if (level == null || s.isEmpty()) return null;
        if (s.getItem() == cookedFrom) return cooked;
        SingleRecipeInput input = new SingleRecipeInput(s);
        Cook c = null;
        var blast = level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, level);
        if (blast.isPresent()) c = cookOf(blast.get(), 1);
        else {
            var smelt = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
            if (smelt.isPresent()) c = cookOf(smelt.get(), 2);
        }
        cookedFrom = s.getItem();
        cooked = c;
        return c;
    }

    private Cook cookOf(RecipeHolder<? extends AbstractCookingRecipe> h, int div) {
        ItemStack r = h.value().getResultItem(level.registryAccess());
        return r.isEmpty() ? null : new Cook(r.getItem(), r.getCount(), Math.max(10, h.value().getCookingTime() / div));
    }

    @Override protected int feCost() { return 0; }
    @Override public int guiFeUse() { return blowing ? Balance.BF_BLOWER_FE : 0; }

    @Override
    protected boolean process() {
        if (progress == 0) {
            ItemStack s = charge.stack;
            if (s.isEmpty()) return fail(NO_INPUT);
            if (isIron(s)) {
                Item steel = wmd("steel_ingot");
                int need = tin.stack.is(wmd("coke")) ? Balance.BF_STEEL_COKE : Balance.BF_STEEL_COAL;
                if (charge.count() < Balance.BF_STEEL_IRON || !isFuel(tin.stack) || tin.count() < need) return fail(NO_INPUT);
                if (fuel <= 0 && coal.count() < 1) return fail(NO_INPUT);
                if (!out.fits(steel, 1)) return fail(OUT_FULL);
                if (fuel <= 0) { fuel = isCoke() ? Balance.BF_SMELT_PER_COKE : Balance.BF_SMELT_PER_COAL; coal.take(1); }
                fuel--;
                charge.take(Balance.BF_STEEL_IRON);
                tin.take(need);
                pending = steel;
                pendingCount = 1;
                cycle = Balance.BF_STEEL_TICKS;
            } else if (isCopper(s)) {
                Item bronze = wmd("bronze_ingot");
                if (charge.count() < Balance.BF_BRONZE_COPPER || !tin.stack.is(TIN) || tin.count() < Balance.BF_BRONZE_TIN) return fail(NO_INPUT);
                if (fuel <= 0 && coal.count() < 1) return fail(NO_INPUT);
                if (!out.fits(bronze, Balance.BF_BRONZE_OUT)) return fail(OUT_FULL);
                if (fuel <= 0) { fuel = isCoke() ? Balance.BF_SMELT_PER_COKE : Balance.BF_SMELT_PER_COAL; coal.take(1); }
                fuel--;
                charge.take(Balance.BF_BRONZE_COPPER);
                tin.take(Balance.BF_BRONZE_TIN);
                pending = bronze;
                pendingCount = Balance.BF_BRONZE_OUT;
                cycle = Balance.BF_BRONZE_TICKS;
            } else {
                Cook c = cook(s);
                if (c == null) return fail(NO_INPUT);
                if (fuel <= 0 && coal.count() < 1) return fail(NO_INPUT);
                if (!out.fits(c.item(), c.count())) return fail(OUT_FULL);
                if (fuel <= 0) { fuel = isCoke() ? Balance.BF_SMELT_PER_COKE : Balance.BF_SMELT_PER_COAL; coal.take(1); }
                fuel--;
                charge.take(1);
                pending = c.item();
                pendingCount = c.count();
                cycle = c.ticks();
            }
            progress = 1;
            setChanged();
            return true;
        }
        blowing = useExtraEnergy(Balance.BF_BLOWER_FE);
        int step = blowing ? Balance.BF_BLOWER_SPEED : 1;
        flowAcc += step;
        progress += step;
        if (progress > cycle) {
            progress = 0;
            if (pending != Items.AIR) out.add(pending, pendingCount);
            pending = Items.AIR;
            setChanged();
        }
        return true;
    }

    @Override protected void idle() { super.idle(); blowing = false; }

    @Override protected String[] iconIds() { return new String[] { "steel_ingot", "steel_ingot", "steel_ingot" }; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(Items.RAW_IRON), new ItemStack(wmd("tin_ingot")), new ItemStack(Items.COAL), new ItemStack(wmd("steel_ingot"))); }

    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        if (in.isEmpty()) return ItemStack.EMPTY;
        if (isIron(in.get(0))) return new ItemStack(wmd("steel_ingot"));
        if (isCopper(in.get(0))) return new ItemStack(wmd("bronze_ingot"), Balance.BF_BRONZE_OUT);
        Cook c = cook(in.get(0));
        return c == null ? ItemStack.EMPTY : new ItemStack(c.item(), c.count());
    }

    @Override protected float progress01() { return progress == 0 || cycle <= 0 ? 0.0F : progress / (float) cycle; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("bf_charge", "bf_alloy", "fuel", "bf_out"); }
    @Override protected int rateMbPerTick() { return Balance.BF_BLOWER_SPEED; }
    @Override protected int inputTanks() { return 3; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.MUFFLER || (t == UpgradeItem.Type.AUTO_EJECT && super.acceptsUpgrade(t)); }

    @Override public IEnergyStorage guiEnergy() { return null; }
    @Override public boolean guiHidesPower() { return true; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.blast_furnace", Balance.BF_STEEL_IRON, Balance.BF_STEEL_COAL, Balance.BF_STEEL_TICKS / 20),
                Component.translatable("gui.wmd.info.blast_furnace_smelt", Balance.BF_SMELT_PER_COAL),
                Component.translatable("gui.wmd.info.blast_furnace_bronze", Balance.BF_BRONZE_COPPER, Balance.BF_BRONZE_TIN, Balance.BF_BRONZE_OUT, Balance.BF_BRONZE_TICKS / 20),
                Component.translatable("gui.wmd.info.blast_furnace_blower", Balance.BF_BLOWER_SPEED, Balance.BF_BLOWER_FE * 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.BF_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }
    @Override protected void runStarted(Level level) { playOnce(level, SoundEvents.FIRECHARGE_USE, 0.6F, 0.7F); }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x4246_0000_0000_0000L;
        double[] top = worldPoint(0.0, 2.98, 0.5), door = worldPoint(-0.125, 2.06, 1.38), tap = worldPoint(0.0, 0.97, -0.43), wind = worldPoint(0.0, 1.6, 0.5);
        float blast = flowFraction();
        boolean blow = blast > 0.6F;
        if (blow && t % 80 == 0) level.playLocalSound(wind[0], wind[1], wind[2], WmdSounds.BF_BLOWER_LOOP.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F, false);
        VeilBridge.machineGlow(key, wind[0], wind[1], wind[2], 1.0F, 0.5F, 0.15F, 2.6F, 0.9F + 0.15F * (float) Math.sin(t * 0.21), t);
        VeilBridge.machineGlow(key + 1, door[0], door[1], door[2], 1.0F, 0.55F, 0.2F, 1.8F, 0.8F + 0.25F * (float) Math.sin(t * 0.37), t);
        VeilBridge.machineGlow(key + 2, top[0], top[1] + 0.3, top[2], 1.0F, 0.6F, 0.25F, 2.2F, (blow ? 1.1F : 0.8F) + 0.3F * (float) Math.sin(t * 0.53), t);
        var r = level.random;
        if (t % (blow ? 1 : 2) == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, top[0] + (r.nextDouble() - 0.5) * 0.45, top[1] + 0.05, top[2] + (r.nextDouble() - 0.5) * 0.45,
                    (r.nextDouble() - 0.5) * 0.012, 0.05 + r.nextDouble() * (blow ? 0.06 : 0.03), (r.nextDouble() - 0.5) * 0.012);
        if (t % (blow ? 4 : 7) == 0)
            level.addParticle(ParticleTypes.LAVA, top[0] + (r.nextDouble() - 0.5) * 0.3, top[1] + 0.1, top[2] + (r.nextDouble() - 0.5) * 0.3, 0, 0, 0);
        if (t % 3 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, top[0], top[1] + 0.55, top[2], (r.nextDouble() - 0.5) * 0.02, 0.07, (r.nextDouble() - 0.5) * 0.02);
        if (t % 23 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, top[0], top[1] + 0.8, top[2], 0, 0.05, 0);
        if (t % 6 == 0)
            level.addParticle(ParticleTypes.SMALL_FLAME, door[0] + (r.nextDouble() - 0.5) * 0.3, door[1] + 0.25, door[2] + 0.08, 0, 0.02, 0);
        if (t % 13 == 0)
            level.addParticle(ParticleTypes.SMOKE, door[0], door[1] + 0.35, door[2] + 0.1, 0, 0.03, 0);
        double loop = (t % SKIP_LOOP) / (double) SKIP_LOOP;
        if (loop >= POUR_FROM && loop < POUR_TO && t % 4 == 0)
            level.addParticle(ParticleTypes.DRIPPING_LAVA, tap[0] + (r.nextDouble() - 0.5) * 0.06, tap[1], tap[2] + (r.nextDouble() - 0.5) * 0.06, 0, 0, 0);
    }

    public static final int SKIP_LOOP = 160;
    public static final double POUR_FROM = 0.78, POUR_TO = 0.95;

    @Override
    protected String detail() { return "charge " + charge.count() + ", tin " + tin.count() + ", coal " + coal.count() + " (+" + fuel + " smelts), out " + out.count() + ", cycle " + (cycle > 0 ? progress * 100 / cycle : 0) + "%" + (blowing ? ", blower on" : ""); }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 1, Direction.SOUTH)) return in;
        if (at(part, side, 0, 0, 0, Direction.NORTH)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("progress", progress);
        tag.putInt("cycle", cycle);
        tag.putInt("fuel", fuel);
        tag.putString("pending", BuiltInRegistries.ITEM.getKey(pending).toString());
        tag.putInt("pendingCount", pendingCount);
        tag.putBoolean("bf2", true);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        progress = tag.getInt("progress");
        cycle = tag.getInt("cycle");
        fuel = tag.getInt("fuel");
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("pending"));
        pending = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        pendingCount = tag.getInt("pendingCount");
        if (tag.contains("progress") && !tag.contains("bf2")) {
            out.stack = coal.stack; coal.stack = tin.stack; tin.stack = ItemStack.EMPTY;
            out.sync(); coal.sync(); tin.sync();
        }
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 2).item(-1, 0, 1, Direction.SOUTH, false).item(0, 0, 0, Direction.NORTH, true)
            .power(1, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
