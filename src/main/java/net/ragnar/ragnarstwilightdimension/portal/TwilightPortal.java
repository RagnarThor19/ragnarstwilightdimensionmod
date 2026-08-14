package net.ragnar.ragnarstwilightdimension.portal;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Turns the lone end portal frame in a ruin into a two-way gateway.
 *
 * <p>Right-clicking an eyeless frame with an eye of ender destroys the eye and moves you across.
 * The frame is deliberately <em>not</em> filled, so a ruin can be used again and again as long as
 * you bring another eye.
 *
 * <p>The twilight is compressed: its dimension type sets a coordinate scale of 8, so one block
 * walked there is eight overworld blocks of ground covered. Crossing therefore divides or
 * multiplies your X/Z rather than keeping it, exactly the way the Nether does.
 *
 * <p>That compression means the two structure sets can no longer be laid out so their ruins line
 * up - a twilight ruin and an overworld ruin at matching coordinates would have to be sixty-four
 * times denser on one side. Instead, pairs are made on demand: arriving somewhere with no ruin
 * places one, and because flooring the division loses at most seven blocks - comfortably inside
 * {@link #ARRIVAL_SEARCH_XZ} - the return trip always finds the ruin you originally left from. Use
 * a gateway once and it has a permanent twin.
 */
public final class TwilightPortal {
	/** Half-width of the check that keeps real End portals (stronghold rings) working normally. */
	private static final int RING_CHECK_RADIUS = 2;

	/**
	 * How far around the arrival column to look for an existing ruin frame. Must stay above 7, the
	 * largest drift that flooring the scaled coordinate can introduce, or round trips would stop
	 * finding the ruin they set out from and would litter a fresh one every crossing.
	 */
	private static final int ARRIVAL_SEARCH_XZ = 8;
	private static final int ARRIVAL_SEARCH_Y = 8;

	/**
	 * A ruin template and where inside it the portal frame sits, relative to the template origin.
	 * The twilight's ruin is a much smaller plinth than the overworld's, so the frame sits nearer
	 * its corner.
	 */
	private record Ruin(Identifier template, int frameOffset) {
	}

	private static final Ruin OVERWORLD_RUIN =
			new Ruin(Identifier.of(RagnarsTwilightDimension.MOD_ID, "ruin"), 5);

	private static final Ruin TWILIGHT_RUIN =
			new Ruin(Identifier.of(RagnarsTwilightDimension.MOD_ID, "twilight_ruin"), 2);

	private TwilightPortal() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(TwilightPortal::onUseBlock);
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
		// A frame with neighbouring frames belongs to a real End portal - leave it to vanilla.
		if (isPartOfEndPortalRing(world, framePos)) {
			return ActionResult.PASS;
		}

		RegistryKey<World> destinationKey = destinationFor(world.getRegistryKey());
		if (destinationKey == null) {
			return ActionResult.PASS;
		}

		// Let the client run its normal (harmless) prediction so the interaction still reaches us.
		if (world.isClient) {
			return ActionResult.PASS;
		}

		ServerWorld origin = (ServerWorld) world;
		ServerWorld destination = origin.getServer().getWorld(destinationKey);
		if (destination == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}

		player.getStackInHand(hand).decrementUnlessCreative(1, player);
		PortalEffects.departure(origin, framePos);

		BlockPos arrivalColumn = scaleAcross(origin, destination, framePos);
		BlockPos arrivalFrame = findOrCreateRuinFrame(destination, arrivalColumn.getX(), arrivalColumn.getZ(),
				ruinFor(destinationKey));
		Vec3d standing = standingSpotBeside(destination, arrivalFrame);

		serverPlayer.teleport(destination, standing.x, standing.y, standing.z, 180.0F, 0.0F);
		PortalEffects.arrival(destination, standing);

		return ActionResult.SUCCESS;
	}

	/**
	 * Converts a position in one world to the matching column in the other, using the ratio between
	 * the two dimension types' coordinate scales, and pulls it inside the destination's world border.
	 *
	 * <p>Reading the ratio from the dimension types rather than hard-coding 8 keeps the JSON the one
	 * place the compression is decided. Nothing in vanilla applies it for us here: the coordinate
	 * scale is only consulted by vanilla's own portal travel, and world borders are copied between
	 * dimensions at the same size rather than divided down, so the clamp is a genuine bound and not
	 * a formality.
	 */
	private static BlockPos scaleAcross(ServerWorld origin, ServerWorld destination, BlockPos pos) {
		double scale = DimensionType.getCoordinateScaleFactor(origin.getDimension(), destination.getDimension());
		return destination.getWorldBorder().clamp(pos.getX() * scale, pos.getY(), pos.getZ() * scale);
	}

	private static Ruin ruinFor(RegistryKey<World> world) {
		return ModDimensions.TWILIGHT_WORLD.equals(world) ? TWILIGHT_RUIN : OVERWORLD_RUIN;
	}

	private static RegistryKey<World> destinationFor(RegistryKey<World> current) {
		if (ModDimensions.TWILIGHT_WORLD.equals(current)) {
			return World.OVERWORLD;
		}
		if (World.OVERWORLD.equals(current)) {
			return ModDimensions.TWILIGHT_WORLD;
		}
		return null;
	}

	private static boolean isPartOfEndPortalRing(World world, BlockPos pos) {
		for (int dx = -RING_CHECK_RADIUS; dx <= RING_CHECK_RADIUS; dx++) {
			for (int dz = -RING_CHECK_RADIUS; dz <= RING_CHECK_RADIUS; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				if (world.getBlockState(pos.add(dx, 0, dz)).isOf(Blocks.END_PORTAL_FRAME)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Finds the mirrored ruin's frame near the given column, generating the chunk if needed, and
	 * places a fresh ruin when none is found.
	 */
	private static BlockPos findOrCreateRuinFrame(ServerWorld world, int x, int z, Ruin ruin) {
		world.getChunk(x >> 4, z >> 4);   // force-generate before we go looking

		int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int dy = -ARRIVAL_SEARCH_Y; dy <= ARRIVAL_SEARCH_Y; dy++) {
			for (int dx = -ARRIVAL_SEARCH_XZ; dx <= ARRIVAL_SEARCH_XZ; dx++) {
				for (int dz = -ARRIVAL_SEARCH_XZ; dz <= ARRIVAL_SEARCH_XZ; dz++) {
					cursor.set(x + dx, surface + dy, z + dz);
					if (world.getBlockState(cursor).isOf(Blocks.END_PORTAL_FRAME)) {
						return cursor.toImmutable();
					}
				}
			}
		}

		return placeRuin(world, x, z, surface, ruin);
	}

	private static BlockPos placeRuin(ServerWorld world, int x, int z, int surface, Ruin ruin) {
		int floorY = surface - 1;   // the template's floor layer replaces the top terrain block
		BlockPos frame = new BlockPos(x, floorY + 1, z);

		StructureTemplate template = world.getStructureTemplateManager()
				.getTemplate(ruin.template())
				.orElse(null);

		if (template == null) {
			RagnarsTwilightDimension.LOGGER.warn(
					"Ruin template {} is missing; placing a bare portal frame at {}", ruin.template(), frame);
			world.setBlockState(frame, Blocks.END_PORTAL_FRAME.getDefaultState(), Block.NOTIFY_LISTENERS);
			return frame;
		}

		BlockPos origin = new BlockPos(x - ruin.frameOffset(), floorY, z - ruin.frameOffset());
		template.place(world, origin, origin, new StructurePlacementData().setIgnoreEntities(true),
				world.getRandom(), Block.NOTIFY_LISTENERS);
		return frame;
	}

	/** A spot two blocks south of the frame, nudged up if something is in the way. */
	private static Vec3d standingSpotBeside(ServerWorld world, BlockPos frame) {
		BlockPos spot = frame.south(2);

		for (int i = 0; i < 8 && !isStandable(world, spot); i++) {
			spot = spot.up();
		}
		return new Vec3d(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
	}

	private static boolean isStandable(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
				&& world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty();
	}
}
