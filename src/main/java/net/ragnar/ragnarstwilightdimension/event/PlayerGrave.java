package net.ragnar.ragnarstwilightdimension.event;

import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.ragnar.ragnarstwilightdimension.portal.BlankReturns;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.feature.GravestoneFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Nothing is dropped in the twilight. It is buried.
 *
 * <p>A player who dies here does not leave a pile of their own things scattered across the floor for
 * five minutes and then nothing. They get the same grave as everybody else who died here: cobblestone
 * headstone, sign, mound of coarse dirt, a chest in the ground under it, and a figure standing at the
 * foot of it looking at the writing. The only two things that mark it out as theirs are that the
 * chest is a double one, because a player carries more than the dead did, and that the sign has a
 * name on it.
 *
 * <p>That name is the single exception to there being no people here. Everything else in the
 * dimension is anonymous on purpose - the blank signs, the unsigned book, the Steves. The one grave
 * that is allowed to say who is in it is the one the player already knows the answer to, which gives
 * away nothing and puts them in the same row as the rest of them.
 *
 * <p>Two consequences worth stating plainly, because they are the point rather than side effects:
 * the loot does not despawn, and it does not come back to you. It stays exactly where you died, in a
 * dimension with no map and eleven blocks of visibility, and getting it back means finding the place
 * again. See {@link TwilightRespawn} for the other half of that.
 *
 * <p>Driven from {@code PlayerGraveMixin}, which cancels the vanilla scatter when - and only when -
 * this reports that it has taken the inventory instead.
 */
public final class PlayerGrave {
	private PlayerGrave() {
	}

	/**
	 * Buries a player who has just died, if they died somewhere that buries people.
	 *
	 * <p>The order matters. The stacks go into the grave first and the inventory is emptied second,
	 * because the chest is holding the same {@link ItemStack} objects the player was carrying - if
	 * this returned before clearing them, every death in the twilight would double the player's
	 * belongings. And if the grave cannot be dug, nothing has been touched at all and vanilla's drop
	 * is left to run exactly as it would have.
	 *
	 * @return whether the belongings are now in the ground, meaning the caller must not drop them
	 */
	public static boolean bury(ServerPlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return false;
		}

		// Where the grave goes, which is not always where they died - see graveSiteFor.
		GraveSite site = graveSiteFor(player, world);
		if (site == null) {
			return false;
		}

		// With the gamerule on, the player keeps everything and there is nothing to bury. A grave with
		// an empty chest under it would be a lie about what happened here.
		if (world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			return false;
		}

		PlayerInventory inventory = player.getInventory();
		List<ItemStack> belongings = new ArrayList<>();

		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);

			// Curse of vanishing is left to mean what it means: those are destroyed rather than buried,
			// which is what vanilla's own vanishCursedItems does one line before the drop this replaces.
			if (stack.isEmpty()
					|| EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}

			belongings.add(stack);
		}

		GravestoneFeature.Burial burial = new GravestoneFeature.Burial(
				Text.literal(player.getGameProfile().getName()), belongings);

		if (!GravestoneFeature.placeBurial(site.world(), site.pos(),
				Direction.fromRotation(player.getYaw()), burial)) {
			return false;
		}

		inventory.clear();
		return true;
	}

	/** A world and a block in it to dig the grave at. */
	private record GraveSite(ServerWorld world, BlockPos pos) {
	}

	/**
	 * Where this death buries, or null if it does not bury at all.
	 *
	 * <p>The twilight buries you where you fell, which is the whole of the idea: the loot stays in the
	 * place that killed you.
	 *
	 * <p>The disc cannot. It is sealed - the way in costs twelve eyes and the way out only opens when
	 * the blank one is dead - so a grave left standing on it is not a hard walk back, it is a second
	 * fight for the right to try. Burying at the temple the player came in through keeps what losing
	 * costs proportionate to the trip: the eyes and the walk, not the inventory.
	 *
	 * <p>Anywhere else is vanilla's, and scatters.
	 */
	private static GraveSite graveSiteFor(ServerPlayerEntity player, ServerWorld world) {
		if (ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return new GraveSite(world, player.getBlockPos());
		}

		if (!ModDimensions.BLANK_WORLD.equals(world.getRegistryKey())) {
			return null;
		}

		ServerWorld twilight = player.getServer().getWorld(ModDimensions.TWILIGHT_WORLD);
		BlockPos ring = BlankReturns.get(player.getServer()).returnPoint(player.getUuid());

		// Somebody who reached the disc without going through a portal - an operator, a fresh world -
		// has no temple to be sent back to, and gets vanilla's scatter rather than a grave dropped at
		// an arbitrary place.
		if (twilight == null || ring == null) {
			return null;
		}

		BlockPos outside = besideTemple(twilight, ring);
		return outside != null ? new GraveSite(twilight, outside) : null;
	}

	/**
	 * How far out from the ring the grave goes.
	 *
	 * <p>The building is thirteen across with the ring in the middle, so its outer wall is six blocks
	 * from the ring. A grave is three blocks long and { placeBurial} picks which way it runs
	 * itself, so it can reach two blocks back toward the temple from wherever it is anchored - ten out
	 * leaves the nearest it can possibly get at eight, two clear of the wall, whichever way it turns.
	 * At eight it could just reach the wall and knock a hole in it.
	 */
	private static final int CLEAR_OF_TEMPLE = 10;

	/** How far below the ring to look for ground. The ring stands on a plinth a few blocks up. */
	private static final int GROUND_SEARCH_DOWN = 12;

	/**
	 * Open ground outside the temple, or null if there is none.
	 *
	 * <p>What is remembered is the portal block itself, and that is the one place in the building the
	 * grave must not go. It sits on a plinth in the middle of a stone room, and digging a grave there
	 * would take the portal out and leave the chest standing where the pool was - so the way back in
	 * would be gone and the way to the loot would be through twelve more eyes.
	 *
	 * <p>So the grave goes outside the wall instead, on whatever the temple is standing on. All four
	 * sides are tried, because a temple on a slope has a good one and a bad one; the first with ground
	 * and room above it wins. If every side is buried or hanging over a drop this gives up, and the
	 * death scatters the ordinary way rather than sealing somebody's belongings inside a hill.
	 *
	 * @param ring the remembered portal block, which is within one block of the middle of the ring -
	 *             near enough, against an eight block offset, that snapping it exactly is not worth
	 *             the search it would cost
	 */
	private static BlockPos besideTemple(ServerWorld twilight, BlockPos ring) {
		for (Direction side : Direction.Type.HORIZONTAL) {
			BlockPos.Mutable cursor = ring.offset(side, CLEAR_OF_TEMPLE).mutableCopy();

			for (int drop = 0; drop <= GROUND_SEARCH_DOWN; drop++) {
				cursor.setY(ring.getY() - drop);

				boolean footing = twilight.getBlockState(cursor).isSolidBlock(twilight, cursor);
				boolean room = twilight.getBlockState(cursor.up()).isAir()
						&& twilight.getBlockState(cursor.up(2)).isAir();

				if (footing && room) {
					// placeBurial wants where the player would be standing, not the block underfoot.
					return cursor.up().toImmutable();
				}
			}
		}

		return null;
	}
}
