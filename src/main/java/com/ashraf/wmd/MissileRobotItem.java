package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public class MissileRobotItem extends Item {

    private final int kind;

    public MissileRobotItem(Properties properties) { this(properties, MissileRobotEntity.KIND_ROBOT); }

    public MissileRobotItem(Properties properties, int kind) { super(properties.stacksTo(1)); this.kind = kind; }

    private String key(String k) { return (kind == MissileRobotEntity.KIND_XL ? "message.wmd.xl." : kind == MissileRobotEntity.KIND_RUNNER ? "message.wmd.runner." : "message.wmd.robot.") + k; }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos dock = controllerOf(ctx);
        if (dock == null) {
            if (!ctx.getLevel().isClientSide) player.displayClientMessage(Component.translatable(key("needs_dock")), true);
            return InteractionResult.FAIL;
        }
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (!(sl.getBlockEntity(dock) instanceof RobotDockBlockEntity be)) return InteractionResult.FAIL;
        if (!Access.mayUse(be, player)) { Access.deny(be, player); return InteractionResult.FAIL; }
        Entity old = be.robot() != null ? sl.getEntity(be.robot()) : null;
        if (old instanceof MissileRobotEntity && !old.isRemoved()) { player.displayClientMessage(Component.translatable(key("dock_taken")), true); return InteractionResult.FAIL; }
        int ccs = CommandCenter.activeBuildings(player.getUUID()).size();
        if (ccs == 0) { player.displayClientMessage(Component.translatable(key("needs_cc")), true); return InteractionResult.FAIL; }
        int max = ccs * (kind == MissileRobotEntity.KIND_XL ? Balance.XL_MAX_PER_CC : kind == MissileRobotEntity.KIND_RUNNER ? Balance.RUNNER_MAX_PER_CC : Balance.ROBOT_MAX_PER_CC);
        int have = 0;
        for (MissileRobotEntity r : Logistics.robotsOf(player.getUUID())) if (r.vehicleKind() == kind) have++;
        if (have >= max) { player.displayClientMessage(Component.translatable(key("too_many"), max), true); return InteractionResult.FAIL; }
        Vec3 park = RobotDockBlockEntity.parkSpot(sl, dock);
        MissileRobotEntity r = kind == MissileRobotEntity.KIND_XL ? Wmd.XL_TRANSPORTER.get().create(sl) : kind == MissileRobotEntity.KIND_RUNNER ? Wmd.PARTS_RUNNER.get().create(sl)
                : Wmd.MISSILE_ROBOT.get().create(sl);
        if (park == null || r == null) return InteractionResult.FAIL;
        r.setup(player, dock, RobotDockBlockEntity.parkYaw(sl, dock));
        if (r instanceof PartsRunnerEntity pr && PartsRunnerEntity.upgradedItem(ctx.getItemInHand())) pr.setUpgraded(true);
        r.moveTo(park.x, park.y, park.z, RobotDockBlockEntity.parkYaw(sl, dock), 0.0F);
        sl.addFreshEntity(r);
        be.setRobot(r.getUUID());
        Logistics.noteRobot(r);
        if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        sl.playSound(null, dock, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 0.8F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private BlockPos controllerOf(UseOnContext ctx) {
        BlockPos p = ctx.getClickedPos();
        if (ctx.getLevel().getBlockEntity(p) instanceof MachinePartBlockEntity part && part.getController() != null) p = part.getController();
        var b = ctx.getLevel().getBlockState(p).getBlock();
        int dockKind = b instanceof XlDockBlock ? MissileRobotEntity.KIND_XL : b instanceof RunnerChargerBlock ? MissileRobotEntity.KIND_RUNNER : MissileRobotEntity.KIND_ROBOT;
        return b instanceof RobotDockBlock && dockKind == kind ? p : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(kind == MissileRobotEntity.KIND_XL ? Component.translatable("tooltip.wmd.xl_transporter", Balance.XL_MAX_PER_CC).withStyle(ChatFormatting.GRAY)
                : kind == MissileRobotEntity.KIND_RUNNER ? Component.translatable("tooltip.wmd.parts_runner", Balance.RUNNER_MAX_PER_CC).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.wmd.missile_robot", Balance.ROBOT_MAX_PER_CC).withStyle(ChatFormatting.GRAY));
        if (kind == MissileRobotEntity.KIND_RUNNER && PartsRunnerEntity.upgradedItem(stack))
            tooltip.add(Component.translatable("tooltip.wmd.parts_runner.up", Balance.RUNNER_UP_STACKS, Balance.RUNNER_UP_ITEMS).withStyle(ChatFormatting.AQUA));
    }
}
