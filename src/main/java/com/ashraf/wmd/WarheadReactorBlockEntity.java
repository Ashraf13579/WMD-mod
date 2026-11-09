package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class WarheadReactorBlockEntity extends BlockEntity implements GeoBlockEntity, Owned {

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public static final String[][] GAUGES = {
            { "heat", "flow" }, { "heat", "gel_mix" }, { "pressure", "flow" }, { "charge", "tuning" },
            { "pressure", "heat", "fuel_mix" }, { "feed_rate", "timing", "pressure" }, { "heat", "press", "alignment" }, { "flow", "static_charge", "humidity" },
            { "heat", "pressure", "hardening", "alignment" }, { "charge", "coil_temp", "discharge_rate", "shield" }, { "heat", "pressure", "shield", "criticality" },
            { "tuning", "timing" } };

    public static final int R_NONE = 0, R_BAD = 1, R_NORMAL = 2, R_EXCELLENT = 3, R_SPOILED = 4;

    private int type, tick, damage, greenTicks, redTicks, lastResult;
    private boolean running;

    private boolean autoRun;
    final float[] g = { 0.5F, 0.5F, 0.5F, 0.5F };
    final int[] turns = new int[4];
    private final float[] vel = new float[4];

    private final net.minecraft.world.SimpleContainer held = new net.minecraft.world.SimpleContainer(8);

    final float[] shownGauge = { 0.5F, 0.5F, 0.5F, 0.5F };
    final float[] valveAngle = new float[4];
    float spin; int clientTicks;

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public WarheadReactorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.WARHEAD_REACTOR_BE.get(), pos, state);
    }

    public WarheadType type() { return WarheadType.ofOrdinal(type); }
    public int typeIndex() { return type; }
    public int gaugeCount() { return Math.min(4, type().tier + 1); }
    public boolean isRunning() { return running; }
    public int damage() { return damage; }
    public int greenTicks() { return greenTicks; }
    public int lastResult() { return lastResult; }
    public int redTicks() { return redTicks; }
    public int tick() { return tick; }
    public double greenHalf() { return Balance.LAB_GREEN_HALF[type().tier - 1]; }

    public int statusOf(float value) {
        double d = Math.abs(value - 0.5);
        return d <= greenHalf() ? 0 : d >= Balance.LAB_RED_ZONE ? 2 : 1;
    }

    private Vec3 centre() {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        return SableBridge.toWorld(level, Vec3.atCenterOf(Machines.toWorld(worldPosition, facing, Machines.mount(getBlockState()), 0, 1, 1)));
    }

    private Vec3 frontSpot() {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        return Vec3.atBottomCenterOf(Machines.toWorld(worldPosition, facing, Machines.mount(getBlockState()), 0, 0, -1)).add(0, 0.2, 0);
    }

    private LabModuleBlockEntity line() { return level == null ? null : LabLine.coreOf(this); }

    public void lineReady(int typeIndex) {
        type = typeIndex;
        for (int i = 0; i < 4; i++) { g[i] = 0.5F; vel[i] = 0; }
        sync();
        tell(Component.translatable("message.wmd.reactor.ready"));
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void tell(Component c) {
        if (level == null) return;
        Vec3 m = centre();
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(9))) p.displayClientMessage(c, true);
    }

    public void status(Player player) {
        net.minecraft.network.chat.MutableComponent gauges = Component.empty();
        String[] gn = GAUGES[Mth.clamp(type, 0, GAUGES.length - 1)];
        for (int i = 0; i < gn.length; i++) gauges.append(i == 0 ? "" : ", ").append(Component.translatable("gauge.wmd." + gn[i]));
        Component name = Component.translatable("warhead.wmd." + type().id);
        if (running) player.displayClientMessage(Component.translatable("message.wmd.reactor.status", name, gauges, damage, Balance.LAB_MAX_DAMAGE,
                Component.translatable("message.wmd.reactor.running")), true);
        else player.displayClientMessage(Component.translatable("message.wmd.reactor.idle_status", name, needsLine(), gauges, damage, Balance.LAB_MAX_DAMAGE), true);
    }

    private Component needsLine() {
        net.minecraft.network.chat.MutableComponent c = Component.empty();
        boolean first = true;
        for (LabRecipes.Need n : LabRecipes.needs(type())) {
            c.append(first ? "" : ", ").append(Math.min(n.count(), LabRecipes.have(held, n)) + "/" + n.count() + " ").append(Component.translatable("lab.wmd.need." + n.key()));
            first = false;
        }
        return c;
    }

    public void load(Player player) {
        net.minecraft.world.item.ItemStack hand = player.getMainHandItem();
        if (line() != null) { status(player); return; }
        if (running || hand.isEmpty()) { status(player); return; }
        for (LabRecipes.Need n : LabRecipes.needs(type())) {
            if (!n.test().test(hand)) continue;
            int room = n.count() - LabRecipes.have(held, n);
            if (room <= 0) continue;
            int take = Math.min(room, hand.getCount());
            net.minecraft.world.item.ItemStack in = hand.copyWithCount(take);
            if (!player.getAbilities().instabuild) hand.shrink(take);
            held.addItem(in);
            level.playSound(null, worldPosition, WmdSounds.WR_INSERT.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
            sync();
            player.displayClientMessage(Component.translatable("message.wmd.reactor.loaded", needsLine()), true);
            return;
        }
        status(player);
    }

    void refund() {
        Vec3 f = frontSpot();
        for (int i = 0; i < held.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = held.getItem(i);
            if (!s.isEmpty()) level.addFreshEntity(new ItemEntity(level, f.x, f.y, f.z, s.copy(), 0, 0.15, 0));
        }
        held.clearContent();
    }

    public void cycleType(Player player, boolean back) {
        if (line() != null) { player.displayClientMessage(Component.translatable("message.wmd.reactor.set_on_core"), true); return; }
        if (running) { player.displayClientMessage(Component.translatable("message.wmd.reactor.busy"), true); return; }
        int n = WarheadType.values().length;
        refund();
        do type = (type + (back ? n - 1 : 1)) % n; while (WarheadType.ofOrdinal(type).bombOnly() || WarheadType.ofOrdinal(type).machineOnly());
        for (int i = 0; i < 4; i++) { g[i] = 0.5F; vel[i] = 0; }
        level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.7F, 1.2F);
        status(player);
        sync();
    }

    public void start(Player player) {
        if (running) { player.displayClientMessage(Component.translatable("message.wmd.reactor.busy"), true); return; }
        LabModuleBlockEntity line = line();
        if (line != null) {

            player.displayClientMessage(Component.translatable(line.atReactor() ? "message.wmd.reactor.auto" : "message.wmd.reactor.not_ready"), true);
            return;
        } else {
            List<LabRecipes.Need> needs = LabRecipes.needs(type());
            if (!LabRecipes.satisfied(held, needs)) { player.displayClientMessage(Component.translatable("message.wmd.reactor.missing", needsLine()), true); return; }
            LabRecipes.consume(held, needs);
        }
        RandomSource r = level.random;
        running = true; tick = 0; greenTicks = 0; redTicks = 0; lastResult = R_NONE;
        for (int i = 0; i < 4; i++) { g[i] = 0.5F + (i < gaugeCount() ? (r.nextFloat() - 0.5F) * 0.16F : 0); vel[i] = 0; }
        level.playSound(null, worldPosition, WmdSounds.WR_STAGE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        player.displayClientMessage(Component.translatable("message.wmd.reactor.start", Balance.LAB_RUN_TICKS / 20), true);
        sync();
    }

    public void lineStartAuto() {
        if (running || level == null) return;
        running = true; autoRun = true; tick = 0; greenTicks = 0; redTicks = 0; lastResult = R_NONE;
        for (int i = 0; i < 4; i++) { g[i] = 0.5F; vel[i] = 0; }
        level.playSound(null, worldPosition, WmdSounds.WR_STAGE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.15F);
        sync();
    }

    public boolean autoRunning() { return running && autoRun; }

    public void lineStart() {
        if (running || level == null) return;
        RandomSource r = level.random;
        running = true; autoRun = false; tick = 0; greenTicks = 0; redTicks = 0; lastResult = R_NONE;
        for (int i = 0; i < 4; i++) { g[i] = 0.5F + (i < gaugeCount() ? (r.nextFloat() - 0.5F) * 0.2F : 0); vel[i] = 0; }
        level.playSound(null, worldPosition, WmdSounds.WR_STAGE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        tell(Component.translatable("message.wmd.reactor.start", Balance.LAB_RUN_TICKS / 20));
        sync();
    }

    public void valve(Player player, int i, boolean lower) {
        if (i >= gaugeCount()) return;
        if (!running) { player.displayClientMessage(Component.translatable("message.wmd.reactor.not_running"), true); return; }
        if (autoRun) { player.displayClientMessage(Component.translatable("message.wmd.reactor.auto_run"), true); return; }
        g[i] = Mth.clamp(g[i] + (float) (lower ? -Balance.LAB_VALVE_STEP : Balance.LAB_VALVE_STEP), 0.0F, 1.0F);
        turns[i] += lower ? -1 : 1;
        level.playSound(null, worldPosition, WmdSounds.WR_DOOR.get(), SoundSource.BLOCKS, Balance.SND_CLICK, lower ? 0.85F : 1.0F);
        sync();
    }

    public boolean repair(Player player) {
        if (damage <= 0) { player.displayClientMessage(Component.translatable("message.wmd.reactor.no_damage"), true); return false; }
        damage--;
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.3F);
        player.displayClientMessage(Component.translatable("message.wmd.reactor.repaired", damage, Balance.LAB_MAX_DAMAGE), true);
        sync();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WarheadReactorBlockEntity be) {
        if (!be.running) return;
        ServerLevel sl = (ServerLevel) level;
        if (be.autoRun) {
            be.tick++;
            for (int i = 0; i < be.gaugeCount(); i++) be.g[i] = 0.5F + 0.03F * Mth.sin((be.tick + i * 17) * 0.05F);
            if (be.tick >= Balance.LAB_RUN_TICKS) { be.autoRun = false; be.result(sl, R_NORMAL); return; }
            if (be.tick % 10 == 0) be.sync();
            return;
        }
        RandomSource r = level.random;
        WarheadType t = be.type();
        int n = be.gaugeCount();
        double mult = Balance.LAB_DRIFT[t.tier - 1];
        boolean allGreen = true, anyRed = false;
        for (int i = 0; i < n; i++) {
            be.vel[i] += (float) ((r.nextFloat() - 0.5F) * 2 * Balance.LAB_ACCEL * mult + (be.g[i] - 0.5F) * Balance.LAB_PUSH * mult * 0.1);
            if (r.nextInt(70) == 0) be.vel[i] += (r.nextBoolean() ? 1 : -1) * (float) (Balance.LAB_KICK * mult);
            be.vel[i] = Mth.clamp(be.vel[i] * 0.995F, (float) -(Balance.LAB_MAX_SPEED * mult), (float) (Balance.LAB_MAX_SPEED * mult));
            be.g[i] += be.vel[i];
            if (be.g[i] < 0 || be.g[i] > 1) { be.g[i] = Mth.clamp(be.g[i], 0, 1); be.vel[i] = 0; }
            int s = be.statusOf(be.g[i]);
            if (s != 0) allGreen = false;
            if (s == 2) anyRed = true;
        }
        be.tick++;
        if (allGreen) be.greenTicks++;
        if (anyRed) be.redTicks++; else if (be.redTicks > 0) be.redTicks = Math.max(0, be.redTicks - 2);

        Vec3 m = be.centre();
        if (anyRed && level.getGameTime() % 8 == 0) {
            if (level.getGameTime() % 32 == 0) level.playSound(null, pos, WmdSounds.WR_ALARM.get(), SoundSource.BLOCKS, Balance.SND_ALARM, 1.0F);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y + 0.6, m.z, 6, 0.5, 0.5, 0.5, 0.1);
            if (be.redTicks > Balance.LAB_HAZARD_TICKS / 2) sl.sendParticles(ParticleTypes.CLOUD, m.x, m.y + 1.2, m.z, 4, 0.4, 0.3, 0.4, 0.05);
        }
        if (be.redTicks >= Balance.LAB_HAZARD_TICKS) { be.hazard(sl); return; }
        if (be.tick >= Balance.LAB_RUN_TICKS) { be.finishRun(sl); return; }
        if (be.tick % 2 == 0) be.sync();
    }

    private void hazard(ServerLevel sl) {
        Vec3 m = centre();
        sl.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 3, 0.6, 0.4, 0.6, 0.0);
        sl.sendParticles(ParticleTypes.LAVA, m.x, m.y + 0.5, m.z, 12, 0.6, 0.4, 0.6, 0.0);
        sl.playSound(null, worldPosition, WmdSounds.WR_FAIL.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        WarheadType t = type();
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(Balance.LAB_HAZARD_RADIUS))) {
            double shield = Hardened.shield(Hardened.layers(sl, m, p.position().add(0.0, p.getBbHeight() * 0.5, 0.0)));
            p.hurt(sl.damageSources().explosion(null, null), (float) (Balance.LAB_HAZARD_DAMAGE * shield));
            if (t == WarheadType.INCENDIARY || t == WarheadType.THERMOBARIC || t == WarheadType.NUKE || t == WarheadType.THERMO) p.igniteForSeconds(6);
        }
        result(sl, R_SPOILED);
    }

    private void finishRun(ServerLevel sl) {
        result(sl, greenTicks >= Balance.LAB_EXCELLENT_TICKS ? R_EXCELLENT : greenTicks < Balance.LAB_BAD_TICKS ? R_BAD : R_NORMAL);
    }

    private void result(ServerLevel sl, int res) {
        running = false;
        lastResult = res;
        redTicks = 0;
        Vec3 m = centre();
        LabModuleBlockEntity line = line();
        if (res == R_BAD || res == R_SPOILED) {
            damage++;
            if (line != null) line.reactorDone(false, false);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y + 0.8, m.z, 20, 0.5, 0.5, 0.5, 0.02);
            tell(Component.translatable(res == R_BAD ? "message.wmd.reactor.bad" : "message.wmd.reactor.spoiled", damage, Balance.LAB_MAX_DAMAGE));
            if (damage >= Balance.LAB_MAX_DAMAGE) { explode(sl); return; }
        } else {
            if (line != null) line.reactorDone(true, res == R_EXCELLENT);
            else {
                ItemStack stack = LabRecipes.warheadStack(type());
                if (res == R_EXCELLENT) stack.set(WmdComponents.WARHEAD_GRADE.get(), 1);
                Vec3 f = frontSpot();
                ItemEntity e = new ItemEntity(sl, f.x, f.y, f.z, stack, 0, 0.15, 0);
                sl.addFreshEntity(e);
            }
            sl.playSound(null, worldPosition, WmdSounds.LAB_REACTOR_DONE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, res == R_EXCELLENT ? 1.08F : 0.96F);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.x, m.y + 0.5, m.z, res == R_EXCELLENT ? 25 : 8, 0.5, 0.5, 0.5, 0.0);
            tell(Component.translatable(res == R_EXCELLENT ? "message.wmd.reactor.excellent" : "message.wmd.reactor.normal", Component.translatable("warhead.wmd." + type().id)));
        }
        sync();
    }

    private void explode(ServerLevel sl) {
        Vec3 m = centre();
        float power = Balance.LAB_BLAST[type().tier - 1];
        tell(Component.translatable("message.wmd.reactor.exploded"));
        sl.destroyBlock(worldPosition, false);
        sl.explode(null, m.x, m.y, m.z, power, Config.Server.canBreak(sl, worldPosition) ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WarheadReactorBlockEntity be) {
        be.clientTicks++;
        for (int i = 0; i < 4; i++) {
            be.shownGauge[i] += (be.g[i] - be.shownGauge[i]) * 0.35F;
            be.valveAngle[i] += (be.turns[i] * 30.0F - be.valveAngle[i]) * 0.3F;
        }
        be.spin += be.running ? 0.35F : 0.04F;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeState(tag);
        tag.put("held", held.createTag(registries));
        ownerData.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readState(tag);
        if (tag.contains("held")) held.fromTag(tag.getList("held", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
        ownerData.read(tag);
    }

    private void writeState(CompoundTag tag) {
        tag.putInt("type", type); tag.putInt("tick", tick); tag.putInt("damage", damage); tag.putInt("green", greenTicks);
        tag.putInt("red", redTicks); tag.putInt("result", lastResult); tag.putBoolean("running", running); tag.putBoolean("autoRun", autoRun);
        for (int i = 0; i < 4; i++) { tag.putFloat("g" + i, g[i]); tag.putInt("t" + i, turns[i]); }
    }

    private void readState(CompoundTag tag) {
        type = tag.getInt("type"); tick = tag.getInt("tick"); damage = tag.getInt("damage"); greenTicks = tag.getInt("green");
        redTicks = tag.getInt("red"); lastResult = tag.getInt("result"); running = tag.getBoolean("running"); autoRun = tag.getBoolean("autoRun");
        for (int i = 0; i < 4; i++) { g[i] = tag.contains("g" + i) ? tag.getFloat("g" + i) : 0.5F; turns[i] = tag.getInt("t" + i); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeState(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    public static List<String> gaugeNames(int typeIndex) { return List.of(GAUGES[Mth.clamp(typeIndex, 0, GAUGES.length - 1)]); }
}
