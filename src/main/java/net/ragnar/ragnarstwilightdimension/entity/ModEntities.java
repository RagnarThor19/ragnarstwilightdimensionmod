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

	/**
	 * The blank one. No spawn egg and no {@code /summon} like the rest of the set pieces - use
	 * {@code /stare}, which puts one where the event would have put it.
	 *
	 * <p>The tracking range is the part that matters. It is given in chunks, and the stare places this
	 * up to forty blocks out and thirty up; at the default 8 chunks that is fine, but it is set
	 * explicitly here so that moving the event further out later does not silently stop the client
	 * being told the thing exists.
	 */
	public static final EntityType<PaleFigureEntity> PALE_FIGURE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "pale_figure"),
			EntityType.Builder.create(PaleFigureEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.maxTrackingRange(8)
					.makeFireImmune()
					.disableSummon()
					.build("pale_figure"));

	/**
	 * The one in the pew. Left summonable, like the blood steve and unlike the other set pieces,
	 * because he is the only thing in the mod whose whole point is a pose: a summoned one sits down
	 * and stares wherever it was facing, which is the entire behaviour, and finding a church every
	 * time you want to look at him would be a waste of an afternoon.
	 *
	 * <p>Placed for real by the church itself - he is baked into the structure template rather than
	 * spawned by anything here. See {@link ChurchSteveEntity}.
	 *
	 * <p>The dimensions are the seated ones and are measured from the seat, not the floor, which is
	 * why the height is well under a player's.
	 */
	public static final EntityType<ChurchSteveEntity> CHURCH_STEVE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "church_steve"),
			EntityType.Builder.create(ChurchSteveEntity::new, SpawnGroup.MISC)
					.dimensions(ChurchSteveEntity.WIDTH, ChurchSteveEntity.HEIGHT)
					.maxTrackingRange(8)
					.build("church_steve"));

	/**
	 * The one that watches the sky. No spawn egg and no {@code /summon} - it is placed by
	 * {@link WitnessSpawner} and by {@code /witness}, both of which give it the bearing it points
	 * along, and one placed without that would be a boss standing to attention at zero degrees.
	 *
	 * <p>Tracking range is in chunks and is set well past the fight's own reach, because unlike the
	 * other set pieces this one has a boss bar, a fog and a soundtrack attached to it: a client that
	 * has stopped being told the entity exists would keep all three.
	 */
	public static final EntityType<WitnessEntity> WITNESS = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "witness"),
			EntityType.Builder.create(WitnessEntity::new, SpawnGroup.MISC)
					.dimensions(WitnessEntity.WIDTH, WitnessEntity.HEIGHT)
					.maxTrackingRange(10)
					.makeFireImmune()
					.disableSummon()
					.build("witness"));

	private ModEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(SILHOUETTE, SilhouetteEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PALE_FIGURE, PaleFigureEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WITNESS, WitnessEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WANDERER, WandererEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BLOOD_STEVE, BloodSteveEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(GIANT_STEVE, GiantSteveEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CHURCH_STEVE, ChurchSteveEntity.createAttributes());
	}
}
