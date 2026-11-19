#version 120
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferPreviousModelView;
uniform mat4 gbufferPreviousProjection;
uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;
uniform bool nocturnaTemporalEnabled;
uniform vec2 nocturnaTemporalJitter;
uniform float viewWidth;
uniform float viewHeight;
varying vec2 texcoord;
/*
const int colortex7Format = RG16F;
*/
/* DRAWBUFFERS:07 */
void main() {
    vec2 unjittered=texcoord-(nocturnaTemporalEnabled?nocturnaTemporalJitter/vec2(viewWidth,viewHeight):vec2(0));
    vec4 view=gbufferProjectionInverse*vec4(unjittered*2.0-1.0,texture2D(depthtex0,texcoord).r*2.0-1.0,1);
    view/=view.w;
    vec4 world=gbufferModelViewInverse*view;
    world.xyz+=cameraPosition-previousCameraPosition;
    vec4 previous=gbufferPreviousProjection*gbufferPreviousModelView*world;
    vec2 motion=previous.xy/previous.w*0.5+0.5-unjittered;
    gl_FragData[0]=texture2D(colortex0,texcoord);
    gl_FragData[1]=vec4(motion,0,1);
}
