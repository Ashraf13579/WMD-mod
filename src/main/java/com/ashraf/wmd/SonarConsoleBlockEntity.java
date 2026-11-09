package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SonarConsoleBlockEntity extends ProcessUnitBlockEntity implements Owned {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 0;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 0, Direction.SOUTH, false);

    private final OwnerData ownerData = new OwnerData();
    private int frequency = 1;

    private ListTag contacts = new ListTag();

    private ListTag fixes = new ListTag();

    private ListTag fall = new ListTag();

    private int domes, arrays;

    private ListTag sens = new ListTag();
    private int cx, cz;

    public SonarConsoleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SONAR_CONSOLE_BE.get(), pos, state, Balance.SONAR_CONSOLE_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    public int frequency() { return frequency; }

    public void setFrequency(int f) {
        f = Math.max(1, Math.min(9999, f));
        if (f == frequency) return;
        frequency = f;
        fall = new ListTag();
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return true;
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 31L)) % Balance.SONAR_HULL_TICKS == 7) updateHull(sl);
        if (hunting && now % 10 == 5) {
            boolean ready = false;
            for (SonarNet.Sensor s : SonarNet.sensors(sl, getOwner(), frequency))
                if (s.kind() == SonarNet.DOME && CommsLink.linked(sl, CommsLink.shipOf(sl, worldPosition), s.ship()) && sl.getBlockEntity(BlockPos.of(s.pos())) instanceof SonarDomeBlockEntity d && d.cooldown(now) <= 0) { ready = true; break; }
            if (ready) { pingAll(null); return true; }
        }
        if ((now + (worldPosition.asLong() & 15L)) % Balance.SONAR_SCAN_TICKS != 3) return true;
        compile(sl);
        return true;
    }

    private void compile(ServerLevel sl) {
        UUID o = getOwner();
        int d = 0, a = 0;
        ListTag sn = new ListTag();
        java.util.UUID myShip = CommsLink.shipOf(sl, worldPosition);
        for (SonarNet.Sensor s : SonarNet.sensors(sl, o, frequency)) {
            if (!CommsLink.linked(sl, myShip, s.ship())) continue;
            if (s.kind() == SonarNet.DOME) d++; else a++;
            if (sn.size() < Balance.SONAR_KEEP && s.ear() != null) {
                CompoundTag t = new CompoundTag();
                t.putInt("x", (int) Math.floor(s.ear().x));
                t.putInt("z", (int) Math.floor(s.ear().z));
                t.putInt("k", s.kind());
                sn.add(t);
            }
        }
        sens = sn;
        Vec3 me = SableBridge.worldPos(sl, worldPosition);
        cx = (int) Math.floor(me.x);
        cz = (int) Math.floor(me.z);
        domes = d;
        arrays = a;
        ListTag c = new ListTag();
        int[] row = new int[Math.min(Balance.SONAR_KEEP, 64)];
        int n = 0;
        for (SonarNet.Contact k : SonarNet.contacts(sl, o, frequency)) {
            if (c.size() >= Balance.SONAR_KEEP) break;
            if (!CommsLink.hears(sl, worldPosition, k.ear())) continue;
            CompoundTag t = new CompoundTag();
            int b10 = (int) Math.round(((k.bearing() % 360.0) + 360.0) % 360.0 * 10.0) % 3600;
            t.putInt("b", b10);
            t.putInt("l", (int) Math.round(k.level()));
            t.putInt("k", k.kind().ordinal());
            if (k.ear() != null) { t.putInt("ex", (int) Math.floor(k.ear().x)); t.putInt("ez", (int) Math.floor(k.ear().z)); }
            if (k.fix() != null) {
                t.putBoolean("f", true);
                t.putInt("fx", (int) Math.round(k.fix().x()));
                t.putInt("fz", (int) Math.round(k.fix().z()));
                t.putInt("fe", (int) Math.ceil(k.fix().err()));
            }
            c.add(t);
            if (n < row.length) row[n++] = b10 | (Math.max(0, Math.min(255, (int) Math.round(k.level()))) << 12);
        }
        ListTag f = new ListTag();
        for (SonarNet.Fix x : SonarNet.pingFixes(sl, o, frequency)) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", (int) Math.round(x.x()));
            t.putInt("y", (int) Math.round(x.y()));
            t.putInt("z", (int) Math.round(x.z()));
            t.putInt("d", (int) Math.round(x.depth()));
            t.putBoolean("s", x.submerged());
            t.putString("n", x.label());
            f.add(t);
            if (f.size() >= Balance.SONAR_KEEP) break;
        }
        contacts = c;
        fixes = f;
        survey = surveyRows(sl, me, myShip);
        fall.add(0, new IntArrayTag(java.util.Arrays.copyOf(row, n)));
        while (fall.size() > Balance.SONAR_WATERFALL_ROWS) fall.remove(fall.size() - 1);
        setChanged();
        sync();
    }

    private boolean hunting;

    public boolean hunting() { return hunting; }

    public void setHunting(boolean on) {
        if (hunting == on) return;
        hunting = on;
        setChanged();
        sync();
    }

    public int pingAll(Player by) {
        if (!(level instanceof ServerLevel sl) || !isRunning()) return 0;
        int n = 0;
        for (SonarNet.Sensor s : SonarNet.sensors(sl, getOwner(), frequency)) {
            if (s.kind() != SonarNet.DOME || !CommsLink.linked(sl, CommsLink.shipOf(sl, worldPosition), s.ship())) continue;
            if (sl.getBlockEntity(BlockPos.of(s.pos())) instanceof SonarDomeBlockEntity dome && dome.ping(by) == SonarDomeBlockEntity.P_OK) n++;
        }
        compile(sl);
        return n;
    }

    private ListTag survey = new ListTag();

    private ListTag surveyRows(ServerLevel sl, Vec3 me, UUID myShip) {
        List<NoduleSurvey.Mark> ms = new ArrayList<>();
        for (NoduleSurvey.Mark m : NoduleSurvey.marks(sl, getOwner(), frequency))
            if (CommsLink.linked(sl, myShip, m.ship())) ms.add(m);
        ms.sort((a, b) -> Double.compare(Math.hypot(a.x() - me.x, a.z() - me.z), Math.hypot(b.x() - me.x, b.z() - me.z)));
        ListTag l = new ListTag();
        long now = sl.getGameTime();
        for (NoduleSurvey.Mark m : ms) {
            if (l.size() >= Balance.SONAR_KEEP) break;
            CompoundTag t = new CompoundTag();
            t.putInt("x", m.x()); t.putInt("y", m.y()); t.putInt("z", m.z()); t.putInt("r", m.r()); t.putInt("n", m.left());
            t.putInt("d", NoduleSurvey.depth(sl, m.y()));
            t.putInt("a", (int) Math.max(0L, (now - m.time()) / 20L));
            l.add(t);
        }
        return l;
    }

    public int surveyAll(Player by) {
        surveyFail = null;
        if (!(level instanceof ServerLevel sl) || !isRunning()) { surveyFail = Component.translatable("gui.wmd.sonar.survey_why.console"); return 0; }
        int n = 0, domes = 0, unlinked = 0, cool = Integer.MAX_VALUE, dry = 0;
        SonarDomeBlockEntity weak = null;
        long now = sl.getGameTime();
        for (SonarNet.Sensor s : SonarNet.sensors(sl, getOwner(), frequency)) {
            if (s.kind() != SonarNet.DOME) continue;
            domes++;
            if (!CommsLink.linked(sl, CommsLink.shipOf(sl, worldPosition), s.ship())) { unlinked++; continue; }
            if (!(sl.getBlockEntity(BlockPos.of(s.pos())) instanceof SonarDomeBlockEntity dome)) continue;
            int r = dome.survey(by);
            if (r == SonarDomeBlockEntity.P_OK) n++;
            else if (r == SonarDomeBlockEntity.P_COOLDOWN) cool = Math.min(cool, dome.cooldown(now));
            else if (r == SonarDomeBlockEntity.P_DRY) dry++;
            else if (r == SonarDomeBlockEntity.P_NO_POWER && (weak == null || dome.energy().getEnergyStored() > weak.energy().getEnergyStored())) weak = dome;
        }

        if (n == 0) surveyFail = domes == 0 ? Component.translatable("gui.wmd.sonar.survey_why.none", frequency)
                : cool != Integer.MAX_VALUE ? Component.translatable("gui.wmd.sonar.survey_why.cool", (cool + 19) / 20)
                : weak != null ? Component.translatable("gui.wmd.sonar.survey_why.power", String.format("%,d", weak.energy().getEnergyStored()), String.format("%,d", Balance.SURVEY_FE + weak.feCost()))
                : dry > 0 ? Component.translatable("gui.wmd.sonar.survey_why.dry")
                : unlinked > 0 ? Component.translatable("gui.wmd.sonar.survey_why.link", unlinked)
                : Component.translatable("gui.wmd.sonar.survey_none");
        return n;
    }

    private Component surveyFail;
    public Component surveyFail() { return surveyFail; }

    public List<CompoundTag> surveyRows() { return rows(survey); }

    private CompoundTag hull = new CompoundTag();

    private void updateHull(ServerLevel sl) {
        CompoundTag h;
        try { h = HullPage.compute(sl, worldPosition, Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.NORTH)); }
        catch (RuntimeException e) { Wmd.LOGGER.error("Sonar console hull page failed at {}", worldPosition, e); h = new CompoundTag(); }
        if (h.equals(hull)) return;
        hull = h;
        setChanged();
        sync();
    }

    public CompoundTag hullInfo() { return hull; }

    public int domes() { return domes; }
    public int arrays() { return arrays; }

    public List<CompoundTag> contactRows() { return rows(contacts); }
    public List<CompoundTag> fixRows() { return rows(fixes); }

    public List<CompoundTag> sensorRows() { return rows(sens); }

    public int mapX() { return cx; }
    public int mapZ() { return cz; }

    public List<int[]> waterfall() {
        List<int[]> l = new ArrayList<>();
        for (Tag t : fall) if (t instanceof IntArrayTag ia) l.add(ia.getAsIntArray());
        return l;
    }

    private static List<CompoundTag> rows(ListTag t) {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag x : t) if (x instanceof CompoundTag c) l.add(c);
        return l;
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.SON_ROOM_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.SONAR_CONSOLE_FE * 20), Component.translatable("gui.wmd.info.sonar_console"));
    }

    @Override
    protected String detail() {
        return (isRunning() ? "on" : "no power") + ", " + domes + " dome(s), " + arrays + " array(s), " + contacts.size() + " contact(s), frequency " + frequency;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("sonFreq", frequency);
        tag.putBoolean("sonHunt", hunting);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("sonFreq")) frequency = Math.max(1, Math.min(9999, tag.getInt("sonFreq")));
        hunting = tag.getBoolean("sonHunt");
        if (tag.contains("sonCont")) contacts = tag.getList("sonCont", Tag.TAG_COMPOUND);
        if (tag.contains("sonFix")) fixes = tag.getList("sonFix", Tag.TAG_COMPOUND);
        if (tag.contains("sonFall")) fall = tag.getList("sonFall", Tag.TAG_INT_ARRAY);
        domes = tag.getInt("sonDomes");
        arrays = tag.getInt("sonArrays");
        if (tag.contains("sonSens")) sens = tag.getList("sonSens", Tag.TAG_COMPOUND);
        cx = tag.contains("sonCx") ? tag.getInt("sonCx") : worldPosition.getX();
        cz = tag.contains("sonCz") ? tag.getInt("sonCz") : worldPosition.getZ();
        if (tag.contains("sonSv")) survey = tag.getList("sonSv", Tag.TAG_COMPOUND);
        if (tag.contains("sonHull")) hull = tag.getCompound("sonHull");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putInt("sonFreq", frequency);
        tag.putBoolean("sonHunt", hunting);
        tag.put("sonCont", contacts);
        tag.put("sonFix", fixes);
        tag.put("sonFall", fall);
        tag.putInt("sonDomes", domes);
        tag.putInt("sonArrays", arrays);
        tag.put("sonSens", sens);
        tag.putInt("sonCx", cx);
        tag.putInt("sonCz", cz);
        tag.put("sonSv", survey);
        tag.put("sonHull", hull);
        return tag;
    }
}
