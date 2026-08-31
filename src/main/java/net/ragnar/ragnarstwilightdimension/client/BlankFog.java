package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.util.math.MathHelper;

/**
 * Fog distances for the disc.
 *
 * <h2>Why this has to exist at all</h2>
 *
 * <p>The disc wants a black nothing behind it rather than a sky, and the only way to get no sky drawn
 * is a dimension type whose {@code effects} are {@code minecraft:the_nether} - that is the one vanilla
 * set with {@code SkyType.NONE}. But effects are a single bundled object, and the Nether's also
 * answers {@code true} to {@code useThickFog}. So the disc was quietly getting the Nether's fog as
 * the price of the Nether's sky, and those two have nothing to do with each other.
 *
 * <p>Thick fog is {@code viewDistance x 0.05} to {@code min(viewDistance, 192) x 0.5} - which is fully
 * opaque at ninety-six blocks however far anybody's render distance is set. The eyes hang between
 * eighty and a hundred and ten blocks out, so they were being drawn as black on black; even at fifty
 * blocks an eye was already two thirds fogged out.
 *
 * <p>What is restored here is not a special fog, it is the <em>ordinary</em> one: the numbers below
 * are exactly vanilla's own non-thick terrain fog, the branch that would have been taken if the
 * dimension had not borrowed the Nether's effects. A gentle fade over the last few blocks of render
 * distance, and nothing at all before that.
 *
 * <p>The fog <em>colour</em> is not set here; it comes from {@code fog_color} in the biome JSON, and
 * with no sky drawn it is also the colour of the whole background. It stays black either way - fog
 * distance and fog colour are separate, which is why pushing the fog out does not make the void grey.
 */
@Environment(EnvType.CLIENT)
public final class BlankFog {
	private BlankFog() {
	}

	/**
	 * Where the fade begins, in blocks.
	 *
	 * <p>The sky pass keeps vanilla's zero: with no sky drawn it makes no difference, and matching the
	 * branch this replaces means there is nothing to rediscover if the dimension is ever given one.
	 */
	public static float start(BackgroundRenderer.FogType fogType, float viewDistance) {
		if (fogType == BackgroundRenderer.FogType.FOG_SKY) {
			return 0.0F;
		}

		// Vanilla's own: a tenth of the render distance, never less than four blocks or more than
		// sixty-four, taken off the far edge.
		return viewDistance - MathHelper.clamp(viewDistance / 10.0F, 4.0F, 64.0F);
	}

	/** Fully fogged at the render edge, and not one block before it. */
	public static float end(float viewDistance) {
		return viewDistance;
	}
}
