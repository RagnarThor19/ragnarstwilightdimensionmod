package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The floor grows ice, one piece at a time, and then in the same order it all goes off.
 *
 * <p>Twice over, at two sizes, which is why this is one class and two entries in
 * {@link Attacks#SURVIVAL}. Both are the same event and the same second of fuse; what changes is how
 * many, how big, and how much it costs to be under one.
 *
 * <ul>
 *   <li>{@link #hail()} - fifteen ankle-high shards scattered right across the disc, a tenth of a
 *       second apart. Nowhere near enough to cover seventy blocks, so there is always somewhere to
 *       be, and finding it is a sprint rather than a step.
 *   <li>{@link #spikes()} - four of them, five metres tall and wide enough to stand behind,
 *       erupting out of the floor. Less often than the hail and far worse: the cold off one of these
 *       is measured in seconds of not being able to run.
 * </ul>
 *
 * <p>Neither waits for the phase to warm up. They are the two attacks in the fight that can land in
 * the first second of a survival phase and in the last, which is most of what makes a phase that
 * opens with three spikes feel different from one that does not.
 *
 * <h2>The tenth of a second</h2>
 *
 * <p>They do not arrive together and they do not go off together. Each is a few ticks behind the one
 * before it, so the attack is a wave that crosses the arena twice: once as ice coming out of the
 * floor in a scatter, and once, a second later, as the same scatter in the same order coming apart.
 *
 * <p>All of them at once would be a single flash and a single decision, and the decision would be
 * made before anybody had read the board. Strung out, the first burst is a warning about the ones
 * still standing, and where somebody is when it starts decides how much of the wave they have time to
 * answer.
 *
 * <p>What it leaves behind is the freeze - the frost on the edges of the screen that powder snow
 * gives you, and a stretch of not being able to run properly, which on a disc where the only defence
 * is being somewhere else is most of the damage. Being caught by one is survivable. Being caught by
 * one and then slowed into the next is not meant to be.
 */
public final class FrostAttack implements Attack {
	/**
	 * How long into the phase before either of them can happen: one second, and only that.
	 *
	 * <p>Everything else in the fight has a real grace period. These two are meant to be able to land
	 * anywhere in the sixty-four seconds, so this is not politeness but legibility - the phase opens on
	 * a blackout half a second long, and ice erupting inside it reads as a bug rather than as an
	 * attack.
	 */
	private static final int GRACE = 20;

	/** How often the dice are rolled at all. */
	private static final int INTERVAL = 40;

	/** How long each stands there after it has grown, before it goes. One second, both sizes. */
	private static final int FUSE_TICKS = 20;

	/** How long it takes to come up out of the floor, of that second. */
	private static final int GROW_TICKS = 6;

	/** How far off vertical one leans. They are grown, not planted, so no two stand the same. */
	private static final double MAX_LEAN = 0.45;

	private static final BlockState ICE = Blocks.ICE.getDefaultState();

	/** The pale blue of everything else in this sky, one shade colder. */
	private static final DustParticleEffect FROST =
			new DustParticleEffect(new Vector3f(0.72F, 0.88F, 1.0F), 1.2F);

	/** How finely the rim of a burst is drawn. */
	private static final int RIM_POINTS = 20;

	/**
	 * One size of it. Everything that differs between the fifteen small ones and the three large.
	 *
	 * @param name        what it is called, for whoever is tuning this later
	 * @param count       how many come out of the floor
	 * @param stagger     ticks between one and the next, and so between one burst and the next
	 * @param height      how far out of the floor one stands, in blocks
	 * @param thickness   and how wide it is
	 * @param radius      what the burst catches, measured on the floor
	 * @param damage      what it does to somebody standing in that
	 * @param knockback   and how far it throws them
	 * @param freeze      frozen ticks put on them. The game runs these down by <b>two</b> a tick, so
	 *                    this is half as many ticks of frost on the screen as it looks - and anything
	 *                    over 140 is also worth a heart of the game's own freeze damage every two
	 *                    seconds it stays above it
	 * @param slow        how long they cannot run properly afterwards
	 * @param slowLevel   and how badly. 1 is Slowness II
	 * @param warning     the pitch of the one sound the whole wave gets. Bigger ice, deeper note
	 * @param chanceEarly the odds on each roll at the start of a phase
	 * @param chanceLate  and at the end of it
	 */
	private record Ice(String name, int count, int stagger, double height, double thickness,
					   double radius, float damage, double knockback, int freeze, int slow,
					   int slowLevel, float warning, float chanceEarly, float chanceLate) {
		/** How far above the middle of a burst it still counts. Over the top of what made it. */
		private double reachUp() {
			return this.height + 1.0;
		}
	}

	/** Fifteen of them, ankle high, a tenth of a second apart. The common one. */
	public static FrostAttack hail() {
		return new FrostAttack(new Ice("frost", 15, 2,
				1.6, 0.55, 2.6,
				7.0F, 0.7,
				170, 40, 1,
				0.5F, 0.010F, 0.030F));
	}

	/**
	 * Four of them, five metres of it, a quarter of a second apart.
	 *
	 * <p>The big one, and no longer the rare one. It used to land in about one phase in four, which
	 * made it a thing that happened to a fight rather than a thing the fight was made of - a party
	 * could clear the survival phase twice over without once seeing the attack that most changes what
	 * the floor is for. Now it comes round about once or twice a phase, and there are four of them
	 * rather than three, so part of the disc having a wall across it is an ordinary state for the disc
	 * to be in.
	 *
	 * <p>Priced as it always was: two and a half times the damage of a shard, twice the reach on the
	 * floor, and better than twice the cold. A spike is taller than anything else that comes out of
	 * this floor and wide enough to lose somebody behind, so unlike the hail it is also, for the
	 * second it stands there, a wall.
	 */
	public static FrostAttack spikes() {
		return new FrostAttack(new Ice("spike", 4, 5,
				5.0, 1.3, 4.5,
				18.0F, 1.2,
				400, 60, 2,
				0.35F, 0.022F, 0.055F));
	}

	private final Ice ice;

	private FrostAttack(Ice ice) {
		this.ice = ice;
	}

	@Override
	public String name() {
		return this.ice.name();
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		if (tick < GRACE || tick % INTERVAL != 0) {
			return false;
		}

		return random.nextFloat() < this.ice.chanceEarly()
				+ (this.ice.chanceLate() - this.ice.chanceEarly()) * progress;
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		boss.launch(new Frost(this.ice, world.getRandom()));

		// One warning for the whole wave, at everybody's own ears rather than at any one piece of it -
		// they are scattered across the disc and nobody should be told about the near one and not the
		// far.
		for (ServerPlayerEntity player : targets) {
			player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
					SoundCategory.HOSTILE, 0.9F, this.ice.warning());
		}
	}

	/**
	 * The whole wave, as one thing.
	 *
	 * <p>A separate {@link Ongoing} for each shard would do the same job, and this is one on purpose:
	 * the order they go off in is the attack, and an order is a property of the list rather than of
	 * anything in it.
	 */
	private static final class Frost implements Ongoing {
		private final Ice ice;
		private final List<Shard> shards = new ArrayList<>();

		private int ticks;

		private Frost(Ice ice, Random random) {
			this.ice = ice;

			for (int i = 0; i < ice.count(); i++) {
				Vec3d at = Attacks.somewhereOnDisc(random);

				// Leaned a different way each, and only a little. Identical spikes standing to attention
				// are a fence; leaning ones are something that grew there.
				double bearing = random.nextDouble() * Math.PI * 2.0;
				double lean = random.nextDouble() * MAX_LEAN;

				this.shards.add(new Shard(ice, at, new Vec3d(
						Math.cos(bearing) * lean, 1.0, Math.sin(bearing) * lean).normalize()));
			}
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			boolean anyLeft = false;

			for (int i = 0; i < this.shards.size(); i++) {
				// Everything is measured from this one's own start, so the last of them does exactly what
				// the first did, a wave later.
				int age = this.ticks - i * this.ice.stagger();

				if (age <= 0) {
					anyLeft = true;
					continue;
				}

				anyLeft |= this.shards.get(i).tick(boss, world, age);
			}

			if (anyLeft) {
				return false;
			}

			cancel(world);
			return true;
		}

		@Override
		public void cancel(ServerWorld world) {
			for (Shard shard : this.shards) {
				shard.take();
			}
		}
	}

	/** One of them: growing, standing, and coming apart. */
	private static final class Shard {
		private final Ice ice;
		private final Vec3d at;

		/** Which way it points out of the floor. Up, mostly. */
		private final Vec3d lean;

		private DisplayEntity.BlockDisplayEntity body;
		private boolean gone;

		private Shard(Ice ice, Vec3d at, Vec3d lean) {
			this.ice = ice;
			this.at = at;
			this.lean = lean;
		}

		/** @return whether this one still has anything left to do */
		private boolean tick(TheEntity boss, ServerWorld world, int age) {
			if (this.gone) {
				return false;
			}

			if (age <= GROW_TICKS) {
				grow(world, age);
				return true;
			}

			if (age < FUSE_TICKS) {
				// Standing. A few flakes coming off it, so a shard that is about to go is never quite as
				// still as the floor next to one that already has.
				if (age % 4 == 0) {
					world.spawnParticles(ParticleTypes.SNOWFLAKE,
							this.at.x, this.at.y + this.ice.height() * 0.6, this.at.z,
							2, 0.25, this.ice.height() * 0.25, 0.25, 0.01);
				}

				return true;
			}

			burst(boss, world);
			return false;
		}

		/**
		 * Out of the floor, over the first third of a second.
		 *
		 * <p>Drawn at its full length from the first tick and pushed up through the ground, so what is
		 * above the floor is the tip of something rising rather than a small shard getting bigger - and
		 * five metres of it arriving in six ticks is an eruption, which is the whole of what the big
		 * one is. The floor of the disc is solid and a display collides with nothing, so the part still
		 * underneath is simply not seen.
		 */
		private void grow(ServerWorld world, int age) {
			double share = (double) age / GROW_TICKS;
			double below = this.ice.height() * (1.0 - share);

			Vec3d root = this.at.subtract(this.lean.multiply(below));
			Vec3d tip = root.add(this.lean.multiply(this.ice.height()));

			if (this.body == null) {
				this.body = Solid.bar(world, ICE, root, tip, this.ice.thickness());

				world.spawnParticles(ParticleTypes.SNOWFLAKE, this.at.x, this.at.y + 0.1, this.at.z,
						8, this.ice.thickness() * 0.6, 0.05, this.ice.thickness() * 0.6, 0.02);
				return;
			}

			// The near end is what moves while it rises, and the transform runs from wherever the entity
			// is standing - so this one is moved as well as re-aimed.
			this.body.setPosition(root.x, root.y, root.z);
			Solid.aim(this.body, root, tip, this.ice.thickness());
		}

		/**
		 * It comes apart.
		 *
		 * <p>Damage, throw and freeze all land on the same circle, and the circle is the one every
		 * other attack in the fight uses - see {@link Attacks#burst}. The freeze is applied separately
		 * because that is the one thing the shared burst has no opinion about.
		 */
		private void burst(TheEntity boss, ServerWorld world) {
			this.gone = true;
			take();

			double radius = this.ice.radius();

			Attacks.burst(boss, world, this.at, radius,
					this.ice.damage(), this.ice.knockback(), this.ice.reachUp());
			freeze(world);

			world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, this.at.x, this.at.y + 0.8, this.at.z,
					40, radius * 0.4, 0.5, radius * 0.4, 0.25);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, this.at.x, this.at.y + 0.6, this.at.z,
					30, radius * 0.5, 0.4, radius * 0.5, 0.06);

			// The edge of what it caught, drawn as a rim, so the thing that hit you is the thing you saw.
			for (int i = 0; i < RIM_POINTS; i++) {
				double bearing = Math.PI * 2.0 * i / RIM_POINTS;

				world.spawnParticles(FROST,
						this.at.x + Math.cos(bearing) * radius, this.at.y + 0.15,
						this.at.z + Math.sin(bearing) * radius, 1, 0.0, 0.0, 0.0, 0.0);
			}

			// Short, sharp and quiet, and pointedly not an explosion: fifteen of these can go off within
			// two seconds of each other, and the wave has to be heard crossing the arena rather than
			// heard as one noise in the middle of it.
			world.playSound(null, this.at.x, this.at.y, this.at.z,
					SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 0.55F, 1.4F);
		}

		/** The cold, on everybody the burst reached. */
		private void freeze(ServerWorld world) {
			double radius = this.ice.radius();

			Box around = new Box(this.at.x - radius, this.at.y - 2.0, this.at.z - radius,
					this.at.x + radius, this.at.y + this.ice.reachUp(), this.at.z + radius);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, around,
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				double dx = player.getX() - this.at.x;
				double dz = player.getZ() - this.at.z;

				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}

				// Added rather than set, and capped, so somebody walking the length of a wave gets colder
				// and colder instead of being put back to the same few seconds by every burst.
				player.setFrozenTicks(Math.min(this.ice.freeze() * 2, player.getFrozenTicks() + this.ice.freeze()));
				player.addStatusEffect(new StatusEffectInstance(
						StatusEffects.SLOWNESS, this.ice.slow(), this.ice.slowLevel()));
			}
		}

		/** The ice itself, gone. Safe to call twice, and called at the seam whether it burst or not. */
		private void take() {
			Solid.remove(this.body);
			this.body = null;
		}
	}
}
