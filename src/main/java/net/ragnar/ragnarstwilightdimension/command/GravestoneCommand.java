package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.ragnar.ragnarstwilightdimension.world.feature.GravestoneFeature;

/**
 * {@code /gravestone [distance]} - lays out one grave on the ground ahead of whoever ran it, for
 * testing, rather than waiting for one to turn up in the world. The grave runs away from you, so you
 * walk up to the headstone first and the watcher is looking back towards you down its length.
 *
 * <p>Requires permission level 2, matching {@code /silhouette}.
 */
public final class GravestoneCommand {
	private static final double DEFAULT_DISTANCE = 6.0;

	private GravestoneCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("gravestone")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> place(context, DEFAULT_DISTANCE))
						.then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(1.0, 64.0))
								.executes(context -> place(context,
										DoubleArgumentType.getDouble(context, "distance"))))));
	}

	private static int place(CommandContext<ServerCommandSource> context, double distance) {
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

		// Horizontal only, so looking at the sky doesn't fling the placement sideways.
		float yawDegrees = source.getRotation().y;
		float yaw = yawDegrees * MathHelper.RADIANS_PER_DEGREE;
		int x = MathHelper.floor(origin.x - MathHelper.sin(yaw) * distance);
		int z = MathHelper.floor(origin.z + MathHelper.cos(yaw) * distance);

		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos head = new BlockPos(x, y, z);

		if (!GravestoneFeature.place(world, head, Direction.fromRotation(yawDegrees), world.getRandom())) {
			source.sendError(Text.literal(
					"No flat ground at " + x + ", " + y + ", " + z + " - try a different distance."));
			return 0;
		}

		source.sendFeedback(
				() -> Text.literal("A gravestone is standing at " + x + ", " + y + ", " + z),
				false);
		return 1;
	}
}
