package net.ragnar.ragnarstwilightdimension.world.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

/**
 * Builds the blank one's dimension: one disc of snow at the origin and void everywhere else.
 *
 * <p>There is no noise here and nothing random. Every column either falls inside
 * {@link TheBlank#insideDisc} and gets {@link TheBlank#THICKNESS} blocks of snow, or it does not and
 * gets nothing at all. That makes the whole world a pure function of the constants in
 * {@link TheBlank}, which is the point: the disc does not need to be stored, cannot drift between
 * saves, and comes back the same if the chunks are ever deleted.
 *
 * <p>A custom generator rather than a superflat with the floor carved in afterwards, because a
 * carved floor is only correct in the chunks something bothered to carve. Flying out to the rim of a
 * world generated any other way finds either an endless plain or nothing at all.
 */
public class DiscChunkGenerator extends ChunkGenerator {
	public static final MapCodec<DiscChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
			).apply(instance, instance.stable(DiscChunkGenerator::new)));

	private static final BlockState FLOOR = Blocks.SNOW_BLOCK.getDefaultState();

	public DiscChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> getCodec() {
		return CODEC;
	}

	/**
	 * Lays the disc into whichever part of it falls in this chunk.
	 *
	 * <p>The heightmaps are updated by hand alongside the blocks. Nothing recomputes them for a
	 * generator that writes states directly, and everything downstream that asks how high the ground
	 * is here - mob spawning, structure placement, {@code getTopY} - reads them rather than the
	 * blocks.
	 */
	@Override
	public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig,
												  StructureAccessor structureAccessor, Chunk chunk) {
		ChunkPos pos = chunk.getPos();
		Heightmap oceanFloor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				if (!TheBlank.insideDisc(pos.getStartX() + localX, pos.getStartZ() + localZ)) {
					continue;
				}

				for (int y = TheBlank.FLOOR_Y - TheBlank.THICKNESS + 1; y <= TheBlank.FLOOR_Y; y++) {
					chunk.setBlockState(cursor.set(localX, y, localZ), FLOOR, false);
					oceanFloor.trackUpdate(localX, y, localZ, FLOOR);
					worldSurface.trackUpdate(localX, y, localZ, FLOOR);
				}
			}
		}

		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
		return TheBlank.insideDisc(x, z) ? TheBlank.FLOOR_Y + 1 : world.getBottomY();
	}

	@Override
	public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
		BlockState[] column = new BlockState[world.getHeight()];
		java.util.Arrays.fill(column, Blocks.AIR.getDefaultState());

		if (TheBlank.insideDisc(x, z)) {
			for (int y = TheBlank.FLOOR_Y - TheBlank.THICKNESS + 1; y <= TheBlank.FLOOR_Y; y++) {
				column[y - world.getBottomY()] = FLOOR;
			}
		}

		return new VerticalBlockSample(world.getBottomY(), column);
	}

	@Override
	public int getSpawnHeight(HeightLimitView world) {
		return TheBlank.FLOOR_Y + 1;
	}

	/** Nothing to do: the disc is laid down whole in {@link #populateNoise} and has no surface layer. */
	@Override
	public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
	}

	/** No caves in a floor three blocks thick. */
	@Override
	public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
					  StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
	}

	/** Nothing lives here that was not put here on purpose. */
	@Override
	public void populateEntities(ChunkRegion region) {
	}

	@Override
	public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
	}

	/**
	 * These two have to agree with {@code min_y} and {@code height} in the dimension type, which the
	 * game does not check for us - a mismatch writes blocks outside the world's own section array.
	 */
	@Override
	public int getMinimumY() {
		return 0;
	}

	@Override
	public int getWorldHeight() {
		return 128;
	}

	/** No water anywhere, so anything comparing against sea level should always be above it. */
	@Override
	public int getSeaLevel() {
		return 0;
	}
}
