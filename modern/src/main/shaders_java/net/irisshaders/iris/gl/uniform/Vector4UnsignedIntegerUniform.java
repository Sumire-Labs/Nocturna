package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector4i;
import org.joml.Vector4ic;

import java.util.function.Supplier;

/**
 * For {@code uvec4} uniforms. There is no {@code Vector4ui} in JOML, so this reuses
 * {@link Vector4i} as a plain bit-pattern carrier and uploads it with
 * {@link IrisRenderSystem#uniform4ui}, unlike {@link Vector4IntegerJomlUniform} which is for
 * genuine {@code ivec4} uniforms and uses {@code glUniform4i}. The GL spec does not allow the
 * signed variant to load a {@code uvec4}.
 */
public class Vector4UnsignedIntegerUniform extends Uniform {
    private final Runnable updateListener = uploadWhileBound(this::updateValue);
	private final Supplier<? extends Vector4ic> value;
	private final Vector4i cachedValue;

	Vector4UnsignedIntegerUniform(int location, Supplier<? extends Vector4ic> value) {
		this(location, value, null);
	}

	Vector4UnsignedIntegerUniform(int location, Supplier<? extends Vector4ic> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector4i();
		this.value = value;
	}

	@Override
	public void update() {
		updateValue();

		if (notifier != null) {
			notifier.setListener(updateListener);
		}
	}

	private void updateValue() {
		Vector4ic newValue = value.get();

		if (dirty || !newValue.equals(cachedValue)) {
            dirty = false;
			cachedValue.set(newValue.x(), newValue.y(), newValue.z(), newValue.w());
			IrisRenderSystem.uniform4ui(this.location, cachedValue.x, cachedValue.y, cachedValue.z, cachedValue.w);
		}
	}
}
