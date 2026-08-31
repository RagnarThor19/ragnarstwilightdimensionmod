package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;

import java.util.List;

/**
 * One thing the sky does during the survival phase.
 *
 * <p>An attack owns nothing and remembers nothing between phases - it is asked, once a tick, whether
 * this is its tick, and if it says yes it is run and handed everybody still standing. Anything it
 * needs to keep for longer than that goes into an {@link Ongoing} instead, so that two of the same
 * attack can be in the air at once without either knowing about the other.
 *
 * <p>The contract is deliberately this small because there are meant to be a great many of these
 * sharing the same sixty-four seconds, and none of them should have to know about the other
 * nineteen. Adding one is writing a class and putting it in {@link Attacks#SURVIVAL}; nothing in the
 * boss changes, and nothing an attack does can break the clock, because an attack is never told how
 * long the phase is or asked to end it.
 */
public interface Attack {
	/** What it is called, for whoever is tuning the fight later. */
	String name();

	/**
	 * Whether this attack is in the fight at all right now.
	 *
	 * <p>Separate from {@link #due} because it is a different question: due asks whether this is the
	 * tick, available asks whether the attack exists yet. Almost everything answers yes always - the
	 * survival phase is weather and weather does not check anything - but it is the seam for an attack
	 * that only shows up once the thing is hurt, which is the one shape of escalation this fight has no
	 * other way to express. See {@code BeamAttack}.
	 */
	default boolean available(TheEntity boss) {
		return true;
	}

	/**
	 * Whether this attack goes off on this tick.
	 *
	 * <p>Given the boss's own random, so an attack can be genuinely occasional rather than merely
	 * infrequent - see {@link Attacks#ramped} for the ordinary steady-beat case, and {@code
	 * CircleAttack} for one that rolls for it.
	 *
	 * @param tick     ticks since the survival phase began
	 * @param progress how far through the phase, 0 at the first tick and 1 at the last
	 */
	boolean due(Random random, int tick, float progress);

	/**
	 * Do it.
	 *
	 * @param targets everybody the fight is happening to. Never empty - the boss does not run attacks
	 *                at an empty disc.
	 */
	void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress);
}
