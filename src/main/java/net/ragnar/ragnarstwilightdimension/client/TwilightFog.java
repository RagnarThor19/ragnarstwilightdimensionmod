package net.ragnar.ragnarstwilightdimension.client;

/**
 * Fog distances for the twilight dimension. Fog is a shader uniform, so shortening it costs nothing
 * to render - it just hides everything past {@link #FOG_END} behind the biome fog colour.
 *
 * <p>The fog <em>colour</em> is not set here; it comes from {@code fog_color} in the biome JSON.
 */
public final class TwilightFog {
	/** Distance (in blocks) where the fog starts fading in. */
	public static final float FOG_START = 2.0F;

	/** Distance (in blocks) where the fog is fully opaque - the practical view distance. */
	public static final float FOG_END = 11.0F;

	private TwilightFog() {
	}
}
