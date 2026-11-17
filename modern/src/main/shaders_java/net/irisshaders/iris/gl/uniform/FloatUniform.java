package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;

public class FloatUniform extends Uniform {
    private final Runnable updateListener = uploadWhileBound(this::updateValue);
	private final FloatSupplier value;
	private float cachedValue;

	FloatUniform(int location, FloatSupplier value) {
		this(location, value, null);
	}

	FloatUniform(int location, FloatSupplier value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = 0;
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
		float newValue = value.getAsFloat();

		if (dirty || cachedValue != newValue) {
            dirty = false;
			cachedValue = newValue;
			IrisRenderSystem.uniform1f(location, newValue);
		}
	}
}
