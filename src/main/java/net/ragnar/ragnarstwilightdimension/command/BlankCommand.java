package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.ragnar.ragnarstwilightdimension.portal.TempleGate;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

/**
 * {@code /blank exit} - opens the way off the disc.
 *
 * <p>Stands in for the thing that is supposed to do this. The disc is sealed by design: twelve eyes
 * to get in, and the blank one dead to get out. Until there is a fight to win, that leaves nobody a
 * way back off it except dying, and no way at all to test that the return trip works.
 *
 * <p>When the fight is built this command does not need to change and does not need to be removed.
 * It calls the same {@link TempleGate#openExit} the death will call, so what it exercises is exactly
 * the code path the real thing will take.
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
						.then(CommandManager.literal("exit").executes(BlankCommand::openExit))));
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
}
