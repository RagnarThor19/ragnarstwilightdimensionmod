package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.BlockStateParticleEffect;
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
 * The ceiling comes down. Three by three of it, somewhere in the arena, over and over.
 *
 * <p>The disc is a sheet of snow hanging in nothing with no sky above it, and this is the only thing
 * in the mod that answers the question of what is up there. The answer is more of the same, falling.
 *
 * <p>Everything about this one is about being read from underneath. The mark on the floor is exactly
 * the nine columns that will be hit and not one block more, and the blocks come from far enough up
 * that they are audible before they are visible - so the attack is survivable by looking at the floor
 * or by listening, and being caught by it means you were doing neither.
 *
 * <p>The snow does not settle. Every block shatters on landing, because nine of these a phase for as
 * long as the fight lasts would otherwise bury the arena a metre deep and turn the disc into terrain.
 *
 * <h2>What being under it costs</h2>
 *
 * <p>Thirty metres of falling snow does not nudge somebody. A hit caught by the edge of the square
 * throws hard; a hit in the middle of it - the three columns somebody would be standing in if they
 * never read the mark at all - throws roughly three times as far as this attack used to, and upwards
 * as well as outwards, so the rest of it is spent in the air with no say in where it ends.
 *
 * <p>That is dangerous near the rim of the disc on purpose, and it is not a trick: the mark is thirty
 * ticks long and drawn as the exact nine blocks, and the middle of those nine is the one square in
 * the arena where the floor is about to be somewhere else. Being thrown off the world for standing
 * there is the same bargain {@code HoleAttack} makes, announced the same way and for as long.
 *
 * <h2>The crater</h2>
 *
 * <p>What lands takes the floor with it, all of it. The nine columns are cut out through the entire
 * three blocks of the disc for five seconds and then put back exactly as they were, which leaves a
 * three by three shaft with the void underneath it - the same hole {@code HoleAttack} opens, smaller
 * and for a third as long.
 *
 * <p>So the square that was announced is not a place to be hit, it is a place the world stops. Being
 * under a fall was always survivable and is still survivable; standing on the square for the five
 * seconds afterwards is not, and neither is being thrown onto it by the next one. The mark is the
 * exact nine blocks and it is up for thirty ticks before anything arrives, which is the same bargain
 * every hole in this fight makes.
 *
 * <p>Straight through rather than one layer deep, and that is the whole difference: a pit you climb
 * out of is scenery, and a shaft you fall through is the attack. Up to three of these land at once
 * for the whole sixty-four seconds, so by the end of a phase the disc is a floor with holes in it
 * that keep moving.
 *
 * <p>Three separate things put the floor back: the five second timer, the phase seam through
 * {@link Snowfall#cancel}, and {@code TheEntityFight.repair} if the server stops while one is open.
 */
public final class SnowfallAttack implements Attack {
	private static final int GRACE = 30;

	/** Ticks between falls, at the start of the phase and at the end of it. */
	private static final int SLOWEST = 70;
	private static final int FASTEST = 34;

	/** How many go up at once, at the start of the phase and at the end of it. */
	private static final int FEWEST = 1;
	private static final int MOST = 3;

	/** How long the floor is marked before anything is dropped on it. */
	private static final int WARNING_TICKS = 30;

	/** How far up they start. High enough that they are heard coming and arrive at speed. */
	private static final double DROP_HEIGHT = 30.0;

	/** Backstop, in case a block somehow never lands - nothing hangs about for ever. */
	private static final int MAX_TICKS = 200;

	private static final double RADIUS = 2.2;
	private static final float DAMAGE = 12.0F;

	/** How hard it throws anybody it only caught by the edge. */
	private static final double KNOCKBACK = 1.9;

	/** How close to the middle of the nine counts as having been underneath it. */
	private static final double DIRECT_RADIUS = 1.3;

	/** And what that costs. Far enough to cross most of the disc, from wherever it happened. */
	private static final double DIRECT_KNOCKBACK = 3.2;

	/** How far up a direct hit goes, which is what buys the throw its distance. */
	private static final double LIFT = 0.7;

	/** How far above the floor still counts. Jumping is not a dodge; leaving the square is. */
	private static final double REACH_UP = 4.0;

	/** How long the floor stays broken where one landed. */
	private static final int CRATER_TICKS = 100;

	/** The dimension's own colour, one shade colder, as everything drawn in this fight is. */
	private static final DustParticleEffect MARK =
			new DustParticleEffect(new Vector3f(0.93F, 0.95F, 1.0F), 1.2F);

	@Override
	public String name() {
		return "snowfall";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		Random random = world.getRandom();
		int many = FEWEST + random.nextInt(Math.round((MOST - FEWEST) * progress) + 1);

		for (int i = 0; i < many; i++) {
			// Blind, like the charge. Nothing here is aimed at anybody - what makes this dangerous is
			// how much of the floor is unsafe at once, not any one square being clever about where it is.
			boss.launch(new Snowfall(Attacks.somewhereOnDisc(random)));
		}
	}

	/** One square: the mark, the fall, what was standing underneath, and the shaft it leaves. */
	private static final class Snowfall implements Ongoing {
		/** The middle of the three by three, snapped to the grid the blocks will actually land on. */
		private final BlockPos middle;
		private final Vec3d centre;

		private final List<FallingBlockEntity> falling = new ArrayList<>();

		/** Exactly what the impact took out of the floor, so what goes back is what was there. */
		private final List<BlockPos> taken = new ArrayList<>();
		private final List<BlockState> was = new ArrayList<>();

		private int ticks;

		/** The tick it landed on, or zero while it is still coming down. */
		private int struck;

		private Snowfall(Vec3d at) {
			this.middle = BlockPos.ofFloored(at.x, Attacks.GROUND_Y, at.z);

			// Taken back off the grid so the damage circle sits in the middle of the nine blocks rather
			// than in the corner of the middle one.
			this.centre = new Vec3d(this.middle.getX() + 0.5, Attacks.GROUND_Y, this.middle.getZ() + 0.5);
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks < WARNING_TICKS) {
				mark(world);
				return false;
			}

			if (this.ticks == WARNING_TICKS) {
				drop(world);
				return false;
			}

			if (this.struck == 0) {
				// Watched rather than timed. Working out when a falling block lands is a matter of gravity,
				// drag and terminal velocity, and every one of those is vanilla to change - asking the
				// block where it is cannot go out of date.
				for (FallingBlockEntity block : this.falling) {
					if (block.isRemoved() || block.isOnGround()) {
						land(boss, world);
						this.struck = this.ticks;
						return false;
					}
				}

				// Nothing landed and nothing is going to. Nothing came out of the floor either, so there
				// is nothing left here to put back.
				return this.ticks > MAX_TICKS;
			}

			if (this.ticks - this.struck >= CRATER_TICKS) {
				fill(world);
				return true;
			}

			rim(world);
			return false;
		}

		/** The nine columns, drawn as themselves, so what is marked is exactly what is hit. */
		private void mark(ServerWorld world) {
			if (this.ticks % 5 != 0) {
				return;
			}

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					world.spawnParticles(MARK,
							this.centre.x + dx, Attacks.GROUND_Y + 0.1, this.centre.z + dz,
							2, 0.35, 0.02, 0.35, 0.0);
				}
			}

			if (this.ticks == 5) {
				world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
						SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.HOSTILE, 2.0F, 0.5F);
			}
		}

		private void drop(ServerWorld world) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos from = this.middle.add(dx, (int) DROP_HEIGHT, dz);

					// spawnFromBlock clears the block it is given and puts a falling one in its place. Up
					// here that block is already air, so what it clears is nothing and what is left is the
					// entity - which is the cheapest way to get one of these into the sky.
					FallingBlockEntity block =
							FallingBlockEntity.spawnFromBlock(world, from, Blocks.SNOW_BLOCK.getDefaultState());

					// Shatters instead of settling. Without this the arena silts up over a five minute
					// fight and the last phase is fought on top of a pile of the first one.
					block.setDestroyedOnLanding();
					block.dropItem = false;

					this.falling.add(block);
				}
			}
		}

		private void land(TheEntity boss, ServerWorld world) {
			Attacks.burst(boss, world, this.centre, RADIUS, DAMAGE, KNOCKBACK, REACH_UP,
					DIRECT_RADIUS, DIRECT_KNOCKBACK, LIFT);

			dig(world);

			world.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.getDefaultState()),
					this.centre.x, Attacks.GROUND_Y + 0.3, this.centre.z, 60, 1.4, 0.3, 1.4, 0.2);
			world.spawnParticles(ParticleTypes.EXPLOSION,
					this.centre.x, Attacks.GROUND_Y + 0.5, this.centre.z, 2, 0.8, 0.1, 0.8, 0.0);

			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.4F, 1.5F);
		}

		/**
		 * The nine columns, all three layers of them, taken out and written down.
		 *
		 * <p>Air is skipped rather than recorded, which is what makes two of these landing on the same
		 * square safe: the second one finds the first shaft already open, takes nothing, and puts nothing
		 * back when its own five seconds are up. The floor belongs to whoever actually lifted it.
		 *
		 * <p>{@code TheEntityFight.repair} is the backstop for all of it. A server stopped inside those
		 * five seconds, or a chunk unloaded out from under one, otherwise saves the disc with a hole in
		 * it that nothing will ever fill - the chunk has already been generated.
		 */
		private void dig(ServerWorld world) {
			int bottom = TheBlank.FLOOR_Y - TheBlank.THICKNESS + 1;

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int y = bottom; y <= TheBlank.FLOOR_Y; y++) {
						BlockPos at = new BlockPos(this.middle.getX() + dx, y, this.middle.getZ() + dz);
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

			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.HOSTILE, 2.5F, 0.5F);
		}

		/** Loose snow off the lip of it while it is open, so a shaft reads as a shaft in the dark. */
		private void rim(ServerWorld world) {
			if (this.taken.isEmpty() || (this.ticks - this.struck) % 10 != 0) {
				return;
			}

			world.spawnParticles(MARK,
					this.centre.x, Attacks.GROUND_Y - 0.4, this.centre.z, 6, 1.4, 0.05, 1.4, 0.0);
		}

		/** Exactly what was there, exactly where it was. */
		private void fill(ServerWorld world) {
			for (int i = 0; i < this.taken.size(); i++) {
				world.setBlockState(this.taken.get(i), this.was.get(i), Block.NOTIFY_LISTENERS);
			}

			if (!this.taken.isEmpty()) {
				world.spawnParticles(ParticleTypes.SNOWFLAKE,
						this.centre.x, Attacks.GROUND_Y + 0.2, this.centre.z, 25, 1.4, 0.2, 1.4, 0.03);
				world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
						SoundEvents.BLOCK_SNOW_PLACE, SoundCategory.HOSTILE, 1.6F, 0.7F);
			}

			this.taken.clear();
			this.was.clear();
		}

		@Override
		public void cancel(ServerWorld world) {
			for (FallingBlockEntity block : this.falling) {
				if (!block.isRemoved()) {
					block.discard();
				}
			}

			fill(world);
		}
	}
}
