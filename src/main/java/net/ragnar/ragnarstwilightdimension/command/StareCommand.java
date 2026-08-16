package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.ragnar.ragnarstwilightdimension.event.Stare;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * {@code /stare [stage] | reset} - runs the stare on whoever typed it, for testing, rather than
 * waiting out the roughly one-an-hour roll.
 *
 * <p>Plain {@code /stare} is the real thing: it uses the player's own count and adds one to it, so
 * running it five times walks the whole approach from forty blocks down to eight. {@code /stare 3}
 * forces a particular occasion without touching the count, which is what you want when you are
 * looking at one distance over and over. {@code /stare reset} puts the player back to never having
 * been found.
 *
 * <p>Requires permission level 2, matching the other four.
 */
public final class StareCommand {
	private StareCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("stare")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> run(context, -1))
						.then(CommandManager.argument("stage", IntegerArgumentType.integer(1, Stare.distances().size()))
								.executes(context -> run(context, IntegerArgumentType.getInteger(context, "stage"))))
						.then(CommandManager.literal("reset")
								.executes(StareCommand::reset))));
	}

	private static int run(CommandContext<ServerCommandSource> context, int stage) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player - there is nobody for it to look at."));
			return 0;
		}

		ServerWorld world = player.getServerWorld();
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			source.sendError(Text.literal("Only happens in the twilight."));
			return 0;
		}

		if (!Stare.start(world, player, stage)) {
			source.sendError(Text.literal(
					"Nowhere to put it - something is in the way overhead. Move out from under it and try again."));
			return 0;
		}

		// The count is not advanced until it actually fires, half a second from now, so this reports
		// the occasion that is coming rather than one that has happened.
		int count = Stare.count(world, player);
		source.sendFeedback(() -> Text.literal(stage > 0
				? "Occasion " + stage + " is coming - forced, so the count stays at " + count
				: "Occasion " + (count + 1) + " is coming"), false);
		return 1;
	}

	private static int reset(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player."));
			return 0;
		}

		ServerWorld world = source.getServer().getWorld(ModDimensions.TWILIGHT_WORLD);
		if (world == null) {
			source.sendError(Text.literal("The twilight is not loaded."));
			return 0;
		}

		Stare.forget(world, player);
		source.sendFeedback(() -> Text.literal("It has never seen you. The next one will be at forty blocks."), false);
		return 1;
	}
}
