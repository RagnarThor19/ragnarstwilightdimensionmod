package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.ragnar.ragnarstwilightdimension.network.WitnessPayload;
import net.ragnar.ragnarstwilightdimension.network.WitnessPullPayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * It is watching something in the sky, and there is nothing in the sky.
 *
 * <p>Two and a half times a player and wearing that player's own skin, it stands perfectly still with
 * one arm raised, jabbing at a fixed point above the horizon every few seconds. It never turns, never
 * follows anyone with its eyes, and cannot be hurt or provoked - hit it as long as you like and it
 * goes on pointing. The dimension has no sky to speak of, which is the whole joke: whatever it is
 * pointing at is not merely far away, it is not rendered.
 *
 * <p><b>The fare is sixty-four of anything.</b> Hand it a full stack and it stops. The arm stays up
 * and the head comes down onto you, and for five seconds it does nothing else. Then the bar appears.
 *
 * <p>The fight is a slow one. It is tanky, it hits like a player in good gear, and it shoves anybody
 * who stands on top of it out into the fog. Three times - at three quarters, a half and a quarter of
 * its health - it stops fighting altogether, plants itself, and points at the sky again, and cannot
 * be touched while it does. Each of those costs the room some of its size: the fog closes from eleven
 * blocks to ten, to eight, to six, to five, so by the end you are fighting something you can only see
 * when it is already on you.
 *
 * <p>It also learns. From the halfway point its own blows are as hard as the hardest one it has been
 * hit with, so the better the gear brought to it, the worse the thing swinging back.
 *
 * <p>When it finally goes, it does not fall over. It stands for a moment with the arm still up, and
 * then it goes straight down into the ground at a walking pace until there is nothing above the
 * grass. No stagger and no body left lying in the field - it was there before the player was, and the
 * only thing that changes is that it is not there any more. What it leaves is left at the surface.
 *
 * @see net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity the blank one, which is the same
 *      idea from the other end: a copy with nothing on it yet, closing the distance to get a look
 */
public class WitnessEntity extends PathAwareEntity {
	// --- shape ----------------------------------------------------------------
	/** Exactly one and a half players. Tall enough to be wrong, short enough to be a person. */
	public static final float HEIGHT = 2.7F;

	/** The player model is built at 1.8 blocks, so this is what the renderer scales it by. */
	public static final float MODEL_SCALE = HEIGHT / 1.8F;

	public static final float WIDTH = 0.6F * MODEL_SCALE;

	// --- the fare -------------------------------------------------------------
	/**
	 * How many items it takes. Sixty-four, and never a configurable number: it is the count the whole
	 * dimension is built around, and the one thing a player can hand over that is worth something and
	 * costs nothing in particular.
	 */
	public static final int FARE = 64;

	/** How long it looks at you before the bar appears. Five seconds of nothing happening. */
	private static final int LOOK_TICKS = 100;

	// --- the fight ------------------------------------------------------------
	/** Four sixty-fours. */
	public static final float MAX_HEALTH = 256.0F;

	/** Its base walk. Raised twice from the 0.16 it started at, ten per cent each time. */
	private static final double SPEED = 0.1936;
	private static final double ARMOUR = 8.0;
	private static final double BASE_DAMAGE = 8.0;

	/** What its blows are worth against the hardest one it has taken, once it has started learning. */
	private static final double LEARNED_SHARE = 0.75;

	/**
	 * How long each of the three punctuations lasts, and it cannot be hurt for any of it.
	 *
	 * <p>Four seconds is a long time to be unable to do anything to something, which is why the point
	 * is not a pause: for all four the player's legs belong to it - see {@link #beginPoint} - and they
	 * spend the whole time being walked into range of what happens at the end.
	 */
	private static final int POINT_TICKS = 80;

	/** The legs are released just before the point ends, so the release cannot fight the blow. */
	private static final int PULL_TICKS = POINT_TICKS - 4;

	/**
	 * How far you can see while it is pointing, unless the fight is already worse than this - see
	 * {@link #fightFog}. Four blocks, for the whole time the player is being walked somewhere they
	 * cannot see.
	 */
	private static final float POINT_FOG = 4.0F;

	/** Ticks between shoves, per quarter. It gets less patient. */
	private static final int[] SHOVE_INTERVAL = {160, 140, 110, 80};

	/** Where the fog ends during each quarter. The room gets smaller as it loses. */
	private static final float[] FOG_END = {10.0F, 8.0F, 6.0F, 4.0F};

	/** How fast it moves per quarter, as a multiple of {@link #SPEED}. Slow, and less slow. */
	private static final double[] SPEED_SCALE = {1.0, 1.15, 1.3, 1.45};

	private static final double SHOVE_RADIUS = 5.0;

	/**
	 * How hard it shoves, in vanilla knockback. A sword's Knockback II is 1.0, so this is a good deal
	 * more than any hit in the game and lands the player several blocks out - which at six blocks of
	 * fog is far enough to lose sight of what did it.
	 */
	private static final double SHOVE_STRENGTH = 1.5;
	private static final float SHOVE_DAMAGE = 3.0F;

	/** How far away the bar, the music and the fog reach. */
	private static final double FIGHT_RANGE = 48.0;

	/** Stand further off than this and it starts getting better. Distance is not a weapon here. */
	private static final double ENGAGE_RANGE = 24.0;
	private static final float REGEN_PER_SECOND = 2.0F;

	// --- keeping your distance -------------------------------------------------
	/**
	 * The distance it will not let you settle at, and the distance it reappears at. Twelve blocks is
	 * just past the fog, so both ends of the trick are invisible: it is not there any more, and it is
	 * not anywhere you can see.
	 */
	private static final double REACH_DISTANCE = 12.0;

	/** How often it considers moving, and how likely it is to. Roughly once every six seconds of it. */
	private static final int MOVE_CHECK_TICKS = 40;
	private static final float MOVE_CHANCE = 0.35F;

	/** Bearings tried before it gives up and stays where it is for another two seconds. */
	private static final int MOVE_ATTEMPTS = 12;

	/** How far the landing spot has to be from anybody who is not the one it is chasing. */
	private static final double MOVE_CLEARANCE = 6.0;

	/** Past this it is not being kited, it is being left - so it does not follow. */
	private static final double MOVE_MAX_DISTANCE = 25.0;

	/** And past this it gives the whole thing up and goes back to the sky. */
	private static final double GIVE_UP_DISTANCE = 33.0;

	// --- the neighbours --------------------------------------------------------
	/** How many turn up at each of the last two punctuations, and how far out they stand. */
	private static final int WATCHERS_PER_POINT = 2;
	private static final double WATCHER_DISTANCE = 11.0;

	/** Leave it alone this long and it forgets the whole thing and goes back to the sky, whole. */
	private static final int FORGET_TICKS = 100;

	/** How long it takes to go: three seconds of pointing at nothing, then it goes under. */
	private static final int DYING_TICKS = 60;

	/** How long it stands there before it starts to go down. One second, with the arm still up. */
	private static final int SINK_HOLD_TICKS = 20;

	/**
	 * How far it goes down each tick once it has started, in blocks.
	 *
	 * <p>Worked out from the two above rather than typed, so it is always exactly buried by the time
	 * the three seconds are up however either of them is retuned.
	 *
	 * <p>The margin is measured against the arm and not against {@link #HEIGHT}. The hitbox stops at
	 * the top of its head, but the thing is pointing - the raised hand reaches something like four
	 * tenths of a block above that, and a sink that only cleared the hitbox would leave a finger
	 * sticking out of the field forever.
	 */
	private static final double SINK_PER_TICK = (HEIGHT + 1.0) / (DYING_TICKS - SINK_HOLD_TICKS);


	/** How far up it points, in degrees above the horizon. */
	private static final float SKY_PITCH = -55.0F;

	// --- state ----------------------------------------------------------------
	/** What it is doing. Tracked because the renderer needs it before anything else is asked for. */
	private static final TrackedData<Byte> STATE =
			DataTracker.registerData(WitnessEntity.class, TrackedDataHandlerRegistry.BYTE);

	/** Which way the arm is. Tracked for the same reason. */
	private static final TrackedData<Byte> POSE =
			DataTracker.registerData(WitnessEntity.class, TrackedDataHandlerRegistry.BYTE);

	public enum State {
		/** Standing in a field pointing at nothing, and no part of the world can change that. */
		WATCHING,
		/** Paid, and looking at whoever paid. Five seconds. */
		LOOKING,
		/** The fight. */
		FIGHTING,
		/** Losing, slowly, on purpose. */
		DYING
	}

	public enum ArmPose {
		/** Arm down. It is only fighting. */
		LOWERED,
		/** Arm up at the thing that is not there. */
		SKY,
		/** Arm out at you. It has stopped watching the sky, and that is worse. */
		PLAYER
	}

	private final ServerBossBar bar = new ServerBossBar(
			net.minecraft.text.Text.translatable("entity.ragnarstwilightdimension.witness"),
			BossBar.Color.WHITE, BossBar.Style.PROGRESS);

	/** The bearing it points along, fixed when it is placed and never revised. */
	private float watchYaw;

	private UUID challenger;
	private int stateTicks;
	private int pointTicks;
	private int shoveTicks;
	private int aloneTicks;
	private int moveTicks;
	private int quarter;
	private float hardestHit;

	/**
	 * The height it was standing at when it started to go, kept so that what it leaves behind can be
	 * left there rather than three blocks under. NaN until it starts dying.
	 */
	private double surfaceY = Double.NaN;
	private final Set<UUID> affected = new HashSet<>();
	private final List<SilhouetteEntity> watchers = new ArrayList<>();

	public WitnessEntity(EntityType<? extends WitnessEntity> type, World world) {
		super(type, world);
		this.setSilent(false);
		this.setAiDisabled(true);
		this.setPersistent();
		this.experiencePoints = 100;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STATE, (byte) State.WATCHING.ordinal());
		builder.add(POSE, (byte) ArmPose.SKY.ordinal());
	}

	@Override
	protected void initGoals() {
		// None of these run until the fare is paid - the constructor disables AI outright and only
		// beginFight turns it back on.
		this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, (float) FIGHT_RANGE));
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, BASE_DAMAGE)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
				.add(EntityAttributes.GENERIC_ARMOR, ARMOUR)
				// It is two and a half metres of something that has been standing in one spot for
				// years. Nothing the player can swing moves it.
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, FIGHT_RANGE);
	}

	// --- outside the fight ----------------------------------------------------

	/** Points it along a bearing and leaves it there. Called by the spawner and by {@code /witness}. */
	public void watchSky(float yaw) {
		this.watchYaw = yaw;
		setState(State.WATCHING);
		setArmPose(ArmPose.SKY);
		faceSky();
	}

	/**
	 * The fare. Sixty-four of anything, in one hand, and nothing else in the game will do it.
	 *
	 * <p>Deliberately not an attack. There is no way to start this fight by hitting the thing, because
	 * it does not know you are there until you give it something - see {@link #damage} - and a player
	 * who works that out has learned the only rule the dimension has ever told them.
	 */
	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		if (getState() != State.WATCHING || stack.getCount() < FARE) {
			return ActionResult.PASS;
		}

		if (this.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}

		if (!player.getAbilities().creativeMode) {
			stack.decrement(FARE);
		}

		beginLooking(player);
		return ActionResult.SUCCESS;
	}

	private void beginLooking(PlayerEntity player) {
		this.challenger = player.getUuid();
		this.stateTicks = 0;
		setState(State.LOOKING);

		// The arm stays where it is. It is pointing at the sky and looking at you, which is a worse
		// shape than either on its own.
		setArmPose(ArmPose.SKY);
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 1.5F, 0.5F);
	}

	// --- the fight ------------------------------------------------------------

	private void beginFight() {
		setState(State.FIGHTING);
		setArmPose(ArmPose.LOWERED);
		setAiDisabled(false);
		this.quarter = 0;
		this.shoveTicks = SHOVE_INTERVAL[0];
		this.aloneTicks = 0;
		applyQuarter();

		PlayerEntity player = challenger();
		if (player != null) {
			setTarget(player);
		}
	}

	/**
	 * Everything that changes when it loses another quarter: how often it shoves, how fast it walks,
	 * and how much of the world is left to see.
	 */
	private void applyQuarter() {
		EntityAttributeInstance speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(SPEED * SPEED_SCALE[this.quarter]);
		}

		// From halfway, it hits as hard as it has been hit. Nothing announces this. A player in
		// netherite works out that the thing has got worse and does not necessarily work out why.
		if (this.quarter >= 2) {
			EntityAttributeInstance damage = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
			if (damage != null) {
				damage.setBaseValue(Math.max(BASE_DAMAGE, this.hardestHit * LEARNED_SHARE));
			}
		}
	}

	/** Which quarter its current health falls in: 0 while above three quarters, 3 below one. */
	private int quarterFor(float health) {
		float fraction = health / this.getMaxHealth();

		if (fraction > 0.75F) {
			return 0;
		}
		if (fraction > 0.5F) {
			return 1;
		}
		if (fraction > 0.25F) {
			return 2;
		}
		return 3;
	}

	/**
	 * The punctuation, and the worst four seconds in the fight.
	 *
	 * <p>It stops fighting, squares up to the player, puts the arm back up at the sky and cannot be
	 * touched. What the player gets in exchange is that <b>their legs stop being theirs</b>: for the
	 * whole four seconds they walk towards it whatever they press, while the fog drops to four blocks
	 * so they cannot see what they are walking into. They can still look wherever they like, which is
	 * the part that makes it land - nothing is hidden from them, they simply cannot decline.
	 *
	 * <p>At the end of it, it hits whatever is now standing in front of it, and shoves the rest away.
	 * The blow is not dodgeable and is not meant to be: it is the price of the two seconds of free
	 * damage the player got before the point started, and the reason the punctuation is a threat
	 * rather than a rest.
	 *
	 * <p>This is the whole rhythm of the fight. Without it the thing is a slab of health; with it
	 * there are three moments where the player is walked into something while watching it happen.
	 */
	private void beginPoint() {
		this.pointTicks = POINT_TICKS;
		setArmPose(this.quarter >= 3 ? ArmPose.PLAYER : ArmPose.SKY);

		// Planted properly rather than merely told to stop. With the AI still running, the attack goal
		// keeps re-pathing underneath and the thing shuffles on the spot while it is meant to be a
		// statue; turning it off makes isImmobile true and nothing moves it at all.
		setAiDisabled(true);
		this.getNavigation().stop();
		this.setVelocity(Vec3d.ZERO);
		this.swingHand(Hand.MAIN_HAND);
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0F, 0.55F);

		pullEveryone(PULL_TICKS);
		callWatchers();
	}

	/**
	 * From the halfway point, they come to watch.
	 *
	 * <p>Two silhouettes at each of the last two punctuations, standing in the fog at the edge of what
	 * can be seen, facing the fight. They do not join in, they cannot be hurt, and they leave the
	 * instant it is over. They are the ordinary ones - the same entity that stands at the graves,
	 * given the same job of staring at one spot - so a player who has met those knows exactly what
	 * they are and exactly how much use it is to walk towards one.
	 *
	 * <p>Nothing in the fight depends on them. They are there because a thing that has been standing
	 * alone in a field for years turns out to have neighbours, and they only turn up once it starts
	 * losing.
	 */
	private void callWatchers() {
		if (this.quarter < 2 || !(this.getWorld() instanceof ServerWorld world)) {
			return;
		}

		for (int i = 0; i < WATCHERS_PER_POINT; i++) {
			double bearing = this.random.nextDouble() * Math.PI * 2.0;
			int x = MathHelper.floor(this.getX() + Math.cos(bearing) * WATCHER_DISTANCE);
			int z = MathHelper.floor(this.getZ() + Math.sin(bearing) * WATCHER_DISTANCE);

			BlockPos ground = WitnessSpawner.findGround(world, x, MathHelper.floor(this.getY()) + 4, z);
			if (ground == null) {
				continue;
			}

			SilhouetteEntity watcher = ModEntities.SILHOUETTE.create(world);
			if (watcher == null) {
				return;
			}

			BlockPos feet = ground.up();
			watcher.refreshPositionAndAngles(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);

			// The grave watcher's job, pointed at this instead of at a sign: stand still, stare at one
			// place, and leave if anybody gets close enough to look at it properly.
			watcher.watchGrave(this.getBlockPos().up(2));
			world.spawnEntity(watcher);
			this.watchers.add(watcher);
		}
	}

	/** Sends them away. Called on every path out of the fight, including the one it wins. */
	private void dismissWatchers() {
		for (SilhouetteEntity watcher : this.watchers) {
			if (!watcher.isRemoved()) {
				watcher.discard();
			}
		}

		this.watchers.clear();
	}

	private void endPoint() {
		// From the last quarter the arm stays out at the player. It has stopped watching the sky, and
		// there is only one other thing here to point at.
		setArmPose(this.quarter >= 3 ? ArmPose.PLAYER : ArmPose.LOWERED);
		setAiDisabled(false);
		pullEveryone(0);

		PlayerEntity player = challenger();
		if (player != null && player.isAlive()) {
			setTarget(player);
		}

		// No special blow at the end of it, and deliberately no shove either. The four seconds of
		// walking have put the player inside its reach, so the thing that happens next is simply its
		// ordinary attack landing - which is what the pull was for. Shoving here would throw them back
		// out before it could swing and undo the whole point.
		this.shoveTicks = SHOVE_INTERVAL[this.quarter];
		applyQuarter();
	}

	/** Hands everyone near the fight over to the pull, or hands their legs back when ticks is 0. */
	private void pullEveryone(int ticks) {
		WitnessPullPayload payload = ticks <= 0
				? WitnessPullPayload.release()
				: new WitnessPullPayload(this.getX(), this.getY(), this.getZ(), ticks);

		for (UUID id : this.affected) {
			ServerPlayerEntity player = playerById(id);

			if (player != null) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/**
	 * Puts everybody on the floor a few blocks further away.
	 *
	 * <p>Without this the fight is solved by standing inside it and holding down the button, which is
	 * how every slow melee boss in the game is solved. With it, the player is repeatedly put back out
	 * into fog they can no longer see through and has to find it again.
	 */
	private void shove(double strength) {
		Box area = this.getBoundingBox().expand(SHOVE_RADIUS);

		for (PlayerEntity player : this.getWorld().getEntitiesByClass(PlayerEntity.class, area,
				player -> !player.isSpectator() && player.isAlive())) {
			player.damage(this.getDamageSources().mobAttack(this), SHOVE_DAMAGE);

			// Through vanilla's own knockback rather than by writing a velocity, which matters for a
			// player: they are moved by their own client, and the client argues with a velocity that
			// did not arrive the way knockback normally does. takeKnockback is the path every sword in
			// the game already uses, so the shove travels the same way a hit does.
			//
			// Its arguments are the direction of whatever is doing the pushing, and it applies the
			// opposite - hence this entity's position rather than the player's.
			player.takeKnockback(strength, this.getX() - player.getX(), this.getZ() - player.getZ());
			player.velocityModified = true;
		}

		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 3.0F, 0.6F);
	}

	// --- ticking --------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient) {
			return;
		}

		this.stateTicks++;

		switch (getState()) {
			case WATCHING -> tickWatching();
			case LOOKING -> tickLooking();
			case FIGHTING -> tickFighting();
			case DYING -> tickDying();
		}
	}

	private void tickWatching() {
		faceSky();

		// The jab. Every couple of seconds, at nothing.
		if (this.random.nextInt(60) == 0) {
			this.swingHand(Hand.MAIN_HAND);
		}
	}

	private void tickLooking() {
		PlayerEntity player = challenger();

		if (player == null || player.isRemoved() || player.getWorld() != this.getWorld()) {
			// Whoever paid is gone. It goes back to the sky and keeps the stack.
			setState(State.WATCHING);
			syncEffects(false);
			return;
		}

		faceEntity(player);
		syncEffects(true);

		if (this.stateTicks >= LOOK_TICKS) {
			beginFight();
		}
	}

	private void tickFighting() {
		if (this.pointTicks > 0 && --this.pointTicks == 0) {
			endPoint();
		}

		if (this.pointTicks > 0) {
			// Planted. It does not move, does not swing and cannot be hurt - but it is squared up to
			// the player the whole time, with its face turned up past them. Being pointedly not looked
			// at by the thing dragging you towards it is the shape of the four seconds.
			this.getNavigation().stop();
			this.setVelocity(Vec3d.ZERO);
			facePlayerLookingUp();
		} else {
			if (--this.shoveTicks <= 0) {
				this.shoveTicks = SHOVE_INTERVAL[this.quarter];
				shove(SHOVE_STRENGTH);
			}

			tickDistance();
		}

		int now = quarterFor(this.getHealth());
		if (now > this.quarter) {
			this.quarter = now;
			beginPoint();
		}

		this.bar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
		syncEffects(true);
		tickCrowd();
	}

	/**
	 * The answer to being kept at arm's length.
	 *
	 * <p>Past {@link #REACH_DISTANCE} it starts rolling to move: not towards the player - it never
	 * closes with this - but to <i>somewhere else the same distance away</i>. The fight is unchanged
	 * by it and the player has lost nothing except the thing being where they left it, which is the
	 * point. Something slow that cannot be outrun is a different animal from something slow that can,
	 * and this is what turns backing away from a good idea into a way of losing track of it.
	 *
	 * <p>It never lands on top of anybody. See {@link #findSpotNear}.
	 */
	private void tickDistance() {
		if (--this.moveTicks > 0) {
			return;
		}

		this.moveTicks = MOVE_CHECK_TICKS;

		PlayerEntity player = this.getWorld().getClosestPlayer(this, FIGHT_RANGE);
		if (player == null) {
			return;
		}

		double distance = Math.sqrt(this.squaredDistanceTo(player));

		// Only in the band between the two. Nearer than REACH_DISTANCE it has no reason to move, and
		// further than MOVE_MAX_DISTANCE the player is not keeping their distance, they are leaving -
		// and a thing that follows somebody out of a fight they have decided to quit is a thing that
		// cannot be quit. It lets them go, and goes back to the sky on its own a little further out.
		if (distance < REACH_DISTANCE || distance > MOVE_MAX_DISTANCE) {
			return;
		}
		if (this.random.nextFloat() >= MOVE_CHANCE) {
			return;
		}

		Vec3d spot = findSpotNear(player);
		if (spot == null) {
			return;
		}

		// Silently. There is no noise at either end of it - the first the player knows is that the
		// thing is not where they left it, and they only find that out by looking.
		this.getNavigation().stop();
		this.refreshPositionAndAngles(spot.x, spot.y, spot.z, this.getYaw(), this.getPitch());
		faceEntity(player);
	}

	/**
	 * Somewhere else at the same distance from that player: a random bearing, the ground under it, and
	 * three rules.
	 *
	 * <p>It must be clear of blocks, it must not be where it already is, and - the one that matters -
	 * <b>it must not be near anybody else</b>. A thing that teleports on top of a second player is not
	 * a fight, it is an accident, and in a dimension where the fog is eleven blocks the second player
	 * would never see it coming.
	 */
	private Vec3d findSpotNear(PlayerEntity player) {
		for (int attempt = 0; attempt < MOVE_ATTEMPTS; attempt++) {
			double bearing = this.random.nextDouble() * Math.PI * 2.0;
			int x = MathHelper.floor(player.getX() + Math.cos(bearing) * REACH_DISTANCE);
			int z = MathHelper.floor(player.getZ() + Math.sin(bearing) * REACH_DISTANCE);

			BlockPos ground = WitnessSpawner.findGround(
					(ServerWorld) this.getWorld(), x, MathHelper.floor(player.getY()) + 4, z);

			if (ground == null) {
				continue;
			}

			Vec3d spot = Vec3d.ofBottomCenter(ground.up());
			if (spot.squaredDistanceTo(this.getPos()) < 64.0) {
				// Barely moved. Not worth the noise.
				continue;
			}

			Box landing = this.getType().getDimensions().getBoxAt(spot);
			if (!this.getWorld().isSpaceEmpty(this, landing)) {
				continue;
			}

			boolean occupied = false;
			for (PlayerEntity other : this.getWorld().getEntitiesByClass(PlayerEntity.class,
					landing.expand(MOVE_CLEARANCE), other -> !other.isSpectator())) {
				if (other != player) {
					occupied = true;
					break;
				}
			}

			if (!occupied) {
				return spot;
			}
		}

		return null;
	}

	/**
	 * Keeps everyone honest about being in the fight.
	 *
	 * <p>Standing off with a bow is not a strategy the dimension recognises: out past
	 * {@link #ENGAGE_RANGE} it starts putting itself back together, and if everybody leaves it
	 * entirely it forgets the whole thing, heals to full and goes back to watching the sky. There is
	 * no half-finished witness left standing in a field on ten health.
	 */
	private void tickCrowd() {
		PlayerEntity engaged = this.getWorld().getClosestPlayer(this, ENGAGE_RANGE);

		if (engaged == null && this.age % 10 == 0) {
			this.heal(REGEN_PER_SECOND / 2.0F);
		}

		// Thirty-three blocks is three times the fog. Nobody at that range is fighting it - they have
		// walked away, and it stops pretending otherwise.
		if (this.getWorld().getClosestPlayer(this, GIVE_UP_DISTANCE) == null) {
			if (++this.aloneTicks > FORGET_TICKS) {
				forget();
			}
		} else {
			this.aloneTicks = 0;
		}
	}

	private void forget() {
		setAiDisabled(true);
		setTarget(null);
		this.getNavigation().stop();
		this.setHealth(this.getMaxHealth());
		this.challenger = null;
		this.pointTicks = 0;
		this.quarter = 0;
		this.hardestHit = 0.0F;
		applyQuarter();
		dismissWatchers();

		this.bar.setVisible(false);
		this.bar.clearPlayers();
		syncEffects(false);

		watchSky(this.watchYaw);
	}

	/**
	 * Three seconds of dying, which it spends going into the ground.
	 *
	 * <p>It does not fall over. It stands for a second with the arm still up, and then it goes straight
	 * down, at a walking pace, until there is nothing above the grass - no stagger, no topple, no body
	 * left lying in the field. It was standing there before the player arrived and the only thing that
	 * changes is that it is not standing there any more.
	 *
	 * <p>Kept ticking rather than left to vanilla's death animation because vanilla's is a fall, and a
	 * fall is the one shape this must not have. What vanilla does get is the twenty ticks after
	 * {@link #onDeath}, by which point it is already under and nobody can see it.
	 */
	private void tickDying() {
		this.setVelocity(Vec3d.ZERO);
		faceSky();
		this.bar.setPercent(Math.max(0.0F, 1.0F - (float) this.stateTicks / DYING_TICKS));

		// The fight outlasts the expiry the packets carry, so the fog and the music have to keep being
		// re-stated through the whole of it or they lapse out from under the death.
		syncEffects(true);

		// Straight down, through whatever is in the way - noClip is set on the way into this state. It
		// stops once it is buried; the ticks after that belong to vanilla and happen out of sight.
		if (this.stateTicks > SINK_HOLD_TICKS && this.stateTicks <= DYING_TICKS) {
			this.setPosition(this.getX(), this.getY() - SINK_PER_TICK, this.getZ());
		}

		if (this.stateTicks >= DYING_TICKS) {
			this.bar.setVisible(false);
			this.bar.clearPlayers();

			// Killed through onDeath rather than through damage(), which refuses everything in this
			// state by design - this is the one path out and it is the last thing that happens.
			this.setHealth(0.0F);
			this.onDeath(this.getDamageSources().generic());
		}
	}

	// --- damage ---------------------------------------------------------------

	/**
	 * Nothing touches it that has not paid, nothing touches it while it is pointing, and the last blow
	 * does not kill it - it starts it dying.
	 */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getState() == State.WATCHING || getState() == State.DYING || this.pointTicks > 0) {
			return false;
		}

		// Nothing thrown at it counts. A bow turns this fight into a walk backwards - it is slow, it
		// is melee, and it cannot follow an arrow home - so the arrows come off it and the player is
		// told so by the sound. The trade is deliberate: everything it does is at arm's length, so
		// everything done to it has to be too.
		if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.8F, 0.55F);
			return false;
		}
		if (getState() == State.LOOKING) {
			// Hitting it during the five seconds does nothing at all. It is not ready.
			return false;
		}

		if (source.getAttacker() instanceof PlayerEntity) {
			this.hardestHit = Math.max(this.hardestHit, amount);
		}

		if (amount < this.getHealth()) {
			return super.damage(source, amount);
		}

		// The killing blow lands and it does not fall over. Absorbed here rather than passed down with
		// the remainder taken off, because a blow that leaves exactly one health would hand vanilla a
		// damage of zero, and what vanilla does with a zero is not worth depending on.
		this.setHealth(1.0F);
		this.hurtTime = this.maxHurtTime = 10;
		this.playSound(getHurtSound(source), this.getSoundVolume(), this.getSoundPitch());

		this.pointTicks = 0;
		pullEveryone(0);
		dismissWatchers();
		setState(State.DYING);
		setArmPose(ArmPose.SKY);
		setAiDisabled(true);
		setTarget(null);
		this.getNavigation().stop();
		this.swingHand(Hand.MAIN_HAND);

		// Where it was standing, which is where what it leaves behind belongs - see dropLoot. Taken here
		// rather than at the end because by then it is under the ground it is being measured against.
		this.surfaceY = this.getY();

		// It goes down through the world rather than into it, and nothing is allowed to hold it up on
		// the way: no collision, and no gravity to argue with the tick that is moving it.
		this.noClip = true;
		this.setNoGravity(true);
		return true;
	}

	/**
	 * What is left of it.
	 *
	 * <p>Eyes of ender, which is the only currency the dimension has - the fare in and the fare out,
	 * and otherwise only found by robbing the graves. Two to four of them is several crossings, which
	 * makes this the one thing in the twilight that can be <i>farmed</i>, except that it cannot: they
	 * do not come back, and each one costs another sixty-four to start.
	 *
	 * <p>Written straight out rather than through a loot table on purpose. There is no
	 * {@code entities/witness.json} to go looking for and nothing to accidentally inject into: what it
	 * drops is decided here, next to the reason.
	 *
	 * <p>Left at the surface, not at the body. By the time this runs the thing is three blocks under
	 * the field and dropping at its feet would bury the eyes in the ground with it - so the offset puts
	 * them back up at {@link #surfaceY}, where the player watched it go down.
	 */
	@Override
	protected void dropLoot(DamageSource source, boolean causedByPlayer) {
		float toSurface = Double.isNaN(this.surfaceY) ? 0.0F : (float) (this.surfaceY - this.getY());
		this.dropStack(new ItemStack(Items.ENDER_EYE, 2 + this.random.nextInt(3)), toSurface);
	}

	// --- facing ---------------------------------------------------------------

	private void faceSky() {
		this.setYaw(this.watchYaw);
		this.setBodyYaw(this.watchYaw);
		this.setHeadYaw(this.watchYaw);
		this.setPitch(SKY_PITCH);

		this.prevYaw = this.watchYaw;
		this.prevBodyYaw = this.watchYaw;
		this.prevHeadYaw = this.watchYaw;
		this.prevPitch = SKY_PITCH;
	}

	/**
	 * Squared up to whoever it is fighting, with its face turned past them at the sky.
	 *
	 * <p>Body one way and eyes another is the whole read of the punctuation: it has stopped to deal
	 * with something more important, and the something more important is above and behind the person
	 * currently hitting it.
	 */
	private void facePlayerLookingUp() {
		PlayerEntity player = challenger();
		if (player == null) {
			player = this.getWorld().getClosestPlayer(this, FIGHT_RANGE);
		}

		if (player != null) {
			faceEntity(player);
		}

		// Only the head goes up, and only when the arm is up with it.
		if (getArmPose() == ArmPose.SKY) {
			this.setPitch(SKY_PITCH);
		}
	}

	private void faceEntity(Entity target) {
		double dx = target.getX() - this.getX();
		double dz = target.getZ() - this.getZ();
		double dy = target.getEyeY() - this.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(dy, horizontal) * MathHelper.DEGREES_PER_RADIAN));

		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.setPitch(pitch);
	}

	// --- the room -------------------------------------------------------------

	/**
	 * Tells everyone near enough what the fight is doing to their client: which fog to use and whether
	 * the music has been replaced.
	 *
	 * <p>Sent on a repeat with an expiry on it rather than once at each edge, so that a client which
	 * missed a packet, or whose witness was unloaded out from under it, is never left with a
	 * five-block world and no way out of it.
	 */
	private void syncEffects(boolean on) {
		if (!on) {
			// The one ending that is not an interruption is the one it loses, and that one leaves the
			// track playing. Everything else that gets here - walked away from, forgotten, the chunk
			// unloaded, the challenger logged out - is something being cut short, and is cut.
			WitnessPayload ending = getState() == State.DYING
					? WitnessPayload.fadeOut()
					: WitnessPayload.off();

			for (UUID id : new ArrayList<>(this.affected)) {
				ServerPlayerEntity player = playerById(id);
				if (player != null) {
					ServerPlayNetworking.send(player, ending);

					// Whatever ended this, nobody is left walking towards a fight that is over.
					ServerPlayNetworking.send(player, WitnessPullPayload.release());
				}
			}
			this.affected.clear();
			return;
		}

		if (this.age % 10 != 0) {
			return;
		}

		// Never fading here. This is the fight still going on, and the flag belongs to the one packet
		// that ends it - see the ending above.
		WitnessPayload payload = new WitnessPayload(true, fightFog(), 40, false);

		Set<UUID> near = new HashSet<>();

		for (PlayerEntity player : this.getWorld().getEntitiesByClass(PlayerEntity.class,
				this.getBoundingBox().expand(FIGHT_RANGE), player -> !player.isSpectator())) {
			if (!(player instanceof ServerPlayerEntity server)) {
				continue;
			}

			ServerPlayNetworking.send(server, payload);
			near.add(server.getUuid());

			if (getState() == State.FIGHTING) {
				this.bar.addPlayer(server);
			}
		}

		for (UUID id : new ArrayList<>(this.affected)) {
			if (near.contains(id)) {
				continue;
			}

			ServerPlayerEntity player = playerById(id);
			if (player != null) {
				ServerPlayNetworking.send(player, WitnessPayload.off());
				this.bar.removePlayer(player);
			}
		}

		this.affected.clear();
		this.affected.addAll(near);

		if (getState() == State.FIGHTING) {
			this.bar.setVisible(true);
		}
	}

	/**
	 * How much of the world the fight is currently allowing: nothing at all before it starts, the
	 * quarter's own fog while it is swinging, and {@link #POINT_FOG} for the four seconds it is
	 * pointing - the room is at its smallest exactly while the player has no say in where they are
	 * walking.
	 *
	 * <p>Dying counts as fighting here. The room it closed stays closed until the body is gone, so
	 * nothing about the last three seconds hands any of it back - the fog going out at the end is the
	 * fight being over, not a thing the fight does.
	 */
	private float fightFog() {
		if (getState() != State.FIGHTING && getState() != State.DYING) {
			return 0.0F;
		}

		float quarterFog = FOG_END[this.quarter];

		// Never the larger of the two. By the last quarter the fight is already tighter than the point
		// is, and a punctuation that handed some of the room back would be a relief rather than a
		// threat.
		return this.pointTicks > 0 ? Math.min(POINT_FOG, quarterFog) : quarterFog;
	}

	private ServerPlayerEntity playerById(UUID id) {
		return this.getWorld().getServer() == null
				? null
				: this.getWorld().getServer().getPlayerManager().getPlayer(id);
	}

	private PlayerEntity challenger() {
		return this.challenger == null ? null : this.getWorld().getPlayerByUuid(this.challenger);
	}

	@Override
	public void remove(RemovalReason reason) {
		// Whatever took it away - death, an unloaded chunk, a command - the bar and the fog go with it.
		if (!this.getWorld().isClient) {
			this.bar.setVisible(false);
			this.bar.clearPlayers();
			syncEffects(false);
			dismissWatchers();
		}
		super.remove(reason);
	}

	// --- odds and ends --------------------------------------------------------

	public State getState() {
		return State.values()[MathHelper.clamp(this.dataTracker.get(STATE), 0, State.values().length - 1)];
	}

	private void setState(State state) {
		this.dataTracker.set(STATE, (byte) state.ordinal());
		this.stateTicks = 0;
	}

	public ArmPose getArmPose() {
		return ArmPose.values()[MathHelper.clamp(this.dataTracker.get(POSE), 0, ArmPose.values().length - 1)];
	}

	private void setArmPose(ArmPose pose) {
		this.dataTracker.set(POSE, (byte) pose.ordinal());
	}

	/** It sounds like a player, because it is one. This is the single most upsetting thing about it. */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PLAYER_DEATH;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	/** Placed once and kept. Nothing about the mob cap has any say over it. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("WatchYaw", this.watchYaw);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);

		// A fight is never reloaded. Whatever it was doing when the world closed, it is back in the
		// field pointing at the sky with all of its health, and the fare has to be paid again.
		watchSky(nbt.getFloat("WatchYaw"));
		this.setHealth(this.getMaxHealth());
		this.setAiDisabled(true);

		// Including one that was half way into the ground. NoGravity is vanilla's and does persist, so
		// without this a witness saved mid-death comes back hanging in the air where it had got to.
		this.noClip = false;
		this.setNoGravity(false);
		this.surfaceY = Double.NaN;
	}

	/** For {@code /witness}: everything the command needs to know without exposing the internals. */
	public List<String> describe() {
		return List.of("state=" + getState(), "pose=" + getArmPose(), "quarter=" + this.quarter,
				"health=" + Math.round(this.getHealth()) + "/" + Math.round(this.getMaxHealth()),
				"hardestHit=" + String.format("%.1f", this.hardestHit));
	}
}
