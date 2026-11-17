package net.irisshaders.iris.gl.uniform;

import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.program.ProgramUniforms;

public abstract class Uniform {
	protected final int location;
    protected boolean dirty = true;
	protected final ValueUpdateNotifier notifier;

	Uniform(int location) {
		this(location, null);
	}

	Uniform(int location, ValueUpdateNotifier notifier) {
		this.location = location;
		this.notifier = notifier;
	}

	public abstract void update();

    protected final Runnable uploadWhileBound(Runnable upload) {
        return () -> {
            if (ProgramUniforms.isActiveProgramBound()) upload.run();
            else { dirty = true; ProgramUniforms.markActiveDeferred(); }
        };
    }

	public final int getLocation() {
		return location;
	}

	public final ValueUpdateNotifier getNotifier() {
		return notifier;
	}
}
