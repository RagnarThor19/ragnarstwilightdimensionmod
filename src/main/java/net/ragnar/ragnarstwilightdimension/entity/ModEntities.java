package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

public final class ModEntities {
	/**
	 * Deliberately has no spawn egg and {@code disableSummon()}, so it does not turn up in the
	 * creative menu or in {@code /summon} autocomplete. Use {@code /silhouette} to spawn one.
	 */
	public static final EntityType<SilhouetteEntity> SILHOUETTE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "silhouette"),
			EntityType.Builder.create(SilhouetteEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.maxTrackingRange(6)
					.makeFireImmune()
					.disableSummon()
					.build("silhouette"));

	/**
	 * Same treatment as the silhouette - no spawn egg, no {@code /summon}. Use {@code /wanderer} to
	 * send one past, since waiting for the natural roll takes about an hour.
	 */
	public static final EntityType<WandererEntity> WANDERER = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "wanderer"),
			EntityType.Builder.create(WandererEntity::new, SpawnGroup.MISC)
					.dimensions(WandererEntity.WIDTH, WandererEntity.HEIGHT)
					.maxTrackingRange(6)
					.makeFireImmune()
					.disableSummon()
					.build("wanderer"));

	/**
	 * Left summonable, unlike the other two - it is an ordinary hostile mob rather than a set piece,
	 * and being able to {@code /summon} one is genuinely useful for tuning the fight.
	 */
	public static final EntityType<BloodSteveEntity> BLOOD_STEVE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "blood_steve"),
			EntityType.Builder.create(BloodSteveEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.8F)
					.maxTrackingRange(8)
					.build("blood_steve"));

	/**
	 * Back to the set-piece treatment - no spawn egg and no {@code /summon}. There would be nothing to
	 * test with either: one placed outside an event removes itself on its first tick. Use
	 * {@code /bloodmoon start}, which puts one out for every player in the dimension.
	 *
	 * <p>The tracking range is the part that matters here. It is given in chunks, and at the default 8
	 * this thing would sit six chunks out and never be sent to the client at all.
	 */
	public static final EntityType<GiantSteveEntity> GIANT_STEVE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "giant_steve"),
			EntityType.Builder.create(GiantSteveEntity::new, SpawnGroup.MISC)
					.dimensions(GiantSteveEntity.WIDTH, GiantSteveEntity.HEIGHT)
					.maxTrackingRange(12)
					.makeFireImmune()
					.disableSummon()
					.build("giant_steve"));

	private ModEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(SILHOUETTE, SilhouetteEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WANDERER, WandererEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BLOOD_STEVE, BloodSteveEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(GIANT_STEVE, GiantSteveEntity.createAttributes());
	}
}
