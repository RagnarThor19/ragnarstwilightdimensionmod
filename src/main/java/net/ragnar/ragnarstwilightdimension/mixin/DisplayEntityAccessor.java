package net.ragnar.ragnarstwilightdimension.mixin;

import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The knobs on a display entity, which vanilla keeps for itself.
 *
 * <p>{@link DisplayEntity} is the one thing in the game that will draw an arbitrary box of an
 * arbitrary block at an arbitrary angle, which is the only way to put something genuinely
 * <i>solid</i> in the world without placing a block in it - see {@code Solid}. Everything that
 * decides what it looks like is a private setter on the class, reachable in vanilla only by writing
 * NBT at it, because the only thing that was ever meant to drive one is a command block.
 *
 * <p>So this is three invokers rather than a page of hand-built NBT. The names are checked at
 * startup: if a mapping moves under us the game refuses to load, which is the whole reason to do it
 * this way instead of hoping a string still matches.
 */
@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
	/** Where it is drawn, how big, and which way round. Relative to the entity's own position. */
	@Invoker("setTransformation")
	void callSetTransformation(AffineTransformation transformation);

	/** Overrides the light it is drawn at. {@link Brightness#FULL} is what makes a beam glow. */
	@Invoker("setBrightness")
	void callSetBrightness(Brightness brightness);

	/**
	 * How far off it is still drawn, as a multiple of the usual sixty-four blocks.
	 *
	 * <p>The default of one is measured from the entity's <i>position</i>, and a beam is drawn from
	 * its position out to seventy blocks away - so anybody standing at the far end of it is outside
	 * the range of the thing pointing at them.
	 */
	@Invoker("setViewRange")
	void callSetViewRange(float viewRange);
}
