package net.ragnar.ragnarstwilightdimension.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * The mod's blocks. The temple portal is not something anybody can hold: it is
 * placed by {@code TempleGate} and by the end of the fight, and has no item, no recipe and no way to
 * be broken.
 */
public final class ModBlocks {
	/**
	 * Settings copied from vanilla's end portal, for the same reasons it has them: no collision so you
	 * fall in, full light so the pool is not a dark hole in a dark room, and a hardness of -1 with
	 * blast resistance in the millions so that neither a pickaxe nor a creeper can take a portal out
	 * from under somebody standing in it.
	 */
	public static final Block TEMPLE_PORTAL = Registry.register(
			Registries.BLOCK,
			id("temple_portal"),
			new TemplePortalBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BLACK)
					.noCollision()
					.luminance(state -> 15)
					.strength(-1.0F, 3600000.0F)
					.dropsNothing()
					.pistonBehavior(PistonBehavior.BLOCK)));

	/**
	 * Declared after the block on purpose - the builder wants the block instance, and a field
	 * referenced before its own initialiser has run reads as null.
	 */
	public static final BlockEntityType<TemplePortalBlockEntity> TEMPLE_PORTAL_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			id("temple_portal"),
			BlockEntityType.Builder.create(TemplePortalBlockEntity::new, TEMPLE_PORTAL).build(null));

	/**
	 * The rose, as it was before 1.7 replaced it with the poppy: same name, same look. It has
	 * the poppy's settings and suspicious-stew effect, since that is what the game turned it into.
	 */
	public static final Block ROSE = Registry.register(
			Registries.BLOCK,
			id("rose"),
			new FlowerBlock(StatusEffects.NIGHT_VISION, 5.0F, AbstractBlock.Settings.copy(Blocks.POPPY)));

	/** The rose in a flower pot. Constructing it is what tells vanilla's empty pot it can hold a rose. */
	public static final Block POTTED_ROSE = Registry.register(
			Registries.BLOCK,
			id("potted_rose"),
			new FlowerPotBlock(ROSE, AbstractBlock.Settings.copy(Blocks.POTTED_POPPY)));

	public static final Item ROSE_ITEM = Registry.register(
			Registries.ITEM,
			id("rose"),
			new BlockItem(ROSE, new Item.Settings()));

	private ModBlocks() {
	}

	/** Touching the class runs the initialisers above. The rose also goes next to the poppy in the creative menu. */
	public static void initialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.addAfter(Items.POPPY, ROSE_ITEM));
	}

	private static Identifier id(String path) {
		return Identifier.of(RagnarsTwilightDimension.MOD_ID, path);
	}
}
