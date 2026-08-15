package net.ragnar.ragnarstwilightdimension.event;

import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
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
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
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

		if (!GravestoneFeature.placeBurial(world, player.getBlockPos(),
				Direction.fromRotation(player.getYaw()), burial)) {
			return false;
		}

		inventory.clear();
		return true;
	}
}
