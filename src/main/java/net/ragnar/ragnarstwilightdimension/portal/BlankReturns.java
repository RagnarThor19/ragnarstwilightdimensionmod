package net.ragnar.ragnarstwilightdimension.portal;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Which temple each player went into the disc through.
 *
 * <p>The disc is one circle at one set of coordinates that everybody shares, so there is nothing
 * about a player standing on it that says where they came from. Without this, coming back out would
 * have to pick some default - the dimension spawn, or the nearest temple to the origin - and two
 * players who lit two temples on opposite sides of the world would both be returned to the same
 * wrong building.
 *
 * <p>Only the position is kept, not the world: the ring is a temple ring, temples only generate in
 * the twilight, and {@code TempleGate} will not light one anywhere else. If that ever stops being
 * true this needs a dimension alongside the position.
 *
 * <p>Kept as world data next to {@code StareState} and for the same reason - it has to survive a
 * death, which is one of the two ways the trip is meant to end.
 */
public class BlankReturns extends PersistentState {
	private static final String KEY = "ragnarstwilightdimension_blank_returns";

	private static final String RETURNS = "Returns";

	/** Player UUID to the portal block they stepped into, as an {@code [x, y, z]} int array. */
	private final NbtCompound returns;

	public BlankReturns() {
		this(new NbtCompound());
	}

	private BlankReturns(NbtCompound returns) {
		this.returns = returns;
	}

	private static final Type<BlankReturns> TYPE = new Type<>(
			BlankReturns::new,
			(nbt, lookup) -> new BlankReturns(nbt.getCompound(RETURNS)),
			null);

	/**
	 * Attached to the twilight rather than to the disc, because the positions in it are twilight
	 * positions and because the twilight is the world that is certain to exist whenever anybody asks.
	 */
	public static BlankReturns get(MinecraftServer server) {
		ServerWorld twilight = server.getWorld(ModDimensions.TWILIGHT_WORLD);
		ServerWorld
                host = twilight != null ? twilight : server.getOverworld();
		return host.getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	/** Where this player goes when they come back out, or null if we never saw them go in. */
	public BlockPos returnPoint(UUID player) {
		int[] stored = this.returns.getIntArray(player.toString());
		return stored.length == 3 ? new BlockPos(stored[0], stored[1], stored[2]) : null;
	}

	/** Notes the portal block a player just stepped into. Overwrites any earlier trip. */
	public void remember(UUID player, BlockPos portal) {
		this.returns.putIntArray(player.toString(),
				new int[] {portal.getX(), portal.getY(), portal.getZ()});
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.put(RETURNS, this.returns);
		return nbt;
	}
}
