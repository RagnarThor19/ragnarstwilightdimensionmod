package net.ragnar.ragnarstwilightdimension.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A square of ground that is in the wrong place, by exactly ten blocks on a side and no blend at all.
 *
 * <p>Two of them, and they are the same fault read in opposite directions. One drops the square out
 * of the world entirely, leaving a shaft with sheer stone walls that falls the whole depth of the
 * terrain and then keeps going, through the empty sixty blocks the dimension has under its crust,
 * and stops on the bedrock. The other lifts the square twenty blocks and leaves it there, standing on
 * a plinth of its own stone with grass still on top of it.
 *
 * <p>Neither is eroded, sloped, cracked or decorated. The edges are the edges of the hundred columns
 * the feature was given and nothing has been done to disguise them, because the point is that the
 * terrain has a grain size and here it slipped by one square of it. It is the arithmetic showing
 * through - the same thing the far lands were, which were never built either.
 *
 * <p><b>The shaft does not reach the void and must not be made to.</b> It stops one block above
 * {@code min_y}, so whatever goes down it lands on the bedrock floor and stays in the world. A hole
 * that dropped a player out of the bottom would be an ordinary death; this is a room nobody was meant
 * to be able to get into, with a floor, in the dark, sixty blocks under a dimension that has no caves.
 *
 * <p>Both run in {@code raw_generation}, before anything is planted. So the trees, the grass and the
 * buildings arrive afterwards and lay themselves over whatever they find: they grow on top of the
 * raised square as if it had always been that height, and they do not grow in the shaft, because
 * there is no longer any ground there for them to test.
 */
public class FaultFeature extends Feature<DefaultFeatureConfig> {
	/** Which way the square went. */
	public enum Kind {
		/** Straight down, out of the world, as far as the bedrock. */
		SUBSIDENCE,

		/** Twenty blocks up, and still there. */
		UPLIFT
	}

	/** Ten by ten, exactly, both of them. A fault with a soft edge is a hill. */
	public static final int SIDE = 10;

	/** How far {@link Kind#UPLIFT} raises the square. */
	private static final int LIFT = 20;

	/**
	 * The most the ground may rise and fall across the square before it is left alone.
	 *
	 * <p>Not tidiness - it is what keeps the edge legible. On level ground the fault is a clean wall
	 * ten blocks long and obviously not terrain; across a slope it is a lump, and it reads as an
	 * outcrop that somebody could have walked past without noticing. It also quietly refuses any
	 * square with a tree already standing in it, since the surface under a canopy is twenty blocks
	 * above the surface beside it.
	 */
	public static final int RELIEF = 4;

	/** Soil under the top block of a raised square, so it looks like ground rather than a cut face. */
	private static final int SOIL = 2;

	private final Kind kind;

	public FaultFeature(Codec<DefaultFeatureConfig> codec, Kind kind) {
		super(codec);
		this.kind = kind;
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();

		int[][] surface = new int[SIDE][SIDE];
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;

		// The terrain's own heightmap rather than a search downward. A hundred columns is too many to
		// probe one at a time, and at this stage the heightmap is still exactly the surface the noise
		// and the surface rule left - nothing has been planted on it yet.
		for (int dx = 0; dx < SIDE; dx++) {
			for (int dz = 0; dz < SIDE; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
				BlockState state = world.getBlockState(new BlockPos(x, top, z));

				if (state.isAir() || !state.getFluidState().isEmpty()) {
					return false;
				}

				surface[dx][dz] = top;
				lowest = Math.min(lowest, top);
				highest = Math.max(highest, top);
			}
		}

		if (highest - lowest > RELIEF) {
			return false;
		}

		return this.kind == Kind.SUBSIDENCE
				? drop(world, origin, surface)
				: raise(world, origin, surface, highest);
	}

	/**
	 * Takes the square out. Everything from the surface down to one block above the floor of the
	 * world, which leaves the bedrock layer whole.
	 */
	private static boolean drop(StructureWorldAccess world, BlockPos origin, int[][] surface) {
		int floor = world.getBottomY() + 1;

		for (int dx = 0; dx < SIDE; dx++) {
			for (int dz = 0; dz < SIDE; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;

				for (int y = surface[dx][dz]; y >= floor; y--) {
					world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				}
			}
		}

		return true;
	}

	/**
	 * Puts the square up on a plinth of its own stone, keeping whatever was on top of each column on
	 * top of it. The ground it was cut from is left where it was - nothing opens underneath.
	 */
	private static boolean raise(StructureWorldAccess world, BlockPos origin, int[][] surface, int highest) {
		// Nothing may be standing in the twenty blocks the square is going to occupy. Measured off the
		// highest column so the check covers the whole slab at once.
		for (int dx = 0; dx < SIDE; dx++) {
			for (int dz = 0; dz < SIDE; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;

				for (int y = surface[dx][dz] + 1; y <= highest + LIFT; y++) {
					if (!GravestoneFeature.isFree(world, new BlockPos(x, y, z))) {
						return false;
					}
				}
			}
		}

		for (int dx = 0; dx < SIDE; dx++) {
			for (int dz = 0; dz < SIDE; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int from = surface[dx][dz];
				BlockState top = world.getBlockState(new BlockPos(x, from, z));

				for (int course = 1; course <= LIFT; course++) {
					BlockState state;

					if (course == LIFT) {
						state = top;
					} else if (course > LIFT - 1 - SOIL) {
						state = Blocks.DIRT.getDefaultState();
					} else {
						state = Blocks.STONE.getDefaultState();
					}

					world.setBlockState(new BlockPos(x, from + course, z), state, Block.NOTIFY_ALL);
				}
			}
		}

		return true;
	}
}
