package net.ragnar.ragnarstwilightdimension.sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.ragnar.ragnarstwilightdimension.event.TwilightSchedule;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Someone else is out there.
 *
 * <p>Every so often this drops the sound of a player going about their business - a few footsteps
 * through grass, a tuft being torn up, a block of dirt being set down - onto a patch of ground close
 * enough to hear but well past what the fog lets you see. There is nothing there. Nothing is ever
 * there.
 *
 * <p>The sounds are the genuine vanilla block sounds in the genuine vanilla categories, so they are
 * indistinguishable from a real player doing the same thing nearby.
 */
public final class TwilightAmbience {
	// --- tuning ---------------------------------------------------------------
	/** How often each player rolls for an event. 100 ticks = 5 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 100;

	/** Chance per roll. 0.03 every 5s averages out to roughly one event every 3 minutes. */
	private static final float CHANCE = 0.03F;

	/**
	 * Distance the sound is placed from the player. Kept under 16, because the server only forwards
	 * a sound to players within 16 blocks of it unless the volume is above 1.0 (past that the range
	 * becomes 16 x volume).
	 */
	private static final double MIN_DISTANCE = 8.0;
	private static final double MAX_DISTANCE = 14.0;

	private static final float STEP_VOLUME = 0.8F;
	private static final float BLOCK_VOLUME = 1.0F;

	/**
	 * Flip to true once you have dropped .ogg files into
	 * {@code assets/ragnarstwilightdimension/sounds/ambience/}. Until then leaving this off keeps
	 * silent rolls out of the rotation.
	 */
	private static final boolean INCLUDE_CUSTOM_AMBIENCE = false;
	// --------------------------------------------------------------------------

	/** A sound waiting for its moment. */
	private record Cue(Vec3d pos, SoundEvent sound, SoundCategory category, float volume, float pitch,
					   int playAt) {
	}

	private static final List<Cue> QUEUE = new ArrayList<>();

	/** Runaway guard - a queue this long means something is wrong, so drop the lot. */
	private static final int MAX_QUEUED = 256;

	private TwilightAmbience() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(TwilightAmbience::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();
		playDueCues(world, now);

		if (!TwilightSchedule.rolls(now, CHECK_INTERVAL_TICKS, TwilightSchedule.AMBIENCE)) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (world.getRandom().nextFloat() < CHANCE) {
				scheduleEvent(world, player, now);
			}
		}
	}

	private static void playDueCues(ServerWorld world, int now) {
		if (QUEUE.isEmpty()) {
			return;
		}
		if (QUEUE.size() > MAX_QUEUED) {
			QUEUE.clear();
			return;
		}

		Iterator<Cue> cues = QUEUE.iterator();
		while (cues.hasNext()) {
			Cue cue = cues.next();
			if (cue.playAt() <= now) {
				// A null "except" player means nobody is skipped, so every player in range hears it.
				world.playSound(null, cue.pos().x, cue.pos().y, cue.pos().z,
						cue.sound(), cue.category(), cue.volume(), cue.pitch());
				cues.remove();
			}
		}
	}

	private static void scheduleEvent(ServerWorld world, ServerPlayerEntity player, int now) {
		Random random = world.getRandom();

		float bearing = random.nextFloat() * MathHelper.TAU;
		double distance = MathHelper.lerp(random.nextDouble(), MIN_DISTANCE, MAX_DISTANCE);

		double x = player.getX() + Math.cos(bearing) * distance;
		double z = player.getZ() + Math.sin(bearing) * distance;
		double y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				MathHelper.floor(x), MathHelper.floor(z));

		Vec3d at = new Vec3d(x, y, z);

		int roll = random.nextInt(INCLUDE_CUSTOM_AMBIENCE ? 5 : 4);
		switch (roll) {
			case 0, 1 -> footsteps(at, random, now);
			case 2 -> queue(at, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS,
					BLOCK_VOLUME, jitter(random), now + 1);
			case 3 -> digThenFill(at, random, now);
			// Plain dirt uses the gravel sound group in vanilla, hence BLOCK_GRAVEL_PLACE.
			default -> queue(player.getPos(), ModSounds.AMBIENCE_TWILIGHT.value(),
					SoundCategory.AMBIENT, 1.0F, 1.0F, now + 1);
		}
	}

	/** Four to seven paces crossing the ground, not heading anywhere near you. */
	private static void footsteps(Vec3d start, Random random, int now) {
		int paces = 4 + random.nextInt(4);
		float heading = random.nextFloat() * MathHelper.TAU;
		double strideX = Math.cos(heading) * 0.65;
		double strideZ = Math.sin(heading) * 0.65;

		int when = now;
		for (int i = 0; i < paces; i++) {
			when += 4 + random.nextInt(3);
			queue(start.add(strideX * i, 0.0, strideZ * i), SoundEvents.BLOCK_GRASS_STEP,
					SoundCategory.PLAYERS, STEP_VOLUME, jitter(random), when);
		}
	}

	/** Grass torn up, then a moment later a block of dirt set down over it. */
	private static void digThenFill(Vec3d at, Random random, int now) {
		queue(at, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, BLOCK_VOLUME, jitter(random), now + 1);
		queue(at, SoundEvents.BLOCK_GRAVEL_PLACE, SoundCategory.BLOCKS, BLOCK_VOLUME, jitter(random),
				now + 12 + random.nextInt(10));
	}

	private static float jitter(Random random) {
		return 0.94F + random.nextFloat() * 0.12F;
	}

	private static void queue(Vec3d pos, SoundEvent sound, SoundCategory category, float volume,
							  float pitch, int playAt) {
		QUEUE.add(new Cue(pos, sound, category, volume, pitch, playAt));
	}
}
