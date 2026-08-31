package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.entity.WandererEntity;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One of the tall ones comes out of the dark on one side of the disc and does not stop until it is
 * out of the dark on the other side.
 *
 * <p>It is the same wanderer that walks past in the twilight, and that is the whole idea of it. Out
 * there it is scenery with a rule attached - it never comes near you, it never turns, and if you
 * chase it you lose ground. In here the rule is unchanged and the room is seventy blocks across, so
 * for the first time the line it is walking has you standing on it.
 *
 * <p>It does not aim. The bearing is picked before anybody's position is looked at, which makes this
 * the one attack in the fight that can be stood still through - if the line misses you, it misses
 * you, and no amount of running will change a line that was never drawn at you.
 *
 * <p>Which is why the summon has to be seen. A thing that crosses the arena in three seconds and
 * cannot be dodged reactively has to be dodgeable <i>before</i> it starts, so the first second and a
 * half is nothing but a column of light standing where it is about to come from, at the edge of the
 * world, in a direction. That column is the attack; the wanderer is only the consequence.
 */
public final class ChargeAttack implements Attack {
	/** Nothing for the first three seconds of the phase. */
	private static final int GRACE = 60;

	/** Ticks between charges, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 200;
	private static final int FASTEST = 90;

	/** How long the column stands there before anything comes out of it. */
	private static final int SUMMON_TICKS = 30;

	/** How far out the run starts and ends, measured from the middle. Both are off the disc. */
	private static final double START_OUT = TheBlank.RADIUS + 6.0;

	/** So it is still going when it leaves, rather than stopping politely at the far rim. */
	private static final double RUN_LENGTH = START_OUT * 2.0 + 8.0;

	/** How close it has to pass. Its own width is about two, so this is a body and a bit either side. */
	private static final double HIT_RADIUS = 2.6;

	private static final float DAMAGE = 14.0F;

	/** Thrown, not nudged. Being clipped by this is meant to cost the next few seconds as well. */
	private static final double KNOCKBACK = 2.4;

	/** How far up it reaches. It is four metres tall; jumping over it is not a plan. */
	private static final double REACH_UP = 5.0;

	@Override
	public String name() {
		return "charge";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		Random random = world.getRandom();

		// The bearing is rolled blind. Nothing about where anybody is standing is consulted, here or
		// later - see the note above about why that is the point rather than an oversight.
		double bearing = random.nextDouble() * Math.PI * 2.0;
		Vec3d direction = new Vec3d(Math.cos(bearing), 0.0, Math.sin(bearing));
		Vec3d start = direction.multiply(-START_OUT).add(0.0, Attacks.GROUND_Y, 0.0);

		// Offset sideways by a random amount, so it is a chord across the circle rather than always a
		// diameter through the middle. A run that always goes through the centre is a run everybody
		// learns to stand at the edge of.
		Vec3d across = new Vec3d(-direction.z, 0.0, direction.x);
		double offset = (random.nextDouble() - 0.5) * 2.0 * (TheBlank.RADIUS * 0.6);

		boss.launch(new Charge(start.add(across.multiply(offset)), direction));
	}

	/** The column of light, and then the thing that was standing behind it. */
	private static final class Charge implements Ongoing {
		private final Vec3d start;
		private final Vec3d direction;

		private int ticks;
		private WandererEntity runner;

		/** Everybody it has already caught, so one crossing is one hit and not three. */
		private final Set<UUID> hit = new HashSet<>();

		private Charge(Vec3d start, Vec3d direction) {
			this.start = start;
			this.direction = direction;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks <= SUMMON_TICKS) {
				summon(world);
				return false;
			}

			if (this.runner == null) {
				return !launch(world);
			}

			if (this.runner.isRemoved()) {
				return true;
			}

			sweep(boss, world);
			return false;
		}

		/**
		 * The warning: a column standing where it is about to come from, taller every tick.
		 *
		 * <p>Drawn at the spawn point rather than along the line it will take, which is a deliberate
		 * choice about how much is given away. Marking the whole line would make this a stripe on the
		 * floor to step off, and it would stop being a thing arriving out of the dark. A column tells
		 * you where and roughly when; working out that the answer to "where is it going" is "straight
		 * across, through the middle of the arena" is left to the player, once.
		 */
		private void summon(ServerWorld world) {
			double height = 1.0 + (double) this.ticks / SUMMON_TICKS * (WandererEntity.HEIGHT + 2.0);

			world.spawnParticles(ParticleTypes.END_ROD,
					this.start.x, this.start.y + height * 0.5, this.start.z,
					6, 0.35, height * 0.5, 0.35, 0.01);

			if (this.ticks == 1) {
				world.playSound(null, this.start.x, this.start.y, this.start.z,
						SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0F, 0.5F);
			}

			// A second beat halfway through, so the timing of what is coming is audible as well as
			// visible - the column is a long way out and the fight is loud.
			if (this.ticks == SUMMON_TICKS / 2) {
				world.playSound(null, this.start.x, this.start.y, this.start.z,
						SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.5F, 0.6F);
			}
		}

		/** @return whether there is now something running. */
		private boolean launch(ServerWorld world) {
			WandererEntity wanderer = ModEntities.WANDERER.create(world);

			if (wanderer == null) {
				return false;
			}

			// Flat, not following the ground: it starts and ends out over the void, where there is no
			// ground to follow and asking would sink it. The disc is one level sheet, so a fixed height
			// is not an approximation - it is the floor.
			wanderer.beginRun(this.start, this.direction, RUN_LENGTH, false);
			world.spawnEntity(wanderer);
			this.runner = wanderer;

			world.playSound(null, this.start.x, this.start.y, this.start.z,
					SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0F, 0.4F);
			return true;
		}

		/**
		 * Whatever is standing on the line this tick.
		 *
		 * <p>Done here rather than by making the wanderer solid. It crosses about half a block a tick
		 * and a player is six tenths wide, so collision would be a coin toss on whether the two boxes
		 * ever overlapped on the same tick; a radius around its position hits everything it actually
		 * went through. The wanderer stays non-collidable, which also means it cannot be blocked, stood
		 * on, or shoved off its line.
		 */
		private void sweep(TheEntity boss, ServerWorld world) {
			Vec3d at = this.runner.getPos();
			DamageSource source = boss.getDamageSources().mobAttack(boss);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,
					this.runner.getBoundingBox().expand(HIT_RADIUS, REACH_UP, HIT_RADIUS),
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				double dx = player.getX() - at.x;
				double dz = player.getZ() - at.z;

				// The box query is square and the sweep is not, and somebody it has not reached yet must
				// stay unrecorded so that it can still catch them a tick later.
				if (dx * dx + dz * dz > HIT_RADIUS * HIT_RADIUS || !this.hit.add(player.getUuid())) {
					continue;
				}

				player.damage(source, DAMAGE);

				// Thrown along the run rather than away from the middle of it. It is a shoulder from
				// something four metres tall going in a straight line - it does not push you aside, it
				// takes you with it.
				player.takeKnockback(KNOCKBACK, -this.direction.x, -this.direction.z);
				player.velocityModified = true;

				world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
						player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.4, 0.4, 0.0);
				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.6F, 0.6F);
			}
		}

		@Override
		public void cancel(ServerWorld world) {
			if (this.runner != null && !this.runner.isRemoved()) {
				this.runner.discard();
			}
		}
	}
}
