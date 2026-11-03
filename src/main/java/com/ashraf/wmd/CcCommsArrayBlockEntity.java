package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcCommsArrayBlockEntity extends CcModuleBlockEntity {

    private boolean relayOk;

    public CcCommsArrayBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_COMMS_ARRAY_BE.get(), pos, state, Balance.CC_COMMS_FE); }

    @Override public CcModule kind() { return CcModule.COMMS_ARRAY; }

    public boolean relayOk() { return relayOk; }

    public static boolean relayControlled(net.minecraft.server.MinecraftServer server, java.util.UUID owner) {
        if (server == null || owner == null) return false;
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (owner.equals(s.owner()) && s.type().equals("relay") && now < s.endMs() && OrbitData.controlled(s)) return true;
        return false;
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if ((now + 9) % 20 != 0 || level.getServer() == null) return;
        boolean r = isOnline() && relayControlled(level.getServer(), getOwner());
        if (r != relayOk) {
            relayOk = r;
            setChanged();
            sync();
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        boolean on = isOnline() && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final float a = on ? (float) Math.toRadians((tt * CcSpecC.DISH_DEG_PER_TICK) % 360.0) : 0.0F;
        model.getBone("dish0").ifPresent(b -> b.setRotY(a));
        model.getBone("dish1").ifPresent(b -> b.setRotY(-a * 0.7F));
        final boolean blink = on && ((long) tt) % 30 < 6;
        model.getBone("blink").ifPresent(b -> b.setHidden(!blink));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("relayOk", relayOk);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        relayOk = tag.getBoolean("relayOk");
    }
}
