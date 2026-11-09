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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class PeriscopeBlockEntity extends ProcessUnitBlockEntity implements NavalPanel, CcAnimated {

    public static final int ROW_RAISE = 0, ROW_LOOK = 1;
    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);

    private boolean up;
    private int ext;
    private boolean headWet;
    private boolean blocked;
    private Vec3 lastHead;

    public PeriscopeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.PERISCOPE_BE.get(), pos, state, Balance.PERI_FE); }

    public float extension(float pt) { return Mth.clamp(ext / (float) Balance.PERI_RAISE_TICKS, 0.0F, 1.0F); }
    public boolean fullyUp() { return up && ext >= Balance.PERI_RAISE_TICKS; }
    public boolean headWet() { return headWet; }
    public boolean canLook() { return fullyUp() && !headWet; }

    public Vec3 head() {
        double[] p = worldPoint(0.0, 3.0 + Balance.PERI_HEAD_UP * extension(0.0F) - 0.25, 0.0);
        return new Vec3(p[0], p[1], p[2]);
    }

    public boolean mastClear() { return HullPlug.open(this, facing(), Machines.mount(getBlockState()), new int[] { 0, 3, 0 }, new int[] { 0, 4, 0 }, new int[] { 0, 5, 0 }, new int[] { 0, 6, 0 }); }
    public boolean blocked() { return blocked; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        if (up && sl.getGameTime() % 10 == 0 && !mastClear()) { up = false; blocked = true; sync(); }
        boolean moved = false;
        if (up && ext < Balance.PERI_RAISE_TICKS) { ext++; moved = true; }
        else if (!up && ext > 0) { ext--; moved = true; }
        if (moved && (ext == 1 || ext == Balance.PERI_RAISE_TICKS - 1)) sl.playSound(null, worldPosition, WmdSounds.LIFT_MOTOR_LOOP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.3F);
        boolean wet = fullyUp() && NavalLayer.depthBelowSurface(sl, head()) > 0.05;
        if (wet != headWet) { headWet = wet; sync(); }
        if (moved && (ext == 0 || ext == Balance.PERI_RAISE_TICKS || ext % 10 == 0)) sync();
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (canLook() && ship != null && sl.getGameTime() % Balance.MAST_BEAT == 0) Masts.beat(sl, ship, Masts.PERISCOPE);
        if (!fullyUp() && ship != null && ext == Balance.PERI_RAISE_TICKS - 1) Masts.drop(sl, ship, Masts.PERISCOPE);
        return moved || up;
    }

    @Override
    protected void idle() {
        super.idle();
        if (getLevel() instanceof ServerLevel sl && !up) { UUID ship = SableBridge.shipIdAt(sl, worldPosition); if (ship != null) Masts.drop(sl, ship, Masts.PERISCOPE); }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public String panelTitle() { return "gui.wmd.periscope.panel"; }

    @Override
    public List<Component> panelLines() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable(fullyUp() ? (headWet ? "gui.wmd.periscope.too_deep" : "gui.wmd.periscope.up") : up ? "gui.wmd.periscope.raising" : ext > 0 ? "gui.wmd.periscope.lowering" : "gui.wmd.periscope.down"));
        l.add(Component.translatable("gui.wmd.periscope.ext", Math.round(extension(0.0F) * 100.0F)));
        l.add(Component.translatable("gui.wmd.periscope.exposed"));
        if (blocked) l.add(Component.translatable("gui.wmd.periscope.blocked"));
        return l;
    }

    @Override
    public List<Component> panelRows() {
        return List.of(Component.translatable(up ? "gui.wmd.periscope.row_lower" : "gui.wmd.periscope.row_raise"), Component.translatable("gui.wmd.periscope.row_look"));
    }

    @Override public boolean panelEnabled(int row) { return row != ROW_LOOK || canLook(); }
    @Override public int panelChipKind() { return canLook() ? WmdUi.K_WARN : up ? WmdUi.K_INFO : WmdUi.K_IDLE; }
    @Override public Component panelChip() { return Component.translatable(canLook() ? "gui.wmd.periscope.chip_up" : "gui.wmd.periscope.chip_down"); }

    @Override
    public boolean panelClient(int row) {
        if (row != ROW_LOOK) return false;
        if (level != null && level.isClientSide && canLook()) ClientPeriscope.enter(worldPosition);
        return true;
    }

    @Override
    public void setMode(int row) {
        if (row != ROW_RAISE || !(getLevel() instanceof ServerLevel sl)) return;
        if (!up && !mastClear()) {
            blocked = true;
            sl.playSound(null, worldPosition, WmdSounds.HATCH_OPEN.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.6F);
            sync();
            return;
        }
        blocked = false;
        up = !up;
        sl.playSound(null, worldPosition, WmdSounds.HATCH_OPEN.get(), SoundSource.BLOCKS, Balance.SND_CLICK, up ? 1.2F : 0.9F);
        sync();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float e = extension(partialTick);
        model.getBone("mast").ifPresent(b -> b.setPosY(Balance.PERI_STAGE_PX * Math.min(1.0F, e * 2.0F)));
        model.getBone("mast2").ifPresent(b -> b.setPosY(Balance.PERI_STAGE_PX * Math.max(0.0F, e * 2.0F - 1.0F)));
        boolean looking = ClientPeriscope.viewing(worldPosition);
        model.getBone("train").ifPresent(b -> b.setRotY(looking ? (float) Math.toRadians(-ClientPeriscope.trainDeg()) : 0.0F));
        model.getBone("handles").ifPresent(b -> b.setRotX(looking ? (float) Math.toRadians(90.0) : 0.0F));

        boolean ownView = looking && ClientPeriscope.active();
        model.getBone("mast2").ifPresent(b -> b.setHidden(ownView));
        model.getBone("lamp").ifPresent(b -> b.setHidden(ownView || !canLook()));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (!canLook()) { lastHead = null; return; }
        Vec3 h = head();
        Vec3 prev = lastHead;
        lastHead = h;
        if (prev == null) return;
        double sp = Math.hypot(h.x - prev.x, h.z - prev.z);
        if (sp < Balance.PERI_WAKE_SPEED) return;
        int top = NavalLayer.waterTop(lv, Mth.floor(h.x), Mth.floor(h.z));
        if (top == NavalLayer.NO_WATER) return;
        double y = top + 1.02, bx = (prev.x - h.x) / sp, bz = (prev.z - h.z) / sp;
        var r = lv.random;
        for (int k = 0; k < 2; k++)
            lv.addParticle(ParticleTypes.SPLASH, h.x + (r.nextDouble() - 0.5) * 0.3, y, h.z + (r.nextDouble() - 0.5) * 0.3, bx * 0.05, 0.08, bz * 0.05);
        if (r.nextInt(3) == 0) lv.addParticle(ParticleTypes.BUBBLE_POP, h.x + bx * 0.6, y, h.z + bz * 0.6, 0.0, 0.0, 0.0);
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override public List<Component> guiInfo() { return List.of(Component.translatable("gui.wmd.info.use", Balance.PERI_FE * 20), Component.translatable("gui.wmd.info.periscope")); }
    @Override protected SoundEvent runSound() { return WmdSounds.LIFT_MOTOR_LOOP.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return (up ? "up" : "down") + " " + Math.round(extension(0.0F) * 100) + "%" + (headWet ? " (head under water)" : ""); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
    }

    private void put(CompoundTag tag) {
        tag.putBoolean("peUp", up);
        tag.putInt("peExt", ext);
        tag.putBoolean("peWet", headWet);
        tag.putBoolean("peBlk", blocked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        up = tag.getBoolean("peUp");
        ext = Mth.clamp(tag.getInt("peExt"), 0, Balance.PERI_RAISE_TICKS);
        headWet = tag.getBoolean("peWet");
        blocked = tag.getBoolean("peBlk");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}
