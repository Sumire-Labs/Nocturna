package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.function.Supplier;

public class Vector2IntegerJomlUniform extends Uniform {
    private final Runnable updateListener = uploadWhileBound(this::updateValue);
	private final Supplier<? extends Vector2ic> value;
	private final Vector2i cachedValue;

	Vector2IntegerJomlUniform(int location, Supplier<? extends Vector2ic> value) {
		this(location, value, null);
	}

	Vector2IntegerJomlUniform(int location, Supplier<? extends Vector2ic> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector2i();
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
		Vector2ic newValue = value.get();

		if (dirty || !newValue.equals(cachedValue)) {
            dirty = false;
			cachedValue.set(newValue.x(), newValue.y());
			IrisRenderSystem.uniform2i(this.location, cachedValue.x, cachedValue.y);
		}
	}
}
