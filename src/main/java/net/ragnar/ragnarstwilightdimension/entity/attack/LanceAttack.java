package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Six of it stand off the edge of the world and cut the disc into slices.
 *
 * <p>Each one puts a beam straight down into the rim under its own feet, and then walks that beam
 * across the arena in a dead straight line to the far side, like a knife through a pizza. It is not
 * aimed at anybody and it never was: where a cut goes is rolled before the first of them fires, and
 * no amount of running changes it. What running changes is whether you are standing on it.
 *
 * <p>That is the whole difference between this and the attacks that come for a <i>person</i>. The
 * ring picks somebody. The archer leads them. This one does not know anybody is there. It is the
 * disc being cut up, and the players happen to be on the disc.
 *
 * <h2>Being cut twice</h2>
 *
 * <p>The six lines are rolled independently, so they cross - somewhere near the middle, usually, and
 * never in quite the same place twice. One cut is heavy. Two arriving together is worse than twice as
 * bad, because they are dealt as a single blow: the game gives out half a second of invulnerability
 * after any hit, which would ordinarily mean the second and third beams to reach somebody cost them
 * nothing at all. Here every cut that lands on the same person on the same tick is added up first,
 * and the window is cleared before it is dealt, so standing where two lines meet costs what it looks
 * like it should.
 *
 * <p>Each beam goes through each person once, however long it is on them - it is a knife going past,
 * not a fire. The dodge is a second and a half of every line being drawn flat on the floor before
 * anything fires. After that they are quick, and nobody outruns one along its own length; you step
 * off it, which takes about half a second, or you wear it.
 *
 * <h2>What it looks like</h2>
 *
 * <p>A guardian's beam, and pointedly not a guardian's beam. Thin, solid, bright, with a wisp of
 * colour around it - the width of a wire rather than of an arm, because the first version of this was
 * as thick as a fence post and read as a wall being dragged about. It is a real bar of block rather
 * than a line of particles: see {@link Solid}.
 */
public final class LanceAttack implements Attack {
	private static final int GRACE = 200;

	/** Ticks between volleys, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 460;
	private static final int FASTEST = 300;

	/** How many cuts the disc is put into. Six lines, twelve slices. */
	private static final int FIGURES = 6;

	/** Exactly on the rim, which is where a cut has to start from. */
	private static final double STAND_OUT = TheBlank.RADIUS;

	/** How high off the rim they hang. High enough for the first shot to read as going straight down. */
	private static final double SKY = Attacks.GROUND_Y + 12.0;

	/** How tall they are drawn. Large, and a metre under the four that stand up at a quarter health. */
	private static final float HEIGHT = 4.0F;

	/** The player model is 1.8 blocks tall, so this is what the renderer has to scale it by. */
	private static final float SCALE = HEIGHT / 1.8F;

	/** Where the beam leaves them, as a share of that height. The mouth. */
	private static final double MOUTH_SHARE = 0.86;

	/** And so the height every beam is fired from, which never moves. */
	private static final double MOUTH_Y = SKY + HEIGHT * MOUTH_SHARE;

	/**
	 * How far off the exact diameter the far end of a cut may land, in radians.
	 *
	 * <p>Zero would put every one of them through the dead centre of the disc at once, which is a
	 * rule rather than a fight: stand anywhere but the middle. A little scatter puts the crossings
	 * somewhere different every time and keeps every one of them worth watching, while they still read as
	 * slices of the same pizza.
	 */
	private static final double SPREAD = 0.5;

	/** How long the lines are drawn on the floor before anything fires. */
	private static final int AIM_TICKS = 30;

	/**
	 * How long a cut takes to cross the disc, and how far apart the six of them start.
	 *
	 * <p>Seventy blocks in fifty ticks is about one and a half a tick, and there is a floor under how
	 * slow this is allowed to be but also a ceiling on how fast: the beam is tested against people once
	 * a tick, so a sweep that moved more than twice {@link #HIT_RADIUS} in one of them would step
	 * clean over somebody standing on the line. Half of what is allowed is a comfortable place to be.
	 */
	private static final int SWEEP_TICKS = 50;
	private static final int STAGGER = 5;

	/** And how long they hang there afterwards before they are gone. */
	private static final int LINGER_TICKS = 15;

	/**
	 * What one cut costs.
	 *
	 * <p>Heavy on purpose, and heavier again where two of them meet - see the note above about how
	 * they are added together. Nothing is thrown by this: being knocked out of one cut and into
	 * another would be a joke at the player's expense rather than a fight.
	 */
	private static final float DAMAGE = 14.0F;

	/** How far off the line still counts. Barely wider than what is drawn. */
	private static final double HIT_RADIUS = 0.6;

	/** How finely the beam is walked when working out what it is touching. */
	private static final double HIT_STEP = 0.4;

	/** The wire and the wisp around it. Solid block, both of them, and both thin. */
	private static final BlockState CORE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState HALO = Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();

	private static final double CORE_THICKNESS = 0.12;
	private static final double HALO_THICKNESS = 0.28;

	/** The line drawn flat on the floor while nothing is happening yet. */
	private static final DustParticleEffect AIM =
			new DustParticleEffect(new Vector3f(0.85F, 0.90F, 1.0F), 0.7F);

	/** How far apart the marks on that line are, in blocks. */
	private static final double AIM_STEP = 1.6;

	@Override
	public String name() {
		return "lance";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		// The whole arrangement is turned a random amount each time, so the slices are never the same
		// slices and nowhere on the rim is reliably behind one of them.
		boss.launch(new Volley(world.getRandom().nextDouble() * Math.PI * 2.0));

		for (ServerPlayerEntity player : targets) {
			player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 0.8F, 0.5F);
		}
	}

	/** The six of them: standing up, drawing their lines, cutting in turn, and going. */
	private static final class Volley implements Ongoing {
		private final double turn;
		private final List<Cut> cuts = new ArrayList<>();

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

			// Everything any of them catches this tick, gathered before anybody is hurt, so that two
			// beams landing on the same person are one blow of twice the size rather than one blow and
			// one shrug. This map is the whole reason the cuts are ticked from here instead of each
			// being an Ongoing of its own.
			Map<PlayerEntity, Integer> caught = new HashMap<>();

			for (int i = 0; i < this.cuts.size(); i++) {
				this.cuts.get(i).tick(world, this.ticks - i * STAGGER, caught);
			}

			hurt(boss, caught);

			if (this.ticks >= (FIGURES - 1) * STAGGER + AIM_TICKS + SWEEP_TICKS + LINGER_TICKS) {
				cancel(world);
				return true;
			}

			return false;
		}

		/** All six arrive together. The cutting is what is staggered, not the appearing. */
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
				figure.refreshPositionAndAngles(x, SKY, z, 0.0F, 0.0F);
				world.spawnEntity(figure);

				// Across, and out the other side. The far end is opposite this one give or take, so the
				// line always crosses the whole disc rather than clipping a corner off it.
				double across = bearing + Math.PI + (world.getRandom().nextDouble() - 0.5) * SPREAD;

				this.cuts.add(new Cut(figure,
						new Vec3d(x, Attacks.GROUND_Y, z),
						new Vec3d(Math.cos(across) * STAND_OUT, Attacks.GROUND_Y, Math.sin(across) * STAND_OUT)));

				world.spawnParticles(ParticleTypes.END_ROD, x, SKY + 1.0, z, 40, 0.5, 1.0, 0.5, 0.03);
			}
		}

		/**
		 * Everybody a cut reached this tick: once each, for as much as reached them.
		 *
		 * <p>The invulnerability window is cleared rather than worked around. Two beams crossing on
		 * somebody is the entire point of there being six, and a fight that quietly throws the second
		 * hit away is a fight whose numbers do not mean anything.
		 */
		private void hurt(TheEntity boss, Map<PlayerEntity, Integer> caught) {
			if (caught.isEmpty()) {
				return;
			}

			DamageSource source = boss.getDamageSources().mobAttack(boss);

			for (Map.Entry<PlayerEntity, Integer> entry : caught.entrySet()) {
				PlayerEntity player = entry.getKey();

				player.timeUntilRegen = 0;
				player.damage(source, DAMAGE * entry.getValue());
			}
		}

		@Override
		public void cancel(ServerWorld world) {
			for (Cut cut : this.cuts) {
				cut.take(world);
			}

			this.cuts.clear();
		}
	}

	/** One figure, the line it was given, and the beam walking along it. */
	private static final class Cut {
		private final PaleFigureEntity figure;

		/** The rim under its own feet, and the rim on the far side. The cut is the line between. */
		private final Vec3d from;
		private final Vec3d to;

		/** Who this one has already been through. A knife going past, not a fire. */
		private final Set<UUID> already = new HashSet<>();

		private DisplayEntity.BlockDisplayEntity core;
		private DisplayEntity.BlockDisplayEntity halo;

		private Cut(PaleFigureEntity figure, Vec3d from, Vec3d to) {
			this.figure = figure;
			this.from = from;
			this.to = to;
		}

		/** @param age ticks since this one in particular started, which may not have happened yet */
		private void tick(ServerWorld world, int age, Map<PlayerEntity, Integer> caught) {
			if (age <= 0 || this.figure.isRemoved()) {
				return;
			}

			if (age <= AIM_TICKS) {
				line(world, age);
				return;
			}

			int cutting = age - AIM_TICKS;

			if (cutting > SWEEP_TICKS) {
				// The beam goes out; the figure stays. All six arrived together and all six leave
				// together, with the cutting staggered in between.
				douse();
				return;
			}

			// Along the line at a flat speed, from directly under its own feet to the far rim. The first
			// tick of it is straight down, which is what makes the rest read as a cut being made rather
			// than as a beam that happens to be moving.
			double share = (double) (cutting - 1) / (SWEEP_TICKS - 1);
			Vec3d at = this.from.add(this.to.subtract(this.from).multiply(share));

			if (cutting == 1) {
				open(world, at);
			} else {
				draw(world, at);
			}

			burn(world, at, caught);
		}

		/** Out of the mouth of it. */
		private Vec3d mouth() {
			return new Vec3d(this.figure.getX(), MOUTH_Y, this.figure.getZ());
		}

		/** The cut drawn flat on the floor, before there is anything to be hurt by. */
		private void line(ServerWorld world, int age) {
			if (age % 3 != 0) {
				return;
			}

			Vec3d span = this.to.subtract(this.from);
			double length = span.length();
			Vec3d step = span.multiply(AIM_STEP / length);
			Vec3d point = this.from;

			for (double walked = 0.0; walked <= length; walked += AIM_STEP) {
				world.spawnParticles(AIM, point.x, point.y + 0.1, point.z, 1, 0.0, 0.0, 0.0, 0.0);
				point = point.add(step);
			}
		}

		/** It fires, straight down, at full length on one tick, because that is what a beam does. */
		private void open(ServerWorld world, Vec3d at) {
			Vec3d mouth = mouth();

			this.halo = Solid.bar(world, HALO, mouth, at, HALO_THICKNESS);
			this.core = Solid.bar(world, CORE, mouth, at, CORE_THICKNESS);

			world.playSound(null, mouth.x, mouth.y, mouth.z,
					SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.4F, 0.6F);
		}

		/** The beam, re-pointed at where the cut has got to, and the mess where it is landing. */
		private void draw(ServerWorld world, Vec3d at) {
			Vec3d mouth = mouth();

			Solid.aim(this.halo, mouth, at, HALO_THICKNESS);
			Solid.aim(this.core, mouth, at, CORE_THICKNESS);

			world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.1, at.z, 2, 0.15, 0.05, 0.15, 0.02);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.1, at.z, 3, 0.3, 0.1, 0.3, 0.03);
		}

		/**
		 * Whoever the line is going through, added to the tally rather than hurt here.
		 *
		 * <p>Walked in short steps and tested against each player's own box rather than solved as a
		 * line against a cylinder. A beam is straight down at one end of its sweep and nearly flat at
		 * the other, and the flat-line arithmetic the rest of the fight uses has no answer for that;
		 * this has the same answer at every angle, and a couple of hundred point tests against at most a
		 * handful of boxes is not worth being clever about.
		 */
		private void burn(ServerWorld world, Vec3d at, Map<PlayerEntity, Integer> caught) {
			Vec3d mouth = mouth();
			Vec3d span = at.subtract(mouth);
			double length = span.length();

			if (length < 1.0E-4) {
				return;
			}

			Vec3d step = span.multiply(HIT_STEP / length);

			// One box around the whole line to keep the query cheap, then the real test against the line.
			Box around = new Box(mouth, at).expand(HIT_RADIUS + 1.0);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, around,
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				if (this.already.contains(player.getUuid())) {
					continue;
				}

				Box body = player.getBoundingBox().expand(HIT_RADIUS);
				Vec3d point = mouth;

				for (double walked = 0.0; walked <= length; walked += HIT_STEP) {
					if (body.contains(point)) {
						this.already.add(player.getUuid());
						caught.merge(player, 1, Integer::sum);
						break;
					}

					point = point.add(step);
				}
			}
		}

		/** The beam alone, gone. Safe to call twice. */
		private void douse() {
			Solid.remove(this.core);
			Solid.remove(this.halo);
			this.core = null;
			this.halo = null;
		}

		/** The figure and its beam, gone. Safe to call twice. */
		private void take(ServerWorld world) {
			douse();

			if (!this.figure.isRemoved()) {
				world.spawnParticles(ParticleTypes.END_ROD,
						this.figure.getX(), SKY + 1.0, this.figure.getZ(), 25, 0.5, 1.0, 0.5, 0.03);
				this.figure.discard();
			}
		}
	}
}
