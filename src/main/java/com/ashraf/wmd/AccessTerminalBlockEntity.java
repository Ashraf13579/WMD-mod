package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class AccessTerminalBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, CopycatState.Host {

    static final float VISOR_SIGN = 1.0F;
    static final double OPEN_R = 4.0;
    static final float OPEN_TICKS = 12.0F, VISOR_DEG = 80.0F, FOLD_DEG = -178.0F, SCAN_PX = 3.4F;

    private final CopycatState copycat = new CopycatState();

    private int players;

    private float shown;
    private double lastT = -1.0;

    public AccessTerminalBlockEntity(BlockPos pos, BlockState state) { super(AccessBlocks.TERMINAL_BE.get(), pos, state, 0, 1, 0); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    void setPlayers(int n) {
        if (n == players) return;
        players = n;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    @Override protected boolean work() { return false; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (level == null) return;
        double dt = lastT < 0 ? 0.0 : Mth.clamp(tt - lastT, 0.0, 5.0);
        lastT = tt;
        boolean near = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, OPEN_R, false) != null;
        shown = Mth.clamp(shown + (near ? 1.0F : -1.0F) * (float) dt / OPEN_TICKS, 0.0F, 1.0F);
        float k = shown < 0.5F ? 2.0F * shown * shown : 1.0F - (float) Math.pow(-2.0F * shown + 2.0F, 2) / 2.0F;
        final float v = VISOR_SIGN * (float) Math.toRadians(VISOR_DEG * k), v2 = VISOR_SIGN * (float) Math.toRadians(FOLD_DEG * k);
        model.getBone("visor").ifPresent(b -> b.setRotX(v));
        model.getBone("visor2").ifPresent(b -> b.setRotX(v2));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean ui = !dark && k > 0.5F;
        model.getBone("screen_ui").ifPresent(b -> b.setHidden(!ui));
        final float scan = ui ? SCAN_PX * (0.5F + 0.5F * Mth.sin((float) tt * 0.15F)) : 0.0F;
        model.getBone("scan").ifPresent(b -> { b.setHidden(!ui); b.setPosZ(scan); });
        for (int i = 0; i < 6; i++) {
            final boolean on = !dark && i < players;
            model.getBone("list" + i).ifPresent(b -> b.setHidden(!on));
        }
        final boolean lamp = !dark && ((long) tt % 40 < 30);
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
    }

    @Override protected void clientEffects(Level lv) {}
    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.ASR_SWITCH.get(); }
    @Override protected float soundVolume() { return Balance.SND_CLICK; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return "-"; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        copycat.save(tag, registries);
        tag.putInt("players", players);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copycat.load(tag, registries, level != null && level.isClientSide);
        players = tag.getInt("players");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        copycat.putSync(tag);
        tag.putInt("players", players);
        return tag;
    }
}
