package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.particle.ModParticles;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * The snow on the disc.
 *
 * <p>Not weather. The dimension has no skylight, and vanilla will not run a weather cycle or draw
 * precipitation in a world that has none - and even where it does, the snow it draws falls at a fixed
 * speed and a density tied to the rain gradient, neither of which is adjustable. What is wanted here
 * is a great deal of it falling far too slowly, so it is done with particles instead: see
 * {@code SlowSnowParticle} for why the flake itself had to be a custom one.
 *
 * <p>Driven from the client rather than by the server spawning particles into the world. An ambient
 * effect that is always running is one particle packet per flake per player otherwise, forever, for
 * something every client can work out for itself from the fact that it is standing on the disc.
 *
 * <p>Flakes are put in a box around whoever is looking rather than over the disc as a whole. Seeding
 * the entire circle would be mostly wasted on air nobody is looking at, and the box travels with the
 * player so the snowfall never runs out or thins toward the rim.
 */
@Environment(EnvType.CLIENT)
public final class TwilightSnow {
	/**
	 * Flakes seeded per tick, on average - about eight a second.
	 *
	 * <p>Fractional on purpose. This started as a whole number of ticks between flakes, which cannot
	 * express a rate between one every two ticks and one every three; {@link #flakesThisTick} spends
	 * the fraction as a chance instead, so any rate at all can be asked for.
	 *
	 * <p>At around five hundred and fifty ticks of life apiece this settles at a bit over two hundred
	 * flakes in the air at once, through a box thirty-two blocks across.
	 */
	private static final double FLAKES_PER_TICK = 0.4167;

	/** How far either side of the player flakes are seeded, in blocks. */
	private static final double SPREAD = 16.0;

	/**
	 * The vertical band they appear in, relative to the player. It starts slightly below eye level so
	 * that walking onto the disc has snow in it immediately - seeded only overhead, at this speed,
	 * nothing would reach the player for the better part of a minute.
	 */
	private static final double LOWEST = -2.0;
	private static final double HIGHEST = 20.0;

	private static final Random RANDOM = Random.create();

	private TwilightSnow() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TwilightSnow::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientWorld world = client.world;
		Entity camera = client.getCameraEntity();

		if (world == null || camera == null || client.isPaused()
				|| !ModDimensions.BLANK_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		Vec3d at = camera.getPos();
		int flakes = flakesThisTick(rateFor(client.options.getParticles().getValue()));

		for (int i = 0; i < flakes; i++) {
			world.addParticle(ModParticles.SLOW_SNOW,
					at.x + (RANDOM.nextDouble() * 2.0 - 1.0) * SPREAD,
					at.y + LOWEST + RANDOM.nextDouble() * (HIGHEST - LOWEST),
					at.z + (RANDOM.nextDouble() * 2.0 - 1.0) * SPREAD,
					0.0, 0.0, 0.0);
		}
	}

	/**
	 * Turns an average rate into a whole number of flakes for this one tick.
	 *
	 * <p>The whole part always goes out; the fraction is the chance of one more. Over any run of ticks
	 * that averages out to exactly the rate asked for, which is what lets the rate be a decimal
	 * without the count having to be.
	 */
	private static int flakesThisTick(double rate) {
		int whole = (int) rate;
		return RANDOM.nextDouble() < rate - whole ? whole + 1 : whole;
	}

	/**
	 * Honours the particles video setting by hand.
	 *
	 * <p>Worth doing for something that runs every tick forever: anybody who has turned particles down
	 * has done it to get frames back, and an ambient effect is exactly what they meant.
	 *
	 * @return flakes per tick, or zero for none at all
	 */
	private static double rateFor(ParticlesMode mode) {
		return switch (mode) {
			case ALL -> FLAKES_PER_TICK;
			case DECREASED -> FLAKES_PER_TICK / 3.0;
			case MINIMAL -> 0.0;
		};
	}
}
