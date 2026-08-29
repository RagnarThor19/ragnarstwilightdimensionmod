package net.ragnar.ragnarstwilightdimension.world.dimension;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The shape of the blank one's dimension, in one place.
 *
 * <p>A single disc of snow hanging in the dark, and nothing else anywhere in the world. There is no
 * terrain to speak of and no reason for any of these numbers to be discovered at runtime, so they
 * are constants and both the generator that builds the disc and the code that puts players onto it
 * read them from here. Changing {@link #RADIUS} changes the world; changing it after somebody has
 * been there leaves the chunks they already visited at the old size.
 */
public final class TheBlank {
	/** How far the floor reaches from the centre, in blocks, in every direction. */
	public static final int RADIUS = 50;

	/** The y of the topmost floor block. Players stand at {@code FLOOR_Y + 1}. */
	public static final int FLOOR_Y = 64;

	/**
	 * How many blocks thick the disc is. Three so that it reads as ground rather than as a sheet
	 * when seen edge-on from across the circle, which is the only angle anybody ever sees it from.
	 */
	public static final int THICKNESS = 3;

	/**
	 * Where the portal puts you: on the floor, out at the rim, looking in.
	 *
	 * <p>Not the centre. The centre is where the fight is, and arriving standing in it would put the
	 * player inside the thing they came to look at before the screen had finished loading.
	 */
	public static final Vec3d ARRIVAL = new Vec3d(0.5, FLOOR_Y + 1, RADIUS - 10 + 0.5);

	/** Facing north, which from {@link #ARRIVAL} is facing the middle of the circle. */
	public static final float ARRIVAL_YAW = 180.0F;

	/** Where the way out opens when the fight is over. */
	public static final BlockPos EXIT = new BlockPos(0, FLOOR_Y + 1, 0);

	private TheBlank() {
	}

	/** Whether the column at these world coordinates is floor rather than void. */
	public static boolean insideDisc(int x, int z) {
		return x * x + z * z <= RADIUS * RADIUS;
	}
}
