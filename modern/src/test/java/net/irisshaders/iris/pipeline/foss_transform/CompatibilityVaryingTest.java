package net.irisshaders.iris.pipeline.foss_transform;

import com.google.gson.JsonParser;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.shadowed.org.antlr.v4.runtime.tree.ParseTree;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityVaryingTest {
    private static final String VERTEX = "#version 450 core\nvoid main(){gl_Position=vec4(0,0,0,1);}";

    @Test
    void missingMellowStyleOutputIsDeclaredAndZeroInitialized() {
        var pair = patch(VERTEX, "#version 450 core\nin vec3 ViewPos;out vec4 color;void main(){color=vec4(ViewPos,1);}");
        assertTrue(pair.vertex.findQualifiers(GLSLLexer.OUT).containsKey("ViewPos"));
        assertTrue(pair.vertex.hasAssigment("ViewPos"));
        assertTrue(pair.vertexText().contains("ViewPos = vec3(0.0)"), pair.vertexText());
        assertFalse(pair.vertexText().contains("vec3ViewPos"));
    }

    @Test
    void preservesQualifiersWithoutMutatingConsumerOrInitializerOrder() {
        String fragment = "#version 450 core\nlayout(location=3) flat highp in ivec3 index;out vec4 color;"
                + "void main(){color=vec4(index,1);}";
        var pair = patch(VERTEX, fragment);
        assertEquals(formatted(fragment), pair.fragmentText());
        var output = pair.vertex.findQualifiers(GLSLLexer.OUT).get("index").fully_specified_type();
        assertTrue(output.getText().contains("layout(location=3)"));
        assertTrue(output.getText().contains("flat"));
        assertTrue(output.getText().contains("highp"));
        assertTrue(output.getText().contains("out"));
        assertTrue(pair.vertexText().indexOf("index =") < pair.vertexText().indexOf("gl_Position ="));
    }

    @Test
    void initializesNewGlobalEvenWhenLocalOrPrefixAssignmentsExist() {
        var pair = patch("#version 450 core\nvoid helper(){vec3 ViewPos;ViewPos=vec3(1);}" +
                        "void main(){vec3 ViewPosition;ViewPosition=vec3(2);helper();gl_Position=vec4(0,0,0,1);}",
                "#version 450 core\nin vec3 ViewPos;out vec4 color;void main(){color=vec4(ViewPos,1);}");
        assertTrue(pair.vertexText().contains("ViewPos = vec3(0.0)"), pair.vertexText());
    }

    @Test
    void leavesAssignedOutputsAndUnusedInputsUnchanged() {
        String vertex = "#version 450 core\nout vec3 value;void main(){value=vec3(4);gl_Position=vec4(0,0,0,1);}";
        var pair = patch(vertex, "#version 450 core\nin vec3 value, unused;out vec4 color;void main(){color=vec4(value,1);}");
        assertEquals(formatted(vertex), pair.vertexText());
        assertFalse(pair.vertex.findQualifiers(GLSLLexer.OUT).containsKey("unused"));
    }

    @Test
    void patchesEachUsedMemberOfADeclarationAndIsIdempotent() {
        var pair = patch(VERTEX, "#version 450 core\nin vec3 first, second;out vec4 color;void main(){color=vec4(first+second,1);}");
        assertEquals(2, pair.vertex.findQualifiers(GLSLLexer.OUT).size());
        assertTrue(pair.vertex.hasAssigment("first"));
        assertTrue(pair.vertex.hasAssigment("second"));
        String once = pair.vertexText();
        CompTransformer.transformGrouped(pair.stages, null);
        assertEquals(once, pair.vertexText());
    }

    @Test
    void supportsIrisNumericDefaultsAndSkipsNonNumericInputs() {
        for (String type : new String[]{"float", "int", "uint", "bool", "double", "vec2", "vec3", "vec4",
                "ivec3", "uvec4", "bvec2", "dvec3", "mat3", "mat2x4", "dmat4x2"}) {
            var pair = patch(VERTEX, "#version 450 core\nin " + type + " value;void main(){" + type + " copy=value;}");
            assertTrue(pair.vertex.findQualifiers(GLSLLexer.OUT).containsKey("value"), type);
            assertTrue(pair.vertex.hasAssigment("value"), type);
            assertFalse(pair.vertexText().contains(type + "value"), type);
        }
        var pair = patch(VERTEX, "#version 450 core\nstruct Payload{vec3 color;};in Payload value;void main(){vec3 copy=value.color;}");
        assertTrue(pair.vertex.findQualifiers(GLSLLexer.OUT).isEmpty());
    }

    @Test
    void geometryArrayInputProducesAScalarVertexOutput() {
        var stages = new EnumMap<ShaderType, Transformer>(ShaderType.class);
        stages.put(ShaderType.VERTEX, transformer(VERTEX));
        stages.put(ShaderType.GEOMETRY, transformer("#version 450 core\nlayout(points) in;layout(points,max_vertices=1) out;"
                + "in vec3 value[];void main(){gl_Position=vec4(value[0],1);EmitVertex();EndPrimitive();}"));
        CompTransformer.transformGrouped(stages, null);
        var output = stages.get(ShaderType.VERTEX).findQualifiers(GLSLLexer.OUT).get("value");
        assertNotNull(output);
        assertNull(output.typeless_declaration().array_specifier());
        assertNull(output.fully_specified_type().type_specifier().array_specifier());
    }

    @Test
    void matchesOutputsFromTheCompletePinnedIrisTransformer() throws Exception {
        try (var input = getClass().getResourceAsStream("/iris-varying-reference.json")) {
            assertNotNull(input);
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var element : data.getAsJsonArray("fixtures")) {
                var fixture = element.getAsJsonObject();
                var stages = new EnumMap<ShaderType, Transformer>(ShaderType.class);
                fixture.getAsJsonObject("stages").entrySet().forEach(entry ->
                        stages.put(stage(entry.getKey()), transformer(entry.getValue().getAsString())));
                CompTransformer.transformGrouped(stages, null);
                for (var entry : fixture.getAsJsonObject("expected").entrySet()) {
                    var expected = transformer(entry.getValue().getAsString());
                    assertEquals(interfaceAndFunctions(expected), interfaceAndFunctions(stages.get(stage(entry.getKey()))),
                            fixture.get("name").getAsString() + " " + entry.getKey());
                }
            }
        }
    }

    private static ShaderType stage(String name) {
        return switch (name) {
            case "GEOMETRY" -> ShaderType.GEOMETRY;
            case "TESS_CONTROL" -> ShaderType.TESSELATION_CONTROL;
            case "TESS_EVAL" -> ShaderType.TESSELATION_EVAL;
            default -> ShaderType.valueOf(name);
        };
    }

    // Compare types/qualifiers, initialization values and placement, and the remaining ordered function bodies.
    // Independent leading zero assignments may be emitted in a different order by the two AST libraries.
    private static Map<String, Object> interfaceAndFunctions(Transformer transformer) {
        var result = new TreeMap<String, Object>();
        var outputs = new TreeMap<String, String>();
        transformer.findQualifiers(GLSLLexer.OUT).forEach((name, declaration) -> {
            var type = declaration.fully_specified_type();
            outputs.put(name, type.type_qualifier().getText() + " " + canonicalZero(type.type_specifier().getText()));
        });
        result.put("outputs", outputs);
        transformer.mutateTree(tree -> {
            for (var function : descendants(tree, GLSLParser.Function_definitionContext.class)) {
                String name = function.function_prototype().IDENTIFIER().getText();
                var statements = function.compound_statement_no_new_scope().statement_list();
                var ordered = new ArrayList<String>();
                var initializers = new TreeMap<String, String>();
                boolean prefix = name.equals("main");
                if (statements != null) for (var statement : statements.statement()) {
                    String text = canonicalTokens(statement);
                    int equals = text.indexOf('=');
                    if (prefix && equals > 0 && outputs.containsKey(text.substring(0, equals)))
                        initializers.put(text.substring(0, equals), text.substring(equals + 1));
                    else { prefix = false; ordered.add(text); }
                }
                result.put(name, List.of(initializers, ordered));
            }
        });
        return result;
    }

    private static String canonicalZero(String text) {
        return text.replaceAll("(?<![\\w.])0\\.0(?:lf|f)?(?![\\w.])", "0")
                .replaceAll("(?<![\\w.])0[uU](?![\\w.])", "0")
                .replaceAll("\\b(d?mat)([234])x\\2\\b", "$1$2");
    }

    private static String canonicalTokens(ParseTree tree) {
        if (tree.getChildCount() == 0) return canonicalZero(tree.getText());
        var result = new StringBuilder();
        for (int i = 0; i < tree.getChildCount(); i++) result.append(canonicalTokens(tree.getChild(i)));
        return result.toString();
    }

    private static <T extends ParseTree> List<T> descendants(ParseTree tree, Class<T> type) {
        var result = new ArrayList<T>();
        if (type.isInstance(tree)) result.add(type.cast(tree));
        for (int i = 0; i < tree.getChildCount(); i++) result.addAll(descendants(tree.getChild(i), type));
        return result;
    }

    private static Pair patch(String vertex, String fragment) {
        var stages = new EnumMap<ShaderType, Transformer>(ShaderType.class);
        stages.put(ShaderType.VERTEX, transformer(vertex));
        stages.put(ShaderType.FRAGMENT, transformer(fragment));
        CompTransformer.transformGrouped(stages, null);
        return new Pair(stages, stages.get(ShaderType.VERTEX), stages.get(ShaderType.FRAGMENT));
    }

    private static Transformer transformer(String source) { return new Transformer(ShaderParser.parseShader(source).full()); }
    private static String formatted(String source) { return ShaderPrinter.getFormattedShader(ShaderParser.parseShader(source).full()); }
    private record Pair(EnumMap<ShaderType, Transformer> stages, Transformer vertex, Transformer fragment) {
        String vertexText() { return text(vertex); }
        String fragmentText() { return text(fragment); }
        private static String text(Transformer transformer) {
            String[] result = new String[1];
            transformer.mutateTree(tree -> result[0] = ShaderPrinter.getFormattedShader(tree));
            return result[0];
        }
    }
}
