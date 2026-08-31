package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.ragnarstwilightdimension.entity.attack.Attack;
import net.ragnar.ragnarstwilightdimension.entity.attack.Attacks;
import net.ragnar.ragnarstwilightdimension.entity.attack.Ongoing;
import net.ragnar.ragnarstwilightdimension.network.TheEntityPayload;
import net.ragnar.ragnarstwilightdimension.portal.TempleGate;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Entity. The thing the disc was built around, and the only fight in the dimension that is not
 * optional once you are standing in it.
 *
 * <h2>The shape of it</h2>
 *
 * <p>Four phases, and the first three go round for ever until somebody stops:
 *
 * <ol>
 *   <li><b>{@link Phase#INTRO}</b> - thirty seconds. It hangs over the middle of the disc with its
 *       face turned up at a sky the dimension does not draw, and does nothing whatsoever.
 *       {@code entitystart.ogg} plays, and the half minute is not for it - it is for whoever just
 *       arrived, to eat, to drink, and to look at it.
 *   <li><b>{@link Phase#SURVIVAL}</b> - sixty-four seconds, exactly the length of {@code entity.ogg}.
 *       It goes up to twenty blocks and stays there, out of reach of anything a player can swing, and
 *       nothing they do to it lands. This half of the fight is not a fight: it is weather. See
 *       {@link Attacks}.
 *   <li><b>{@link Phase#PAUSE}</b> - fifteen seconds, over one of {@code entitypause1..3.ogg}. It
 *       comes down. Now it can be hurt, and now it hurts back - and it moves like nothing else in the
 *       mod does, because the whole point of these fifteen seconds is that they cannot be planned.
 *       See {@link Move}.
 *   <li><b>{@link Phase#DYING}</b> - it goes up, the way the dragon goes up, and does not come back.
 * </ol>
 *
 * <p>Every seam between phases is the same half second: the screen goes black for everybody on the
 * disc, a switch is thrown, and when the world comes back the thing is somewhere else. That is the
 * only move in the fight that is never seen, and it is what makes twenty blocks up and standing on
 * the ground read as two different rooms rather than as one thing travelling between them.
 *
 * <h2>What is here and what is not</h2>
 *
 * <p>What the survival phase actually throws is not here at all - it is the list in {@link Attacks},
 * which the phase walks once a tick without knowing what is in it, so that the twenty more that are
 * wanted can be added without touching this class.
 *
 * <p>What this class owns is the clock, the blackouts, and the fifteen seconds it spends on the
 * ground. What it deliberately does <b>not</b> own is the boss bar, the ending, or its own
 * existence - all three belong to {@link TheEntityFight}, for reasons written out at length there.
 * The short version: an entity stops being asked to do anything the moment its chunk stops ticking,
 * so anything that has to happen when the last player dies cannot be kept on the thing that killed
 * them.
 *
 * <p>It is never kept between fights, and never even written to disk - see {@link #shouldSave}. The
 * fight puts one on the disc when somebody is standing on it and takes it away when nobody is, which
 * is where "if they all die it resets" comes from: dying empties the disc, and an empty disc has no
 * Entity on it.
 */
public class TheEntity extends MobEntity {
	/** The same shape as the blank figure, because it is the blank figure. */
	public static final float WIDTH = 0.6F;
	public static final float HEIGHT = 1.8F;

	/**
	 * How much of it there is to get through, and it is only ever reachable in fifteen second windows.
	 * Four or five clean pauses with a decent weapon, which is somewhere near five minutes of fight.
	 */
	public static final float MAX_HEALTH = 400.0F;

	/**
	 * Thirty-three seconds of nothing at all, because {@code entitystart.ogg} is 32.91 of them.
	 *
	 * <p>Rounded up rather than down, so the switch lands on a track that has finished rather than
	 * cutting the last three seconds of it off. The tenth of a second of silence on the end is the
	 * price, and nobody has ever heard a tenth of a second of silence before a scare.
	 */
	private static final int INTRO_TICKS = 660;

	/** Sixty-four seconds, which is the length of {@code entity.ogg}. These two numbers are one number. */
	public static final int SURVIVAL_TICKS = 1280;

	/** Fifteen seconds on the ground, which is the length of the pause tracks. */
	private static final int PAUSE_TICKS = 300;

	/** Half a second of nothing, at every seam. */
	private static final int BLACKOUT_TICKS = 10;

	/** How long the clients are told to go on believing the fight is up. See {@link TheEntityPayload}. */
	private static final int HOLD_TICKS = 40;

	/** How often that is re-stated. Comfortably inside the hold above, twice over. */
	private static final int HEARTBEAT_TICKS = 10;

	/** The dragon's own number: ten seconds of going up. */
	private static final int DEATH_TICKS = 200;

	/** How fast it rises while it dies, in blocks a tick. Also the dragon's. */
	private static final double DEATH_RISE = 0.1;

	/** What it is worth. Handed out over the ten seconds rather than in one lump at the end. */
	private static final int DEATH_XP = 5000;

	/** How often a share of that is dropped. */
	private static final int XP_EVERY = 10;

	// --- where it stands ------------------------------------------------------

	/** The middle of the disc, in the middle of the block. */
	private static final double CENTRE_X = 0.5;
	private static final double CENTRE_Z = 0.5;

	/** Standing height. The disc is one flat sheet, so this is the ground everywhere on it. */
	private static final double GROUND_Y = TheBlank.FLOOR_Y + 1;

	/** How high it hangs during the intro. High enough to read as floating from across the circle. */
	private static final double INTRO_Y = GROUND_Y + 6.0;

	/** And during the survival phase. Twenty, which is out of reach of everything. */
	private static final double SURVIVAL_Y = GROUND_Y + 20.0;

	/** Straight up. There is no sky there, which has never stopped it. */
	private static final float SKY_PITCH = -90.0F;

	/** How fast it turns on the spot while it is up there, in degrees a tick. */
	private static final float SURVIVAL_SPIN = 0.6F;

	// --- the fifteen seconds on the ground ------------------------------------

	/** How far it will look for somebody. The disc is seventy across, so this is all of it. */
	private static final double REACH = 80.0;

	/** How close it has to be to swing. A little past a player's own reach, and no more. */
	private static final double PUNCH_RANGE = 3.2;

	/** Ticks between swings. */
	private static final int PUNCH_COOLDOWN = 14;

	/** How hard it hits, and what the landing of a swoop does to whoever was underneath. */
	private static final double BASE_DAMAGE = 9.0;
	private static final float LANDING_DAMAGE = 7.0F;
	private static final double LANDING_RADIUS = 4.0;
	private static final double LANDING_KNOCKBACK = 1.4;

	/**
	 * How fast it moves doing each thing.
	 *
	 * <p>The spread between the slowest and the fastest is the whole character of the fifteen seconds.
	 * A stalk is slower than walking pace, which is what makes it look like it has decided something; a
	 * lunge is three times a sprint, which is what makes the decision matter.
	 */
	private static final double STALK_SPEED = 0.17;
	private static final double STRAFE_SPEED = 0.30;
	private static final double WITHDRAW_SPEED = 0.52;
	private static final double RUSH_SPEED = 0.62;
	private static final double RECOIL_SPEED = 0.42;
	private static final double SWOOP_SPEED = 0.55;
	private static final double LUNGE_SPEED = 0.85;

	/** How fast it closes the gap to the height it wants, as a share of that gap per tick, and a cap. */
	private static final double CLIMB_RATE = 0.35;
	private static final double CLIMB_MAX = 0.9;

	/**
	 * How close it will come while stalking, and stop.
	 *
	 * <p>A hair outside its own swing. Without this it walks into whoever it is fighting and stays
	 * there - it does not collide, so there is nothing to stop it - and a thing standing inside you is
	 * not menacing, it is stuck.
	 */
	private static final double STANDOFF = 2.9;

	/** The circle it prefers to hold while sidling. */
	private static final double STRAFE_RADIUS = 5.5;

	/** How far out it teleports to, how far it runs, and how far it backs off before a lunge. */
	private static final double BLINK_MIN = 3.0;
	private static final double BLINK_MAX = 7.0;
	private static final double WITHDRAW_DISTANCE = 12.0;
	private static final double RECOIL_DISTANCE = 7.0;

	/**
	 * How often an arrow simply does not arrive.
	 *
	 * <p>Half. The witness refuses projectiles outright, on the grounds that a boss that cannot follow
	 * an arrow home turns its fight into a walk backwards. This one is reachable for fifteen seconds in
	 * every eighty, so refusing the bow entirely would be taking away most of what a ranged player has
	 * - but landing every shot from across the arena would make those fifteen seconds free. Half is the
	 * answer to both: the bow works, and it is the worse of the two options.
	 *
	 * <p>It is a <b>dodge</b> rather than a miss. Nothing bounces off it and nothing is absorbed - it
	 * is somewhere else by the time the arrow gets there, which is a different sentence to read.
	 */
	private static final float DODGE_CHANCE = 0.5F;

	/** How much of a fresh sample is folded into the drift each tick. See {@link #trackDrift}. */
	private static final double DRIFT_SMOOTHING = 0.35;

	/** How high a swoop goes over their heads. */
	private static final double SWOOP_HEIGHT = 7.0;

	public enum Phase {
		/** Hanging over the middle, doing nothing, for thirty seconds. */
		INTRO,
		/** Twenty blocks up, untouchable, for sixty-four. */
		SURVIVAL,
		/** On the ground, reachable, for fifteen. */
		PAUSE,
		/** Going up, and not coming back. */
		DYING
	}

	/**
	 * What it is doing this second of the pause.
	 *
	 * <p><b>None of these is standing still.</b> That is a rule rather than an accident: a boss that
	 * stops moving is a boss being read, and fifteen seconds is short enough that any of it spent as a
	 * statue is most of what the player remembers. Even the moves whose <i>point</i> is distance -
	 * withdrawing, recoiling - sidle while they wait, because something circling at range is a thing
	 * choosing its moment and something standing at range is a target.
	 *
	 * <p>Two sequences are fixed and everything else is rolled. A recoil always becomes a lunge, and a
	 * withdraw always becomes a rush. Those are the only two things about the fifteen seconds that can
	 * be learned, and they are learnable on purpose - a player who works out that it backing off means
	 * it is about to come has learned something true, which is worth more than one more thing that
	 * cannot be predicted.
	 */
	private enum Move {
		/** Coming on slowly, and stopping at arm's length instead of walking into them. */
		STALK,
		/** Sideways, around them, holding its circle. */
		STRAFE,
		/** Away, fast, out past anything they can reach. Always followed by a rush. */
		WITHDRAW,
		/** And back in, fast. Only ever out of a withdraw. */
		RUSH,
		/** Up, over their heads, and down again hard enough to move whoever was underneath. */
		SWOOP,
		/** A short step back, which always means a lunge is next. */
		RECOIL,
		/** The lunge. Only ever out of a recoil. */
		LUNGE
	}

	/** How long each move runs before another is rolled, in ticks. */
	private static final int STALK_TICKS = 40;
	private static final int STRAFE_TICKS = 34;
	private static final int WITHDRAW_TICKS = 26;
	private static final int RUSH_TICKS = 30;
	private static final int SWOOP_TICKS = 45;
	private static final int RECOIL_TICKS = 20;
	private static final int LUNGE_TICKS = 16;

	/** One move in this many opens with it simply not being where it was. See {@link #pickMove}. */
	private static final int BLINK_ONE_IN = 6;

	private Phase phase = Phase.INTRO;
	private int phaseTicks;
	private int deathTicks;

	/**
	 * Bumped at every seam. The clients act on a change of this and ignore every repeat of it, which
	 * is what lets the heartbeat be sent twice a second without ever restarting the music.
	 */
	private int cue;

	/** What the clients should currently be playing, re-stated by every heartbeat. */
	private TheEntityPayload.Track track = TheEntityPayload.Track.NONE;

	/** Who is currently being told any of this, so they can be told when it stops. */
	private final Set<UUID> affected = new HashSet<>();

	/** What the attacks have in the air. Ticked here, and dropped at every seam. See {@link Ongoing}. */
	private final List<Ongoing> running = new ArrayList<>();

	/** Where everybody was last tick, and how fast they have been going. See {@link #trackDrift}. */
	private final Map<UUID, Vec3d> wasAt = new HashMap<>();
	private final Map<UUID, Vec3d> drift = new HashMap<>();

	private Move move = Move.STALK;
	private int moveTicks;
	private int punchCooldown;

	/** Which way round it is currently sidling: 1 or -1. Re-rolled with every move. */
	private int strafeSpin = 1;

	/** Where it was last tick, so the walk animation can be driven off how far it actually went. */
	private double lastX;
	private double lastZ;
	private boolean hasLast;

	/** Where a swoop is headed, fixed when it starts so it flies past rather than following them down. */
	private Vec3d swoopTo = Vec3d.ZERO;

	public TheEntity(EntityType<? extends TheEntity> type, World world) {
		super(type, world);

		// Everything it does is driven from tick() below. Vanilla's goals never get a turn, in any
		// phase - a boss that can be pathed into a corner is a boss that has been solved.
		this.setAiDisabled(true);
		this.setPersistent();
		this.setNoGravity(true);

		// Nothing is dropped by the ordinary death path, because it never takes the ordinary death
		// path. What it is worth is handed out over the ten seconds it spends going up - see tickDying.
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, BASE_DAMAGE)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.0)
				// It can be moved, but not by being hit. Knocking it back out of its own pause would turn
				// the fifteen seconds it is reachable into fifteen seconds of it not being.
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, REACH);
	}

	/** Puts a fresh one over the middle of the disc, at full health, at the top of the intro. */
	public void begin() {
		this.refreshPositionAndAngles(CENTRE_X, INTRO_Y, CENTRE_Z, 0.0F, SKY_PITCH);
		this.setHealth(MAX_HEALTH);
		this.deathTicks = 0;
		faceSky();
		enter(Phase.INTRO, TheEntityPayload.Track.START, false);
	}

	public Phase getPhase() {
		return this.phase;
	}

	/**
	 * Ends the phase it is in now and starts the next one, blackout and all.
	 *
	 * <p>For {@code /blank entity skip}, and for nothing else. A loop whose shortest lap is a minute
	 * and a half cannot be tuned by playing it.
	 */
	public void skipPhase() {
		switch (this.phase) {
			case INTRO, PAUSE -> enter(Phase.SURVIVAL, TheEntityPayload.Track.FIGHT, true);
			case SURVIVAL -> enter(Phase.PAUSE, TheEntityPayload.Track.pause(this.random.nextInt(3)), true);
			case DYING -> {
			}
		}
	}

	// --- the clock ------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		swingLegs();

		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}

		this.phaseTicks++;

		switch (this.phase) {
			case INTRO -> tickIntro();
			case SURVIVAL -> tickSurvival(world);
			case PAUSE -> tickPause(world);
			case DYING -> tickDying(world);
		}

		tickRunning(world);

		if (!this.isRemoved()) {
			sync(world);
		}
	}

	private void tickIntro() {
		hold(CENTRE_X, INTRO_Y, CENTRE_Z);
		faceSky();

		if (this.phaseTicks >= INTRO_TICKS) {
			enter(Phase.SURVIVAL, TheEntityPayload.Track.FIGHT, true);
		}
	}

	private void tickSurvival(ServerWorld world) {
		hold(CENTRE_X, SURVIVAL_Y, CENTRE_Z);

		// Still looking up, and turning on the spot while it does. Everything landing on the disc comes
		// out of the sky it is looking at, so it never once looks at any of the people it lands on.
		faceSky();
		float yaw = MathHelper.wrapDegrees(this.getYaw() + SURVIVAL_SPIN);
		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);

		List<ServerPlayerEntity> targets = livingTargets(world);
		trackDrift(targets);

		if (!targets.isEmpty()) {
			float progress = MathHelper.clamp((float) this.phaseTicks / SURVIVAL_TICKS, 0.0F, 1.0F);

			for (Attack attack : Attacks.SURVIVAL) {
				if (attack.available(this) && attack.due(this.random, this.phaseTicks, progress)) {
					attack.run(this, world, targets, progress);
				}
			}
		}

		if (this.phaseTicks >= SURVIVAL_TICKS) {
			enter(Phase.PAUSE, TheEntityPayload.Track.pause(this.random.nextInt(3)), true);
		}
	}

	private void tickDying(ServerWorld world) {
		this.deathTicks++;
		this.setVelocity(Vec3d.ZERO);
		this.setPosition(this.getX(), this.getY() + DEATH_RISE, this.getZ());
		faceSky();

		TheEntityFight.BAR.setPercent(Math.max(0.0F, 1.0F - (float) this.deathTicks / DEATH_TICKS));

		if (this.deathTicks == 1) {
			// The one borrowed sound in the fight, and it is borrowed deliberately: everybody who has
			// killed a dragon knows what it means the first time they hear it in here.
			for (ServerPlayerEntity player : world.getPlayers()) {
				player.playSoundToPlayer(SoundEvents.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.HOSTILE, 1.0F, 0.8F);
			}
		}

		if (this.deathTicks % 4 == 0) {
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
					this.getX() + (this.random.nextDouble() - 0.5) * 2.0,
					this.getY() + this.random.nextDouble() * 2.0,
					this.getZ() + (this.random.nextDouble() - 0.5) * 2.0,
					1, 0.0, 0.0, 0.0, 0.0);
		}

		// Dropped over the whole ten seconds rather than in one lump, and at the floor rather than at
		// its feet - by the end its feet are twenty blocks up and the orbs would spend the next minute
		// falling through the fight that is already over.
		if (this.deathTicks % XP_EVERY == 0) {
			ExperienceOrbEntity.spawn(world, new Vec3d(CENTRE_X, GROUND_Y, CENTRE_Z),
					DEATH_XP / (DEATH_TICKS / XP_EVERY));
		}

		if (this.deathTicks >= DEATH_TICKS) {
			// The way off the disc. The ring of bedrock, the sound and the blocks all belong to the gate,
			// so that this and {@code /blank exit} open exactly the same door.
			TempleGate.openExit(world);

			// Nothing else takes its place while the people who killed it are standing there. The disc
			// has to be left empty before there is another one - see TheEntityFight.
			TheEntityFight.beaten();

			// One of the two real endings, and the reason this is a call rather than something done in
			// remove(): the bar has to come down for the winners, who are still standing there.
			TheEntityFight.end(world.getServer());
			this.discard();
		}
	}

	/**
	 * Moves to the next phase: the screen goes black for everybody, a switch is thrown, and by the
	 * time there is anything to see again the thing is somewhere else.
	 *
	 * <p>The move happens <i>inside</i> the black rather than before or after it, which is the whole
	 * reason the black is there. Nothing in this fight is ever seen travelling between the ground and
	 * twenty blocks up.
	 */
	private void enter(Phase next, TheEntityPayload.Track music, boolean blackout) {
		this.phase = next;
		this.phaseTicks = 0;
		this.track = music;
		this.cue++;

		clearRunning();

		switch (next) {
			case INTRO -> hold(CENTRE_X, INTRO_Y, CENTRE_Z);
			case SURVIVAL -> hold(CENTRE_X, SURVIVAL_Y, CENTRE_Z);
			case PAUSE -> {
				hold(CENTRE_X, GROUND_Y, CENTRE_Z);

				// It arrives out of the black already walking. The first thing it does on the ground is
				// come at whoever is nearest, slowly, which is the only part of the fifteen seconds that
				// is the same every time.
				begin(Move.STALK, STALK_TICKS);
				this.punchCooldown = 0;
			}
			case DYING -> {
			}
		}

		if (this.getWorld() instanceof ServerWorld world) {
			int black = blackout ? BLACKOUT_TICKS : 0;

			for (ServerPlayerEntity player : world.getPlayers()) {
				ServerPlayNetworking.send(player,
						new TheEntityPayload(this.cue, music.ordinal(), black, HOLD_TICKS));

				if (blackout) {
					// At their own ears, so it is the same flick of the same switch however far apart two
					// players are standing. It is not a thing happening somewhere on the disc.
					player.playSoundToPlayer(SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.HOSTILE, 1.0F, 0.55F);
				}
			}
		}
	}

	// --- the fifteen seconds --------------------------------------------------

	private void tickPause(ServerWorld world) {
		if (this.punchCooldown > 0) {
			this.punchCooldown--;
		}

		PlayerEntity target = nearest(world);

		if (target == null) {
			// Nobody left to fight. It waits where it is; the phase runs out on its own, and the fight
			// takes it away entirely once the disc is empty.
			hold(this.getX(), GROUND_Y, this.getZ());
		} else {
			faceEntity(target);

			if (--this.moveTicks <= 0) {
				pickMove(target);
			}

			switch (this.move) {
				case STALK -> tickStalk(target);
				case STRAFE -> strafeAround(target, STRAFE_SPEED);
				case WITHDRAW -> tickWithdraw(target);
				case RUSH -> tickRush(target);
				case SWOOP -> tickSwoop(world);
				case RECOIL -> tickRecoil(target);
				case LUNGE -> driveTowards(target.getPos(), LUNGE_SPEED, GROUND_Y);
			}

			punchIfClose(target);
		}

		TheEntityFight.BAR.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));

		if (this.phaseTicks >= PAUSE_TICKS) {
			enter(Phase.SURVIVAL, TheEntityPayload.Track.FIGHT, true);
		}
	}

	/**
	 * Rolls the next move.
	 *
	 * <p>A recoil is never rolled <i>into</i> a lunge by chance - it always becomes one, and a lunge
	 * only ever comes out of a recoil. That is the single readable sequence in the whole fifteen
	 * seconds, and it is readable on purpose: a player who learns that it backing off means it is
	 * about to come has learned something that is actually true, which is worth more here than one
	 * more thing that cannot be predicted.
	 */
	private void pickMove(PlayerEntity target) {
		// Which way round it sidles, re-rolled every time, so it never circles the same way twice
		// running and cannot be led.
		this.strafeSpin = this.random.nextBoolean() ? 1 : -1;

		// The two fixed sequences. See the note on Move.
		if (this.move == Move.RECOIL) {
			begin(Move.LUNGE, LUNGE_TICKS);
			this.swingHand(Hand.MAIN_HAND);
			return;
		}

		if (this.move == Move.WITHDRAW) {
			begin(Move.RUSH, RUSH_TICKS);
			return;
		}

		// Coming out of a rush it is most likely to break off and do the whole thing again, which is
		// where the away-and-back rhythm comes from. It cannot do it for ever: the other half of the
		// roll ends the loop, so a player who has started reading it as a pattern is wrong about that
		// as well.
		if (this.move == Move.RUSH) {
			int after = this.random.nextInt(100);

			if (after < 45) {
				begin(Move.WITHDRAW, WITHDRAW_TICKS);
			} else if (after < 75) {
				begin(Move.STRAFE, STRAFE_TICKS);
			} else {
				begin(Move.STALK, STALK_TICKS);
			}

			return;
		}

		// Otherwise, once in a while, it simply is not where it was any more. This is a reposition and
		// not a state - whatever is rolled below happens from the new spot, starting the same tick, so
		// a teleport never costs it a moment of standing about.
		if (this.random.nextInt(BLINK_ONE_IN) == 0) {
			blink(target);
		}

		int roll = this.random.nextInt(100);

		if (roll < 22) {
			begin(Move.STALK, STALK_TICKS);
		} else if (roll < 46) {
			begin(Move.STRAFE, STRAFE_TICKS);
		} else if (roll < 66) {
			begin(Move.WITHDRAW, WITHDRAW_TICKS);
		} else if (roll < 84) {
			begin(Move.SWOOP, SWOOP_TICKS);

			// Fixed now, and past them rather than at them, so it flies over and lands beyond. Following
			// them down would make this a homing missile with a wind-up, which is a different animal and
			// a worse one - a swoop can be stepped out of, and it is meant to be.
			Vec3d along = target.getPos().subtract(this.getPos()).multiply(1.0, 0.0, 1.0);
			this.swoopTo = onDisc(along.lengthSquared() < 1.0E-4
					? target.getPos()
					: target.getPos().add(along.normalize().multiply(3.0)));
		} else {
			begin(Move.RECOIL, RECOIL_TICKS);
		}
	}

	private void begin(Move next, int ticks) {
		this.move = next;
		this.moveTicks = ticks;
	}

	/**
	 * Slowly in, and then not into them.
	 *
	 * <p>Past the standoff it comes on at less than walking pace. Inside it, it sidles instead of
	 * pressing, which is the whole difference between a thing closing on you and a thing that has
	 * arrived and run out of ideas.
	 */
	private void tickStalk(PlayerEntity target) {
		if (Math.sqrt(this.squaredDistanceTo(target)) > STANDOFF) {
			driveTowards(target.getPos(), STALK_SPEED, GROUND_Y);
		} else {
			strafeAround(target, STALK_SPEED);
		}
	}

	/**
	 * Sideways around them, holding its circle.
	 *
	 * <p>Two components at once: around, which is the movement, and a correction in or out, which is
	 * what stops it spiralling away over fifteen seconds of tangents. This is also what every other
	 * move falls back on when it has got where it was going - see the rule about never standing still.
	 */
	private void strafeAround(PlayerEntity target, double speed) {
		Vec3d out = this.getPos().subtract(target.getPos()).multiply(1.0, 0.0, 1.0);

		if (out.lengthSquared() < 1.0E-4) {
			out = new Vec3d(1.0, 0.0, 0.0);
		}

		double distance = out.length();
		Vec3d radial = out.normalize();
		Vec3d around = new Vec3d(-radial.z, 0.0, radial.x).multiply(this.strafeSpin);

		// Eased rather than snapped, so it curves back onto its circle instead of turning a corner.
		double correction = MathHelper.clamp(distance - STRAFE_RADIUS, -1.5, 1.5) * 0.35;

		driveTowards(this.getPos().add(around.subtract(radial.multiply(correction))), speed, GROUND_Y);
	}

	/** Away, fast, and circling once it is out - a thing that stops dead at range is a thing to aim at. */
	private void tickWithdraw(PlayerEntity target) {
		Vec3d away = this.getPos().subtract(target.getPos()).multiply(1.0, 0.0, 1.0);

		if (away.lengthSquared() < 1.0E-4) {
			away = new Vec3d(1.0, 0.0, 0.0);
		}

		if (away.length() < WITHDRAW_DISTANCE) {
			Vec3d spot = target.getPos().add(away.normalize().multiply(WITHDRAW_DISTANCE));
			driveTowards(onDisc(spot), WITHDRAW_SPEED, GROUND_Y);
		} else {
			strafeAround(target, STRAFE_SPEED);
		}
	}

	/** And back in. Slows to a sidle at the standoff rather than piling into them. */
	private void tickRush(PlayerEntity target) {
		double distance = Math.sqrt(this.squaredDistanceTo(target));

		if (distance > STANDOFF) {
			driveTowards(target.getPos(), RUSH_SPEED, GROUND_Y);
		} else {
			strafeAround(target, STRAFE_SPEED);
		}
	}

	/** Up, across, and down: three equal thirds of the same move. */
	private void tickSwoop(ServerWorld world) {
		int elapsed = SWOOP_TICKS - this.moveTicks;

		if (elapsed < SWOOP_TICKS / 3) {
			driveTowards(this.getPos(), 0.0, GROUND_Y + SWOOP_HEIGHT);
		} else if (elapsed < SWOOP_TICKS * 2 / 3) {
			driveTowards(this.swoopTo, SWOOP_SPEED, GROUND_Y + SWOOP_HEIGHT);
		} else {
			driveTowards(this.swoopTo, SWOOP_SPEED * 0.5, GROUND_Y);

			if (this.moveTicks == 1) {
				land(world);
			}
		}
	}

	/**
	 * Backing away, and then a slow sidle at the edge of its reach.
	 *
	 * <p>The sidle is the tell. It used to be a full stop, which read better and broke the one rule
	 * this phase has - see the note on {@link Move} - so what marks the wind-up now is the <i>change of
	 * pace</i> rather than the absence of one: a fast step back that turns into a crawl is the last
	 * thing that happens before it comes. A thing still walking backwards when it lunges has not told
	 * anybody anything.
	 */
	private void tickRecoil(PlayerEntity target) {
		Vec3d away = this.getPos().subtract(target.getPos()).multiply(1.0, 0.0, 1.0);

		if (away.lengthSquared() < 1.0E-4) {
			away = new Vec3d(1.0, 0.0, 0.0);
		}

		if (Math.sqrt(this.squaredDistanceTo(target)) < RECOIL_DISTANCE) {
			driveTowards(target.getPos().add(away.normalize().multiply(RECOIL_DISTANCE)),
					RECOIL_SPEED, GROUND_Y);
		} else {
			strafeAround(target, STALK_SPEED * 0.8);
		}
	}

	/** Somewhere else around whoever it is fighting, before they have finished turning round. */
	private void blink(PlayerEntity target) {
		double angle = this.random.nextDouble() * Math.PI * 2.0;
		double distance = MathHelper.lerp(this.random.nextDouble(), BLINK_MIN, BLINK_MAX);

		double x = target.getX() + Math.cos(angle) * distance;
		double z = target.getZ() + Math.sin(angle) * distance;

		// Never off the edge. There is nothing under the disc, and a boss that has teleported into the
		// void is a fight that has ended by accident. A roll that lands outside simply does not happen -
		// it stands where it was, which from the outside is one more unreadable thing it did.
		if (!TheBlank.insideDisc(MathHelper.floor(x), MathHelper.floor(z))) {
			return;
		}

		puff();
		this.requestTeleport(x, GROUND_Y, z);
		this.setVelocity(Vec3d.ZERO);
		faceEntity(target);
		puff();

		// Silent, deliberately. There was an enderman's teleport here and it was the wrong sound in the
		// most specific way: it is one of the half dozen noises in the game every player can name
		// instantly, so the one thing in the dimension that has never been explained was announcing
		// itself as something they already knew. The blank figure is silent everywhere else in the mod
		// and it is silent here.
	}

	/** The end of a swoop: everybody underneath is thrown off it. */
	private void land(ServerWorld world) {
		world.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), GROUND_Y, this.getZ(), 6, 1.2, 0.1, 1.2, 0.0);
		world.playSound(null, this.getX(), GROUND_Y, this.getZ(),
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.0F, 1.4F);

		DamageSource source = this.getDamageSources().mobAttack(this);

		for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,
				this.getBoundingBox().expand(LANDING_RADIUS),
				p -> !p.isSpectator() && p.isAlive())) {
			double dx = player.getX() - this.getX();
			double dz = player.getZ() - this.getZ();

			// The query box is square and the landing is not. Being hit by something you were
			// demonstrably clear of is the one failure that turns a dodge back into a tax.
			if (dx * dx + dz * dz > LANDING_RADIUS * LANDING_RADIUS) {
				continue;
			}

			player.damage(source, LANDING_DAMAGE);

			// Vanilla's knockback takes the direction of the push and applies the opposite, so what goes
			// in is the middle of the landing as seen from the player.
			player.takeKnockback(LANDING_KNOCKBACK, -dx, -dz);
			player.velocityModified = true;
		}
	}

	private void punchIfClose(PlayerEntity target) {
		if (this.punchCooldown > 0 || this.squaredDistanceTo(target) > PUNCH_RANGE * PUNCH_RANGE) {
			return;
		}

		this.punchCooldown = PUNCH_COOLDOWN;
		this.swingHand(Hand.MAIN_HAND);
		this.tryAttack(target);
	}

	// --- moving ---------------------------------------------------------------

	/**
	 * Drives it at a point on the ground and at a height, both at once.
	 *
	 * <h2>Why this writes the position instead of setting a velocity</h2>
	 *
	 * <p>Because velocity does not work on this thing, and finding that out cost a whole pause phase of
	 * a boss that teleported, punched, and never took a step.
	 *
	 * <p>The constructor disables AI. {@code MobEntity.isImmobile()} reports true whenever AI is off,
	 * and {@code LivingEntity} then skips its movement step - so a velocity set on this entity is
	 * simply never spent. It is not slowed or damped, it sits there: measured over a hundred ticks the
	 * velocity was still exactly what it had been set to and the entity had not moved a single block.
	 * {@link SilhouetteEntity} and {@link WandererEntity} both say so in as many words, and both move
	 * by writing the position, which is what this now does too.
	 *
	 * <p>Nothing is lost by it. Nothing in this fight is pathfound and there is nothing to path around
	 * - the disc is one flat circle with nothing standing on it - so collision would have had nothing
	 * to resolve, and writing the position outright is what makes movement that reads as deliberate
	 * rather than as a mob walking. In particular it can be told to hold perfectly still, and it can be
	 * told to stop exactly at arm's length, neither of which a velocity ever quite manages.
	 */
	private void driveTowards(Vec3d spot, double speed, double height) {
		double dx = spot.x - this.getX();
		double dz = spot.z - this.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);

		double x = this.getX();
		double z = this.getZ();

		if (speed > 0.0 && flat > 1.0E-4) {
			// Never past the thing it is heading for, so arriving is arriving rather than an overshoot
			// and a correction every tick once it is close.
			double step = Math.min(speed, flat);

			x += dx / flat * step;
			z += dz / flat * step;
		}

		double y = this.getY() + MathHelper.clamp((height - this.getY()) * CLIMB_RATE, -CLIMB_MAX, CLIMB_MAX);

		// Never off the edge, for the same reason a blink is not allowed to land there: the disc has
		// nothing under it, and a boss that has walked into the void is a fight that ended by accident.
		Vec3d next = onDisc(new Vec3d(x, y, z));

		this.setPosition(next.x, next.y, next.z);
		this.setVelocity(Vec3d.ZERO);
	}

	/**
	 * The same point, pulled back inside the circle if it was outside it.
	 *
	 * <p>A swoop is aimed three blocks past whoever it is aimed at, and somebody standing on the rim
	 * is three blocks from the edge of the world. Without this, chasing a player to the edge sends the
	 * thing out over the void - where it does not fall, because it has no gravity, and simply hangs
	 * there being unreachable for the rest of the pause.
	 */
	private Vec3d onDisc(Vec3d spot) {
		double distance = Math.sqrt(spot.x * spot.x + spot.z * spot.z);
		double limit = TheBlank.RADIUS - 2.0;

		if (distance <= limit || distance < 1.0E-4) {
			return spot;
		}

		double share = limit / distance;
		return new Vec3d(spot.x * share, spot.y, spot.z * share);
	}

	/**
	 * Makes the legs swing.
	 *
	 * <p>The other half of moving by writing the position. Vanilla drives the walk animation from
	 * inside the movement step this entity never runs, so without this it slides around the arena in a
	 * standing pose - which is its own kind of "not moving", and arguably a worse one than not moving
	 * at all.
	 *
	 * <p>Measured from how far it actually went rather than from a velocity it never has, and run on
	 * both sides: the server moves it outright, the client sees the same movement arrive through the
	 * entity tracker, and the legs swing in both places. The same trick, for the same reason, as
	 * {@link WandererEntity}.
	 */
	private void swingLegs() {
		if (this.hasLast) {
			double dx = this.getX() - this.lastX;
			double dz = this.getZ() - this.lastZ;

			this.updateLimbs((float) Math.sqrt(dx * dx + dz * dz));
		}

		this.lastX = this.getX();
		this.lastZ = this.getZ();
		this.hasLast = true;
	}

	/** Nailed to one spot, with nothing left over to drift on. */
	private void hold(double x, double y, double z) {
		this.setVelocity(Vec3d.ZERO);
		this.setPosition(x, y, z);
	}

	private void faceSky() {
		this.setPitch(SKY_PITCH);
		this.prevPitch = SKY_PITCH;
	}

	private void faceEntity(PlayerEntity target) {
		double dx = target.getX() - this.getX();
		double dz = target.getZ() - this.getZ();
		double dy = target.getEyeY() - this.getEyeY();
		double flat = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;

		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.setPitch((float) (-(MathHelper.atan2(dy, flat) * MathHelper.DEGREES_PER_RADIAN)));
	}

	private void puff() {
		if (this.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.END_ROD,
					this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.3, 0.8, 0.3, 0.02);
		}
	}

	// --- what is in the air ---------------------------------------------------

	/**
	 * How fast everybody is actually moving, worked out by watching them.
	 *
	 * <p>A player's velocity cannot simply be read on the server. Their movement arrives as positions
	 * rather than as inputs, so {@code getVelocity} on somebody sprinting flat out is whatever gravity
	 * last did to them and nothing else - the number is real, it is just not the number anybody means
	 * by it. So this differences their position tick over tick instead, the same way the witness does.
	 *
	 * <p>Smoothed, because one tick of walking is a fifth of a block and the noise on a single sample
	 * is most of the sample. It is kept here rather than inside any one attack because every attack
	 * that throws something ahead of somebody wants it, and twenty of them each keeping their own copy
	 * would be twenty position maps ticking over the same players.
	 */
	private void trackDrift(List<ServerPlayerEntity> targets) {
		Set<UUID> here = new HashSet<>();

		for (ServerPlayerEntity player : targets) {
			UUID id = player.getUuid();
			here.add(id);

			Vec3d now = player.getPos();
			Vec3d before = this.wasAt.put(id, now);

			if (before == null) {
				continue;
			}

			Vec3d step = now.subtract(before).multiply(1.0, 0.0, 1.0);
			Vec3d smoothed = this.drift.getOrDefault(id, Vec3d.ZERO);
			this.drift.put(id, smoothed.add(step.subtract(smoothed).multiply(DRIFT_SMOOTHING)));
		}

		this.wasAt.keySet().retainAll(here);
		this.drift.keySet().retainAll(here);
	}

	/** How far this player moved last tick, smoothed, flat. Zero for anybody not being watched. */
	public Vec3d driftOf(PlayerEntity player) {
		return this.drift.getOrDefault(player.getUuid(), Vec3d.ZERO);
	}

	/**
	 * Hands the boss something an attack has started, to be ticked until it says it is finished.
	 *
	 * <p>This is the whole of what an attack can ask the boss for. It cannot end the phase, change the
	 * clock, or see the other attacks - which is what makes twenty of them able to share sixty-four
	 * seconds without any of them being written around the other nineteen.
	 */
	public void launch(Ongoing ongoing) {
		this.running.add(ongoing);
	}

	private void tickRunning(ServerWorld world) {
		for (Iterator<Ongoing> ongoing = this.running.iterator(); ongoing.hasNext(); ) {
			if (ongoing.next().tick(this, world)) {
				ongoing.remove();
			}
		}
	}

	/**
	 * Everything in the air goes out at every seam. The black is a clean break, and that includes
	 * taking back anything an attack had put in the world - see {@link Ongoing#cancel}. Without it a
	 * phase that ends mid-charge leaves a four-metre thing running across an empty arena.
	 */
	private void clearRunning() {
		if (this.getWorld() instanceof ServerWorld world) {
			for (Ongoing ongoing : this.running) {
				ongoing.cancel(world);
			}
		}

		this.running.clear();
	}

	// --- who is here ----------------------------------------------------------

	/**
	 * Everybody the fight is actually happening to.
	 *
	 * <p>Creative is out as well as spectator, which is not the usual line to draw. Marks are laid on
	 * players and every one laid on somebody who cannot be hurt is one not laid on somebody who can -
	 * on a disc with one or two people on it, a single creative player would halve the fight for
	 * everyone else.
	 */
	private List<ServerPlayerEntity> livingTargets(ServerWorld world) {
		List<ServerPlayerEntity> here = new ArrayList<>();

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator() && !player.isCreative() && player.isAlive()) {
				here.add(player);
			}
		}

		return here;
	}

	private PlayerEntity nearest(ServerWorld world) {
		PlayerEntity closest = null;
		double best = REACH * REACH;

		for (ServerPlayerEntity player : livingTargets(world)) {
			double distance = this.squaredDistanceTo(player);

			if (distance < best) {
				best = distance;
				closest = player;
			}
		}

		return closest;
	}

	// --- telling the clients --------------------------------------------------

	/**
	 * The heartbeat, and the boss bar.
	 *
	 * <p>Everybody on the disc is in this fight - it is one circle seventy across with nothing else in
	 * it - so there is no distance test here, unlike the witness's. Being on the disc is the test.
	 *
	 * <p>The packet repeats the current cue, so a client that already has it does nothing with it
	 * except renew its countdown. A client that does <i>not</i> already have it - somebody who has just
	 * logged in, or come back through the temple mid-fight - acts on it, and picks the phase's track up
	 * from the top rather than sitting in silence until the next seam.
	 */
	private void sync(ServerWorld world) {
		boolean fighting = this.phase != Phase.INTRO;

		// Who has the bar is settled every tick, not on the heartbeat, and the reason is one player
		// dying while the others fight on. That is the moment the bar has to go from their screen, and
		// it has to go while they are still the player the server is holding - the instant they click
		// respawn they become a different object in a different world and this loop can no longer see
		// them at all. Half a second of lag on that is half a second of a boss bar over a death screen.
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (fighting && !player.isSpectator() && player.isAlive()) {
				TheEntityFight.BAR.addPlayer(player);
			} else {
				TheEntityFight.BAR.removePlayer(player);
			}
		}

		TheEntityFight.BAR.setVisible(fighting);

		if (this.age % HEARTBEAT_TICKS != 0) {
			return;
		}

		TheEntityPayload beat = new TheEntityPayload(this.cue, this.track.ordinal(), 0, HOLD_TICKS);
		Set<UUID> here = new HashSet<>();

		for (ServerPlayerEntity player : world.getPlayers()) {
			ServerPlayNetworking.send(player, beat);
			here.add(player.getUuid());
		}

		for (UUID id : new ArrayList<>(this.affected)) {
			if (here.contains(id)) {
				continue;
			}

			ServerPlayerEntity gone = world.getServer().getPlayerManager().getPlayer(id);

			if (gone != null) {
				ServerPlayNetworking.send(gone, TheEntityPayload.off());
				TheEntityFight.BAR.removePlayer(gone);
			}
		}

		this.affected.clear();
		this.affected.addAll(here);
	}

	/**
	 * Deliberately does almost nothing, and deliberately does not end the fight.
	 *
	 * <p>Two reasons, and both were bugs before they were reasons. It is not called on every way out -
	 * unloading a chunk goes through the final {@code setRemoved} instead - so anything that has to
	 * happen when a fight ends cannot live here or it will be skipped exactly when it matters most.
	 * And it <i>is</i> called on ways out that are not endings at all, such as the duplicate being
	 * discarded when two somehow exist, where tearing the bar and the music down would interrupt a
	 * fight that is still going on.
	 *
	 * <p>So the endings are {@link TheEntityFight#end}, called from the two places that are really
	 * endings. What is left here is the marks in the air, which belong to this object and to nothing
	 * else.
	 */
	@Override
	public void remove(RemovalReason reason) {
		if (!this.getWorld().isClient) {
			clearRunning();
			this.affected.clear();
		}

		super.remove(reason);
	}

	// --- damage ---------------------------------------------------------------

	/**
	 * Immortal everywhere except the fifteen seconds it spends on the ground, and even then the last
	 * blow does not kill it - it starts it going up.
	 *
	 * <p>Nothing is refused by <i>type</i>, unlike the witness, which throws arrows off itself outright.
	 * Half of them get through instead - see {@link #DODGE_CHANCE} and {@link #dodged}.
	 */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.phase != Phase.PAUSE) {
			return false;
		}

		if (dodged(source)) {
			return false;
		}

		if (amount < this.getHealth()) {
			return super.damage(source, amount);
		}

		// Absorbed here rather than handed down with the remainder taken off: a blow that would leave
		// exactly one health hands vanilla a damage of zero, and what vanilla does with a zero is not
		// worth depending on.
		this.setHealth(1.0F);
		this.hurtTime = this.maxHurtTime = 10;
		this.playSound(getHurtSound(source), this.getSoundVolume(), this.getSoundPitch());
		beginDeath();
		return true;
	}

	/**
	 * Starts it going up, and takes the music away on the way.
	 *
	 * <p>No blackout on this seam, alone among all of them. Every other one is the fight moving the
	 * thing while nobody is allowed to watch; this is the one time it goes somewhere in full view,
	 * which is the entire point of the ten seconds.
	 */
	public void beginDeath() {
		if (this.phase == Phase.DYING) {
			return;
		}

		this.phase = Phase.DYING;
		this.phaseTicks = 0;
		this.deathTicks = 0;
		this.cue++;
		this.track = TheEntityPayload.Track.NONE;
		clearRunning();

		this.setVelocity(Vec3d.ZERO);

		// Straight up through anything, and nothing is allowed to hold it up on the way.
		this.noClip = true;

		if (this.getWorld() instanceof ServerWorld world) {
			// Silence for the ten seconds it takes. There is nothing left to score.
			for (ServerPlayerEntity player : world.getPlayers()) {
				ServerPlayNetworking.send(player,
						new TheEntityPayload(this.cue, TheEntityPayload.Track.NONE.ordinal(), 0, HOLD_TICKS));
			}
		}
	}

	/**
	 * Whether this one simply did not arrive.
	 *
	 * <p>Only projectiles, and only half of them. What happens is not a block and not a miss: it steps
	 * out of the way, in the sense that it is standing somewhere else by the time the arrow reaches
	 * where it was. The dodge is the same {@link #blink} the pause uses to reposition, so an archer who
	 * lands a shot and an archer who does not are looking at the same event from opposite sides - one
	 * of them hit it, and one of them made it move.
	 *
	 * <p>The roll happens before the blink rather than after, so a dodge that would have landed it off
	 * the disc is still a dodge - see {@code blink}, which declines those. It stays where it was and
	 * takes nothing, which is a fraction of a percent of shots and not worth a second rule.
	 */
	private boolean dodged(DamageSource source) {
		if (!source.isIn(DamageTypeTags.IS_PROJECTILE) || this.random.nextFloat() >= DODGE_CHANCE) {
			return false;
		}

		if (!(this.getWorld() instanceof ServerWorld world)) {
			return false;
		}

		PlayerEntity nearest = nearest(world);

		if (nearest == null) {
			return false;
		}

		blink(nearest);
		return true;
	}

	/** It sounds like a player, for the same reason everything else in this dimension does. */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	/** Its lifetime is the fight's business, not the mob cap's. See {@link TheEntityFight}. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}

	// --- saving ---------------------------------------------------------------

	/**
	 * Never. Not once, not in any phase, not on a clean server stop.
	 *
	 * <p>This is the other half of the reset, and the half that is easy to get wrong. An Entity is a
	 * fight in progress, not a thing that lives on the disc, and a fight in progress is meaningless
	 * once there is nobody having it. Saved to the chunk, it comes back with the health and the phase
	 * it had when the last player died - which is not a boss that reset, it is a boss that was paused,
	 * and the next party through the temple walks into somebody else's half-finished fight.
	 *
	 * <p>Being unsaveable also means every way the arena can go quiet is the same way. The chunk
	 * unloading, the server stopping, the world being deleted out from under it - none of them leave
	 * anything behind, so {@link TheEntityFight} never has to tell the difference between an Entity
	 * that is missing and an Entity that is merely not loaded. There is only ever one answer: make a
	 * new one, from the top.
	 *
	 * <p>Nothing is lost by it. {@code TheEntityFight} puts a fresh one over the middle within a second
	 * of anybody standing on the disc.
	 */
	@Override
	public boolean shouldSave() {
		return false;
	}
}
