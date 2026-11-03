package com.ashraf.wmd;

public class CcBuildingRenderer extends StagedUnitRenderer<CcBuildingBlockEntity> {

    public CcBuildingRenderer() { super(CcBuildingSpec.NAME, 10, 19); }

    @Override
    public int getViewDistance() { return 192; }
}
