package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class BlinkGatePieceBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    private final boolean isCorner;
    private final OwnerData ownerData = new OwnerData();
    private BlockPos core;
    private float order;

    private double lastTT = Double.NaN;
    private float spin;

    private BlinkGatePieceBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state, boolean corner) {
        super(type, pos, state, 0, 0, 0);
        this.isCorner = corner;
    }

    static BlinkGatePieceBlockEntity corner(BlockPos pos, BlockState state) { return new BlinkGatePieceBlockEntity(BlinkGateBlocks.BLINK_CORNER_BE.get(), pos, state, true); }

    static BlinkGatePieceBlockEntity segment(BlockPos pos, BlockState state) { return new BlinkGatePieceBlockEntity(BlinkGateBlocks.BLINK_SEGMENT_BE.get(), pos, state, false); }

    public BlockPos core() { return core; }
    public float order() { return order; }
    public boolean isCorner() { return isCorner; }

    void bind(BlockPos corePos, float ringOrder, UUID owner, String ownerName) {
        core = corePos.immutable();
        order = ringOrder;
        ownerData.set(owner, ownerName);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private BlinkGateCoreBlockEntity coreBe() {
        if (core == null || level == null || !level.isLoaded(core)) return null;
        return level.getBlockEntity(core) instanceof BlinkGateCoreBlockEntity c ? c : null;
    }

    @Override public boolean ownable() { return false; }
    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { BlinkGateCoreBlockEntity c = coreBe(); return c != null ? c.security() : ownerData.security(); }
    @Override public void claim(Player player) { if (ownerData.id() == null) { ownerData.claim(player); setChanged(); } }

    static void openCore(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof BlinkGatePieceBlockEntity p && p.core != null && level.getBlockEntity(p.core) instanceof BlinkGateCoreBlockEntity c) {
            if (c.getOwner() != null && !Access.mayUse(c, player)) { Access.deny(c, player); return; }
            MachineMenu.open(player, c);
            return;
        }
        player.displayClientMessage(Component.translatable("message.wmd.blink.no_core"), true);
    }

    static void removed(Block self, Level level, BlockPos pos, BlockState newState, boolean moved, boolean corner) {
        if (level.isClientSide || newState.is(self) || ShipRelink.shipMove(level, moved)) return;
        if (!(level.getBlockEntity(pos) instanceof BlinkGatePieceBlockEntity p)) return;
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(corner ? BlinkGateBlocks.CORNER_ITEM.get() : BlinkGateBlocks.SEGMENT_ITEM.get()));
        BlinkGateCoreBlockEntity c = p.coreBe();
        if (c != null) c.pieceGone();
    }

    private boolean top() {
        BlockState st = getBlockState();
        BlockPos o = Machines.toWorld(BlockPos.ZERO, st.getValue(MachineBlock.FACING), Machines.mount(st), 0, 0, 0);
        BlockPos d = Machines.toWorld(BlockPos.ZERO, st.getValue(MachineBlock.FACING), Machines.mount(st), 1, 1, 0).subtract(o);
        return d.getY() > 0;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double dt = Double.isNaN(lastTT) ? 0.0 : Mth.clamp(tt - lastTT, 0.0, 5.0);
        lastTT = tt;
        BlinkGateCoreBlockEntity c = coreBe();
        double dep = c == null ? 0.0 : c.deploy(order, tt);
        boolean locked = c != null && c.locked(order, tt);
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean lit = c != null && !dark && c.lit(order, tt);
        final float bar = (float) (Math.PI * (1.0 - BlinkGateCoreBlockEntity.ease(dep)));
        model.getBone("bar").ifPresent(b -> b.setRotX(bar));
        model.getBone("lit").ifPresent(b -> b.setHidden(!lit));
        model.getBone("lens").ifPresent(b -> b.setHidden(!lit));
        if (isCorner) {
            double v = c == null ? 0.0 : c.spin(tt);
            spin = (float) ((spin + Balance.BG_ROTOR_DEG * v * dt) % 360.0);
            final float r = spin * Mth.DEG_TO_RAD;
            model.getBone("rot").ifPresent(b -> b.setRotZ(r));
            final boolean bOn = top() && c != null && !dark && c.gateState() >= BlinkGateCoreBlockEntity.S_POWER && c.gateState() <= BlinkGateCoreBlockEntity.S_DOWN
                    && ((long) tt) % 20 < 10;
            model.getBone("beacon").ifPresent(b -> b.setHidden(!bOn));
        } else {
            final float jz = locked ? Balance.BG_JAW_IN : 0.0F;
            model.getBone("jaw0").ifPresent(b -> b.setPosZ(jz));
            model.getBone("jaw1").ifPresent(b -> b.setPosZ(-jz));
        }
    }

    @Override protected boolean work() { return false; }
    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected SoundEvent runSound() { return null; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public boolean guiHidesPower() { return true; }
    @Override public List<Component> guiInfo() { return List.of(); }
    @Override protected String detail() { return "blink gate " + (isCorner ? "corner" : "segment") + ", core " + (core == null ? "-" : core.toShortString()) + ", order " + order; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        put(tag);
    }

    private void put(CompoundTag tag) {
        if (core != null) tag.putLong("bgCore", core.asLong());
        tag.putFloat("bgOrder", order);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        core = tag.contains("bgCore") ? BlockPos.of(tag.getLong("bgCore")) : null;
        order = tag.getFloat("bgOrder");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        put(tag);
        return tag;
    }

    Direction pieceFacing() { return getBlockState().getValue(MachineBlock.FACING); }
}
