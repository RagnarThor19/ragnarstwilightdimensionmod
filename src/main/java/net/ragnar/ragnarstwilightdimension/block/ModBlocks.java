package net.ragnar.ragnarstwilightdimension.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * The mod's blocks. There is one, and it is not something anybody can hold: the temple portal is
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

	private ModBlocks() {
	}

	/** Touching the class runs the initialisers above; there is nothing else to do. */
	public static void initialize() {
	}

	private static Identifier id(String path) {
		return Identifier.of(RagnarsTwilightDimension.MOD_ID, path);
	}
}
