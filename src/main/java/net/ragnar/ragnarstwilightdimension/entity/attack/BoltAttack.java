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

import java.util.ArrayList;
import java.util.List;

/**
 * Something comes down out of a sky that is not drawn, slowly, ringing, and lands on somebody.
 *
 * <p>This is the witness's gesture inherited and made literal. Out in the twilight that thing stands
 * in a field with one arm up at nothing and three times a fight something comes down out of the
 * nothing into a circle drawn on the grass. Here there is no circle and no arm - the disc has no sky
 * at all, so what falls comes out of flat black - and it does not land where you are, it comes down
 * <i>at</i> you, visibly, for two seconds, from a direction.
 *
 * <p>Angelic rather than violent, which is a decision about sound and colour rather than about
 * damage: it chimes on the way in, it is white and gold, and it hits harder than anything else the
 * survival phase does. The point of it is that the worst thing in the fight is also the prettiest,
 * and that watching it come is the only warning there is.
 *
 * <p>It leads its target the same way {@code EyeAttack} does, but only gently, and it keeps steering
 * for the first half of the descent. A bolt cannot be outrun in a straight line; it can be made to
 * commit and then stepped out of, which is a different skill from either of the two the eye and the
 * charge ask for.
 */
public final class BoltAttack implements Attack {
	private static final int GRACE = 120;

	/** Ticks between volleys, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 150;
	private static final int FASTEST = 70;

	/** How many players get one, at the start of the phase and at the end of it. */
	private static final int FEWEST = 1;
	private static final int MOST = 3;

	/** How far up it starts, and how far off to the side. */
	private static final double HEIGHT = 26.0;
	private static final double OFFSET = 7.0;

	/** Blocks a tick. Slow enough to watch, fast enough that watching is not the same as escaping. */
	private static final double SPEED = 0.62;

	/** How long it goes on steering, as a share of the way down. */
	private static final double HOMING_SHARE = 0.5;

	/** How much of the gap it closes per tick while it is still steering. */
	private static final double HOMING_RATE = 0.09;

	/** Backstop, so nothing hangs in the air for ever if it somehow never arrives. */
	private static final int MAX_TICKS = 160;

	private static final double RADIUS = 2.6;
	private static final float DAMAGE = 16.0F;
	private static final double KNOCKBACK = 2.0;
	private static final double REACH_UP = 4.0;

	/** Gold, and pale gold, which is the only warm colour anywhere in the dimension. */
	private static final DustParticleEffect HALO =
			new DustParticleEffect(new Vector3f(1.0F, 0.94F, 0.72F), 1.4F);
	private static final DustParticleEffect TRAIL =
			new DustParticleEffect(new Vector3f(1.0F, 1.0F, 0.92F), 0.9F);

	@Override
	public String name() {
		return "bolt";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		Random random = world.getRandom();

		// A shuffled copy, so that with more players than bolts it is a different few each volley
		// rather than whoever happens to be first in the world's player list every time.
		List<ServerPlayerEntity> picked = new ArrayList<>(targets);
		int many = Math.min(picked.size(), FEWEST + Math.round((MOST - FEWEST) * progress));

		for (int i = 0; i < many; i++) {
			int swap = i + random.nextInt(picked.size() - i);
			ServerPlayerEntity chosen = picked.get(swap);
			picked.set(swap, picked.get(i));
			picked.set(i, chosen);

			double bearing = random.nextDouble() * Math.PI * 2.0;
			Vec3d from = chosen.getPos().add(
					Math.cos(bearing) * OFFSET, HEIGHT, Math.sin(bearing) * OFFSET);

			boss.launch(new Bolt(from, chosen));
			world.playSound(null, chosen.getX(), chosen.getY(), chosen.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 2.0F, 0.7F);
		}
	}

	/**
	 * One of them on its way down.
	 *
	 * <p>Not an entity. It is a position and a heading that the boss ticks, drawn with particles - the
	 * same arrangement the witness's marks use, and for the same reasons: nothing about it needs
	 * saving, tracking, or to be seen by anybody who is not in the fight already.
	 */
	private static final class Bolt implements Ongoing {
		private final ServerPlayerEntity target;

		private Vec3d at;
		private Vec3d heading;
		private int ticks;

		private Bolt(Vec3d from, ServerPlayerEntity target) {
			this.at = from;
			this.target = target;
			this.heading = aimAt(from, target);
		}

		private static Vec3d aimAt(Vec3d from, ServerPlayerEntity target) {
			Vec3d gap = target.getPos().add(0.0, 1.0, 0.0).subtract(from);
			return gap.lengthSquared() < 1.0E-6 ? new Vec3d(0.0, -1.0, 0.0) : gap.normalize();
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks > MAX_TICKS) {
				return true;
			}

			// Steers for the first half and then commits. A bolt that homed all the way down would be
			// unavoidable and would stop being a thing you can bait; one that never steered would miss a
			// walking player every time.
			boolean stillSteering = this.at.y > Attacks.GROUND_Y + HEIGHT * (1.0 - HOMING_SHARE);

			if (stillSteering && this.target.isAlive()) {
				Vec3d wanted = aimAt(this.at, this.target);
				this.heading = this.heading.add(wanted.subtract(this.heading).multiply(HOMING_RATE)).normalize();
			}

			this.at = this.at.add(this.heading.multiply(SPEED));

			draw(world);

			if (this.at.y <= Attacks.GROUND_Y + 0.5) {
				land(boss, world);
				return true;
			}

			return false;
		}

		private void draw(ServerWorld world) {
			world.spawnParticles(HALO, this.at.x, this.at.y, this.at.z, 2, 0.12, 0.12, 0.12, 0.0);
			world.spawnParticles(ParticleTypes.END_ROD, this.at.x, this.at.y, this.at.z, 1, 0.05, 0.05, 0.05, 0.0);

			// A short tail behind it, so the direction it is coming from is readable from underneath -
			// which is the angle everybody is looking at it from.
			Vec3d behind = this.at.subtract(this.heading.multiply(1.2));
			world.spawnParticles(TRAIL, behind.x, behind.y, behind.z, 2, 0.2, 0.2, 0.2, 0.0);

			// And a mark on the floor directly below, because the last thing anybody wants is to work out
			// where a falling object is going to land by looking up at it.
			if (this.ticks % 3 == 0) {
				world.spawnParticles(TRAIL, this.at.x, Attacks.GROUND_Y + 0.1, this.at.z,
						3, 0.5, 0.02, 0.5, 0.0);
			}
		}

		private void land(TheEntity boss, ServerWorld world) {
			Vec3d floor = new Vec3d(this.at.x, Attacks.GROUND_Y, this.at.z);

			world.spawnParticles(ParticleTypes.FLASH, floor.x, floor.y + 0.6, floor.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.END_ROD, floor.x, floor.y + 0.4, floor.z,
					70, 0.9, 0.5, 0.9, 0.28);
			world.spawnParticles(HALO, floor.x, floor.y + 0.3, floor.z, 40, 1.5, 0.2, 1.5, 0.05);

			world.playSound(null, floor.x, floor.y, floor.z,
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5F, 1.3F);
			world.playSound(null, floor.x, floor.y, floor.z,
					SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 2.0F, 0.8F);

			Attacks.burst(boss, world, floor, RADIUS, DAMAGE, KNOCKBACK, REACH_UP);
		}
	}
}
