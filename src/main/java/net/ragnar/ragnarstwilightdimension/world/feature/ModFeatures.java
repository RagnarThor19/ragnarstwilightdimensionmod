package net.ragnar.ragnarstwilightdimension.world.feature;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

public final class ModFeatures {
	/**
	 * Referenced by {@code worldgen/configured_feature/gravestone.json}, which is in turn placed by
	 * {@code worldgen/placed_feature/gravestone.json} and listed in the twilight biome.
	 */
	public static final Feature<DefaultFeatureConfig> GRAVESTONE = Registry.register(
			Registries.FEATURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "gravestone"),
			new GravestoneFeature(DefaultFeatureConfig.CODEC, GravestoneFeature.Kind.BURIED));

	/**
	 * The same grave, already dug out. Referenced by {@code worldgen/configured_feature/open_grave.json}
	 * and placed far more rarely than the ordinary kind - it only means anything if you have seen
	 * several of the intact ones first.
	 */
	public static final Feature<DefaultFeatureConfig> OPEN_GRAVE = Registry.register(
			Registries.FEATURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "open_grave"),
			new GravestoneFeature(DefaultFeatureConfig.CODEC, GravestoneFeature.Kind.OPENED));

	/**
	 * The plank column and the emptied grave at the foot of it. Referenced by
	 * {@code worldgen/configured_feature/pillar.json} and placed by
	 * {@code worldgen/placed_feature/pillar.json}, rarer than either grave - there is meant to be about
	 * one of these for every several worlds a player walks across, and it means nothing to somebody who
	 * has not already found the ordinary graves first.
	 */
	public static final Feature<DefaultFeatureConfig> PILLAR = Registry.register(
			Registries.FEATURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "pillar"),
			new PillarFeature(DefaultFeatureConfig.CODEC));

	private ModFeatures() {
	}

	/** Exists to force class loading - the registration above happens in the static initialiser. */
	public static void initialize() {
	}
}
