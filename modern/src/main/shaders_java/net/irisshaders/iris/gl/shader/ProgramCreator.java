// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.irisshaders.iris.gl.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pipeline.ShaderLoadTimings;
import net.irisshaders.iris.cache.ProgramBinaryCache;
import net.irisshaders.iris.gl.program.RetainedPrograms;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.debug.GLDebug;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.KHRDebug;

import java.util.List;
import java.util.Map;

public class ProgramCreator {
	private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

	public static final Map<String, Integer> DEFAULT_ATTRIBUTE_LOCATIONS = Map.of(
		"iris_Entity", 11,
		"mc_Entity", 11,
		"mc_midTexCoord", 12,
		"at_tangent", 13,
		"at_midBlock", 14,
		"Position", 0,
		"UV0", 1
	);

    /** Complete native linker inputs; deterministic attribute order also makes keys stable across JVM starts. */
    public static ProgramBinaryCache.Key cacheKey(Map<ShaderType, String> sources, Map<String, Integer> attributes) {
        if (!ProgramBinaryCache.isEnabled()) return null;
        var key = ProgramBinaryCache.key();
        new java.util.TreeMap<>(attributes).forEach(key::attribute);
        if (sources.containsKey(ShaderType.COMPUTE)) {
            key.stage(ShaderType.COMPUTE.id, sources.get(ShaderType.COMPUTE));
        } else {
            key.stage(ShaderType.VERTEX.id, sources.get(ShaderType.VERTEX));
            for (var type : new ShaderType[]{ShaderType.GEOM, ShaderType.TESS_CTRL, ShaderType.TESS_EVALUATE}) {
                if (sources.get(type) != null) key.stage(type.id, sources.get(type));
            }
            key.stage(ShaderType.FRAGMENT.id, sources.get(ShaderType.FRAGMENT));
        }
        return key;
    }

    public static Submitted load(String name, ProgramBinaryCache.Key key) {
        return load(name, key, null);
    }

    public static Submitted reuse(String name, RetainedPrograms.Ownership owner, ProgramBinaryCache.Key key) {
        int program = RetainedPrograms.take(owner);
        if (program == 0) return null;
        if (key != null && !ProgramBinaryCache.saveRetained(key, program)) {
            GlStateManager.glDeleteProgram(program);
            return null;
        }
        GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);
        return new Submitted(name, program, List.of(), null, owner, true);
    }

    public static Submitted load(String name, ProgramBinaryCache.Key key, RetainedPrograms.Ownership owner) {
        if (key == null) return null;
        int program = ProgramBinaryCache.load(key);
        if (program == 0) return null;
        GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);
        return new Submitted(name, program, List.of(), null, owner, true);
    }

	/**
	 * Compiles and links a program without waiting for either to finish, so that the driver can work on it while
	 * further programs are submitted. The program must not be used before {@link Submitted#finish()} returns.
	 */
	public static Submitted submit(String name, List<GlShader.Deferred> shaders, Map<String, Integer> attributeLocations) {
        return submit(name, shaders, attributeLocations, null);
    }

    public static Submitted submit(String name, List<GlShader.Deferred> shaders,
                                   Map<String, Integer> attributeLocations, ProgramBinaryCache.Key cacheKey) {
		return submit(name, shaders, attributeLocations, cacheKey, null);
    }

    public static Submitted submit(String name, List<GlShader.Deferred> shaders,
                                   Map<String, Integer> attributeLocations, ProgramBinaryCache.Key cacheKey,
                                   RetainedPrograms.Ownership owner) {
        RenderSystem.assertOnRenderThread();

		int program = GlStateManager.glCreateProgram();
        if (cacheKey != null) ProgramBinaryCache.markRetrievable(program);

		attributeLocations.forEach((attribute, location) -> GlStateManager._glBindAttribLocation(program, location, attribute));

		for (GlShader.Deferred shader : shaders) {
			shader.attachTo(program);
		}

		GlStateManager.glLinkProgram(program);

		GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);

		return new Submitted(name, program, shaders, cacheKey, owner, false);
	}

	public static final class Submitted {
		private final String name;
		private final int program;
		private final List<GlShader.Deferred> shaders;
        private final ProgramBinaryCache.Key cacheKey;
        private final RetainedPrograms.Ownership owner;
        private final boolean alreadyLinked;
		private boolean done;

		private Submitted(String name, int program, List<GlShader.Deferred> shaders, ProgramBinaryCache.Key cacheKey, RetainedPrograms.Ownership owner, boolean alreadyLinked) {
			this.name = name;
			this.program = program;
			this.shaders = shaders;
            this.cacheKey = cacheKey;
            this.owner = owner;
            this.alreadyLinked = alreadyLinked;
		}

        public RetainedPrograms.Ownership ownership() { return owner; }

		public String getName() {
			return name;
		}

		/**
		 * Waits for the program's shaders to compile and the program to link, then returns its handle. On failure,
		 * everything is deleted and a {@link ShaderCompileException} thrown.
		 */
		public int finish() {
			RenderSystem.assertOnRenderThread();
			checkNotDone();
			done = true;
            if (alreadyLinked) return program;

			long waitStart = ShaderLoadTimings.now();
			List<GlShader> compiled;

			try {
				compiled = GlShader.Deferred.awaitAll(shaders);
			} catch (GlShader.CompileException e) {
				GlStateManager.glDeleteProgram(program);
				throw new ShaderCompileException(e.getShaderName(), e.getLog());
			}

			String log = IrisRenderSystem.getProgramInfoLog(program);

			if (!log.isEmpty()) {
				LOGGER.warn("Program link log for " + name + ": " + log);
			}

			boolean linked = GlStateManager.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL20C.GL_TRUE;
			ShaderLoadTimings.record(ShaderLoadTimings.Stage.COMPILE_WAIT, waitStart);

			//Always detach shaders according to https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
			for (GlShader shader : compiled) {
				IrisRenderSystem.detachShader(program, shader.handle());
				shader.delete();
			}

			if (!linked) {
				GlStateManager.glDeleteProgram(program);
				throw new ShaderCompileException(name, log);
			}

            if (cacheKey != null) ProgramBinaryCache.save(cacheKey, program);
			return program;
		}

		/**
		 * Deletes a program that is no longer needed, without waiting for it to finish compiling.
		 */
		public void delete() {
			checkNotDone();
			done = true;

			shaders.forEach(GlShader.Deferred::delete);
			GlStateManager.glDeleteProgram(program);
		}

		private void checkNotDone() {
			if (done) {
				throw new IllegalStateException("Program " + name + " was already finished or deleted");
			}
		}
	}
}
