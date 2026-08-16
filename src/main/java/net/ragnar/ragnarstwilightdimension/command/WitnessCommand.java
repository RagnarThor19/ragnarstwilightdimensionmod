package net.ragnar.ragnarstwilightdimension.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.entity.WitnessEntity;
import net.ragnar.ragnarstwilightdimension.entity.WitnessSpawner;

/**
 * {@code /witness [distance]} - stands one on the ground ahead of you, and {@code /witness look}
 * reports what the nearest one is doing.
 *
 * <p>Worth having for the same reason {@code /gravestone} is: the natural roll is about one in two
 * hours and the thing it produces is a landmark you then have to find. This puts one in front of you,
 * pointing at nothing, ready to be paid.
 *
 * <p>It does <b>not</b> start the fight. That takes sixty-four of something in your hand, and giving
 * it to the thing yourself is most of the point - a command that skipped it would be testing a
 * different feature.
 */
public final class WitnessCommand {
	private static final double DEFAULT_DISTANCE = 10.0;

	private WitnessCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("witness")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> place(context, DEFAULT_DISTANCE))
						.then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(2.0, 64.0))
								.executes(context -> place(context,
										DoubleArgumentType.getDouble(context, "distance"))))
						.then(CommandManager.literal("look")
								.executes(WitnessCommand::look))));
	}

	private static int place(CommandContext<ServerCommandSource> context, double distance) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player."));
			return 0;
		}

		ServerWorld world = player.getServerWorld();
		float yaw = player.getYaw();
		double radians = yaw * MathHelper.RADIANS_PER_DEGREE;

		Vec3d at = new Vec3d(
				player.getX() - MathHelper.sin((float) radians) * distance,
				player.getY(),
				player.getZ() + MathHelper.cos((float) radians) * distance);

		// Its own bearing, unrelated to yours - it is not looking at anything you can walk towards.
		WitnessEntity witness = WitnessSpawner.place(world, at, world.getRandom().nextFloat() * 360.0F - 180.0F);

		if (witness == null) {
			source.sendError(Text.literal("No room there - try a different distance."));
			return 0;
		}

		source.sendFeedback(() -> Text.literal("Standing at "
				+ witness.getBlockPos().toShortString()
				+ ". Give it " + WitnessEntity.FARE + " of anything."), false);
		return 1;
	}

	private static int look(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.literal("This has to be run by a player."));
			return 0;
		}

		WitnessEntity nearest = player.getServerWorld()
				.getEntitiesByClass(WitnessEntity.class, player.getBoundingBox().expand(64.0), w -> true)
				.stream()
				.min((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)))
				.orElse(null);

		if (nearest == null) {
			source.sendError(Text.literal("Nothing within sixty-four blocks."));
			return 0;
		}

		source.sendFeedback(() -> Text.literal(String.join(", ", nearest.describe())), false);
		return 1;
	}
}
