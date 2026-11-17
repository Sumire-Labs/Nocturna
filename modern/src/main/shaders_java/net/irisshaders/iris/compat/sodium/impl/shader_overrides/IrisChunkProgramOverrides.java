package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.debug.GLDebug;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.terrain.sampling.TerrainSampling;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexLayout;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.VertexFetchMode;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.cache.ProgramBinaryCache;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import net.irisshaders.iris.gl.program.RetainedPrograms;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.pipeline.ShaderLoadTimings;
import net.irisshaders.iris.pipeline.SodiumTerrainPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL43C;

import java.util.*;
import java.util.function.Function;

public class IrisChunkProgramOverrides {
    private static final List<ShaderType> RELEVANT_SHADER_TYPES = List.of(ShaderType.VERTEX, ShaderType.GEOM, ShaderType.TESS_CTRL, ShaderType.TESS_EVALUATE, ShaderType.FRAGMENT);

	private final EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>> programs = new EnumMap<>(IrisTerrainPass.class);
	private boolean shadersCreated = false;
	private int versionCounterForSodiumShaderReload = -1;

    private record SubmittedProgram(GlProgram.Deferred program, int restored, ProgramBinaryCache.Key cacheKey, RetainedPrograms.Ownership owner,
                                    Function<ShaderBindingContext, IrisChunkShaderInterface> factory) {
        GlProgram<IrisChunkShaderInterface> finish() {
            if (program == null) return GlProgram.fromLinkedProgram(restored, factory, id -> RetainedPrograms.release(owner, id));
            var linked = program.finish(factory, id -> RetainedPrograms.release(owner, id));
            if (cacheKey != null) ProgramBinaryCache.save(cacheKey, linked.handle());
            return linked;
        }

        void delete() {
            if (program != null) program.delete();
            else com.mojang.blaze3d.platform.GlStateManager.glDeleteProgram(restored);
        }
    }

	private GlShader.Deferred createShader(ShaderType type, IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		Optional<String> irisVertexShader;

        var info = pipeline.getPassInfo(pass);

        if (info == null) {
            throw new IllegalArgumentException("Unknown pass type " + pass);
        }

        irisVertexShader = info.sources().get(type);

		String source = irisVertexShader.orElse(null);

		if (source == null) {
			return null;
		}

		return GlShader.compileDeferred(type, "iris:" +
			"sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + "." + type.fileExtension, source);
	}

	private BlendModeOverride getBlendOverride(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
        return pipeline.getPassInfo(pass).blendModeOverride();
	}

	private List<BufferBlendOverride> getBufferBlendOverride(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
        return pipeline.getPassInfo(pass).bufferOverrides();
	}

	@Nullable
	private SubmittedProgram submitProgram(IrisTerrainPass pass, SodiumTerrainPipeline pipeline, RenderPassConfiguration configuration) {
		BlendModeOverride blendOverride = getBlendOverride(pass, pipeline);
		List<BufferBlendOverride> bufferOverrides = getBufferBlendOverride(pass, pipeline);
		float alpha = getAlphaReference(pass, pipeline);

        Map<ShaderType, String> sources = new EnumMap<>(ShaderType.class);
        for (var type : RELEVANT_SHADER_TYPES) pipeline.getPassInfo(pass).sources().get(type)
                .ifPresent(source -> sources.put(type, source));
        if (!sources.containsKey(ShaderType.VERTEX) || !sources.containsKey(ShaderType.FRAGMENT)) return null;
        Map<String, Integer> attributes = new java.util.TreeMap<>();
        var terrainPass = pass.toTerrainPass(configuration);
        if (terrainPass.vertexFetchMode() == VertexFetchMode.ATTRIBUTES) {
            int i = 0;
            var vertexType = configuration.getVertexTypeForPass(terrainPass);
            for (var attr : ChunkVertexLayout.requireAttributes(vertexType.getLayout()).getAttributes()) {
                attributes.put(attr.getName(), i++);
            }
        }

        var owner = RetainedPrograms.ownership(sources, attributes);
        String name = "sodium:chunk_shader_for_" + pass.getName();
        var cached = ProgramCreator.reuse(name, owner, ProgramBinaryCache.isFullCompileRunning()
            ? ProgramCreator.cacheKey(sources, attributes) : null);
        var key = cached == null ? ProgramCreator.cacheKey(sources, attributes) : null;
        if (cached == null) cached = ProgramCreator.load(name, key, owner);
        int restored = cached == null ? 0 : cached.finish();
        GlProgram.Deferred deferred = null;
        if (restored == 0) {
            var builder = GlProgram.builder("sodium:chunk_shader_for_" + pass.getName());
            if (key != null) ProgramBinaryCache.markRetrievable(builder.getProgramId());
            for (var type : RELEVANT_SHADER_TYPES) {
                var shader = createShader(type, pass, pipeline);
                if (shader != null) builder.attachShader(shader);
            }
            attributes.forEach(builder::bindAttribute);
            deferred = builder.linkDeferred();
        }
		boolean hasTessellation = sources.containsKey(ShaderType.TESS_CTRL) || sources.containsKey(ShaderType.TESS_EVALUATE);

		return new SubmittedProgram(deferred, restored, key, owner, (shader) -> {
			int handle = ((GlObject) shader).handle();
			ShaderBindingContext contextExt = shader;
			GLDebug.nameObject(GL43C.GL_PROGRAM, handle, "sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT));
			return new IrisChunkShaderInterface(handle, contextExt, pipeline, new ChunkShaderOptions(List.of(ChunkFogMode.SMOOTH), pass.toTerrainPass(configuration), TerrainSampling.NATIVE),
				hasTessellation, pass.isShadow(), blendOverride, bufferOverrides, alpha, pipeline.getCustomUniforms());
		});
	}

	private float getAlphaReference(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
        return pipeline.getPassInfo(pass).alphaTest().orElseThrow(() -> new IllegalStateException("Expected alpha test to be available")).reference();
	}

	public void createShaders(SodiumTerrainPipeline pipeline, RenderPassConfiguration configuration) {
		if (pipeline != null) {
			long buildStart = ShaderLoadTimings.now();
			pipeline.patchShaders(configuration);

			// Every pass is submitted before any is finished, so the driver can compile them together.
			List<Map.Entry<IrisTerrainPass, SubmittedProgram>> submitted = new ArrayList<>();

			for (IrisTerrainPass pass : IrisTerrainPass.values()) {
				SubmittedProgram program = pass.isShadow() && !pipeline.hasShadowPass() ? null : submitProgram(pass, pipeline, configuration);

				if (program != null) {
					submitted.add(Map.entry(pass, program));
				} else {
					this.programs.put(pass, null);
				}
			}

			for (int i = 0; i < submitted.size(); i++) {
				var entry = submitted.get(i);

				try {
					long waitStart = ShaderLoadTimings.now();
					this.programs.put(entry.getKey(), entry.getValue().finish());
					ShaderLoadTimings.record(ShaderLoadTimings.Stage.COMPILE_WAIT, waitStart);
				} catch (RuntimeException e) {
					for (int j = i + 1; j < submitted.size(); j++) {
						submitted.get(j).getValue().delete();
					}
					throw e;
				}
			}

			ShaderLoadTimings.record(ShaderLoadTimings.Stage.TERRAIN_BUILD, buildStart);
			ShaderLoadTimings.report("terrain programs");
		} else {
			for (GlProgram<?> program : this.programs.values()) {
				if (program != null) {
					program.delete();
				}
			}
			this.programs.clear();
		}

		shadersCreated = true;
	}

	@Nullable
	public GlProgram<IrisChunkShaderInterface> getProgramOverride(TerrainRenderPass pass, RenderPassConfiguration configuration) {
		if (versionCounterForSodiumShaderReload != Iris.getPipelineManager().getVersionCounterForSodiumShaderReload()) {
			versionCounterForSodiumShaderReload = Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
			deleteShaders();
		}

		WorldRenderingPipeline worldRenderingPipeline = Iris.getPipelineManager().getPipelineNullable();
		SodiumTerrainPipeline sodiumTerrainPipeline = null;

		if (worldRenderingPipeline != null) {
			sodiumTerrainPipeline = worldRenderingPipeline.getSodiumTerrainPipeline();
		}

		if (!shadersCreated) {
			createShaders(sodiumTerrainPipeline, configuration);
		}

		// Translucent terrain discards nearly transparent fragments on 26.1, but is still drawn as translucent rather
		// than cutout.
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			if (sodiumTerrainPipeline != null && !sodiumTerrainPipeline.hasShadowPass()) {
				throw new IllegalStateException("Shadow program requested, but the pack does not have a shadow pass?");
			}

			if (pass.isReverseOrder()) {
				return this.programs.get(IrisTerrainPass.SHADOW_TRANSLUCENT);
			} else if (pass.supportsFragmentDiscard()) {
				return this.programs.get(IrisTerrainPass.SHADOW_CUTOUT);
			} else {
				return this.programs.get(IrisTerrainPass.SHADOW);
			}
		} else {
			if (pass.isReverseOrder()) {
				return this.programs.get(IrisTerrainPass.GBUFFER_TRANSLUCENT);
			} else if (pass.supportsFragmentDiscard()) {
				return this.programs.get(IrisTerrainPass.GBUFFER_CUTOUT);
			} else {
				return this.programs.get(IrisTerrainPass.GBUFFER_SOLID);
			}
		}
	}

	public void deleteShaders() {
		for (GlProgram<?> program : this.programs.values()) {
			if (program != null) {
				program.delete();
			}
		}

		this.programs.clear();
		shadersCreated = false;
	}
}
