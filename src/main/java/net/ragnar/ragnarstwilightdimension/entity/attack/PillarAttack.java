package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import org.joml.Vector3f;

import java.util.List;

/**
 * Halfway through every survival phase, five circles light up on the floor, and a second and a half
 * later the sky comes down through them.
 *
 * <p>This is the only attack in the fight that happens at a <b>fixed time</b>. Everything else keeps
 * its own beat and lands where it lands, so no two phases are alike; this one is at thirty-two
 * seconds, every phase, for ever. That is the point of it. Sixty-four seconds of unpredictable
 * weather with one appointment in the middle turns the phase from a stretch of survival into a
 * stretch with a <i>shape</i> - there is a before and an after, and everybody in the arena learns to
 * count to it.
 *
 * <p>It is also far and away the hardest-hitting thing here: twenty-five hearts, which is more than
 * twice what an unarmoured player has. Standing in one is not a mistake to be healed through, it is
 * the end of the attempt. In exchange it is the most honestly telegraphed attack in the fight - a
 * full circle of light, exactly the size of what will be hit, standing there for a second and a half
 * doing nothing at all.
 */
public final class PillarAttack implements Attack {
	/**
	 * Second thirty-two of sixty-four. Derived rather than typed, so that changing the length of the
	 * phase moves the appointment with it instead of quietly putting it somewhere else.
	 */
	private static final int AT_TICK = TheEntity.SURVIVAL_TICKS / 2;

	/** How many go up. */
	private static final int PILLARS = 5;

	/** How wide each one is. */
	private static final double RADIUS = 3.0;

	/** A second and a half of light before anything happens. */
	private static final int WARNING_TICKS = 30;

	/** And how long the beam itself is drawn for afterwards. */
	private static final int BEAM_TICKS = 12;

	/** Twenty-five hearts. See the note above about why this one is allowed to be absurd. */
	private static final float DAMAGE = 50.0F;

	/** Nothing to speak of - there is no point throwing what is already dead. */
	private static final double KNOCKBACK = 0.4;

	/** How far up the column reaches, and how far up it still counts as being in it. */
	private static final double BEAM_HEIGHT = 40.0;
	private static final double REACH_UP = 6.0;

	/** Points around the rim, and how far apart the rings up the column are. */
	private static final int RIM_POINTS = 28;

	private static final DustParticleEffect GLOW =
			new DustParticleEffect(new Vector3f(1.0F, 0.98F, 0.85F), 1.3F);
	private static final DustParticleEffect BEAM =
			new DustParticleEffect(new Vector3f(1.0F, 1.0F, 1.0F), 2.6F);

	@Override
	public String name() {
		return "pillar";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return tick == AT_TICK;
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		for (int i = 0; i < PILLARS; i++) {
			boss.launch(new Pillar(Attacks.somewhereOnDisc(world.getRandom())));
		}

		// One warning for all five, at everybody's own ears rather than at any of the circles - they are
		// scattered across seventy blocks and nobody should be told about the near one and not the far.
		for (ServerPlayerEntity player : targets) {
			player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.0F, 0.6F);
		}
	}

	/** One circle: the light, the column, and whatever was standing in it. */
	private static final class Pillar implements Ongoing {
		private final Vec3d centre;
		private int ticks;

		private Pillar(Vec3d centre) {
			this.centre = centre;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks < WARNING_TICKS) {
				light(world);
				return false;
			}

			if (this.ticks == WARNING_TICKS) {
				fire(boss, world);
				return false;
			}

			// The column is drawn for a few ticks after it has already done its damage. Everything that
			// was going to happen happened on one tick; this is only so that what happened is visible.
			beam(world);
			return this.ticks >= WARNING_TICKS + BEAM_TICKS;
		}

		/**
		 * The circle on the floor, brightening.
		 *
		 * <p>Drawn as a rim rather than a filled disc, and the rim is the honest boundary: everything
		 * inside it is hit and everything outside it is not, exactly. It rises off the floor as the
		 * warning runs down, so the second and a half is readable as a height as well as a brightness -
		 * which matters when five of these are up at once across an arena and you are running past them.
		 */
		private void light(ServerWorld world) {
			float share = (float) this.ticks / WARNING_TICKS;
			double height = share * 2.5;

			for (int i = 0; i < RIM_POINTS; i++) {
				double bearing = Math.PI * 2.0 * i / RIM_POINTS;
				double x = this.centre.x + Math.cos(bearing) * RADIUS;
				double z = this.centre.z + Math.sin(bearing) * RADIUS;

				world.spawnParticles(GLOW, x, Attacks.GROUND_Y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);

				// A second ring climbing the wall of the cylinder, so it reads as a shape standing on the
				// floor rather than as a mark drawn on it.
				if (this.ticks % 2 == 0) {
					world.spawnParticles(GLOW, x, Attacks.GROUND_Y + height, z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}

			if (this.ticks % 6 == 0) {
				world.spawnParticles(ParticleTypes.END_ROD,
						this.centre.x, Attacks.GROUND_Y + height * 0.5, this.centre.z,
						4, RADIUS * 0.4, height * 0.4, RADIUS * 0.4, 0.01);
			}
		}

		private void fire(TheEntity boss, ServerWorld world) {
			beam(world);

			world.spawnParticles(ParticleTypes.FLASH,
					this.centre.x, Attacks.GROUND_Y + 1.0, this.centre.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.0F, 0.7F);
			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0F, 0.5F);

			Attacks.burst(boss, world, this.centre, RADIUS, DAMAGE, KNOCKBACK, REACH_UP);
		}

		/** The column itself, filled rather than outlined - this half is not information any more. */
		private void beam(ServerWorld world) {
			for (double y = 0.0; y < BEAM_HEIGHT; y += 1.5) {
				world.spawnParticles(BEAM,
						this.centre.x, Attacks.GROUND_Y + y, this.centre.z,
						2, RADIUS * 0.55, 0.2, RADIUS * 0.55, 0.0);
			}
		}
	}
}
