#version 150

// The targeting pod's HEAT MASK (ClientPodThermal): the warm bodies the lens can see, drawn again in plain white into a picture of their own.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
