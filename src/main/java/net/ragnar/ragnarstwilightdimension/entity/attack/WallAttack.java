package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Five by five of the floor gets up and goes across the room.
 *
 * <p>A slab of the disc's own snow, five wide and five tall, standing on the ground and travelling
 * flat out from one rim to the other. It does not rise, it does not turn and it does not care where
 * anybody is - the only way past it is to not be on the five blocks it is going through.
 *
 * <p>It is the third of the three unaimed attacks, and the one that closes the gap the other two
 * leave. The charge is a line you step off; the snowfall is squares you stand between. Both of those
 * can be beaten by holding still in the right place. This one is a moving wall, and holding still is
 * exactly how it gets you - it turns the arena from a set of places into a set of <i>moments</i>.
 *
 * <h2>Why it is drawn rather than built</h2>
 *
 * <p>It is particles, not blocks, and not a block display either. Real blocks would leave the arena
 * full of snow if the server stopped mid-flight and would suffocate whoever they were placed inside;
 * a {@code BlockDisplayEntity} keeps its block state and its scale behind private setters, so driving
 * one means writing NBT by hand and hoping - and a boss attack that silently renders nothing when a
 * key name changes is a bad trade for a nicer edge on the cube. What is here is a dense grid of the
 * snow block's own texture, which at this size and speed reads as a solid face of snow, and is the
 * same way every other attack in this fight is drawn.
 */
public final class WallAttack implements Attack {
	private static final int GRACE = 160;

	/** Ticks between walls, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 260;
	private static final int FASTEST = 130;

	/** How long it stands at the rim before it goes. */
	private static final int SUMMON_TICKS = 25;

	/** Five by five, measured out from the middle of the face. */
	private static final double HALF_WIDTH = 2.5;
	private static final double HEIGHT = 5.0;

	/** Blocks a tick. A sprinting player does about 0.28, so it cannot be outrun along its own line. */
	private static final double SPEED = 0.85;

	/** How far out it starts, and how far it goes. Both ends are off the disc. */
	private static final double START_OUT = TheBlank.RADIUS + 4.0;
	private static final double RUN_LENGTH = START_OUT * 2.0 + 6.0;

	/** How thick the face is for the purposes of being hit by it. */
	private static final double THICKNESS = 1.4;

	/** Ten hearts. */
	private static final float DAMAGE = 20.0F;

	/** Crazy, as asked. It is a five metre wall of snow doing twenty blocks a second. */
	private static final double KNOCKBACK = 3.4;

	/** How finely the face is drawn, in blocks between points. */
	private static final double GRID = 0.55;

	@Override
	public String name() {
		return "wall";
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
		Vec3d across = new Vec3d(-direction.z, 0.0, direction.x);

		// Offset sideways, like the charge, so it is a chord rather than always a diameter - otherwise
		// the middle of the arena is the one place that is never safe and the rim always is.
		double offset = (random.nextDouble() - 0.5) * 2.0 * (TheBlank.RADIUS * 0.55);
		Vec3d start = direction.multiply(-START_OUT).add(across.multiply(offset))
				.add(0.0, Attacks.GROUND_Y, 0.0);

		boss.launch(new Wall(start, direction, across));
	}

	/** One slab on its way across. */
	private static final class Wall implements Ongoing {
		private final Vec3d direction;
		private final Vec3d across;

		private Vec3d at;
		private int ticks;
		private double travelled;

		/** Everybody it has already carried off, so one crossing is one hit. */
		private final Set<UUID> hit = new HashSet<>();

		private Wall(Vec3d start, Vec3d direction, Vec3d across) {
			this.at = start;
			this.direction = direction;
			this.across = across;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks <= SUMMON_TICKS) {
				summon(world);
				return false;
			}

			this.at = this.at.add(this.direction.multiply(SPEED));
			this.travelled += SPEED;

			draw(world);
			sweep(boss, world);

			return this.travelled >= RUN_LENGTH;
		}

		/** It builds itself at the rim first, so there is a second to see which way the room is closing. */
		private void summon(ServerWorld world) {
			float share = (float) this.ticks / SUMMON_TICKS;

			for (double h = 0.0; h < HEIGHT * share; h += 0.8) {
				for (double w = -HALF_WIDTH; w <= HALF_WIDTH; w += 0.8) {
					Vec3d point = this.at.add(this.across.multiply(w)).add(0.0, h, 0.0);

					world.spawnParticles(ParticleTypes.SNOWFLAKE,
							point.x, point.y, point.z, 1, 0.1, 0.1, 0.1, 0.0);
				}
			}

			if (this.ticks == 1) {
				world.playSound(null, this.at.x, this.at.y, this.at.z,
						SoundEvents.BLOCK_SNOW_PLACE, SoundCategory.HOSTILE, 3.0F, 0.4F);
			}
		}

		/**
		 * The face, drawn as a grid of the snow block's own broken texture.
		 *
		 * <p>Zero velocity on every particle, which is what makes it a face rather than a cloud - block
		 * particles given any spread at all drift apart within a tick and the whole thing reads as spray.
		 */
		private void draw(ServerWorld world) {
			BlockStateParticleEffect snow =
					new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.getDefaultState());

			for (double h = 0.1; h < HEIGHT; h += GRID) {
				for (double w = -HALF_WIDTH; w <= HALF_WIDTH; w += GRID) {
					Vec3d point = this.at.add(this.across.multiply(w)).add(0.0, h, 0.0);

					world.spawnParticles(snow, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}

			// A little spray off the leading edge, which is the only part allowed to look like weather.
			Vec3d edge = this.at.add(this.direction.multiply(0.6));
			world.spawnParticles(ParticleTypes.SNOWFLAKE,
					edge.x, edge.y + HEIGHT * 0.5, edge.z, 6, HALF_WIDTH * 0.6, HEIGHT * 0.4, 0.2, 0.02);
		}

		private void sweep(TheEntity boss, ServerWorld world) {
			DamageSource source = boss.getDamageSources().mobAttack(boss);

			Box reach = new Box(
					this.at.x - HALF_WIDTH - 1.0, this.at.y - 1.0, this.at.z - HALF_WIDTH - 1.0,
					this.at.x + HALF_WIDTH + 1.0, this.at.y + HEIGHT, this.at.z + HALF_WIDTH + 1.0);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, reach,
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				Vec3d gap = player.getPos().subtract(this.at);

				// Resolved along the wall's own two axes rather than against a box, because the wall is at
				// whatever angle it was rolled and a box is not. Along is how far through the face they
				// are, sideways is how far along it - and only the first of those is thin.
				double along = Math.abs(gap.dotProduct(this.direction));
				double sideways = Math.abs(gap.dotProduct(this.across));

				if (along > THICKNESS || sideways > HALF_WIDTH || !this.hit.add(player.getUuid())) {
					continue;
				}

				player.damage(source, DAMAGE);

				// Along the wall's heading, not away from its middle. It does not brush people aside, it
				// takes them with it - and a little upward, so what follows is a long trip rather than a
				// scrape along the floor.
				player.takeKnockback(KNOCKBACK, -this.direction.x, -this.direction.z);
				player.addVelocity(0.0, 0.55, 0.0);
				player.velocityModified = true;

				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.8F, 0.5F);
			}
		}
	}
}
