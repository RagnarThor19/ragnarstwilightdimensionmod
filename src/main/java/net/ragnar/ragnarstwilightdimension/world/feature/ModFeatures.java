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

	/**
	 * The stand of bare oak columns. Referenced by {@code worldgen/configured_feature/log_grove.json}
	 * and placed by {@code worldgen/placed_feature/log_grove.json}, in both of the dimension's biomes -
	 * it is not the snowfield's, it is the dimension's, and finding the same wrong thing in a biome
	 * that is otherwise nothing like the first one is the point of it.
	 */
	public static final Feature<DefaultFeatureConfig> LOG_GROVE = Registry.register(
			Registries.FEATURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "log_grove"),
			new LogGroveFeature(DefaultFeatureConfig.CODEC));

	/**
	 * The ten-by-ten square that fell out of the world. Referenced by
	 * {@code worldgen/configured_feature/subsidence.json} and placed in {@code raw_generation}, before
	 * anything has been planted on the ground it takes away.
	 */
	public static final Feature<DefaultFeatureConfig> SUBSIDENCE = Registry.register(
			Registries.FEATURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "subsidence"),
			new FaultFeature(DefaultFeatureConfig.CODEC, FaultFeature.Kind.SUBSIDENCE));

	/**
	 * The same square, twenty blocks the other way. Same class, same checks, same edges - the two are
	 * one fault read in opposite directions and are meant to be recognised as a pair.
	 */
	public static final Feature<DefaultFeatureConfig> UPLIFT = Registry.register(
			Registries.FEATURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "uplift"),
			new FaultFeature(DefaultFeatureConfig.CODEC, FaultFeature.Kind.UPLIFT));

	/**
	 * The figure a quarter of the way into the ground. Referenced by
	 * {@code worldgen/configured_feature/stuck_player.json} and placed very rarely by
	 * {@code worldgen/placed_feature/stuck_player.json}, in both biomes.
	 */
	public static final Feature<DefaultFeatureConfig> STUCK_PLAYER = Registry.register(
			Registries.FEATURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "stuck_player"),
			new StuckPlayerFeature(DefaultFeatureConfig.CODEC));

	private ModFeatures() {
	}

	/** Exists to force class loading - the registration above happens in the static initialiser. */
	public static void initialize() {
	}
}
