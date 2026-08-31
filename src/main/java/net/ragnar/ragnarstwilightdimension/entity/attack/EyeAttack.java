package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.EyeEntity;
import net.ragnar.ragnarstwilightdimension.entity.EyeSpawner;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import org.joml.Vector3f;

import java.util.List;

/**
 * One of the eyes comes in off the dark, looks at where somebody is going to be, and burns a line
 * through it.
 *
 * <p>The eyes are the disc's own furniture - they hang eighty blocks out and do nothing at all, which
 * is the entire joke of them. This is the joke stopping. It is the same picture, brought close and
 * made small enough to read as a thing in the room rather than a wall in the distance, and it does
 * the one thing it has never done.
 *
 * <p><b>It shoots where you are going, not where you are.</b> That is the whole design of the attack
 * and it is why it aims for a second and a half in full view: the beam lands on a point that was
 * decided when the eye opened, so the counter is not to run - running is what it is aiming at - but
 * to stop, or to turn. It is the exact inverse of the charge, which cannot be reacted to at all and
 * has to be walked out of early. Between them the two attacks punish the two opposite habits.
 *
 * <p>The aim is visible the whole time, as a line of dust from the eye to the spot it has chosen, so
 * a player who understands the rule can read their own future off the floor and stop being in it.
 */
public final class EyeAttack implements Attack {
	private static final int GRACE = 90;

	/** Ticks between openings, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 120;
	private static final int FASTEST = 55;

	/** How long it aims before it fires. Long enough to be read, short enough to still catch a runner. */
	private static final int AIM_TICKS = 34;

	/** How long the eye hangs about afterwards, going out. */
	private static final int LINGER_TICKS = 16;

	/** How big it is drawn, as a share of the thirty-block one out in the dark. */
	private static final float SCALE = 0.18F;

	/** Where it opens, relative to the spot it is aiming at. */
	private static final double HOVER = 11.0;
	private static final double OUT = 9.0;

	/** How far ahead of somebody it aims, and the ceiling on that. See {@link Attacks#leadOn}. */
	private static final double LEAD_SHARE = 0.9;
	private static final double LEAD_MAX = 7.0;

	private static final double RADIUS = 2.0;
	private static final float DAMAGE = 10.0F;
	private static final double KNOCKBACK = 1.1;
	private static final double REACH_UP = 4.0;

	/** The aiming line. Thin and pale - it is information, not an effect. */
	private static final DustParticleEffect AIM =
			new DustParticleEffect(new Vector3f(0.95F, 0.95F, 1.0F), 0.7F);

	/** The beam. The same colour, three times the size, for the one tick it exists. */
	private static final DustParticleEffect BEAM =
			new DustParticleEffect(new Vector3f(1.0F, 1.0F, 1.0F), 2.1F);

	@Override
	public String name() {
		return "eye";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		Random random = world.getRandom();
		ServerPlayerEntity target = targets.get(random.nextInt(targets.size()));

		// Where they will be, decided now and never revised. Everything after this is the eye showing
		// its work.
		Vec3d at = Attacks.leadOn(boss, target, AIM_TICKS, LEAD_SHARE, LEAD_MAX);

		// It comes in off the dark rather than straight down, so the line it draws crosses the floor at
		// an angle and can be seen from beside it as well as from under it.
		double bearing = random.nextDouble() * Math.PI * 2.0;
		Vec3d from = at.add(Math.cos(bearing) * OUT, HOVER, Math.sin(bearing) * OUT);

		boss.launch(new Shot(from, at));
	}

	/** One eye: opening, aiming, firing, and going out. */
	private static final class Shot implements Ongoing {
		private final Vec3d from;
		private final Vec3d at;

		private int ticks;
		private EyeEntity eye;

		private Shot(Vec3d from, Vec3d at) {
			this.from = from;
			this.at = at;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			if (this.ticks == 0) {
				open(world);
			}

			this.ticks++;

			if (this.ticks < AIM_TICKS) {
				aim(world);
				return false;
			}

			if (this.ticks == AIM_TICKS) {
				fire(boss, world);
				return false;
			}

			if (this.ticks >= AIM_TICKS + LINGER_TICKS) {
				cancel(world);
				return true;
			}

			return false;
		}

		private void open(ServerWorld world) {
			EyeEntity opened = EyeSpawner.spawnAt(world, this.from.x, this.from.y, this.from.z, eye -> {
				// Set before it is spawned, or the first thing every client in range sees is one tick of
				// a thirty-block eye at eleven blocks' distance.
				eye.shrink(SCALE, AIM_TICKS + LINGER_TICKS);
			});

			this.eye = opened;

			// The eye opening is silent. It had an enderman's stare on it, which is a sound every player
			// can name on hearing - and naming the thing is exactly what the eyes are for not letting you
			// do. What is audible is the shot, a second and a half later, and by then it is not a warning.
		}

		/**
		 * The line from the eye to the spot, drawn a few points at a time.
		 *
		 * <p>Sparse on purpose. A solid beam for a second and a half would read as the attack having
		 * already happened; a dotted line that thickens as the fuse burns down reads as aim.
		 */
		private void aim(ServerWorld world) {
			float progress = (float) this.ticks / AIM_TICKS;
			int points = 3 + Math.round(progress * 6.0F);

			for (int i = 0; i < points; i++) {
				double share = world.getRandom().nextDouble();
				Vec3d point = this.from.lerp(this.at, share);

				world.spawnParticles(AIM, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
			}

			// And the spot itself, so the floor says where as well as the air saying from where.
			if (this.ticks % 4 == 0) {
				for (int i = 0; i < 12; i++) {
					double bearing = Math.PI * 2.0 * i / 12.0;

					world.spawnParticles(AIM,
							this.at.x + Math.cos(bearing) * RADIUS, this.at.y + 0.1,
							this.at.z + Math.sin(bearing) * RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}

		private void fire(TheEntity boss, ServerWorld world) {
			int steps = MathHelper.ceil(this.from.distanceTo(this.at) * 3.0);

			for (int i = 0; i <= steps; i++) {
				Vec3d point = this.from.lerp(this.at, (double) i / steps);
				world.spawnParticles(BEAM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			}

			world.spawnParticles(ParticleTypes.FLASH, this.at.x, this.at.y + 0.5, this.at.z,
					1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.END_ROD, this.at.x, this.at.y + 0.4, this.at.z,
					40, 0.6, 0.4, 0.6, 0.12);

			world.playSound(null, this.at.x, this.at.y, this.at.z,
					SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.2F, 1.6F);

			Attacks.burst(boss, world, this.at, RADIUS, DAMAGE, KNOCKBACK, REACH_UP);
		}

		@Override
		public void cancel(ServerWorld world) {
			if (this.eye != null && !this.eye.isRemoved()) {
				this.eye.discard();
				this.eye = null;
			}
		}
	}
}
