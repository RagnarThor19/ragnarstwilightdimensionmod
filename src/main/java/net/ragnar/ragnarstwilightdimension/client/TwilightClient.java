package net.ragnar.ragnarstwilightdimension.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/** Shared client-side checks for the twilight dimension. */
public final class TwilightClient {
	private TwilightClient() {
	}

	public static boolean isInTwilight() {
		ClientWorld world = MinecraftClient.getInstance().world;
		return world != null && world.getRegistryKey() == ModDimensions.TWILIGHT_WORLD;
	}
}
