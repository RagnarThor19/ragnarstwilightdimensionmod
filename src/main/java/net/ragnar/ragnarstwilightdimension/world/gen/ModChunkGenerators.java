package net.ragnar.ragnarstwilightdimension.world.gen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * The mod's chunk generators.
 *
 * <p>What is registered is the codec, not the generator: a dimension's {@code generator} block in
 * JSON is read by looking its {@code type} up here and handing the rest of the object to whatever
 * codec is registered under it. Without this the disc dimension fails to load with an unknown
 * generator type rather than with anything that points at the cause.
 */
public final class ModChunkGenerators {
	private ModChunkGenerators() {
	}

	public static void initialize() {
		Registry.register(Registries.CHUNK_GENERATOR,
				Identifier.of(RagnarsTwilightDimension.MOD_ID, "disc"),
				DiscChunkGenerator.CODEC);
	}
}
