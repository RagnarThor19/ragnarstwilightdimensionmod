package net.ragnar.ragnarstwilightdimension.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * You do not get asked.
 *
 * <p>Dying anywhere else stops the game: the world greys out, the screen says what killed you, and it
 * waits for you to choose between going back and giving up. In the twilight there is no screen and no
 * choice. You die and you are somewhere else, with no seam in between, and everything you were
 * carrying is still back there in the ground - see {@link PlayerGrave}.
 *
 * <p>That is the dream logic the whole dimension runs on, and this is the most direct statement of
 * it: dreams do not offer you a menu at the point where they change scene, they simply change scene,
 * and the moment of the change is the one part you never get to look at.
 *
 * <p>Nothing here forces a respawn or moves anybody. It flips the flag vanilla already has for this -
 * the one behind the {@code doImmediateRespawn} gamerule - on and off as players cross into the
 * dimension and back out of it, and the ordinary respawn does the rest. Doing it that way rather than
 * by suppressing the screen client-side means the death itself is untouched: the player still
 * respawns wherever their spawn point actually is, in whatever world that is in.
 *
 * <p>The flag lives on the client and only changes when it is told to, so every route that can leave
 * a player in a different world from the one they were last told about has to be covered: walking
 * through the portal, respawning out of the dimension, and logging in already inside it.
 */
public final class TwilightRespawn {
	private TwilightRespawn() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> update(handler.getPlayer()));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> update(player));

		// Fires for the death this is all about. A player who dies in the twilight is a different entity
		// by the time they are standing in the overworld, and the new one has to be told that the death
		// screen is theirs again.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> update(newPlayer));
	}

	/**
	 * Tells one player whether they are somewhere that asks.
	 *
	 * <p>Outside the twilight this hands the gamerule back rather than switching the screen on
	 * outright, so a world that was already set to respawn immediately keeps doing it.
	 */
	private static void update(ServerPlayerEntity player) {
		boolean immediate = ModDimensions.TWILIGHT_WORLD.equals(player.getWorld().getRegistryKey())
				|| player.getWorld().getGameRules().getBoolean(GameRules.DO_IMMEDIATE_RESPAWN);

		player.networkHandler.sendPacket(
				new GameStateChangeS2CPacket(GameStateChangeS2CPacket.IMMEDIATE_RESPAWN, immediate ? 1.0F : 0.0F));
	}
}
