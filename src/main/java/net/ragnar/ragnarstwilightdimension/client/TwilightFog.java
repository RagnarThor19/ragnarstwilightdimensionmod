package net.ragnar.ragnarstwilightdimension.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Fog distances for the twilight dimension. Fog is a shader uniform, so shortening it costs nothing
 * to render - it just hides everything past {@link #FOG_END} behind the biome fog colour.
 */
public final class TwilightFog {
	/** Distance (in blocks) where the fog starts fading in. */
	public static final float FOG_START = 2.0F;

	/** Distance (in blocks) where the fog is fully opaque - the practical view distance. */
	public static final float FOG_END = 11.0F;

	private TwilightFog() {
	}

	public static boolean isInTwilight() {
		ClientWorld world = MinecraftClient.getInstance().world;
		return world != null && world.getRegistryKey() == ModDimensions.TWILIGHT_WORLD;
	}
}
