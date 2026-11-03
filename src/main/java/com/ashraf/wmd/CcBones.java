package com.ashraf.wmd;

import java.util.Optional;

public interface CcBones {

    Optional<Bone> getBone(String name);

    interface Bone {
        void setRotX(float v);
        void setRotY(float v);
        void setRotZ(float v);
        void setPosX(float v);
        void setPosY(float v);
        void setPosZ(float v);
        void setScaleX(float v);
        void setScaleY(float v);
        void setScaleZ(float v);
        void setHidden(boolean hidden);
    }
}
