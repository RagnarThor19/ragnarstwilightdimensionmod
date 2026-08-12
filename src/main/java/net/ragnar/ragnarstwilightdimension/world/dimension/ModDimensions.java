package net.ragnar.ragnarstwilightdimension.world.dimension;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * Registry keys for the twilight dimension. The dimension itself is defined by the JSON files under
 * {@code data/ragnarstwilightdimension/dimension} and {@code .../worldgen}; these keys are how code
 * refers to them (teleporting, fog rendering, biome checks).
 */
public final class ModDimensions {
	public static final RegistryKey<World> TWILIGHT_WORLD =
			RegistryKey.of(RegistryKeys.WORLD, id("twilight"));

	public static final RegistryKey<DimensionType> TWILIGHT_DIMENSION_TYPE =
			RegistryKey.of(RegistryKeys.DIMENSION_TYPE, id("twilight"));

	public static final RegistryKey<Biome> TWILIGHT_PLAINS =
			RegistryKey.of(RegistryKeys.BIOME, id("twilight_plains"));

	private ModDimensions() {
	}

	private static Identifier id(String path) {
		return Identifier.of(RagnarsTwilightDimension.MOD_ID, path);
	}
}
