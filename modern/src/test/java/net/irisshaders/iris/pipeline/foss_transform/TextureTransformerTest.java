package net.irisshaders.iris.pipeline.foss_transform;

import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.parameter.ComputeParameters;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.irisshaders.iris.pipeline.transform.parameter.TextureStageParameters;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TextureTransformerTest {
    @Test
    void matchesTheCompleteIrisTextureTransformer() throws Exception {
        try (var stream = getClass().getResourceAsStream("/iris-texture-reference.json")) {
            assertNotNull(stream);
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(74, data.getAsJsonArray("fixtures").size());
            for (var entry : data.getAsJsonArray("fixtures")) {
                var fixture = entry.getAsJsonObject();
                var mappings = new Object2ObjectLinkedOpenHashMap<Tri<String, TextureType, TextureStage>, String>();
                for (var value : fixture.getAsJsonArray("rules")) {
                    var rule = value.getAsJsonObject();
                    mappings.put(new Tri<>(rule.get("name").getAsString(), TextureType.valueOf(rule.get("type").getAsString()),
                            TextureStage.valueOf(rule.get("stage").getAsString())), rule.get("replacement").getAsString());
                }
                int version = fixture.get("version").getAsInt();
                String source = ShaderTransformer.renameReservedWords(fixture.get("source").getAsString(), version);
                var tree = ShaderParser.parseShader(source).full();
                TextureTransformer.transform(new Transformer(tree), new TextureStageParameters(Patch.COMPOSITE,
                        TextureStage.valueOf(fixture.get("stage").getAsString()), mappings), version);
                String expected = ShaderTransformer.renameReservedWords(fixture.get("expected").getAsString(), version);
                assertEquals(ShaderParser.parseShader(expected).full().getText(), tree.getText(), fixture.get("name").getAsString());
            }
        }
    }

    @Test
    void compositeAndComputeEntryPointsApplyTheMapping() throws Exception {
        var mappings = mapping(TextureStage.DEFERRED, "customtex0");
        var fragment = patch(ShaderType.FRAGMENT, "#version 450 core\nuniform sampler3D depthtex0;out vec4 color;"
                        + "void main(){color=texture(depthtex0,vec3(0));}",
                new TextureStageParameters(Patch.COMPOSITE, TextureStage.DEFERRED, mappings));
        assertTrue(fragment.contains("uniform sampler3D customtex0;"), fragment);
        assertFalse(fragment.contains("uniform sampler3D depthtex0;"));

        var compute = patch(ShaderType.COMPUTE, "#version 450 core\nlayout(local_size_x=1) in;uniform sampler3D depthtex0;"
                        + "layout(rgba32f,binding=0) uniform image2D resultImage;"
                        + "void main(){imageStore(resultImage,ivec2(0),texture(depthtex0,vec3(0)));}",
                new ComputeParameters(Patch.COMPUTE, TextureStage.DEFERRED, mappings));
        assertTrue(compute.contains("uniform sampler3D customtex0;"), compute);
        assertTrue(compute.contains("texture(customtex0,"), compute);
    }

    @Test
    void mappingIsPartOfTransformationCacheIdentity() {
        Map<ShaderType, String> sources = Map.of(ShaderType.FRAGMENT, "source");
        var first = new TextureStageParameters(Patch.COMPOSITE, TextureStage.DEFERRED, mapping(TextureStage.DEFERRED, "customtex0"));
        var second = new TextureStageParameters(Patch.COMPOSITE, TextureStage.DEFERRED, mapping(TextureStage.DEFERRED, "customtex1"));
        assertNotEquals(new ShaderTransformer.TransformKey<>(Patch.COMPOSITE, sources, first),
                new ShaderTransformer.TransformKey<>(Patch.COMPOSITE, sources, second));
    }

    private static Object2ObjectLinkedOpenHashMap<Tri<String, TextureType, TextureStage>, String> mapping(TextureStage stage, String target) {
        var map = new Object2ObjectLinkedOpenHashMap<Tri<String, TextureType, TextureStage>, String>();
        map.put(new Tri<>("depthtex0", TextureType.TEXTURE_3D, stage), target);
        return map;
    }

    // Exercise the actual pipeline transformation while avoiding the game-directory-dependent disk-cache wrapper.
    @SuppressWarnings("unchecked")
    private static String patch(ShaderType stage, String source, Parameters parameters) throws Exception {
        var method = ShaderTransformer.class.getDeclaredMethod("transformInternal", String.class, Map.class, Patch.class, Parameters.class);
        method.setAccessible(true);
        var result = (Map<ShaderType, String>) method.invoke(null, "texture-reference", Map.of(stage, source), parameters.patch, parameters);
        return result.get(stage);
    }
}
