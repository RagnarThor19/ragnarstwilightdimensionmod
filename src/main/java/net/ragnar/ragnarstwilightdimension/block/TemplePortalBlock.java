package net.ragnar.ragnarstwilightdimension.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Portal;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.ragnar.ragnarstwilightdimension.portal.BlankReturns;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

/**
 * The pool that opens in the temple ring, and the one that opens on the disc when the fight is over.
 *
 * <p>Deliberately not vanilla's end portal. That one is hard-wired to the End in both directions and
 * carries the credits with it, and lighting a temple would otherwise be a very elaborate way of
 * arriving somewhere the player has already been. This is the same shape and the same shallow pool
 * six pixels off the floor, pointed somewhere else and drawn in a different colour.
 *
 * <h2>Where it goes</h2>
 *
 * <p>One block, two directions, decided by the world it is standing in rather than by anything
 * stored on it - so a portal that is somehow orphaned still behaves sensibly, and neither end has to
 * be told about the other when it is placed.
 *
 * <p>The way in remembers where it was entered from, in {@link BlankReturns}, because the way out
 * has to land the player back at their own temple rather than at some default. The disc is one
 * fixed circle shared by everybody, so without that the second player through would come back out
 * of the first player's building.
 */
public class TemplePortalBlock extends BlockWithEntity implements Portal {
	public static final MapCodec<TemplePortalBlock> CODEC = createCodec(TemplePortalBlock::new);

	/** The same shallow pool as the end portal: a slab of nothing you fall through rather than a wall. */
	protected static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);

	public TemplePortalBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<TemplePortalBlock> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new TemplePortalBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/**
	 * Anything that can use a portal and is actually inside the pool goes through.
	 *
	 * <p>The shape test is vanilla's, and it is why you have to be in the water rather than standing
	 * on the rim: the block is six pixels shorter than a full cube on top and bottom both, so walking
	 * across the frame does not trigger it.
	 */
	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (entity.canUsePortals(false)
				&& VoxelShapes.matchesAnywhere(
						VoxelShapes.cuboid(entity.getBoundingBox().offset(-pos.getX(), -pos.getY(), -pos.getZ())),
						state.getOutlineShape(world, pos), BooleanBiFunction.AND)) {
			entity.tryUsePortal(this, pos);
		}
	}

	@Override
	public TeleportTarget createTeleportTarget(ServerWorld world, Entity entity, BlockPos pos) {
		boolean leaving = ModDimensions.BLANK_WORLD.equals(world.getRegistryKey());
		RegistryKey<World> destinationKey = leaving ? ModDimensions.TWILIGHT_WORLD : ModDimensions.BLANK_WORLD;

		ServerWorld destination = world.getServer().getWorld(destinationKey);
		if (destination == null) {
			return null;
		}

		Vec3d landing;
		float yaw;

		if (leaving) {
			// Straight back onto the portal block they left from, which lands them standing in the pool.
			// That does not bounce them straight out again: an entity that arrives in a portal is on a
			// portal cooldown, and Entity.tryUsePortal tops that cooldown back up every tick it is still
			// standing in one rather than counting it down. The trip only fires again once they have
			// stepped out and back in, which is how a nether portal behaves and what people expect.
			//
			// Back to the ring they lit. Nobody has a remembered temple the first time they are seen on
			// the disc - a fresh world, an operator who teleported in - so the dimension's own spawn is
			// the fallback rather than a refusal to let them out.
			BlockPos temple = BlankReturns.get(world.getServer()).returnPoint(entity.getUuid());
			BlockPos target = temple != null ? temple : destination.getSpawnPos();
			landing = target.toBottomCenterPos();
			yaw = entity.getYaw();
		} else {
			BlankReturns.get(world.getServer()).remember(entity.getUuid(), pos);
			landing = TheBlank.ARRIVAL;
			yaw = TheBlank.ARRIVAL_YAW;
		}

		return new TeleportTarget(destination, landing, Vec3d.ZERO, yaw, entity.getPitch(),
				TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET));
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		double x = pos.getX() + random.nextDouble();
		double y = pos.getY() + 0.8;
		double z = pos.getZ() + random.nextDouble();
		world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
	}

	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		return ItemStack.EMPTY;
	}

	@Override
	protected boolean canBucketPlace(BlockState state, Fluid fluid) {
		return false;
	}
}
