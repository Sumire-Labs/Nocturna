// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package org.embeddedt.embeddium.impl.render.weather;
public final class WeatherGlsl {
    // wHalfWidth is vanilla's rainXCoords table.
    private static final String WEATHER_PROLOGUE = """
        vec2 {p}wColumn = {span}.xy;
        float {p}wBottom = {span}.z;
        float {p}wTop = {span}.w;
        float {p}wLightV = floor({params}.x / 65536.0);
        vec2 {p}wLightmap = vec2({params}.x - {p}wLightV * 65536.0, {p}wLightV);
        bool {p}wProvider = {params}.w > 0.5;
        vec3 {p}wTranslate = {p}WeatherParams0.xyz;
        float {p}wInvRadius = {p}wProvider ? {p}WeatherParams3.x : {p}WeatherParams0.w;
        vec2 {p}wCameraFrac = {p}WeatherParams1.xy;
        float {p}wPartialTicks = {p}WeatherParams1.z;
        float {p}wAge = {p}wProvider ? {p}WeatherParams2.w : {p}WeatherParams1.w;
        float {p}wTick = {p}wProvider ? {p}WeatherParams2.y : {p}WeatherParams2.x;
        float {p}wStrength = {p}wProvider ? 1.0 : {p}WeatherParams2.z;

        vec2 {p}wHalfWidth = vec2(-{p}wColumn.y, {p}wColumn.x) * (0.5 / sqrt(dot({p}wColumn, {p}wColumn)));
        vec2 {p}wCornerXZ = {p}wColumn + vec2(0.5) + ({corner}.x * 2.0 - 1.0) * {p}wHalfWidth;
        float {p}wCornerY = mix({p}wBottom, {p}wTop, {corner}.y);
        {vertex} = vec4(vec3({p}wCornerXZ.x, {p}wCornerY, {p}wCornerXZ.y) + {p}wTranslate, 1.0);

        float {p}wDistance = length({p}wColumn + vec2(0.5) - {p}wCameraFrac) * {p}wInvRadius;
        float {p}wFade = 1.0 - {p}wDistance * {p}wDistance;
        float {p}wScroll = (mod({p}wTick + {params}.y, {layer}.x) + {p}wPartialTicks) / {layer}.x * {params}.z;
        vec2 {p}wDrift = vec2({jitter}.x + {p}wAge * {layer}.y * {jitter}.y, {jitter}.z + {p}wAge * {layer}.z * {jitter}.w);
        float {p}wAlpha = {p}wFade * {layer}.w + 0.5;

        {tex0} = vec4({corner}.x + {p}wDrift.x, {p}wCornerY * 0.25 + {p}wScroll + {p}wDrift.y, 0.0, 1.0);
        {tex1} = vec4({p}wLightmap, 0.0, 1.0);
        {color} = floor(clamp(vec4({colorAttr}.rgb, {p}wAlpha * {colorAttr}.a * {p}wStrength), 0.0, 1.0) * 255.0) / 255.0;""";

    public static String weatherPrologue(String uniformPrefix, String attribPrefix, String corner,
        String vertex, String color, String tex0, String tex1, String sep) {
        return WEATHER_PROLOGUE
            .replace("{p}", uniformPrefix)
            .replace("{span}", attribPrefix + "InstColumnSpan")
            .replace("{jitter}", attribPrefix + "InstJitter")
            .replace("{params}", attribPrefix + "InstWeatherParams")
            .replace("{layer}", attribPrefix + "InstWeatherLayer")
            .replace("{colorAttr}", attribPrefix + "InstWeatherColor")
            .replace("{corner}", corner)
            .replace("{vertex}", vertex)
            .replace("{color}", color)
            .replace("{tex0}", tex0)
            .replace("{tex1}", tex1)
            .replace("\n", sep);
    }

}
