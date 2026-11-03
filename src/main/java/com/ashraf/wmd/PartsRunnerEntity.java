package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PartsRunnerEntity extends MissileRobotEntity {

    private static final EntityDataAccessor<Boolean> UPGRADED = SynchedEntityData.defineId(PartsRunnerEntity.class, EntityDataSerializers.BOOLEAN);

    private float walk, walkO, stride;

    public PartsRunnerEntity(EntityType<? extends PartsRunnerEntity> type, Level level) { super(type, level); }

    @Override public int vehicleKind() { return KIND_RUNNER; }
    @Override int carryStacks() { return upgraded() ? Balance.RUNNER_UP_STACKS : 1; }
    @Override public boolean upgraded() { return entityData.get(UPGRADED); }
    void setUpgraded(boolean u) { entityData.set(UPGRADED, u); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(UPGRADED, false);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Wmd.RUNNER_UPGRADE_KIT.get())) return super.interact(player, hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (owner() != null && !owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.wmd.not_yours", ownerName()), true);
            return InteractionResult.SUCCESS;
        }
        if (upgraded()) { player.displayClientMessage(Component.translatable("message.wmd.runner.already_up"), true); return InteractionResult.SUCCESS; }
        setUpgraded(true);
        if (!player.getAbilities().instabuild) held.shrink(1);
        level().playSound(null, blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.wmd.runner.upgraded", Balance.RUNNER_UP_STACKS, Balance.RUNNER_UP_ITEMS), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setUpgraded(tag.getBoolean("upgraded"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("upgraded", upgraded());
    }
    @Override int battery() { return Balance.RUNNER_BATTERY; }
    @Override int maxHealth() { return Balance.RUNNER_HEALTH; }
    @Override int reserve() { return Balance.RUNNER_JOB_RESERVE; }
    @Override int fePerBlock() { return Balance.RUNNER_FE_PER_BLOCK; }
    @Override double speed() { return Balance.RUNNER_SPEED; }
    @Override int loadTicks() { return Balance.RUNNER_LOAD_TICKS; }
    @Override int half() { return 0; }
    @Override int head() { return 2; }
    @Override ItemStack asItem() {
        ItemStack s = new ItemStack(Wmd.PARTS_RUNNER_ITEM.get());
        if (upgraded()) s.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(upgradedTag()));
        return s;
    }

    private static CompoundTag upgradedTag() { CompoundTag t = new CompoundTag(); t.putBoolean("wmd_upgraded", true); return t; }

    static boolean upgradedItem(ItemStack s) {
        var cd = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBoolean("wmd_upgraded");
    }
    @Override String infoKey() { return "message.wmd.runner.info."; }

    @Override
    void clientTick() {
        double px = getX(), pz = getZ();
        super.clientTick();
        double d = Math.hypot(getX() - px, getZ() - pz);
        walkO = walk;
        walk += (float) d * 4.2F;
        stride += ((d > 0.01 ? 1.0F : 0.0F) - stride) * 0.25F;
    }

    public float walk(float pt) { return walkO + (walk - walkO) * pt; }
    public float stride() { return stride; }
}
