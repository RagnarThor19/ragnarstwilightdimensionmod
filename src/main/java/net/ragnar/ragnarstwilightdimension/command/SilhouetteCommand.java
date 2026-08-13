package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.entity.SilhouetteEntity;
import net.ragnar.ragnarstwilightdimension.entity.SilhouetteSpawner;

/**
 * {@code /silhouette [distance] [exit]} - puts one on the ground ahead of whoever ran it, for
 * testing. The optional exit pins how it leaves ({@code sprint}, {@code sink}, {@code blink}) rather
 * than leaving it to a one-in-three roll.
 *
 * <p>Requires permission level 2, so it stays out of an ordinary player's autocomplete. Works from
 * anything with a position and a facing, so command blocks and the server console can use it too.
 */
public final class SilhouetteCommand {
	private static final double DEFAULT_DISTANCE = 10.0;

	private SilhouetteCommand() {
	}

	private static final String[] EXIT_CHOICES = {"random", "sprint", "sink", "blink"};

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("silhouette")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> spawn(context, DEFAULT_DISTANCE, null))
						.then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(1.0, 64.0))
								.executes(context -> spawn(context,
										DoubleArgumentType.getDouble(context, "distance"), null))
								.then(CommandManager.argument("exit", StringArgumentType.word())
										.suggests((context, builder) ->
												CommandSource.suggestMatching(EXIT_CHOICES, builder))
										.executes(context -> spawn(context,
												DoubleArgumentType.getDouble(context, "distance"),
												StringArgumentType.getString(context, "exit")))))));
	}

	private static int spawn(CommandContext<ServerCommandSource> context, double distance, String exitName) {
		ServerCommandSource source = context.getSource();

		// The dedicated-server console has no world attached to its command source, so this has to
		// be checked rather than assumed.
		ServerWorld world = source.getWorld();
		if (world == null) {
			source.sendError(Text.literal(
					"This has to be run from in-game or a command block - the console has no world."));
			return 0;
		}

		Vec3d origin = source.getPosition();

		// Horizontal only, so looking at the sky doesn't fling the spawn point sideways.
		float yaw = source.getRotation().y * MathHelper.RADIANS_PER_DEGREE;
		int x = MathHelper.floor(origin.x - MathHelper.sin(yaw) * distance);
		int z = MathHelper.floor(origin.z + MathHelper.cos(yaw) * distance);

		SilhouetteEntity.Exit forcedExit = null;
		if (exitName != null && !exitName.equalsIgnoreCase("random")) {
			try {
				forcedExit = SilhouetteEntity.Exit.valueOf(exitName.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				source.sendError(Text.literal(
						"Unknown exit '" + exitName + "' - expected one of: " + String.join(", ", EXIT_CHOICES)));
				return 0;
			}
		}

		SilhouetteEntity spawned = SilhouetteSpawner.spawnAt(world, x, z);

		if (spawned == null) {
			source.sendError(Text.literal(
					"Nowhere to stand at " + x + ", " + z + " - try a different distance."));
			return 0;
		}

		if (forcedExit != null) {
			spawned.forceExit(forcedExit);
		}

		int y = spawned.getBlockY();
		String how = forcedExit == null ? "random exit" : forcedExit.name().toLowerCase(Locale.ROOT) + " exit";
		source.sendFeedback(
				() -> Text.literal("A silhouette is standing at " + x + ", " + y + ", " + z + " (" + how + ")"),
				false);
		return 1;
	}
}
