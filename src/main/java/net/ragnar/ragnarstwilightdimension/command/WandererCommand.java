package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.ragnar.ragnarstwilightdimension.entity.WandererEntity;
import net.ragnar.ragnarstwilightdimension.entity.WandererSpawner;

/**
 * {@code /wanderer [distance]} - sends one past whoever ran it straight away, for testing. The
 * optional distance overrides how close the run gets, which is otherwise
 * {@link WandererSpawner#PASS_DISTANCE}.
 *
 * <p>Needs a player rather than just a position, because the run is laid out around one. Permission
 * level 2, so it stays out of an ordinary player's autocomplete.
 */
public final class WandererCommand {
	private WandererCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("wanderer")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> send(context, WandererSpawner.PASS_DISTANCE))
						.then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(2.0, 64.0))
								.executes(context -> send(context,
										DoubleArgumentType.getDouble(context, "distance"))))));
	}

	private static int send(CommandContext<ServerCommandSource> context, double distance) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player - the run is laid out around one."));
			return 0;
		}

		ServerWorld world = source.getWorld();
		WandererEntity wanderer = WandererSpawner.sendPast(world, player, distance);

		if (wanderer == null) {
			source.sendError(Text.literal(
					"Could not start a run - either one is already about, or there is no ground where it would begin."));
			return 0;
		}

		source.sendFeedback(
				() -> Text.literal("Something is coming past at " + (int) distance + " blocks. Look around."),
				false);
		return 1;
	}
}
