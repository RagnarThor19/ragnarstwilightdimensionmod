package net.ragnar.ragnarstwilightdimension.event;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.UUID;

/**
 * How many times each player has been looked at.
 *
 * <p>This is the one number in the mod that has to survive everything - logging out, dying, the
 * server restarting - because the whole point of the event is that it is the same encounter each
 * time, one step closer. A counter that resets puts the figure back out at forty blocks and quietly
 * throws away the only progression in the dimension.
 *
 * <p>Kept as world data rather than on the player, so it lives or dies with the world the same way
 * the graves do, and so a death cannot take it. Nothing here ever counts down.
 */
public class StareState extends PersistentState {
	/**
	 * The file it goes in, under {@code data/} in the dimension's folder. Attached to the twilight
	 * rather than the overworld because it is the twilight's count - a copy of the world without the
	 * dimension has nothing to remember.
	 */
	private static final String KEY = "ragnarstwilightdimension_stare";

	private static final String COUNTS = "Counts";

	private final NbtCompound counts;

	public StareState() {
		this(new NbtCompound());
	}

	private StareState(NbtCompound counts) {
		this.counts = counts;
	}

	private static final Type<StareState> TYPE = new Type<>(
			StareState::new,
			(nbt, lookup) -> new StareState(nbt.getCompound(COUNTS)),
			null);

	public static StareState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	/** How many times this player has been through it already, zero for somebody it has never found. */
	public int count(UUID player) {
		return this.counts.getInt(player.toString());
	}

	/** Counts one more, and hands back the new total. */
	public int increment(UUID player) {
		int next = count(player) + 1;
		this.counts.putInt(player.toString(), next);
		markDirty();
		return next;
	}

	/** Puts one player back to the beginning. Only {@code /stare reset} uses this. */
	public void reset(UUID player) {
		this.counts.remove(player.toString());
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		nbt.put(COUNTS, this.counts);
		return nbt;
	}
}
