package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.ragnar.ragnarstwilightdimension.sound.ChurchBell;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * {@code /bell} - rings the churches now, rather than waiting out the twenty minutes to the next
 * daily one.
 *
 * <p>Exactly what the daily roll does, including finding the churches, so it also answers the
 * question you actually have while testing: whether there is one in earshot at all. Nothing rang
 * means nothing is within {@code ModSounds.BELL_RANGE} of anybody.
 *
 * <p>Requires permission level 2, matching the rest.
 */
public final class BellCommand {
	private BellCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("bell")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(BellCommand::run)));
	}

	private static int run(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player - the bells ring for whoever is near one."));
			return 0;
		}

		ServerWorld world = player.getServerWorld();
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			source.sendError(Text.literal("Only happens in the twilight."));
			return 0;
		}

		int rung = ChurchBell.ring(world);
		if (rung == 0) {
			source.sendError(Text.literal("No church within earshot of anybody."));
			return 0;
		}

		source.sendFeedback(() -> Text.literal(rung == 1 ? "One bell." : rung + " bells."), false);
		return rung;
	}
}
