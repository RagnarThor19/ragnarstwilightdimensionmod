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

	/** The disc the blank one lives on, which is a different world with different rules. */
	public static boolean isInBlank() {
		ClientWorld world = MinecraftClient.getInstance().world;
		return world != null && world.getRegistryKey() == ModDimensions.BLANK_WORLD;
	}
}
