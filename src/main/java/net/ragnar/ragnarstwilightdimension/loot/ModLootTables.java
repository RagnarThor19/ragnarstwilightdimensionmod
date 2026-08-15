package net.ragnar.ragnarstwilightdimension.loot;

import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * Keys for the mod's own loot tables.
 *
 * <p>The tables themselves are data, in {@code data/ragnarstwilightdimension/loot_table/}. These keys
 * exist because code has to be able to recognise a table by identity - see
 * {@code LootableInventoryMixin}, which lays one of them out differently from every other chest in
 * the game and needs to know which one it is looking at.
 */
public final class ModLootTables {
	/**
	 * The chest in the abandoned building. Household goods, iron at the very best, and one eye.
	 *
	 * <p>Kept in sync by hand with the {@code LootTable} string inside {@code abandoned_building.nbt} -
	 * the structure names the table, and nothing cross-checks the two. If the chest ever generates
	 * empty, that is the first thing to look at.
	 */
	public static final RegistryKey<LootTable> ABANDONED_BUILDING = of("chests/abandoned_building");

	private ModLootTables() {
	}

	private static RegistryKey<LootTable> of(String path) {
		return RegistryKey.of(RegistryKeys.LOOT_TABLE,
				Identifier.of(RagnarsTwilightDimension.MOD_ID, path));
	}
}
