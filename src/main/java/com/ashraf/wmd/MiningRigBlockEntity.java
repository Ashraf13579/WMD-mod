package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class MiningRigBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank water = new FluidTank(Balance.RIG_WATER_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final Slot bit = new Slot(4, false, s -> bitTier(s) > 0);
    private final Slot ore = new Slot(64, true, s -> false).chute(2, 0, 1, Direction.EAST);
    private final Slot[] slots = { bit, ore };
    private final List<FluidTank> tanks = List.of(water, bit.gauge, ore.gauge);
    private final IFluidHandler waterIn = fillOnly(water);
    private final IItemHandler in = inHandler(bit), outH = outHandler(ore);

    private int progress, cycleTicks;

    private int bitLife, bitTier;
    private List<BlockPos> targets = new ArrayList<>();
    private BlockPos current;
    private long nextScan;

    private int seen;

    private java.util.UUID placer;
    private final java.util.Set<Long> approved = new java.util.HashSet<>();
    private int claimed;

    void setPlacer(java.util.UUID id) { placer = id; setChanged(); }

    protected static final int NO_WATER = 6, NO_DEPOSIT = 8, WRONG_BIT = 9, NO_BIT = 10;

    public MiningRigBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.MINING_RIG_BE.get(), pos, state, Balance.RIG_FE_PER_TICK);
    }

    static int bitTier(ItemStack s) {
        if (s.is(wmd("drill_bit"))) return 1;
        if (s.is(wmd("carbide_drill_bit"))) return 2;
        if (s.is(wmd("diamond_drill_bit"))) return 3;
        return 0;
    }

    private static int ticksFor(int tier) { return tier == 3 ? Balance.RIG_TICKS_DIAMOND : tier == 2 ? Balance.RIG_TICKS_CARBIDE : Balance.RIG_TICKS_STEEL; }

    static final String WORN_KEY = "wmd_bit_left";

    static int wornLife(ItemStack s) {
        net.minecraft.world.item.component.CustomData d = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getInt(WORN_KEY);
    }

    @Override
    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>(super.takeAll());
        if (bitLife > 0 && bitTier > 0) {
            ItemStack b = new ItemStack(wmd(bitTier == 3 ? "diamond_drill_bit" : bitTier == 2 ? "carbide_drill_bit" : "drill_bit"));
            if (bitLife < lifeFor(bitTier)) {
                CompoundTag t = new CompoundTag();
                t.putInt(WORN_KEY, bitLife);
                b.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
                b.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                        Component.translatable("tooltip.wmd.bit_worn", bitLife, lifeFor(bitTier)).withStyle(net.minecraft.ChatFormatting.GRAY))));
            }
            l.add(b);
            bitLife = 0;
            setChanged();
        }
        return l;
    }

    private static int lifeFor(int tier) { return tier == 3 ? Balance.RIG_BIT_BLOCKS_DIAMOND : tier == 2 ? Balance.RIG_BIT_BLOCKS_CARBIDE : Balance.RIG_BIT_BLOCKS_STEEL; }

    private String rawFor(BlockState s, int tier) {
        if (!(s.getBlock() instanceof DepositOreBlock d)) return null;
        return switch (d.ore) {
            case "rutile" -> "raw_titanium";
            case "wolframite" -> "raw_tungsten";
            case "uraninite" -> tier >= 3 ? "raw_uranium" : null;
            default -> null;
        };
    }

    @Override
    protected boolean process() {
        if (level == null) return false;
        if (level.getGameTime() >= nextScan) scan();
        if (progress == 0) {
            if (bitLife <= 0) {
                if (bit.count() < 1) return fail(NO_BIT);
                bitTier = bitTier(bit.stack);
                int worn = wornLife(bit.stack);
                bit.take(1);
                bitLife = worn > 0 ? Math.min(worn, lifeFor(bitTier)) : lifeFor(bitTier);
                setChanged();
            }
            if (targets.isEmpty()) return fail(NO_DEPOSIT);
            if (water.getFluidAmount() < Balance.RIG_WATER_MB) return fail(NO_WATER);

            current = null;
            String raw = null;
            for (BlockPos p : targets) {
                if (!level.isLoaded(p)) continue;
                raw = rawFor(level.getBlockState(p), bitTier);
                if (raw != null) { current = p; break; }
            }
            if (current == null) return fail(WRONG_BIT);
            if (!ore.fits(wmd(raw), Balance.RIG_RAW_PER_BLOCK)) return fail(OUT_FULL);
            water.drain(Balance.RIG_WATER_MB, IFluidHandler.FluidAction.EXECUTE);
            cycleTicks = ticksFor(bitTier);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += Balance.RIG_WATER_MB / Math.max(1, cycleTicks);
        if (++progress > cycleTicks) {
            progress = 0;
            if (current != null) {
                BlockState s = level.getBlockState(current);
                String raw = rawFor(s, bitTier);
                if (raw != null) {
                    level.setBlock(current, current.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
                    ore.add(wmd(raw), Balance.RIG_RAW_PER_BLOCK);
                    bitLife--;
                }
                targets.remove(current);
                current = null;
            }
            setChanged();
        }
        return true;
    }

    private void scan() {
        targets = new ArrayList<>();
        if (level == null) return;
        BlockPos hole = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), 0, 0, 2);
        int top = hole.getY() - 1, bottom = Math.max(level.getMinBuildHeight(), hole.getY() - Balance.RIG_DEPTH);
        int r = Balance.RIG_RADIUS, r2 = r * r;
        List<BlockPos> found = new ArrayList<>();
        for (int cx = (hole.getX() - r) >> 4; cx <= (hole.getX() + r) >> 4; cx++) {
            for (int cz = (hole.getZ() - r) >> 4; cz <= (hole.getZ() + r) >> 4; cz++) {
                net.minecraft.world.level.chunk.LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                net.minecraft.world.level.chunk.LevelChunkSection[] secs = ch.getSections();
                for (int si = level.getSectionIndex(bottom); si <= level.getSectionIndex(top); si++) {
                    if (si < 0 || si >= secs.length) continue;
                    net.minecraft.world.level.chunk.LevelChunkSection sec = secs[si];
                    if (sec.hasOnlyAir() || !sec.maybeHas(st -> st.getBlock() instanceof DepositOreBlock)) continue;
                    int baseY = level.getSectionYFromSectionIndex(si) << 4;
                    for (int ly = 0; ly < 16; ly++) {
                        int y = baseY + ly;
                        if (y < bottom || y > top) continue;
                        for (int lx = 0; lx < 16; lx++) {
                            int dx = (cx << 4) + lx - hole.getX();
                            for (int lz = 0; lz < 16; lz++) {
                                int dz = (cz << 4) + lz - hole.getZ();
                                if (dx * dx + dz * dz > r2) continue;
                                if (sec.getBlockState(lx, ly, lz).getBlock() instanceof DepositOreBlock) found.add(new BlockPos(hole.getX() + dx, y, hole.getZ() + dz));
                            }
                        }
                    }
                }
            }
        }
        found = claimFilter(hole, found);
        found.sort((a, b) -> {
            int da = Math.abs(a.getX() - hole.getX()) + Math.abs(a.getZ() - hole.getZ()), db = Math.abs(b.getX() - hole.getX()) + Math.abs(b.getZ() - hole.getZ());
            return da != db ? Integer.compare(da, db) : Integer.compare(b.getY(), a.getY());
        });
        targets = found;
        seen = found.size();
        nextScan = level.getGameTime() + Balance.RIG_SCAN_TICKS;
    }

    public int bitLife1000() { return bitLife <= 0 ? 0 : Math.min(1000, bitLife * 1000 / lifeFor(bitTier)); }

    @Override protected String[] iconIds() { return new String[] { "drill_bit", "raw_titanium" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Math.max(1, cycleTicks); }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("water", "bit", "raw_ore"); }
    @Override protected int rateMbPerTick() { return Balance.RIG_WATER_MB / Balance.RIG_TICKS_STEEL; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.RIG_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.rig", Balance.RIG_RAW_PER_BLOCK, Balance.RIG_TICKS_STEEL / 20, Balance.RIG_TICKS_CARBIDE / 20, Balance.RIG_TICKS_DIAMOND / 20, Balance.RIG_WATER_MB),
                Component.translatable("gui.wmd.info.rig_reach", Balance.RIG_RADIUS, seen));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.MR_DRILL_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override protected float levelFraction() { return bitLife1000() / 1000.0F; }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 3 != 0) return;
        var r = level.random;
        double[] w = worldPoint(0.0, 0.9, 2.0);
        level.addParticle(ParticleTypes.WHITE_ASH, w[0] + (r.nextDouble() - 0.5) * 0.9, w[1], w[2] + (r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.02, 0.03, (r.nextDouble() - 0.5) * 0.02);
        if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.SPLASH, w[0] + (r.nextDouble() - 0.5) * 0.7, w[1] + 0.2, w[2] + (r.nextDouble() - 0.5) * 0.7, 0, 0.04, 0);
    }

    @Override
    protected String detail() {
        return "water " + mb(water) + ", bit " + (bitLife <= 0 ? "none" : (bitTier == 3 ? "diamond " : bitTier == 2 ? "carbide " : "steel ") + bitLife + " blocks") + ", deposit " + seen + " blocks in reach" + (claimed > 0 ? " (" + claimed + " more under a claim)" : "") + ", ore " + ore.count() + ", cycle " + (cycleTicks == 0 ? 0 : progress * 100 / cycleTicks) + "%";
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -2, 0, 1, Direction.WEST) ? waterIn : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 2, 0, 1, Direction.EAST)) return outH;
        if (at(part, side, -2, 1, 0, Direction.NORTH)) return in;
        return null;
    }

    private List<BlockPos> claimFilter(BlockPos hole, List<BlockPos> found) {
        claimed = 0;
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl) || !Config.Server.respectClaimsPlacing() || found.isEmpty()) return found;
        long own = net.minecraft.world.level.ChunkPos.asLong(hole.getX() >> 4, hole.getZ() >> 4);
        net.minecraft.server.level.ServerPlayer op = placer == null ? null : sl.getServer().getPlayerList().getPlayer(placer);
        if (op != null && op.level() != sl) op = null;
        java.util.Map<Long, Boolean> asked = new java.util.HashMap<>();
        List<BlockPos> ok = new ArrayList<>(found.size());
        for (BlockPos p : found) {
            long c = net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            final net.minecraft.server.level.ServerPlayer who = op;
            boolean yes = c == own || asked.computeIfAbsent(c, k -> {
                if (who != null) {
                    boolean y = ClaimGuard.mayPlaceQuiet(sl, who, p);
                    if (y) approved.add(k); else approved.remove(k);
                    return y;
                }
                return approved.contains(k) || !ClaimGuard.protectedAt(sl, p);
            });
            if (yes) ok.add(p); else claimed++;
        }
        if (op != null) setChanged();
        return ok;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("progress", progress); tag.putInt("cycle", cycleTicks); tag.putInt("bitLife", bitLife); tag.putInt("bitTier", bitTier); if (current != null) tag.putLong("current", current.asLong());
        if (placer != null) tag.putUUID("placer", placer);
        tag.putLongArray("claimOk", approved.stream().mapToLong(Long::longValue).toArray());
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (!tag.contains("bitLife")) return;
        progress = tag.getInt("progress"); cycleTicks = tag.getInt("cycle"); bitLife = tag.getInt("bitLife"); bitTier = tag.getInt("bitTier");
        current = tag.contains("current") ? BlockPos.of(tag.getLong("current")) : null;
        placer = tag.hasUUID("placer") ? tag.getUUID("placer") : null;
        approved.clear();
        for (long c : tag.getLongArray("claimOk")) approved.add(c);
        nextScan = 0;
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 5).fluid(-2, 0, 1, Direction.WEST, "water", false).item(2, 0, 1, Direction.EAST, true).item(-2, 1, 0, Direction.NORTH, false).power(0, 0, 4, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
