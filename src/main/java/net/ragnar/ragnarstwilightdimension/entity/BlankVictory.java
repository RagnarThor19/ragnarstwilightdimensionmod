package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Whether The Entity has been killed, once, ever.
 *
 * <p>The one thing about the disc that is not allowed to be a fight in progress. Everything else in
 * {@link TheEntityFight} is deliberately thrown away the moment the circle empties - the boss is
 * never written to the chunk, the bar is never saved, and a party that wipes comes back to a fight
 * that starts from the top. This is the exception, and it has to be, because "you have already done
 * this" is the only thing in the dimension that a wipe must <i>not</i> undo.
 *
 * <p>So it is a single boolean on disk, and it only ever goes one way. Once it is set:
 *
 * <ul>
 *   <li>nothing spawns on the disc again, ever, by itself - see {@link TheEntityFight};
 *   <li>walking back in is walking into an empty room with the way out still open in the middle of
 *       it, which is what the circle is for once it has been won.
 * </ul>
 *
 * <p>Kept on the disc's own world rather than on the twilight, next to the bedrock and the portal
 * that the kill also left behind. All three are the same fact written in three places - <i>this has
 * been done</i> - and deleting the dimension folder takes all three at once rather than leaving a
 * flag that says the fight is over standing over an arena that has forgotten it.
 */
public class BlankVictory extends PersistentState {
	private static final String KEY = "ragnarstwilightdimension_blank_victory";

	private static final String BEATEN = "Beaten";

	private boolean beaten;

	public BlankVictory() {
	}

	private BlankVictory(boolean beaten) {
		this.beaten = beaten;
	}

	private static final Type<BlankVictory> TYPE = new Type<>(
			BlankVictory::new,
			(nbt, lookup) -> new BlankVictory(nbt.getBoolean(BEATEN)),
			null);

	/**
	 * Falls back to the overworld for the same reason {@code BlankReturns} falls back to it: a server
	 * that somehow has no disc still has to be able to answer the question rather than throw on it.
	 */
	public static BlankVictory get(MinecraftServer server) {
		ServerWorld blank = server.getWorld(ModDimensions.BLANK_WORLD);
		ServerWorld host = blank != null ? blank : server.getOverworld();
		return host.getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	/** Whether the thing has been killed. */
	public boolean beaten() {
		return this.beaten;
	}

	/** It has been killed. Idempotent, because the ten seconds it takes are ticked, not awaited. */
	public void win() {
		if (!this.beaten) {
			this.beaten = true;
			markDirty();
		}
	}

	/** Puts the disc back to never having been won. Only {@code /blank reset} uses this. */
	public void forget() {
		if (this.beaten) {
			this.beaten = false;
			markDirty();
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		nbt.putBoolean(BEATEN, this.beaten);
		return nbt;
	}
}
