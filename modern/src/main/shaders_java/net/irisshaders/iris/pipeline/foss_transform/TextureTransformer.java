package net.irisshaders.iris.pipeline.foss_transform;

import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

/** Iris's stage- and sampler-type-specific custom texture renaming, using Cornea's AST. */
public final class TextureTransformer {
    private TextureTransformer() { }

    public static void transform(Transformer shader, Parameters parameters, int version) {
        parameters.getTextureMap().forEach((key, replacement) -> {
            if (key.third() != parameters.getTextureStage()) return;
            String name = key.first();
            // Cornea needs these otherwise legal variable names escaped before parsing.
            if (name.equals("texture") || name.equals("sample") && version < 400) name = "iris_renamed_" + name;
            String declarationName = name;
            boolean[] matches = {false};
            shader.mutateTree(tree -> {
                for (var external : tree.external_declaration()) {
                    var declaration = external.declaration();
                    if (declaration == null || declaration.init_declarator_list() == null) continue;
                    var list = declaration.init_declarator_list();
                    var first = list.single_declaration();
                    int type = first.fully_specified_type().type_specifier().type_specifier_nonarray().getStart().getType();
                    if (!isTypeValid(key.second(), type)) continue;
                    if (first.typeless_declaration() != null && first.typeless_declaration().IDENTIFIER() != null
                            && first.typeless_declaration().IDENTIFIER().getText().equals(declarationName)) matches[0] = true;
                    for (var member : list.typeless_declaration()) {
                        if (member.IDENTIFIER().getText().equals(declarationName)) matches[0] = true;
                    }
                }
            });
            if (matches[0]) shader.rename(name, replacement);
        });
    }

    private static boolean isTypeValid(TextureType expected, int type) {
        return switch (expected) {
            case TEXTURE_1D -> type == GLSLLexer.SAMPLER1D || type == GLSLLexer.ISAMPLER1D || type == GLSLLexer.USAMPLER1D;
            case TEXTURE_2D -> type == GLSLLexer.SAMPLER2D || type == GLSLLexer.ISAMPLER2D || type == GLSLLexer.USAMPLER2D;
            case TEXTURE_3D -> type == GLSLLexer.SAMPLER3D || type == GLSLLexer.ISAMPLER3D || type == GLSLLexer.USAMPLER3D;
            case TEXTURE_RECTANGLE -> type == GLSLLexer.SAMPLER2DRECT || type == GLSLLexer.ISAMPLER2DRECT || type == GLSLLexer.USAMPLER2DRECT;
        };
    }
}
