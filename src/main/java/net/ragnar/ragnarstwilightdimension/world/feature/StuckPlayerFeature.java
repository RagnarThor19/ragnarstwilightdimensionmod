package net.ragnar.ragnarstwilightdimension.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.StuckPlayerEntity;

/**
 * Puts one {@link StuckPlayerEntity} into the ground at the placed position.
 *
 * <p>The placement hands over the first air block above the surface. The figure goes there, lowered
 * by {@link StuckPlayerEntity#SINK}, so its feet are inside the top block rather than on it. The
 * feature refuses anything that is not solid ground with open air above it, so a figure never ends
 * up in a tree or with its head inside an overhang.
 */
public class StuckPlayerFeature extends Feature<DefaultFeatureConfig> {
	public StuckPlayerFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos feet = context.getOrigin();
		Random random = context.getRandom();

		BlockState ground = world.getBlockState(feet.down());
		if (!ground.isSolidBlock(world, feet.down())
				|| !world.isAir(feet)
				|| !world.isAir(feet.up())) {
			return false;
		}

		StuckPlayerEntity figure = ModEntities.STUCK_PLAYER.create(world.toServerWorld());
		if (figure == null) {
			return false;
		}

		figure.refreshPositionAndAngles(
				feet.getX() + 0.5, feet.getY() - StuckPlayerEntity.SINK, feet.getZ() + 0.5,
				random.nextFloat() * 360.0F, 0.0F);
		figure.assignName(random);
		world.spawnEntity(figure);
		return true;
	}
}
