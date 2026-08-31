package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.BloodSteveEntity;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Five of the blood moon's own come out onto the disc.
 *
 * <p>Every other thing the survival phase throws is scenery with a hitbox: it comes down where it
 * comes down, it does not know your name, and once it has landed it is over. These are the only
 * things in the phase that <b>follow you</b>. They path, they pick a target, they change their minds,
 * and they are still there in thirty seconds unless somebody deals with them.
 *
 * <p>Which is what makes it worth borrowing rather than inventing. Anybody who reaches the disc has
 * survived blood moons in the twilight and knows exactly what these are and exactly how they behave -
 * so the attack needs no telegraph and no explanation, and the horror of it is entirely that they are
 * <i>here</i>, in the sealed room, at the end of everything. The dimension's one ordinary threat,
 * turning up in the one place it should not be able to reach.
 *
 * <p>The trade for that is the fight's only sustained decision: the phase does not stop while they
 * are up, so every second spent on them is a second not spent reading the floor. Rare, because five
 * of them on top of everything else for half a minute is a lot to ask of anybody.
 */
public final class SteveAttack implements Attack {
	/** Nothing in the first third of the phase. There is enough going on by then. */
	private static final int GRACE = 380;

	/** How often the dice are rolled at all. */
	private static final int INTERVAL = 60;

	/** And the odds on each roll, at the start of the phase and at the end of it. */
	private static final float CHANCE_EARLY = 0.03F;
	private static final float CHANCE_LATE = 0.09F;

	/** How many come out. */
	private static final int STEVES = 5;

	/** How far from the middle they arrive, so they are never on top of anybody at the moment they do. */
	private static final double RING_MIN = 12.0;
	private static final double RING_MAX = 24.0;

	/** How long they get before they are taken away again, whether or not anybody dealt with them. */
	private static final int LIFETIME_TICKS = 500;

	@Override
	public String name() {
		return "steves";
	}

	@Override
	public boolean due(Random random, int tick, float progress) {
		if (tick < GRACE || tick % INTERVAL != 0) {
			return false;
		}

		return random.nextFloat() < CHANCE_EARLY + (CHANCE_LATE - CHANCE_EARLY) * progress;
	}

	@Override
	public void run(TheEntity boss, ServerWorld world, List<ServerPlayerEntity> targets, float progress) {
		boss.launch(new Crowd());
	}

	/** The five of them, from arriving to being taken away. */
	private static final class Crowd implements Ongoing {
		private final List<BloodSteveEntity> steves = new ArrayList<>();
		private int ticks;

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			if (this.ticks == 0) {
				arrive(world);
			}

			this.ticks++;

			// Dropped early if they are all dealt with, so that killing them is worth something beyond
			// not being hit by them - the attack is over when the players end it.
			this.steves.removeIf(steve -> steve.isRemoved() || !steve.isAlive());

			if (this.steves.isEmpty() || this.ticks >= LIFETIME_TICKS) {
				cancel(world);
				return true;
			}

			return false;
		}

		private void arrive(ServerWorld world) {
			Random random = world.getRandom();

			for (int i = 0; i < STEVES; i++) {
				BloodSteveEntity steve = ModEntities.BLOOD_STEVE.create(world);

				if (steve == null) {
					continue;
				}

				double bearing = random.nextDouble() * Math.PI * 2.0;
				double distance = RING_MIN + random.nextDouble() * (RING_MAX - RING_MIN);
				Vec3d at = Attacks.ontoDisc(new Vec3d(
						Math.cos(bearing) * distance, Attacks.GROUND_Y, Math.sin(bearing) * distance));

				// Without this they remove themselves on their very first tick: what keeps one of these
				// in the world is the blood moon, and there is no blood moon in here. See
				// BloodSteveEntity#setUnbound.
				steve.setUnbound();
				steve.refreshPositionAndAngles(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
				world.spawnEntity(steve);
				this.steves.add(steve);

				world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 20, 0.4, 0.8, 0.4, 0.02);
			}

			world.playSound(null, 0.5, Attacks.GROUND_Y, 0.5,
					SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2F, 1.4F);
		}

		@Override
		public void cancel(ServerWorld world) {
			for (BloodSteveEntity steve : this.steves) {
				if (!steve.isRemoved()) {
					world.spawnParticles(ParticleTypes.LARGE_SMOKE,
							steve.getX(), steve.getY() + 1.0, steve.getZ(), 15, 0.4, 0.8, 0.4, 0.02);
					steve.discard();
				}
			}

			this.steves.clear();
		}
	}
}
