package net.ragnar.ragnarstwilightdimension.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A stand of bare oak columns, twenty blocks up, packed close enough to walk between.
 *
 * <p>Five to nine of them, each one block wide, no branches, no leaves, nothing on top. They are made
 * of the same oak log the trees twenty metres away are made of, and they stand in a clearing among
 * those trees at two to four blocks apart - close enough that the stand reads as one object rather
 * than several, and far enough that a player can get inside it.
 *
 * <p>This is not a building and it is not a tree. It is what the dimension produces when it is asked
 * for a forest and gets the trunks right and nothing else - the same material, the same height, the
 * same spacing a copse would have, with every part that makes a tree a tree left out. A player who
 * walks into one is standing in a wood that was never finished, and there is nothing in it to find:
 * no loot, no door, no way up, no marker. It is only wrong.
 *
 * <p>Deliberately <i>not</i> {@link PillarFeature}, which it superficially resembles. That one is
 * planks, a single column, and somebody's work, with a grave at the foot of it saying so. This is
 * logs, a cluster, and nobody's - it has no grave and no meaning, and the two must not be mistaken
 * for one another. Where the pillar is a decision, this is a defect.
 *
 * <p>Written as a feature rather than a jigsaw structure because the shape has to answer to the
 * ground under it. Every column finds its own footing and its own height, so the stand sits on a
 * slope the way a copse of trees does instead of levelling a platform for itself the way a template
 * would.
 */
public class LogGroveFeature extends Feature<DefaultFeatureConfig> {
	/** How many columns are aimed for. Fewer than {@link #MINIMUM} placed and the stand is abandoned. */
	private static final int FEWEST_WANTED = 5;
	private static final int MOST_WANTED = 9;

	/**
	 * Below this the thing stops reading as a cluster and becomes two lone posts, which is the
	 * pillar's job and not this one's.
	 */
	private static final int MINIMUM = 4;

	/** Each column picks its own height in this range, so the tops do not make a flat ceiling. */
	private static final int SHORTEST = 18;
	private static final int TALLEST = 22;

	/**
	 * Centre-to-centre spacing, which is the gap between two columns plus the one block each of them
	 * occupies - so three to five here is a walkable gap of two to four.
	 */
	private static final int NEAREST = 3;
	private static final int FURTHEST = 5;

	/** Tries at finding somewhere to put the next column before the stand settles for what it has. */
	private static final int ATTEMPTS = 60;

	/**
	 * How far a column's footing may sit above or below the first one's. The stand follows the ground,
	 * but past this it is straddling a cliff rather than standing on a slope.
	 */
	private static final int DROP = 3;

	/** How far down a column looks for something to stand on before giving up. */
	private static final int GROUND_SEARCH_DEPTH = 4;

	public LogGroveFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();

		int wanted = FEWEST_WANTED + random.nextInt(MOST_WANTED - FEWEST_WANTED + 1);

		List<BlockPos> bases = new ArrayList<>(wanted);
		List<Integer> heights = new ArrayList<>(wanted);

		int height = SHORTEST + random.nextInt(TALLEST - SHORTEST + 1);
		BlockPos first = footingFor(world, context.getOrigin(), height);

		if (first == null) {
			return false;
		}

		bases.add(first);
		heights.add(height);

		// Each new column is hung off one of the ones already standing rather than off the origin, so
		// the stand wanders across the clearing instead of filling a disc around its centre.
		for (int attempt = 0; attempt < ATTEMPTS && bases.size() < wanted; attempt++) {
			BlockPos anchor = bases.get(random.nextInt(bases.size()));
			int dx = random.nextInt(FURTHEST * 2 + 1) - FURTHEST;
			int dz = random.nextInt(FURTHEST * 2 + 1) - FURTHEST;
			int reach = dx * dx + dz * dz;

			if (reach < NEAREST * NEAREST || reach > FURTHEST * FURTHEST) {
				continue;
			}

			BlockPos at = anchor.add(dx, 0, dz);
			if (tooClose(bases, at)) {
				continue;
			}

			int next = SHORTEST + random.nextInt(TALLEST - SHORTEST + 1);
			BlockPos base = footingFor(world, at, next);

			if (base == null || Math.abs(base.getY() - first.getY()) > DROP) {
				continue;
			}

			bases.add(base);
			heights.add(next);
		}

		if (bases.size() < MINIMUM) {
			return false;
		}

		// Nothing has been written until here: a stand that could not find room for four columns leaves
		// no half of itself behind.
		for (int i = 0; i < bases.size(); i++) {
			raise(world, bases.get(i), heights.get(i));
		}

		return true;
	}

	/**
	 * The block a column at this position would stand on, or null if there is nowhere to stand or no
	 * room to go up.
	 *
	 * <p>Does not use {@link GravestoneFeature#findGround}, which insists on dirt because a grave has
	 * to be dug into something. These are posts: they stand on whatever the surface happens to be, and
	 * the snowfield's is not dirt.
	 */
	private static BlockPos footingFor(StructureWorldAccess world, BlockPos column, int height) {
		BlockPos.Mutable pos = new BlockPos.Mutable(column.getX(), column.getY() + 1, column.getZ());

		for (int i = 0; i < GROUND_SEARCH_DEPTH; i++) {
			BlockState state = world.getBlockState(pos);

			if (!state.getFluidState().isEmpty()) {
				return null;
			}

			if (!state.isAir() && !state.isReplaceable()) {
				return clear(world, pos, height) ? pos.toImmutable() : null;
			}

			pos.move(Direction.DOWN);
		}

		return null;
	}

	/**
	 * Whether the whole column is empty air. A post with a tree growing through it is a pair of
	 * accidents rather than the one deliberate wrong thing this is meant to be.
	 */
	private static boolean clear(StructureWorldAccess world, BlockPos base, int height) {
		for (int course = 1; course <= height; course++) {
			if (!GravestoneFeature.isFree(world, base.up(course))) {
				return false;
			}
		}

		return true;
	}

	/** Horizontal spacing is a floor as well as a ceiling - two columns never touch. */
	private static boolean tooClose(List<BlockPos> bases, BlockPos at) {
		for (BlockPos base : bases) {
			int dx = base.getX() - at.getX();
			int dz = base.getZ() - at.getZ();

			if (dx * dx + dz * dz < NEAREST * NEAREST) {
				return true;
			}
		}

		return false;
	}

	/** Puts one column in, from the footing up. */
	private static void raise(StructureWorldAccess world, BlockPos base, int height) {
		for (int course = 1; course <= height; course++) {
			world.setBlockState(base.up(course), Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
		}
	}
}
