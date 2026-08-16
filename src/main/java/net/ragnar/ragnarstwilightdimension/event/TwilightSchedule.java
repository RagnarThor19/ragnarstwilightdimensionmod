package net.ragnar.ragnarstwilightdimension.event;

import net.ragnar.ragnarstwilightdimension.entity.SilhouetteSpawner;
import net.ragnar.ragnarstwilightdimension.entity.WandererSpawner;
import net.ragnar.ragnarstwilightdimension.sound.TwilightAmbience;
import net.ragnar.ragnarstwilightdimension.sound.TwilightLeviathan;

/**
 * Which tick each of the dimension's dice are rolled on.
 *
 * <p>Everything in here rolls on an interval, and every interval used is a multiple of the shortest
 * one. Left alone they all count from zero, which means they are not five independent clocks at all -
 * they are one clock that lands on the same tick over and over. Every 200 ticks four of them rolled
 * together and every 600 all five did, so a run of bad luck was never spread out: the dimension went
 * quiet for ten seconds and then tried to do four things at once. It is most obvious in the first
 * minute after arriving, when the counter is still small and every roll is still in step.
 *
 * <p>The odds are not the thing to change - each system still rolls exactly as often, at exactly the
 * chance it always had. What changes is <i>when</i>, by giving each one a phase: a fixed offset into
 * its own interval, so the rolls interleave instead of stacking.
 *
 * <table>
 *   <caption>The cycle, in ticks, as it repeats every 200</caption>
 *   <tr><th>tick</th><th>rolls</th><th>every</th></tr>
 *   <tr><td>0, 100</td><td>{@link TwilightAmbience}</td><td>100</td></tr>
 *   <tr><td>33</td><td>{@link SilhouetteSpawner}</td><td>200</td></tr>
 *   <tr><td>66</td><td>{@link TwilightLeviathan}</td><td>200</td></tr>
 *   <tr><td>133</td><td>{@link WandererSpawner}</td><td>200</td></tr>
 *   <tr><td>166</td><td>{@link BloodMoon}</td><td>600, so one cycle in three</td></tr>
 *   <tr><td>183</td><td>{@link Stare}</td><td>200</td></tr>
 *   <tr><td>116</td><td>{@link net.ragnar.ragnarstwilightdimension.entity.WitnessSpawner}</td><td>200</td></tr>
 * </table>
 *
 * <p>Nothing shares a tick with anything else, and that is the property this file exists to keep - it
 * is not visible from inside any one of those classes, which is why the numbers live here together
 * rather than one apiece next to the code that uses them.
 *
 * <p><b>Adding another:</b> its phase has to stay clear of the ones above modulo <i>every</i>
 * interval the two of them share, not just its own. A phase of 133 on a 400-tick interval still lands
 * on the wanderer's tick every other time; a phase of 100 on anything lands on the ambience roll,
 * because that one comes round twice as often as the rest.
 *
 * <p>All of this is measured against {@link net.minecraft.server.MinecraftServer#getTicks()}, which
 * is the only clock every one of them can agree on. The dimension's own {@code world.getTime()} would
 * do as well, but it has to be one or the other: two clocks with different origins put the spacing
 * back at the mercy of whatever the world's age happened to be when the server came up.
 */
public final class TwilightSchedule {
	/** Every 100 ticks, and the only one that rolls twice a cycle. */
	public static final int AMBIENCE = 0;

	/** Every 200 ticks. */
	public static final int SILHOUETTE = 33;

	/** Every 200 ticks. */
	public static final int LEVIATHAN = 66;

	/** Every 200 ticks. */
	public static final int WANDERER = 133;

	/** Every 600 ticks: 366 is 166 into the third cycle. */
	public static final int BLOOD_MOON = 366;

	/** Every 200 ticks, seventeen after the blood moon's slot and seventeen before the cycle turns. */
	public static final int STARE = 183;

	/** Every 200 ticks, in the gap between the ambience's second roll and the wanderer's. */
	public static final int WITNESS = 116;

	private TwilightSchedule() {
	}

	/**
	 * Whether this is the tick that system rolls on.
	 *
	 * <p>{@code floorMod} rather than {@code %} because the first cycle after a restart runs with
	 * {@code now} below the phase, and a plain remainder of a negative number is negative - which
	 * never equals zero, and would silently cost every system its first roll of the session.
	 */
	public static boolean rolls(int now, int interval, int phase) {
		return Math.floorMod(now - phase, interval) == 0;
	}
}
