package net.irisshaders.iris.pipeline.foss_transform;

import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.weather.WeatherGlsl;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;

import java.util.EnumMap;
import java.util.Map;

/** Feeds Angelica's unchanged weather expansion to the existing, already transformed shader-pack program. */
public final class WeatherVertices {
    public static final Map<String, Integer> ATTRIBUTES = Map.of(
            "iris_WeatherCorner", 0, "iris_InstColumnSpan", 1, "iris_InstJitter", 2,
            "iris_InstWeatherParams", 3, "iris_InstWeatherColor", 4, "iris_InstWeatherLayer", 5);

    public static String declarations() {
        return "uniform vec4 iris_WeatherParams0; uniform vec4 iris_WeatherParams1;"
                + "uniform vec4 iris_WeatherParams2; uniform vec4 iris_WeatherParams3;"
                + "in vec2 iris_WeatherCorner; in vec4 iris_InstColumnSpan; in vec4 iris_InstJitter;"
                + "in vec4 iris_InstWeatherParams; in vec4 iris_InstWeatherColor; in vec4 iris_InstWeatherLayer;";
    }

    public static String generator() {
        return "void iris_generateWeatherVertex(){vec4 nocturna_vertex;vec4 nocturna_uv;vec4 nocturna_light;"
                + WeatherGlsl.weatherPrologue("iris_", "iris_", "iris_WeatherCorner", "nocturna_vertex",
                "iris_weatherColor", "nocturna_uv", "nocturna_light", "\n")
                + "iris_weatherPosition=nocturna_vertex.xyz;iris_weatherUV=nocturna_uv.xy;"
                + "iris_weatherLight=ivec2(nocturna_light.xy);}";
    }

    public static Map<ShaderType, String> patch(Map<ShaderType, String> sources) {
        String source = sources.get(ShaderType.VERTEX);
        var tree = ShaderParser.parseShader(source).full();
        var transform = new Transformer(tree);
        String[][] fields = {{"iris_Position", "iris_weatherPosition"}, {"iris_Color", "iris_weatherColor"},
                {"iris_UV0", "iris_weatherUV"}, {"iris_UV2", "iris_weatherLight"}};
        for (var field : fields) {
            transform.removeVariable(field[0]); transform.rename(field[0], field[1]);
        }
        for (String declaration : ("vec3 iris_weatherPosition;vec4 iris_weatherColor;vec2 iris_weatherUV;ivec2 iris_weatherLight;"
                + declarations()).split(";")) transform.injectVariable(declaration + ";");
        transform.injectFunction(generator());
        transform.prependMain("iris_generateWeatherVertex();");
        String version = source.substring(0, source.indexOf('\n') + 1);
        var out = new EnumMap<ShaderType, String>(ShaderType.class); out.putAll(sources);
        out.put(ShaderType.VERTEX, version + ShaderPrinter.getFormattedShader(tree));
        return out;
    }
}
