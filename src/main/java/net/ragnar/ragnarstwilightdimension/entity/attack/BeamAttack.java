package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Four of them, enormous, standing on the four edges of the world, cutting the arena into pieces.
 *
 * <p>This one does not exist until the thing is nearly dead. Everything else in the survival phase is
 * there from the first second of the first phase and only gets faster; this is the fight's single
 * escalation, and it is worth being a wall rather than a slope. A party that has taken it below a
 * quarter has been fighting for four or five minutes and has learned the whole of the rest of the
 * phase - so the last of it introduces something they have never seen, at the point where they have
 * the least health and the most confidence.
 *
 * <p>What arrives is the blank figure, which everywhere else in the mod is the thing that watches and
 * does nothing. Four of them, five metres tall - a head over the wanderer, which is the tallest thing
 * anybody has seen up to here - standing exactly on the rim, one at each end of the disc. They are
 * not a threat in themselves. Nothing can touch them and they cannot move. And then they open their
 * mouths.
 *
 * <h2>Reading it</h2>
 *
 * <p>Each fires <b>across the circle</b>, out of its mouth, angled down so the far end of the beam is
 * at head height on the other side of the arena - so the whole seventy blocks of floor along the line
 * is inside it, not just the far half. The bearings are rolled blind, like the charge and the wall:
 * no beam is ever aimed at anybody, so no amount of running changes where one goes. But four lines
 * across a seventy block circle leave very little floor untouched, and they fire in sequence rather
 * than together - a beam every second - so the arena is closed off a quarter at a time and the last
 * safe place is somewhere you have to have already been moving towards.
 *
 * <p>Each is drawn as a thin line from the mouth for a second and a half before it fires, which is
 * the only warning there is and is the whole of the dodge.
 */
public final class BeamAttack implements Attack {
	/** The share of its health below which this exists at all. */
	private static final float THRESHOLD = 0.25F;

	private static final int GRACE = 100;

	/** Ticks between volleys, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 300;
	private static final int FASTEST = 200;

	/** How many stand up, and so how many ends the disc is considered to have. */
	private static final int FIGURES = 4;

	/**
	 * How tall they stand, in blocks.
	 *
	 * <p>A metre over the wanderer, which until this point in the game is the largest thing in the
	 * mod. That is the whole of the size decision: it has to be recognisably the same blank figure
	 * that has been watching from the sky since the first night, and it has to be bigger than the
	 * biggest thing the player has a memory of.
	 */
	private static final float HEIGHT = 5.0F;

	/** The player model is 1.8 blocks tall, so this is what the renderer has to scale it by. */
	private static final float SCALE = HEIGHT / 1.8F;

	/**
	 * How far up the beam leaves them, as a share of their height.
	 *
	 * <p>The mouth. A player's eyes sit at nine tenths of their height and the mouth a little under
	 * that, and the figure is the player model scaled about its own feet - so this lands on the face
	 * of a five metre figure the same way it would on a person.
	 */
	private static final double MOUTH_SHARE = 0.86;
	private static final double MOUTH_Y = Attacks.GROUND_Y + HEIGHT * MOUTH_SHARE;

	/** Exactly on the rim: the end of the disc, measured from the middle. */
	private static final double STAND_OUT = TheBlank.RADIUS;

	/** Right the way across and a little past, whatever bearing it is fired on. */
	private static final double LENGTH = TheBlank.RADIUS * 2.0 + 4.0;

	/**
	 * How far off the far end lands, above the floor.
	 *
	 * <p>The beam is not level. It leaves the mouth four and a half metres up and comes down to about
	 * head height at the other side, which is what puts the floor along its whole length inside it - a
	 * level beam from a mouth that high would pass clean over everybody until the far rim.
	 */
	private static final double FAR_Y = Attacks.GROUND_Y + 1.2;

	/** How wide it is on the ground. Larger than the warden's, which is the point of it. */
	private static final double BEAM_RADIUS = 2.6;

	/** How far under and over the line still counts. Generous below: it is a blast, not a wire. */
	private static final double REACH_DOWN = 3.8;
	private static final double REACH_UP = 2.2;

	/**
	 * How the beam gets from the mouth down to head height, as an exponent on the distance.
	 *
	 * <p>Under one, so it drops hard in the first metre or two and then runs almost flat for the rest
	 * of the crossing. A straight line from a mouth four and a half metres up does not work: measured
	 * against a player standing on the floor, a linear slope passes clean over their head for the first
	 * <b>twenty-four blocks</b> - a third of the disc, right where the shooter is, silently safe. This
	 * has it down to hitting height within a block of the mouth, so the whole length of the line is the
	 * attack, which is the entire point of firing it across the arena.
	 */
	private static final double DROP_CURVE = 0.35;

	/** How long each aims before it fires, and how long between one firing and the next. */
	private static final int AIM_TICKS = 30;
	private static final int STAGGER = 20;

	/** And how long they stand there afterwards before they are gone. */
	private static final int LINGER_TICKS = 25;

	/** Devastating, as asked - second only to the pillar, and unlike the pillar there are four. */
	private static final float DAMAGE = 35.0F;
	private static final double KNOCKBACK = 2.2;

	/** How finely the beam is drawn, and how far out its halo sits. */
	private static final double STEP = 0.8;
	private static final double HALO = 1.1;

	/**
	 * How far off its own bearing a figure may fire, in radians.
	 *
	 * <p>Kept under a right angle either side, which is what guarantees every beam goes <i>into</i>
	 * the circle. Each is measured from the figure's own position, not from the volley's - reading it
	 * off the volley was a bug that had three of the four firing off the edge of the world.
	 */
	private static final double SPREAD = 1.3;

	private static final DustParticleEffect AIM =
			new DustParticleEffect(new Vector3f(0.85F, 0.90F, 1.0F), 0.6F);

	/** The body of the beam, around the sonic core. */
	private static final DustParticleEffect BODY =
			new DustParticleEffect(new Vector3f(1.0F, 1.0F, 1.0F), 3.0F);

	@Override
	public boolean available(TheEntity boss) {
		return boss.getHealth() / boss.getMaxHealth() < THRESHOLD;
	}

	@Override
	public String name() {
		return "beam";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		// The four ends are turned a random amount each time, so they are the four edges of the world
		// rather than the four compass points - nowhere on the rim is reliably behind one.
		boss.launch(new Volley(world.getRandom().nextDouble() * Math.PI * 2.0));

		for (ServerPlayerEntity player : targets) {
			player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_AGITATED, SoundCategory.HOSTILE, 1.0F, 0.5F);
		}
	}

	/** The four of them: standing up, aiming in turn, firing in turn, and going. */
	private static final class Volley implements Ongoing {
		private final double turn;
		private final List<PaleFigureEntity> figures = new ArrayList<>();

		/** Where each one is shooting, decided when it stands up and never revised. */
		private final List<Vec3d> bearings = new ArrayList<>();

		/** Which of them have already fired. */
		private final Set<Integer> fired = new HashSet<>();

		private int ticks;

		private Volley(double turn) {
			this.turn = turn;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			if (this.ticks == 0) {
				stand(world);
			}

			this.ticks++;

			for (int i = 0; i < this.figures.size(); i++) {
				int at = AIM_TICKS + i * STAGGER;

				if (this.ticks < at) {
					// Only the ones whose turn is coming draw a line. All four at once would be four
					// warnings and no information about which is first.
					if (this.ticks > i * STAGGER) {
						aim(world, i);
					}
				} else if (this.fired.add(i)) {
					fire(boss, world, i);
				}
			}

			if (this.ticks >= AIM_TICKS + (FIGURES - 1) * STAGGER + LINGER_TICKS) {
				cancel(world);
				return true;
			}

			return false;
		}

		private void stand(ServerWorld world) {
			for (int i = 0; i < FIGURES; i++) {
				double bearing = this.turn + Math.PI * 2.0 * i / FIGURES;
				double x = Math.cos(bearing) * STAND_OUT;
				double z = Math.sin(bearing) * STAND_OUT;

				PaleFigureEntity figure = ModEntities.PALE_FIGURE.create(world);

				if (figure == null) {
					continue;
				}

				// Before it is spawned, or every client in range gets one tick of a life-size one.
				figure.setScale(SCALE);
				figure.refreshPositionAndAngles(x, Attacks.GROUND_Y, z, 0.0F, 0.0F);
				world.spawnEntity(figure);
				this.figures.add(figure);

				// Into the circle, always. The base direction is straight back through the middle from
				// where this one is standing, and the roll only ever leans it off that by less than a
				// right angle - so every beam crosses the disc and none goes out into the dark.
				double across = bearing + Math.PI + (world.getRandom().nextDouble() - 0.5) * SPREAD;
				this.bearings.add(new Vec3d(Math.cos(across), 0.0, Math.sin(across)));

				world.spawnParticles(ParticleTypes.END_ROD, x, Attacks.GROUND_Y + HEIGHT * 0.5, z,
						50, 0.6, HEIGHT * 0.4, 0.6, 0.03);
			}
		}

		/** Out of the mouth. */
		private Vec3d mouth(int i) {
			PaleFigureEntity figure = this.figures.get(i);
			return new Vec3d(figure.getX(), MOUTH_Y, figure.getZ());
		}

		/** Where the beam is at a given distance along it, sloping from the mouth down to the far side. */
		private Vec3d along(int i, double distance) {
			Vec3d from = mouth(i);
			Vec3d heading = this.bearings.get(i);
			double drop = Math.pow(distance / LENGTH, DROP_CURVE);

			return new Vec3d(
					from.x + heading.x * distance,
					MathHelper.lerp(drop, MOUTH_Y, FAR_Y),
					from.z + heading.z * distance);
		}

		/** The line it will take, drawn thin, from the mouth. */
		private void aim(ServerWorld world, int i) {
			if (this.ticks % 3 != 0) {
				return;
			}

			for (double d = 0.0; d < LENGTH; d += 2.5) {
				Vec3d point = along(i, d);
				world.spawnParticles(AIM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/**
		 * The beam.
		 *
		 * <p>A sonic core with a body of white around it, rather than the warden's bare line - it is
		 * meant to read as bigger than the thing it is borrowed from. The halo is drawn on the two
		 * axes across the beam, so it has girth from every angle instead of only from the side.
		 */
		private void fire(TheEntity boss, ServerWorld world, int i) {
			Vec3d heading = this.bearings.get(i);
			Vec3d across = new Vec3d(-heading.z, 0.0, heading.x);

			for (double d = 0.0; d < LENGTH; d += STEP) {
				Vec3d point = along(i, d);

				world.spawnParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
				world.spawnParticles(BODY, point.x, point.y, point.z, 2, HALO * 0.5, HALO * 0.5, HALO * 0.5, 0.0);

				// The edges of it, so the width that hits is the width that is drawn.
				for (int side = -1; side <= 1; side += 2) {
					Vec3d edge = point.add(across.multiply(HALO * side));
					world.spawnParticles(BODY, edge.x, edge.y, edge.z, 1, 0.0, 0.0, 0.0, 0.0);
					world.spawnParticles(BODY, point.x, point.y + HALO * side, point.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}

			Vec3d from = mouth(i);

			world.playSound(null, from.x, from.y, from.z,
					SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0F, 0.7F);

			hurt(boss, world, i);
		}

		private void hurt(TheEntity boss, ServerWorld world, int i) {
			DamageSource source = boss.getDamageSources().mobAttack(boss);

			Vec3d from = mouth(i);
			Vec3d heading = this.bearings.get(i);
			Vec3d to = along(i, LENGTH);

			// One box around the whole line, then the real test against the line itself. The box is
			// only there to keep the query cheap.
			Box around = new Box(from, to).expand(BEAM_RADIUS + 1.0, REACH_DOWN + 1.0, BEAM_RADIUS + 1.0);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, around,
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				Vec3d gap = player.getPos().subtract(from).multiply(1.0, 0.0, 1.0);

				// How far along the beam they are, and how far off it. Anything behind the figure or past
				// the far end is not on the beam at all, however close it looks from above.
				double alongBeam = gap.dotProduct(heading);

				if (alongBeam < 0.0 || alongBeam > LENGTH) {
					continue;
				}

				if (gap.subtract(heading.multiply(alongBeam)).length() > BEAM_RADIUS) {
					continue;
				}

				// And the height, measured against where the beam actually is at that point rather than
				// against one number - it is four and a half metres up at one end and one at the other.
				double beamY = along(i, alongBeam).y;
				double above = player.getY() - beamY;

				if (above > REACH_UP || above < -REACH_DOWN) {
					continue;
				}

				player.damage(source, DAMAGE);

				// Sideways, off the line, which is the one direction that helps them.
				Vec3d push = gap.subtract(heading.multiply(alongBeam));
				Vec3d away = push.lengthSquared() < 1.0E-4
						? new Vec3d(heading.z, 0.0, -heading.x)
						: push.normalize();

				player.takeKnockback(KNOCKBACK, -away.x, -away.z);
				player.velocityModified = true;
			}
		}

		@Override
		public void cancel(ServerWorld world) {
			for (PaleFigureEntity figure : this.figures) {
				if (!figure.isRemoved()) {
					world.spawnParticles(ParticleTypes.END_ROD,
							figure.getX(), Attacks.GROUND_Y + HEIGHT * 0.5, figure.getZ(),
							30, 0.6, HEIGHT * 0.4, 0.6, 0.03);
					figure.discard();
				}
			}

			this.figures.clear();
		}
	}
}
