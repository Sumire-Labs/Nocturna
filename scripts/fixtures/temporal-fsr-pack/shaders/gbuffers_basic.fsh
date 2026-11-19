#version 120
varying vec4 tint;
/* DRAWBUFFERS:0 */
void main() {gl_FragData[0]=vec4(tint.rgb,1);}
