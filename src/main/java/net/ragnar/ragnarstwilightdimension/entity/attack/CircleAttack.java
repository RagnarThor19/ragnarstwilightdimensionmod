package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Five of them arrive around one person, and for three seconds that person is not going anywhere.
 *
 * <p>The rare one. Everything else in the survival phase is weather - it comes down on the arena and
 * whoever is under it wears it, and none of it knows your name. This one picks somebody.
 *
 * <p>They are blank figures, the same ones that hang in the sky over the twilight watching people who
 * have not looked up yet, and this is the only time in the mod they are ever within arm's reach.
 * There is nothing to fight: they cannot be hurt, they do not block, and hitting them does nothing.
 * They stand in a ring, they take turns, and then they are not there.
 *
 * <p><b>The cage is the attack.</b> Three seconds is not long, and none of the individual punches is
 * the worst thing in the fight - but the disc is a room where the only defence is being somewhere
 * else, and this is the one attack that takes that away. What makes it survivable is everything the
 * player did in the ten seconds before it: it lands where you were standing, so being caught in the
 * open by it is a different sentence from being caught with room to move afterwards.
 *
 * <p>Everybody else in the arena is left completely alone for the duration, which matters on a server
 * - it is one player's three seconds, and the others get to watch it happen to somebody.
 */
public final class CircleAttack implements Attack {
	/** Nothing in the first quarter of the phase. It is a closer, not an opener. */
	private static final int GRACE = 320;

	/** How often the dice are rolled at all. */
	private static final int INTERVAL = 40;

	/** And the odds on each roll, at the start of the phase and at the end of it. */
	private static final float CHANCE_EARLY = 0.015F;
	private static final float CHANCE_LATE = 0.05F;

	/** How many stand in the ring. */
	private static final int FIGURES = 5;

	/** How far out they stand. Just past arm's reach, so the ring reads as a room rather than a pile. */
	private static final double RING = 2.6;

	/** How long the whole thing lasts. */
	private static final int HOLD_TICKS = 60;

	/** How long they stand there before the first one moves. One beat, to understand what has happened. */
	private static final int STILL_TICKS = 10;

	/** Ticks between punches. Five figures over fifty ticks is one every ten. */
	private static final int PUNCH_EVERY = 10;

	private static final float DAMAGE = 5.0F;

	/**
	 * How far out of the ring the player is allowed to get before they are put back.
	 *
	 * <p>Slightly wider than the ring itself, so there is somewhere to move inside it. A cage the exact
	 * size of the thing it is holding reads as being frozen, which is a worse feeling and a worse look
	 * than being hemmed in.
	 */
	private static final double LEASH = 2.2;

	/** How hard they are pushed back in. Enough to beat a sprint, and not enough to fling anybody. */
	private static final double LEASH_PULL = 0.42;

	@Override
	public String name() {
		return "circle";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		if (tick < GRACE || tick % INTERVAL != 0) {
			return false;
		}

		return random.nextFloat() < CHANCE_EARLY + (CHANCE_LATE - CHANCE_EARLY) * progress;
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		ServerPlayerEntity target = targets.get(world.getRandom().nextInt(targets.size()));
		boss.launch(new Circle(target, target.getPos()));
	}

	/** The ring, the three seconds, and the taking away of it. */
	private static final class Circle implements Ongoing {
		private final ServerPlayerEntity target;

		/** Where it closed, kept so the cage does not follow them around the arena. */
		private final Vec3d centre;

		private final List<PaleFigureEntity> figures = new ArrayList<>();

		private int ticks;
		private int next;

		private Circle(ServerPlayerEntity target, Vec3d centre) {
			this.target = target;
			this.centre = new Vec3d(centre.x, Attacks.GROUND_Y, centre.z);
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			if (this.ticks == 0) {
				close(world);
			}

			this.ticks++;

			// Anything that ends it early ends it completely. Somebody who dies inside the ring is not
			// held there by their own corpse, and somebody who left the world takes the ring with them.
			if (!this.target.isAlive() || this.target.getWorld() != world || this.ticks > HOLD_TICKS) {
				cancel(world);
				return true;
			}

			hold(world);

			if (this.ticks > STILL_TICKS && --this.next <= 0) {
				this.next = PUNCH_EVERY;
				punch(boss, world);
			}

			return false;
		}

		private void close(ServerWorld world) {
			for (int i = 0; i < FIGURES; i++) {
				PaleFigureEntity figure = ModEntities.PALE_FIGURE.create(world);

				if (figure == null) {
					continue;
				}

				double bearing = Math.PI * 2.0 * i / FIGURES;
				double x = this.centre.x + Math.cos(bearing) * RING;
				double z = this.centre.z + Math.sin(bearing) * RING;

				figure.refreshPositionAndAngles(x, Attacks.GROUND_Y, z, 0.0F, 0.0F);

				// Pointed at them from the first frame. The blank figure squares its whole body up to
				// whoever it is watching, which is the entire reason it is the thing standing here.
				figure.watch(this.target);
				world.spawnEntity(figure);
				this.figures.add(figure);

				world.spawnParticles(ParticleTypes.END_ROD, x, Attacks.GROUND_Y + 1.0, z,
						25, 0.25, 0.7, 0.25, 0.02);
			}

			// One sound, and not an enderman's. The figures arriving is a silent event - they are silent
			// everywhere else in the mod - so what is heard is the ring closing rather than five things
			// appearing, which is also the only warning anybody outside it gets.
			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.2F, 0.4F);
		}

		/**
		 * Keeps them inside it.
		 *
		 * <p>By velocity rather than by moving them. Writing a player's position from the server is a
		 * correction their client argues with, and three seconds of that is three seconds of the world
		 * juddering; pushing them costs nothing to predict and reads as being shoved back in, which is
		 * what is actually happening.
		 *
		 * <p>Only ever inward, and only when they are already outside. Somebody who stays in the middle
		 * is never touched by this and can move freely in the two metres they have got.
		 */
		private void hold(ServerWorld world) {
			double dx = this.target.getX() - this.centre.x;
			double dz = this.target.getZ() - this.centre.z;
			double out = Math.sqrt(dx * dx + dz * dz);

			if (out <= LEASH || out < 1.0E-4) {
				return;
			}

			Vec3d velocity = this.target.getVelocity();

			this.target.setVelocity(-dx / out * LEASH_PULL, velocity.y, -dz / out * LEASH_PULL);
			this.target.velocityModified = true;

			if (this.ticks % 4 == 0) {
				world.spawnParticles(ParticleTypes.END_ROD,
						this.target.getX(), this.target.getY() + 1.0, this.target.getZ(),
						3, 0.2, 0.4, 0.2, 0.01);
			}
		}

		/** One of them, in turn, going round the ring. */
		private void punch(TheEntity boss, ServerWorld world) {
			if (this.figures.isEmpty()) {
				return;
			}

			PaleFigureEntity figure = this.figures.get((this.ticks / PUNCH_EVERY) % this.figures.size());

			if (figure.isRemoved()) {
				return;
			}

			figure.swingHand(Hand.MAIN_HAND);

			DamageSource source = boss.getDamageSources().mobAttack(boss);
			this.target.damage(source, DAMAGE);

			// Shoved across the ring rather than out of it, so the punches move them about inside their
			// two metres instead of stacking up against the leash on one side.
			double dx = this.target.getX() - figure.getX();
			double dz = this.target.getZ() - figure.getZ();

			this.target.takeKnockback(0.45, -dx, -dz);
			this.target.velocityModified = true;

			world.playSound(null, this.target.getX(), this.target.getY(), this.target.getZ(),
					SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.3F, 0.7F);
		}

		@Override
		public void cancel(ServerWorld world) {
			for (PaleFigureEntity figure : this.figures) {
				if (figure.isRemoved()) {
					continue;
				}

				world.spawnParticles(ParticleTypes.END_ROD,
						figure.getX(), figure.getY() + 1.0, figure.getZ(), 15, 0.25, 0.7, 0.25, 0.02);
				figure.discard();
			}

			this.figures.clear();
		}
	}
}
