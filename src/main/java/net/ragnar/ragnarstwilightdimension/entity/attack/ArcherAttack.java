package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;

import java.util.List;

/**
 * One of them turns up holding a bow, empties it into somebody for three seconds, and goes.
 *
 * <p>The blank figure has held nothing in its hands for the whole of this mod. It hangs over the
 * twilight watching people who have not looked up, it stands in the ring during the circle and
 * punches, and at no point has it ever picked anything up. This is the one time it does, and what it
 * has picked up is the most ordinary object in the game.
 *
 * <p>That is the joke and the threat at once. Everything else the disc throws is weather, or light,
 * or something enormous - and then the thing that has been watching you since the first night in the
 * twilight walks out of the dark with a bow and shoots you fifteen times.
 *
 * <h2>Reading it</h2>
 *
 * <p>Arrows, so they are dodged the way arrows always were: get behind something, or move across the
 * line rather than along it. There is nothing to get behind on the disc, which leaves moving - and
 * moving is what everything else in the phase is already punishing. It is deliberately the attack
 * that is hardest to combine with the others, which is why it is rare.
 *
 * <p>It cannot be killed - nothing can touch a blank figure - so there is no answer except outlasting
 * the three seconds. It leaves on its own, and it was always going to.
 */
public final class ArcherAttack implements Attack {
	private static final int GRACE = 300;

	/** How often the dice are rolled at all. */
	private static final int INTERVAL = 50;

	/** And the odds on each roll, at the start of the phase and at the end of it. */
	private static final float CHANCE_EARLY = 0.03F;
	private static final float CHANCE_LATE = 0.09F;

	/** How far off it stands. Far enough to be a shooter rather than a brawler. */
	private static final double STAND_OFF = 14.0;

	/** How long it stands there before the first arrow, so there is something to react to. */
	private static final int DRAW_TICKS = 20;

	/** Three seconds of shooting. */
	private static final int FIRE_TICKS = 60;

	/** And a moment afterwards before it is gone. */
	private static final int LINGER_TICKS = 12;

	/** Ticks between arrows. Every fourth, which is fifteen of them. */
	private static final int EVERY = 4;

	/** How hard each one hits. Strong, as asked - about double a fully drawn bow. */
	private static final double DAMAGE = 7.0;

	/** How fast they go, and how wide it sprays. Enough spread that fifteen is not fifteen hits. */
	private static final float SPEED = 2.6F;
	private static final float SPREAD = 6.0F;

	@Override
	public String name() {
		return "archer";
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
		ServerPlayerEntity target = targets.get(world.getRandom().nextInt(targets.size()));

		double bearing = world.getRandom().nextDouble() * Math.PI * 2.0;
		Vec3d at = Attacks.ontoDisc(target.getPos().add(
				Math.cos(bearing) * STAND_OFF, 0.0, Math.sin(bearing) * STAND_OFF));

		boss.launch(new Archer(new Vec3d(at.x, Attacks.GROUND_Y, at.z), target));
	}

	/** One of them, from arriving to leaving. */
	private static final class Archer implements Ongoing {
		private final Vec3d at;
		private final ServerPlayerEntity target;

		private PaleFigureEntity figure;
		private int ticks;

		private Archer(Vec3d at, ServerPlayerEntity target) {
			this.at = at;
			this.target = target;
		}

		@Override
		public boolean tick(TheEntity boss, ServerWorld world) {
			if (this.ticks == 0) {
				arrive(world);
			}

			this.ticks++;

			if (this.figure == null || !this.target.isAlive() || this.target.getWorld() != world) {
				cancel(world);
				return true;
			}

			if (this.ticks > DRAW_TICKS && this.ticks <= DRAW_TICKS + FIRE_TICKS
					&& (this.ticks - DRAW_TICKS) % EVERY == 0) {
				shoot(world);
			}

			if (this.ticks >= DRAW_TICKS + FIRE_TICKS + LINGER_TICKS) {
				cancel(world);
				return true;
			}

			return false;
		}

		private void arrive(ServerWorld world) {
			PaleFigureEntity archer = ModEntities.PALE_FIGURE.create(world);

			if (archer == null) {
				return;
			}

			archer.refreshPositionAndAngles(this.at.x, this.at.y, this.at.z, 0.0F, 0.0F);

			// The one thing it has ever held. The renderer draws it because PaleFigureRenderer carries a
			// held-item feature - every other figure in the mod holds an empty stack and draws nothing.
			archer.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));

			// Which keeps it turned to face them for the whole three seconds, which is also what makes
			// the arrows come from where it is looking.
			archer.watch(this.target);
			world.spawnEntity(archer);
			this.figure = archer;

			world.spawnParticles(ParticleTypes.END_ROD,
					this.at.x, this.at.y + 1.0, this.at.z, 30, 0.3, 0.8, 0.3, 0.02);
		}

		/**
		 * One arrow, aimed from its own eyes at theirs.
		 *
		 * <p>Ordinary arrows, owned by the figure, so everything a player already knows about arrows
		 * holds: they can be blocked with a shield, they are slower than a beam and they can be walked
		 * out of the way of. The spread is what turns fifteen shots into a threat rather than a
		 * sentence.
		 */
		private void shoot(ServerWorld world) {
			Vec3d from = new Vec3d(this.figure.getX(), this.figure.getEyeY(), this.figure.getZ());

			ArrowEntity arrow = new ArrowEntity(world, this.figure,
					new ItemStack(Items.ARROW), new ItemStack(Items.BOW));

			arrow.setPosition(from.x, from.y, from.z);
			arrow.setDamage(DAMAGE);

			double dx = this.target.getX() - from.x;
			double dy = this.target.getBodyY(0.5) - from.y;
			double dz = this.target.getZ() - from.z;

			arrow.setVelocity(dx, dy, dz, SPEED, SPREAD);
			world.spawnEntity(arrow);

			world.playSound(null, from.x, from.y, from.z,
					SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.2F, 0.9F);
		}

		@Override
		public void cancel(ServerWorld world) {
			if (this.figure != null && !this.figure.isRemoved()) {
				world.spawnParticles(ParticleTypes.END_ROD,
						this.figure.getX(), this.figure.getY() + 1.0, this.figure.getZ(),
						20, 0.3, 0.8, 0.3, 0.02);
				this.figure.discard();
			}

			this.figure = null;
		}
	}
}
