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
import net.ragnar.ragnarstwilightdimension.portal.TempleGate;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * <p>Somebody alive on the disc and no Entity: put one over the middle, at full health, at the top of
 * its intro. Nobody alive on the disc: end the fight and take it away. The disc is a sealed room with
 * one way in, so "everybody died" and "there is nobody on the disc" are the same event - which is why
 * a wipe is not a case anything here has to detect. It is just the circle being empty.
 *
 * <p>Alive is part of the rule rather than a detail of it. A player lying dead on the disc is not
 * having the fight, and waiting for them to become another world's problem before the clock starts
 * would spend the first seconds of every reset watching a corpse.
 *
 * <p>The one thing the second rule does not cover by itself is how fast somebody can come back. The
 * Entity is gone within five seconds of the circle emptying and gone for good if the arena unloads,
 * but a party with a bed at the temple can be back inside four - so {@link #emptied} remembers that
 * the circle was empty at all, and the first person through the door after that gets a fight from the
 * top whether or not the last one had finished being taken away.
 *
 * <h2>The one thing that is kept</h2>
 *
 * <p>Winning. That is in {@link BlankVictory}, on disk, and it is the only fact about this fight that
 * survives the circle going empty: once the thing has been killed, nothing is ever put out there
 * again by itself, and the disc stays what the kill left it as - an empty room with the way out
 * standing open in the middle of it.
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
	 * cannot be left to the Entity's own ticking, and why the bar is taken back by {@link #prune}
	 * every tick rather than by the fight remembering to hand it in.
	 */
	private static final int EMPTY_GRACE_TICKS = 100;

	/**
	 * How long the disc has to have been empty for the fight on it to count as lost, in ticks.
	 *
	 * <p>A second, and it is deliberately much shorter than the grace above, because the two are
	 * answering different questions. That one is "is this over", and being wrong about it takes a boss
	 * bar off somebody mid-fight. This one is "was it lost", and being wrong about it is impossible in
	 * the direction that matters: there is exactly one way onto the disc and it has a portal cooldown
	 * on it, so nobody alive has ever been off it for a second and come back.
	 *
	 * <p>Without this the reset would be worth whatever the grace happens to be. A party that wipes
	 * with a bed at the temple can be back through the portal in four seconds, and what they would walk
	 * into is the same Entity on the same clock at the same health - the fight they just lost, waiting
	 * for them, which is the one thing it is not supposed to be.
	 */
	private static final int WIPE_TICKS = 20;

	/** How often the disc is looked at. There is no hurry about any of this. */
	private static final int CHECK_INTERVAL_TICKS = 20;

	/** How far out to look for one that already exists. Comfortably the whole disc and then some. */
	private static final double SEARCH = TheBlank.RADIUS * 2.0 + 64.0;

	private static int emptyTicks;

	/** Whether {@link #end} has already run for this empty disc, so it runs once and not every second. */
	private static boolean ended;

	/**
	 * Whether everybody has been off the disc since the last time anybody was on it.
	 *
	 * <p>This is the wipe, and it is a flag rather than a moment because the moment is not observable:
	 * the last player to die is gone within a tick or two and nothing on the disc is told about it.
	 * What can be observed is the circle being empty, and the next person to walk in finding this set
	 * is the next person to walk in on a fight that nobody survived.
	 */
	private static boolean emptied;

	/**
	 * Everybody currently holding the bar: who they are, and the object they were when they got it.
	 *
	 * <p>Both halves are load-bearing, and the pair is the whole of why a bar can no longer be left in
	 * another world. {@code ServerBossBar} holds {@code ServerPlayerEntity} objects, and a player who
	 * dies and respawns is a <i>new object</i> - so asking the bar to remove the live player finds
	 * nothing to remove and sends them nothing, while the one it is still holding is a corpse nobody
	 * is looking through any more. The uuid is what survives that; the object is what the bar itself
	 * has to be handed back.
	 */
	private static final Map<UUID, ServerPlayerEntity> SHOWING = new HashMap<>();

	private TheEntityFight() {
	}

	/** It has been killed, for good. See {@link BlankVictory}. */
	public static void won(ServerWorld blank) {
		BlankVictory.get(blank.getServer()).win();
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(TheEntityFight::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.BLANK_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		// Every tick, ahead of everything else. Whoever this takes the bar from has already stopped
		// being in the fight - by dying, by walking out, by logging off - and every one of those is
		// somebody who is somewhere else by the time they see their screen again.
		prune(world);

		boolean anybody = false;

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator() && player.isAlive()) {
				anybody = true;
				break;
			}
		}

		emptyTicks = anybody ? 0 : emptyTicks + 1;

		if (emptyTicks >= WIPE_TICKS) {
			emptied = true;
		}

		if (world.getServer().getTicks() % CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		List<TheEntity> here = world.getEntitiesByClass(TheEntity.class,
				new Box(TheBlank.EXIT).expand(SEARCH), entity -> true);

		if (anybody) {
			ended = false;

			// Already won: walking back in is walking into an empty circle, and nothing is put up.
			// Anything somehow still standing out there is a leftover rather than a fight - it goes, and
			// the bar and the music go with it.
			if (BlankVictory.get(world.getServer()).beaten()) {
				if (!here.isEmpty()) {
					for (TheEntity entity : here) {
						entity.discard();
					}

					end(world.getServer());
				}

				return;
			}

			if (here.isEmpty()) {
				spawn(world);
			} else if (emptied) {
				// Somebody walking in on a fight that everybody died in. Starting it over is the same
				// three lines as putting a new one up - full health, the top of the intro, a swept arena -
				// because it is the same thing: what makes a reset is the circle having been empty, not
				// whether the chunk happened to unload before anybody came back.
				here.get(0).begin();
			}

			emptied = false;

			// More than one is a bug somewhere upstream rather than a thing to design around, but two
			// boss bars and two soundtracks is a bad way to find out about it. The oldest keeps the fight.
			for (int i = 1; i < here.size(); i++) {
				here.get(i).discard();
			}

			return;
		}

		if (emptyTicks >= EMPTY_GRACE_TICKS && !ended) {
			ended = true;

			// Best effort. If the arena has already unloaded there is nothing in this list, and there does
			// not need to be - an Entity is never written to the chunk, so one that unloaded is gone.
			for (TheEntity entity : here) {
				// Except for one that was already going up. A kill nobody was left to watch land is still
				// a kill - it only gets into that phase one way, by being beaten - and the ten seconds it
				// had left were a curtain call rather than part of the fight. Somebody who won and then
				// fell off the edge of the disc watching it rise has still won, so the last two things the
				// rise would have done are done here instead of being thrown away with it.
				if (entity.getPhase() == TheEntity.Phase.DYING) {
					TempleGate.openExit(world);
					won(world);
				}

				entity.discard();
			}

			end(world.getServer());
		}
	}

	// --- the bar --------------------------------------------------------------

	/**
	 * Who is looking at the bar this tick, settled from scratch every tick.
	 *
	 * <p>Called by {@link TheEntity} while it is fighting, and whether there is a fight at all is the
	 * only thing it gets a say in. <i>Where</i> the bar is allowed to be is decided here and nowhere
	 * else: on the disc, alive, not spectating. There is no distance test - the disc is one circle
	 * seventy across with nothing else in it, so being on it is the test - and there is no way onto
	 * the list from any other world, which is the point of it being one list in one place.
	 */
	static void showBar(ServerWorld blank, boolean fighting) {
		if (!fighting) {
			BAR.setVisible(false);
			clear(blank.getServer());
			return;
		}

		BAR.setVisible(true);

		for (ServerPlayerEntity player : blank.getPlayers()) {
			if (!watching(player, blank)) {
				continue;
			}

			ServerPlayerEntity had = SHOWING.put(player.getUuid(), player);

			if (had == player) {
				continue;
			}

			// A different object under the same uuid is somebody who died and came back. The bar is
			// holding the corpse; hand it to the person.
			if (had != null) {
				BAR.removePlayer(had);
			}

			BAR.addPlayer(player);
		}
	}

	/** Whether this player is somebody the fight is currently happening to. */
	private static boolean watching(ServerPlayerEntity player, ServerWorld blank) {
		return player.getServerWorld() == blank && player.isAlive() && !player.isSpectator();
	}

	/**
	 * Takes the bar back from anybody who has stopped being in the fight.
	 *
	 * <p>Run every tick from the world tick rather than from the boss, because the case it exists for
	 * is the boss no longer ticking: the last player dies, the arena unloads within a second, and
	 * every line of code that would have handed the bar in is in a method nothing will call again.
	 * This one keeps running.
	 */
	private static void prune(ServerWorld blank) {
		if (SHOWING.isEmpty()) {
			return;
		}

		MinecraftServer server = blank.getServer();
		Iterator<Map.Entry<UUID, ServerPlayerEntity>> showing = SHOWING.entrySet().iterator();

		while (showing.hasNext()) {
			Map.Entry<UUID, ServerPlayerEntity> entry = showing.next();
			ServerPlayerEntity live = server.getPlayerManager().getPlayer(entry.getKey());

			if (live != null && live == entry.getValue() && watching(live, blank)) {
				continue;
			}

			take(entry.getValue(), live);
			showing.remove();
		}
	}

	/** Hands the bar in for everybody holding one. */
	private static void clear(MinecraftServer server) {
		if (SHOWING.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, ServerPlayerEntity> entry : SHOWING.entrySet()) {
			take(entry.getValue(), server.getPlayerManager().getPlayer(entry.getKey()));
		}

		SHOWING.clear();
	}

	/**
	 * Off one player's screen, whichever of the two objects they currently are.
	 *
	 * <p>{@code removePlayer} on the object the bar is holding is what empties the bar's own set, and
	 * the flat packet to whoever is actually playing is what reaches the screen. Nearly always they
	 * are the same player and one of the two is redundant; on the tick somebody respawns they are not,
	 * and a second removal is a packet the client throws away.
	 */
	private static void take(ServerPlayerEntity had, ServerPlayerEntity live) {
		BAR.removePlayer(had);

		if (live != null) {
			live.networkHandler.sendPacket(BossBarS2CPacket.remove(BAR.getUuid()));
		}
	}

	/**
	 * The fight is over, for everybody, however it ended.
	 *
	 * <p>Called from three places: the disc going empty, above, the moment the Entity finishes going
	 * up, and somebody walking back into a circle that has already been won. All three are idempotent
	 * and none of them depends on the Entity still existing, which is the entire point of this method
	 * being here rather than on it.
	 *
	 * <p>The bar is taken back with a packet sent flatly to everybody online as well as from the
	 * people known to be holding it, and that is not belt and braces - it is the one of the three that
	 * covers a bar left on somebody this class is no longer tracking at all.
	 */
	public static void end(MinecraftServer server) {
		BAR.setVisible(false);
		clear(server);

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.networkHandler.sendPacket(BossBarS2CPacket.remove(BAR.getUuid()));
			ServerPlayNetworking.send(player, TheEntityPayload.off());
		}

		// Server-side state back to nothing, including whatever stale objects were still in the set.
		BAR.clearPlayers();

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
