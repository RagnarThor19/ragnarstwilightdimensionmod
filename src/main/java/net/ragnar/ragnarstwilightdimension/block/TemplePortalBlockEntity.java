package net.ragnar.ragnarstwilightdimension.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Exists only so the temple portal has something for a renderer to hang off.
 *
 * <p>It extends the vanilla end portal's block entity rather than {@code BlockEntity} directly, and
 * that is load-bearing: {@code EndPortalBlockEntityRenderer} is typed to this class, so extending it
 * is what lets the client reuse vanilla's portal rendering wholesale and change only the colour. See
 * {@code TemplePortalRenderer}.
 *
 * <p>Stores nothing and ticks never. The block it belongs to holds all the behaviour.
 */
public class TemplePortalBlockEntity extends EndPortalBlockEntity {
	public TemplePortalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TEMPLE_PORTAL_ENTITY, pos, state);
	}
}
