#version 120
uniform bool nocturnaTemporalEnabled;
uniform vec2 nocturnaTemporalJitter;
uniform float viewWidth;
uniform float viewHeight;
varying vec4 tint;
void main() {
    gl_Position=ftransform();
    if(nocturnaTemporalEnabled) gl_Position.xy+=nocturnaTemporalJitter*2.0/vec2(viewWidth,viewHeight)*gl_Position.w;
    tint=gl_Color;
}
