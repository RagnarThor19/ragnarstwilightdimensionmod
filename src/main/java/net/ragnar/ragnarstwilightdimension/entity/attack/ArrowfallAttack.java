package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Five spots on the floor are marked, and then the sky empties itself into them.
 *
 * <p>{@code ArcherAttack} is one blank figure with a bow standing somewhere on the disc, and it is
 * rare because it is a duel. This is the same object arriving as artillery instead: nobody fires
 * these, there is nothing up there to have fired them, and what comes down is not a scatter but five
 * columns of arrows going into five squares of floor as fast as they can be put there.
 *
 * <h2>Five places, not everywhere</h2>
 *
 * <p>It used to be thirty arrows spread over nine metres of floor around somebody, which made it
 * weather - unavoidable, unreadable, and paid for in an arrow or two a volley no matter what anybody
 * did. It is now five barrages: five marked circles about a metre across, placed around the players
 * at anywhere from three to ten metres out, each of which is then filled with two dozen arrows in
 * under half a second.
 *
 * <p>So the whole attack is dodged completely by not being in any of five small circles, and walking
 * into one while it is pouring is not one arrow, it is however many arrive before you leave. That is
 * the trade the rework makes: it stopped being a tax on existing and became five places you must not
 * be, which is what everything else on this disc already is.
 *
 * <p>They do not open together. Each is a few ticks behind the one before it, so the attack crosses
 * the arena as a sequence and the last of them is still being read while the first is already coming
 * down - and running out of one is capable of running into the next.
 *
 * <h2>The tips</h2>
 *
 * <p>Each barrage picks its own, out of {@link #TIPS}, and no two in the same volley pick the same
 * one. The circle is drawn in the colour of whatever it is about to be full of, which is the whole of
 * the warning: a green ring and a pale blue one are not the same mistake.
 *
 * <p>The frost tip is the one that is not a status effect. Vanilla has no arrow that freezes, so the
 * cold is put on by hand wherever each shaft ends up - see {@link Salvo#cold} - and it is the same
 * cold {@link FrostAttack} deals in, which on a disc where the only defence is being somewhere else
 * is most of the damage.
 *
 * <h2>They cannot be picked up</h2>
 *
 * <p>Explicitly, on every single one. A hundred and twenty arrows a volley otherwise ends the fight
 * with several stacks on the floor of an arena somebody is fighting a boss in - and a player who
 * leaves the Blank paid in arrows for having been shot at has been paid for being hit, which no
 * attack in this fight has ever done. They also all go, two seconds after the last one lands, rather
 * than waiting out vanilla despawn with the next volley already in the air.
 */
public final class ArrowfallAttack implements Attack {
	private static final int GRACE = 100;

	/**
	 * Ticks between volleys, at the start of the phase and at the end of it.
	 *
	 * <p>A little longer than they were. One volley is now five separate things to read rather than a
	 * spray to stand still in, and two of those overlapping is enough of the floor spoken for.
	 */
	private static final int SLOWEST = 150;
	private static final int FASTEST = 82;

	/** How many barrages in one volley. */
	private static final int BARRAGES = 5;

	/** Ticks between one barrage opening and the next. */
	private static final int STAGGER = 7;

	/** How long a barrage's circle is drawn before anything is put into it. */
	private static final int WARNING_TICKS = 16;

	/** How long it pours for, once it starts. Well under a second. */
	private static final int POUR_TICKS = 8;

	/** And how many arrows go in per tick of that. */
	private static final int PER_TICK = 3;

	/** How long the last of them stands in the floor before the volley is taken away. */
	private static final int LINGER = 40;

	/** How far out around somebody a barrage is placed, at the nearest and at the furthest. */
	private static final double NEAREST = 3.0;
	private static final double FURTHEST = 10.0;

	/** How wide the circle is - both the mark and the spread the arrows are jittered inside. */
	private static final double SPOT_RADIUS = 1.1;

	/** How far up they start. */
	private static final double DROP_HEIGHT = 30.0;

	/**
	 * How fast they are launched, before gravity gets to them. A fully drawn bow is 3.0.
	 *
	 * <p>Half again as fast as that, because the point of this one is that the gap between the circle
	 * filling with light and the circle being full of arrows is about a quarter of a second.
	 */
	private static final float SPEED = 4.5F;

	/**
	 * Straight down, and no spread at all.
	 *
	 * <p>The scatter inside a barrage is put in by hand, at the point the arrow is spawned, so that
	 * the mark on the floor is exactly the circle the arrows are launched inside. Vanilla's own spread
	 * is applied to the velocity and so widens with the distance fallen, which over thirty metres is
	 * the attack landing somewhere it did not say it would.
	 */
	private static final float SPREAD = 0.0F;

	/**
	 * What one is worth.
	 *
	 * <p>Not the damage, the multiplier: vanilla works an arrow out as {@code ceil(speed * damage)} at
	 * the moment it hits, so this is six by the time something moving this fast arrives. Three hearts
	 * for having been in the circle, and the effect on top of it - which is priced for the fact that a
	 * player's own damage cooldown means a barrage lands about two of these on anybody, not
	 * twenty-four.
	 */
	private static final double DAMAGE = 1.2;

	/** How close to a shaft counts as having been under it, for the tips that do more than damage. */
	private static final double TOUCH = 1.4;

	/**
	 * One kind of arrow.
	 *
	 * @param name      what it is called, for whoever is tuning this later
	 * @param effect    what it puts on whatever it hits, or null for the one that is not an effect
	 * @param duration  for how long, in ticks
	 * @param amplifier and how badly. 0 is the first tier
	 * @param freeze    frozen ticks put on anybody the shaft ends up next to. The game runs these down
	 *                  by <b>two</b> a tick, so this is half as many ticks of frost on the screen as it
	 *                  looks. Zero for every tip but the frost one
	 * @param slow      how long they cannot run properly afterwards, and
	 * @param slowLevel how badly. Only ever paired with the freeze
	 * @param colour    what the circle is drawn in, so the tip is read off the floor before it arrives
	 * @param pitch     and the note the barrage opens on
	 */
	private record Tip(String name, @Nullable RegistryEntry<StatusEffect> effect, int duration,
					   int amplifier, int freeze, int slow, int slowLevel, Vector3f colour, float pitch) {
		private DustParticleEffect mark() {
			return new DustParticleEffect(this.colour(), 1.2F);
		}
	}

	/**
	 * All of them. Five are drawn from this without replacement for each volley, so a volley is always
	 * five different mistakes and never the same one five times.
	 */
	private static final List<Tip> TIPS = List.of(
			new Tip("frost", null, 0, 0,
					220, 60, 0, new Vector3f(0.72F, 0.88F, 1.0F), 1.6F),
			new Tip("venom", StatusEffects.POISON, 120, 1,
					0, 0, 0, new Vector3f(0.45F, 0.85F, 0.35F), 1.2F),
			new Tip("rot", StatusEffects.WITHER, 100, 0,
					0, 0, 0, new Vector3f(0.30F, 0.26F, 0.30F), 0.7F),
			new Tip("dark", StatusEffects.BLINDNESS, 90, 0,
					0, 0, 0, new Vector3f(0.16F, 0.16F, 0.22F), 0.9F),
			new Tip("weight", StatusEffects.WEAKNESS, 200, 1,
					0, 0, 0, new Vector3f(0.58F, 0.58F, 0.66F), 0.6F),
			new Tip("fever", StatusEffects.NAUSEA, 120, 0,
					0, 0, 0, new Vector3f(0.58F, 0.36F, 0.64F), 1.05F));

	@Override
	public String name() {
		return "arrowfall";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		return Attacks.ramped(tick, progress, GRACE, SLOWEST, FASTEST);
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		boss.launch(new Volley(List.copyOf(targets), world.getRandom()));
	}

	/** One volley: five barrages, each a few ticks behind the last, and then all of it taken back. */
	private static final class Volley implements Ongoing {
		private final List<Salvo> salvos = new ArrayList<>();

		/** Everybody who was alive when it started. Only used to open on, never aimed with again. */
		private final List<ServerPlayerEntity> targets;

		private int ticks;

		private Volley(List<ServerPlayerEntity> targets, Random random) {
			this.targets = targets;

			List<Tip> tips = draw(random);

			for (int i = 0; i < BARRAGES; i++) {
				this.salvos.add(new Salvo(where(targets, random), tips.get(i)));
			}
		}

		/** Five of the tips, without replacement. Fisher-Yates on a copy, then the front of it. */
		private static List<Tip> draw(Random random) {
			List<Tip> pool = new ArrayList<>(TIPS);

			for (int i = pool.size() - 1; i > 0; i--) {
				int j = random.nextInt(i + 1);
				Tip held = pool.get(i);

				pool.set(i, pool.get(j));
				pool.set(j, held);
			}

			return pool;
		}

		/**
		 * Around somebody, at a bearing and a distance.
		 *
		 * <p>Never on them and never further than ten metres off: a barrage placed on top of where a
		 * player is standing is a hit rather than a placement, and one at the far end of the disc is not
		 * an attack on anybody. The band between those two is the whole of the dodge - close enough that
		 * five of them account for most of the floor a player can reach, far enough that leaving is
		 * always possible.
		 */
		private static Vec3d where(List<ServerPlayerEntity> targets, Random random) {
			List<ServerPlayerEntity> standing = new ArrayList<>();

			for (ServerPlayerEntity player : targets) {
				if (player.isAlive() && !player.isSpectator()) {
					standing.add(player);
				}
			}

			if (standing.isEmpty()) {
				return Attacks.somewhereOnDisc(random);
			}

			ServerPlayerEntity at = standing.get(random.nextInt(standing.size()));

			double bearing = random.nextDouble() * Math.PI * 2.0;
			double out = NEAREST + random.nextDouble() * (FURTHEST - NEAREST);

			Vec3d spot = Attacks.ontoDisc(at.getPos().add(
					Math.cos(bearing) * out, 0.0, Math.sin(bearing) * out));

			return new Vec3d(spot.x, Attacks.GROUND_Y, spot.z);
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			this.ticks++;

			if (this.ticks == 1) {
				open(world);
			}

			boolean anyLeft = false;

			for (int i = 0; i < this.salvos.size(); i++) {
				// Measured from this one's own start, so the last barrage does exactly what the first did,
				// a wave later.
				int age = this.ticks - i * STAGGER;

				if (age <= 0) {
					anyLeft = true;
					continue;
				}

				anyLeft |= this.salvos.get(i).tick(boss, world, age);
			}

			if (anyLeft) {
				return false;
			}

			cancel(world);
			return true;
		}

		/**
		 * A bow let go of, somewhere above everybody, pitched down.
		 *
		 * <p>Played at each player rather than at a point, because there is no point - whatever this is
		 * coming out of has no position, and a sound that falls off with distance would imply one.
		 */
		private void open(ServerWorld world) {
			for (ServerPlayerEntity player : this.targets) {
				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.6F, 0.6F);
			}
		}

		/** Every arrow this volley put in the world, whether it landed or not. */
		@Override
		public void cancel(ServerWorld world) {
			for (Salvo salvo : this.salvos) {
				salvo.take();
			}
		}
	}

	/** One barrage: the circle, the pour, and the cold on whoever was standing in it. */
	private static final class Salvo {
		private final Vec3d at;
		private final Tip tip;
		private final DustParticleEffect mark;

		/** Everything it put in the world, so all of it can be taken back at once. */
		private final List<ArrowEntity> arrows = new ArrayList<>();

		/**
		 * The ones that have not finished yet, for the tips that do something where they end up.
		 *
		 * <p>Left empty for every tip but the frost one, which is what keeps {@link #cold} free for five
		 * sixths of the barrages in a volley.
		 */
		private final List<ArrowEntity> unresolved = new ArrayList<>();

		private Salvo(Vec3d at, Tip tip) {
			this.at = at;
			this.tip = tip;
			this.mark = tip.mark();
		}

		/** @return whether this one still has anything left to do */
		private boolean tick(TheEntity boss, ServerWorld world, int age) {
			if (age <= WARNING_TICKS) {
				warn(world, age);
				return true;
			}

			int poured = age - WARNING_TICKS;

			if (poured <= POUR_TICKS) {
				if (poured == 1) {
					world.playSound(null, this.at.x, this.at.y, this.at.z,
							SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.4F, this.tip.pitch());
				}

				for (int i = 0; i < PER_TICK; i++) {
					fall(boss, world);
				}

				ring(world, 1.0F);
			}

			cold(world);

			return age <= WARNING_TICKS + POUR_TICKS + LINGER;
		}

		/**
		 * The circle, in the colour of what is about to fill it, brightening as it runs down.
		 *
		 * <p>Drawn as a rim and a column together. The rim is the honest boundary of what the arrows are
		 * launched inside, and the column is so that a barrage placed ten metres behind somebody is read
		 * at all - a metre-wide ring on a white floor is invisible from anywhere but standing on it,
		 * which for the one attack in this fight that is completely dodgeable would be a mugging.
		 */
		private void warn(ServerWorld world, int age) {
			if (age % 2 != 0) {
				return;
			}

			float share = (float) age / WARNING_TICKS;

			ring(world, share);

			world.spawnParticles(this.mark,
					this.at.x, Attacks.GROUND_Y + share * 2.5, this.at.z, 2, 0.25, 0.25, 0.25, 0.0);

			if (age == 2) {
				world.playSound(null, this.at.x, this.at.y, this.at.z,
						SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE,
						0.8F, this.tip.pitch());
			}
		}

		/** Exactly the circle the arrows come down inside, and nothing wider. */
		private void ring(ServerWorld world, float share) {
			int points = 12;

			for (int i = 0; i < points; i++) {
				double bearing = Math.PI * 2.0 * i / points;

				world.spawnParticles(this.mark,
						this.at.x + Math.cos(bearing) * SPOT_RADIUS, Attacks.GROUND_Y + 0.1,
						this.at.z + Math.sin(bearing) * SPOT_RADIUS,
						share > 0.6F ? 2 : 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/** One arrow, from thirty metres up, into the circle rather than at anybody. */
		private void fall(TheEntity boss, ServerWorld world) {
			Random random = world.getRandom();

			// Square-rooted, so the arrows are spread evenly over the circle rather than crowded into the
			// middle of it. A barrage with a hollow edge is one a player can stand on the rim of.
			double bearing = random.nextDouble() * Math.PI * 2.0;
			double out = Math.sqrt(random.nextDouble()) * SPOT_RADIUS;

			double x = this.at.x + Math.cos(bearing) * out;
			double z = this.at.z + Math.sin(bearing) * out;

			// Owned by the thing, so being killed by one reads as being killed by it rather than by a
			// stray arrow. It is thirty metres below them and they are going straight down, so the owner
			// is never in the way of its own barrage.
			ArrowEntity arrow = new ArrowEntity(world, boss,
					new ItemStack(Items.ARROW), new ItemStack(Items.BOW));

			arrow.setPosition(x, Attacks.GROUND_Y + DROP_HEIGHT, z);
			arrow.setVelocity(0.0, -1.0, 0.0, SPEED, SPREAD);
			arrow.setDamage(DAMAGE);

			if (this.tip.effect() != null) {
				// A fresh instance per arrow. addEffect puts it into the arrow's own stack component, and
				// one instance shared across two dozen of them is one object with two dozen owners.
				arrow.addEffect(new StatusEffectInstance(
						this.tip.effect(), this.tip.duration(), this.tip.amplifier()));
			}

			// The whole of the no-loot rule, and it has to be said out loud: an arrow is otherwise
			// whatever the stack it was made from says it is.
			arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;

			world.spawnEntity(arrow);
			this.arrows.add(arrow);

			if (this.tip.freeze() > 0) {
				this.unresolved.add(arrow);
			}
		}

		/**
		 * The cold, where a shaft finished.
		 *
		 * <p>Vanilla has no frozen arrow and no hook for one, so this watches instead. An arrow that has
		 * reached the floor got there, and an arrow that has been removed before reaching it hit
		 * somebody on the way - and those two are the only ends one of these has, because they are
		 * launched straight down from thirty metres over solid ground. Both resolve the same way, on the
		 * circle where it finished, so being shot by one and having one land at your feet are the same
		 * cold; this is meant to freeze the square rather than the player.
		 *
		 * <p>Read off the height rather than off the velocity, which is the tempting version and the
		 * wrong one: a stuck arrow keeps whatever velocity the block hit left on it, and how much that is
		 * depends on where in its last step it met the floor.
		 */
		private void cold(ServerWorld world) {
			Iterator<ArrowEntity> shafts = this.unresolved.iterator();

			while (shafts.hasNext()) {
				ArrowEntity arrow = shafts.next();

				if (!arrow.isRemoved() && arrow.getY() > Attacks.GROUND_Y + 0.5) {
					continue;
				}

				shafts.remove();
				chill(world, new Vec3d(arrow.getX(), Attacks.GROUND_Y, arrow.getZ()));

				world.spawnParticles(ParticleTypes.SNOWFLAKE,
						arrow.getX(), arrow.getY(), arrow.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
			}
		}

		/** Everybody within reach of one shaft's end. */
		private void chill(ServerWorld world, Vec3d end) {
			Box around = new Box(end.x - TOUCH, end.y - 2.0, end.z - TOUCH,
					end.x + TOUCH, end.y + 2.0, end.z + TOUCH);

			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, around,
					candidate -> !candidate.isSpectator() && candidate.isAlive())) {
				double dx = player.getX() - end.x;
				double dz = player.getZ() - end.z;

				if (dx * dx + dz * dz > TOUCH * TOUCH) {
					continue;
				}

				// Added rather than set, and capped, so somebody who stood in the barrage gets colder with
				// every shaft instead of being put back to the same second by the last one.
				player.setFrozenTicks(Math.min(
						this.tip.freeze() * 2, player.getFrozenTicks() + this.tip.freeze()));
				player.addStatusEffect(new StatusEffectInstance(
						StatusEffects.SLOWNESS, this.tip.slow(), this.tip.slowLevel()));
			}
		}

		/** Everything this barrage put in the world. Safe to call twice. */
		private void take() {
			for (ArrowEntity arrow : this.arrows) {
				if (!arrow.isRemoved()) {
					arrow.discard();
				}
			}

			this.arrows.clear();
			this.unresolved.clear();
		}
	}
}
