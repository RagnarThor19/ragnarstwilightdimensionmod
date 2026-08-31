package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.network.TheEntityPayload;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.List;

/**
 * Whether there is a fight on the disc, and everything about it that has to outlive the thing doing
 * the fighting.
 *
 * <h2>Why the boss bar does not belong to the boss</h2>
 *
 * <p>It used to, and that was wrong in a way worth writing down, because the obvious design has a
 * hole in it that only shows up when everybody dies.
 *
 * <p>An entity is only asked to do anything while its chunk is loaded and ticking. When the last
 * player leaves the disc - which, since dying sends you to your bed in another world, is what losing
 * <i>is</i> - the arena stops ticking within a second or so and then unloads. Two things follow, and
 * neither of them is obvious:
 *
 * <ul>
 *   <li>The entity's {@code remove} is never called. Unloading a chunk goes through
 *       {@code Entity#setRemoved}, which is final, and not through {@code remove(RemovalReason)} -
 *       so anything an entity does on the way out is simply skipped, and a boss bar cleared there is
 *       a boss bar that stays on the screen of somebody now standing in the overworld.
 *   <li>The entity is <i>written to the chunk</i>, with its health and its phase, and comes back
 *       exactly as it was the next time anybody walks in. That is not a reset, it is a save.
 * </ul>
 *
 * <p>So the bar lives here, on a class that ticks with the world whether or not anything is loaded,
 * and the Entity itself is told never to save - see {@link TheEntity#shouldSave}. Between them, every
 * way a fight can end goes through {@link #end}, including the ways nobody thought of: the server
 * stopping, the last player logging out, a chunk unloading at an awkward moment.
 *
 * <h2>The two rules</h2>
 *
 * <p>Somebody on the disc and no Entity: put one over the middle, at full health, at the top of its
 * intro. Nobody on the disc: end the fight and take it away. Every reset in the design falls out of
 * the second rule, which is why there is no code anywhere that handles a wipe - the disc is a sealed
 * room with one way in, so "everybody died" and "there is nobody on the disc" are the same event.
 *
 * <p>It also means the fight can be had again. The disc keeps whatever was opened on it last time, so
 * a party that has already won and comes back finds the way out still standing in the middle of the
 * circle - and a new Entity over the top of it.
 */
public final class TheEntityFight {
	/**
	 * The bar. One of them, for the one fight this dimension can have at a time, and it outlives every
	 * individual Entity that ever shows it - see the note above.
	 */
	static final ServerBossBar BAR = new ServerBossBar(
			Text.translatable("entity.ragnarstwilightdimension.the_entity"),
			BossBar.Color.WHITE, BossBar.Style.PROGRESS);

	/**
	 * How long the disc has to have been empty before the fight is ended, in ticks.
	 *
	 * <p>Not immediate, and this is the only judgement call in the class. A player crossing between
	 * dimensions is briefly in neither, and a client that is loading the disc has not arrived on it
	 * yet; five seconds is long enough that neither of those counts as everybody having left.
	 *
	 * <p>It is <b>not</b> a grace period for the death screen, because there is no death screen. Dying
	 * in here is an immediate respawn - see {@code TwilightRespawn} - so a player who dies is out of
	 * the world within a tick or two rather than lying there deciding. That is exactly why the ending
	 * cannot be left to the Entity's own ticking, and why {@link #end} sends the bar back to every
	 * player on the server rather than to the ones it can still see.
	 */
	private static final int EMPTY_GRACE_TICKS = 100;

	/** How often the disc is looked at. There is no hurry about any of this. */
	private static final int CHECK_INTERVAL_TICKS = 20;

	/** How far out to look for one that already exists. Comfortably the whole disc and then some. */
	private static final double SEARCH = TheBlank.RADIUS * 2.0 + 64.0;

	private static int emptyTicks;

	/** Whether {@link #end} has already run for this empty disc, so it runs once and not every second. */
	private static boolean ended;

	/**
	 * Whether the last one on this disc was killed rather than merely taken away.
	 *
	 * <p>Without this the reward for winning would be another fight starting over the bodies: the
	 * Entity discards itself, the disc still has people on it, and twenty ticks later the first rule
	 * puts a fresh one in the sky. So a kill is remembered until the circle has been empty for its
	 * grace period - long enough for the winners to walk through the door they opened, and gone by the
	 * time anybody could come back through it.
	 *
	 * <p>Not written to disk, deliberately. It only has to outlive the walk to the portal, and a server
	 * restarted between the kill and that walk has bigger discontinuities than this one.
	 */
	private static boolean beaten;

	private TheEntityFight() {
	}

	/** Called by {@link TheEntity} as it finishes going up. See {@link #beaten}. */
	public static void beaten() {
		beaten = true;
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(TheEntityFight::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.BLANK_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		boolean anybody = false;

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator()) {
				anybody = true;
				break;
			}
		}

		emptyTicks = anybody ? 0 : emptyTicks + 1;

		if (world.getServer().getTicks() % CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		List<TheEntity> here = world.getEntitiesByClass(TheEntity.class,
				new Box(TheBlank.EXIT).expand(SEARCH), entity -> true);

		if (anybody) {
			ended = false;

			if (here.isEmpty() && !beaten) {
				spawn(world);
			}

			// More than one is a bug somewhere upstream rather than a thing to design around, but two
			// boss bars and two soundtracks is a bad way to find out about it. The oldest keeps the fight.
			for (int i = 1; i < here.size(); i++) {
				here.get(i).discard();
			}

			return;
		}

		if (emptyTicks >= EMPTY_GRACE_TICKS && !ended) {
			ended = true;
			beaten = false;

			// Best effort. If the arena has already unloaded there is nothing in this list, and there does
			// not need to be - an Entity is never written to the chunk, so one that unloaded is gone.
			for (TheEntity entity : here) {
				entity.discard();
			}

			end(world.getServer());
		}
	}

	/**
	 * The fight is over, for everybody, however it ended.
	 *
	 * <p>Called from exactly two places: the disc going empty, above, and the moment the Entity
	 * finishes going up. Both are idempotent and neither depends on the Entity still existing, which
	 * is the entire point of this method being here rather than on it.
	 *
	 * <p>The bar is taken back with a packet sent flatly to everybody online rather than by removing
	 * them from it, and that is not belt and braces - it is the fix. {@code ServerBossBar#removePlayer}
	 * only sends anything <i>if the player was in its set</i>, and the set holds
	 * {@code ServerPlayerEntity} objects: a player who has died and respawned is a brand new object,
	 * and the one the bar is holding is a corpse nobody is looking through any more. Removing the live
	 * player finds nothing to remove and tells them nothing, which is precisely how somebody ends up
	 * standing in the overworld under a boss bar for a fight two dimensions away.
	 */
	public static void end(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.networkHandler.sendPacket(BossBarS2CPacket.remove(BAR.getUuid()));
			ServerPlayNetworking.send(player, TheEntityPayload.off());
		}

		// Server-side state back to nothing, including whatever stale objects were still in the set.
		BAR.clearPlayers();
		BAR.setVisible(false);

		ServerWorld blank = server.getWorld(ModDimensions.BLANK_WORLD);

		if (blank != null) {
			repair(blank);
		}
	}

	/**
	 * Puts back any floor the fight took out and did not return.
	 *
	 * <p>One attack digs a hole through the disc - see {@code HoleAttack} - and it has two of its own
	 * ways of filling it back in: its timer, and the phase seam. This is the third, and it exists
	 * because the first two are held by an object that only runs while the arena is ticking. A server
	 * stopped during those fifteen seconds, or a chunk unloaded out from under it, saves the disc with
	 * a hole in it, and a hole in a procedurally generated arena is permanent - the chunk has already
	 * been generated, so nothing will ever put it back.
	 *
	 * <p>Only air is replaced, which is what makes this safe to run over the whole disc rather than
	 * clever. Whatever was opened at the middle last time somebody won is not air and is left exactly
	 * where it is.
	 */
	private static void repair(ServerWorld blank) {
		BlockState floor = Blocks.SNOW_BLOCK.getDefaultState();
		BlockPos.Mutable at = new BlockPos.Mutable();
		int filled = 0;

		for (int x = -TheBlank.RADIUS; x <= TheBlank.RADIUS; x++) {
			for (int z = -TheBlank.RADIUS; z <= TheBlank.RADIUS; z++) {
				if (!TheBlank.insideDisc(x, z)) {
					continue;
				}

				for (int y = TheBlank.FLOOR_Y - TheBlank.THICKNESS + 1; y <= TheBlank.FLOOR_Y; y++) {
					at.set(x, y, z);

					if (blank.getBlockState(at).isAir()) {
						blank.setBlockState(at, floor, Block.NOTIFY_LISTENERS);
						filled++;
					}
				}
			}
		}

		if (filled > 0) {
			RagnarsTwilightDimension.LOGGER.info("Put back {} blocks of the disc.", filled);
		}
	}

	/** Puts one over the middle of the disc and starts its thirty seconds. */
	public static TheEntity spawn(ServerWorld world) {
		TheEntity entity = ModEntities.THE_ENTITY.create(world);

		if (entity == null) {
			return null;
		}

		// Before spawning, not after: begin() is what puts it over the middle of the disc, and an entity
		// added to the world at wherever create() left it is added into the wrong chunk.
		entity.begin();
		world.spawnEntity(entity);
		return entity;
	}
}
