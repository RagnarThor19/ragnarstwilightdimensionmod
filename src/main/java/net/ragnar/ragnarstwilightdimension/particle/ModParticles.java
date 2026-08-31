package net.ragnar.ragnarstwilightdimension.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * The mod's particles.
 *
 * <p>Registered on both sides. The type is what a particle is identified by on the wire and in
 * commands; what it looks like and how it moves is the client's business and lives in
 * {@code SlowSnowParticle}.
 */
public final class ModParticles {
	/**
	 * The snow on the disc. A type of its own rather than {@code minecraft:snowflake} because the
	 * whole point of it is the physics, and vanilla's are not adjustable from outside the class.
	 */
	public static final SimpleParticleType SLOW_SNOW = Registry.register(
			Registries.PARTICLE_TYPE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "slow_snow"),
			FabricParticleTypes.simple());

	private ModParticles() {
	}

	public static void initialize() {
	}
}
