package net.irisshaders.iris.pathways.scaling;

/**
 * The filter used to resolve a reduced-resolution world image back up to the size of
 * Minecraft's main render target.
 */
public enum UpscaleMode {
	BILINEAR,
	FSR,
	FSR2,
	FSR3,
	FSR4_1;

	public int temporalFamily() {
		return switch (this) { case FSR2 -> 2; case FSR3 -> 3; case FSR4_1 -> 4; default -> 0; };
	}
}
