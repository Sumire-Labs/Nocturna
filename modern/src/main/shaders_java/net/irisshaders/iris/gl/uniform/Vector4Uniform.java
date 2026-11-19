package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.function.Supplier;

public class Vector4Uniform extends Uniform {
    private final Runnable updateListener = uploadWhileBound(this::updateValue);
	private final Vector4f cachedValue;
	private final Supplier<? extends Vector4fc> value;

	Vector4Uniform(int location, Supplier<? extends Vector4fc> value) {
		this(location, value, null);
	}

	Vector4Uniform(int location, Supplier<? extends Vector4fc> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector4f();
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
		Vector4fc newValue = value.get();

		if (dirty || !newValue.equals(cachedValue)) {
            dirty = false;
			cachedValue.set(newValue.x(), newValue.y(), newValue.z(), newValue.w());
			IrisRenderSystem.uniform4f(location, cachedValue.x(), cachedValue.y(), cachedValue.z(), cachedValue.w());
		}
	}
}
