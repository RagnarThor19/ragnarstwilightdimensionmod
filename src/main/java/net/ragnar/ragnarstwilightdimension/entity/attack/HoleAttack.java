package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The floor goes away.
 *
 * <p>The disc is three blocks of snow hanging in nothing, and every other attack in this fight treats
 * that as the stage rather than as part of the fight. This one takes a circle of it out - all three
 * layers, straight through - and for fifteen seconds there is a hole in the world with the void
 * underneath it.
 *
 * <p>It is the only attack that does no damage at all and the only one that can kill outright, which
 * is the entire idea. Everything else is arithmetic against a health bar; this is a question about
 * where you are standing, and getting it wrong is not a hit to be healed, it is the end of the
 * attempt. Nothing in the twilight has ever suggested the ground is optional.
 *
 * <h2>Why the warning is so long</h2>
 *
 * <p>Five seconds, which is three times anything else in the fight, and the circle is drawn solid
 * rather than as a rim. An attack with no damage roll and no partial outcome has to be completely
 * unambiguous or it is not a mechanic, it is a mugging - so it is announced early, drawn brightly,
 * and it never moves once it has been placed. Anybody who falls through this had five seconds and a
 * lit floor telling them not to.
 *
 * <p>The blocks are put back exactly as they were, and there are three separate paths that put them
 * back - the timer, the phase seam, and the fight ending - because a hole that outlives the attack
 * that made it is a permanently broken arena. See {@link #cancel} and {@code TheEntityFight.repair}.
 */
public final class HoleAttack implements Attack {
	private static final int GRACE = 240;

	/** How often the dice are rolled at all. */
	private static final int INTERVAL = 60;

	/** And the odds on each roll, at the start of the phase and at the end of it. */
	private static final float CHANCE_EARLY = 0.04F;
	private static final float CHANCE_LATE = 0.10F;

	/** How wide. */
	private static final double RADIUS = 3.0;

	/** Five seconds of a circle on the floor doing nothing whatsoever. */
	private static final int WARNING_TICKS = 100;

	/** And fifteen of there being no floor. */
	private static final int OPEN_TICKS = 300;

	/** Bright, and the one warm-white thing in the fight that is not the bolts. */
	private static final DustParticleEffect MARK =
			new DustParticleEffect(new Vector3f(1.0F, 0.97F, 0.80F), 1.5F);

	@Override
	public String name() {
		return "hole";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		if (tick < GRACE || tick % INTERVAL != 0) {
			return false;
		}

		// Never so late in the phase that the floor would still be open when the blackout comes. The
		// seam puts it back anyway, but a hole that closes because the phase ended rather than because
		// its time was up is one the players never got to finish reading.
		if (tick + WARNING_TICKS + OPEN_TICKS > TheEntity.SURVIVAL_TICKS) {
			return false;
		}

		return random.nextFloat() < CHANCE_EARLY + (CHANCE_LATE - CHANCE_EARLY) * progress;
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		boss.launch(new Hole(Attacks.somewhereOnDisc(world.getRandom())));
	}

	/** One circle: five seconds of warning, fifteen of nothing, and then the floor again. */
	private static final class Hole implements Ongoing {
		private final Vec3d centre;

		/** Exactly what was taken and exactly where, so what goes back is what was there. */
		private final List<BlockPos> taken = new ArrayList<>();
		private final List<BlockState> was = new ArrayList<>();

		private int ticks;
		private boolean open;

		private Hole(Vec3d centre) {
			this.centre = centre;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks < WARNING_TICKS) {
				mark(world);
				return false;
			}

			if (this.ticks == WARNING_TICKS) {
				dig(world);
				return false;
			}

			if (this.ticks % 10 == 0) {
				edge(world);
			}

			if (this.ticks >= WARNING_TICKS + OPEN_TICKS) {
				fill(world);
				return true;
			}

			return false;
		}

		/**
		 * The circle, drawn solid and brightening.
		 *
		 * <p>Filled rather than outlined, unlike every other warning in the fight. Those mark where
		 * something will land and the rim is the honest boundary; this marks where the floor will stop
		 * existing, and a rim would leave the middle of it looking like the safe part.
		 */
		private void mark(ServerWorld world) {
			if (this.ticks % 4 != 0) {
				return;
			}

			float share = (float) this.ticks / WARNING_TICKS;
			int rings = 3;

			for (int r = 1; r <= rings; r++) {
				double radius = RADIUS * r / rings;
				int points = 8 + r * 6;

				for (int i = 0; i < points; i++) {
					double bearing = Math.PI * 2.0 * i / points;

					world.spawnParticles(MARK,
							this.centre.x + Math.cos(bearing) * radius, Attacks.GROUND_Y + 0.1,
							this.centre.z + Math.sin(bearing) * radius, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}

			// And a column that grows as the five seconds run down, so the countdown is readable from
			// across the arena rather than only from standing on it.
			world.spawnParticles(ParticleTypes.END_ROD,
					this.centre.x, Attacks.GROUND_Y + share * 3.0, this.centre.z, 3, 0.4, 0.3, 0.4, 0.01);

			if (this.ticks == 4) {
				world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
						SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.6F, 1.5F);
			}
		}

		/** Straight through all three layers, and everything taken is written down first. */
		private void dig(ServerWorld world) {
			int reach = (int) Math.ceil(RADIUS);
			int bottom = TheBlank.FLOOR_Y - TheBlank.THICKNESS + 1;

			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					double x = this.centre.x + dx;
					double z = this.centre.z + dz;

					if (dx * dx + dz * dz > RADIUS * RADIUS) {
						continue;
					}

					for (int y = bottom; y <= TheBlank.FLOOR_Y; y++) {
						BlockPos at = BlockPos.ofFloored(x, y, z);
						BlockState state = world.getBlockState(at);

						if (state.isAir()) {
							continue;
						}

						this.taken.add(at);
						this.was.add(state);
						world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}

			this.open = true;

			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.HOSTILE, 3.0F, 0.5F);
			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5F, 0.6F);
		}

		/** A rim around the edge while it is open, so the hole is visible from above in the dark. */
		private void edge(ServerWorld world) {
			for (int i = 0; i < 20; i++) {
				double bearing = Math.PI * 2.0 * i / 20.0;

				world.spawnParticles(MARK,
						this.centre.x + Math.cos(bearing) * RADIUS, Attacks.GROUND_Y + 0.1,
						this.centre.z + Math.sin(bearing) * RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/** Exactly what was there, exactly where it was. */
		private void fill(ServerWorld world) {
			if (!this.open) {
				return;
			}

			for (int i = 0; i < this.taken.size(); i++) {
				world.setBlockState(this.taken.get(i), this.was.get(i), Block.NOTIFY_LISTENERS);
			}

			this.open = false;
			this.taken.clear();
			this.was.clear();

			world.spawnParticles(ParticleTypes.SNOWFLAKE,
					this.centre.x, Attacks.GROUND_Y + 0.5, this.centre.z, 60, RADIUS * 0.7, 0.3, RADIUS * 0.7, 0.05);
			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.BLOCK_SNOW_PLACE, SoundCategory.HOSTILE, 2.5F, 0.6F);
		}

		/** The phase ended while the floor was missing. It goes back, immediately. */
		@Override
		public void cancel(ServerWorld world) {
			fill(world);
		}
	}
}
