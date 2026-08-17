package net.ragnar.ragnarstwilightdimension.sound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BellBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BellBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.event.TwilightSchedule;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * The church bell, once a day.
 *
 * <p>The dimension has {@code fixed_time} on it, so there is no dawn to hang this off and no way to
 * tell one hour from another by looking. A day here is just twenty-four thousand ticks of server
 * clock like anywhere else, and the bell is the only thing that marks one - which is most of why it
 * is worth having. It is the single largest sound in the mod that is not attached to something
 * hunting you.
 *
 * <p>It carries {@link ModSounds#BELL_RANGE} blocks, which is well past the fog and well past the
 * render distance most people play this at. Hearing it means there is a building out there; hearing
 * it get louder means you are walking the right way.
 *
 * <h2>Finding the bells</h2>
 *
 * <p>A sound has to be played at a position, and the position of a church that nobody has visited is
 * not written down anywhere. So each ring asks {@link ServerWorld#locateStructure} where the nearest
 * church to each player is, which answers from the world seed alone and does not load or generate
 * anything. That gets us the building; it does not get us the bell inside it, which is nineteen
 * blocks up a tower whose orientation depends on which of the four rotations the church came out at.
 *
 * <p>So the exact block is found the once, the first time somebody is close enough that the tower is
 * loaded, by looking through the block entities of the chunks around the church - and then kept, so
 * every later ring comes from the right place even from two hundred blocks away with the whole
 * building unloaded. Until then the sound comes from the corner of the chunk the church starts in,
 * which is wrong by up to about fifteen blocks. That only ever happens to a listener who has never
 * been there, at a distance where fifteen blocks is a couple of degrees of bearing and a twentieth of
 * the volume.
 */
public final class ChurchBell {
	// --- tuning ---------------------------------------------------------------
	/** A day. The interval the bell rolls on, and it rolls every time - there is no chance here. */
	private static final int DAY_TICKS = 24000;

	/**
	 * How far out each player is asked about, in chunks. Sixteen chunks is 256 blocks, which is the
	 * bell's own range rounded up - a church further away than this could not be heard anyway.
	 */
	private static final int SEARCH_RADIUS_CHUNKS = 16;

	/**
	 * How far around a located church to look for the bell block, in chunks. The church is 24 by 13,
	 * so two chunks either way covers it whichever way round it was placed.
	 */
	private static final int BELL_SEARCH_CHUNKS = 2;

	/**
	 * A bell further than this from the located church, measured flat, is somebody else's bell.
	 *
	 * <p>Flat and not straight-line, because the position a church is located at has no useful height
	 * in it - see {@link #ring}. The real bell is eighty-odd blocks above it, up a tower, and a
	 * three-dimensional check against a y of zero would throw away every bell there has ever been.
	 */
	private static final double BELL_MAX_OFFSET = 40.0;

	/**
	 * Two players either side of the same church locate the same church, and the sound is played to
	 * everyone in range rather than to the player who found it - so without this it rings twice, on
	 * top of itself, for anybody who can hear both. Anything within this many blocks of a ring already
	 * queued is the same tower.
	 */
	private static final double SAME_CHURCH = 64.0;

	/**
	 * Left at 1.0, like the leviathan and for the same reason: the client clamps gain there, so a
	 * larger number would only stretch the range, which the fixed-range sound event already handles.
	 * Loudness is the .ogg's job.
	 */
	private static final float VOLUME = 1.0F;

	/** Straight, with no jitter. It is a cast bell; it does not have a different note each day. */
	private static final float PITCH = 1.0F;
	// --------------------------------------------------------------------------

	/** The churches, by tag, so {@code locateStructure} can be asked about all of them at once. */
	private static final TagKey<Structure> CHURCHES = TagKey.of(RegistryKeys.STRUCTURE,
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "church"));

	/**
	 * Church origin to the bell block inside it, once anybody has been near enough for us to look.
	 *
	 * <p>Keyed on what {@code locateStructure} returns, which is derived from the seed and so is the
	 * same position no matter which player asked or where they were standing. Lives for the session
	 * only - losing it costs one imprecise ring.
	 */
	private static final Map<BlockPos, BlockPos> BELLS = new HashMap<>();

	private ChurchBell() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(ChurchBell::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		if (TwilightSchedule.rolls(world.getServer().getTicks(), DAY_TICKS, TwilightSchedule.BELL)) {
			ring(world);
		}
	}

	/**
	 * Rings every church anybody is near enough to hear.
	 *
	 * <p>Churches with nobody within earshot are not rung, because there is nothing to ring for: the
	 * server sends a play packet to the players in range of the sound and there are none.
	 *
	 * <p>What comes back from {@code locateStructure} is the corner of the chunk the church starts in,
	 * at a y of zero - a position on the map rather than a position in the world. It is close enough
	 * to hunt for the bell block from and no use at all for playing a sound at, which is what the
	 * fallback below is for.
	 *
	 * @return how many towers rang
	 */
	public static int ring(ServerWorld world) {
		List<ServerPlayerEntity> players = world.getPlayers();
		if (players.isEmpty()) {
			return 0;
		}

		List<BlockPos> ringing = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			// One church each: the nearest. Two churches inside one player's range is possible and only
			// the closer of them rings, which is a trade for not walking the whole placement grid.
			BlockPos church = world.locateStructure(CHURCHES, player.getBlockPos(), SEARCH_RADIUS_CHUNKS, false);
			if (church == null) {
				continue;
			}

			BlockPos bell = bellOf(world, church);
			if (bell == null) {
				// Never been there. The tower is somewhere in that chunk and its height is unknown, so the
				// sound is put at the listener's own height, on the same reasoning TwilightLeviathan
				// places its calls at: attenuation is measured in three dimensions, and a guessed height
				// only ever adds distance that is not really between them.
				bell = new BlockPos(church.getX(), player.getBlockY(), church.getZ());
			}

			if (!alreadyRinging(ringing, bell)) {
				ringing.add(bell);
			}
		}

		for (BlockPos bell : ringing) {
			toll(world, bell);
		}

		return ringing.size();
	}

	/** One tower: the block swings if it is loaded, and the sound goes out either way. */
	private static void toll(ServerWorld world, BlockPos bell) {
		WorldChunk chunk = loadedChunk(world, new ChunkPos(bell));
		if (chunk != null) {
			BlockState state = chunk.getBlockState(bell);
			BlockEntity entity = chunk.getBlockEntity(bell);

			// activate() only sets the swing going and tells the clients about it - the sound vanilla
			// would play alongside it comes from BellBlock.ring, which is not called here. So the bell
			// visibly rings and the only thing anybody hears is ours.
			if (state.isOf(Blocks.BELL) && entity instanceof BellBlockEntity ringer) {
				ringer.activate(state.get(BellBlock.FACING));
			}
		}

		world.playSound(null, bell.getX() + 0.5, bell.getY() + 0.5, bell.getZ() + 0.5,
				ModSounds.BELL.value(), SoundCategory.AMBIENT, VOLUME, PITCH);
	}

	/** The bell block of a church, remembered once seen, or null if nobody has been close enough yet. */
	private static BlockPos bellOf(ServerWorld world, BlockPos church) {
		BlockPos known = BELLS.get(church);
		if (known != null) {
			return known;
		}

		BlockPos found = findBell(world, church);
		if (found != null) {
			BELLS.put(church, found);
		}

		return found;
	}

	/**
	 * Looks for the bell in the chunks around a church, and only in the ones that happen to be loaded.
	 *
	 * <p>Goes through each chunk's block entities rather than reading blocks: a bell is one, so the
	 * candidates are a handful of positions per chunk instead of the ninety thousand a box that size
	 * would have to be read one at a time.
	 */
	private static BlockPos findBell(ServerWorld world, BlockPos church) {
		ChunkPos centre = new ChunkPos(church);
		BlockPos best = null;
		double bestDistance = BELL_MAX_OFFSET * BELL_MAX_OFFSET;

		for (int x = centre.x - BELL_SEARCH_CHUNKS; x <= centre.x + BELL_SEARCH_CHUNKS; x++) {
			for (int z = centre.z - BELL_SEARCH_CHUNKS; z <= centre.z + BELL_SEARCH_CHUNKS; z++) {
				WorldChunk chunk = loadedChunk(world, new ChunkPos(x, z));
				if (chunk == null) {
					continue;
				}

				for (BlockPos candidate : chunk.getBlockEntityPositions()) {
					if (!chunk.getBlockState(candidate).isOf(Blocks.BELL)) {
						continue;
					}

					// Flat distance. The church's own y is zero and the bell's is up a tower.
					double dx = candidate.getX() - church.getX();
					double dz = candidate.getZ() - church.getZ();
					double distance = dx * dx + dz * dz;
					if (distance < bestDistance) {
						bestDistance = distance;
						best = candidate.toImmutable();
					}
				}
			}
		}

		return best;
	}

	/**
	 * The chunk if it is already in memory, and null if it is not.
	 *
	 * <p>The {@code false} is the whole point of going through the chunk manager rather than asking
	 * the world for a block: {@code world.getBlockEntity} on a chunk that is not loaded loads it,
	 * which for a daily sweep of every church within 256 blocks of every player is a great deal of
	 * disk work in aid of a noise.
	 */
	private static WorldChunk loadedChunk(ServerWorld world, ChunkPos pos) {
		Chunk chunk = world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
		return chunk instanceof WorldChunk loaded ? loaded : null;
	}

	/** Flat again: the same tower can be queued at bell height by one player and at ground level by another. */
	private static boolean alreadyRinging(List<BlockPos> ringing, BlockPos bell) {
		double minimum = SAME_CHURCH * SAME_CHURCH;
		for (BlockPos queued : ringing) {
			double dx = queued.getX() - bell.getX();
			double dz = queued.getZ() - bell.getZ();
			if (dx * dx + dz * dz < minimum) {
				return true;
			}
		}
		return false;
	}
}
