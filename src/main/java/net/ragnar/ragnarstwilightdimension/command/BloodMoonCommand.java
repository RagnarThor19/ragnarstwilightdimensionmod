package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.ragnar.ragnarstwilightdimension.event.BloodMoon;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * {@code /bloodmoon start|stop} - runs the event on demand, since the natural roll averages about
 * two hours. Permission level 2.
 *
 * <p>Succeeds silently. The only sign that it worked is the same sign everybody else gets.
 */
public final class BloodMoonCommand {
	private BloodMoonCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("bloodmoon")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("start").executes(context -> set(context, true)))
						.then(CommandManager.literal("stop").executes(context -> set(context, false)))));
	}

	private static int set(CommandContext<ServerCommandSource> context, boolean start) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getServer().getWorld(ModDimensions.TWILIGHT_WORLD);

		if (world == null) {
			source.sendError(Text.literal("The twilight dimension is not loaded."));
			return 0;
		}

		if (start == BloodMoon.isActive()) {
			source.sendError(Text.literal("The blood moon is already " + (start ? "up." : "down.")));
			return 0;
		}

		// Deliberately silent on success, and not an oversight - do not add a confirmation back in.
		//
		// sendFeedback puts the line in front of every operator on the server, not just whoever typed
		// it, so a single /bloodmoon start used to announce itself to half the players it was meant to
		// catch out. Nothing about the event is supposed to arrive as an announcement: the fog turning
		// is the only notification there is, and a player who is told in chat what is happening has
		// been handed the answer before they have had a chance to be wrong about it.
		//
		// The errors above are kept. Those only ever reach the person who typed the command, and a
		// command that fails without saying so is just broken.
		if (start) {
			BloodMoon.start(world);
		} else {
			BloodMoon.stop(world);
		}

		return 1;
	}
}
