package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SuitRackBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    static final float ARM_SIGN = 1.0F;
    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private final OwnerData ownerData = new OwnerData();
    private final ItemStack[] suit = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
    private boolean charging;

    private ItemStack tool = ItemStack.EMPTY;

    private float open = 1.0F, prevOpen = 1.0F;

    public SuitRackBlockEntity(BlockPos pos, BlockState state) {
        super(ExoSuitBlocks.SUIT_RACK_BE.get(), pos, state, 1, Balance.RACK_BUFFER_FE, Balance.RACK_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public ItemStack piece(int i) { return suit[i]; }

    public boolean holdsSuit() { for (ItemStack s : suit) if (!s.isEmpty()) return true; return false; }

    public boolean charging() { return charging; }

    public ItemStack tool() { return tool; }

    boolean holster(Player player) {
        if (level == null || level.isClientSide) return false;
        ItemStack h = player.getMainHandItem();
        if (HandTech.isTool(h)) {
            if (!tool.isEmpty()) { player.displayClientMessage(Component.translatable("message.wmd.suit_rack.holster_full").withStyle(ChatFormatting.YELLOW), true); return true; }
            tool = h.copy();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            playOnce(level, WmdSounds.CAM_SERVO.get(), Balance.SND_SMALL, 1.1F);
            sync();
            return true;
        }
        if (h.isEmpty() && player.isShiftKeyDown() && !tool.isEmpty() && !standsIn(player)) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
            tool = ItemStack.EMPTY;
            playOnce(level, WmdSounds.SCREW_SERVO.get(), Balance.SND_SMALL, 1.3F);
            sync();
            return true;
        }
        return false;
    }

    void piecesChanged() { sync(); }

    net.minecraft.world.MenuProvider menu() {
        return new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new SuitRackMenu(id, inv, this, worldPosition), Component.translatable("block.wmd.suit_rack"));
    }

    boolean standsIn(Player player) {
        return new AABB(worldPosition).expandTowards(0.0, 1.0, 0.0).inflate(0.35, 0.0, 0.35).contains(player.position());
    }

    void use(Player player) {
        if (level == null || level.isClientSide) return;
        AABB cell = new AABB(worldPosition).expandTowards(0.0, 1.0, 0.0).inflate(0.35, 0.0, 0.35);
        if (!cell.contains(player.position())) {
            player.displayClientMessage(Component.translatable("message.wmd.suit_rack.step_in").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (player.isShiftKeyDown()) {
            int n = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack w = player.getItemBySlot(SLOTS[i]);
                if (!(w.getItem() instanceof ExoSuitItem) || !suit[i].isEmpty()) continue;
                suit[i] = w.copy();
                player.setItemSlot(SLOTS[i], ItemStack.EMPTY);
                n++;
            }
            if (n == 0) { player.displayClientMessage(Component.translatable("message.wmd.suit_rack.nothing").withStyle(ChatFormatting.YELLOW), true); return; }
            playOnce(level, WmdSounds.CAM_SERVO.get(), Balance.SND_SMALL, 0.8F);
            player.displayClientMessage(Component.translatable("message.wmd.suit_rack.stored", n), true);
        } else {
            if (!holdsSuit()) { player.displayClientMessage(Component.translatable("message.wmd.suit_rack.empty").withStyle(ChatFormatting.YELLOW), true); return; }
            int n = 0, blocked = 0;
            for (int i = 0; i < 4; i++) {
                if (suit[i].isEmpty()) continue;
                if (!player.getItemBySlot(SLOTS[i]).isEmpty()) { blocked++; continue; }
                player.setItemSlot(SLOTS[i], suit[i]);
                suit[i] = ItemStack.EMPTY;
                n++;
            }
            if (n > 0) playOnce(level, WmdSounds.SCREW_SERVO.get(), Balance.SND_SMALL, 1.2F);
            if (blocked > 0) player.displayClientMessage(Component.translatable("message.wmd.suit_rack.slots_full", blocked).withStyle(ChatFormatting.YELLOW), true);
        }
        charging = false;
        sync();
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel)) return false;
        ItemStack c = suit[1];
        boolean ch = false;
        if (c.getItem() instanceof ExoSuitItem) {
            int missing = Balance.EXO_FE_CAP - ExoSuitItem.fe(c);
            int give = Math.min(Balance.RACK_CHARGE_FE, Math.min(missing, energy().getEnergyStored() - 1));
            if (give > 0 && useExtraEnergy(give)) {
                ExoSuitItem.setFe(c, ExoSuitItem.fe(c) + give);
                PowerMeter.noteIn(this, give);
                ch = true;
                setChanged();
            }
        }
        if (!tool.isEmpty() && HandTech.fe(tool) < HandTech.cap(tool)) {
            int give = Math.min(Balance.RACK_CHARGE_FE, Math.min(HandTech.cap(tool) - HandTech.fe(tool), energy().getEnergyStored() - 1));
            if (give > 0 && useExtraEnergy(give)) {
                HandTech.setFe(tool, HandTech.fe(tool) + give);
                PowerMeter.noteIn(this, give);
                ch = true;
                setChanged();
            }
        }
        if (ch != charging || (ch && level.getGameTime() % 20 == 0)) { charging = ch; sync(); }
        return true;
    }

    @Override protected void jammed(ServerLevel sl) { if (charging) { charging = false; sync(); } }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        for (int i = 0; i < 4; i++) if (!suit[i].isEmpty()) { l.add(suit[i]); suit[i] = ItemStack.EMPTY; }
        if (!tool.isEmpty()) { l.add(tool); tool = ItemStack.EMPTY; }
        return l;
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("gui.wmd.info.suit_rack", String.format("%,d", Balance.RACK_CHARGE_FE)));
        return l;
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return Balance.RACK_CHARGE_FE; }

    @Override
    protected String detail() {
        ItemStack c = suit[1];
        int pieces = 0;
        for (ItemStack s : suit) if (!s.isEmpty()) pieces++;
        return pieces == 0 ? "empty" : pieces + " / 4 pieces" + (c.isEmpty() ? "" : ", chest " + Math.round(100.0F * ExoSuitItem.fe(c) / Balance.EXO_FE_CAP) + " %")
                + (charging ? ", charging" : "");
    }

    @Override protected SoundEvent runSound() { return WmdSounds.SA_HYDRAULIC_LOOP.get(); }
    @Override protected boolean soundAlive() { return false; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    static void clientTickRack(Level level, BlockPos pos, BlockState st, SuitRackBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, st, be);
        be.prevOpen = be.open;
        float target = be.holdsSuit() ? 0.0F : 1.0F;
        be.open = target > be.open ? Math.min(target, be.open + 1.0F / Balance.RACK_ARM_TICKS) : Math.max(target, be.open - 1.0F / Balance.RACK_ARM_TICKS);
    }

    public float openness(float pt) {
        float o = prevOpen + (open - prevOpen) * pt;
        return o < 0.5F ? 2.0F * o * o : 1.0F - (float) Math.pow(-2.0F * o + 2.0F, 2) / 2.0F;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float o = openness(pt);
        final float arm = ARM_SIGN * (float) Math.toRadians(55.0F * o);
        model.getBone("rack_arm_l").ifPresent(bn -> bn.setRotY(arm));
        model.getBone("rack_arm_r").ifPresent(bn -> bn.setRotY(-arm));
        final float plug = 2.0F * Mth.clamp(o * 2.0F, 0.0F, 1.0F);
        model.getBone("rack_plug").ifPresent(bn -> bn.setPosY(plug));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        long t = (long) tt;
        final boolean lit = !dark && hasPower() && (!charging || t % 20 < 14);
        model.getBone("lt_rack").ifPresent(bn -> bn.setHidden(!lit));
    }

    private void writeSuit(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < 4; i++) {
            if (suit[i].isEmpty()) tag.remove("rackSuit" + i);
            else tag.put("rackSuit" + i, suit[i].save(registries));
        }
        if (tool.isEmpty()) tag.remove("rackTool");
        else tag.put("rackTool", tool.save(registries));
    }

    private void readSuit(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < 4; i++) suit[i] = tag.contains("rackSuit" + i) ? ItemStack.parse(registries, tag.getCompound("rackSuit" + i)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        tool = tag.contains("rackTool") ? ItemStack.parse(registries, tag.getCompound("rackTool")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        writeSuit(tag, registries);
        tag.putBoolean("rackCharging", charging);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        readSuit(tag, registries);
        charging = tag.getBoolean("rackCharging");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        writeSuit(tag, registries);
        tag.putBoolean("rackCharging", charging);
        return tag;
    }
}
