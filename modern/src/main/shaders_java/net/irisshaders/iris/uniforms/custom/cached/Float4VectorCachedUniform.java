package net.irisshaders.iris.uniforms.custom.cached;

import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.parsing.VectorType;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL21;

import java.util.function.Supplier;

public class Float4VectorCachedUniform extends VectorCachedUniform<Vector4f> {

	public Float4VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<? extends Vector4fc> supplier) {
		super(name, updateFrequency, new Vector4f(), adapt(supplier));
	}

	private static Supplier<Vector4f> adapt(Supplier<? extends Vector4fc> supplier) {
		final Vector4f scratch = new Vector4f();
		return () -> {
			final Vector4fc v = supplier.get();
			return scratch.set(v.x(), v.y(), v.z(), v.w());
		};
	}

	@Override
	protected void setFrom(Vector4f other) {
		this.cached.set(other);
	}

	@Override
	public void push(int location) {
		GL21.glUniform4f(location, this.cached.x, this.cached.y, this.cached.z, this.cached.w);
	}

	@Override
	public VectorType getType() {
		return VectorType.VEC4;
	}
}
