package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public record CcTarget(int x, int z, String label, int source, String by, long timeMs) {

    public static final int SRC_TABLE = 0, SRC_RECON = 1, SRC_DESIGNATOR = 2, SRC_COMMAND = 3;

    public static final int SRC_COUNTER = 4;
    private static final int LIMIT = 29_999_000;

    public CcTarget {
        x = Math.max(-LIMIT, Math.min(LIMIT, x));
        z = Math.max(-LIMIT, Math.min(LIMIT, z));
        label = label == null ? "" : label.length() > 64 ? label.substring(0, 64) : label;
        by = by == null ? "" : by.length() > 32 ? by.substring(0, 32) : by;
    }

    public String sourceKey() { return "gui.wmd.cc.des.src." + Math.max(0, Math.min(SRC_COUNTER, source)); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("x", x); t.putInt("z", z); t.putString("l", label); t.putInt("s", source); t.putString("b", by); t.putLong("t", timeMs);
        return t;
    }

    public static CcTarget load(CompoundTag t) {
        if (t == null || !t.contains("x")) return null;
        return new CcTarget(t.getInt("x"), t.getInt("z"), t.getString("l"), t.getInt("s"), t.getString("b"), t.getLong("t"));
    }

    public static CcTarget of(UUID owner) {
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) if (b.designated() != null) return b.designated();
        return null;
    }

    public static int designate(ServerPlayer player, List<CcBuildingBlockEntity> buildings, CcTarget t) {
        int n = 0;
        for (CcBuildingBlockEntity b : buildings) {
            if (b == null || b.isRemoved() || !player.getUUID().equals(b.getOwner())) continue;
            b.designate(t);
            n++;
        }
        if (n == 0) {
            player.displayClientMessage(Component.translatable("message.wmd.cc.des.owner_only").withStyle(ChatFormatting.RED), true);
            return 0;
        }
        if (t == null) player.displayClientMessage(Component.translatable("message.wmd.cc.des.cleared"), true);
        else {
            player.displayClientMessage(Component.translatable("message.wmd.cc.des.set", t.x(), t.z(), Component.translatable(t.label()),
                    Component.translatable(t.sourceKey())).withStyle(ChatFormatting.RED), false);
            player.level().playSound(null, player.blockPosition(), t.source() == SRC_DESIGNATOR ? WmdSounds.TOOL_DESIGNATOR.get() : WmdSounds.CC_PING.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
        }
        Wmd.LOGGER.info("Command center: {} {} the designated target{}", player.getGameProfile().getName(), t == null ? "cleared" : "set",
                t == null ? "" : " to " + t.x() + " " + t.z() + " (" + t.label() + ")");
        return n;
    }

    public static void beam(ServerPlayer player, double x, double y, double z) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        for (int i = 0; i < 24; i++)
            sl.sendParticles(player, ParticleTypes.END_ROD, true, x, y + i * 1.5, z, 2, 0.05, 0.3, 0.05, 0.0);
        sl.sendParticles(player, ParticleTypes.FLASH, true, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
