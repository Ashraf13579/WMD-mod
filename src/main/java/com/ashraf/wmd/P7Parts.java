package com.ashraf.wmd;

import java.util.Set;

interface P7Parts {

    Set<String> movedBones();

    float[] boneShift(String bone, float pt);

    default boolean boneLit(String bone) { return false; }

    default float[][] cables(float pt) { return new float[0][]; }
}
