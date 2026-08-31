package net.ragnar.ragnarstwilightdimension.entity.attack;

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
	private static final double KNOCKBACK = 1.5;

	/** How far above the floor still counts. Jumping is not a dodge; leaving the square is. */
	private static final double REACH_UP = 4.0;

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

	/** One square: the mark, the fall, and what was standing underneath. */
	private static final class Snowfall implements Ongoing {
		/** The middle of the three by three, snapped to the grid the blocks will actually land on. */
		private final BlockPos middle;
		private final Vec3d centre;

		private final List<FallingBlockEntity> falling = new ArrayList<>();

		private int ticks;
		private boolean landed;

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

			if (this.landed || this.ticks > MAX_TICKS) {
				return true;
			}

			// Watched rather than timed. Working out when a falling block lands is a matter of gravity,
			// drag and terminal velocity, and every one of those is vanilla's to change - asking the
			// block where it is cannot go out of date.
			for (FallingBlockEntity block : this.falling) {
				if (block.isRemoved() || block.isOnGround()) {
					land(boss, world);
					this.landed = true;
					return true;
				}
			}

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
			Attacks.burst(boss, world, this.centre, RADIUS, DAMAGE, KNOCKBACK, REACH_UP);

			world.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.getDefaultState()),
					this.centre.x, Attacks.GROUND_Y + 0.3, this.centre.z, 60, 1.4, 0.3, 1.4, 0.2);
			world.spawnParticles(ParticleTypes.EXPLOSION,
					this.centre.x, Attacks.GROUND_Y + 0.5, this.centre.z, 2, 0.8, 0.1, 0.8, 0.0);

			world.playSound(null, this.centre.x, this.centre.y, this.centre.z,
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.4F, 1.5F);
		}

		@Override
		public void cancel(ServerWorld world) {
			for (FallingBlockEntity block : this.falling) {
				if (!block.isRemoved()) {
					block.discard();
				}
			}
		}
	}
}
