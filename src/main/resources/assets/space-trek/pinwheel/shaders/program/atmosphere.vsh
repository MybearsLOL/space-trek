#version 330 core

in vec3 Position;

out vec2 vNdc;

void main() {
    vNdc = Position.xy;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
