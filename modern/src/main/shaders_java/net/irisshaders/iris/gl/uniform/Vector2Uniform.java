package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector2f;

import java.util.function.Supplier;

public class Vector2Uniform extends Uniform {
    private final Runnable updateListener = uploadWhileBound(this::updateValue);
	private final Supplier<Vector2f> value;
	private final Vector2f cachedValue;

	Vector2Uniform(int location, Supplier<Vector2f> value) {
		super(location);

		this.cachedValue = new Vector2f();
		this.value = value;
	}

	Vector2Uniform(int location, Supplier<Vector2f> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector2f();
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
		Vector2f newValue = value.get();

		if (dirty || !newValue.equals(cachedValue)) {
            dirty = false;
			cachedValue.set(newValue);
			IrisRenderSystem.uniform2f(this.location, cachedValue.x, cachedValue.y);
		}
	}
}
