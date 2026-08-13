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

	private ModEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(SILHOUETTE, SilhouetteEntity.createAttributes());
	}
}
