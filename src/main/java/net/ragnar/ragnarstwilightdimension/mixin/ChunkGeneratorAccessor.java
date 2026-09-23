package net.ragnar.ragnarstwilightdimension.mixin;

import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Supplier;

/**
 * Reaches the generator's own feature ordering, which is what decides the decorator seed.
 *
 * <p>{@code /fault find} has to answer "would a shaft have been rolled for this chunk" without
 * generating the chunk, and the roll is seeded with {@code setDecoratorSeed(populationSeed, index,
 * step)} where {@code index} is the feature's position in this list. Recomputing that ordering by
 * hand would be a second implementation of something the game already worked out once, and a silent
 * off-by-one in it would produce a locator that confidently points at empty ground. So it is read
 * from the generator instead.
 */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
	@Accessor("indexedFeaturesListSupplier")
	Supplier<List<PlacedFeatureIndexer.IndexedFeatures>> twilight$indexedFeatures();
}
