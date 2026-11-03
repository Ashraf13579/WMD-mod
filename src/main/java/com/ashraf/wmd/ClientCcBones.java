package com.ashraf.wmd;

import java.util.Optional;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

final class ClientCcBones implements CcBones {

    private final GeoModel<?> model;

    private ClientCcBones(GeoModel<?> model) { this.model = model; }

    static CcBones of(GeoModel<?> model) { return new ClientCcBones(model); }

    @Override
    public Optional<Bone> getBone(String name) { return model.getBone(name).map(B::new); }

    private record B(GeoBone b) implements Bone {
        @Override public void setRotX(float v) { b.setRotX(v); }
        @Override public void setRotY(float v) { b.setRotY(v); }
        @Override public void setRotZ(float v) { b.setRotZ(v); }
        @Override public void setPosX(float v) { b.setPosX(v); }
        @Override public void setPosY(float v) { b.setPosY(v); }
        @Override public void setPosZ(float v) { b.setPosZ(v); }
        @Override public void setScaleX(float v) { b.setScaleX(v); }
        @Override public void setScaleY(float v) { b.setScaleY(v); }
        @Override public void setScaleZ(float v) { b.setScaleZ(v); }
        @Override public void setHidden(boolean hidden) { b.setHidden(hidden); }
    }
}
