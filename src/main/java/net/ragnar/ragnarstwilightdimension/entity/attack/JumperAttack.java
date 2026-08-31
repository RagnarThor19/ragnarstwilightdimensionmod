package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
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
import org.joml.Vector3f;

import java.util.List;

/**
 * One of them comes across the disc, stops dead in the middle of its run, goes up, and comes back
 * down.
 *
 * <p>The wanderer's whole character everywhere else in the mod is that it does not stop. It walks a
 * line past you and out of the fog and nothing you do changes it, and {@link ChargeAttack} is that
 * fact made lethal without altering it in any way. This is the fact <b>breaking</b>, in front of you,
 * halfway across the arena - which is why it is worth having as a separate attack rather than as a
 * variant of the charge. The first one teaches you that the line is all it is. The second one teaches
 * you that you were wrong.
 *
 * <h2>The dodge</h2>
 *
 * <p>Everything else in this fight is dodged in space: be somewhere the attack is not. This one can
 * only be dodged in <b>time</b> - the shockwave takes whoever is standing on the floor when it
 * arrives, and passes clean under anybody who is in the air. So the counter is to jump, and to jump
 * <i>late</i>: a player who panics and jumps early is back on the ground by the time it lands.
 *
 * <p>That is why the fall is drawn as hard as it is. A dodge on a one-tick window is only fair if the
 * tick can be seen coming, so the descent has a ring closing on the ground underneath it, and the
 * whole thing is a four metre figure falling out of the sky rather than an effect.
 */
public final class JumperAttack implements Attack {
	private static final int GRACE = 200;

	/** Ticks between jumpers, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 240;
	private static final int FASTEST = 110;

	/** How far out it comes from, and how far it gets before it stops. */
	private static final double START_OUT = TheBlank.RADIUS + 5.0;
	private static final double RUN_LENGTH = START_OUT * 2.0 + 8.0;

	/** How long it runs before stopping, in ticks. Far enough in to be somewhere that matters. */
	private static final int RUN_TICKS_MIN = 55;
	private static final int RUN_TICKS_MAX = 95;

	/** The wind-up. It stands perfectly still, which on this thing is the loudest sound in the mod. */
	private static final int CROUCH_TICKS = 16;

	/** Up, hang, and down. */
	private static final int RISE_TICKS = 18;
	private static final int HANG_TICKS = 8;
	private static final int FALL_TICKS = 11;

	/** How high it goes. */
	private static final double JUMP_HEIGHT = 22.0;

	/** How far the shockwave reaches. */
	private static final double SHOCK_RADIUS = 5.0;

	private static final float DAMAGE = 8.0F;

	/**
	 * How hard whoever was standing there goes up.
	 *
	 * <p>Twenty blocks, near enough. A player climbs about {@code v squared / 0.16} blocks off a launch
	 * before gravity wins, so this is the square root of twenty times that, with a little on top for
	 * the drag that sum ignores. It is deliberately not exact: what matters is that it is a long way
	 * up and a long way down, and the fall at the end of it is vanilla's business.
	 */
	private static final double LAUNCH = 1.85;

	/** The ring under it on the way down, which is the only clock the dodge has. */
	private static final int RIM_POINTS = 24;

	private static final DustParticleEffect SHADOW =
			new DustParticleEffect(new Vector3f(0.85F, 0.88F, 0.95F), 1.4F);

	@Override
	public String name() {
		return "jumper";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		Random random = world.getRandom();

		double bearing = random.nextDouble() * Math.PI * 2.0;
		Vec3d direction = new Vec3d(Math.cos(bearing), 0.0, Math.sin(bearing));
		Vec3d start = direction.multiply(-START_OUT).add(0.0, Attacks.GROUND_Y, 0.0);

		int runTicks = RUN_TICKS_MIN + random.nextInt(RUN_TICKS_MAX - RUN_TICKS_MIN);

		boss.launch(new Jumper(start, direction, runTicks));
	}

	/** The run, the stop, the jump, and what was underneath. */
	private static final class Jumper implements Ongoing {
		private final Vec3d start;
		private final Vec3d direction;
		private final int runTicks;

		private WandererEntity runner;
		private int ticks;

		/** Where it stopped, and so where it comes back down. */
		private Vec3d landing = Vec3d.ZERO;

		private Jumper(Vec3d start, Vec3d direction, int runTicks) {
			this.start = start;
			this.direction = direction;
			this.runTicks = runTicks;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			if (this.runner == null) {
				return !launch(world);
			}

			if (this.runner.isRemoved()) {
				return true;
			}

			this.ticks++;

			// Still running. The wanderer is moving itself, exactly as it does in the twilight, and
			// nothing here touches it.
			if (this.ticks < this.runTicks) {
				return false;
			}

			if (this.ticks == this.runTicks) {
				stop(world);
				return false;
			}

			int since = this.ticks - this.runTicks;

			if (since <= CROUCH_TICKS) {
				crouch(world, since);
				return false;
			}

			int flight = since - CROUCH_TICKS;

			if (flight <= RISE_TICKS) {
				rise(world, flight);
				return false;
			}

			if (flight <= RISE_TICKS + HANG_TICKS) {
				return false;
			}

			int falling = flight - RISE_TICKS - HANG_TICKS;

			if (falling < FALL_TICKS) {
				fall(world, falling);
				return false;
			}

			land(boss, world);
			cancel(world);
			return true;
		}

		/** @return whether there is now something running. */
		private boolean launch(ServerWorld world) {
			WandererEntity wanderer = ModEntities.WANDERER.create(world);

			if (wanderer == null) {
				return false;
			}

			// Flat, for the same reason the charge is: it starts out over the void, where the heightmap
			// has no floor to report.
			wanderer.beginRun(this.start, this.direction, RUN_LENGTH, false);
			world.spawnEntity(wanderer);
			this.runner = wanderer;
			return true;
		}

		/** The moment it does the thing it has never done. */
		private void stop(ServerWorld world) {
			this.runner.pause();
			this.landing = new Vec3d(this.runner.getX(), Attacks.GROUND_Y, this.runner.getZ());

			world.playSound(null, this.landing.x, this.landing.y, this.landing.z,
					SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0F, 0.4F);
		}

		private void crouch(ServerWorld world, int since) {
			if (since % 3 != 0) {
				return;
			}

			world.spawnParticles(ParticleTypes.SNOWFLAKE,
					this.landing.x, Attacks.GROUND_Y + 0.2, this.landing.z, 6, 0.7, 0.05, 0.7, 0.02);
		}

		private void rise(ServerWorld world, int flight) {
			// Eased, so it leaves hard and slows at the top rather than travelling up at a constant rate
			// like a lift. What goes up like a lift does not read as having jumped.
			double share = (double) flight / RISE_TICKS;
			double height = JUMP_HEIGHT * (1.0 - (1.0 - share) * (1.0 - share));

			this.runner.setPosition(this.landing.x, Attacks.GROUND_Y + height, this.landing.z);

			if (flight == 1) {
				world.playSound(null, this.landing.x, this.landing.y, this.landing.z,
						SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 3.0F, 0.5F);
			}
		}

		/**
		 * Coming down, with a ring closing under it.
		 *
		 * <p>The ring is the dodge. It is drawn at the true radius of the shockwave and it tightens as
		 * the thing falls, so the moment of impact is readable off the floor without looking up - which
		 * matters, because looking up in this fight is how you walk into everything else.
		 */
		private void fall(ServerWorld world, int falling) {
			double share = (double) falling / FALL_TICKS;
			double height = JUMP_HEIGHT * (1.0 - share * share);

			this.runner.setPosition(this.landing.x, Attacks.GROUND_Y + height, this.landing.z);

			double closing = SHOCK_RADIUS * (1.0 - share * 0.55);

			for (int i = 0; i < RIM_POINTS; i++) {
				double bearing = Math.PI * 2.0 * i / RIM_POINTS;

				world.spawnParticles(SHADOW,
						this.landing.x + Math.cos(bearing) * closing, Attacks.GROUND_Y + 0.1,
						this.landing.z + Math.sin(bearing) * closing, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/**
		 * The landing.
		 *
		 * <p>Anybody in the air is passed clean under and takes nothing at all - not reduced damage, not
		 * reduced launch, nothing. A dodge that still costs half is not a dodge, and the whole of this
		 * attack is that there is one thing to do and one moment to do it in.
		 */
		private void land(TheEntity boss, ServerWorld world) {
			this.runner.setPosition(this.landing.x, Attacks.GROUND_Y, this.landing.z);

			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
					this.landing.x, Attacks.GROUND_Y, this.landing.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.SNOWFLAKE,
					this.landing.x, Attacks.GROUND_Y + 0.3, this.landing.z, 120, SHOCK_RADIUS * 0.7, 0.2,
					SHOCK_RADIUS * 0.7, 0.35);
			world.playSound(null, this.landing.x, this.landing.y, this.landing.z,
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5F, 0.6F);

			DamageSource source = boss.getDamageSources().mobAttack(boss);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,
					this.runner.getBoundingBox().expand(SHOCK_RADIUS, 3.0, SHOCK_RADIUS),
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				double dx = player.getX() - this.landing.x;
				double dz = player.getZ() - this.landing.z;

				if (dx * dx + dz * dz > SHOCK_RADIUS * SHOCK_RADIUS) {
					continue;
				}

				// The whole attack, in one line. It travels through the floor, so the floor is the only
				// thing it can reach you through.
				if (!player.isOnGround()) {
					world.spawnParticles(ParticleTypes.END_ROD,
							player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
					continue;
				}

				player.damage(source, DAMAGE);

				// Straight up, and set rather than added, so it is the same twenty blocks whatever they
				// happened to be doing at the time.
				player.setVelocity(player.getVelocity().x, LAUNCH, player.getVelocity().z);
				player.velocityModified = true;
			}
		}

		@Override
		public void cancel(ServerWorld world) {
			if (this.runner != null && !this.runner.isRemoved()) {
				this.runner.discard();
				this.runner = null;
			}
		}
	}
}
