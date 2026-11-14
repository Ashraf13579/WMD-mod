package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShorePayload(BlockPos pos, int action, int[] data) implements CustomPacketPayload {

    public static final Type<ShorePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "shore"));
    private static final int MAX = 4;

    public static final StreamCodec<FriendlyByteBuf, ShorePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.action());
                int n = Math.min(MAX, p.data() == null ? 0 : p.data().length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeInt(p.data()[i]);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int action = buf.readVarInt(), n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("shore payload: too many numbers " + n);
                int[] d = new int[n];
                for (int i = 0; i < n; i++) d[i] = buf.readInt();
                return new ShorePayload(pos, action, d);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ShorePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu)) return;
            if (menu.machine() instanceof CoastalBatteryBlockEntity b && b.getBlockPos().equals(p.pos()) && !b.isRemoved() && Access.mayUse(b, context.player())
                    && p.action() >= CoastalBatteryBlockEntity.A_MODE && p.action() <= CoastalBatteryBlockEntity.A_FREQ)
                b.act(p.action(), p.data(), context.player());
            else if (menu.machine() instanceof DockCraneBlockEntity c && c.getBlockPos().equals(p.pos()) && !c.isRemoved() && Access.mayUse(c, context.player())
                    && p.action() == DockCraneBlockEntity.A_TOGGLE)
                c.act(p.action(), p.data(), context.player());
            else if (menu.machine() instanceof ServiceArmBlockEntity s && s.getBlockPos().equals(p.pos()) && !s.isRemoved() && Access.mayUse(s, context.player())
                    && p.action() >= ServiceArmBlockEntity.A_MODE && p.action() <= ServiceArmBlockEntity.A_LAST)
                s.act(p.action(), p.data(), context.player());
            else if (menu.machine() instanceof CargoRampBlockEntity r && r.getBlockPos().equals(p.pos()) && !r.isRemoved() && Access.mayUse(r, context.player())
                    && p.action() >= CargoRampBlockEntity.A_FIRST && p.action() <= CargoRampBlockEntity.A_LAST)
                r.act(p.action(), p.data(), context.player());
            else if (menu.machine() instanceof TurntableBlockEntity t && t.getBlockPos().equals(p.pos()) && !t.isRemoved() && Access.mayUse(t, context.player())
                    && p.action() >= TurntableBlockEntity.A_FIRST && p.action() <= TurntableBlockEntity.A_LAST)
                context.player().displayClientMessage(t.answer(t.order(p.action())), true);
        });
    }
}
