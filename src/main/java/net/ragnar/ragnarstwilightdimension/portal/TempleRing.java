package net.ragnar.ragnarstwilightdimension.portal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.util.math.BlockPos;
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
