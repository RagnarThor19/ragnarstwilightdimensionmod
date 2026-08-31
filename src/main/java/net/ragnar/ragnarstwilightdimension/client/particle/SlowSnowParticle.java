package net.ragnar.ragnarstwilightdimension.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * Snow that falls at a speed nothing falls at.
 *
 * <p>Real snow accelerates until drag balances gravity, and vanilla's {@code minecraft:snowflake}
 * does the same: it carries a fixed gravity of 0.225 and settles at about 1.6 blocks a second no
 * matter what velocity it is handed. There is no way to slow that down from outside the class, which
 * is the whole reason this is a particle of its own rather than a sparse scattering of vanilla ones.
 *
 * <p>Here gravity is zero and the velocity multiplier is one, so the two lines in
 * {@code Particle.tick} that would change the velocity both leave it exactly as it was set. The
 * result is a flake that does not accelerate at all - it descends at {@link #FALL_SPEED} from the
 * instant it appears to the instant it goes out, which is a thing that does not happen anywhere else
 * in the game and reads immediately as wrong. Falling slowly is the effect; falling at a
 * <em>constant</em> speed is what makes it unnatural rather than merely gentle.
 *
 * <p>The art is vanilla's own generic particle sprites, the same eight {@code minecraft:generic_*}
 * that {@code minecraft:snowflake} uses, referenced across namespaces from this mod's particle JSON.
 * Nothing new is added to the atlas.
 */
@Environment(EnvType.CLIENT)
public class SlowSnowParticle extends SpriteBillboardParticle {
	/**
	 * Blocks per tick, downward. Twenty ticks to the second, so this is a bit under two thirds of a
	 * block a second - around two fifths of the speed the vanilla flake settles at, and still slow
	 * enough that a flake crossing the player's eyeline is legible as an object rather than a streak.
	 */
	private static final double FALL_SPEED = -0.0321;

	/** How far a flake wanders sideways, in blocks per tick, at the widest part of its drift. */
	private static final double DRIFT = 0.006;

	/** Ticks. Long, because at this speed a flake covers very little ground in a short life. */
	private static final int MIN_AGE = 400;
	private static final int AGE_SPREAD = 300;

	private final SpriteProvider sprites;

	/** Where in its side-to-side wander this flake starts, so they do not all sway together. */
	private final double driftPhase;

	private final double driftRate;

	protected SlowSnowParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites) {
		super(world, x, y, z);
		this.sprites = sprites;

		// The two fields that would otherwise impose vanilla physics on it.
		this.gravityStrength = 0.0F;
		this.velocityMultiplier = 1.0F;

		// Straight through the floor and on into the void rather than settling on it. A flake that
		// lands stays lying there for the rest of its very long life, and a few hundred of those turn
		// into a layer of static dots on top of the snow.
		this.collidesWithWorld = false;

		this.velocityX = 0.0;
		this.velocityY = FALL_SPEED;
		this.velocityZ = 0.0;

		this.driftPhase = this.random.nextDouble() * Math.PI * 2.0;
		this.driftRate = 0.01 + this.random.nextDouble() * 0.02;

		this.scale = 0.06F * (this.random.nextFloat() * 0.6F + 0.7F);
		this.maxAge = MIN_AGE + this.random.nextInt(AGE_SPREAD);

		// Vanilla's own flake colour: white, very slightly blue.
		this.setColor(0.923F, 0.964F, 0.999F);
		this.setSpriteForAge(sprites);
	}

	@Override
	public void tick() {
		// Reset the horizontal velocity every tick rather than accumulating it, so the wander stays a
		// wander and never builds into a sideways drift the flake cannot come back from.
		double sway = this.driftPhase + this.age * this.driftRate;
		this.velocityX = Math.cos(sway) * DRIFT;
		this.velocityZ = Math.sin(sway) * DRIFT;
		this.velocityY = FALL_SPEED;

		super.tick();
		this.setSpriteForAge(this.sprites);
	}

	/** Opaque, like the vanilla flake - these are meant to be specks of something, not glows. */
	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE;
	}

	@Environment(EnvType.CLIENT)
	public static class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider sprites;

		public Factory(SpriteProvider sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world,
									   double x, double y, double z,
									   double velocityX, double velocityY, double velocityZ) {
			return new SlowSnowParticle(world, x, y, z, this.sprites);
		}
	}
}
