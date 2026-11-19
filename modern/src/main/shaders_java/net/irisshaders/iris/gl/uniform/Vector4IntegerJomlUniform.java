package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector4i;
import org.joml.Vector4ic;

import java.util.function.Supplier;

public class Vector4IntegerJomlUniform extends Uniform {
    private final Runnable updateListener = uploadWhileBound(this::updateValue);
	private final Supplier<? extends Vector4ic> value;
	private final Vector4i cachedValue;

	Vector4IntegerJomlUniform(int location, Supplier<? extends Vector4ic> value) {
		this(location, value, null);
	}

	Vector4IntegerJomlUniform(int location, Supplier<? extends Vector4ic> value, ValueUpdateNotifier notifier) {
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
			IrisRenderSystem.uniform4i(this.location, cachedValue.x, cachedValue.y, cachedValue.z, cachedValue.w);
		}
	}
}
