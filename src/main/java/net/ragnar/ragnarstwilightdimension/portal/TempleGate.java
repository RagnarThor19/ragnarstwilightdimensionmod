package net.ragnar.ragnarstwilightdimension.portal;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.ragnar.ragnarstwilightdimension.block.ModBlocks;
import net.ragnar.ragnarstwilightdimension.sound.ChurchBell;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

/**
 * Lighting the temple.
 *
 * <p>Twelve eyes into twelve frames, exactly as the End portal is lit, and the last one opens
 * something else. The temple is the only building in the dimension with a full ring in it and the
 * only reason to have been keeping eyes of ender.
 *
 * <h2>Why this takes the interaction rather than letting vanilla have it</h2>
 *
 * <p>{@code EnderEyeItem} does the whole job in one method - places the eye, checks the ring,
 * and fills it with {@code minecraft:end_portal} - with nothing in between to hook. So the eye is
 * placed here instead and vanilla's item never runs on a temple frame. What that costs is that the
 * two behaviours have to be kept the same by hand: the eye going in, the sound and particles that
 * go with it, and the comparator update all have to be reproduced, and they are, below.
 *
 * <p>Everywhere else is left completely alone. A stronghold in the overworld still lights a real End
 * portal through vanilla's own code, because the dimension check below sends this straight back.
 *
 * <h2>Ordering</h2>
 *
 * <p>{@code TwilightPortal} is registered on the same event and looks at the same block. It steps
 * aside for anything with another frame within two blocks, which every frame in a twelve-frame ring
 * has, so a temple never reaches its one-frame ruin behaviour and a ruin never reaches this.
 */
public final class TempleGate {
	/** How far above the ring to look for the bell. The temple's hangs four blocks up. */
	private static final int BELL_SEARCH_UP = 12;

	private TempleGate() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(TempleGate::onUseBlock);
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
		BlockPos framePos = hit.getBlockPos();
		BlockState state = world.getBlockState(framePos);

		if (!state.isOf(Blocks.END_PORTAL_FRAME) || state.get(EndPortalFrameBlock.EYE)) {
			return ActionResult.PASS;
		}
		if (!player.getStackInHand(hand).isOf(Items.ENDER_EYE)) {
			return ActionResult.PASS;
		}

		// A ring of twelve in the overworld is a stronghold and belongs to vanilla. Only in the twilight
		// is one of these a temple.
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return ActionResult.PASS;
		}

		TempleRing.Ring ring = TempleRing.find(world, framePos);
		if (ring == null) {
			return ActionResult.PASS;
		}

		// Let the client predict as usual; the eye actually goes in server-side below.
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.PASS;
		}

		placeEye(serverWorld, framePos, state);
		player.getStackInHand(hand).decrementUnlessCreative(1, player);

		if (ring.isComplete(serverWorld)) {
			open(serverWorld, ring);
		}

		return ActionResult.SUCCESS;
	}

	/** The eye going in, matching vanilla's own handling of it beat for beat. */
	private static void placeEye(ServerWorld world, BlockPos pos, BlockState state) {
		BlockState filled = state.with(EndPortalFrameBlock.EYE, true);

		// The eye adds three pixels of height the frame did not have, so anything standing on it has to
		// be lifted clear before the shape changes under it.
		Block.pushEntitiesUpBeforeBlockChange(state, filled, world, pos);
		world.setBlockState(pos, filled, Block.NOTIFY_LISTENERS);
		world.updateComparators(pos, Blocks.END_PORTAL_FRAME);
		world.syncWorldEvent(WorldEvents.END_PORTAL_FRAME_FILLED, pos, 0);
	}

	/**
	 * Fills the ring and rings the bell.
	 *
	 * <p>The pool is the three by three inside the frames, at the frames' own height - the same nine
	 * blocks vanilla would have filled, holding this mod's portal instead of the End's.
	 */
	private static void open(ServerWorld world, TempleRing.Ring ring) {
		BlockState portal = ModBlocks.TEMPLE_PORTAL.getDefaultState();

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.setBlockState(ring.centre().add(dx, 0, dz), portal, Block.NOTIFY_LISTENERS);
			}
		}

		// Global, like vanilla's: the noise a portal opening makes is heard by everybody in the world
		// and not only by the people who can see it.
		world.syncGlobalEvent(WorldEvents.END_PORTAL_OPENED, ring.centre(), 0);

		ChurchBell.toll(world, bellAbove(world, ring.centre()));
	}

	/**
	 * The temple's bell, which hangs on the centre line directly over the ring.
	 *
	 * <p>Being on the centre line is what makes this a straight search upward rather than a hunt: the
	 * structure is placed at one of four rotations and the bell is on the axis they all turn around,
	 * so it is over the middle of the ring whichever way the building came out.
	 *
	 * <p>Falls back to the ring itself, so a temple somebody has taken the bell out of still makes the
	 * sound - it is the sound the moment is built around, and the block is only where it comes from.
	 */
	private static BlockPos bellAbove(ServerWorld world, BlockPos centre) {
		for (int dy = 1; dy <= BELL_SEARCH_UP; dy++) {
			BlockPos candidate = centre.up(dy);
			if (world.getBlockState(candidate).isOf(Blocks.BELL)) {
				return candidate;
			}
		}
		return centre;
	}

	/**
	 * Opens the way off the disc.
	 *
	 * <p>Called when the blank one dies. There is no fight yet, so for now the only thing that calls
	 * it is {@code BlankCommand} - but this is the whole of what "killing it lets you leave" has to
	 * do, and the fight will not need to know anything about portals beyond calling this once.
	 *
	 * <p>Idempotent: opening a way out that is already open changes nothing.
	 */
	public static void openExit(ServerWorld blank) {
		BlockState portal = ModBlocks.TEMPLE_PORTAL.getDefaultState();

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				blank.setBlockState(TheBlank.EXIT.add(dx, 0, dz), portal, Block.NOTIFY_LISTENERS);
			}
		}

		blank.syncGlobalEvent(WorldEvents.END_PORTAL_OPENED, TheBlank.EXIT, 0);
	}
}
