#version 330 core

// Full-screen quad: Position is already in normalised device coordinates (-1 .. 1).
in vec3 Position;

out vec2 vNdc;

void main() {
    vNdc = Position.xy;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
