package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector3i;

import java.util.function.Supplier;

public class Vector3IntegerUniform extends Uniform {
    private final Runnable updateListener = uploadWhileBound(this::updateValue);
	private final Vector3i cachedValue;
	private final Supplier<Vector3i> value;

	Vector3IntegerUniform(int location, Supplier<Vector3i> value) {
		super(location);

		this.cachedValue = new Vector3i();
		this.value = value;
	}

	Vector3IntegerUniform(int location, Supplier<Vector3i> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector3i();
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
		Vector3i newValue = value.get();

		if (dirty || !newValue.equals(cachedValue)) {
            dirty = false;
			cachedValue.set(newValue.x(), newValue.y(), newValue.z());
			IrisRenderSystem.uniform3i(location, cachedValue.x(), cachedValue.y(), cachedValue.z());
		}
	}
}
