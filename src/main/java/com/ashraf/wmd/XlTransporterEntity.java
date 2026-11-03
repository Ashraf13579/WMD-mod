package com.ashraf.wmd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class XlTransporterEntity extends MissileRobotEntity {

    private float erect, erectO;

    public XlTransporterEntity(EntityType<? extends XlTransporterEntity> type, Level level) { super(type, level); }

    @Override boolean heavy() { return true; }
    @Override int battery() { return Balance.XL_BATTERY; }
    @Override int maxHealth() { return Balance.XL_HEALTH; }
    @Override int reserve() { return Balance.XL_JOB_RESERVE; }
    @Override int fePerBlock() { return Balance.XL_FE_PER_BLOCK; }
    @Override double speed() { return Balance.XL_SPEED; }
    @Override int loadTicks() { return Balance.XL_LOAD_TICKS; }
    @Override int half() { return 2; }
    @Override int head() { return 4; }
    @Override ItemStack asItem() { return new ItemStack(Wmd.XL_TRANSPORTER_ITEM.get()); }
    @Override String infoKey() { return "message.wmd.xl.info."; }

    @Override
    void clientTick() {
        super.clientTick();
        erectO = erect;
        boolean up = state() == LOADING || state() == UNLOADING;
        erect += ((up ? 1.0F : 0.0F) - erect) * 0.04F;
    }

    public float erect(float pt) { return erectO + (erect - erectO) * pt; }
}
