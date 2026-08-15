package net.ragnar.ragnarstwilightdimension.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.LootableInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.ragnarstwilightdimension.loot.ModLootTables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lays the abandoned building's chest out in a line instead of scattering it.
 *
 * <p>A loot table decides <i>what</i> is in a chest and has no say at all in <i>where</i> it lands:
 * {@code LootTable.supplyInventory} collects the free slots, shuffles them, and deals the stacks out
 * at random. Every generated chest in the game looks the same way because of it - contents strewn
 * across three rows with gaps everywhere, which reads as spillage.
 *
 * <p>That is wrong for this one building. Somebody lived here, and what is left is what they packed:
 * it should sit in one tidy row, filled from the left, in the order it was put in. The loot table
 * yields at most nine stacks for exactly this reason - a chest's top row is nine slots, so a full
 * roll is one clean line and nothing wraps. <b>Adding a pool to that table without taking a roll off
 * another one breaks this</b>, and the tenth stack lands alone on the second row.
 *
 * <p>This is the whole reason the mod knows the loot table by key. The check is on identity, so every
 * other container in the game - villages, dungeons, the gravestones - keeps vanilla's scatter.
 */
@Mixin(LootableInventory.class)
public interface LootableInventoryMixin {
	/**
	 * Replaces the generation step for our chest only.
	 *
	 * <p>Vanilla's version is reimplemented here rather than post-processed, because by the time it
	 * returns it has already cleared the loot table key and there is no longer anything to identify
	 * the chest by. The steps below are its steps, in its order, with the final placement changed.
	 */
	@Inject(method = "generateLoot", at = @At("HEAD"), cancellable = true)
	private void twilight$fillInOrder(PlayerEntity player, CallbackInfo ci) {
		LootableInventory self = (LootableInventory) (Object) this;

		RegistryKey<LootTable> key = self.getLootTable();
		if (!ModLootTables.ABANDONED_BUILDING.equals(key)) {
			return;
		}

		World world = self.getWorld();
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		ci.cancel();

		if (player instanceof ServerPlayerEntity serverPlayer) {
			Criteria.PLAYER_GENERATES_CONTAINER_LOOT.trigger(serverPlayer, key);
		}

		// Cleared before a single stack goes in, which is what vanilla does and is not optional: every
		// read of a lootable container calls back into this method first, so a path that leaves the key
		// set regenerates the contents on the next access and the chest becomes infinite.
		self.setLootTable(null);

		LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(serverWorld)
				.add(LootContextParameters.ORIGIN, Vec3d.ofCenter(self.getPos()));

		if (player != null) {
			builder.luck(player.getLuck()).add(LootContextParameters.THIS_ENTITY, player);
		}

		LootTable table = serverWorld.getServer().getReloadableRegistries().getLootTable(key);
		ObjectArrayList<ItemStack> loot =
				table.generateLoot(builder.build(LootContextTypes.CHEST), self.getLootTableSeed());

		// The only real change. Pools generate in the order they are declared in the json, so the row
		// reads left to right the way the table is written - larder, tools, timber, garden, trophies,
		// then the eye they never spent, and last of all, a third of the time, the question.
		int slot = 0;
		for (ItemStack stack : loot) {
			if (slot >= self.size()) {
				break;
			}
			if (!stack.isEmpty()) {
				self.setStack(slot++, stack);
			}
		}

		self.markDirty();
	}
}
