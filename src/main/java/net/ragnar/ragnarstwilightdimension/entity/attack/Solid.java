package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.mixin.BlockDisplayAccessor;
import net.ragnar.ragnarstwilightdimension.mixin.DisplayEntityAccessor;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * A bar of solid block, drawn between two points at any angle, of any length and any thickness.
 *
 * <p>Everything else this fight throws is particles - see the note in {@code WallAttack} about why -
 * and particles are the right answer for a slab of snow the size of a house, which is meant to read
 * as weather. They are the wrong answer for a laser. A laser is a hard edge: it either has one or it
 * is a cloud of dots in the shape of a line, and no amount of tuning the density gets you from the
 * second to the first.
 *
 * <p>So the two attacks that need a hard edge use the one thing in the game that has one. A block
 * display is a block drawn by an arbitrary matrix, with no collision, no physics, no light and no
 * interaction of any kind - a shape in the air and nothing else. Squashed to a fifth of a block on
 * two axes and stretched to seventy on the third, it is a solid, sharp-edged bar of whatever block
 * you gave it, lit at full brightness so it glows in the dark of the disc.
 *
 * <p>The reason it was avoided before is that everything on the class that decides what it looks
 * like is private - {@code WallAttack} calls this "writing NBT by hand and hoping". That is what
 * {@link DisplayEntityAccessor} is for: the same setters, reached by name, checked at startup.
 *
 * <h2>Taking them back</h2>
 *
 * <p>Unlike a particle, one of these is a real entity, and unlike the blank figure it has no
 * lifetime of its own and <i>is</i> written to the chunk. Every attack that spawns one is expected
 * to discard it, and does, on the tick it is finished and again at every phase seam. The backstop
 * for the one case that cannot be caught from inside the fight - the server going down in the two
 * seconds a beam is up - is {@link #sweep}, which the boss runs when it arrives.
 */
public final class Solid {
	/** Stamped on everything spawned here, so {@link #sweep} can recognise its own leftovers. */
	private static final String TAG = "twilight_solid";

	/** Multiples of sixty-four blocks. Two covers the disc from any point on it to any other. */
	private static final float VIEW_RANGE = 2.0F;

	/** Local {@code +z}: the axis the unit cube is stretched along before it is turned. */
	private static final Vector3f ALONG = new Vector3f(0.0F, 0.0F, 1.0F);

	private Solid() {
	}

	/**
	 * A bar from one point to another.
	 *
	 * <p>The entity sits at {@code from} and never moves; the whole of the shape is in its transform,
	 * so re-pointing one is {@link #aim} and costs no teleport.
	 */
	public static DisplayEntity.BlockDisplayEntity bar(ServerWorld world, BlockState block,
													   Vec3d from, Vec3d to, double thickness) {
		DisplayEntity.BlockDisplayEntity display = EntityType.BLOCK_DISPLAY.create(world);

		if (display == null) {
			return null;
		}

		// All of it before it is spawned, or every client in range gets one tick of a full-size block
		// of ice sitting in mid-air at the wrong angle.
		((BlockDisplayAccessor) display).callSetBlockState(block);
		((DisplayEntityAccessor) display).callSetBrightness(Brightness.FULL);
		((DisplayEntityAccessor) display).callSetViewRange(VIEW_RANGE);
		display.addCommandTag(TAG);

		display.refreshPositionAndAngles(from.x, from.y, from.z, 0.0F, 0.0F);
		shape(display, from, to, thickness);

		world.spawnEntity(display);
		return display;
	}

	/**
	 * Points an existing bar somewhere else, keeping its near end where it is.
	 *
	 * <p>Called every tick by the sweeping beams. There is deliberately no interpolation asked for:
	 * a display only lerps between transforms if its {@code start_interpolation} is re-sent, and that
	 * field is tracked data, which is not re-sent when it is set to the value it already had. Asking
	 * for it and not getting it would leave the beam a tick behind where it hits, which is worse than
	 * stepping - so the transform is simply replaced, twenty times a second, and the sweep is kept
	 * slow enough that the steps do not read.
	 */
	public static void aim(DisplayEntity.BlockDisplayEntity display, Vec3d from, Vec3d to, double thickness) {
		if (display != null && !display.isRemoved()) {
			shape(display, from, to, thickness);
		}
	}

	/** Gone, and nothing left where it was. */
	public static void remove(DisplayEntity.BlockDisplayEntity display) {
		if (display != null && !display.isRemoved()) {
			display.discard();
		}
	}

	/**
	 * Anything of ours still standing on the disc from a fight that did not end cleanly.
	 *
	 * <p>Only ever leftovers: nothing spawned here outlives the attack that made it, so anything this
	 * finds was written to the chunk by a server that stopped mid-attack. The tag is what keeps it to
	 * ours - somebody's own block displays in the arena, however unlikely, are not the boss's to
	 * delete.
	 */
	public static void sweep(ServerWorld world) {
		double reach = TheBlank.RADIUS + 8.0;

		Box arena = new Box(-reach, TheBlank.FLOOR_Y - 4.0, -reach, reach, TheBlank.FLOOR_Y + 64.0, reach);
		List<DisplayEntity.BlockDisplayEntity> stale = world.getEntitiesByClass(
				DisplayEntity.BlockDisplayEntity.class, arena, display -> display.getCommandTags().contains(TAG));

		for (DisplayEntity.BlockDisplayEntity display : stale) {
			display.discard();
		}
	}

	/**
	 * The matrix.
	 *
	 * <p>A display draws its block as the unit cube from its own position out along {@code +x +y +z},
	 * put through {@code translation * leftRotation * scale}. So the cube is first stretched into a
	 * bar of the right length along {@code +z}, then turned to lie along the line, and then shifted
	 * half its thickness back on the other two axes so the line runs down the middle of it rather
	 * than along one corner - and that shift has to be turned too, because it happens after.
	 */
	private static void shape(DisplayEntity.BlockDisplayEntity display, Vec3d from, Vec3d to, double thickness) {
		Vec3d span = to.subtract(from);
		double length = Math.max(span.length(), 1.0E-4);

		Quaternionf turn = new Quaternionf().rotationTo(ALONG,
				new Vector3f((float) (span.x / length), (float) (span.y / length), (float) (span.z / length)));

		float half = (float) thickness * 0.5F;
		Vector3f offset = new Vector3f(-half, -half, 0.0F).rotate(turn);

		((DisplayEntityAccessor) display).callSetTransformation(new AffineTransformation(
				offset, turn, new Vector3f((float) thickness, (float) thickness, (float) length), new Quaternionf()));
	}
}
