package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.entity.WatcherEntity;
import net.ragnar.ragnarstwilightdimension.entity.WatcherSpawner;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The thing in the dark opens its eye all the way, and a column of light the width of a house comes
 * down onto the disc.
 *
 * <p>Everything else in the fight comes from the middle of the arena or out of the floor of it. This
 * comes from ninety blocks up and eighty blocks out, from something that has been standing there since
 * before anybody walked in and has never once done anything - see {@link WatcherEntity}. That is the
 * whole of why it is worth having: the players have already decided what the watchers are, and what
 * they decided was scenery.
 *
 * <h2>Four and a half seconds</h2>
 *
 * <p>It is the longest wind-up in the fight by a factor of three, and it needs every tick of it,
 * because what lands is not survivable and is not meant to be. The warning is in three places at once
 * and all three are unmissable:
 *
 * <ul>
 *   <li><b>The eye.</b> It swells and lights up, out in the dark, on every client watching.
 *   <li><b>The line.</b> A thread of white from the eye down to the floor, thickening the whole way
 *       through the wind-up, so the angle it is coming in on can be read from anywhere on the disc.
 *   <li><b>The circle.</b> A ring on the floor where it is going to land, with a second ring inside it
 *       closing in on the first - which is the clock. When the two rings meet, it fires.
 * </ul>
 *
 * <p>It is aimed at where somebody <i>is</i>, not where they are going. That is the opposite of nearly
 * everything else here and it is deliberate: at this damage, leading the target would be an execution.
 * Aimed at the spot they were standing on when the eye opened, it is the one attack in the fight whose
 * entire counter is to walk, at any speed, in any direction, and keep walking.
 *
 * <h2>What it costs</h2>
 *
 * <p>Ninety. There is no armour in the game that makes that survivable from full health, and the
 * blast that goes out around the column afterwards will finish anybody it throws. Being hit by this is
 * not a mistake that costs you a fight, it is the fight ending - which is the price of a warning this
 * long and this loud, and is why it only comes round about twice in a phase.
 *
 * <p>The column stands for two seconds after it lands, and it goes on hurting for as long as it is
 * there. Walking into one that has already fired is the second way this kills people, and there is
 * nothing subtle about it either.
 */
public final class WatcherAttack implements Attack {
	/**
	 * How long into the phase before one can happen.
	 *
	 * <p>Thirteen seconds, and by a long way the longest grace in the fight. The opening of a survival
	 * phase is already the busiest part of it, and something that takes four and a half seconds to
	 * arrive and then deletes somebody does not belong in the same breath as the blackout.
	 */
	private static final int GRACE = 260;

	/** Ticks between shots, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 700;
	private static final int FASTEST = 460;

	/** How long the eye winds up for before it fires. */
	private static final int CHARGE_TICKS = 90;

	/** How long the column stands there once it has landed. */
	private static final int BEAM_TICKS = 40;

	/** And how long it takes to go out afterwards. */
	private static final int FADE_TICKS = 16;

	/**
	 * How wide the column is, in blocks, as drawn.
	 *
	 * <p>Two of them: a solid white core and a glass shell around it, because one bar of one block at
	 * this size reads as a wall rather than as light. The shell is what makes it a beam - it is
	 * see-through, so the core inside it is visible through its own halo.
	 */
	private static final double CORE = 5.0;
	private static final double SHELL = 13.0;

	/** How wide the thing that hits you is, measured off the middle of the column. */
	private static final double BEAM_RADIUS = 6.5;

	/** And what it takes out on the floor where it lands, which is wider than the column. */
	private static final double IMPACT_RADIUS = 8.0;

	/** How far above the impact the kill still counts. Jumping is not a dodge, at any height. */
	private static final double REACH_UP = 12.0;

	/** What it does. There is no number here that anybody walks away from. */
	private static final float DAMAGE = 90.0F;
	private static final double KNOCKBACK = 3.0;

	/** The blast that goes out around it when it lands, for everybody who was nearly clear. */
	private static final double WAVE_RADIUS = 18.0;
	private static final float WAVE_DAMAGE = 16.0F;
	private static final double WAVE_KNOCKBACK = 2.6;

	/** And what standing in one that is already burning costs, and how often it is charged. */
	private static final float STANDING_DAMAGE = 30.0F;
	private static final int STANDING_INTERVAL = 10;

	/** How thin the aiming line starts, and how thick it is by the time it fires. */
	private static final double AIM_THIN = 0.15;
	private static final double AIM_THICK = 1.1;

	/** How much wider than the impact the closing ring starts. See the note about the clock. */
	private static final double CLOCK_WIDE = 2.4;

	/** How finely the rings on the floor are drawn. */
	private static final int RING_POINTS = 40;

	private static final BlockState CORE_BLOCK = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState SHELL_BLOCK = Blocks.WHITE_STAINED_GLASS.getDefaultState();

	/** The ring that says where. Pale, thin, and drawn on the floor rather than in the air. */
	private static final DustParticleEffect MARK =
			new DustParticleEffect(new Vector3f(1.0F, 0.96F, 0.92F), 1.6F);

	/** The one closing in on it, which is the clock. Colder, so the two rings are told apart. */
	private static final DustParticleEffect CLOCK =
			new DustParticleEffect(new Vector3f(0.62F, 0.82F, 1.0F), 1.3F);

	@Override
	public String name() {
		return "watcher";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		ServerPlayerEntity target = targets.get(world.getRandom().nextInt(targets.size()));

		// Where they are standing now, and nothing else. See the note at the top about why this one
		// does not lead.
		strike(boss, world, new Vec3d(target.getX(), Attacks.GROUND_Y, target.getZ()));
	}

	/**
	 * One shot at one spot, from whichever of them is nearest it.
	 *
	 * <p>Public and separate from {@link #run} for {@code /blank watcher beam}, which is not a
	 * convenience: this is the longest and loudest single thing in the fight, it only comes round twice
	 * in a sixty-four second phase, and every part of it that matters - the wind-up, the rings, the
	 * angle it comes in at, what it looks like when it lands - is a matter of how it looks rather than
	 * of what it does. None of that can be tuned by waiting for the dice.
	 *
	 * @return whether one was actually fired
	 */
	public static boolean strike(TheEntity boss, ServerWorld world, Vec3d spot) {
		Vec3d at = Attacks.ontoDisc(new Vec3d(spot.x, Attacks.GROUND_Y, spot.z));
		WatcherEntity watcher = WatcherSpawner.shooter(world, at);

		if (watcher == null) {
			return false;
		}

		boss.launch(new Strike(watcher, at));

		// At everybody's own ears rather than at the watcher, which is ninety blocks out: the sound of
		// this starting has to arrive at the same volume for the player standing under it and the one
		// on the far rim, because both of them need to look.
		for (ServerPlayerEntity player : world.getPlayers()) {
			player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.4F, 0.35F);
			player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.HOSTILE, 1.2F, 0.5F);
		}

		return true;
	}

	/** One shot: the eye winding up, the column landing, and the two seconds it stands there. */
	private static final class Strike implements Ongoing {
		private final WatcherEntity watcher;

		/** Where it lands. Decided before the eye lights and never revised. */
		private final Vec3d at;

		/** Where it comes from, read once - the watcher does not move, and cannot be relied on to. */
		private final Vec3d from;

		/** Everybody it has already gone through, so the column charges each person once a beat. */
		private final Set<UUID> caught = new HashSet<>();

		private DisplayEntity.BlockDisplayEntity core;
		private DisplayEntity.BlockDisplayEntity shell;

		private int ticks;

		private Strike(WatcherEntity watcher, Vec3d at) {
			this.watcher = watcher;
			this.at = at;
			this.from = watcher.eyePos();
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks <= CHARGE_TICKS) {
				charge(world);
				return false;
			}

			int since = this.ticks - CHARGE_TICKS;

			if (since == 1) {
				fire(boss, world);
				return false;
			}

			if (since <= BEAM_TICKS) {
				standing(boss, world, since);
				return false;
			}

			if (since <= BEAM_TICKS + FADE_TICKS) {
				fade(since - BEAM_TICKS);
				return false;
			}

			cancel(world);
			return true;
		}

		/** The eye, the line and the two rings, all the way up to the tick it goes. */
		private void charge(ServerWorld world) {
			float progress = (float) this.ticks / CHARGE_TICKS;

			if (!this.watcher.isRemoved()) {
				this.watcher.charge(progress);
			}

			// The line, thickening. It is the same bar the whole way through rather than a new one each
			// tick, so what the player sees is one thing growing rather than a flicker.
			double thickness = MathHelper.lerp(progress, AIM_THIN, AIM_THICK);

			if (this.core == null) {
				this.core = Solid.bar(world, CORE_BLOCK, this.at, this.from, thickness);
			} else {
				Solid.aim(this.core, this.at, this.from, thickness);
			}

			rings(world, progress);
			winding(world, progress);
		}

		/**
		 * The circle it will land in, and the one closing on it.
		 *
		 * <p>Two rings rather than a countdown of anything else in the fight's vocabulary, because this
		 * is the only attack long enough that "it is coming" is not enough information - four and a half
		 * seconds of a ring simply being there tells nobody whether to run now or in three seconds. The
		 * inner one starts well outside the kill and closes onto it exactly as the eye finishes, so the
		 * gap between the two rings is the time left, read off the floor.
		 */
		private void rings(ServerWorld world, float progress) {
			if (this.ticks % 2 != 0) {
				return;
			}

			double closing = MathHelper.lerp(progress, IMPACT_RADIUS * CLOCK_WIDE, IMPACT_RADIUS);

			for (int i = 0; i < RING_POINTS; i++) {
				double bearing = Math.PI * 2.0 * i / RING_POINTS;
				double cos = Math.cos(bearing);
				double sin = Math.sin(bearing);

				// Not forced, unlike the eye: these are drawn on the floor around somebody who is
				// standing on them, and the thirty-two blocks an ordinary particle carries is every
				// player this circle is a problem for.
				world.spawnParticles(MARK, this.at.x + cos * IMPACT_RADIUS, this.at.y + 0.15,
						this.at.z + sin * IMPACT_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
				world.spawnParticles(CLOCK, this.at.x + cos * closing, this.at.y + 0.15,
						this.at.z + sin * closing, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/** The eye gathering, and the beat it gathers on. */
		private void winding(ServerWorld world, float progress) {
			if (this.ticks % 3 == 0) {
				show(world, ParticleTypes.END_ROD, this.from.x, this.from.y, this.from.z);
			}

			// A heartbeat that speeds up and climbs. It is the only part of the warning that works with
			// the screen off, which during a fight this loud is not a small thing.
			int beat = Math.max(4, 14 - Math.round(progress * 10.0F));

			if (this.ticks % beat == 0) {
				for (ServerPlayerEntity player : world.getPlayers()) {
					player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE,
							1.0F, 0.5F + progress * 0.8F);
				}
			}

			// And the warden's own wind-up, laid under the last two seconds of it.
			if (this.ticks == CHARGE_TICKS - 34) {
				for (ServerPlayerEntity player : world.getPlayers()) {
					player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE,
							2.5F, 0.45F);
				}
			}
		}

		/**
		 * It lands.
		 *
		 * <p>The order here is the one thing about this method that is not obvious. The column is dealt
		 * first and the blast around it second, because the game gives out half a second of
		 * invulnerability after any hit - so a blast dealt first would leave somebody standing dead
		 * centre of a ninety-damage laser having taken sixteen.
		 */
		private void fire(TheEntity boss, ServerWorld world) {
			this.watcher.charge(0.0F);

			Solid.remove(this.core);
			this.core = Solid.bar(world, CORE_BLOCK, this.at, this.from, CORE);
			this.shell = Solid.bar(world, SHELL_BLOCK, this.at, this.from, SHELL);

			hurt(boss, world, DAMAGE, true);
			Attacks.burst(boss, world, this.at, WAVE_RADIUS, WAVE_DAMAGE, WAVE_KNOCKBACK, REACH_UP);

			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.at.x, this.at.y + 1.0, this.at.z,
					4, IMPACT_RADIUS * 0.5, 0.5, IMPACT_RADIUS * 0.5, 0.0);
			world.spawnParticles(ParticleTypes.FLASH, this.at.x, this.at.y + 2.0, this.at.z,
					6, IMPACT_RADIUS * 0.4, 1.0, IMPACT_RADIUS * 0.4, 0.0);
			world.spawnParticles(ParticleTypes.END_ROD, this.at.x, this.at.y + 1.0, this.at.z,
					260, IMPACT_RADIUS * 0.8, 1.5, IMPACT_RADIUS * 0.8, 0.9);
			world.spawnParticles(ParticleTypes.SONIC_BOOM, this.at.x, this.at.y + 3.0, this.at.z,
					12, IMPACT_RADIUS * 0.5, 2.0, IMPACT_RADIUS * 0.5, 0.0);

			// The blast, drawn where it reaches, so what threw somebody across the disc is a thing they
			// watched arrive rather than a number.
			for (int i = 0; i < RING_POINTS * 2; i++) {
				double bearing = Math.PI * 2.0 * i / (RING_POINTS * 2);

				world.spawnParticles(ParticleTypes.SNOWFLAKE,
						this.at.x + Math.cos(bearing) * WAVE_RADIUS, this.at.y + 0.4,
						this.at.z + Math.sin(bearing) * WAVE_RADIUS, 6, 0.4, 0.3, 0.4, 0.08);
			}

			// Loud at the point of impact for anybody near it, and again at everybody's own ears, because
			// a sound this size arriving quietly for the player on the far rim reads as it having missed.
			boom(world, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 8.0F, 0.45F);
			boom(world, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 8.0F, 0.55F);
			world.playSound(null, this.at.x, this.at.y, this.at.z,
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 4.0F, 0.4F);

			for (ServerPlayerEntity player : world.getPlayers()) {
				player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0F, 0.5F);
				player.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0F, 0.6F);
			}
		}

		/** The two seconds it goes on standing there, and goes on costing. */
		private void standing(TheEntity boss, ServerWorld world, int since) {
			if (since % STANDING_INTERVAL == 0) {
				// Everybody it has already been through is cleared first, so a beat is a fresh chance to
				// be caught rather than a permanent exemption for anybody who was.
				this.caught.clear();
				hurt(boss, world, STANDING_DAMAGE, false);
			}

			world.spawnParticles(ParticleTypes.END_ROD, this.at.x, this.at.y + 1.0, this.at.z,
					24, CORE * 0.5, 2.0, CORE * 0.5, 0.35);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, this.at.x, this.at.y + 0.4, this.at.z,
					20, IMPACT_RADIUS * 0.6, 0.3, IMPACT_RADIUS * 0.6, 0.1);

			if (since % 8 == 0) {
				world.playSound(null, this.at.x, this.at.y, this.at.z,
						SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 4.0F, 0.35F);
			}

			// The rim of what is still lethal, for as long as it is.
			for (int i = 0; i < RING_POINTS; i++) {
				double bearing = Math.PI * 2.0 * i / RING_POINTS;

				world.spawnParticles(MARK,
						this.at.x + Math.cos(bearing) * IMPACT_RADIUS, this.at.y + 0.15,
						this.at.z + Math.sin(bearing) * IMPACT_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/** It goes out: the column thins to nothing rather than being switched off. */
		private void fade(int into) {
			double left = 1.0 - (double) into / FADE_TICKS;

			Solid.aim(this.core, this.at, this.from, CORE * left);
			Solid.aim(this.shell, this.at, this.from, SHELL * left);
		}

		/**
		 * Everybody inside the column, or inside the circle it landed in.
		 *
		 * <p>The column is tested as a real cylinder around the line from the floor up to the eye, not
		 * as a circle on the ground, because it comes in at forty degrees: a player on the far side of
		 * the impact is under the beam by twenty blocks of air, and one standing on the near side is
		 * inside it well before it reaches the floor.
		 *
		 * @param throwThem whether to knock them off it as well as hurt them. Only the landing does -
		 *                  being pushed out of a beam you walked into would be doing you a favour.
		 */
		private void hurt(TheEntity boss, ServerWorld world, float damage, boolean throwThem) {
			DamageSource source = boss.getDamageSources().mobAttack(boss);

			Vec3d heading = this.from.subtract(this.at);
			double length = heading.length();

			if (length < 1.0E-4) {
				return;
			}

			Vec3d along = heading.multiply(1.0 / length);

			// One box around the whole line, then the real test. The box is only there to keep the query
			// off every entity on the disc.
			Box around = new Box(this.at, this.from).expand(BEAM_RADIUS + IMPACT_RADIUS + 2.0);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, around,
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				if (!this.caught.add(player.getUuid())) {
					continue;
				}

				Vec3d eye = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0);
				Vec3d gap = eye.subtract(this.at);

				double up = MathHelper.clamp(gap.dotProduct(along), 0.0, length);
				double off = gap.subtract(along.multiply(up)).length();

				boolean inBeam = off <= BEAM_RADIUS;
				boolean inCircle = player.getY() - this.at.y < REACH_UP
						&& player.getY() - this.at.y > -2.0
						&& flatGap(player) <= IMPACT_RADIUS;

				if (!inBeam && !inCircle) {
					continue;
				}

				player.damage(source, damage);

				if (throwThem) {
					// Off the line rather than away from the middle of it, which on a column coming in at
					// an angle are two different directions and only one of them is out.
					Vec3d push = gap.subtract(along.multiply(up)).multiply(1.0, 0.0, 1.0);
					Vec3d away = push.lengthSquared() < 1.0E-4
							? new Vec3d(along.z, 0.0, -along.x)
							: push.normalize();

					player.takeKnockback(KNOCKBACK, -away.x, -away.z);
					player.velocityModified = true;
				}
			}
		}

		/** How far off the middle of the impact somebody is, measured on the floor. */
		private double flatGap(PlayerEntity player) {
			double dx = player.getX() - this.at.x;
			double dz = player.getZ() - this.at.z;

			return Math.sqrt(dx * dx + dz * dz);
		}

		/**
		 * A particle everybody can see, wherever it is.
		 *
		 * <p>The ordinary call only reaches players within thirty-two blocks, which is fine for
		 * everything else in this fight and useless here - the eye is ninety blocks out, and the warning
		 * drawn on it is the whole of the point of it.
		 */
		private void show(ServerWorld world, ParticleEffect particle, double x, double y, double z) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				world.spawnParticles(player, particle, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/** One sound, at the impact, loud enough to carry the length of the disc and then some. */
		private void boom(ServerWorld world, SoundEvent sound, float volume, float pitch) {
			world.playSound(null, this.at.x, this.at.y, this.at.z, sound, SoundCategory.HOSTILE, volume, pitch);
		}

		@Override
		public void cancel(ServerWorld world) {
			if (!this.watcher.isRemoved()) {
				this.watcher.charge(0.0F);
			}

			Solid.remove(this.core);
			Solid.remove(this.shell);

			this.core = null;
			this.shell = null;
		}
	}
}
