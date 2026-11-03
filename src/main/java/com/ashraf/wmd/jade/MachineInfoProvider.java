package com.ashraf.wmd.jade;

import java.util.List;

import com.ashraf.wmd.Access;
import com.ashraf.wmd.ArtilleryGunBlockEntity;
import com.ashraf.wmd.BayKind;
import com.ashraf.wmd.BayLine;
import com.ashraf.wmd.BayModuleBlockEntity;
import com.ashraf.wmd.BombLine;
import com.ashraf.wmd.BombLineBlockEntity;
import com.ashraf.wmd.DroneCatapultBlockEntity;
import com.ashraf.wmd.DroneHiveBlockEntity;
import com.ashraf.wmd.DroneKind;
import com.ashraf.wmd.DroneLine;
import com.ashraf.wmd.DroneModuleBlockEntity;
import com.ashraf.wmd.DroneType;
import com.ashraf.wmd.LaserTripodBlockEntity;
import com.ashraf.wmd.CamoNetBlockEntity;
import com.ashraf.wmd.CruiseLauncherBlockEntity;
import com.ashraf.wmd.DecoyBlockEntity;
import com.ashraf.wmd.GeneratorEngineBlockEntity;
import com.ashraf.wmd.LabLine;
import com.ashraf.wmd.LabModuleBlockEntity;
import com.ashraf.wmd.GuiItemSlots;
import com.ashraf.wmd.GuiMachine;
import com.ashraf.wmd.FusionReactorBlockEntity;
import com.ashraf.wmd.MachinePartBlockEntity;
import com.ashraf.wmd.MultiblockPartBlock;
import com.ashraf.wmd.Owned;
import com.ashraf.wmd.PadFillerBlock;
import com.ashraf.wmd.PadPlateBlock;
import com.ashraf.wmd.PadStructure;
import com.ashraf.wmd.RadarPartBlock;
import com.ashraf.wmd.RadarStructure;
import com.ashraf.wmd.Wmd;
import com.ashraf.wmd.FloodgateBlockEntity;
import com.ashraf.wmd.FluidValveBlock;
import com.ashraf.wmd.MissileDepotBlockEntity;
import com.ashraf.wmd.MissileTeleporterBlockEntity;
import com.ashraf.wmd.RobotDockBlockEntity;
import com.ashraf.wmd.SorterBlockEntity;
import com.ashraf.wmd.Balance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElementHelper;

public final class MachineInfoProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final MachineInfoProvider INSTANCE = new MachineInfoProvider();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "machine");
    private static final String KEY = "wmd";

    private MachineInfoProvider() {}

    @Override public ResourceLocation getUid() { return UID; }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) { return isOurs(accessor.getBlock()); }

    private static boolean isOurs(Block b) { return Wmd.MODID.equals(BuiltInRegistries.BLOCK.getKey(b).getNamespace()); }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();
        BlockPos ctl = controllerOf(level, pos, accessor.getBlockState(), accessor.getBlockEntity());
        BlockEntity be = level.getBlockEntity(ctl);
        if (be == null) return;
        CompoundTag t = new CompoundTag();
        if (!ctl.equals(pos)) t.putLong("ctl", ctl.asLong());

        Owned owned = be instanceof Owned ow ? ow : Access.ownerAt(level, ctl);
        if (owned != null && owned.getOwner() != null && accessor.getPlayer() != null && !Access.mayUse(owned, accessor.getPlayer())) {
            if (be instanceof GuiMachine m) t.putInt("st", m.guiStatus());
            t.putString("owner", owned.getOwnerName());
            t.putBoolean("pub", false);
            t.putInt("trusted", 0);
            data.put(KEY, t);
            return;
        }

        IEnergyStorage energy = null;
        int feUse = 0;
        if (be instanceof GuiMachine m) {
            t.putInt("st", m.guiStatus());
            t.putInt("pr", m.guiProgress());
            energy = m.guiEnergy();
            feUse = m.guiFeUse();
            List<FluidTank> tanks = m.guiTanks();
            List<Fluid> fluids = m.guiTankFluids();
            int nFluid = m instanceof GuiItemSlots s ? Math.min(s.firstSlotTank(), tanks.size()) : tanks.size();
            ListTag tl = new ListTag();
            for (int i = 0; i < nFluid; i++) {
                FluidTank tank = tanks.get(i);
                Fluid f = !tank.isEmpty() ? tank.getFluid().getFluid() : i < fluids.size() ? fluids.get(i) : Fluids.EMPTY;
                tl.add(tank(f, tank.getFluidAmount(), tank.getCapacity(), m.guiTankColoured() && m.guiTankOutput(i), m.guiTankUnit(i)));
            }
            if (!tl.isEmpty()) t.put("tanks", tl);
            if (m instanceof GuiItemSlots s) {
                Container c = s.slotContainer();
                ListTag il = new ListTag();
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack st = c.getItem(i);
                    if (st.isEmpty()) continue;
                    CompoundTag it = new CompoundTag();
                    it.putString("id", BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
                    it.putInt("n", st.getCount());
                    il.add(it);
                }
                if (!il.isEmpty()) t.put("items", il);
            }
        } else if (be instanceof GeneratorEngineBlockEntity g) {
            t.putInt("st", g.isRunning() ? 1 : 0);
            energy = g.getEnergy();
            FluidTank fuel = g.getFuelTank();
            ListTag tl = new ListTag();
            tl.add(tank(fuel.getFluid().getFluid(), fuel.getFluidAmount(), fuel.getCapacity(), false, "mB"));
            t.put("tanks", tl);
        }
        if (energy == null && !(be instanceof GuiMachine hm && hm.guiHidesPower())) energy = energyCap(level, ctl);
        if (energy != null && energy.getMaxEnergyStored() > 0) {
            t.putInt("fe", energy.getEnergyStored());
            t.putInt("feMax", energy.getMaxEnergyStored());
            if (feUse > 0) t.putInt("feUse", feUse);
        }
        extras(t, be, level);
        if (be instanceof Owned o && o.getOwner() != null) {
            t.putString("owner", o.getOwnerName());
            t.putBoolean("pub", o.security().isPublic());
            t.putInt("trusted", o.security().trusted().size());
        }
        if (!t.isEmpty()) data.put(KEY, t);
    }

    private static void extras(CompoundTag t, BlockEntity be, Level level) {
        if (be instanceof ArtilleryGunBlockEntity g) {
            int st = g.mission() != 0 && g.gunStatus() == ArtilleryGunBlockEntity.S_READY ? ArtilleryGunBlockEntity.S_FIRING : g.gunStatus();
            t.putString("say", "gui.wmd.art.st." + st);
            t.putInt("sayKind", st == ArtilleryGunBlockEntity.S_READY || st == ArtilleryGunBlockEntity.S_FIRING ? 1
                    : st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING ? 3 : st == ArtilleryGunBlockEntity.S_NO_TARGET ? 0 : 2);
            if (g.hasTarget()) { t.putInt("tx", g.targetX()); t.putInt("tz", g.targetZ()); }
        } else if (be instanceof CruiseLauncherBlockEntity c) {
            int st = c.launcherStatus();
            t.putString("say", "gui.wmd.cruise.st." + st);
            t.putInt("sayKind", st == CruiseLauncherBlockEntity.S_READY || st == CruiseLauncherBlockEntity.S_FIRING ? 1
                    : st == CruiseLauncherBlockEntity.S_RAISING || st == CruiseLauncherBlockEntity.S_LOWERING ? 3 : st == CruiseLauncherBlockEntity.S_NO_TARGET ? 0 : 2);
            if (c.hasTarget()) { t.putInt("tx", c.targetX()); t.putInt("tz", c.targetZ()); }
            t.putIntArray("load", new int[] { c.loaded(), CruiseLauncherBlockEntity.CANISTERS, c.fuelCanisters() });
        } else if (be instanceof FusionReactorBlockEntity f) {
            int st = f.state();
            t.putString("say", "gui.wmd.fusion.st." + st);
            t.putInt("sayKind", st == FusionReactorBlockEntity.RUNNING || st == FusionReactorBlockEntity.RAMP ? 1 : st == FusionReactorBlockEntity.QUENCH ? 2
                    : st == FusionReactorBlockEntity.COLD ? 0 : 3);
            if (st == FusionReactorBlockEntity.RAMP || st == FusionReactorBlockEntity.RUNNING || st == FusionReactorBlockEntity.SHUTDOWN)
                line(t, level, Component.translatable("gui.wmd.fusion.output", String.format("%,d", f.outputFe())));
            else if (st == FusionReactorBlockEntity.ENERGISING)
                line(t, level, Component.translatable("gui.wmd.fusion.charge", f.chargePermille() / 10));
        } else if (be instanceof com.ashraf.wmd.ForcefieldBlockEntity f) {
            int st = f.state();
            t.putString("say", "gui.wmd.ff.st." + st);
            t.putInt("sayKind", st == com.ashraf.wmd.ForcefieldBlockEntity.UP ? 1 : st == com.ashraf.wmd.ForcefieldBlockEntity.CRACK || st == com.ashraf.wmd.ForcefieldBlockEntity.DOWN ? 2
                    : st == com.ashraf.wmd.ForcefieldBlockEntity.OFF ? 0 : 3);
            line(t, level, Component.translatable("gui.wmd.ff.buffer", f.pct()));
            line(t, level, Component.translatable("gui.wmd.ff.jade_radius", f.radius()));
            if (st == com.ashraf.wmd.ForcefieldBlockEntity.CRACK || st == com.ashraf.wmd.ForcefieldBlockEntity.DOWN) line(t, level, Component.translatable("gui.wmd.ff.why." + f.broke()));
        } else if (be instanceof com.ashraf.wmd.LaserDefenceBlockEntity ld) {
            int st = ld.state();
            t.putString("say", "gui.wmd.info.ld_state." + st);
            t.putInt("sayKind", st == com.ashraf.wmd.LaserDefenceBlockEntity.FIRE ? 2 : st == com.ashraf.wmd.LaserDefenceBlockEntity.IDLE ? 0 : 3);
        } else if (be instanceof com.ashraf.wmd.SwarmLauncherBlockEntity sw) {
            line(t, level, Component.translatable("gui.wmd.swarm.loaded", sw.loaded(), com.ashraf.wmd.Balance.SW_CELLS));
            if (sw.hasTarget()) line(t, level, Component.translatable("gui.wmd.railgun.target", sw.targetX(), sw.targetZ()));
            if (sw.firing()) { t.putString("say", "gui.wmd.swarm.st.firing"); t.putInt("sayKind", 3); }
        } else if (be instanceof com.ashraf.wmd.DeadHandBlockEntity dh) {
            int left = dh.secondsLeft();
            t.putString("say", left >= 0 ? "jade.wmd.deadhand.count" : dh.armed() ? "gui.wmd.deadhand.st.armed" : "gui.wmd.deadhand.st.safe");
            t.putInt("sayKind", left >= 0 ? 2 : dh.armed() ? 3 : 0);
            line(t, level, Component.translatable("gui.wmd.deadhand.board", dh.pads().size(), com.ashraf.wmd.Balance.DH_MAX_PADS));
        } else if (be instanceof DecoyBlockEntity d) {
            List<Component> info = d.guiInfo();
            if (!info.isEmpty()) t.putString("info", Component.Serializer.toJson(info.get(0), level.registryAccess()));
        } else if (be instanceof CamoNetBlockEntity n) {
            t.putInt("camo", !n.has() ? 0 : n.torn() ? 2 : 1);
            if (n.has()) t.putIntArray("span", new int[] { n.x1() - n.x0() + 1, n.z1() - n.z0() + 1 });
        } else if (be instanceof BayModuleBlockEntity m) {
            BayModuleBlockEntity core = m.kind == BayKind.CORE ? m : BayLine.coreOf(m);
            if (core != null) t.putString("info", Component.Serializer.toJson(core.statusText(), level.registryAccess()));
        } else if (be instanceof LabModuleBlockEntity lab) {
            LabModuleBlockEntity core = LabLine.coreOf(lab);
            if (core != null) {
                t.putString("lab", core.type().id);
                t.putString("labStage", core.stage().name().toLowerCase(java.util.Locale.ROOT));
                if (core.stage() != LabModuleBlockEntity.Stage.IDLE && !t.contains("pr")) t.putInt("pr", Math.round(core.progress(level) * 1000.0F));
                if (core.auto() || core.heavyWater() > 0)
                    line(t, level, Component.translatable("jade.wmd.lab.auto", Component.translatable(core.auto() ? "gui.wmd.lab.auto.on" : "gui.wmd.lab.auto.off"),
                            String.format("%,d", core.heavyWater()), String.format("%,d", Balance.LAB_HEAVY_WATER_TANK)));
            }
        } else if (be instanceof RobotDockBlockEntity dock) {
            line(t, level, dock.info());
        } else if (be instanceof MissileDepotBlockEntity dp) {
            line(t, level, Component.translatable("jade.wmd.depot", dp.filled(), Balance.DEPOT_CELLS, dp.energy() / Balance.DEPOT_FE_PER_MOVE));
        } else if (be instanceof MissileTeleporterBlockEntity tp) {
            line(t, level, Component.translatable("jade.wmd.tp", Component.translatable("gui.wmd.tp.phase." + tp.phase()), tp.links().size(), Balance.TP_MAX_LINKS));
            if (tp.cooldownSeconds() > 0) line(t, level, Component.translatable("gui.wmd.tp.status.cooldown", tp.cooldownSeconds()));
            if (!tp.missile().isEmpty()) line(t, level, Component.translatable("jade.wmd.tp.missile", tp.missile().getHoverName()));
        } else if (be instanceof SorterBlockEntity so) {
            line(t, level, Component.translatable("jade.wmd.sorter", Component.translatable(so.roundRobin() ? "jade.wmd.sorter.rr" : "jade.wmd.sorter.prio"),
                    so.buffer().isEmpty() ? Component.translatable("jade.wmd.sorter.empty") : so.buffer().getHoverName()));
        } else if (be instanceof FloodgateBlockEntity fg) {
            line(t, level, fg.info());
        } else if (be instanceof DroneModuleBlockEntity dm) {
            DroneModuleBlockEntity core = dm.kind() == DroneKind.CORE ? dm : DroneLine.coreOf(dm);
            if (core != null) {
                boolean run = core.stage() != DroneModuleBlockEntity.Stage.IDLE;
                lineStatus(t, level, "drone_line", Component.translatable("item.wmd.drone_" + core.type().id()), run, core.stage().name(), core.auto(), core.blocker());
                if (run && !t.contains("pr")) t.putInt("pr", Math.round(core.progress(level) * 1000.0F));
            }
            if (dm.kind() == DroneKind.HANGAR) {
                for (int i = 0; i < 2; i++) {
                    DroneType p = DroneType.of(dm.bay(i));
                    if (p != null) line(t, level, Component.translatable("jade.wmd.hangar.bay", i + 1, Component.translatable("item.wmd.drone_" + p.id())));
                }
                if (dm.outId() != 0) line(t, level, Component.translatable("jade.wmd.drone_out", dm.outId()));
            }
        } else if (be instanceof BombLineBlockEntity bl) {
            BombLineBlockEntity core = BombLine.coreOf(bl);
            if (core != null) {
                boolean run = core.stage() != BombLineBlockEntity.Stage.IDLE;
                lineStatus(t, level, "bomb_line", Component.translatable("item.wmd." + core.type().id), run, core.stage().name(), core.auto(), core.blocker());
                if (run && !t.contains("pr")) t.putInt("pr", Math.round(core.progress(level) * 1000.0F));
            }
        } else if (be instanceof DroneHiveBlockEntity hive) {
            for (int i = 0; i < DroneHiveBlockEntity.BAYS; i++) {
                DroneType d = hive.docked(i);
                if (d != null) line(t, level, Component.translatable("jade.wmd.hive.bay", i + 1, Component.translatable("item.wmd.drone_" + d.id()),
                        Math.min(100, hive.charge(i) * 100 / Balance.DRONE_CHARGE_FE)));
                else if (hive.out(i) != 0) line(t, level, Component.translatable("jade.wmd.hive.out", i + 1, hive.out(i)));
            }
        } else if (be instanceof DroneCatapultBlockEntity cat) {
            DroneType d = cat.drone();
            if (d != null) line(t, level, Component.translatable("jade.wmd.catapult.loaded", Component.translatable("item.wmd.drone_" + d.id())));
            if (cat.outId() != 0) line(t, level, Component.translatable("jade.wmd.drone_out", cat.outId()));
        } else if (be instanceof LaserTripodBlockEntity tri) {
            line(t, level, Component.translatable(tri.lasing() ? "jade.wmd.tripod.on" : "jade.wmd.tripod.off"));
        }
        BlockState bs = be.getBlockState();
        if (bs.getBlock() instanceof FluidValveBlock)
            line(t, level, Component.translatable("jade.wmd.valve", Component.translatable(bs.getValue(FluidValveBlock.OPEN) ? "jade.wmd.valve.open" : "jade.wmd.valve.shut"),
                    Component.translatable(bs.getValue(FluidValveBlock.ONE_WAY) ? "jade.wmd.valve.one_way" : "jade.wmd.valve.both")));
    }

    private static void lineStatus(CompoundTag t, Level level, String line, Component type, boolean run, String stage, boolean auto, int blocker) {
        if (run) line(t, level, Component.translatable("jade.wmd.line.building", type, Component.translatable("gui.wmd." + line + ".stage." + stage.toLowerCase(java.util.Locale.ROOT))));
        else if (blocker == 0) line(t, level, Component.translatable(auto ? "gui.wmd." + line + ".auto.ready" : "gui.wmd." + line + ".ready"));
        else line(t, level, Component.translatable("message.wmd." + line + ".cannot." + blocker));
        if (!run) line(t, level, Component.translatable("jade.wmd.line.next", type));
    }

    private static void line(CompoundTag t, Level level, Component c) {
        ListTag l = t.getList("lines", Tag.TAG_STRING);
        l.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c, level.registryAccess())));
        t.put("lines", l);
    }

    private static CompoundTag tank(Fluid f, int amount, int cap, boolean out, String unit) {
        CompoundTag c = new CompoundTag();
        c.putString("f", BuiltInRegistries.FLUID.getKey(f).toString());
        c.putInt("a", amount);
        c.putInt("c", cap);
        if (out) c.putBoolean("o", true);
        if (!"mB".equals(unit)) c.putString("u", unit);
        return c;
    }

    private static BlockPos controllerOf(Level level, BlockPos pos, BlockState state, BlockEntity be) {
        BlockPos c = null;
        if (be instanceof MachinePartBlockEntity p) c = p.getController();
        else if (state.getBlock() instanceof RadarPartBlock) c = RadarStructure.findController(level, pos);
        else if (state.getBlock() instanceof MultiblockPartBlock && state.getValue(MultiblockPartBlock.FORMED)) {
            GeneratorEngineBlockEntity g = GeneratorEngineBlockEntity.findOwner(level, pos);
            if (g != null) c = g.getBlockPos();
        } else if (state.getBlock() instanceof PadPlateBlock || state.getBlock() instanceof PadFillerBlock) c = PadStructure.findOwner(level, pos);
        return c != null ? c : pos;
    }

    private static IEnergyStorage energyCap(Level level, BlockPos pos) {
        IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (e != null) return e;
        for (Direction d : Direction.values()) {
            e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, d);
            if (e != null) return e;
        }
        return null;
    }

    @Override
    public void appendTooltip(ITooltip tip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag t = data.getCompound(KEY);
        IThemeHelper theme = IThemeHelper.get();

        if (t.contains("ctl")) {
            BlockState cs = accessor.getLevel().getBlockState(BlockPos.of(t.getLong("ctl")));
            if (!cs.isAir()) tip.replace(JadeIds.CORE_OBJECT_NAME, theme.title(cs.getBlock().getName()));
        }
        if (t.contains("say")) {
            int k = t.getInt("sayKind");
            Component word = Component.translatable(t.getString("say"));
            tip.add(Component.translatable("jade.wmd.status", k == 1 ? theme.success(word) : k == 2 ? theme.danger(word) : k == 3 ? theme.warning(word) : theme.info(word)));
        } else if (t.contains("st")) {
            int st = t.getInt("st");
            Component word = Component.translatable("gui.wmd.state." + stateKey(st));
            Component shown = st == 1 ? theme.success(word) : bad(st) ? theme.danger(word) : st == 3 || st == 4 ? theme.warning(word) : theme.info(word);
            tip.add(Component.translatable("jade.wmd.status", shown));
        }
        if (t.contains("tx")) tip.add(Component.translatable("jade.wmd.target", t.getInt("tx"), t.getInt("tz")));
        if (t.contains("load")) {
            int[] l = t.getIntArray("load");
            if (l.length == 3) tip.add(Component.translatable("gui.wmd.cruise.load", l[0], l[1], l[2]));
        }
        if (t.contains("info")) {
            try {
                Component c = Component.Serializer.fromJson(t.getString("info"), accessor.getLevel().registryAccess());
                if (c != null) tip.add(c);
            } catch (RuntimeException ignored) {}
        }
        for (Tag raw : t.getList("lines", Tag.TAG_STRING)) {
            try {
                Component c = Component.Serializer.fromJson(raw.getAsString(), accessor.getLevel().registryAccess());
                if (c != null) tip.add(c);
            } catch (RuntimeException ignored) {}
        }
        if (t.contains("camo")) {
            int c = t.getInt("camo");
            int[] sp = t.getIntArray("span");
            tip.add(c == 0 ? theme.warning(Component.translatable("jade.wmd.camo.none"))
                    : c == 2 ? theme.danger(Component.translatable("jade.wmd.camo.torn"))
                    : theme.success(Component.translatable("jade.wmd.camo.covering", sp.length == 2 ? sp[0] : 0, sp.length == 2 ? sp[1] : 0)));
        }
        if (t.contains("lab")) {
            tip.add(Component.translatable("jade.wmd.lab.warhead", Component.translatable("warhead.wmd." + t.getString("lab"))));
            tip.add(Component.translatable("jade.wmd.lab.stage", Component.translatable("gui.wmd.lab.stage." + t.getString("labStage"))));
        }
        int pr = t.contains("pr") ? t.getInt("pr") : -1;
        if (pr > 0 || (pr == 0 && t.getInt("st") == 1)) tip.add(Component.translatable("jade.wmd.progress", Math.round(pr / 10.0F)));
        if (t.contains("feMax")) {
            tip.add(Component.translatable("jade.wmd.power", String.format("%,d", t.getInt("fe")), String.format("%,d", t.getInt("feMax"))));
            if (t.contains("feUse")) tip.add(Component.translatable("jade.wmd.power_use", String.format("%,d", t.getInt("feUse"))));
        }
        for (Tag raw : t.getList("tanks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) raw;
            Fluid f = BuiltInRegistries.FLUID.get(ResourceLocation.parse(c.getString("f")));
            Component name = f == Fluids.EMPTY ? Component.translatable("jade.wmd.tank_empty") : new FluidStack(f, 1).getHoverName();
            String unit = c.contains("u") ? c.getString("u") : "mB";
            tip.add(Component.translatable(c.getBoolean("o") ? "jade.wmd.tank_out" : "jade.wmd.tank_in",
                    name, String.format("%,d", c.getInt("a")), String.format("%,d", c.getInt("c")), unit));
        }
        IElementHelper el = IElementHelper.get();
        for (Tag raw : t.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) raw;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(c.getString("id")));
            if (item == Items.AIR) continue;
            ItemStack st = new ItemStack(item, c.getInt("n"));
            tip.add(el.smallItem(st));
            tip.append(Component.translatable("jade.wmd.item", c.getInt("n"), st.getHoverName()));
        }
        if (t.contains("owner")) {
            int trusted = t.getInt("trusted");
            Component lock = t.getBoolean("pub") ? Component.translatable("jade.wmd.public")
                    : trusted > 0 ? Component.translatable("jade.wmd.private_trusted", trusted) : Component.translatable("jade.wmd.private");
            tip.add(Component.translatable("jade.wmd.owner", t.getString("owner"), lock));
        }
    }

    private static String stateKey(int st) {
        return switch (st) {
            case 1 -> "running"; case 2 -> "no_power"; case 3 -> "output_full"; case 4 -> "no_input"; case 5 -> "no_electrode";
            case 6 -> "no_water"; case 7 -> "no_impeller"; case 8 -> "no_deposit"; case 9 -> "wrong_bit"; case 10 -> "no_bit"; case 11 -> "no_blades"; case 12 -> "field_down";
            default -> "idle";
        };
    }

    private static boolean bad(int st) { return st == 2 || (st >= 5 && st <= 10); }
}
