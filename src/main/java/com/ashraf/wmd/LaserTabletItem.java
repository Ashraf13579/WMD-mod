package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class LaserTabletItem extends Item {

    public LaserTabletItem(Properties p) { super(p); }

    static final int A_REFRESH = 0, A_FIRE = 1, A_BURST = 3, A_ABORT = 4, A_SETUP = 5, A_CEASE = 6, A_UNLINK = 7, A_OPEN = 8;

    static final int K_PAD = 0, K_GUN = 1, K_DRONE = 2, K_EMP = 4, K_STORM = 5;

    static final int SRC_OWN = 0, SRC_XZ = 1, SRC_DESIG = 2, SRC_LASER = 3;

    static final int[] ROUNDS = { 1, 3, -1 };
    private static final Map<UUID, int[]> LAST = Statics.server(new HashMap<>());

    private static CompoundTag data(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static void put(ItemStack s, String key, long pos, String dim) {
        CompoundTag t = data(s);
        t.putLong(key, pos);
        t.putString(key + "Dim", dim);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    static BlockPos bound(ItemStack s, String key, Level level) {
        CompoundTag t = data(s);
        if (!t.contains(key) || !t.getString(key + "Dim").equals(level.dimension().location().toString())) return null;
        return BlockPos.of(t.getLong(key));
    }

    private static CompoundTag newSlot(int kind, long pos, String dim) {
        CompoundTag c = new CompoundTag();
        c.putInt("k", kind); c.putLong("p", pos); c.putString("d", dim);
        c.putInt("src", SRC_OWN); c.putInt("r", 0); c.putInt("t", 1);
        return c;
    }

    static ListTag slots(CompoundTag t) {
        if (t.contains("slots")) return t.getList("slots", Tag.TAG_COMPOUND);
        ListTag l = new ListTag();
        long[] ps = t.contains("pads") ? t.getLongArray("pads") : t.contains("launcher") ? new long[] { t.getLong("launcher") } : new long[0];
        String pd = t.contains("pads") ? t.getString("padsDim") : t.getString("launcherDim");
        for (long p : ps) if (l.size() < Balance.TABLET_SLOTS) l.add(newSlot(K_PAD, p, pd));
        if (t.contains("hive") && l.size() < Balance.TABLET_SLOTS) l.add(newSlot(K_DRONE, t.getLong("hive"), t.getString("hiveDim")));
        return l;
    }

    private static void saveSlots(ItemStack s, ListTag l) {
        CompoundTag t = data(s);
        for (String k : new String[] { "pads", "padsDim", "launcher", "launcherDim", "hive", "hiveDim" }) t.remove(k);
        t.put("slots", l);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    private static String toggle(ItemStack s, int kind, long pos, String dim) {
        ListTag l = slots(data(s));
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            if (c.getLong("p") == pos && c.getString("d").equals(dim)) { l.remove(i); saveSlots(s, l); return "message.wmd.tablet.unlinked"; }
        }
        if (l.size() >= Balance.TABLET_SLOTS) return "message.wmd.tablet.slots_full";
        l.add(newSlot(kind, pos, dim));
        saveSlots(s, l);
        return "message.wmd.tablet.linked";
    }

    private static BlockEntity machineAt(Level level, BlockPos p) {
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof MachinePartBlockEntity part && part.getController() != null) be = level.getBlockEntity(part.getController());
        return be;
    }

    private static boolean droneBase(BlockEntity be) {
        return be instanceof DroneHiveBlockEntity || be instanceof BigDroneSite site && (site instanceof DroneCatapultBlockEntity || site.isHangarModule());
    }

    private static int kindOf(BlockEntity be) {
        if (be instanceof LaunchTerminalBlockEntity) return K_PAD;
        if (be instanceof ArtilleryGunBlockEntity) return K_GUN;
        if (droneBase(be)) return K_DRONE;
        if (be instanceof PulseEmpBlockEntity) return K_EMP;
        if (be instanceof StormGeneratorBlockEntity) return K_STORM;
        return -1;
    }

    private static BlockPos anchor(BlockEntity be) { return be instanceof BigDroneSite site && !(be instanceof DroneHiveBlockEntity) ? site.sitePos() : be.getBlockPos(); }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockEntity be = machineAt(level, ctx.getClickedPos());
        int kind = kindOf(be);
        if (kind < 0 && !(be instanceof LaserTripodBlockEntity)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Owned o = (Owned) be;
        if (o.getOwner() != null && !Access.trusts(o, player.getUUID())) { player.displayClientMessage(Component.translatable("message.wmd.tablet.not_yours"), true); return InteractionResult.FAIL; }
        String dim = level.dimension().location().toString();
        if (be instanceof LaserTripodBlockEntity tri) {
            put(stack, "tripod", tri.getBlockPos().asLong(), dim);
            player.displayClientMessage(Component.translatable("message.wmd.tablet.bound_tripod", tri.getBlockPos().toShortString()), true);
        } else {
            if (be instanceof LaunchTerminalBlockEntity term && term.getTier() > 3) { player.displayClientMessage(Component.translatable("message.wmd.tablet.no_t4"), true); return InteractionResult.FAIL; }
            BlockPos at = anchor(be);
            String msg = toggle(stack, kind, at.asLong(), dim);
            player.displayClientMessage(Component.translatable(msg, be.getBlockState().getBlock().getName(), at.toShortString(), Balance.TABLET_SLOTS), true);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, status(sp, s, hand, ""));
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    private static LaunchTerminalBlockEntity term(ServerPlayer p, long pos) {
        ServerLevel level = p.serverLevel();
        BlockPos lp = BlockPos.of(pos);
        LaunchTerminalBlockEntity term = level.isLoaded(lp) && level.getBlockEntity(lp) instanceof LaunchTerminalBlockEntity t ? t : null;
        if (term != null && term.getOwner() != null && !Access.trusts(term, p.getUUID())) term = null;
        return term;
    }

    private static BlockEntity machine(ServerPlayer p, CompoundTag c) {
        ServerLevel level = p.serverLevel();
        BlockPos at = BlockPos.of(c.getLong("p"));
        if (!level.isLoaded(at)) return null;
        BlockEntity be = level.getBlockEntity(at);
        if (kindOf(be) != c.getInt("k")) return null;
        if (((Owned) be).getOwner() != null && !Access.trusts((Owned) be, p.getUUID())) return null;
        return be;
    }

    private static LaserTripodBlockEntity tripod(ServerPlayer p, ItemStack s) {
        ServerLevel level = p.serverLevel();
        BlockPos tp = bound(s, "tripod", level);
        LaserTripodBlockEntity tri = tp != null && level.isLoaded(tp) && level.getBlockEntity(tp) instanceof LaserTripodBlockEntity x ? x : null;
        if (tri != null && tri.getOwner() != null && !Access.trusts(tri, p.getUUID())) tri = null;
        return tri;
    }

    private static int[] target(ServerPlayer p, ItemStack s, CompoundTag c, BlockEntity be, String[] why) {
        why[0] = null;
        switch (c.getInt("src")) {
            case SRC_XZ -> { return new int[] { c.getInt("x"), c.getInt("z") }; }
            case SRC_DESIG -> {
                UUID owner = ((Owned) be).getOwner() != null ? ((Owned) be).getOwner() : p.getUUID();
                CcTarget t = CcTarget.of(owner);
                if (t == null) { why[0] = "gui.wmd.tablet.why.no_desig"; return null; }
                return new int[] { t.x(), t.z() };
            }
            case SRC_LASER -> {
                LaserTripodBlockEntity tri = tripod(p, s);
                if (tri == null) { why[0] = "gui.wmd.tablet.why.tripod"; return null; }
                LaserSpots.Spot spot = LaserSpots.get(p.serverLevel(), tri.spotId());
                if (spot == null) { why[0] = "gui.wmd.tablet.why.dark"; return null; }
                return new int[] { (int) Math.floor(spot.pos().x), (int) Math.floor(spot.pos().z) };
            }
            default -> {
                if (be instanceof LaunchTerminalBlockEntity term) {
                    if (term.isTargetSet()) return new int[] { term.getTargetX(), term.getTargetZ() };
                    why[0] = "gui.wmd.tablet.why.no_target";
                } else if (be instanceof ArtilleryGunBlockEntity g) {
                    if (g.hasTarget()) return new int[] { g.targetX(), g.targetZ() };
                    why[0] = "gui.wmd.tablet.why.own_gun";
                } else if (be instanceof StormGeneratorBlockEntity sg) {
                    if (sg.hasTarget()) return new int[] { sg.targetX(), sg.targetZ() };
                    why[0] = "gui.wmd.tablet.why.own_storm";
                } else if (be instanceof BigDroneSite site && !(be instanceof DroneHiveBlockEntity)) {
                    if (site.mission().hasTarget()) return new int[] { site.mission().tx(), site.mission().tz() };
                    why[0] = "gui.wmd.tablet.why.own_site";
                }
                return null;
            }
        }
    }

    private static String padWhy(ServerPlayer p, LaunchTerminalBlockEntity term, boolean laser, String targetWhy) {
        if (term == null) return "gui.wmd.tablet.why.gone";
        if (term.getTier() > 3) return "gui.wmd.tablet.why.t4";
        if (!Reach.within(p, term.getBlockPos(), Balance.TABLET_RANGE)) return "gui.wmd.tablet.why.range";
        if (term.getLoadedBuild() == null) return "gui.wmd.tablet.why.empty";
        if (term.getStage() != LaunchTerminalBlockEntity.Stage.READY) return "gui.wmd.tablet.why.not_ready";
        if (laser && !MissileBuild.SEEK_LASER.equals(term.getLoadedBuild().seeker())) return "gui.wmd.tablet.why.seeker";
        return targetWhy;
    }

    private static int pointFar(DroneHiveBlockEntity h, int tier, int[] xz) {
        int r = tier == 1 ? Balance.DRONE_T1_RANGE : Balance.DRONE_T2_RANGE;
        Vec3 w = SableBridge.worldPos(h.getLevel(), h.getBlockPos());
        return Math.hypot(xz[0] + 0.5 - w.x, xz[1] + 0.5 - w.z) > r ? r : 0;
    }

    private static int readyCount(DroneHiveBlockEntity h, DroneType t) {
        int n = 0;
        for (int i = 0; i < DroneHiveBlockEntity.BAYS; i++) if (h.out(i) == 0 && h.docked(i) == t && h.charge(i) >= Balance.DRONE_CHARGE_FE) n++;
        return n;
    }

    private static CompoundTag line(String key, Object... args) {
        CompoundTag t = new CompoundTag();
        t.putString("k", key == null ? "" : key);
        ListTag a = new ListTag();
        for (Object o : args) a.add(StringTag.valueOf(String.valueOf(o)));
        t.put("a", a);
        return t;
    }

    private static CompoundTag row(ServerPlayer p, ItemStack s, CompoundTag c, int i) {
        ServerLevel level = p.serverLevel();
        int kind = c.getInt("k");
        BlockPos at = BlockPos.of(c.getLong("p"));
        CompoundTag r = new CompoundTag();
        r.putInt("k", kind);
        for (String k : new String[] { "src", "x", "z", "r", "t" }) r.putInt(k, c.getInt(k));
        r.putString("act", kind == K_DRONE ? "gui.wmd.tablet.launch" : "gui.wmd.tablet.fire");
        r.putInt("aa", A_FIRE);
        r.putBoolean("cf", true);
        String off = null;
        BlockEntity be = null;
        if (!c.getString("d").equals(level.dimension().location().toString())) off = "dim";
        else if (!Reach.within(p, at, Balance.TABLET_RANGE)) off = "far";
        else {
            boolean held = TabletChunks.hold(level, at, Balance.TABLET_HOLD_TICKS, false);
            if (!level.isLoaded(at)) off = held ? "waking" : "noload";
            else {
                BlockEntity raw = level.getBlockEntity(at);
                if (kindOf(raw) != kind) off = "gone";
                else if (((Owned) raw).getOwner() != null && !Access.trusts((Owned) raw, p.getUUID())) off = "not_yours";
                else be = raw;
            }
        }
        if (off != null) {
            r.put("l1", line("gui.wmd.tablet.row_off", i + 1, "#gui.wmd.tablet.kind." + kind, at.getX(), at.getZ()));
            r.put("l2", line("gui.wmd.tablet.off." + off));
            r.put("l3", line(""));
            r.put("why", line("gui.wmd.tablet.off." + off));
            r.putString("own", "gui.wmd.tablet.src.own" + kind);
            return r;
        }
        r.putBoolean("ok", true);
        int dist = (int) Math.round(Math.sqrt(SableBridge.distSqWithShips(level, p.position(), at.getCenter())));
        String[] tw = new String[1];
        int[] xz = target(p, s, c, be, tw);
        CompoundTag why = null, l3;
        boolean good;
        if (be instanceof LaunchTerminalBlockEntity term) {
            r.putString("own", "gui.wmd.tablet.src.own0");
            r.put("l1", line("gui.wmd.tablet.pad", i + 1, term.getTier(), at.getX(), at.getZ(), dist));
            MissileBuild b = term.getLoadedBuild();
            String wh = b == null || b.warheadType() == null || b.warheadType().isEmpty() ? "?" : "#warhead.wmd." + b.warheadType();
            r.put("l2", b == null ? line("gui.wmd.tablet.empty")
                    : line(MissileBuild.SEEK_LASER.equals(b.seeker()) ? "gui.wmd.tablet.missile_laser" : "gui.wmd.tablet.missile_no_seeker", b.calibre(), wh));
            String w = padWhy(p, term, c.getInt("src") == SRC_LASER, xz == null ? tw[0] : null);
            if (w != null) why = line(w);
            good = w == null;
            l3 = good ? line("gui.wmd.tablet.target", xz[0], xz[1]) : "gui.wmd.tablet.why.not_ready".equals(w) ? line("screen.wmd.terminal.hint." + term.hint()) : why;
        } else if (be instanceof ArtilleryGunBlockEntity g) {
            r.putString("own", "gui.wmd.tablet.src.own1");
            r.put("l1", line("gui.wmd.tablet.row", i + 1, "#" + be.getBlockState().getBlock().getDescriptionId(), at.getX(), at.getZ(), dist));
            int pick = g.picked(), type = Math.max(0, g.firedType(pick));
            r.put("l2", line("gui.wmd.tablet.gun_ammo", "#item.wmd." + Artillery.shellId(g.gun(), type), g.roundsOf(pick), "#gui.wmd.tablet.rounds." + Math.floorMod(c.getInt("r"), ROUNDS.length)));
            if (g.mission() != 0) {
                r.putString("act", "gui.wmd.tablet.cease"); r.putInt("aa", A_CEASE); r.putBoolean("cf", false);
                good = true;
                l3 = line("gui.wmd.tablet.gun_busy", "#gui.wmd.art.st." + g.gunStatus(), g.targetX(), g.targetZ());
            } else {
                if (xz == null) why = line(tw[0]);
                else { int st = g.checkFor(xz[0], xz[1], pick); if (st != ArtilleryGunBlockEntity.S_READY) why = line("gui.wmd.art.st." + st); }
                good = why == null;
                l3 = good ? line("gui.wmd.tablet.target", xz[0], xz[1]) : why;
            }
        } else if (be instanceof DroneHiveBlockEntity h) {
            r.putString("own", "gui.wmd.tablet.src.own2");
            int tier = c.getInt("t") == 2 ? 2 : 1, outN = 0;
            for (int b = 0; b < DroneHiveBlockEntity.BAYS; b++) if (h.out(b) != 0) outN++;
            r.putBoolean("recall", outN > 0);
            r.putBoolean("open", true);
            r.put("l1", line("gui.wmd.tablet.row", i + 1, "#block.wmd.drone_hive", at.getX(), at.getZ(), dist));
            r.put("l2", line("gui.wmd.tablet.hive_bays", readyCount(h, DroneType.T1), readyCount(h, DroneType.T2), outN));
            boolean point = c.getInt("src") != SRC_OWN;
            int far = point && xz != null ? pointFar(h, tier, xz) : 0;
            if (h.readyBay(tier == 1 ? DroneType.T1 : DroneType.T2) < 0) why = line("gui.wmd.tablet.why.no_drone", tier);
            else if (point && xz == null) why = line(tw[0]);
            else if (far > 0) why = line("gui.wmd.tablet.why.point_far", far);
            good = why == null;
            l3 = !good ? why : point ? line("gui.wmd.tablet.hive_point", tier, xz[0], xz[1]) : line("gui.wmd.tablet.hive_own", tier);
        } else if (be instanceof PulseEmpBlockEntity emp) {
            r.putString("own", "gui.wmd.tablet.src.own4");
            r.put("l1", line("gui.wmd.tablet.row", i + 1, "#block.wmd.pulse_emp", at.getX(), at.getZ(), dist));
            int left = emp.coolSecondsLeft();
            r.put("l2", emp.empState() == PulseEmpBlockEntity.S_COOL ? line("gui.wmd.tablet.emp_cool", left / 60, String.format("%02d", left % 60))
                    : line("gui.wmd.tablet.emp_charge", emp.chargePermille() / 10));
            if (emp.empState() != PulseEmpBlockEntity.S_READY) why = line("gui.wmd.pemp.st." + emp.empStatus());
            good = why == null;
            l3 = good ? line("gui.wmd.tablet.emp_cone", emp.contacts(), emp.inCone()) : why;
        } else if (be instanceof StormGeneratorBlockEntity sg) {
            r.putString("own", "gui.wmd.tablet.src.own5");
            r.put("l1", line("gui.wmd.tablet.row", i + 1, "#block.wmd.storm_generator", at.getX(), at.getZ(), dist));
            int left = sg.secondsLeft();
            r.put("l2", sg.phase() == StormGeneratorBlockEntity.P_IDLE ? line("gui.wmd.tablet.storm_charge", sg.chargePermille() / 10, sg.radius())
                    : line("gui.wmd.tablet.storm_busy", "#gui.wmd.storm.st." + sg.stormStatus(), left / 60, String.format("%02d", left % 60)));
            if (sg.phase() == StormGeneratorBlockEntity.P_BUILD || sg.phase() == StormGeneratorBlockEntity.P_STORM) {
                r.putString("act", "gui.wmd.tablet.cease"); r.putInt("aa", A_CEASE); r.putBoolean("cf", true);
                good = true;
                l3 = line("gui.wmd.tablet.storm_over", sg.targetX(), sg.targetZ());
            } else {
            if (xz == null) why = line(tw[0]);
            else if (sg.stormStatus() != StormGeneratorBlockEntity.ST_READY && sg.stormStatus() != StormGeneratorBlockEntity.ST_RANGE
                    && sg.stormStatus() != StormGeneratorBlockEntity.ST_NO_TARGET) why = line("gui.wmd.storm.st." + sg.stormStatus());
            good = why == null;
            l3 = good ? line("gui.wmd.tablet.target", xz[0], xz[1]) : why;
            }
        } else {
            BigDroneSite site = (BigDroneSite) be;
            r.putString("own", "gui.wmd.tablet.src.own3");
            r.putBoolean("open", true);
            r.put("l1", line("gui.wmd.tablet.row", i + 1, "#" + be.getBlockState().getBlock().getDescriptionId(), at.getX(), at.getZ(), dist));
            DroneType dt = null;
            for (int k = 0; k < site.droneSlots().getSlots() && dt == null; k++) dt = DroneType.of(site.droneSlots().getStackInSlot(k));
            int bombs = 0;
            for (int k = 0; k < site.bombSlots().getSlots(); k++) if (!site.bombSlots().getStackInSlot(k).isEmpty()) bombs++;
            r.put("l2", line("gui.wmd.tablet.site_drone", dt == null ? "#gui.wmd.tablet.none" : "#item.wmd." + dt.mesh, bombs));
            if (site.outId() != 0) {
                r.putString("act", "gui.wmd.tablet.recall"); r.putInt("aa", A_CEASE); r.putBoolean("cf", false);
                good = true;
                l3 = line("gui.wmd.tablet.site_out", site.outId());
            } else {
                int st = site.siteStatus();
                if (xz == null) why = line(tw[0]);
                else if (st != BigDroneSite.S_READY && st != BigDroneSite.S_NO_TARGET) why = line("gui.wmd.big_drone.st." + st);
                good = why == null;
                l3 = good ? line("gui.wmd.tablet.target", xz[0], xz[1]) : why;
            }
        }
        r.put("why", why == null ? line("") : why);
        r.put("l3", l3 == why && why != null ? why.copy() : l3);
        r.putBoolean("good", good);
        return r;
    }

    static TabletInfoPayload status(ServerPlayer p, ItemStack s, InteractionHand hand, String msg) {
        ServerLevel level = p.serverLevel();
        CompoundTag d = new CompoundTag();
        LaserTripodBlockEntity tri = tripod(p, s);
        LaserSpots.Spot spot = tri == null ? null : LaserSpots.get(level, tri.spotId());
        ListTag rows = new ListTag();
        ListTag sl = slots(data(s));
        for (int i = 0; i < sl.size() && i < Balance.TABLET_SLOTS; i++) rows.add(row(p, s, sl.getCompound(i), i));
        d.put("rows", rows);
        if (tri != null) {
            d.putInt("trx", tri.getBlockPos().getX()); d.putInt("trz", tri.getBlockPos().getZ());
            if (spot != null) { d.putBoolean("lit", true); d.putInt("sx", (int) Math.floor(spot.pos().x)); d.putInt("sy", (int) Math.floor(spot.pos().y)); d.putInt("sz", (int) Math.floor(spot.pos().z)); }
        }

        ListTag fl = new ListTag();
        FlightManager fm = FlightManager.get(level);
        long now = level.getGameTime();
        for (Flight f : fm.flights()) {
            if (fl.size() >= Balance.TABLET_FLIGHTS) break;
            if (!f.isOwnedBy(p.getUUID(), p.getGameProfile().getName()) || !FlightManager.remoteCommandable(f)) continue;
            CompoundTag r = new CompoundTag();
            r.putLong("id", f.id());
            r.putInt("tier", f.tier());
            r.putString("wh", f.build().warheadType());
            r.putInt("tx", f.tx()); r.putInt("tz", f.tz());
            r.putInt("left", (int) Math.max(0L, (f.endTime() - now + 19) / 20));
            r.putBoolean("dive", f.inDive(now));
            String bw = fm.burstWhy(level, f);
            r.putString("bwhy", bw == null ? "" : bw);
            fl.add(r);
        }
        d.put("flights", fl);
        d.putString("msg", msg == null ? "" : msg);
        return new TabletInfoPayload(hand == InteractionHand.MAIN_HAND, d);
    }

    static String act(ServerPlayer p, InteractionHand hand, int action, int index, long flight, int x, int z, int arg) {
        ItemStack s = p.getItemInHand(hand);
        if (!(s.getItem() instanceof LaserTabletItem) || action == A_REFRESH) return "";
        if (AirframeRecords.tooSoon(LAST, p, 0, Balance.TABLET_PRESS_GAP)) return "";
        ServerLevel level = p.serverLevel();
        if (action == A_BURST || action == A_ABORT) {
            FlightManager fm = FlightManager.get(level);
            Flight f = fm.find(flight);
            if (f == null || !f.isOwnedBy(p.getUUID(), p.getGameProfile().getName()) || !FlightManager.remoteCommandable(f)) return "gui.wmd.tablet.ans.no_flight";
            if (action == A_BURST) {
                String why = fm.burstWhy(level, f);
                if (why != null) return why;
                return fm.remoteBurst(level, f) ? "gui.wmd.tablet.ans.burst" : "gui.wmd.tablet.ans.no_flight";
            }
            return fm.remoteAbort(level, f) ? "gui.wmd.tablet.ans.aborted" : "gui.wmd.tablet.ans.no_flight";
        }
        ListTag l = slots(data(s));
        if (index < 0 || index >= l.size() || index >= Balance.TABLET_SLOTS) return "gui.wmd.tablet.why.gone";
        CompoundTag c = l.getCompound(index);
        if (action == A_UNLINK) { l.remove(index); saveSlots(s, l); return "gui.wmd.tablet.ans.unlinked"; }
        if (action == A_SETUP) {
            int src = arg & 0xF, rc = (arg >> 4) & 0xF, tier = (arg >> 8) & 0xF;
            c.putInt("src", src >= SRC_OWN && src <= SRC_LASER ? src : SRC_OWN);
            c.putInt("x", net.minecraft.util.Mth.clamp(x, -29_999_000, 29_999_000)); c.putInt("z", net.minecraft.util.Mth.clamp(z, -29_999_000, 29_999_000));
            c.putInt("r", rc < ROUNDS.length ? rc : 0);
            c.putInt("t", tier == 2 ? 2 : 1);
            saveSlots(s, l);
            return "gui.wmd.tablet.ans.saved";
        }
        if (action != A_FIRE && action != A_CEASE && action != A_OPEN) return "";
        if (!c.getString("d").equals(level.dimension().location().toString())) return "gui.wmd.tablet.off.dim";
        BlockPos at = BlockPos.of(c.getLong("p"));
        if (!Reach.within(p, at, Balance.TABLET_RANGE)) return "gui.wmd.tablet.off.far";
        if (action == A_OPEN) return openSite(p, c);
        if (!level.isLoaded(at) || level.getBlockEntity(at) == null) {
            if (!TabletChunks.hold(level, at, Balance.TABLET_HOLD_TICKS, false)) return "gui.wmd.tablet.off.noload";
            final ItemStack snap = s.copy();
            final CompoundTag slot = c.copy();
            TabletChunks.wake(p, at, sp -> {
                String ans = order(sp, snap, slot, action);
                if (!ans.isEmpty()) sp.displayClientMessage(Component.translatable(ans), true);
            });
            return "gui.wmd.tablet.ans.waking";
        }
        return order(p, s, c, action);
    }

    private static String order(ServerPlayer p, ItemStack s, CompoundTag c, int action) {
        ServerLevel level = p.serverLevel();
        BlockPos at = BlockPos.of(c.getLong("p"));
        if (!c.getString("d").equals(level.dimension().location().toString())) return "gui.wmd.tablet.off.dim";
        if (!Reach.within(p, at, Balance.TABLET_RANGE)) return "gui.wmd.tablet.off.far";
        BlockEntity be = machine(p, c);
        if (be == null) return "gui.wmd.tablet.why.gone";
        String[] tw = new String[1];
        String name = p.getGameProfile().getName();
        if (be instanceof LaunchTerminalBlockEntity) {
            if (action != A_FIRE) return "";
            LaunchTerminalBlockEntity term = term(p, c.getLong("p"));
            int[] xz = term == null ? null : target(p, s, c, term, tw);
            return firePad(p, term, c.getInt("src") == SRC_LASER, xz, tw[0]);
        }
        if (be instanceof ArtilleryGunBlockEntity g) {
            if (action == A_CEASE) { g.act(ArtilleryGunBlockEntity.A_CEASE, 0, 0, p); return "gui.wmd.tablet.ans.ceased"; }
            if (g.mission() != 0) return "gui.wmd.art.st." + g.gunStatus();
            int[] xz = target(p, s, c, g, tw);
            if (xz == null) return tw[0];
            int st = g.order(xz[0], xz[1], g.picked(), ROUNDS[Math.floorMod(c.getInt("r"), ROUNDS.length)], name);
            boolean ok = st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING || st == ArtilleryGunBlockEntity.S_FIRING;
            if (!ok) return "gui.wmd.art.st." + st;
            TabletChunks.hold(level, at, Balance.TABLET_HOLD_TICKS, true);
            p.displayClientMessage(Component.translatable("message.wmd.tablet.gun_fired", be.getBlockState().getBlock().getName(), xz[0], xz[1]), true);
            return "gui.wmd.tablet.ans.gun_fired";
        }
        if (be instanceof DroneHiveBlockEntity h) {
            if (action == A_CEASE) return h.recallAll(p) > 0 ? "gui.wmd.tablet.ans.recalled" : "gui.wmd.tablet.ans.none_out";
            int tier = c.getInt("t") == 2 ? 2 : 1;
            boolean point = c.getInt("src") != SRC_OWN;
            int[] xz = point ? target(p, s, c, h, tw) : null;
            if (point && xz == null) return tw[0];
            if (point && pointFar(h, tier, xz) > 0) return "gui.wmd.tablet.why.point_far_short";
            Component no = h.tabletLaunch(tier == 1 ? DroneType.T1 : DroneType.T2, p, point, point ? xz[0] : 0, point ? xz[1] : 0);
            if (no != null) { p.displayClientMessage(no, true); return "gui.wmd.tablet.ans.refused"; }
            TabletChunks.hold(level, at, Balance.TABLET_HOLD_TICKS, false);
            return "gui.wmd.tablet.ans.launched";
        }
        if (be instanceof PulseEmpBlockEntity emp) {
            if (action != A_FIRE) return "";
            if (!emp.remoteFire()) return "gui.wmd.pemp.st." + emp.empStatus();
            TabletChunks.hold(level, at, Balance.TABLET_HOLD_TICKS, true);
            return "gui.wmd.tablet.ans.emp_fired";
        }
        if (be instanceof StormGeneratorBlockEntity sg) {
            if (action == A_CEASE) { sg.abort(); return "gui.wmd.tablet.ans.storm_stopped"; }
            if (action != A_FIRE) return "";
            int[] xz = target(p, s, c, sg, tw);
            if (xz == null) return tw[0];
            int st = sg.order(xz[0], xz[1], name);
            if (st != StormGeneratorBlockEntity.ST_BUILD) return "gui.wmd.storm.st." + st;
            TabletChunks.hold(level, at, Balance.TABLET_HOLD_TICKS, true);
            return "gui.wmd.tablet.ans.storm_fired";
        }
        BigDroneSite site = (BigDroneSite) be;
        if (action == A_CEASE) { if (site.outId() == 0) return "gui.wmd.tablet.ans.none_out"; site.act(BigDroneSite.A_RECALL, null, p); return "gui.wmd.tablet.ans.recalled"; }
        if (site.outId() != 0) return "gui.wmd.big_drone.st." + BigDroneSite.S_OUT;
        int[] xz = target(p, s, c, be, tw);
        if (xz == null) return tw[0];
        if (c.getInt("src") != SRC_OWN) site.setMission(site.mission().withTarget(xz[0], xz[1]));
        site.act(BigDroneSite.A_LAUNCH, null, p);
        if (site.outId() == 0) return "gui.wmd.tablet.ans.refused";
        TabletChunks.hold(level, at, Balance.TABLET_HOLD_TICKS, false);
        return "gui.wmd.tablet.ans.launched";
    }

    private static String firePad(ServerPlayer p, LaunchTerminalBlockEntity term, boolean laser, int[] xz, String targetWhy) {
        String why = padWhy(p, term, laser, xz == null ? (targetWhy == null ? "gui.wmd.tablet.why.no_target" : targetWhy) : null);
        if (why != null) return why;
        int x = xz[0], z = xz[1];
        Component no = term.remoteLaunch(p.getUUID(), p.getGameProfile().getName(), x, z);
        p.displayClientMessage(no != null ? no : Component.translatable("message.wmd.tablet.fired", x, z), true);
        return no != null ? "gui.wmd.tablet.ans.refused" : "gui.wmd.tablet.ans.fired";
    }

    private static String openSite(ServerPlayer sp, CompoundTag c) {
        BlockPos at = BlockPos.of(c.getLong("p"));
        BlockEntity be = machine(sp, c);
        if (be == null) {
            if (sp.serverLevel().isLoaded(at)) return "gui.wmd.tablet.why.gone";
            TabletChunks.hold(sp.serverLevel(), at, Balance.TABLET_HOLD_TICKS, false);
            return "gui.wmd.tablet.ans.waking_open";
        }
        double range = be instanceof BigDroneSite s && !(be instanceof DroneHiveBlockEntity) ? s.siteRange() : Balance.DRONE_T2_RANGE;

        if (SableBridge.worldPos(sp.level(), at).distanceToSqr(sp.position()) > range * range) return "gui.wmd.tablet.why.open_far";
        if (be instanceof DroneHiveBlockEntity h) DroneHiveMenu.open(sp, h, true);
        else if (be instanceof BigDroneSite s) BigDroneMenu.open(sp, s, true);
        return "";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = data(stack);
        tooltip.add(Component.translatable("tooltip.wmd.tablet", Balance.TABLET_SLOTS, Balance.TABLET_RANGE).withStyle(ChatFormatting.GRAY));
        ListTag l = slots(t);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            tooltip.add(Component.translatable("tooltip.wmd.tablet.slot", i + 1, Component.translatable("gui.wmd.tablet.kind." + c.getInt("k")), BlockPos.of(c.getLong("p")).toShortString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (t.contains("tripod")) tooltip.add(Component.translatable("tooltip.wmd.tablet.tripod", BlockPos.of(t.getLong("tripod")).toShortString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
