package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.server.world.ServerWorld;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;

/**
 * Something an attack started that is not finished yet.
 *
 * <p>Almost nothing the survival phase does happens on one tick. A charge is a warning, then a run,
 * then whatever it caught; snow has to fall before it lands; an eye has to look at somebody before it
 * shoots them. All of that is time, and time needs somewhere to live.
 *
 * <p>It lives here rather than in an entity. The pattern is the witness's {@code SkyStrike} made
 * general: a handful of numbers the boss keeps in a list and ticks itself, with nothing saved,
 * nothing in the entity tracker, and nothing left over if the fight ends in the middle of it. Some of
 * these do put real entities in the world - a wanderer, a falling block, five blank figures - and
 * those are the ones {@link #cancel} exists for.
 *
 * <p>An {@code Ongoing} is created by an {@link Attack} and handed to
 * {@link TheEntity#launch(Ongoing)}. It is ticked every tick until it says it is done, and it is
 * cancelled outright at every seam between phases, because the half second of black is a clean break
 * and nothing is allowed to survive it.
 */
public interface Ongoing {
	/**
	 * One tick of it.
	 *
	 * @return true when it is finished with and the boss should drop it
	 */
	boolean tick(TheEntity boss, ServerWorld world);

	/**
	 * The phase ended while this was still running: take back anything it put in the world.
	 *
	 * <p>Only needs implementing by the ones that spawned something. An attack made of particles and
	 * arithmetic has nothing to take back and can leave this alone.
	 */
	default void cancel(ServerWorld world) {
	}
}
