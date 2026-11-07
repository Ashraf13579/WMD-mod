package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientBayPick {

    private ClientBayPick() {}

    private static final int CHUNKS = 2;

    @SubscribeEvent
    static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.screen != null || p.isShiftKeyDown() || p.isSpectator()) return;
        if (p.getMainHandItem().getItem() instanceof BayModuleItem) return;
        double reach = p.blockInteractionRange();
        Vec3 eye = p.getEyePosition(1.0F), end = eye.add(p.getViewVector(1.0F).scale(reach));
        HitResult hr = mc.hitResult;
        double best = hr == null || hr.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceToSqr(hr.getLocation());
        BlockPos hitController = null, hitCell = null;
        double near = (reach + 1.0) * (reach + 1.0), far = (reach + 30.0) * (reach + 30.0);
        int pcx = SectionPos.blockToSectionCoord(p.getBlockX()), pcz = SectionPos.blockToSectionCoord(p.getBlockZ());
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++)
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                LevelChunk ch = level.getChunkSource().getChunk(pcx + dx, pcz + dz, false);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof BayModuleBlockEntity)) continue;
                    BlockState st = be.getBlockState();
                    if (!(st.getBlock() instanceof BayModuleBlock block) || !BayPick.hasModel(block)) continue;
                    BlockPos c = be.getBlockPos();
                    if (c.distToCenterSqr(eye) > far) continue;
                    Direction f = st.getValue(MachineBlock.FACING), mount = Machines.mount(st);
                    for (int x = BayPick.X0; x <= BayPick.X1; x++)
                        for (int y = BayPick.Y0; y <= BayPick.Y1; y++)
                            for (int z = 0; z < block.kind.length; z++) {
                                BlockPos w = Machines.toWorld(c, f, mount, x, y, z);
                                if (w.distToCenterSqr(eye) > near) continue;
                                VoxelShape s = BayPick.cellShape(level, c, st, block, x, y, z);
                                if (s == null) continue;
                                BlockHitResult r = s.clip(eye, end, w);
                                if (r == null) continue;
                                double d = eye.distanceToSqr(r.getLocation());
                                if (d < best - 1.0E-6) { best = d; hitController = c; hitCell = w; }
                            }
                }
            }
        if (hitController == null) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new BayPick.Ask(hitController, hitCell));
    }
}
