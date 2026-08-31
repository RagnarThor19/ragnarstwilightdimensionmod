package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.server.network.ServerPlayerEntity;
import net.ragnar.ragnarstwilightdimension.entity.EyeEntity;
import net.ragnar.ragnarstwilightdimension.entity.EyeSpawner;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.portal.TempleGate;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.List;

/**
 * Everything about the disc that is worth being able to do without playing for it.
 *
 * <ul>
 *   <li>{@code /blank exit} - opens the way off. The same {@link TempleGate#openExit} the kill calls,
 *       so what this exercises is exactly the code path the real thing takes.
 *   <li>{@code /blank eye} - puts an eye out in the dark, which otherwise rolls once every two
 *       minutes and is over inside half of one.
 *   <li>{@code /blank entity skip} - ends whatever phase The Entity is in and starts the next.
 *   <li>{@code /blank entity kill} - sends it up, from any phase.
 * </ul>
 *
 * <p>The last two exist because the fight's shortest lap is a minute and a half and its shortest
 * <i>whole</i> run is about five minutes. Tuning the fifteen seconds it spends on the ground by
 * waiting sixty-four seconds for each attempt is not tuning, and neither is finding out whether the
 * death animation looks right by winning.
 *
 * <p>There is deliberately no {@code /blank entity} that spawns one. Nothing spawns it - it is there
 * whenever anybody is standing on the disc, and if it is not, that is a bug worth seeing rather than
 * a thing to work around. See {@code TheEntityFight}.
 *
 * <p>Requires permission level 2, matching the rest.
 */
public final class BlankCommand {
	private BlankCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("blank")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("exit").executes(BlankCommand::openExit))
						.then(CommandManager.literal("eye").executes(BlankCommand::eye))
						.then(CommandManager.literal("entity")
								.then(CommandManager.literal("skip").executes(BlankCommand::skip))
								.then(CommandManager.literal("kill").executes(BlankCommand::kill)))));
	}

	private static int skip(CommandContext<ServerCommandSource> context) {
		TheEntity entity = onTheDisc(context.getSource());

		if (entity == null) {
			return 0;
		}

		entity.skipPhase();
		context.getSource().sendFeedback(() -> Text.literal("It is " + entity.getPhase() + " now."), false);
		return 1;
	}

	private static int kill(CommandContext<ServerCommandSource> context) {
		TheEntity entity = onTheDisc(context.getSource());

		if (entity == null) {
			return 0;
		}

		entity.beginDeath();
		context.getSource().sendFeedback(() -> Text.literal("It is going up."), false);
		return 1;
	}

	/** The one on the disc, or null with the reason already reported. */
	private static TheEntity onTheDisc(ServerCommandSource source) {
		ServerWorld blank = source.getServer().getWorld(ModDimensions.BLANK_WORLD);

		if (blank == null) {
			source.sendError(Text.literal("The disc is not loaded."));
			return null;
		}

		List<TheEntity> here = blank.getEntitiesByClass(TheEntity.class,
				new Box(TheBlank.EXIT).expand(TheBlank.RADIUS * 2.0 + 64.0), entity -> true);

		if (here.isEmpty()) {
			source.sendError(Text.literal("There is nothing on the disc. Stand on it and it will be."));
			return null;
		}

		return here.get(0);
	}

	private static int openExit(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerWorld blank = source.getServer().getWorld(ModDimensions.BLANK_WORLD);

		if (blank == null) {
			source.sendError(Text.literal("The disc is not loaded."));
			return 0;
		}

		TempleGate.openExit(blank);
		source.sendFeedback(() -> Text.literal("The way out is open, at " + TheBlank.EXIT.toShortString() + "."), false);
		return 1;
	}

	/**
	 * Puts an eye where one would have appeared on its own, for whoever ran the command.
	 *
	 * <p>Worth having for the same reason {@code /stare} is: the thing rolls once every two minutes
	 * and then only sometimes, it is over inside half a minute, and it is entirely a matter of how it
	 * looks - which is not something that can be checked by waiting around and hoping.
	 */
	private static int eye(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player - the eye appears for somebody."));
			return 0;
		}
		if (!ModDimensions.BLANK_WORLD.equals(player.getServerWorld().getRegistryKey())) {
			source.sendError(Text.literal("Only on the disc."));
			return 0;
		}

		EyeEntity eye = EyeSpawner.spawnFor(player.getServerWorld(), player);
		if (eye == null) {
			source.sendError(Text.literal("Could not place one."));
			return 0;
		}

		source.sendFeedback(() -> Text.literal("Something is looking at you from "
				+ (int) player.getPos().distanceTo(eye.getPos()) + " blocks out."), false);
		return 1;
	}
}
