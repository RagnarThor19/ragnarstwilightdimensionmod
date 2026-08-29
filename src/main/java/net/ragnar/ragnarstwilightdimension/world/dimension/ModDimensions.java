package net.ragnar.ragnarstwilightdimension.world.dimension;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * Registry keys for the mod's two dimensions. The dimensions themselves are defined by the JSON
 * files under {@code data/ragnarstwilightdimension/dimension} and {@code .../worldgen}; these keys
 * are how code refers to them (teleporting, fog rendering, biome checks).
 */
public final class ModDimensions {
	public static final RegistryKey<World> TWILIGHT_WORLD =
			RegistryKey.of(RegistryKeys.WORLD, id("twilight"));

	public static final RegistryKey<DimensionType> TWILIGHT_DIMENSION_TYPE =
			RegistryKey.of(RegistryKeys.DIMENSION_TYPE, id("twilight"));

	public static final RegistryKey<Biome> TWILIGHT_PLAINS =
			RegistryKey.of(RegistryKeys.BIOME, id("twilight_plains"));

	/**
	 * The disc the blank one lives on, reached through the temple portal. One circle of snow in the
	 * dark with nothing else in it - see {@code TheBlank} for the shape and {@code DiscChunkGenerator}
	 * for how it is generated.
	 */
	public static final RegistryKey<World> BLANK_WORLD =
			RegistryKey.of(RegistryKeys.WORLD, id("the_blank"));

	public static final RegistryKey<DimensionType> BLANK_DIMENSION_TYPE =
			RegistryKey.of(RegistryKeys.DIMENSION_TYPE, id("the_blank"));

	public static final RegistryKey<Biome> BLANK_BIOME =
			RegistryKey.of(RegistryKeys.BIOME, id("the_blank"));

	private ModDimensions() {
	}

	private static Identifier id(String path) {
		return Identifier.of(RagnarsTwilightDimension.MOD_ID, path);
	}
}
