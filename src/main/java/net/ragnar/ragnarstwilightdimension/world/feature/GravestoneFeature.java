package net.ragnar.ragnarstwilightdimension.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.SilhouetteEntity;

/**
 * Someone was buried here.
 *
 * <p>Three blocks laid out in a line: a cobblestone headstone with a blank sign on its face, then
 * the mound - two coarse dirt, with a chest buried under the one nearest the headstone. Standing at
 * the far end, on the second patch of dirt, is a figure looking down at the sign.
 *
 * <p>Written as a feature rather than a jigsaw structure because of that figure. A template would
 * cover the blocks, but the watcher has to be given the exact position of the sign it is looking at
 * so it is aimed at the writing rather than merely pointed the right way, and doing that from a
 * template means a marker block and a second pass. Here it is one method.
 *
 * <p>The same class builds both {@linkplain Kind kinds} of grave, because the second one is only
 * interesting for being identical to the first.
 */
public class GravestoneFeature extends Feature<DefaultFeatureConfig> {
	/**
	 * Which of the two this instance lays out.
	 *
	 * <p>They are the same three columns, built by the same code, off the same ground checks. What
	 * separates them is entirely what is missing from the second.
	 */
	public enum Kind {
		/** The ordinary one: mound intact, chest under it, someone standing at the foot. */
		BURIED,

		/**
		 * One that has already been dug out.
		 *
		 * <p>The headstone and its blank sign are still there, and so is the coarse dirt at the foot.
		 * What is gone is the mound over the chest and the chest itself, leaving the hole open two
		 * blocks deep - and whoever was standing at the end of it.
		 *
		 * <p>Nothing states who opened it or when. The only fact on offer is that somebody got here
		 * first, and that this is the one grave in the dimension with nothing left in it.
		 */
		OPENED
	}

	/**
	 * A thin wrapper that rolls {@code minecraft:chests/stronghold_library} itself rather than
	 * reproducing its contents, so anything another mod injects into the stronghold library turns up
	 * here too. On top of that it adds the odd eye of ender and filters the eye armour trim back out.
	 */
	public static final RegistryKey<LootTable> LOOT_TABLE = RegistryKey.of(RegistryKeys.LOOT_TABLE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "chests/gravestone"));

	/** How far down {@link #findGround} will look for the surface before giving up on a column. */
	private static final int GROUND_SEARCH_DEPTH = 4;

	private final Kind kind;

	public GravestoneFeature(Codec<DefaultFeatureConfig> codec, Kind kind) {
		super(codec);
		this.kind = kind;
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		Random random = context.getRandom();
		return place(context.getWorld(), context.getOrigin(),
				Direction.fromHorizontal(random.nextInt(4)), random, this.kind);
	}

	/** Lays out an ordinary grave. */
	public static boolean place(StructureWorldAccess world, BlockPos origin, Direction toGrave, Random random) {
		return place(world, origin, toGrave, random, Kind.BURIED);
	}

	/**
	 * Lays out one grave.
	 *
	 * <p>Every check below is run for both kinds, including the ones that only matter for what an
	 * opened grave no longer has - the solid block under the mound, the headroom at the foot. An
	 * opened grave is supposed to look like an ordinary one that somebody reached first, so it has to
	 * be built where an ordinary one could have stood.
	 *
	 * @param origin  the first free block above the terrain, at the headstone's column
	 * @param toGrave the direction the grave runs in, leading away from the headstone
	 * @param kind    whether this one still has anything in it
	 * @return whether the ground was flat and clear enough to build on
	 */
	public static boolean place(StructureWorldAccess world, BlockPos origin, Direction toGrave, Random random,
								Kind kind) {
		BlockPos head = findGround(world, origin);
		BlockPos mound = findGround(world, origin.offset(toGrave));
		BlockPos foot = findGround(world, origin.offset(toGrave, 2));

		if (head == null || mound == null || foot == null) {
			return false;
		}

		// Level ground only. On a slope the headstone ends up buried at one end or floating at the
		// other, and the watcher would be left standing in mid-air.
		if (head.getY() != mound.getY() || head.getY() != foot.getY()) {
			return false;
		}

		BlockPos headstone = head.up();
		BlockPos sign = mound.up();
		BlockPos chest = mound.down();

		// The watcher needs two blocks of headroom, and the chest needs something to sit in - a grave
		// opening straight into a cave below is not worth building.
		if (!isFree(world, headstone) || !isFree(world, sign) || !isFree(world, foot.up()) || !isFree(world, foot.up(2))) {
			return false;
		}
		if (!world.getBlockState(chest).isSolidBlock(world, chest)) {
			return false;
		}

		boolean opened = kind == Kind.OPENED;

		world.setBlockState(headstone, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(foot, Blocks.COARSE_DIRT.getDefaultState(), Block.NOTIFY_ALL);

		// The mound is the tell. Intact, it is the patch of turned earth over the chest; opened, it has
		// been taken off and the hole below it is left standing empty.
		world.setBlockState(mound,
				opened ? Blocks.AIR.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState(),
				Block.NOTIFY_ALL);

		// Anything the vegetation pass left standing on the mound comes off, so there is a clear line
		// of sight from the watcher to the sign.
		world.setBlockState(foot.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(foot.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

		// A wall sign hung on the headstone's inward face, so it reads from the foot of the grave.
		// The headstone goes down first: with NOTIFY_ALL the sign checks its support immediately and
		// would pop off otherwise.
		world.setBlockState(sign,
				Blocks.OAK_WALL_SIGN.getDefaultState().with(Properties.HORIZONTAL_FACING, toGrave),
				Block.NOTIFY_ALL);
		if (world.getBlockEntity(sign) instanceof SignBlockEntity signEntity) {
			// Blank is the default. Waxing it keeps it that way - an unwaxed sign opens the edit screen
			// on the first right-click, and whatever gets typed is what the grave says from then on.
			//
			// Written through NBT rather than by calling setWaxed, which pushes the change to nearby
			// clients through the block entity's world. During world generation there are no clients and
			// the block entity has no world yet, so that call is a null pointer exception.
			NbtCompound nbt = signEntity.createNbt(world.getRegistryManager());
			nbt.putBoolean("is_waxed", true);
			signEntity.read(nbt, world.getRegistryManager());
		}

		if (opened) {
			// Whatever was in the ground here is not in the ground here any more. Cleared explicitly
			// rather than left alone, because the block being dug out of is solid by the check above.
			world.setBlockState(chest, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

			// And no watcher. There is nothing left to stand over.
			return true;
		}

		world.setBlockState(chest, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, toGrave), Block.NOTIFY_ALL);
		if (world.getBlockEntity(chest) instanceof ChestBlockEntity chestEntity) {
			chestEntity.setLootTable(LOOT_TABLE, random.nextLong());
		}

		spawnWatcher(world, foot.up(), sign);
		return true;
	}

	/**
	 * Stands one on the far patch of dirt - the one with nothing under it - looking back at the sign.
	 *
	 * <p>Deliberately not the patch over the chest: the whole shape of the thing is that it is staring
	 * down the length of the grave at the writing, not standing on top of what is buried.
	 */
	private static void spawnWatcher(StructureWorldAccess world, BlockPos feet, BlockPos sign) {
		SilhouetteEntity watcher = ModEntities.SILHOUETTE.create(world.toServerWorld());
		if (watcher == null) {
			return;
		}

		watcher.refreshPositionAndAngles(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
		watcher.watchGrave(sign);
		world.spawnEntity(watcher);
	}

	/**
	 * Finds the top solid block of a column, or null if there is no ground worth building a grave in.
	 *
	 * <p>Grass and other replaceable growth is looked straight through - this runs after the
	 * vegetation pass, so the topmost block in a column is very often a tuft of grass rather than the
	 * terrain itself.
	 */
	private static BlockPos findGround(StructureWorldAccess world, BlockPos column) {
		BlockPos.Mutable pos = new BlockPos.Mutable(column.getX(), column.getY() + 1, column.getZ());

		for (int i = 0; i < GROUND_SEARCH_DEPTH; i++) {
			BlockState state = world.getBlockState(pos);

			if (!state.getFluidState().isEmpty()) {
				return null;
			}
			if (!state.isAir() && !state.isReplaceable()) {
				// Stone, sand or a tree trunk all mean this is not a patch of ground to dig a grave in.
				return state.isIn(BlockTags.DIRT) ? pos.toImmutable() : null;
			}

			pos.move(Direction.DOWN);
		}

		return null;
	}

	private static boolean isFree(StructureWorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty();
	}
}
