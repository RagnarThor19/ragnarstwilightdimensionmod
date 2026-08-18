package net.ragnar.ragnarstwilightdimension.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A column of oak planks twenty-two blocks high, with an emptied grave at the foot of it.
 *
 * <p>One block wide, nothing on top of it, no ladder up it and no door in it. Beside it, touching the
 * base, an ordinary {@link GravestoneFeature.Kind#OPENED} grave - headstone, blank sign, the hole dug
 * out and nothing in it, and nobody standing at the end of it.
 *
 * <p>It is the only thing in the dimension that is unambiguously <i>somebody's work</i>. The house
 * and the church were remembered from somewhere else; this was put up here, out of the one material
 * the place has any of, by someone who was already in it. Every other building is a copy. This one is
 * a decision.
 *
 * <p><b>What the decision was is never stated and must not be.</b> The reading is there for any
 * player who has already died here once and found out that dying does not stop the game
 * ({@code TwilightRespawn}) and that you are buried where you fall ({@code PlayerGrave}): the fare
 * out is an eye, they did not have one, and this is the other way out. Whether it worked is not on
 * offer either. Twenty-two blocks is nineteen damage, one short of killing anybody who went off it at
 * full health - so the column is either a second attempt or the wrong height, and nothing here will
 * say which.
 *
 * <p>The grave being <i>open</i> is the other half, and it is the half that puts somebody else in the
 * world. It was dug by the dimension, the way every player's is, and then it was emptied - by them,
 * come back through the portal for their own things, or by whatever it is that opens the others.
 *
 * <p>Written as a feature rather than a jigsaw structure for the same reason the graves are: the
 * grave beside it has to be <i>the same object</i> as every other opened grave, built by the same
 * code off the same checks, rather than a template that resembles one.
 */
public class PillarFeature extends Feature<DefaultFeatureConfig> {
	/**
	 * How many planks go up. Changing it changes what the drop does to whoever went off the top, which
	 * is the only thing the building is about.
	 */
	private static final int HEIGHT = 22;

	public PillarFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();

		BlockPos base = GravestoneFeature.findGround(world, context.getOrigin());
		if (base == null) {
			return false;
		}

		// Nothing may be standing where the column goes. A pillar with a tree through it is not
		// something somebody built.
		for (int course = 1; course <= HEIGHT; course++) {
			if (!GravestoneFeature.isFree(world, base.up(course))) {
				return false;
			}
		}

		// The grave decides whether this generates at all. It is the fussier of the two - level ground
		// across three columns, headroom, something solid to have been dug out of - and it is the half
		// that carries the meaning, so ground that will not take a grave does not get a column either.
		Direction first = Direction.fromHorizontal(random.nextInt(4));

		for (int turn = 0; turn < 4; turn++) {
			Direction toGrave = first;
			for (int step = 0; step < turn; step++) {
				toGrave = toGrave.rotateYClockwise();
			}

			// The grave runs away from the column with its headstone against the base, so its three
			// columns start one block out. Its ground has to be the ground the pillar stands on: a
			// grave one step down the slope reads as two things that happen to be near each other.
			BlockPos head = base.offset(toGrave);
			BlockPos ground = GravestoneFeature.findGround(world, head.up());

			if (ground == null || ground.getY() != base.getY()) {
				continue;
			}

			if (GravestoneFeature.place(world, head.up(), toGrave, random, GravestoneFeature.Kind.OPENED)) {
				raise(world, base);
				return true;
			}
		}

		return false;
	}

	/** Puts the planks in, from the ground up, once the grave beside them is in the world. */
	private static void raise(StructureWorldAccess world, BlockPos base) {
		for (int course = 1; course <= HEIGHT; course++) {
			world.setBlockState(base.up(course), Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
		}
	}
}
