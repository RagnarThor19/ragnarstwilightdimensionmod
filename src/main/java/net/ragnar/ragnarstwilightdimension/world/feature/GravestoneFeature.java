package net.ragnar.ragnarstwilightdimension.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.SilhouetteEntity;

import java.util.List;

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
 * <p>The same class builds all {@linkplain Kind kinds} of grave, because the ones that are not the
 * first one are only interesting for being identical to it.
 */
public class GravestoneFeature extends Feature<DefaultFeatureConfig> {
	/**
	 * Which of them this instance lays out.
	 *
	 * <p>They are the same three columns, built by the same code, off the same ground checks. What
	 * separates the first two is entirely what is missing from the second.
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
		OPENED,

		/**
		 * Yours.
		 *
		 * <p>Not rolled for by the terrain like the other two - it is dug where a player died, by
		 * {@link #placeBurial}, and it is the only grave in the dimension that is ever built while
		 * somebody is watching. Two differences from the ordinary one, and no others: the chest under
		 * the mound is a double chest, running the length of the grave so that the second half sits
		 * under the foot as well, and the sign is not blank.
		 */
		PLAYER
	}

	/**
	 * Everything that goes into a player's grave: whose it is, and what they were carrying when they
	 * stopped needing it.
	 *
	 * @param name        goes on the sign, which is the only writing in the dimension that names
	 *                    anybody
	 * @param belongings  filled into the double chest in the order it sat in their inventory
	 */
	public record Burial(Text name, List<ItemStack> belongings) {
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

	/**
	 * Which line of the sign a player's name goes on. The second, so the name sits across the middle
	 * of the board rather than along its top edge.
	 */
	private static final int NAME_LINE = 1;

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
	 * Lays out one grave where the terrain allows it.
	 *
	 * <p>Every check below is run for both kinds, including the ones that only matter for what an
	 * opened grave no longer has - the solid block under the mound, the headroom at the foot. An
	 * opened grave is supposed to look like an ordinary one that somebody reached first, so it has to
	 * be built where an ordinary one could have stood.
	 *
	 * <p>This is the fussy way in, and it is allowed to refuse: the world is large and a grave that
	 * does not fit here can be rolled for somewhere else. {@link #placeBurial} is the way in that
	 * cannot refuse.
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

		// The watcher needs two blocks of headroom, and the chest needs something to sit in - a grave
		// opening straight into a cave below is not worth building.
		if (!isFree(world, head.up()) || !isFree(world, mound.up())
				|| !isFree(world, foot.up()) || !isFree(world, foot.up(2))) {
			return false;
		}

		BlockPos chest = mound.down();
		if (!world.getBlockState(chest).isSolidBlock(world, chest)) {
			return false;
		}

		build(world, head, mound, foot, toGrave, kind, random, null);
		return true;
	}

	/**
	 * Buries a player where they died.
	 *
	 * <p>The difference from {@link #place} is not the shape of the thing, it is that this one is not
	 * allowed to say no. A grave that declines to generate costs nothing; a grave that declines to be
	 * dug drops everything the player owned on the floor instead, which is the exact outcome the
	 * feature exists to avoid. So rather than test the ground and give up on it, this levels three
	 * columns into the shape a grave needs - flooring what is hollow, clearing what is above - and
	 * builds into that.
	 *
	 * <p>It picks its own direction. {@code facing} is only where the player was looking when they
	 * died: if the ground that way is worse than the ground another way, the grave turns. Ties keep
	 * the player's own direction, so a death on flat ground leaves a grave running the way they were
	 * going.
	 *
	 * @param deathPos where the player was standing, which is only a starting point - see
	 *                 {@link #findFooting}
	 * @return whether the grave was built, which is false only if the position cannot hold one at all
	 */
	public static boolean placeBurial(ServerWorld world, BlockPos deathPos, Direction facing, Burial burial) {
		BlockPos anchor = findFooting(world, deathPos);
		if (anchor == null) {
			return false;
		}

		Direction toGrave = chooseDirection(world, anchor, facing);
		BlockPos mound = anchor.offset(toGrave);
		BlockPos foot = anchor.offset(toGrave, 2);

		// Everything that could have failed has failed by now: from here the blocks go in, and nothing
		// below this line can leave the player's belongings half-moved.
		for (BlockPos column : List.of(anchor, mound, foot)) {
			if (!world.getBlockState(column).isSolidBlock(world, column)) {
				world.setBlockState(column, Blocks.COARSE_DIRT.getDefaultState(), Block.NOTIFY_ALL);
			}

			world.setBlockState(column.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			world.setBlockState(column.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}

		build(world, anchor, mound, foot, toGrave, Kind.PLAYER, world.getRandom(), burial);
		return true;
	}

	/**
	 * Lays the blocks. Everything above this point decides <i>where</i>; this decides nothing at all,
	 * which is what keeps a player's grave and a generated one the same object in the world.
	 *
	 * @param burial null for the graves that were already here, which is also what leaves their signs
	 *               blank and their chests on the loot table
	 */
	private static void build(StructureWorldAccess world, BlockPos head, BlockPos mound, BlockPos foot,
							  Direction toGrave, Kind kind, Random random, Burial burial) {
		boolean opened = kind == Kind.OPENED;
		BlockPos sign = mound.up();
		BlockPos chest = mound.down();

		world.setBlockState(head.up(), Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
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

		// The headstone goes down first: with NOTIFY_ALL the sign checks its support immediately and
		// would pop off otherwise.
		writeSign(world, sign, toGrave, burial);

		if (opened) {
			// Whatever was in the ground here is not in the ground here any more. Cleared explicitly
			// rather than left alone, because the block being dug out of is solid by the check above.
			world.setBlockState(chest, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

			// And no watcher. There is nothing left to stand over.
			return;
		}

		if (burial != null) {
			fillGrave(world, chest, foot.down(), toGrave, burial);
		} else {
			world.setBlockState(chest, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, toGrave),
					Block.NOTIFY_ALL);
			if (world.getBlockEntity(chest) instanceof ChestBlockEntity chestEntity) {
				chestEntity.setLootTable(LOOT_TABLE, random.nextLong());
			}
		}

		spawnWatcher(world, foot.up(), sign);
	}

	/**
	 * Hangs the sign on the headstone's inward face, so it reads from the foot of the grave.
	 *
	 * <p>Waxed either way. On the generated graves that keeps them blank - an unwaxed sign opens the
	 * edit screen on the first right-click, and whatever gets typed is what the grave says from then
	 * on. On a player's grave it keeps the name that is on it from being written over, which matters
	 * rather more, because that one is evidence.
	 */
	private static void writeSign(StructureWorldAccess world, BlockPos sign, Direction toGrave, Burial burial) {
		world.setBlockState(sign,
				Blocks.OAK_WALL_SIGN.getDefaultState().with(Properties.HORIZONTAL_FACING, toGrave),
				Block.NOTIFY_ALL);

		if (!(world.getBlockEntity(sign) instanceof SignBlockEntity signEntity)) {
			return;
		}

		if (burial != null) {
			// A live world, so the ordinary API works and the players standing around are told about it.
			signEntity.setText(signEntity.getFrontText().withMessage(NAME_LINE, burial.name()), true);
			signEntity.setWaxed(true);
			return;
		}

		// Written through NBT rather than by calling setWaxed, which pushes the change to nearby
		// clients through the block entity's world. During world generation there are no clients and
		// the block entity has no world yet, so that call is a null pointer exception.
		NbtCompound nbt = signEntity.createNbt(world.getRegistryManager());
		nbt.putBoolean("is_waxed", true);
		signEntity.read(nbt, world.getRegistryManager());
	}

	/**
	 * Puts a player's belongings in the ground.
	 *
	 * <p>A double chest, laid along the grave rather than across it, so it is under the mound and the
	 * foot both - one chest could not hold a full inventory, and two separate ones would mean digging
	 * the grave up twice. The halves have to agree on a facing, and
	 * {@link ChestBlock#getFacing} puts the other half of a {@link ChestType#LEFT} chest on the
	 * clockwise side of the way it faces, so facing the pair <i>across</i> the grave is what makes
	 * them join up <i>along</i> it.
	 *
	 * @param near the half under the mound, at the head end
	 * @param far  the half under the foot, where the watcher is standing
	 */
	private static void fillGrave(StructureWorldAccess world, BlockPos near, BlockPos far, Direction toGrave,
								  Burial burial) {
		Direction facing = toGrave.rotateYCounterclockwise();

		world.setBlockState(near, Blocks.CHEST.getDefaultState()
				.with(ChestBlock.FACING, facing)
				.with(ChestBlock.CHEST_TYPE, ChestType.LEFT), Block.NOTIFY_ALL);
		world.setBlockState(far, Blocks.CHEST.getDefaultState()
				.with(ChestBlock.FACING, facing)
				.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT), Block.NOTIFY_ALL);

		List<ItemStack> belongings = burial.belongings();
		int next = 0;

		for (BlockPos half : List.of(near, far)) {
			if (!(world.getBlockEntity(half) instanceof ChestBlockEntity chestEntity)) {
				continue;
			}

			// In inventory order, filled from the left, the way the abandoned building's chest is - what
			// is in here was packed by somebody rather than spilled.
			for (int slot = 0; slot < chestEntity.size() && next < belongings.size(); slot++) {
				chestEntity.setStack(slot, belongings.get(next++));
			}

			chestEntity.markDirty();
		}

		// Fifty-four slots against forty-one carried, so nothing should ever be left over. If another
		// mod has widened the inventory it still cannot be dropped on the floor - that is the one
		// outcome this whole path exists to prevent - so it goes on top of the grave instead.
		for (; next < belongings.size(); next++) {
			world.spawnEntity(new ItemEntity(world.toServerWorld(),
					near.getX() + 0.5, near.getY() + 2.0, near.getZ() + 0.5, belongings.get(next)));
		}
	}

	/**
	 * Stands one on the far patch of dirt - the one at the foot - looking back at the sign.
	 *
	 * <p>On a generated grave that patch is deliberately the one with nothing under it: the shape of
	 * the thing is that it is staring down the length of the grave at the writing, not standing on top
	 * of what is buried. A player's grave is long enough that it ends up over the far half of the
	 * chest anyway, which is the one place the two differ and is not worth moving it for.
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
	 * Finds the block a player's grave should stand on, starting from where they died.
	 *
	 * <p>Falls through air, grass and fluid all the way to the first thing that would have stopped
	 * them, so a death in mid-air is buried on whatever is underneath it rather than left standing in
	 * the sky. That is the same place their belongings would have landed if they had been dropped, and
	 * it is where the player will go looking - down, from the last thing they remember.
	 *
	 * <p>Read off the blocks rather than off the heightmap, which reports the top of the terrain and
	 * so would put a grave on the roof of the cave somebody died in.
	 *
	 * @return null only if this world has no room for a grave at all
	 */
	private static BlockPos findFooting(ServerWorld world, BlockPos deathPos) {
		// The chest is one below the grave and the watcher stands two above it, and both have to be
		// inside the world.
		int lowest = world.getBottomY() + 1;
		int highest = world.getTopY() - 3;

		if (lowest > highest) {
			return null;
		}

		int from = MathHelper.clamp(deathPos.getY(), lowest, highest);
		BlockPos.Mutable pos = new BlockPos.Mutable(deathPos.getX(), from, deathPos.getZ());

		for (int y = from; y >= lowest; y--) {
			pos.setY(y);

			if (isFooting(world, pos)) {
				return pos.toImmutable();
			}
		}

		// A column with nothing in it at all. The grave gets floored in at the bottom of the world,
		// which is the one place left that is still inside it.
		return new BlockPos(deathPos.getX(), lowest, deathPos.getZ());
	}

	/**
	 * Picks which way a player's grave runs.
	 *
	 * <p>Scores the two columns the grave would take up, ground counting for twice what headroom does:
	 * a grave that has to floor itself over a drop is more obviously wrong than one that has to knock
	 * a block down. Only a strictly better direction wins, so the player's own is the default and the
	 * search is just there to turn the grave off the edge of a cliff.
	 */
	private static Direction chooseDirection(ServerWorld world, BlockPos anchor, Direction facing) {
		Direction best = facing;
		int bestScore = rate(world, anchor, facing);

		for (Direction candidate : Direction.Type.HORIZONTAL) {
			int score = rate(world, anchor, candidate);

			if (score > bestScore) {
				best = candidate;
				bestScore = score;
			}
		}

		return best;
	}

	private static int rate(ServerWorld world, BlockPos anchor, Direction toGrave) {
		int score = 0;

		for (int step = 1; step <= 2; step++) {
			BlockPos column = anchor.offset(toGrave, step);

			if (isFooting(world, column)) {
				score += 2;
			}
			if (isFree(world, column.up()) && isFree(world, column.up(2))) {
				score += 1;
			}
		}

		return score;
	}

	/**
	 * Finds the top solid block of a column, or null if there is no ground worth building a grave in.
	 *
	 * <p>Grass and other replaceable growth is looked straight through - this runs after the
	 * vegetation pass, so the topmost block in a column is very often a tuft of grass rather than the
	 * terrain itself.
	 *
	 * <p>Package-private rather than private so {@link PillarFeature} can stand its column on exactly
	 * the same ground the grave beside it is dug into, off the same check.
	 */
	static BlockPos findGround(StructureWorldAccess world, BlockPos column) {
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

	/** Whether this block is something a grave could be dug into, as opposed to fallen through. */
	private static boolean isFooting(StructureWorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return !state.isAir() && !state.isReplaceable() && state.getFluidState().isEmpty();
	}

	/** Whether this block can be built through. Shared with {@link PillarFeature}, which needs a clear column. */
	static boolean isFree(StructureWorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty();
	}
}
