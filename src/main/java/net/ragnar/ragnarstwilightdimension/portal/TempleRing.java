package net.ragnar.ragnarstwilightdimension.portal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The twelve frames around a temple portal, found from any one of them.
 *
 * <p>Vanilla has its own answer to this in {@code EndPortalFrameBlock.getCompletedFramePattern}, and
 * it is not usable here for two reasons. It only matches a ring that is already complete, so there
 * is no way to ask it about a ring the player is halfway through filling; and it insists every frame
 * face the centre, which makes it a check on how the ring was built rather than on what it is.
 *
 * <p>So the ring is found geometrically instead: gather every frame within reach of the one that was
 * clicked, and accept them only if they are exactly the twelve blocks of a five-by-five perimeter
 * with its corners missing, all at one height. That is the same shape vanilla builds, minus the
 * opinion about facing, and it survives the temple being placed at any of its four rotations without
 * anything having to know which one it came out at.
 *
 * <p>The other thing that can be worked out from one frame is where the <i>building</i> is, which is
 * what {@link #outside} is for: the ring is in the exact middle of the template, so the walls, the
 * steps and the ground outside them are all a fixed distance from any block of the pool. That is the
 * one piece of this file that knows the template rather than the shape - see the note on it.
 */
public final class TempleRing {
	/** The ring is five across, so nothing in it is further than four blocks from anything else. */
	private static final int SEARCH = 4;

	/** A five-by-five perimeter with no corners. */
	private static final int FRAME_COUNT = 12;

	private static final int RING_WIDTH = 5;

	/**
	 * A found ring: where its middle is, and every frame in it.
	 *
	 * @param centre the block in the middle of the ring, level with the frames - the portal's centre
	 *               is directly here and the pool fills the three by three around it
	 * @param frames all twelve, in no particular order
	 */
	public record Ring(BlockPos centre, List<BlockPos> frames) {
		/** Whether every frame has its eye in, which is the thing that opens the portal. */
		public boolean isComplete(World world) {
			for (BlockPos frame : frames) {
				BlockState state = world.getBlockState(frame);
				if (!state.isOf(Blocks.END_PORTAL_FRAME) || !state.get(EndPortalFrameBlock.EYE)) {
					return false;
				}
			}
			return true;
		}
	}

	private TempleRing() {
	}

	/**
	 * Somewhere to stand outside the building, and which way to be looking.
	 *
	 * @param pos the middle of the block to arrive on
	 * @param yaw turned back towards the temple, so the first thing seen is the way back in
	 */
	public record Outside(Vec3d pos, float yaw) {
	}

	/**
	 * How far out from the ring the way back lands somebody, in blocks.
	 *
	 * <p>The temple is thirteen across with the ring exactly in the middle of it, so six is the wall
	 * and eight is two blocks of open ground past the foot of the steps. That relationship is the only
	 * thing this number depends on, and it is a property of the template - change the building and
	 * change this with it.
	 */
	private static final int CLEAR = 8;

	/**
	 * How far the ring sits above the ground outside.
	 *
	 * <p>Also the template's: it is placed with its floor on the surface and the ring four layers up,
	 * so the pool is three blocks over whatever anybody standing outside is standing on.
	 */
	private static final int STAND_DOWN = 3;

	/** How far above and below that the ground is still looked for, for a temple built on a slope. */
	private static final int LOOK_UP = 4;
	private static final int LOOK_DOWN = 6;

	/**
	 * Where somebody coming back out of the disc is put down.
	 *
	 * <p>Not on the portal they left from, which is the obvious answer and a trap in the most literal
	 * sense. The pool sits in the <i>ceiling</i> of a sealed chamber - there is no door in the
	 * template, no stair down to it and no way out of it except the portal overhead - so landing on
	 * the block they left from drops them three blocks into a stone box whose only exit is the trip
	 * they just finished. Somebody who has killed the thing, opened the way home and walked through it
	 * would arrive back underneath their own front door with nothing to do but go round again.
	 *
	 * <p>So the trip ends where it began: outside, at the foot of the steps, looking up at the
	 * building. The four sides are tried in turn and the first one with ground and headroom wins,
	 * which matters because the temple comes out at any of four rotations and can be cut into a slope.
	 *
	 * @return where to stand, or null if all four sides are unusable - the caller decides what a
	 *         temple buried in a mountain means
	 */
	public static Outside outside(World world, BlockPos portal) {
		int expect = portal.getY() - STAND_DOWN;

		for (Direction side : Direction.Type.HORIZONTAL) {
			BlockPos column = portal.offset(side, CLEAR);
			int standing = ground(world, column.getX(), column.getZ(), expect);

			if (standing == Integer.MIN_VALUE) {
				continue;
			}

			Vec3d spot = new Vec3d(column.getX() + 0.5, standing, column.getZ() + 0.5);
			return new Outside(spot, facing(spot, portal));
		}

		// Nowhere to stand on any side of it - a temple cut into a cliff, or one somebody has built
		// around. Second best is the rim of the ring itself, standing on a frame looking down into the
		// pool, which is where whoever lit it was standing when they did. It is not outside, but it is
		// out of the room, which is the part that matters: the chamber under the pool has no door.
		BlockPos rim = frameNear(world, portal);

		if (rim != null) {
			Vec3d spot = new Vec3d(rim.getX() + 0.5, rim.getY() + 1, rim.getZ() + 0.5);
			return new Outside(spot, facing(spot, portal));
		}

		return null;
	}

	/** Any frame of the ring this portal block belongs to, or null if the ring has been taken apart. */
	private static BlockPos frameNear(World world, BlockPos portal) {
		// Three, because the remembered block can be any of the nine in the pool and the frames are two
		// out from the middle of it.
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos candidate = portal.add(dx, 0, dz);

				if (world.getBlockState(candidate).isOf(Blocks.END_PORTAL_FRAME)) {
					return candidate;
				}
			}
		}

		return null;
	}

	/**
	 * The first place in this column somebody can stand, searched downwards from above head height.
	 *
	 * <p>Downwards so that a spot dug out of a hillside is found rather than the hillside over it, and
	 * bounded at both ends so that neither a cliff nor a ravine beside the temple counts as ground.
	 *
	 * @return the y to stand at, or {@link Integer#MIN_VALUE} for a column with nowhere in it
	 */
	private static int ground(World world, int x, int z, int expect) {
		// Chunks are loaded on demand and this one very often is not - it is being asked about from
		// another dimension, on behalf of somebody who is not there yet. Reading block states out of an
		// absent chunk answers air for all of them, which reads as a bottomless hole beside the temple.
		world.getChunk(new BlockPos(x, expect, z));

		for (int y = expect + LOOK_UP; y >= expect - LOOK_DOWN; y--) {
			BlockPos feet = new BlockPos(x, y, z);

			if (standable(world, feet)) {
				return y;
			}
		}

		return Integer.MIN_VALUE;
	}

	/** Solid underfoot, two blocks of nothing to stand in, and no water in either of them. */
	private static boolean standable(World world, BlockPos feet) {
		BlockPos under = feet.down();
		BlockPos head = feet.up();

		return world.getBlockState(under).isSolidBlock(world, under)
				&& world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
				&& world.getBlockState(head).getCollisionShape(world, head).isEmpty()
				&& world.getFluidState(feet).isEmpty()
				&& world.getFluidState(under).isEmpty();
	}

	/** Turned from one place towards another, in the game's own reckoning of yaw. */
	private static float facing(Vec3d from, BlockPos at) {
		double dx = at.getX() + 0.5 - from.x;
		double dz = at.getZ() + 0.5 - from.z;

		return (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
	}

	/**
	 * The ring the given frame belongs to, or null if it is not part of one.
	 *
	 * <p>Null covers everything from a lone frame somebody placed by hand to a real stronghold portal
	 * - a stronghold's ring is the same twelve blocks, so the caller has to be the one that decides a
	 * ring in the wrong dimension is not a temple.
	 */
	public static Ring find(World world, BlockPos frame) {
		if (!world.getBlockState(frame).isOf(Blocks.END_PORTAL_FRAME)) {
			return null;
		}

		List<BlockPos> frames = new ArrayList<>();
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		// One height only. Two rings stacked on top of each other are two rings.
		for (int dx = -SEARCH; dx <= SEARCH; dx++) {
			for (int dz = -SEARCH; dz <= SEARCH; dz++) {
				BlockPos candidate = frame.add(dx, 0, dz);
				if (!world.getBlockState(candidate).isOf(Blocks.END_PORTAL_FRAME)) {
					continue;
				}

				frames.add(candidate);
				minX = Math.min(minX, candidate.getX());
				minZ = Math.min(minZ, candidate.getZ());
				maxX = Math.max(maxX, candidate.getX());
				maxZ = Math.max(maxZ, candidate.getZ());
			}
		}

		if (frames.size() != FRAME_COUNT
				|| maxX - minX != RING_WIDTH - 1
				|| maxZ - minZ != RING_WIDTH - 1) {
			return null;
		}

		// The right number of frames in a box the right size is still not a ring - twelve frames in a
		// heap would pass both. Every one of them has to be on the edge of that box and not at a corner.
		for (BlockPos found : frames) {
			boolean onEdgeX = found.getX() == minX || found.getX() == maxX;
			boolean onEdgeZ = found.getZ() == minZ || found.getZ() == maxZ;

			if (!onEdgeX && !onEdgeZ) {
				return null;   // inside the ring
			}
			if (onEdgeX && onEdgeZ) {
				return null;   // a corner, which a temple ring leaves as stone
			}
		}

		return new Ring(new BlockPos(minX + RING_WIDTH / 2, frame.getY(), minZ + RING_WIDTH / 2), frames);
	}
}
