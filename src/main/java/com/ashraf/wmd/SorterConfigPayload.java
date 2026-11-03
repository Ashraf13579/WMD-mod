package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SorterConfigPayload(BlockPos pos, int op, int face, int value, ItemStack item) implements CustomPacketPayload {

    public static final int MODE = 0, ALLOW = 1, PRIORITY = 2, ROUND_ROBIN = 3, FILTER = 4;

    public static final Type<SorterConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sorter_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SorterConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeByte(p.op()); buf.writeByte(p.face()); buf.writeVarInt(p.value()); ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.item()); },
            buf -> new SorterConfigPayload(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SorterConfigPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp) || !(sp.containerMenu instanceof SorterMenu m) || !m.stillValid(sp) || !m.pos().equals(p.pos())) return;
            if (!(sp.level().getBlockEntity(p.pos()) instanceof SorterBlockEntity be)) return;
            if (!Access.mayUse(be, sp)) { Access.deny(be, sp); return; }
            if (p.face() < 0 || p.face() > 5) return;
            Direction f = Direction.from3DDataValue(p.face());
            switch (p.op()) {
                case MODE -> be.setMode(f, SorterBlock.Face.values()[Math.floorMod(p.value(), SorterBlock.Face.values().length)]);
                case ALLOW -> be.toggleAllow(f);
                case PRIORITY -> be.addPriority(f, Integer.signum(p.value()));
                case ROUND_ROBIN -> be.toggleRoundRobin();
                case FILTER -> be.setFilter(f, p.value(), p.item());
                default -> {}
            }
        });
    }
}
