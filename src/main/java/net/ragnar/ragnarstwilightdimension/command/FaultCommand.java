package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.mixin.ChunkGeneratorAccessor;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.feature.FaultFeature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /fault find [shaft|plinth] [radius]} - points at the nearest fault, without generating
 * anything to find it.
 *
 * <h2>Why this is not a search</h2>
 *
 * <p>{@code /locate} only works on structures, and these are features. A structure's position falls
 * out of the world seed, which is why the game can answer instantly; a feature's falls out of a
 * per-chunk roll taken during decoration, so the obvious way to find one is to generate chunks until
 * a shaft turns up. At one in eight hundred that is the better part of a thousand chunks, a minute of
 * the server thread stopped dead, and a heap of terrain nobody asked for written to disk.
 *
 * <p>So this does not search. It re-takes the roll.
 *
 * <p>Decoration seeds a fresh random per chunk per feature - {@code setPopulationSeed} off the world
 * seed and the chunk corner, then {@code setDecoratorSeed} off that, the feature's index and its
 * generation step. All of that is arithmetic. What comes out of it is exactly the values the
 * placement modifiers consume, in the order {@code PlacedFeature} consumes them: one
 * {@code nextFloat} for the rarity filter, two {@code nextInt(16)} for {@code in_square}, and then a
 * heightmap lookup that {@link ChunkGenerator#getHeight} answers off the noise with no chunk in
 * existence. Sixty-six thousand chunks of that is milliseconds.
 *
 * <p>That gets the rarity roll and the square exactly right. The feature's own terrain test is then
 * re-run the same way, off sampled heights rather than built ones - and this is the part that is only
 * nearly right, because a sampled height and a built one disagree by a block often enough that about
 * one candidate in six is not really there. Measured against real generation at a forced rarity:
 * thirty-eight of forty-six.
 *
 * <p>So the arithmetic produces a shortlist, not an answer. Candidates are sorted by distance and
 * checked against real blocks nearest-first, and the first one that is actually there is what gets
 * reported. That check is the only generation this command does, and the shortlist has already taken
 * it from sixty thousand chunks down to an expected one or two.
 *
 * <p>The feature's index is read out of the generator by {@link ChunkGeneratorAccessor} rather than
 * recomputed here. A locator that is confidently wrong is worse than no locator, and an off-by-one
 * in a reimplemented ordering would produce exactly that. The random implementation is pinned to
 * {@link Xoroshiro128PlusPlusRandom} for the same reason: {@code setPopulationSeed} derives its
 * value through {@code nextLong}, so seeding a legacy random here would give different chunks
 * entirely and there would be nothing to notice it by.
 *
 * <p><b>The one thing that has to be kept in step by hand</b> is the modifier list. Every step in
 * {@link #rollFor} is one entry from {@code worldgen/placed_feature/subsidence.json}; if a modifier
 * is added, removed or reordered there, it has to move here too, and nothing checks that for us.
 */
public final class FaultCommand {
	/** How far out to look, in chunks. At one in eight hundred this holds about eighty of them. */
	private static final int DEFAULT_RADIUS = 128;

	/** {@code raw_generation}, which is the step both faults are placed in. */
	private static final int STEP = 0;

	/**
	 * How many of the shortlist are checked against real blocks before giving up. Each one is a chunk
	 * generated, and the expected number needed is between one and two, so this is a ceiling on a bad
	 * run rather than a budget.
	 */
	private static final int MOST_CHECKED = 12;

	/** Kept in step with {@code worldgen/placed_feature/subsidence.json} and its twin by hand. */
	private static final int CHANCE = 800;

	private FaultCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("fault")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("find")
								.executes(context -> find(context, "subsidence", DEFAULT_RADIUS))
								.then(CommandManager.literal("shaft")
										.executes(context -> find(context, "subsidence", DEFAULT_RADIUS))
										.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 2048))
												.executes(context -> find(context, "subsidence",
														IntegerArgumentType.getInteger(context, "radius")))))
								.then(CommandManager.literal("plinth")
										.executes(context -> find(context, "uplift", DEFAULT_RADIUS))
										.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 2048))
												.executes(context -> find(context, "uplift",
														IntegerArgumentType.getInteger(context, "radius"))))))));
	}

	private static int find(CommandContext<ServerCommandSource> context, String which, int radius) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player - it looks outward from you."));
			return 0;
		}

		ServerWorld world = source.getWorld();
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			source.sendError(Text.literal("The faults are only in the twilight."));
			return 0;
		}

		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		PlacedFeature placed = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE)
				.get(Identifier.of(RagnarsTwilightDimension.MOD_ID, which));

		if (placed == null) {
			source.sendError(Text.literal("No placed feature called " + which + "."));
			return 0;
		}

		List<PlacedFeatureIndexer.IndexedFeatures> steps =
				((ChunkGeneratorAccessor) generator).twilight$indexedFeatures().get();

		if (steps.size() <= STEP) {
			source.sendError(Text.literal("This generator has nothing in raw_generation."));
			return 0;
		}

		int index = steps.get(STEP).indexMapping().applyAsInt(placed);
		long seed = world.getSeed();
		ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));

		ChunkPos from = player.getChunkPos();
		List<BlockPos> candidates = new ArrayList<>();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos hit = rollFor(world, generator, random, seed, index, placed,
						from.x + dx, from.z + dz);

				if (hit != null) {
					candidates.add(hit);
				}
			}
		}

		candidates.sort(Comparator.comparingDouble(
				hit -> hit.getSquaredDistance(player.getX(), hit.getY(), player.getZ())));

		// The prediction is a shortlist, not an answer. It gets the rarity roll and the square exactly
		// right, but the terrain test behind it is run against heights sampled straight off the noise
		// rather than off a built chunk, and those disagree by a block often enough that about one
		// candidate in six does not survive contact with the ground it is standing on. Measured at a
		// forced rarity: thirty-eight of forty-six.
		//
		// So every candidate is checked before it is reported, nearest first, against real blocks. That
		// is the only generation this command does, and the shortlist has already taken it from sixty
		// thousand chunks down to an expected one or two.
		BlockPos best = null;
		int tried = 0;

		for (BlockPos candidate : candidates) {
			if (tried++ >= MOST_CHECKED) {
				break;
			}

			if (confirm(world, candidate, which)) {
				best = candidate;
				break;
			}
		}

		if (best == null) {
			source.sendError(Text.literal(candidates.isEmpty()
					? "No " + label(which) + " within " + radius + " chunks of you. Try a larger radius."
					: "Found " + candidates.size() + " likely " + label(which)
							+ "s within " + radius + " chunks, but none of the nearest " + MOST_CHECKED
							+ " were really there. Try a larger radius."));
			return 0;
		}

		BlockPos middle = best.add(FaultFeature.SIDE / 2, 0, FaultFeature.SIDE / 2);
		int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, middle.getX(), middle.getZ());
		int blocks = (int) Math.sqrt(best.getSquaredDistance(player.getX(), best.getY(), player.getZ()));
		String at = middle.getX() + " " + surface + " " + middle.getZ();

		source.sendFeedback(() -> Text.literal("The nearest " + label(which) + " is at ")
				.append(Text.literal("[" + at + "]").setStyle(Style.EMPTY
						.withColor(Formatting.AQUA)
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + at))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Text.literal("Click to put a /tp in the chat box")))))
				.append(Text.literal(", " + blocks + " blocks away.")), false);

		return 1;
	}

	/**
	 * Re-takes one chunk's roll for one feature. Null unless that chunk would have got one.
	 *
	 * <p>Each step below is one placement modifier from the feature's JSON, in order. See the class
	 * note about keeping the two in step.
	 */
	private static BlockPos rollFor(ServerWorld world, ChunkGenerator generator, ChunkRandom random,
									long seed, int index, PlacedFeature placed, int chunkX, int chunkZ) {
		int startX = chunkX << 4;
		int startZ = chunkZ << 4;

		long population = random.setPopulationSeed(seed, startX, startZ);
		random.setDecoratorSeed(population, index, STEP);

		// minecraft:rarity_filter
		if (random.nextFloat() >= 1.0F / CHANCE) {
			return null;
		}

		// minecraft:in_square
		int x = random.nextInt(16) + startX;
		int z = random.nextInt(16) + startZ;

		// minecraft:heightmap - off the noise, since the chunk it would have read does not exist
		int y = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world,
				world.getChunkManager().getNoiseConfig());

		// minecraft:biome. Both of the dimension's biomes carry both faults, so today this never
		// rejects anything - it is here so that adding a third biome cannot quietly make the locator
		// point at ground the feature would have skipped.
		if (!carries(world, x, y, z, placed)) {
			return null;
		}

		// FaultFeature's own survey, run against the same sampled heights it would have read off the
		// finished chunk.
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;

		for (int dx = 0; dx < FaultFeature.SIDE; dx++) {
			for (int dz = 0; dz < FaultFeature.SIDE; dz++) {
				int top = generator.getHeight(x + dx, z + dz, Heightmap.Type.WORLD_SURFACE_WG, world,
						world.getChunkManager().getNoiseConfig()) - 1;
				lowest = Math.min(lowest, top);
				highest = Math.max(highest, top);
			}
		}

		return highest - lowest > FaultFeature.RELIEF ? null : new BlockPos(x, y, z);
	}

	/**
	 * Generates the one chunk a candidate landed in and looks at what is actually there.
	 *
	 * <p>Both signatures are read against the predicted height rather than against the neighbours, so
	 * this never has to generate anything beyond the single chunk, and neither is a near miss for
	 * ordinary terrain: nothing else in the dimension puts air at {@code y=1}, because the crust is
	 * solid stone from zero upward, and nothing else raises a column fifteen blocks clear of the
	 * height the noise says it should be.
	 */
	private static boolean confirm(ServerWorld world, BlockPos candidate, String which) {
		int x = candidate.getX() + FaultFeature.SIDE / 2;
		int z = candidate.getZ() + FaultFeature.SIDE / 2;

		world.getChunk(x >> 4, z >> 4);

		if (which.equals("subsidence")) {
			return world.getBlockState(new BlockPos(x, 1, z)).isAir();
		}

		// Comfortably under the twenty it was raised by, so a block of slop in the sampled height
		// cannot turn a real plinth into a rejection.
		return world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - candidate.getY() >= 15;
	}

	/** Whether the biome at this position lists this feature, which is what the biome modifier asks. */
	private static boolean carries(ServerWorld world, int x, int y, int z, PlacedFeature placed) {
		RegistryEntry<Biome> biome = world.getChunkManager().getChunkGenerator().getBiomeSource()
				.getBiome(x >> 2, y >> 2, z >> 2,
						world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());

		List<RegistryEntryList<PlacedFeature>> steps = biome.value().getGenerationSettings().getFeatures();

		return steps.size() > STEP && steps.get(STEP).stream()
				.anyMatch(entry -> entry.value() == placed);
	}

	private static String label(String which) {
		return which.equals("subsidence") ? "shaft" : "plinth";
	}
}
